import { describe, expect, it } from "vitest";
import { formatError, parseCommand, readConfiguration, safeRetryAfter } from "../scripts/admin.mjs";

describe("admin CLI", () => {
  it("parses the supported commands without accepting token arguments", () => {
    expect(parseCommand("create", ["--max-devices", "2", "--expires-at", "2000000000"])).toMatchObject({ method: "POST", body: { maxDevices: 2, expiresAtEpochSeconds: 2_000_000_000 } });
    expect(parseCommand("list", [])).toMatchObject({ method: "GET", path: "/v1/admin/licenses" });
    expect(parseCommand("show", ["license/unsafe"])).toMatchObject({ path: "/v1/admin/licenses/license%2Funsafe" });
    expect(parseCommand("reset-device", ["license", "device"])).toMatchObject({ method: "POST" });
    expect(() => parseCommand("create", ["--token", "secret"])).toThrow("Unknown option");
  });

  it("surfaces 429 retry guidance without retrying or exposing credentials", () => {
    const output = formatError(429, { error: { code: "RATE_LIMITED", message: "Too many requests." } }, "request-2", "60");
    expect(output).toContain("RATE_LIMITED"); expect(output).toContain("Retry-After: 60"); expect(output).toContain("request-2");
    expect(output).not.toContain("admin-secret");
    expect(safeRetryAfter("60")).toBe("60"); expect(safeRetryAfter("bad")).toBeNull();
  });

  it("requires environment configuration and HTTPS outside localhost", () => {
    expect(() => readConfiguration({})).toThrow("required");
    expect(readConfiguration({ DWT_LICENSE_ADMIN_API_URL: "http://localhost:8787", DWT_LICENSE_ADMIN_TOKEN: "secret" })).toMatchObject({ token: "secret" });
    expect(() => readConfiguration({ DWT_LICENSE_ADMIN_API_URL: "http://example.com", DWT_LICENSE_ADMIN_TOKEN: "secret" })).toThrow("HTTPS");
  });

  it("surfaces stable server errors and request IDs without formatting the token", () => {
    const output = formatError(401, { error: { code: "ADMIN_UNAUTHORIZED", message: "Denied." } }, "request-1");
    expect(output).toBe("HTTP 401 ADMIN_UNAUTHORIZED: Denied. (request request-1)");
    expect(output).not.toContain("lic-009-test-admin-token-not-for-production");
  });
});
