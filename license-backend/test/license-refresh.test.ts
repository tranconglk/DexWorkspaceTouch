import { applyD1Migrations, env, SELF } from "cloudflare:test";
import { beforeAll, beforeEach, describe, expect, it } from "vitest";
import { base64UrlDecode, base64UrlEncode } from "../src/license/base64url";
import { hashLicenseKey } from "../src/license/license-key-hash";
import { verifyTestToken } from "./token-verifier";
import { route } from "../src/http/router";
import type { Env } from "../src/env";
import { LicenseTokenVerifier } from "../src/license/license-token-verifier";
import { TrustedLicenseSigningKeys } from "../src/license/trusted-signing-keys";

let deviceKey: CryptoKeyPair;
let otherKey: CryptoKeyPair;
beforeAll(async () => { deviceKey = await keyPair(); otherKey = await keyPair(); });
beforeEach(async () => { await applyD1Migrations(env.DB, env.TEST_MIGRATIONS); });

describe("LIC-011 silent refresh", () => {
  it("verifies bearer, issues exactly 32 bytes, proves ownership and renews the same binding", async () => {
    const active = await activate("APP-REFR-ESH0-0001", deviceKey);
    const issued = await refreshChallenge(active.token);
    expect(base64UrlDecode(issued.challenge as string)).toHaveLength(32);
    expect(issued.proofVersion).toBe(1);
    expect(await count("SELECT COUNT(*) AS count FROM license_events WHERE event_type = 'TOKEN_REFRESHED'")).toBe(0);
    const refreshed = await refreshProof(issued, deviceKey.privateKey);
    expect(refreshed.status).toBe(200);
    const data = await success(refreshed);
    const claims = await verifyTestToken(data.licenseToken as string, env.TEST_LICENSE_SIGNING_PUBLIC_KEY, env.LICENSE_SIGNING_KEY_ID);
    expect(claims).toMatchObject({ licenseId: active.licenseId, deviceId: active.deviceId,
      installationId: active.installationId, deviceHash: "a".repeat(64), packageName: PACKAGE });
    expect(await count("SELECT COUNT(*) AS count FROM license_events WHERE event_type = 'TOKEN_REFRESHED'")).toBe(1);
  });

  it("rejects missing, malformed, tampered, unknown-kid and expired bearer tokens", async () => {
    const active = await activate("APP-REFR-ESH0-0002", deviceKey);
    await error(await SELF.fetch(URL + "/v1/license/refresh/challenge", json({ packageName: PACKAGE, signingCertificateSha256: CERT })), "TOKEN_INVALID");
    await error(await challengeRequest("bad", PACKAGE, CERT), "TOKEN_INVALID");
    const parts = active.token.split(".");
    await error(await challengeRequest(`${parts[0]}.${parts[1]}x.${parts[2]}`, PACKAGE, CERT), "TOKEN_INVALID");
    const unknownHeader = base64UrlEncode(new TextEncoder().encode(JSON.stringify({ alg: "RS256", typ: "DWT-LICENSE", kid: "unknown" })));
    await error(await challengeRequest(`${unknownHeader}.${parts[1]}.${parts[2]}`, PACKAGE, CERT), "UNKNOWN_KEY");
    const expired = await activate("APP-REFR-ESH0-0003", deviceKey, Math.floor(Date.now() / 1000) + 60);
    await env.DB.prepare("UPDATE licenses SET expires_at = ? WHERE id = ?").bind(Math.floor(Date.now() / 1000), expired.licenseId).run();
    await error(await challengeRequest(expired.token, PACKAGE, CERT), "LICENSE_EXPIRED");
  });

  it("rejects revoked licenses, reset devices and unauthorized application without creating a challenge", async () => {
    const initialChallenges = await count("SELECT COUNT(*) AS count FROM license_refresh_challenges");
    const revoked = await activate("APP-REFR-ESH0-0004", deviceKey);
    await env.DB.prepare("UPDATE licenses SET status = 'REVOKED', revoked_at = ? WHERE id = ?").bind(Math.floor(Date.now() / 1000), revoked.licenseId).run();
    await error(await challengeRequest(revoked.token, PACKAGE, CERT), "LICENSE_REVOKED");
    const reset = await activate("APP-REFR-ESH0-0005", deviceKey);
    await env.DB.prepare("UPDATE devices SET revoked_at = ? WHERE id = ?").bind(Math.floor(Date.now() / 1000), reset.deviceId).run();
    await error(await challengeRequest(reset.token, PACKAGE, CERT), "DEVICE_REVOKED");
    const denied = await activate("APP-REFR-ESH0-0006", deviceKey);
    await error(await challengeRequest(denied.token, PACKAGE, "c".repeat(64)), "APPLICATION_NOT_ALLOWED");
    expect(await count("SELECT COUNT(*) AS count FROM license_refresh_challenges")).toBe(initialChallenges);
  });

  it("rejects wrong proof, expiration and sequential/concurrent replay", async () => {
    const active = await activate("APP-REFR-ESH0-0007", deviceKey);
    const wrong = await refreshChallenge(active.token);
    await error(await refreshProof(wrong, otherKey.privateKey), "PROOF_INVALID");
    const expired = await refreshChallenge(active.token);
    const boundary = Math.floor(Date.now() / 1000);
    await env.DB.prepare("UPDATE license_refresh_challenges SET created_at = ?, expires_at = ? WHERE id = ?")
      .bind(boundary - 1, boundary, expired.challengeId).run();
    await error(await refreshProof(expired, deviceKey.privateKey), "CHALLENGE_EXPIRED");
    const replay = await refreshChallenge(active.token);
    const signature = base64UrlEncode(await signDer(deviceKey.privateKey, base64UrlDecode(replay.challenge as string)));
    const [one, two] = await Promise.all([submitRefresh(replay.challengeId as string, signature), submitRefresh(replay.challengeId as string, signature)]);
    expect([one.status, two.status].sort()).toEqual([200, 409]);
    await error(await submitRefresh(replay.challengeId as string, signature), "CHALLENGE_USED");
  });

  it("re-checks license and device after challenge issuance", async () => {
    const revoked = await activate("APP-REFR-ESH0-0008", deviceKey);
    const revokedChallenge = await refreshChallenge(revoked.token);
    await env.DB.prepare("UPDATE licenses SET status = 'REVOKED' WHERE id = ?").bind(revoked.licenseId).run();
    await error(await refreshProof(revokedChallenge, deviceKey.privateKey), "LICENSE_REVOKED");
    const reset = await activate("APP-REFR-ESH0-0009", deviceKey);
    const resetChallenge = await refreshChallenge(reset.token);
    await env.DB.prepare("UPDATE devices SET revoked_at = ? WHERE id = ?").bind(Math.floor(Date.now() / 1000), reset.deviceId).run();
    await error(await refreshProof(resetChallenge, deviceKey.privateKey), "DEVICE_REVOKED");
  });

  it("refreshes an old v1 bearer into a v2 token during trusted-key overlap", async () => {
    const active = await activate("APP-REFR-ESH0-0010", deviceKey);
    const v2 = await rsaPair();
    const v1Spki = pemBody(env.LICENSE_SIGNING_PUBLIC_KEY_V1);
    const registryJson = JSON.stringify([
      { kid: env.LICENSE_SIGNING_KEY_ID, algorithm: "RS256", spkiBase64: v1Spki },
      { kid: "license-signing-v2", algorithm: "RS256", spkiBase64: v2.spkiBase64 },
    ]);
    const rotated = { ...env, LICENSE_SIGNING_KEY_ID: "license-signing-v2",
      LICENSE_SIGNING_PRIVATE_KEY: v2.privatePem, LICENSE_TRUSTED_PUBLIC_KEYS_JSON: registryJson } as unknown as Env;
    const issued = await success(await route(new Request(URL + "/v1/license/refresh/challenge",
      json({ packageName: PACKAGE, signingCertificateSha256: CERT }, active.token)), rotated, crypto.randomUUID()));
    const signature = base64UrlEncode(await signDer(deviceKey.privateKey, base64UrlDecode(issued.challenge as string)));
    const refreshed = await success(await route(new Request(URL + "/v1/license/refresh",
      json({ proofVersion: 1, challengeId: issued.challengeId, signature })), rotated, crypto.randomUUID()));
    const trusted = await TrustedLicenseSigningKeys.fromJson(registryJson);
    const verifier = LicenseTokenVerifier.fromRegistry(trusted);
    expect((await verifier.verify(active.token, Math.floor(Date.now() / 1000))).ok).toBe(true);
    expect((await verifier.verify(refreshed.licenseToken as string, Math.floor(Date.now() / 1000))).ok).toBe(true);
    const header = JSON.parse(new TextDecoder().decode(base64UrlDecode((refreshed.licenseToken as string).split(".")[0]!)));
    expect(header.kid).toBe("license-signing-v2");
  });
});

