import { env } from "cloudflare:test";
import { describe, expect, it } from "vitest";
import { base64UrlEncodeUtf8 } from "../src/license/base64url";
import { LicenseTokenSigner } from "../src/license/license-token-signer";
import { LicenseTokenVerifier } from "../src/license/license-token-verifier";
import type { LicenseTokenClaims } from "../src/license/license-token";

const NOW = 1_700_000_000;
const claims: LicenseTokenClaims = { tokenVersion: 1, licenseId: "license", deviceId: "device",
  installationId: "installation", deviceHash: "a".repeat(64), packageName: "com.example.app",
  issuedAtEpochSeconds: NOW - 1, expiresAtEpochSeconds: NOW + 100, offlineValidUntilEpochSeconds: NOW + 100 };

describe("server license token verifier", () => {
  it("accepts current configured kid and rejects an unknown kid", async () => {
    const token = await signed(claims);
    expect((await verifier().then((value) => value.verify(token, NOW))).ok).toBe(true);
    const parts = token.split(".");
    const header = base64UrlEncodeUtf8(JSON.stringify({ alg: "RS256", typ: "DWT-LICENSE", kid: "v1" }));
    expect(await verifier().then((value) => value.verify(`${header}.${parts[1]}.${parts[2]}`, NOW)))
      .toEqual({ ok: false, code: "UNKNOWN_KEY" });
  });

  it("rejects tampering, malformed/header policy, expired and missing claims", async () => {
    const token = await signed(claims); const parts = token.split(".");
    expect((await verifier().then((value) => value.verify(`${parts[0]}.${parts[1]}x.${parts[2]}`, NOW))).ok).toBe(false);
    expect(await verifier().then((value) => value.verify("bad", NOW))).toEqual({ ok: false, code: "TOKEN_INVALID" });
    const expired = await signed({ ...claims, expiresAtEpochSeconds: NOW });
    expect(await verifier().then((value) => value.verify(expired, NOW)))
      .toEqual({ ok: false, code: "TOKEN_EXPIRED" });
    const incomplete = { ...claims } as Record<string, unknown>; delete incomplete.deviceId;
    const incompleteToken = await signed(incomplete as unknown as LicenseTokenClaims);
    expect((await verifier().then((value) => value.verify(incompleteToken, NOW))).ok).toBe(false);
  });
});

async function signed(value: LicenseTokenClaims) {
  return (await LicenseTokenSigner.create(env.LICENSE_SIGNING_PRIVATE_KEY, env.LICENSE_SIGNING_KEY_ID)).sign(value);
}
async function verifier() {
  return LicenseTokenVerifier.create(new Map([[env.LICENSE_SIGNING_KEY_ID, env.LICENSE_SIGNING_PUBLIC_KEY_V1]]));
}
