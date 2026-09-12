import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { mkdtemp, readFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { FulfillmentService, FulfillmentStore, loadProductionManifest } from "../scripts/fulfillment-core.mjs";

const KEY = "DWT-LICENSE-EXACT-TEST-KEY";
const RELEASE = { schemaVersion: 1, applicationId: "com.trancong.dexworkspacetouch",
  versionName: "1.0.0-test", versionCode: 42, apkUrl: "https://updates.example/releases/app.apk",
  apkSha256: "a".repeat(64), signingCertificateSha256: "b".repeat(64) };

describe("customer fulfillment", () => {
  let root: string;
  let admin: ReturnType<typeof fakeAdmin>;
  let service: FulfillmentService;
  beforeEach(async () => {
    root = await mkdtemp(path.join(tmpdir(), "dwt-fulfillment-"));
    admin = fakeAdmin();
    service = makeService(root, admin);
  });
  afterEach(async () => rm(root, { recursive: true, force: true }));

  it("creates a license and a persistent record", async () => {
    const result = await service.create({ orderReference: "ORD-TEST-001" });
    expect(result.record).toMatchObject({ licenseId: "license-test-1", orderReference: "ORD-TEST-001", status: "LICENSE_CREATED" });
    expect(await new FulfillmentStore(path.join(root, "records")).find("FUL-TEST-1")).not.toBeNull();
  });
  it("never persists the plaintext key", async () => {
    await service.create({ orderReference: "ORD-TEST-001" });
    expect(await readFile(path.join(root, "records", "FUL-TEST-1.json"), "utf8")).not.toContain(KEY);
  });
  it("writes the exact canonical key to the delivery artifact", async () => {
    const result = await service.create({ orderReference: "ORD-TEST-001" });
    expect(await readFile(result.deliveryPath, "utf8")).toContain(`License Key:\n${KEY}\n`);
  });
  it("includes current release metadata in record and delivery", async () => {
    const result = await service.create({ orderReference: "ORD-TEST-001" });
    expect(result.record).toMatchObject({ initialReleaseVersion: "1.0.0-test", initialReleaseVersionCode: 42, apkSha256: "a".repeat(64) });
    expect(await readFile(result.deliveryPath, "utf8")).toContain(RELEASE.apkUrl);
    expect(await readFile(result.deliveryPath, "utf8")).toContain("dexworkspacetouch.support@gmail.com");
  });
  it("rejects a duplicate non-cancelled order", async () => {
    await service.create({ orderReference: "ORD-TEST-001" });
    await expect(service.create({ orderReference: "ORD-TEST-001" })).rejects.toThrow("already exists");
    expect(admin.create).toHaveBeenCalledTimes(1);
  });
  it("marks a record delivered with an audit event", async () => {
    await service.create({ orderReference: "ORD-TEST-001" });
    const record = await service.markDelivered("FUL-TEST-1");
    expect(record).toMatchObject({ status: "DELIVERED", deliveryStatus: "DELIVERED" });
    expect(record.events.at(-1)?.type).toBe("DELIVERED");
  });
  it("looks up by order reference", async () => {
    await service.create({ orderReference: "ORD-TEST-001" });
    expect((await service.show("ORD-TEST-001")).record.fulfillmentId).toBe("FUL-TEST-1");
  });
  it("looks up by license ID", async () => {
    await service.create({ orderReference: "ORD-TEST-001" });
    expect((await service.show("license-test-1")).record.fulfillmentId).toBe("FUL-TEST-1");
  });
  it("revokes backend license then updates local status", async () => {
    await service.create({ orderReference: "ORD-TEST-001" });
    const record = await service.revoke("ORD-TEST-001");
    expect(admin.revoke).toHaveBeenCalledWith("license-test-1");
    expect(record.status).toBe("REVOKED");
  });
  it("resets a device through the admin API and audits safe metadata", async () => {
    await service.create({ orderReference: "ORD-TEST-001" });
    const record = await service.resetDevice("ORD-TEST-001", "device-safe-id");
    expect(admin.resetDevice).toHaveBeenCalledWith("license-test-1", "device-safe-id");
    expect(record.events.at(-1)?.type).toBe("DEVICE_RESET");
  });
  it("links replacement records in both directions", async () => {
    await service.create({ orderReference: "ORD-TEST-001" });
    admin.create.mockResolvedValueOnce({ licenseId: "license-test-2", licenseKey: "SECOND-EXACT-KEY", maxDevices: 1, expiresAtEpochSeconds: null });
    const result = await service.replace("ORD-TEST-001", { orderReference: "ORD-TEST-002" });
    expect(result.record.replacesLicenseId).toBe("license-test-1");
    expect(result.replaced.replacedByLicenseId).toBe("license-test-2");
    expect(result.replaced.status).toBe("REPLACED");
    expect(result.replaced.events.map((event: { type: string }) => event.type)).toEqual([
      "LICENSE_CREATED", "LICENSE_REVOKED", "LICENSE_REPLACED",
    ]);
  });
  it("does not provide any lost-key recovery operation", () => {
    expect(Object.getOwnPropertyNames(FulfillmentService.prototype)).not.toContain("recoverLicenseKey");
  });
  it("does not leak the admin token in API errors", async () => {
    const token = "ADMIN-TOKEN-MUST-NOT-LEAK";
    const { AdminClient } = await import("../scripts/fulfillment-core.mjs");
    const client = new AdminClient({ apiUrl: "https://admin.example", token, fetchImpl: vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ error: { code: "DENIED", message: "No" } }), { status: 403, headers: { "X-Request-Id": "req-safe" } })) });
    await expect(client.show("license-id")).rejects.not.toThrow(token);
  });
  it("does not serialize License Key fields in records", async () => {
    const result = await service.create({ orderReference: "ORD-TEST-001" });
    expect(JSON.stringify(result.record)).not.toMatch(/licenseKey|license_key|licenseKeyHash/iu);
  });
  it("does not mark local success after backend failure", async () => {
    admin.create.mockRejectedValueOnce(new Error("HTTP 503 TEMPORARY"));
    await expect(service.create({ orderReference: "ORD-TEST-001" })).rejects.toThrow("HTTP 503");
    expect(await new FulfillmentStore(path.join(root, "records")).list()).toEqual([]);
  });
  it("fails safely on malformed manifest before creating a license", async () => {
    const broken = makeService(root, admin, { applicationId: "wrong" });
    await expect(broken.create({ orderReference: "ORD-TEST-001" })).rejects.toThrow("malformed");
    expect(admin.create).not.toHaveBeenCalled();
  });
  it("requires an HTTPS manifest URL", async () => {
    await expect(loadProductionManifest(vi.fn(), "http://updates.example/manifest.json")).rejects.toThrow("HTTPS");
  });
  it("requires a public HTTPS APK URL", async () => {
    const invalid = { ...RELEASE, apkUrl: "http://updates.example/app.apk" };
    await expect(loadProductionManifest(response(invalid), "https://updates.example/manifest.json")).rejects.toThrow("APK URL");
  });
});

function makeService(root: string, admin: ReturnType<typeof fakeAdmin>, release: unknown = RELEASE) {
  let sequence = 0;
  return new FulfillmentService({ admin, store: new FulfillmentStore(path.join(root, "records")),
    outputDirectory: path.join(root, "output"), fetchImpl: response(release), manifestUrl: "https://updates.example/manifest.json",
    now: () => new Date("2026-09-11T00:00:00.000Z"), id: () => `FUL-TEST-${++sequence}` });
}
function response(body: unknown) { return vi.fn().mockImplementation(async () => new Response(JSON.stringify(body), { status: 200 })); }
function fakeAdmin() { return {
  create: vi.fn().mockResolvedValue({ licenseId: "license-test-1", licenseKey: KEY, maxDevices: 1, expiresAtEpochSeconds: null }),
  show: vi.fn().mockResolvedValue({ licenseId: "license-test-1", status: "ACTIVE" }),
  devices: vi.fn().mockResolvedValue({ devices: [] }), revoke: vi.fn().mockResolvedValue({ revoked: true }),
  resetDevice: vi.fn().mockResolvedValue({ reset: true }),
}; }
