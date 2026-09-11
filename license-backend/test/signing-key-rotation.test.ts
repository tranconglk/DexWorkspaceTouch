import { describe, expect, it } from "vitest";
import { LicenseTokenSigner } from "../src/license/license-token-signer";
import { LicenseTokenVerifier } from "../src/license/license-token-verifier";
import { TrustedLicenseSigningKeys } from "../src/license/trusted-signing-keys";
import type { LicenseTokenClaims } from "../src/license/license-token";

const NOW = 1_700_000_000;
const CLAIMS: LicenseTokenClaims = { tokenVersion: 1, licenseId: "license", deviceId: "device",
  installationId: "installation", deviceHash: "hash", packageName: "com.example",
  issuedAtEpochSeconds: NOW, expiresAtEpochSeconds: NOW + 100, offlineValidUntilEpochSeconds: NOW + 100 };

describe("LIC-013 signing-key rotation readiness", () => {
  it("supports overlap, signer cutover, old-token verification, and exact unknown kid", async () => {
    const v1 = await pair(); const v2 = await pair();
    const tokenV1 = await (await LicenseTokenSigner.create(v1.privatePem, "license-signing-v1")).sign(CLAIMS);
    const overlap = verifier(await registry(entry("license-signing-v1", v1.spki), entry("license-signing-v2", v2.spki)));
    expect((await overlap.verify(tokenV1, NOW)).ok).toBe(true);
    const tokenV2 = await (await LicenseTokenSigner.create(v2.privatePem, "license-signing-v2")).sign(CLAIMS);
    expect((await overlap.verify(tokenV2, NOW)).ok).toBe(true);
    expect((await verifier(await registry(entry("license-signing-v1", v1.spki))).verify(tokenV2, NOW)))
      .toEqual({ ok: false, code: "UNKNOWN_KEY" });
    const aliasToken = await (await LicenseTokenSigner.create(v1.privatePem, "v1")).sign(CLAIMS);
    expect(await overlap.verify(aliasToken, NOW)).toEqual({ ok: false, code: "UNKNOWN_KEY" });
    expect((await verifier(await registry(entry("license-signing-v2", v2.spki))).verify(tokenV1, NOW)))
      .toEqual({ ok: false, code: "UNKNOWN_KEY" });
  });

  it("rejects a token whose declared kid does not match the signing private key", async () => {
    const v1 = await pair(); const v2 = await pair();
    const forged = await (await LicenseTokenSigner.create(v2.privatePem, "license-signing-v1")).sign(CLAIMS);
    expect(await verifier(await registry(entry("license-signing-v1", v1.spki))).verify(forged, NOW))
      .toEqual({ ok: false, code: "TOKEN_INVALID" });
  });

  it("strictly rejects empty, malformed, duplicate, bad kid/algorithm/Base64 and undersized RSA", async () => {
    const good = await pair(); const small = await pair(1024);
    await expect(TrustedLicenseSigningKeys.fromJson("[]")).rejects.toThrow("empty");
    await expect(TrustedLicenseSigningKeys.fromJson("bad")).rejects.toThrow("malformed");
    await expect(registry(entry("v1", good.spki), entry("v1", good.spki))).rejects.toThrow("Duplicate");
    await expect(registry({ ...entry("bad kid", good.spki) })).rejects.toThrow("invalid");
    await expect(registry({ ...entry("v1", good.spki), algorithm: "ES256" })).rejects.toThrow("invalid");
    await expect(registry({ ...entry("v1", good.spki), spkiBase64: "%%%=" })).rejects.toThrow("Base64");
    await expect(registry(entry("small", small.spki))).rejects.toThrow("RSA policy");
  });

  it("produces canonical SHA-256 SPKI fingerprints", async () => {
    const value = await pair(); const keys = await registry(entry("v1", value.spki));
    const expected = Array.from(new Uint8Array(await crypto.subtle.digest("SHA-256", value.spki)),
      (byte) => byte.toString(16).padStart(2, "0")).join("");
    expect(keys.fingerprints.get("v1")).toBe(expected);
  });
});

function verifier(keys: TrustedLicenseSigningKeys) { return LicenseTokenVerifier.fromRegistry(keys); }
function entry(kid: string, spki: ArrayBuffer) {
  return { kid, algorithm: "RS256", spkiBase64: btoa(String.fromCharCode(...new Uint8Array(spki))) };
}
function registry(...entries: unknown[]) { return TrustedLicenseSigningKeys.fromJson(JSON.stringify(entries)); }
async function pair(modulusLength = 2048) {
  const keys = await crypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength,
    publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]);
  const spki = await crypto.subtle.exportKey("spki", keys.publicKey);
  const pkcs8 = await crypto.subtle.exportKey("pkcs8", keys.privateKey);
  const body = btoa(String.fromCharCode(...new Uint8Array(pkcs8)));
  return { spki, privatePem: `-----BEGIN PRIVATE KEY-----\n${body}\n-----END PRIVATE KEY-----` };
}
