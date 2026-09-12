import { applyD1Migrations, env, reset } from "cloudflare:test";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { handleRequest } from "../src/index";
import { loadProductionManifest } from "../src/manifest";
import { PORTAL_HTML } from "../src/portal-html";
import type { Env, OperatorIdentity } from "../src/types";

const IDENTITY: OperatorIdentity = { email: "operator@example.com" };
const authenticate = async () => IDENTITY;
const workerEnv = env as unknown as Env;
const manifest = {
  applicationId: "com.trancong.dexworkspacetouch", versionName: "1.0.0", versionCode: 10,
  apkUrl: "https://updates.test/releases/1/app.apk", apkSha256: "a".repeat(64), apkSize: 123,
  signingCertificateSha256: "19ac0ea99125361b3c2083aaa44c8745ebad9c55fa967642c2b9e5a1086a45e7",
};
const created = { licenseId: "license-1", licenseKey: "DWT-ABCD-EFGH-JKLM", maxDevices: 1, expiresAtEpochSeconds: null };

beforeEach(async () => {
  await applyD1Migrations(env.FULFILLMENT_DB, env.TEST_MIGRATIONS);
  outbound.length = 0;
});
afterEach(async () => reset());

const outbound: Array<{ origin: string; path: string; method: string; status: number; data: unknown }> = [];
const fakeFetch: typeof fetch = async (input, init) => {
  const url = new URL(typeof input === "string" ? input : input instanceof URL ? input.toString() : input.url);
  const method = init?.method ?? (input instanceof Request ? input.method : "GET");
  const index = outbound.findIndex((item) => item.origin === url.origin && item.path === url.pathname && item.method === method);
  if (index < 0) throw new Error(`Unexpected outbound request: ${method} ${url}`);
  const item = outbound.splice(index, 1)[0]!;
  return Response.json(item.status >= 400 ? { error: item.data } : (item.origin === "https://updates.test" ? item.data : { data: item.data }), { status: item.status });
};

function request(path: string, init: RequestInit = {}) {
  const headers = new Headers(init.headers);
  if (init.body) {
    headers.set("Content-Type", "application/json"); headers.set("X-DWT-CSRF", "1");
    if (!headers.has("Origin")) headers.set("Origin", "https://portal.test");
  }
  return handleRequest(new Request(`https://portal.test${path}`, { ...init, headers }), workerEnv, undefined, authenticate, fakeFetch);
}
function mockManifest(value: unknown = manifest, status = 200) {
  outbound.push({ origin: "https://updates.test", path: "/manifest.json", method: "GET", status, data: value });
}
function mockAdmin(path: string, method: string, data: unknown, status = 200) {
  outbound.push({ origin: "https://admin.test", path, method, status, data });
}
async function create(order = "ORDER-1") {
  mockManifest(); mockAdmin("/v1/admin/licenses", "POST", created, 201);
  return request("/api/fulfillments", { method: "POST", body: JSON.stringify({ orderReference: order, maxDevices: 1, expiresAtEpochSeconds: null }) });
}

