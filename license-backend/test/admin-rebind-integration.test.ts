import { applyD1Migrations, env, SELF } from "cloudflare:test";
import { beforeEach, describe, expect, it } from "vitest";
import { base64UrlDecode, base64UrlEncode } from "../src/license/base64url";
import type { ActivationRequest } from "../src/license/types";

const AUTH = { Authorization: "Bearer lic-009-test-admin-token-not-for-production" };

beforeEach(async () => { await applyD1Migrations(env.DB, env.TEST_MIGRATIONS); });

describe("admin reset and LIC-008 safe rebind", () => {
  it("retains Device A revoked and admits distinct Device B through a fresh proof", async () => {
    const createdResponse = await SELF.fetch("https://license.test/v1/admin/licenses", { method: "POST",
      headers: { ...AUTH, "Content-Type": "application/json" }, body: JSON.stringify({ maxDevices: 1, expiresAtEpochSeconds: null }) });
    const created = (await createdResponse.json() as any).data;
    const keyA = await generateDeviceKey(); const keyB = await generateDeviceKey();
    const requestA = await activationRequest(created.licenseKey, crypto.randomUUID(), keyA);
    const activationA = await activate(requestA, keyA.privateKey);
    expect(activationA.status).toBe(200);
    const deviceA = (await activationA.json() as any).data.deviceId as string;
    expect(await activeCount(created.licenseId)).toBe(1);

    const reset = await SELF.fetch(`https://license.test/v1/admin/licenses/${created.licenseId}/devices/${deviceA}/reset`,
      { method: "POST", headers: AUTH });
    expect(reset.status).toBe(200);
    expect(await activeCount(created.licenseId)).toBe(0);

    const oldRetry = await activate(requestA, keyA.privateKey);
    expect((await oldRetry.json() as any).error.code).toBe("DEVICE_MISMATCH");

    const requestB = await activationRequest(created.licenseKey, crypto.randomUUID(), keyB);
    const activationB = await activate(requestB, keyB.privateKey);
    expect(activationB.status).toBe(200);
    const deviceB = (await activationB.json() as any).data.deviceId as string;
    expect(deviceB).not.toBe(deviceA);
    expect(await activeCount(created.licenseId)).toBe(1);
    const rows = await env.DB.prepare("SELECT id, revoked_at FROM devices WHERE license_id = ? ORDER BY created_at, id")
      .bind(created.licenseId).all<{ id: string; revoked_at: number | null }>();
    expect(rows.results).toHaveLength(2);
    expect(rows.results.find((row) => row.id === deviceA)?.revoked_at).not.toBeNull();
    expect(rows.results.find((row) => row.id === deviceB)?.revoked_at).toBeNull();
  });

  it("a revoked license cannot issue a new challenge", async () => {
    const create = await SELF.fetch("https://license.test/v1/admin/licenses", { method: "POST",
      headers: { ...AUTH, "Content-Type": "application/json" }, body: JSON.stringify({ maxDevices: 1, expiresAtEpochSeconds: null }) });
    const license = (await create.json() as any).data;
    await SELF.fetch(`https://license.test/v1/admin/licenses/${license.licenseId}/revoke`, { method: "POST", headers: AUTH });
    const pair = await generateDeviceKey();
    const response = await SELF.fetch("https://license.test/v1/license/challenge", json(await activationRequest(license.licenseKey, crypto.randomUUID(), pair)));
    expect((await response.json() as any).error.code).toBe("LICENSE_REVOKED");
  });
});

async function activate(request: ActivationRequest, privateKey: CryptoKey): Promise<Response> {
  const challenge = await SELF.fetch("https://license.test/v1/license/challenge", json(request));
  const issued = (await challenge.json() as any).data;
  const signature = await signDer(privateKey, base64UrlDecode(issued.challenge));
  return SELF.fetch("https://license.test/v1/license/activate", json({ proofVersion: 1, challengeId: issued.challengeId, signature: base64UrlEncode(signature) }));
}
async function activationRequest(licenseKey: string, installationId: string, pair: CryptoKeyPair): Promise<ActivationRequest> {
  return { licenseKey, device: { installationId, deviceHash: "c".repeat(64), publicKey: toBase64(await crypto.subtle.exportKey("spki", pair.publicKey)) },
    application: { packageName: "com.trancong.dexworkspacetouch", versionName: "lic-009-test", versionCode: 9,
      signingCertificateSha256: "b".repeat(64) } };
}
async function activeCount(licenseId: string): Promise<number> {
  return await env.DB.prepare("SELECT COUNT(*) AS count FROM devices WHERE license_id = ? AND revoked_at IS NULL")
    .bind(licenseId).first<number>("count") ?? 0;
}
function json(value: unknown): RequestInit { return { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(value) }; }
async function generateDeviceKey(): Promise<CryptoKeyPair> { return crypto.subtle.generateKey({ name: "ECDSA", namedCurve: "P-256" }, true, ["sign", "verify"]) as Promise<CryptoKeyPair>; }
async function signDer(key: CryptoKey, payload: Uint8Array): Promise<Uint8Array> {
  const value = new Uint8Array(await crypto.subtle.sign({ name: "ECDSA", hash: "SHA-256" }, key, payload.slice().buffer as ArrayBuffer));
  const integer = (part: Uint8Array) => { let index = 0; while (index < part.length - 1 && part[index] === 0) index += 1;
    let normalized = part.slice(index); if ((normalized[0]! & 0x80) !== 0) normalized = Uint8Array.of(0, ...normalized);
    return Uint8Array.of(2, normalized.length, ...normalized); };
  const r = integer(value.slice(0, 32)); const s = integer(value.slice(32));
  return Uint8Array.of(0x30, r.length + s.length, ...r, ...s);
}
function toBase64(value: ArrayBuffer): string { return btoa(String.fromCharCode(...new Uint8Array(value))); }
