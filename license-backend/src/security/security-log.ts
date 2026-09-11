export type SecurityEvent = "rate_limited" | "activation_rejected" | "refresh_rejected" |
  "admin_unauthorized" | "admin_rate_limited" | "server_error";

export function securityLog(event: SecurityEvent, route: string, status: number, code: string, requestId: string): void {
  console.log(JSON.stringify({ event, route, status, code, requestId }));
}
