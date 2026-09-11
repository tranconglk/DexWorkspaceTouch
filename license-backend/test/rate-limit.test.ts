import { describe, expect, it, vi } from "vitest";
import { actorKey, allowed, rateLimited, RateLimiterUnavailableError, sourceIp } from "../src/security/rate-limit";
import { securityLog } from "../src/security/security-log";

describe("LIC-012 rate-limit boundary", () => {
  it("honors fake limiter outcomes and fails closed on missing/runtime failure", async () => {
    expect(await allowed(fake(true), "actor")).toBe(true);
    expect(await allowed(fake(false), "actor")).toBe(false);
    await expect(allowed(undefined, "actor")).rejects.toBeInstanceOf(RateLimiterUnavailableError);
    await expect(allowed({ limit: async () => { throw new Error("down"); } }, "actor"))
      .rejects.toBeInstanceOf(RateLimiterUnavailableError);
  });

  it("builds semantic actor keys without plaintext credentials or device secrets", () => {
    const licenseHash = "hmac-digest";
    const keys = [actorKey.activationLicense(licenseHash), actorKey.activationProof("challenge"),
      actorKey.refreshDevice("license-id", "device-id"), actorKey.refreshProof("refresh-challenge"),
      actorKey.publicIp("203.0.113.1"), actorKey.adminAuthenticated(), actorKey.adminUnauthorized("203.0.113.1")];
    expect(new Set(keys).size).toBe(keys.length);
    expect(keys.join("|")).not.toContain("APP-AAAA-BBBB-CCCC");
    expect(keys.join("|")).not.toContain("Bearer");
    expect(actorKey.activationLicense(licenseHash)).toContain(licenseHash);
  });

  it("returns the stable private 429 contract", async () => {
    const response = rateLimited();
    expect(response.status).toBe(429);
    expect(response.headers.get("Retry-After")).toBe("60");
    expect(response.headers.get("Cache-Control")).toBe("no-store");
    expect(response.headers.get("X-Content-Type-Options")).toBe("nosniff");
    expect(await response.json()).toEqual({ ok: false, error: {
      code: "RATE_LIMITED", message: "Too many requests. Try again later.",
    } });
  });

  it("uses Cloudflare source IP only ephemerally and safely bounds malformed input", () => {
    expect(sourceIp(new Request("https://example.test", { headers: { "CF-Connecting-IP": "203.0.113.1" } })))
      .toBe("203.0.113.1");
    expect(sourceIp(new Request("https://example.test"))).toBe("unavailable");
    expect(sourceIp(new Request("https://example.test", { headers: { "CF-Connecting-IP": "x".repeat(65) } })))
      .toBe("unavailable");
  });

  it("emits only the allowlisted security-observability fields", () => {
    const output = vi.spyOn(console, "log").mockImplementation(() => undefined);
    securityLog("rate_limited", "/v1/license/challenge", 429, "RATE_LIMITED", "request-id");

    expect(output).toHaveBeenCalledOnce();
    expect(JSON.parse(String(output.mock.calls[0]?.[0]))).toEqual({
      event: "rate_limited",
      route: "/v1/license/challenge",
      status: 429,
      code: "RATE_LIMITED",
      requestId: "request-id",
    });
    expect(output.mock.calls[0]?.[0]).not.toContain("licenseKey");
    expect(output.mock.calls[0]?.[0]).not.toContain("Authorization");
    output.mockRestore();
  });
});

function fake(success: boolean): RateLimit { return { limit: async () => ({ success }) }; }
