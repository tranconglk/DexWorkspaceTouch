export const RATE_LIMIT_WINDOW_SECONDS = 60;

export class RateLimiterUnavailableError extends Error {
  constructor() { super("Rate limiter unavailable."); }
}

export async function allowed(binding: RateLimit | undefined, key: string): Promise<boolean> {
  if (binding === undefined || key.length === 0 || key.length > 512) throw new RateLimiterUnavailableError();
  try { return (await binding.limit({ key })).success; }
  catch { throw new RateLimiterUnavailableError(); }
}

export function sourceIp(request: Request): string {
  const value = request.headers.get("CF-Connecting-IP")?.trim();
  return value && value.length <= 64 ? value : "unavailable";
}

export function rateLimited(): Response {
  return jsonError(429, "RATE_LIMITED", "Too many requests. Try again later.", {
    "Cache-Control": "no-store", "Retry-After": "60",
  });
}

export const actorKey = {
  activationLicense: (licenseKeyHash: string) => `activation-license:${licenseKeyHash}`,
  activationProof: (challengeId: string) => `activation-proof:${challengeId}`,
  publicIp: (ip: string) => `public:${ip}`,
  refreshDevice: (licenseId: string, deviceId: string) => `refresh:${licenseId}:${deviceId}`,
  refreshProof: (challengeId: string) => `refresh-proof:${challengeId}`,
  adminUnauthorized: (ip: string) => `admin-unauth:${ip}`,
  adminAuthenticated: () => "admin-auth:operator",
} as const;
import { jsonError } from "../http/response";
