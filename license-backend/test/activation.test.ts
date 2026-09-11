import { applyD1Migrations, env, SELF } from "cloudflare:test";
import { beforeAll, beforeEach, describe, expect, it } from "vitest";
import { hashLicenseKey } from "../src/license/license-key-hash";
import { base64UrlDecode, base64UrlEncode } from "../src/license/base64url";
import type { ActivationRequest } from "../src/license/types";
import { verifyTestToken } from "./token-verifier";

const NOW = 1_700_000_000;
let deviceKey: CryptoKeyPair;
let otherKey: CryptoKeyPair;
let rsaPublicKeyBase64: string;

beforeAll(async () => {
  deviceKey = await generateDeviceKey(); otherKey = await generateDeviceKey();
  const rsa = await crypto.subtle.generateKey(
    { name: "RSA-PSS", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"],
  );
  rsaPublicKeyBase64 = toBase64(await crypto.subtle.exportKey("spki", rsa.publicKey));
});
beforeEach(async () => { await applyD1Migrations(env.DB, env.TEST_MIGRATIONS); });

describe("LIC-008 challenge issuance", () => {
  it("issues unique 32-byte challenges without activating or exposing secrets", async () => {
    const key = "APP-AAAA-BBBB-0001"; const licenseId = await seedLicense(key, 1);
    const request = await requestFor(key, crypto.randomUUID(), deviceKey);
    const first = await challenge(request); const second = await challenge(request);
    expect(first.status).toBe(200); expect(second.status).toBe(200);
    const one = await successData(first); const two = await successData(second);
    expect(base64UrlDecode(one.challenge as string)).toHaveLength(32);
    expect(one.challenge).not.toBe(two.challenge); expect(one.proofVersion).toBe(1);
    expect(one.expiresAt).toBe((one.serverTime as number) + 120);
    expect(await scalar("SELECT COUNT(*) AS count FROM devices WHERE license_id = ?", licenseId)).toBe(0);
    expect(JSON.stringify(one)).not.toContain("licenseToken");
    const rows = await env.DB.prepare("SELECT * FROM license_challenges WHERE license_id = ?").bind(licenseId).all<Record<string, unknown>>();
    expect(JSON.stringify(rows.results)).not.toContain(key);
  });

  it("rejects invalid, revoked, expired licenses and invalid device public keys", async () => {
    const revoked = "APP-AAAA-BBBB-0002"; const expired = "APP-AAAA-BBBB-0003";
    await seedLicense(revoked, 1, "REVOKED"); await seedLicense(expired, 1, "ACTIVE", NOW + 1);
    await expectError(await challenge(await requestFor("APP-AAAA-BBBB-9999", crypto.randomUUID(), deviceKey)), "LICENSE_INVALID");
    await expectError(await challenge(await requestFor(revoked, crypto.randomUUID(), deviceKey)), "LICENSE_REVOKED");
    await expectError(await challenge(await requestFor(expired, crypto.randomUUID(), deviceKey)), "LICENSE_EXPIRED");
    const request = await requestFor(revoked, crypto.randomUUID(), deviceKey);
    await expectError(await challenge({ ...request, device: { ...request.device, publicKey: "invalid" } }), "INVALID_DEVICE_PUBLIC_KEY");
    await expectError(await challenge({ ...request, device: { ...request.device, publicKey: rsaPublicKeyBase64 } }), "INVALID_DEVICE_PUBLIC_KEY");
  });

  it("rejects unauthorized application identities without creating a challenge", async () => {
    const key = "APP-AAAA-BBBB-0004"; const licenseId = await seedLicense(key, 1);
    const valid = await requestFor(key, crypto.randomUUID(), deviceKey);
    const cases = [
      { ...valid, application: { ...valid.application, packageName: "com.example.resigned" } },
      { ...valid, application: { ...valid.application, signingCertificateSha256: undefined } },
      { ...valid, application: { ...valid.application, signingCertificateSha256: "a".repeat(64) } },
    ];
    for (const request of cases) await expectError(await challenge(request), "APPLICATION_NOT_ALLOWED");
    expect(await scalar("SELECT COUNT(*) AS count FROM license_challenges WHERE license_id = ?", licenseId)).toBe(0);
    expect(await scalar("SELECT COUNT(*) AS count FROM devices WHERE license_id = ?", licenseId)).toBe(0);
  });

  it("rejects malformed certificate input before challenge creation", async () => {
    const key = "APP-AAAA-BBBB-0005"; const licenseId = await seedLicense(key, 1);
    const valid = await requestFor(key, crypto.randomUUID(), deviceKey);
    await expectError(await challenge({ ...valid, application: { ...valid.application, signingCertificateSha256: "bad" } }), "INVALID_REQUEST");
    expect(await scalar("SELECT COUNT(*) AS count FROM license_challenges WHERE license_id = ?", licenseId)).toBe(0);
  });
});

describe("LIC-008 proof completion", () => {
  it("verifies Android-style DER proof, binds once, and issues the unchanged RS256 token", async () => {
    const key = "APP-AAAA-BBBB-0010"; const licenseId = await seedLicense(key, 1);
    const request = await requestFor(key, crypto.randomUUID(), deviceKey);
    const issued = await successData(await challenge(request));
    const response = await proof(issued, deviceKey.privateKey);
    expect(response.status).toBe(200);
    const data = await successData(response);
    const claims = await verifyTestToken(data.licenseToken as string, env.TEST_LICENSE_SIGNING_PUBLIC_KEY, env.LICENSE_SIGNING_KEY_ID);
    expect(claims).toMatchObject({ licenseId, installationId: request.device.installationId,
      deviceHash: request.device.deviceHash, packageName: request.application.packageName, tokenVersion: 1 });
    expect(await scalar("SELECT COUNT(*) AS count FROM devices WHERE license_id = ?", licenseId)).toBe(1);
  });

  it("rejects wrong, changed, malformed, unknown, expired, and replayed proofs without extra mutation", async () => {
    const key = "APP-AAAA-BBBB-0011"; const licenseId = await seedLicense(key, 1);
    const issued = await successData(await challenge(await requestFor(key, crypto.randomUUID(), deviceKey)));
    await expectError(await proof(issued, otherKey.privateKey), "PROOF_INVALID");
    const validDer = await signDer(deviceKey.privateKey, base64UrlDecode(issued.challenge as string));
    const changed = validDer.slice(); changed[changed.length - 1] = changed[changed.length - 1]! ^ 1;
    await expectError(await submitProof(issued.challengeId as string, base64UrlEncode(changed)), "PROOF_INVALID");
    await expectError(await submitProof(issued.challengeId as string, "bad"), "PROOF_INVALID");
    await expectError(await submitProof(crypto.randomUUID(), base64UrlEncode(validDer)), "CHALLENGE_INVALID");
    expect(await scalar("SELECT COUNT(*) AS count FROM devices WHERE license_id = ?", licenseId)).toBe(0);
    const success = await proof(issued, deviceKey.privateKey); expect(success.status).toBe(200);
    await expectError(await proof(issued, deviceKey.privateKey), "CHALLENGE_USED");
    const boundary = Number(issued.serverTime);
    await env.DB.prepare("UPDATE license_challenges SET consumed_at = NULL, created_at = ?, expires_at = ? WHERE id = ?")
      .bind(boundary - 1, boundary, issued.challengeId).run();
    await expectError(await proof(issued, deviceKey.privateKey), "CHALLENGE_EXPIRED");
  });

  it("rejects the old direct activation request and creates no device", async () => {
    const key = "APP-AAAA-BBBB-0012"; const licenseId = await seedLicense(key, 1);
    const response = await SELF.fetch("https://license.test/v1/license/activate", jsonRequest(await requestFor(key, crypto.randomUUID(), deviceKey)));
    expect(response.status).toBe(400); await expectError(response, "INVALID_REQUEST");
    expect(await scalar("SELECT COUNT(*) AS count FROM devices WHERE license_id = ?", licenseId)).toBe(0);
  });

  it("atomically consumes concurrent replay exactly once", async () => {
    const key = "APP-AAAA-BBBB-0013"; const licenseId = await seedLicense(key, 1);
    const issued = await successData(await challenge(await requestFor(key, crypto.randomUUID(), deviceKey)));
    const signature = base64UrlEncode(await signDer(deviceKey.privateKey, base64UrlDecode(issued.challenge as string)));
    const [a, b] = await Promise.all([submitProof(issued.challengeId as string, signature), submitProof(issued.challengeId as string, signature)]);
    expect([a.status, b.status].sort()).toEqual([200, 409]);
    expect(await scalar("SELECT COUNT(*) AS count FROM devices WHERE license_id = ?", licenseId)).toBe(1);
    expect(await scalar("SELECT COUNT(*) AS count FROM license_events WHERE license_id = ? AND event_type = 'ACTIVATED'", licenseId)).toBe(1);
  });

  it("re-checks revocation, expiry, device limits, and existing identity after issuance", async () => {
    const revokedKey = "APP-AAAA-BBBB-0014"; const revokedId = await seedLicense(revokedKey, 1);
    const revokedChallenge = await successData(await challenge(await requestFor(revokedKey, crypto.randomUUID(), deviceKey)));
    await env.DB.prepare("UPDATE licenses SET status = 'REVOKED' WHERE id = ?").bind(revokedId).run();
    await expectError(await proof(revokedChallenge, deviceKey.privateKey), "LICENSE_REVOKED");

    const expiredKey = "APP-AAAA-BBBB-0015"; const expiredId = await seedLicense(expiredKey, 1);
    const expiredChallenge = await successData(await challenge(await requestFor(expiredKey, crypto.randomUUID(), deviceKey)));
    await env.DB.prepare("UPDATE licenses SET expires_at = ? WHERE id = ?").bind(NOW, expiredId).run();
    await expectError(await proof(expiredChallenge, deviceKey.privateKey), "LICENSE_EXPIRED");

    const limitKey = "APP-AAAA-BBBB-0016"; await seedLicense(limitKey, 1);
    const waiting = await successData(await challenge(await requestFor(limitKey, crypto.randomUUID(), deviceKey)));
    expect((await activate(await requestFor(limitKey, crypto.randomUUID(), otherKey), otherKey)).status).toBe(200);
    await expectError(await proof(waiting, deviceKey.privateKey), "DEVICE_LIMIT_REACHED");

    const mismatchKey = "APP-AAAA-BBBB-0017"; await seedLicense(mismatchKey, 1); const installation = crypto.randomUUID();
    expect((await activate(await requestFor(mismatchKey, installation, deviceKey), deviceKey)).status).toBe(200);
    const mismatch = await successData(await challenge(await requestFor(mismatchKey, installation, otherKey)));
    await expectError(await proof(mismatch, otherKey.privateKey), "DEVICE_MISMATCH");
  });

  it("new challenge retry is idempotent after a conceptually lost successful response", async () => {
    const key = "APP-AAAA-BBBB-0018"; const licenseId = await seedLicense(key, 1); const installation = crypto.randomUUID();
    const request = await requestFor(key, installation, deviceKey);
    const first = await activate(request, deviceKey); const second = await activate(request, deviceKey);
    expect(first.status).toBe(200); expect(second.status).toBe(200);
    const one = await successData(first); const two = await successData(second);
    expect(one.deviceId).toBe(two.deviceId);
    expect(await scalar("SELECT COUNT(*) AS count FROM devices WHERE license_id = ?", licenseId)).toBe(1);
  });

  it("re-checks the bound application identity when completing proof", async () => {
    const key = "APP-AAAA-BBBB-0019"; const licenseId = await seedLicense(key, 1);
    const issued = await successData(await challenge(await requestFor(key, crypto.randomUUID(), deviceKey)));
    await env.DB.prepare("UPDATE license_challenges SET signing_certificate_sha256 = ? WHERE id = ?")
      .bind("a".repeat(64), issued.challengeId).run();
    await expectError(await proof(issued, deviceKey.privateKey), "APPLICATION_NOT_ALLOWED");
    expect(await scalar("SELECT COUNT(*) AS count FROM devices WHERE license_id = ?", licenseId)).toBe(0);
    expect(await env.DB.prepare("SELECT consumed_at FROM license_challenges WHERE id = ?").bind(issued.challengeId).first<number | null>("consumed_at")).toBeNull();
  });
});

async function seedLicense(key: string, max: number, status: "NEW"|"ACTIVE"|"REVOKED"|"EXPIRED"="NEW", expires: number|null=null) {
  const id=crypto.randomUUID(); const hash=await hashLicenseKey(key, env.LICENSE_KEY_PEPPER);
  await env.DB.prepare("INSERT INTO licenses (id,license_key_hash,status,max_devices,created_at,expires_at) VALUES (?,?,?,?,?,?)")
    .bind(id,hash,status,max,NOW,expires).run(); return id;
}
async function requestFor(key:string, installationId:string, pair:CryptoKeyPair):Promise<ActivationRequest>{
  return {licenseKey:key,device:{installationId,deviceHash:"a".repeat(64),publicKey:toBase64(await crypto.subtle.exportKey("spki",pair.publicKey))},
    application:{packageName:"com.trancong.dexworkspacetouch",versionName:"1-test",versionCode:1,signingCertificateSha256:"B".repeat(64)}};
}
function challenge(request:ActivationRequest){return SELF.fetch("https://license.test/v1/license/challenge",jsonRequest(request));}
async function activate(request:ActivationRequest,pair:CryptoKeyPair){return proof(await successData(await challenge(request)),pair.privateKey);}
async function proof(issued:Record<string,unknown>,privateKey:CryptoKey){
  const der=await signDer(privateKey,base64UrlDecode(issued.challenge as string)); return submitProof(issued.challengeId as string,base64UrlEncode(der));
}
function submitProof(challengeId:string,signature:string){return SELF.fetch("https://license.test/v1/license/activate",jsonRequest({proofVersion:1,challengeId,signature}));}
function jsonRequest(value:unknown):RequestInit{return {method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify(value)};}
async function successData(response:Response):Promise<Record<string,unknown>>{const body=await response.json<{ok:boolean;data:Record<string,unknown>}>();expect(body.ok).toBe(true);return body.data;}
async function expectError(response:Response,code:string){const body=await response.json<{ok:boolean;error:{code:string}}>();expect(body.ok).toBe(false);expect(body.error.code).toBe(code);}
async function scalar(sql:string,value:string){return (await env.DB.prepare(sql).bind(value).first<{count:number}>())?.count??0;}
async function generateDeviceKey(){return crypto.subtle.generateKey({name:"ECDSA",namedCurve:"P-256"},true,["sign","verify"]) as Promise<CryptoKeyPair>;}
async function signDer(key:CryptoKey,payload:Uint8Array){const p1363=new Uint8Array(await crypto.subtle.sign({name:"ECDSA",hash:"SHA-256"},key,payload.slice().buffer as ArrayBuffer));return p1363ToDer(p1363);}
function p1363ToDer(value:Uint8Array){if(value.length!==64)throw new Error("Expected P1363");const integer=(v:Uint8Array)=>{let i=0;while(i<v.length-1&&v[i]===0)i++;let x=v.slice(i);if((x[0]!&0x80)!==0)x=Uint8Array.of(0,...x);return Uint8Array.of(2,x.length,...x);};const r=integer(value.slice(0,32)),s=integer(value.slice(32));return Uint8Array.of(0x30,r.length+s.length,...r,...s);}
function toBase64(value:ArrayBuffer){return btoa(String.fromCharCode(...new Uint8Array(value)));}
