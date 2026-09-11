import { AdminClient } from "./admin-client";
import { PORTAL_HTML } from "./portal-html";
import { FulfillmentRepository } from "./repository";
import { authenticateAccess, mutationAllowed } from "./security";
import { OperatorService, PortalError } from "./service";
import type { Env, OperatorIdentity } from "./types";

const SECURITY_HEADERS = {
  "X-Content-Type-Options": "nosniff",
  "Referrer-Policy": "no-referrer",
  "Content-Security-Policy": "default-src 'none'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'",
  "Permissions-Policy": "camera=(), microphone=(), geolocation=(), payment=()",
};

export default { fetch: (request: Request, env: Env, ctx: ExecutionContext) => handleRequest(request, env, ctx) };

export async function handleRequest(request: Request, env: Env, _ctx?: ExecutionContext,
  authenticate: (request: Request, env: Env) => Promise<OperatorIdentity | null> = authenticateAccess,
  fetchImpl: typeof fetch = fetch): Promise<Response> {
  const requestId = request.headers.get("CF-Ray") ?? crypto.randomUUID();
  const identity = await authenticate(request, env);
  if (!identity) return response({ error: { code: "ACCESS_REQUIRED", message: "Cloudflare Access authentication is required" } }, 401, true, requestId);
  const url = new URL(request.url);
  if (request.method === "GET" && url.pathname === "/") {
    const nonce = crypto.randomUUID().replace(/-/gu, "");
    return new Response(PORTAL_HTML.replace("__SCRIPT_NONCE__", nonce), { headers: { ...SECURITY_HEADERS,
      "Content-Security-Policy": `${SECURITY_HEADERS["Content-Security-Policy"]}; script-src 'nonce-${nonce}'`,
      "Content-Type": "text/html; charset=utf-8", "Cache-Control": "no-store", "X-Request-Id": requestId } });
  }
  if (!url.pathname.startsWith("/api/")) return response({ error: { code: "NOT_FOUND", message: "Not found" } }, 404, false, requestId);
  if (request.method !== "GET" && !mutationAllowed(request)) {
    return response({ error: { code: "CSRF_REJECTED", message: "Same-origin mutation proof is required" } }, 403, true, requestId);
  }
  const manifestFetch: typeof fetch = env.RELEASES && fetchImpl === fetch
    ? ((input, init) => env.RELEASES!.fetch(input, init)) as typeof fetch
    : fetchImpl;
  const adminFetch: typeof fetch = env.LICENSE_ADMIN && fetchImpl === fetch
    ? ((input, init) => env.LICENSE_ADMIN!.fetch(input, init)) as typeof fetch
    : fetchImpl;
  const service = new OperatorService(new FulfillmentRepository(env.FULFILLMENT_DB),
    new AdminClient(env.LICENSE_ADMIN_API_URL, env.LICENSE_ADMIN_TOKEN, adminFetch), env.UPDATE_MANIFEST_URL,
    () => new Date().toISOString(), manifestFetch);
  try {
    const result = await route(request, url, service, identity);
    safeLog("operator_request", result.status, url.pathname, requestId, identity.email);
    return response(result.body, result.status, result.sensitive, requestId);
  } catch (error) {
    const known = error instanceof PortalError ? error : null;
    const upstream = safeUpstreamError(error);
    const status = known?.status ?? 502;
    const code = known?.code ?? upstream?.code ?? "UPSTREAM_ERROR";
    safeLog("operator_error", status, url.pathname, requestId, identity.email, code);
    return response({ error: { code, message: known?.message ?? upstream?.message ?? "Operator operation failed",
      ...(known?.details ? { details: known.details } : {}) } }, status, true, requestId);
  }
}

async function route(request: Request, url: URL, service: OperatorService, identity: OperatorIdentity): Promise<RouteResult> {
  const parts = url.pathname.slice(5).split("/").filter(Boolean).map((part) => decodeURIComponent(part));
  if (request.method === "GET" && parts.length === 1 && parts[0] === "fulfillments") {
    return ok({ fulfillments: await service.list(url.searchParams.get("query")) });
  }
  if (request.method === "POST" && parts.length === 1 && parts[0] === "fulfillments") {
    return created(await service.create(await json(request), identity), true);
  }
  if (parts[0] !== "fulfillments" || !parts[1]) throw new PortalError(404, "NOT_FOUND", "Not found");
  const reference = parts[1];
  if (request.method === "GET" && parts.length === 2) return ok(await service.show(reference));
  if (request.method === "POST" && parts.length === 3 && parts[2] === "delivered") return ok(await service.markDelivered(reference, identity));
  if (request.method === "POST" && parts.length === 3 && parts[2] === "revoke") {
    const body = await json(request); return ok(await service.revoke(reference, isObject(body) && body.cancelled === true, identity));
  }
  if (request.method === "POST" && parts.length === 3 && parts[2] === "replace") return created(await service.replace(reference, await json(request), identity), true);
  if (request.method === "POST" && parts.length === 5 && parts[2] === "devices" && parts[4] === "reset") {
    return ok(await service.resetDevice(reference, parts[3]!, identity));
  }
  throw new PortalError(404, "NOT_FOUND", "Not found");
}

async function json(request: Request): Promise<unknown> {
  if (!request.headers.get("Content-Type")?.toLowerCase().startsWith("application/json")) throw new PortalError(415, "UNSUPPORTED_MEDIA_TYPE", "JSON is required");
  try { return await request.json(); } catch { throw new PortalError(400, "INVALID_JSON", "Request JSON is invalid"); }
}
function response(body: unknown, status: number, sensitive: boolean, requestId: string): Response {
  return Response.json(body, { status, headers: { ...SECURITY_HEADERS, "Cache-Control": sensitive ? "no-store" : "private, no-store",
    "X-Request-Id": requestId } });
}
function safeLog(event: string, status: number, route: string, requestId: string, operator: string, code?: string) {
  console.log(JSON.stringify({ event, status, route, requestId, operator, ...(code ? { code } : {}) }));
}
function ok(body: unknown, sensitive = false): RouteResult { return { body, status: 200, sensitive }; }
function created(body: unknown, sensitive = false): RouteResult { return { body, status: 201, sensitive }; }
function isObject(value: unknown): value is Record<string, unknown> { return typeof value === "object" && value !== null && !Array.isArray(value); }
function safeUpstreamError(error: unknown): { code: string; message: string } | null {
  if (!isObject(error) || typeof error.code !== "string") return null;
  const manifestCodes = new Set(["MANIFEST_URL_INVALID", "MANIFEST_FETCH_FAILED", "MANIFEST_HTTP_ERROR", "MANIFEST_INVALID"]);
  if (manifestCodes.has(error.code)) return { code: error.code, message: "Production release information is unavailable" };
  if (typeof error.status === "number" && /^[A-Z0-9_]{1,64}$/u.test(error.code)) {
    return { code: `ADMIN_${error.code}`, message: "License administration service rejected the operation" };
  }
  return null;
}
interface RouteResult { body: unknown; status: number; sensitive: boolean }
