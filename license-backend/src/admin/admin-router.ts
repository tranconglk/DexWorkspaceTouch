import type { Env } from "../env";
import { systemClock } from "../license/clock";
import { parseJsonBody } from "../http/body";
import { jsonError, jsonSuccess } from "../http/response";
import { isAdminAuthorized } from "./admin-auth";
import { AdminLicenseRepository } from "./admin-repository";
import { AdminLicenseService } from "./admin-service";
import { actorKey, allowed, rateLimited, sourceIp } from "../security/rate-limit";

const ADMIN_PREFIX = "/v1/admin/";
const MAX_DEVICES = 100;
const MAX_REASON_LENGTH = 200;
const NO_STORE = { "Cache-Control": "no-store" };

export function isAdminPath(pathname: string): boolean {
  return pathname === "/v1/admin" || pathname.startsWith(ADMIN_PREFIX);
}

export async function routeAdmin(request: Request, env: Env, requestId: string): Promise<Response> {
  const authorized = await isAdminAuthorized(request, env);
  if (!authorized) {
    if (!await allowed(env.ADMIN_UNAUTH_LIMITER, actorKey.adminUnauthorized(sourceIp(request)))) return rateLimited();
    return jsonError(401, "ADMIN_UNAUTHORIZED", "Admin authorization is required.",
      { ...NO_STORE, "WWW-Authenticate": "Bearer" });
  }
  if (!await allowed(env.ADMIN_AUTH_LIMITER, actorKey.adminAuthenticated())) return rateLimited();
  if (typeof env.LICENSE_KEY_PEPPER !== "string" || env.LICENSE_KEY_PEPPER.trim().length === 0) {
    throw new Error("Required license key pepper binding is unavailable.");
  }
  const url = new URL(request.url);
  const service = new AdminLicenseService(new AdminLicenseRepository(env.DB), systemClock, env.LICENSE_KEY_PEPPER);
  const segments = url.pathname.slice(ADMIN_PREFIX.length).split("/").filter(Boolean).map(decodeSegment);
  if (segments.some((value) => value === null)) return notFound();

  if (segments.length === 1 && segments[0] === "licenses") {
    if (request.method === "POST") return createLicense(request, service, requestId);
    if (request.method === "GET") return listLicenses(url, service);
    return methodNotAllowed("GET, POST");
  }
  if (segments.length === 2 && segments[0] === "licenses") {
    if (request.method !== "GET") return methodNotAllowed("GET");
    const license = await service.show(segments[1]!);
    return license === null ? licenseNotFound() : jsonSuccess(license, 200, NO_STORE);
  }
  if (segments.length === 3 && segments[0] === "licenses" && segments[2] === "revoke") {
    if (request.method !== "POST") return methodNotAllowed("POST");
    const reason = await parseReason(request);
    if (!reason.ok) return reason.response;
    const changed = await service.revoke(segments[1]!, reason.value, requestId);
    return changed === null ? licenseNotFound() : jsonSuccess({ licenseId: segments[1], revoked: true, alreadyRevoked: !changed }, 200, NO_STORE);
  }
  if (segments.length === 3 && segments[0] === "licenses" && segments[2] === "devices") {
    if (request.method !== "GET") return methodNotAllowed("GET");
    const devices = await service.devices(segments[1]!);
    return devices === null ? licenseNotFound() : jsonSuccess({ devices }, 200, NO_STORE);
  }
  if (segments.length === 5 && segments[0] === "licenses" && segments[2] === "devices" && segments[4] === "reset") {
    if (request.method !== "POST") return methodNotAllowed("POST");
    const reason = await parseReason(request);
    if (!reason.ok) return reason.response;
    const changed = await service.resetDevice(segments[1]!, segments[3]!, reason.value, requestId);
    return changed === null ? jsonError(404, "ADMIN_DEVICE_NOT_FOUND", "Device binding was not found.", NO_STORE)
      : jsonSuccess({ licenseId: segments[1], deviceId: segments[3], reset: true, alreadyReset: !changed }, 200, NO_STORE);
  }
  return notFound();
}