const URL = "https://license.test";
const PACKAGE = "com.trancong.dexworkspacetouch";
const CERT = "b".repeat(64);
async function activate(key: string, pair: CryptoKeyPair, expires: number | null = null) {
  const licenseId = crypto.randomUUID(); const installationId = crypto.randomUUID();
  await env.DB.prepare("INSERT INTO licenses (id,license_key_hash,status,max_devices,created_at,expires_at) VALUES (?,?,?,?,?,?)")
    .bind(licenseId, await hashLicenseKey(key, env.LICENSE_KEY_PEPPER), "NEW", 1, 1_700_000_000, expires).run();
  const request = { licenseKey: key, device: { installationId, deviceHash: "a".repeat(64),
    publicKey: toBase64(await crypto.subtle.exportKey("spki", pair.publicKey)) },
    application: { packageName: PACKAGE, versionName: "test", versionCode: 1, signingCertificateSha256: CERT } };
  const challenge = await success(await SELF.fetch(URL + "/v1/license/challenge", json(request)));
  const response = await SELF.fetch(URL + "/v1/license/activate", json({ proofVersion: 1, challengeId: challenge.challengeId,
    signature: base64UrlEncode(await signDer(pair.privateKey, base64UrlDecode(challenge.challenge as string))) }));
  const data = await success(response);
  return { token: data.licenseToken as string, licenseId, deviceId: data.deviceId as string, installationId };
}
async function refreshChallenge(token: string) { return success(await challengeRequest(token, PACKAGE, CERT)); }
function challengeRequest(token: string, packageName: string, signingCertificateSha256: string) {
  return SELF.fetch(URL + "/v1/license/refresh/challenge", json({ packageName, signingCertificateSha256 }, token));
}
async function refreshProof(issued: Record<string, unknown>, key: CryptoKey) {
  return submitRefresh(issued.challengeId as string,
    base64UrlEncode(await signDer(key, base64UrlDecode(issued.challenge as string))));
}
function submitRefresh(challengeId: string, signature: string) {
  return SELF.fetch(URL + "/v1/license/refresh", json({ proofVersion: 1, challengeId, signature }));
}
function json(body: unknown, token?: string): RequestInit { return { method: "POST", headers: {
  "Content-Type": "application/json", ...(token === undefined ? {} : { Authorization: `Bearer ${token}` }) }, body: JSON.stringify(body) }; }
