import { AdminClient } from "./admin-client";
import { loadProductionManifest } from "./manifest";
import { FulfillmentRepository } from "./repository";
import type { FulfillmentRecord, OperatorIdentity, ProductionRelease } from "./types";

export interface CreateInput { orderReference: string; customerReference: string | null; maxDevices: number; expiresAtEpochSeconds: number | null }

export class OperatorService {
  constructor(private readonly repository: FulfillmentRepository, private readonly admin: AdminClient,
    private readonly manifestUrl: string, private readonly now = () => new Date().toISOString(),
    private readonly fetchImpl: typeof fetch = fetch) {}

  async create(raw: unknown, identity: OperatorIdentity) {
    const input = validateCreate(raw);
    if (await this.repository.findActiveOrder(input.orderReference)) throw new PortalError(409, "DUPLICATE_ORDER", "Order reference already has an active fulfillment");
    const release = await loadProductionManifest(this.manifestUrl, this.fetchImpl);
    const created = await this.admin.create(input.maxDevices, input.expiresAtEpochSeconds);
    const fulfillmentId = `FUL-${crypto.randomUUID()}`;
    let record: FulfillmentRecord;
    try {
      record = await this.repository.create({ fulfillmentId, licenseId: created.licenseId, orderReference: input.orderReference,
        customerReference: input.customerReference, maxDevices: created.maxDevices, expiresAt: created.expiresAtEpochSeconds,
        release, identity, now: this.now() });
    } catch {
      throw new PortalError(500, "PERSISTENCE_FAILED_AFTER_LICENSE_CREATE", "License was created but fulfillment persistence failed",
        { licenseId: created.licenseId, recoveryState: "LICENSE_CREATED_RECORD_MISSING" });
    }
    return oneTime(record, release, created.licenseKey);
  }

  list(query: string | null) { return this.repository.list(query); }

  async show(reference: string) {
    const record = await this.required(reference);
    const [license, devicePayload, events] = await Promise.all([
      this.admin.show(record.licenseId), this.admin.devices(record.licenseId), this.repository.events(record.fulfillmentId),
    ]);
    return { record, license, devices: (devicePayload as { devices?: unknown[] }).devices ?? [], events };
  }

  async markDelivered(reference: string, identity: OperatorIdentity) {
    return this.repository.markDelivered(await this.required(reference), identity, this.now());
  }

  async resetDevice(reference: string, deviceId: string, identity: OperatorIdentity) {
    if (!deviceId.trim()) throw new PortalError(400, "INVALID_DEVICE", "Device ID is required");
    const record = await this.required(reference);
    const result = await this.admin.resetDevice(record.licenseId, deviceId);
    await this.repository.recordReset(record, deviceId, identity, this.now());
    return result;
  }

  async revoke(reference: string, cancelled: boolean, identity: OperatorIdentity) {
    const record = await this.required(reference);
    await this.admin.revoke(record.licenseId);
    return this.repository.recordRevoked(record, cancelled, identity, this.now());
  }

  async replace(reference: string, raw: unknown, identity: OperatorIdentity) {
    const old = await this.required(reference);
    const input = validateCreate(raw);
    if (await this.repository.findActiveOrder(input.orderReference)) throw new PortalError(409, "DUPLICATE_ORDER", "Replacement order reference already exists");
    const release = await loadProductionManifest(this.manifestUrl, this.fetchImpl);
    await this.admin.revoke(old.licenseId);
    await this.repository.recordRevoked(old, false, identity, this.now());
    let created;
    try { created = await this.admin.create(input.maxDevices, input.expiresAtEpochSeconds); }
    catch { throw new PortalError(502, "REPLACEMENT_CREATE_FAILED", "Old license was revoked but replacement creation failed",
      { licenseId: old.licenseId, recoveryState: "OLD_LICENSE_REVOKED_NEW_LICENSE_NOT_CREATED" }); }
    let record;
    try {
      record = await this.repository.create({ fulfillmentId: `FUL-${crypto.randomUUID()}`, licenseId: created.licenseId,
        orderReference: input.orderReference, customerReference: input.customerReference, maxDevices: created.maxDevices,
        expiresAt: created.expiresAtEpochSeconds, release, identity, now: this.now() });
      await this.repository.linkReplacement(old, record, identity, this.now());
    } catch {
      throw new PortalError(500, "REPLACEMENT_PERSISTENCE_FAILED", "Replacement license was created but persistence failed",
        { licenseId: created.licenseId, replacedLicenseId: old.licenseId, recoveryState: "NEW_LICENSE_CREATED_RECORD_INCOMPLETE" });
    }
    return oneTime(record, release, created.licenseKey);
  }

