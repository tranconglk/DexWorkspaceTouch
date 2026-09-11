import { applyD1Migrations, env, SELF } from "cloudflare:test";
import { beforeEach, describe, expect, it } from "vitest";
import { hashLicenseKey } from "../src/license/license-key-hash";

const TOKEN = "lic-009-test-admin-token-not-for-production";
const AUTH = { Authorization: `Bearer ${TOKEN}` };

beforeEach(async () => { await applyD1Migrations(env.DB, env.TEST_MIGRATIONS); });

async function admin(path: string, init: RequestInit = {}): Promise<Response> {
  return SELF.fetch(`https://license.test${path}`, { ...init, headers: { ...AUTH, ...(init.headers ?? {}) } });
}
async function create(maxDevices = 1) {
  const response = await admin("/v1/admin/licenses", { method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ maxDevices, expiresAtEpochSeconds: null }) });
  expect(response.status).toBe(201);
  return (await response.json() as any).data;
}

describe("admin HTTP API", () => {
  it.each([undefined, "Bearer wrong", "Basic nope"])("rejects unauthorized access before mutation (%s)", async (authorization) => {
    const response = await SELF.fetch("https://license.test/v1/admin/licenses", { method: "POST",
      headers: { ...(authorization ? { Authorization: authorization } : {}), "Content-Type": "application/json" }, body: "not-json" });
    expect(response.status).toBe(401);
    expect(response.headers.get("WWW-Authenticate")).toBe("Bearer");
    expect(JSON.stringify(await response.json())).not.toContain(TOKEN);
    expect(await env.DB.prepare("SELECT COUNT(*) AS count FROM licenses").first<number>("count")).toBe(0);
  });

  it("does not mutate revoke or reset operations without authentication", async () => {
    const created = await create(); const deviceId = `${created.licenseId}-unauthorized-device`;
    await insertDevice(created.licenseId, deviceId, `${created.licenseId}-unauthorized-installation`, "e".repeat(64));
    const revoke = await SELF.fetch(`https://license.test/v1/admin/licenses/${created.licenseId}/revoke`, { method: "POST" });
    const reset = await SELF.fetch(`https://license.test/v1/admin/licenses/${created.licenseId}/devices/${deviceId}/reset`, { method: "POST" });
    expect(revoke.status).toBe(401); expect(reset.status).toBe(401);
    expect(await env.DB.prepare("SELECT status FROM licenses WHERE id = ?").bind(created.licenseId).first<string>("status")).toBe("NEW");
    expect(await env.DB.prepare("SELECT revoked_at FROM devices WHERE id = ?").bind(deviceId).first<number | null>("revoked_at")).toBeNull();
  });

  it("keeps public health and challenge routes outside admin authentication", async () => {
    expect((await SELF.fetch("https://license.test/v1/health")).status).toBe(200);
    const challenge = await SELF.fetch("https://license.test/v1/license/challenge", { method: "POST",
      headers: { "Content-Type": "application/json" }, body: "{}" });
    expect(challenge.status).not.toBe(401);
  });

  it("creates a license, stores only its HMAC, and never returns the key from reads", async () => {
    const created = await create(2);
    expect(created.licenseKey).toMatch(/^DWT-(?:[A-Z2-9]{4}-){2}[A-Z2-9]{4}$/);
    const row = await env.DB.prepare("SELECT license_key_hash FROM licenses WHERE id = ?").bind(created.licenseId).first<{ license_key_hash: string }>();
    expect(row?.license_key_hash).toBe(await hashLicenseKey(created.licenseKey, env.LICENSE_KEY_PEPPER));
    expect(row?.license_key_hash).not.toContain(created.licenseKey);
    const show = await admin(`/v1/admin/licenses/${created.licenseId}`);
    const list = await admin("/v1/admin/licenses");
    for (const response of [show, list]) {
      const text = await response.text();
      expect(text).not.toContain(created.licenseKey);
      expect(text).not.toContain("license_key_hash");
      expect(response.headers.get("Cache-Control")).toBe("no-store");
    }
    expect(await env.DB.prepare("SELECT COUNT(*) AS count FROM license_events WHERE license_id = ? AND event_type = 'LICENSE_CREATED'")
      .bind(created.licenseId).first<number>("count")).toBe(1);
  });

  it.each([0, -1, 101, 1.5, "1"])("rejects invalid maxDevices %s", async (maxDevices) => {
    const response = await admin("/v1/admin/licenses", { method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ maxDevices, expiresAtEpochSeconds: null }) });
    expect(response.status).toBe(400);
  });

  it("rejects unknown create fields and invalid expiry", async () => {
    for (const body of [{ maxDevices: 1, expiresAtEpochSeconds: "later" }, { maxDevices: 1, expiresAtEpochSeconds: 1 },
      { maxDevices: 1, expiresAtEpochSeconds: null, status: "ACTIVE" }]) {
      expect((await admin("/v1/admin/licenses", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) })).status).toBe(400);
    }
  });

  it("lists with bounded pagination and reports active device count", async () => {
    const created = await create();
    const response = await admin("/v1/admin/licenses?limit=100&offset=0");
    const body = await response.json() as any;
    expect(body.data.licenses.find((license: any) => license.licenseId === created.licenseId))
      .toMatchObject({ licenseId: created.licenseId, activeDeviceCount: 0 });
    expect(JSON.stringify(body)).not.toContain("licenseKey");
    expect((await admin("/v1/admin/licenses?limit=101")).status).toBe(400);
  });

  it("revokes NEW and ACTIVE licenses idempotently without deleting devices or changing the hash", async () => {
    for (const initialStatus of ["NEW", "ACTIVE"]) {
      const created = await create();
      await env.DB.prepare("UPDATE licenses SET status = ? WHERE id = ?").bind(initialStatus, created.licenseId).run();
      await env.DB.prepare(`INSERT INTO devices (id, license_id, installation_id, device_hash, public_key, key_algorithm, created_at, last_seen_at)
        VALUES (?, ?, ?, ?, ?, 'EC_P256', 1, 1)`).bind(`device-${initialStatus}`, created.licenseId, `installation-${initialStatus}`, "a".repeat(64), "public").run();
      const hashBefore = await env.DB.prepare("SELECT license_key_hash FROM licenses WHERE id = ?").bind(created.licenseId).first<string>("license_key_hash");
      expect((await admin(`/v1/admin/licenses/${created.licenseId}/revoke`, { method: "POST" })).status).toBe(200);
      const second = await admin(`/v1/admin/licenses/${created.licenseId}/revoke`, { method: "POST" });
      expect((await second.json() as any).data.alreadyRevoked).toBe(true);
      expect(await env.DB.prepare("SELECT status FROM licenses WHERE id = ?").bind(created.licenseId).first<string>("status")).toBe("REVOKED");
      expect(await env.DB.prepare("SELECT license_key_hash FROM licenses WHERE id = ?").bind(created.licenseId).first<string>("license_key_hash")).toBe(hashBefore);
      expect(await env.DB.prepare("SELECT COUNT(*) AS count FROM devices WHERE license_id = ?").bind(created.licenseId).first<number>("count")).toBe(1);
      expect(await env.DB.prepare("SELECT COUNT(*) AS count FROM license_events WHERE license_id = ? AND event_type = 'LICENSE_REVOKED'").bind(created.licenseId).first<number>("count")).toBe(1);
    }
  });

  it("returns not found for an unknown license", async () => {
    expect((await admin("/v1/admin/licenses/missing")).status).toBe(404);
    expect((await admin("/v1/admin/licenses/missing/revoke", { method: "POST" })).status).toBe(404);
  });

  it("lists only redacted device fields", async () => {
    const created = await create();
    await insertDevice(created.licenseId, "device-a", "installation-secret", "1234567890abcdef".repeat(4));
    const response = await admin(`/v1/admin/licenses/${created.licenseId}/devices`);
    const text = await response.text();
    expect(text).toContain('"fingerprint":"12345678"');
    for (const secret of ["installation-secret", "public-secret", "1234567890abcdef".repeat(4)]) expect(text).not.toContain(secret);
  });

  it("resets a device idempotently, retains its row, frees its slot, and writes one event", async () => {
    const created = await create();
    const deviceId = `${created.licenseId}-device-a`;
    await insertDevice(created.licenseId, deviceId, `${created.licenseId}-installation-a`, "a".repeat(64));
    const path = `/v1/admin/licenses/${created.licenseId}/devices/${deviceId}/reset`;
    expect((await admin(path, { method: "POST" })).status).toBe(200);
    const second = await admin(path, { method: "POST" });
    expect((await second.json() as any).data.alreadyReset).toBe(true);
    expect(await env.DB.prepare("SELECT COUNT(*) AS count FROM devices WHERE id = ?").bind(deviceId).first<number>("count")).toBe(1);
    expect(await env.DB.prepare("SELECT COUNT(*) AS count FROM devices WHERE license_id = ? AND revoked_at IS NULL").bind(created.licenseId).first<number>("count")).toBe(0);
    expect(await env.DB.prepare("SELECT COUNT(*) AS count FROM license_events WHERE device_id = ? AND event_type = 'DEVICE_RESET'").bind(deviceId).first<number>("count")).toBe(1);
  });

  it("protects reset from cross-license and unknown device IDs", async () => {
    const first = await create(); const second = await create();
    const deviceId = `${first.licenseId}-device-a`;
    await insertDevice(first.licenseId, deviceId, `${first.licenseId}-installation-a`, "a".repeat(64));
    expect((await admin(`/v1/admin/licenses/${second.licenseId}/devices/${deviceId}/reset`, { method: "POST" })).status).toBe(404);
    expect((await admin(`/v1/admin/licenses/${first.licenseId}/devices/missing/reset`, { method: "POST" })).status).toBe(404);
    expect(await env.DB.prepare("SELECT revoked_at FROM devices WHERE id = ?").bind(deviceId).first<number | null>("revoked_at")).toBeNull();
  });
});

async function insertDevice(licenseId: string, deviceId: string, installationId: string, deviceHash: string): Promise<void> {
  await env.DB.prepare(`INSERT INTO devices (id, license_id, installation_id, device_hash, public_key, key_algorithm, created_at, last_seen_at)
    VALUES (?, ?, ?, ?, 'public-secret', 'EC_P256', 1700000000, 1700000000)`)
    .bind(deviceId, licenseId, installationId, deviceHash).run();
}