describe("REL-002 operator portal", () => {
  it("rejects unauthenticated access before serving UI or API", async () => {
    const response = await handleRequest(new Request("https://portal.test/"), workerEnv, undefined, async () => null);
    expect(response.status).toBe(401); expect(await response.text()).not.toContain(PORTAL_HTML);
  });

  it("serves authenticated mobile UI without admin credentials", async () => {
    const response = await request("/"); const text = await response.text();
    expect(response.status).toBe(200); expect(text).toContain("width=device-width"); expect(text).toContain("min-height:48px");
    expect(text).not.toContain(workerEnv.LICENSE_ADMIN_TOKEN); expect(response.headers.get("Content-Security-Policy")).toContain("script-src 'nonce-");
    expect(response.headers.get("Content-Security-Policy")).not.toContain("script-src 'self' 'unsafe-inline'");
    expect(response.headers.get("Access-Control-Allow-Origin")).toBeNull();
  });

  it("rejects GET mutations and cross-origin POST", async () => {
    expect((await request("/api/fulfillments/x/revoke")).status).toBe(404);
    const cross = await request("/api/fulfillments", { method: "POST", headers: { Origin: "https://evil.test" }, body: "{}" });
    expect(cross.status).toBe(403);
  });

  it("creates through Admin API, returns key once, and persists no key", async () => {
    const log = vi.spyOn(console, "log").mockImplementation(() => {});
    const response = await create(); const text = await response.text();
    expect(response.status).toBe(201); expect(response.headers.get("Cache-Control")).toBe("no-store"); expect(text).toContain(created.licenseKey);
    const row = await env.FULFILLMENT_DB.prepare("SELECT * FROM fulfillments WHERE order_reference = 'ORDER-1'").first<Record<string, unknown>>();
    expect(JSON.stringify(row)).not.toContain(created.licenseKey); expect(Object.keys(row ?? {})).not.toContain("license_key");
    expect(JSON.stringify(log.mock.calls)).not.toContain(created.licenseKey); log.mockRestore();
    const list = await request("/api/fulfillments"); expect(await list.text()).not.toContain(created.licenseKey);
  });

  it("rejects duplicate active order without creating another license", async () => {
    expect((await create("DUPLICATE")).status).toBe(201);
    const second = await request("/api/fulfillments", { method: "POST", body: JSON.stringify({ orderReference: "DUPLICATE", maxDevices: 1 }) });
    expect(second.status).toBe(409); expect((await second.json() as any).error.code).toBe("DUPLICATE_ORDER");
  });

  it("rejects insecure APK URL in production manifest", async () => {
    mockManifest({ ...manifest, apkUrl: "http://updates.test/app.apk" });
    const response = await request("/api/fulfillments", { method: "POST", body: JSON.stringify({ orderReference: "BAD-MANIFEST", maxDevices: 1 }) });
    expect(response.status).toBe(502);
    expect((await response.json() as any).error.code).toBe("MANIFEST_INVALID");
  });

  it("reports a safe admin error code without exposing upstream details", async () => {
    mockManifest(); mockAdmin("/v1/admin/licenses", "POST", { code: "UNAUTHORIZED", message: "secret upstream detail" }, 401);
    const response = await request("/api/fulfillments", { method: "POST",
      body: JSON.stringify({ orderReference: "ADMIN-FAIL", maxDevices: 1, expiresAtEpochSeconds: null }) });
    const body = await response.json() as any;
    expect(response.status).toBe(502); expect(body.error.code).toBe("ADMIN_UNAUTHORIZED");
    expect(JSON.stringify(body)).not.toContain("secret upstream detail");
  });

  it("searches, shows safe detail, and marks delivered", async () => {
    const createdResponse = await create("SEARCH-ORDER"); const fulfillment = (await createdResponse.json() as any).record;
    const search = await request("/api/fulfillments?query=SEARCH"); expect((await search.json() as any).fulfillments).toHaveLength(1);
    mockAdmin(`/v1/admin/licenses/${fulfillment.licenseId}`, "GET", { licenseId: fulfillment.licenseId, status: "NEW" });
    mockAdmin(`/v1/admin/licenses/${fulfillment.licenseId}/devices`, "GET", { devices: [] });
    const show = await request(`/api/fulfillments/${fulfillment.fulfillmentId}`); expect(await show.text()).not.toContain(created.licenseKey);
    const delivered = await request(`/api/fulfillments/${fulfillment.fulfillmentId}/delivered`, { method: "POST", body: "{}" });
    expect((await delivered.json() as any).status).toBe("DELIVERED");
  });

  it("resets a safe device and revokes backend before recording status", async () => {
    const fulfillment = (await (await create("OPS-ORDER")).json() as any).record;
    mockAdmin(`/v1/admin/licenses/${fulfillment.licenseId}/devices/device-1/reset`, "POST", { reset: true });
    expect((await request(`/api/fulfillments/${fulfillment.fulfillmentId}/devices/device-1/reset`, { method: "POST", body: "{}" })).status).toBe(200);
    mockAdmin(`/v1/admin/licenses/${fulfillment.licenseId}/revoke`, "POST", { revoked: true });
    const revoked = await request(`/api/fulfillments/${fulfillment.fulfillmentId}/revoke`, { method: "POST", body: JSON.stringify({ cancelled: false }) });
    expect((await revoked.json() as any).status).toBe("REVOKED");
  });

  it("replaces a license, links records, and exposes only the new key once", async () => {
    const old = (await (await create("OLD-ORDER")).json() as any).record;
    mockManifest(); mockAdmin(`/v1/admin/licenses/${old.licenseId}/revoke`, "POST", { revoked: true });
    mockAdmin("/v1/admin/licenses", "POST", { ...created, licenseId: "license-2", licenseKey: "DWT-NPQR-STUV-WXYZ" }, 201);
    const response = await request(`/api/fulfillments/${old.fulfillmentId}/replace`, { method: "POST",
      body: JSON.stringify({ orderReference: "NEW-ORDER", customerReference: null, maxDevices: 1, expiresAtEpochSeconds: null }) });
    const body = await response.json() as any; expect(body.licenseKey).toBe("DWT-NPQR-STUV-WXYZ");
    const oldRow = await env.FULFILLMENT_DB.prepare("SELECT status, replaced_by_license_id FROM fulfillments WHERE fulfillment_id = ?").bind(old.fulfillmentId).first<any>();
    expect(oldRow).toMatchObject({ status: "REPLACED", replaced_by_license_id: "license-2" });
    expect(await (await request("/api/fulfillments?query=NEW-ORDER")).text()).not.toContain(body.licenseKey);
  });

  it("reports safe partial replacement failure and does not retry", async () => {
    const old = (await (await create("FAIL-OLD")).json() as any).record;
    mockManifest(); mockAdmin(`/v1/admin/licenses/${old.licenseId}/revoke`, "POST", { revoked: true });
    mockAdmin("/v1/admin/licenses", "POST", { code: "FAILED", message: "failed" }, 500);
    const response = await request(`/api/fulfillments/${old.fulfillmentId}/replace`, { method: "POST",
      body: JSON.stringify({ orderReference: "FAIL-NEW", maxDevices: 1, expiresAtEpochSeconds: null }) });
    const body = await response.json() as any; expect(response.status).toBe(502);
    expect(body.error.details.recoveryState).toBe("OLD_LICENSE_REVOKED_NEW_LICENSE_NOT_CREATED");
  });

  it("generates customer delivery text without internal identifiers", async () => {
    const body = await (await create("DELIVERY")).json() as any;
    expect(body.deliveryText).toContain(manifest.apkUrl); expect(body.deliveryText).toContain(created.licenseKey);
    expect(body.deliveryText).toContain("dexworkspacetouch.support@gmail.com");
    expect(body.deliveryText).not.toContain(body.record.licenseId); expect(body.deliveryText).not.toContain("admin.test");
  });
});

describe("manifest validation", () => {
  it("accepts the complete HTTPS production contract", async () => {
    const release = await loadProductionManifest("https://manifest.unit/test", async () => new Response(JSON.stringify(manifest)));
    expect(release.versionCode).toBe(10);
  });
  it("rejects malformed hashes and insecure manifest URL", async () => {
    await expect(loadProductionManifest("http://manifest.unit/test")).rejects.toThrow("invalid");
    await expect(loadProductionManifest("https://manifest.unit/test", async () => new Response(JSON.stringify({ ...manifest, apkSha256: "bad" })))).rejects.toThrow("malformed");
  });
});