  private async required(reference: string): Promise<FulfillmentRecord> {
    const record = await this.repository.find(reference);
    if (!record) throw new PortalError(404, "FULFILLMENT_NOT_FOUND", "Fulfillment was not found");
    return record;
  }
}

function validateCreate(raw: unknown): CreateInput {
  if (!isObject(raw)) throw new PortalError(400, "INVALID_REQUEST", "Request is invalid");
  const allowed = ["orderReference", "customerReference", "maxDevices", "expiresAtEpochSeconds"];
  if (Object.keys(raw).some((key) => !allowed.includes(key))) throw new PortalError(400, "INVALID_REQUEST", "Unknown field");
  const orderReference = text(raw.orderReference, 100, true);
  const customerReference = raw.customerReference === null || raw.customerReference === undefined || raw.customerReference === ""
    ? null : text(raw.customerReference, 100, true);
  const maxDevices = raw.maxDevices === undefined ? 1 : raw.maxDevices;
  const expiry = raw.expiresAtEpochSeconds === undefined || raw.expiresAtEpochSeconds === null ? null : raw.expiresAtEpochSeconds;
  if (!Number.isInteger(maxDevices) || (maxDevices as number) < 1 || (maxDevices as number) > 100 ||
      !(expiry === null || Number.isSafeInteger(expiry) && (expiry as number) > Math.floor(Date.now() / 1000))) {
    throw new PortalError(400, "INVALID_REQUEST", "Device count or expiry is invalid");
  }
  return { orderReference, customerReference, maxDevices: maxDevices as number, expiresAtEpochSeconds: expiry as number | null };
}

export function deliveryText(release: ProductionRelease, licenseKey: string): string {
  return `DEXWORKSPACETOUCH\n\nPhiên bản: ${release.versionName} (${release.versionCode})\n\nTải ứng dụng:\n${release.apkUrl}\n\n` +
    `APK SHA-256:\n${release.apkSha256}\n\nLicense Key:\n${licenseKey}\n\n` +
    "Cài đặt và kích hoạt:\n1. Tải APK từ URL chính thức.\n2. Cài và mở DexWorkspaceTouch.\n3. Nhập chính xác License Key.\n\n" +
    "Cập nhật: dùng Kiểm tra cập nhật và cài đè APK mới. KHÔNG gỡ ứng dụng trước khi cập nhật.\n\n" +
    "Hỗ trợ: dexworkspacetouch.support@gmail.com. Cung cấp mã đơn hàng/customer reference; không gửi License Key công khai.\n";
}

function oneTime(record: FulfillmentRecord, release: ProductionRelease, licenseKey: string) {
  return { record, release, licenseKey, deliveryText: deliveryText(release, licenseKey), oneTime: true };
}
function isObject(value: unknown): value is Record<string, unknown> { return typeof value === "object" && value !== null && !Array.isArray(value); }
function text(value: unknown, max: number, required: boolean): string {
  if (typeof value !== "string" || (required && !value.trim()) || value.length > max) throw new PortalError(400, "INVALID_REQUEST", "Text field is invalid");
  return value.trim();
}
export class PortalError extends Error {
  constructor(readonly status: number, readonly code: string, message: string, readonly details?: Record<string, unknown>) { super(message); }
}
