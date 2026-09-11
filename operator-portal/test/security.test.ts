import { createSign, generateKeyPairSync } from "node:crypto";
import { afterEach, describe, expect, it, vi } from "vitest";
import { authenticateAccess, mutationAllowed } from "../src/security";
import type { Env } from "../src/types";

const pair = generateKeyPairSync("rsa", { modulusLength: 2048 });
const publicJwk = pair.publicKey.export({ format: "jwk" });
const env = {
  CF_ACCESS_TEAM_DOMAIN: "team.cloudflareaccess.com",
  CF_ACCESS_AUD: "portal-audience",
  OPERATOR_EMAIL_ALLOWLIST: "operator@example.com",
} as Env;

afterEach(() => vi.unstubAllGlobals());

describe("Cloudflare Access authentication", () => {
  it("accepts a signed, allowed Access identity", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => Response.json({ keys: [{ ...publicJwk, kid: "access-key" }] })));
    const token = jwt({ iss: "https://team.cloudflareaccess.com", aud: ["portal-audience"],
      exp: Math.floor(Date.now() / 1000) + 300, email: "operator@example.com" });
    const request = new Request("https://portal.test/", { headers: {
      "Cf-Access-Jwt-Assertion": token, "Cf-Access-Authenticated-User-Email": "operator@example.com",
    } });
    await expect(authenticateAccess(request, env)).resolves.toEqual({ email: "operator@example.com" });
  });

  it("rejects a browser-supplied email without a JWT", async () => {
    const request = new Request("https://portal.test/", { headers: { "Cf-Access-Authenticated-User-Email": "operator@example.com" } });
    await expect(authenticateAccess(request, env)).resolves.toBeNull();
  });

  it("requires exact same-origin POST mutation proof", () => {
    expect(mutationAllowed(new Request("https://portal.test/api/x", { method: "POST", headers: { Origin: "https://portal.test", "X-DWT-CSRF": "1" } }))).toBe(true);
    expect(mutationAllowed(new Request("https://portal.test/api/x", { method: "POST", headers: { Origin: "https://evil.test", "X-DWT-CSRF": "1" } }))).toBe(false);
    expect(mutationAllowed(new Request("https://portal.test/api/x"))).toBe(false);
  });
});

function jwt(claims: Record<string, unknown>): string {
  const header = encode(JSON.stringify({ alg: "RS256", kid: "access-key", typ: "JWT" }));
  const payload = encode(JSON.stringify(claims));
  const signature = createSign("RSA-SHA256").update(`${header}.${payload}`).sign(pair.privateKey);
  return `${header}.${payload}.${Buffer.from(signature).toString("base64url")}`;
}
function encode(value: string): string { return Buffer.from(value).toString("base64url"); }