async function createLicense(request: Request, service: AdminLicenseService, requestId: string): Promise<Response> {
  const body = await parseJsonBody(request);
  if (!body.ok) return jsonError(body.code === "UNSUPPORTED_MEDIA_TYPE" ? 415 : body.code === "PAYLOAD_TOO_LARGE" ? 413 : 400, body.code, "Request body must be valid JSON.", NO_STORE);
  if (!isObject(body.value) || !onlyKeys(body.value, ["maxDevices", "expiresAtEpochSeconds"]) ||
      !Number.isInteger(body.value.maxDevices) || (body.value.maxDevices as number) < 1 || (body.value.maxDevices as number) > MAX_DEVICES ||
      !(body.value.expiresAtEpochSeconds === null || Number.isSafeInteger(body.value.expiresAtEpochSeconds)) ||
      (typeof body.value.expiresAtEpochSeconds === "number" && body.value.expiresAtEpochSeconds <= systemClock.nowEpochSeconds())) {
    return jsonError(400, "INVALID_REQUEST", "License creation request is invalid.", NO_STORE);
  }
  const created = await service.create(body.value.maxDevices as number, body.value.expiresAtEpochSeconds as number | null, requestId);
  return jsonSuccess(created, 201, NO_STORE);
}

async function listLicenses(url: URL, service: AdminLicenseService): Promise<Response> {
  const limit = parseBoundedInteger(url.searchParams.get("limit"), 50, 1, 100);
  const offset = parseBoundedInteger(url.searchParams.get("offset"), 0, 0, 1_000_000);
  if (limit === null || offset === null) return jsonError(400, "INVALID_REQUEST", "Pagination is invalid.", NO_STORE);
  const licenses = await service.list(limit, offset);
  return jsonSuccess({ licenses, limit, offset }, 200, NO_STORE);
}

async function parseReason(request: Request): Promise<{ ok: true; value: string | null } | { ok: false; response: Response }> {
  if (request.headers.get("Content-Length") === "0") return { ok: true, value: null };
  const contentType = request.headers.get("Content-Type");
  if (contentType === null) return { ok: true, value: null };
  const body = await parseJsonBody(request);
  if (!body.ok) {
    return { ok: false, response: jsonError(body.code === "PAYLOAD_TOO_LARGE" ? 413 : body.code === "UNSUPPORTED_MEDIA_TYPE" ? 415 : 400,
      body.code, "Reason is invalid.", NO_STORE) };
  }
  if (!isObject(body.value) || !onlyKeys(body.value, ["reason"]) ||
      !(body.value.reason === undefined || typeof body.value.reason === "string") ||
      (typeof body.value.reason === "string" && body.value.reason.length > MAX_REASON_LENGTH)) {
    return { ok: false, response: jsonError(400, "INVALID_REQUEST", "Reason is invalid.", NO_STORE) };
  }
  return { ok: true, value: typeof body.value.reason === "string" ? body.value.reason : null };
}

function isObject(value: unknown): value is Record<string, unknown> { return typeof value === "object" && value !== null && !Array.isArray(value); }
function onlyKeys(value: Record<string, unknown>, allowed: readonly string[]): boolean { return Object.keys(value).every((key) => allowed.includes(key)); }
function parseBoundedInteger(raw: string | null, fallback: number, minimum: number, maximum: number): number | null {
  if (raw === null) return fallback;
  if (!/^\d+$/.test(raw)) return null;
  const value = Number(raw);
  return Number.isSafeInteger(value) && value >= minimum && value <= maximum ? value : null;
}
function decodeSegment(value: string): string | null { try { const decoded = decodeURIComponent(value); return decoded.length > 0 && decoded.length <= 128 ? decoded : null; } catch { return null; } }
function methodNotAllowed(allow: string): Response { return jsonError(405, "METHOD_NOT_ALLOWED", "Method not allowed.", { ...NO_STORE, Allow: allow }); }
function licenseNotFound(): Response { return jsonError(404, "ADMIN_LICENSE_NOT_FOUND", "License was not found.", NO_STORE); }
function notFound(): Response { return jsonError(404, "NOT_FOUND", "Route not found.", NO_STORE); }
