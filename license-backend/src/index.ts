import type { Env } from "./env";
import { jsonError } from "./http/response";
import { route } from "./http/router";
import { securityLog, type SecurityEvent } from "./security/security-log";

export default {
  async fetch(request: Request, env: Env, _context: ExecutionContext): Promise<Response> {
    const requestId = crypto.randomUUID();
    let response: Response;
    try {
      response = await route(request, env, requestId);
    } catch {
      response = jsonError(500, "SERVER_ERROR", "The server could not process the request.");
      securityLog("server_error", new URL(request.url).pathname, 500, "SERVER_ERROR", requestId);
    }
    const headers = new Headers(response.headers);
    headers.set("X-Request-Id", requestId);
    const finalResponse = new Response(response.body, {
      status: response.status,
      statusText: response.statusText,
      headers,
    });
    if (finalResponse.status >= 400 && finalResponse.status !== 404 && finalResponse.status !== 405 && finalResponse.status !== 500) {
      const payload: { error?: { code?: string } } = await finalResponse.clone()
        .json<{ error?: { code?: string } }>().catch(() => ({}));
      const code = payload.error?.code ?? "UNKNOWN_ERROR";
      const path = new URL(request.url).pathname;
      securityLog(eventFor(path, finalResponse.status, code), path, finalResponse.status, code, requestId);
    }
    return finalResponse;
  },
} satisfies ExportedHandler<Env>;

function eventFor(path: string, status: number, code: string): SecurityEvent {
  if (status === 429) return path.startsWith("/v1/admin") ? "admin_rate_limited" : "rate_limited";
  if (code === "ADMIN_UNAUTHORIZED") return "admin_unauthorized";
  return path.includes("/refresh") ? "refresh_rejected" : "activation_rejected";
}
