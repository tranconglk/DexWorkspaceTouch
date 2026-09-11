import { env } from "cloudflare:test";
import { describe, expect, it } from "vitest";
import { base64UrlEncodeUtf8 } from "../src/license/base64url";
import { createLicenseTokenClaims, OFFLINE_GRACE_SECONDS, type LicenseTokenClaims } from "../src/license/license-token";
import { LicenseTokenSigner } from "../src/license/license-token-signer";
import { encodeJsonSegment, verifyTestToken } from "./token-verifier";

const KID = "license-signing-v1";
const claims: LicenseTokenClaims = createLicenseTokenClaims({
  licenseId: "license-1", deviceId: "device-1", installationId: "00000000-0000-4000-8000-000000000001",
  deviceHash: "a".repeat(64), packageName: "com.trancong.dexworkspacetouch",
  issuedAtEpochSeconds: 1_000_000, licenseExpiresAtEpochSeconds: null,
});

describe("RS256 license token", () => {
  it("verifies independently with only the SPKI public key", async () => {
    const token = await tokenFor(claims);
    await expect(verifyTestToken(token, env.TEST_LICENSE_SIGNING_PUBLIC_KEY, KID)).resolves.toEqual(claims);
    expect(token.split(".")).toHaveLength(3);
    expect(token).not.toContain("=");
  });

  it("has a fixed trusted header", async () => {
    const headerSegment = (await tokenFor(claims)).split(".")[0]!;
    const base64 = headerSegment.replaceAll("-", "+").replaceAll("_", "/").padEnd(Math.ceil(headerSegment.length / 4) * 4, "=");
    expect(JSON.parse(new TextDecoder().decode(Uint8Array.from(atob(base64), (c) => c.charCodeAt(0))))).toEqual({ alg: "RS256", typ: "DWT-LICENSE", kid: KID });
  });

  it("rejects payload, header, and signature tampering", async () => {
    const token = await tokenFor(claims);
    const [header, payload, signature] = token.split(".") as [string, string, string];
    const changedPayload = encodeJsonSegment({ ...claims, deviceHash: "b".repeat(64) });
    const changedHeader = encodeJsonSegment({ alg: "RS256", typ: "DWT-LICENSE", kid: "other" });
    const changedSignature = `${signature.startsWith("A") ? "B" : "A"}${signature.slice(1)}`;
    await expect(verifyTestToken(`${header}.${changedPayload}.${signature}`, env.TEST_LICENSE_SIGNING_PUBLIC_KEY, KID)).rejects.toThrow();
    await expect(verifyTestToken(`${changedHeader}.${payload}.${signature}`, env.TEST_LICENSE_SIGNING_PUBLIC_KEY, KID)).rejects.toThrow();
    await expect(verifyTestToken(`${header}.${payload}.${changedSignature}`, env.TEST_LICENSE_SIGNING_PUBLIC_KEY, KID)).rejects.toThrow();
  });

  it("rejects a different RSA public key", async () => {
    const other = await crypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]);
    const spki = await crypto.subtle.exportKey("spki", other.publicKey);
    const pem = `-----BEGIN PUBLIC KEY-----\n${btoa(String.fromCharCode(...new Uint8Array(spki)))}\n-----END PUBLIC KEY-----`;
    await expect(verifyTestToken(await tokenFor(claims), pem, KID)).rejects.toThrow();
  });

  it("rejects malformed token, wrong alg, and wrong kid", async () => {
    await expect(verifyTestToken("only.two", env.TEST_LICENSE_SIGNING_PUBLIC_KEY, KID)).rejects.toThrow();
    const token = await tokenFor(claims);
    const [, payload, signature] = token.split(".") as [string, string, string];
    const none = base64UrlEncodeUtf8(JSON.stringify({ alg: "none", typ: "DWT-LICENSE", kid: KID }));
    await expect(verifyTestToken(`${none}.${payload}.${signature}`, env.TEST_LICENSE_SIGNING_PUBLIC_KEY, KID)).rejects.toThrow();
    await expect(verifyTestToken(token, env.TEST_LICENSE_SIGNING_PUBLIC_KEY, "license-signing-v2")).rejects.toThrow();
  });

  it("maps Android claims and applies exactly seven days", () => {
    expect(claims).toMatchObject({ tokenVersion: 1, licenseId: "license-1", deviceId: "device-1", installationId: "00000000-0000-4000-8000-000000000001", deviceHash: "a".repeat(64), packageName: "com.trancong.dexworkspacetouch", issuedAtEpochSeconds: 1_000_000, expiresAtEpochSeconds: 1_000_000 + OFFLINE_GRACE_SECONDS, offlineValidUntilEpochSeconds: 1_000_000 + OFFLINE_GRACE_SECONDS });
    expect(OFFLINE_GRACE_SECONDS).toBe(604_800);
  });

  it("clamps validity to earlier license expiration", () => {
    const value = createLicenseTokenClaims({ licenseId: "l", deviceId: "d", installationId: "i", deviceHash: "a".repeat(64), packageName: "com.trancong.dexworkspacetouch", issuedAtEpochSeconds: 1_000_000, licenseExpiresAtEpochSeconds: 1_000_100 });
    expect(value.expiresAtEpochSeconds).toBe(1_000_100);
    expect(value.offlineValidUntilEpochSeconds).toBe(1_000_100);
  });

  it("issues a fresh token window from a later successful server time", async () => {
    const laterClaims = createLicenseTokenClaims({
      licenseId: claims.licenseId, deviceId: claims.deviceId,
      installationId: claims.installationId, deviceHash: claims.deviceHash,
      packageName: claims.packageName, issuedAtEpochSeconds: claims.issuedAtEpochSeconds + 1,
      licenseExpiresAtEpochSeconds: null,
    });
    expect(laterClaims.issuedAtEpochSeconds).toBe(claims.issuedAtEpochSeconds + 1);
    expect(laterClaims.offlineValidUntilEpochSeconds).toBe(claims.offlineValidUntilEpochSeconds + 1);
    expect(await tokenFor(laterClaims)).not.toBe(await tokenFor(claims));
  });

  it("contains no key, hash, pepper, or private/public key", async () => {
    const token = await tokenFor(claims);
    expect(token).not.toContain("APP-7K3M-9QTX-2PL8");
    expect(token).not.toContain(env.LICENSE_KEY_PEPPER);
    expect(token).not.toContain(env.LICENSE_SIGNING_PRIVATE_KEY);
    expect(token).not.toContain(env.TEST_LICENSE_SIGNING_PUBLIC_KEY);
  });

  it("fails closed for missing, malformed, and undersized private keys", async () => {
    await expect(LicenseTokenSigner.create("", KID)).rejects.toThrow();
    await expect(LicenseTokenSigner.create("not-pem", KID)).rejects.toThrow();
    const small = await crypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 1024, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]);
    const pkcs8 = await crypto.subtle.exportKey("pkcs8", small.privateKey);
    const pem = `-----BEGIN PRIVATE KEY-----\n${btoa(String.fromCharCode(...new Uint8Array(pkcs8)))}\n-----END PRIVATE KEY-----`;
    await expect(LicenseTokenSigner.create(pem, KID)).rejects.toThrow("policy");
  });
});

async function tokenFor(value: LicenseTokenClaims): Promise<string> {
  return (await LicenseTokenSigner.create(env.LICENSE_SIGNING_PRIVATE_KEY, KID)).sign(value);
}