async function success(response: Response) { const value = await response.json<{ok:boolean;data:Record<string,unknown>}>(); expect(value.ok).toBe(true); return value.data; }
async function error(response: Response, code: string) { const value = await response.json<{ok:boolean;error:{code:string}}>(); expect(value.ok).toBe(false); expect(value.error.code).toBe(code); }
async function count(sql: string) { return (await env.DB.prepare(sql).first<{count:number}>())?.count ?? 0; }
async function keyPair() { return crypto.subtle.generateKey({ name: "ECDSA", namedCurve: "P-256" }, true, ["sign", "verify"]) as Promise<CryptoKeyPair>; }
async function signDer(key: CryptoKey, payload: Uint8Array) { const raw = new Uint8Array(await crypto.subtle.sign({ name: "ECDSA", hash: "SHA-256" }, key, payload.slice().buffer as ArrayBuffer)); return p1363ToDer(raw); }
function p1363ToDer(value: Uint8Array) { const integer = (v: Uint8Array) => { let i=0; while(i<v.length-1&&v[i]===0)i++; let x=v.slice(i); if((x[0]!&0x80)!==0)x=Uint8Array.of(0,...x); return Uint8Array.of(2,x.length,...x); }; const r=integer(value.slice(0,32)),s=integer(value.slice(32)); return Uint8Array.of(0x30,r.length+s.length,...r,...s); }
function toBase64(value: ArrayBuffer) { return btoa(String.fromCharCode(...new Uint8Array(value))); }
function pemBody(value: string) { return value.replace(/-----BEGIN PUBLIC KEY-----|-----END PUBLIC KEY-----|\s/gu, ""); }
async function rsaPair() {
  const keys = await crypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048,
    publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]);
  const spkiBase64 = toBase64(await crypto.subtle.exportKey("spki", keys.publicKey));
  const privateBody = toBase64(await crypto.subtle.exportKey("pkcs8", keys.privateKey));
  return { spkiBase64, privatePem: `-----BEGIN PRIVATE KEY-----\n${privateBody}\n-----END PRIVATE KEY-----` };
}
