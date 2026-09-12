import { mkdir, readFile, readdir, rename, rm, writeFile } from "node:fs/promises";
import { randomUUID } from "node:crypto";
import path from "node:path";

export const FULFILLMENT_STATUSES = Object.freeze([
  "DRAFT", "LICENSE_CREATED", "DELIVERED", "ACTIVE", "REPLACED", "REVOKED", "CANCELLED",
]);
export const DEFAULT_MANIFEST_URL = "https://dexworkspacetouch-updates.dex-backend.workers.dev/update-manifest.json";
const APPLICATION_ID = "com.trancong.dexworkspacetouch";
const SHA256 = /^[a-f0-9]{64}$/iu;

export class AdminClient {
  constructor({ apiUrl, token, fetchImpl = fetch }) {
    const url = new URL(apiUrl);
    const local = url.hostname === "localhost" || url.hostname === "127.0.0.1";
    if (url.protocol !== "https:" && !(local && url.protocol === "http:")) {
      throw new Error("Admin API URL must use HTTPS (HTTP is allowed only for localhost).");
    }
    if (!token) throw new Error("DWT_LICENSE_ADMIN_TOKEN is required.");
    this.apiUrl = url.toString().replace(/\/$/u, "");
    this.token = token;
    this.fetchImpl = fetchImpl;
  }

  create({ maxDevices, expiresAtEpochSeconds }) {
    return this.#request("POST", "/v1/admin/licenses", { maxDevices, expiresAtEpochSeconds });
  }
  show(licenseId) { return this.#request("GET", `/v1/admin/licenses/${encodeURIComponent(licenseId)}`); }
  devices(licenseId) { return this.#request("GET", `/v1/admin/licenses/${encodeURIComponent(licenseId)}/devices`); }
  revoke(licenseId) { return this.#request("POST", `/v1/admin/licenses/${encodeURIComponent(licenseId)}/revoke`, {}); }
  resetDevice(licenseId, deviceId) {
    return this.#request("POST", `/v1/admin/licenses/${encodeURIComponent(licenseId)}/devices/${encodeURIComponent(deviceId)}/reset`, {});
  }

  async #request(method, route, body) {
    const response = await this.fetchImpl(`${this.apiUrl}${route}`, {
      method,
      headers: { Authorization: `Bearer ${this.token}`, ...(body === undefined ? {} : { "Content-Type": "application/json" }) },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    const payload = await response.json().catch(() => null);
    if (!response.ok) {
      const code = payload?.error?.code ?? "UNKNOWN_ERROR";
      const message = payload?.error?.message ?? "Request failed.";
      const requestId = response.headers.get("X-Request-Id");
      throw new Error(`HTTP ${response.status} ${code}: ${message}${requestId ? ` (request ${requestId})` : ""}`);
    }
    return payload.data;
  }
}

export class FulfillmentStore {
  constructor(directory) { this.directory = directory; }

  async list() {
    await mkdir(this.directory, { recursive: true });
    const files = (await readdir(this.directory)).filter((name) => name.endsWith(".json"));
    return Promise.all(files.map(async (name) => JSON.parse(await readFile(path.join(this.directory, name), "utf8"))));
  }
  async find(reference) {
    const records = await this.list();
    return records.find((record) => record.fulfillmentId === reference || record.orderReference === reference ||
      record.licenseId === reference || record.customerReference === reference) ?? null;
  }
  async findByOrder(orderReference) {
    return (await this.list()).find((record) => record.orderReference === orderReference) ?? null;
  }
  async save(record) {
    await mkdir(this.directory, { recursive: true });
    const target = path.join(this.directory, `${record.fulfillmentId}.json`);
    const temporary = `${target}.${randomUUID()}.tmp`;
    const backup = `${target}.${randomUUID()}.bak`;
    await writeFile(temporary, `${JSON.stringify(record, null, 2)}\n`, { encoding: "utf8", flag: "wx" });
    let hadExisting = false;
    try {
      await rename(target, backup);
      hadExisting = true;
    } catch (error) {
      if (error?.code !== "ENOENT") throw error;
    }
    try {
      await rename(temporary, target);
      if (hadExisting) await rm(backup, { force: true });
    } catch (error) {
      if (hadExisting) await rename(backup, target).catch(() => {});
      await rm(temporary, { force: true });
      throw error;
    }
  }
}

export class FulfillmentService {
  constructor({ admin, store, outputDirectory, fetchImpl = fetch, manifestUrl = DEFAULT_MANIFEST_URL,
    now = () => new Date(), id = () => `FUL-${randomUUID()}` }) {
    this.admin = admin; this.store = store; this.outputDirectory = outputDirectory;
    this.fetchImpl = fetchImpl; this.manifestUrl = manifestUrl; this.now = now; this.id = id;
  }

  async create({ orderReference, customerReference = null, maxDevices = 1, expiresAtEpochSeconds = null,
    notes = null, replacesLicenseId = null, allowDuplicate = false }) {
    required(orderReference, "orderReference");
    if (!Number.isSafeInteger(maxDevices) || maxDevices < 1) throw new Error("maxDevices must be a positive integer.");
    const existing = await this.store.findByOrder(orderReference);
    if (existing && !["CANCELLED", "REVOKED", "REPLACED"].includes(existing.status) && !allowDuplicate) {
      throw new Error("A non-cancelled fulfillment already exists for this orderReference.");
    }
    const release = await loadProductionManifest(this.fetchImpl, this.manifestUrl);
    const created = await this.admin.create({ maxDevices, expiresAtEpochSeconds });
    const timestamp = this.now().toISOString();
    const record = {
      fulfillmentId: this.id(), licenseId: created.licenseId, orderReference,
      ...(customerReference ? { customerReference } : {}), createdAt: timestamp, updatedAt: timestamp,
      status: "LICENSE_CREATED", maxDevices: created.maxDevices, expiresAt: created.expiresAtEpochSeconds,
      initialReleaseVersion: release.versionName, initialReleaseVersionCode: release.versionCode,
      apkSha256: release.apkSha256, deliveryStatus: "PENDING", ...(notes ? { notes } : {}),
      ...(replacesLicenseId ? { replacesLicenseId } : {}),
      events: [{ type: "LICENSE_CREATED", at: timestamp }],
    };
    try {
      await this.store.save(record);
      const deliveryPath = await writeDeliveryArtifact(this.outputDirectory, record.fulfillmentId, release, created.licenseKey);
      return { record, deliveryPath, licenseKey: created.licenseKey };
    } catch (error) {
      const wrapped = new Error(`License was created, but local fulfillment generation failed. Preserve licenseId ${created.licenseId} for recovery.`);
      wrapped.cause = error;
      throw wrapped;
    }
  }

  async markDelivered(reference) {
    return this.#update(reference, "DELIVERED", "DELIVERED", (record) => ({ ...record, deliveryStatus: "DELIVERED" }));
  }
  async revoke(reference, cancelled = false) {
    const record = await this.#require(reference);
    await this.admin.revoke(record.licenseId);
    return this.#saveTransition(record, cancelled ? "CANCELLED" : "REVOKED", "LICENSE_REVOKED");
  }
  async resetDevice(reference, deviceId) {
    required(deviceId, "deviceId");
    const record = await this.#require(reference);
    await this.admin.resetDevice(record.licenseId, deviceId);
    return this.#saveTransition(record, record.status, "DEVICE_RESET", { deviceId });
  }
  async show(reference) {
    const record = await this.#require(reference);
    const [license, devicePayload] = await Promise.all([this.admin.show(record.licenseId), this.admin.devices(record.licenseId)]);
    const devices = (devicePayload.devices ?? []).map(({ deviceId, fingerprint, status, activatedAt, lastSeenAt, resetAt }) =>
      ({ deviceId, fingerprint, status, activatedAt, lastSeenAt, resetAt }));
    return { record, backend: { license, devices } };
  }
  async replace(reference, options) {
    const old = await this.#require(reference);
    await this.admin.revoke(old.licenseId);
    const revoked = await this.#saveTransition(old, "REVOKED", "LICENSE_REVOKED", { reason: "REPLACEMENT" });
    const created = await this.create({ ...options, replacesLicenseId: old.licenseId, allowDuplicate: true });
    const replaced = await this.#saveTransition(revoked, "REPLACED", "LICENSE_REPLACED", { replacedByLicenseId: created.record.licenseId });
    const replacement = { ...created.record, replacesLicenseId: old.licenseId };
    await this.store.save(replacement);
    return { ...created, record: replacement, replaced };
  }
  async #require(reference) {
    const record = await this.store.find(reference);
    if (!record) throw new Error("Fulfillment record not found.");
    return record;
  }
  async #update(reference, status, event, transform = (value) => value) {
    return this.#saveTransition(transform(await this.#require(reference)), status, event);
  }
  async #saveTransition(record, status, type, metadata = {}) {
    const timestamp = this.now().toISOString();
    const updated = { ...record, ...metadata, status, updatedAt: timestamp,
      events: [...record.events, { type, at: timestamp, ...metadata }] };
    await this.store.save(updated);
    return updated;
  }
}

export async function loadProductionManifest(fetchImpl, manifestUrl) {
  const url = new URL(manifestUrl);
  if (url.protocol !== "https:") throw new Error("Production update manifest URL must use HTTPS.");
  const response = await fetchImpl(url);
  if (!response.ok) throw new Error(`Production update manifest request failed with HTTP ${response.status}.`);
  const value = await response.json().catch(() => null);
  if (!value || value.applicationId !== APPLICATION_ID || typeof value.versionName !== "string" || !value.versionName ||
      !Number.isSafeInteger(value.versionCode) || value.versionCode < 1 || !SHA256.test(value.apkSha256 ?? "") ||
      !SHA256.test(value.signingCertificateSha256 ?? "")) throw new Error("Production update manifest is malformed.");
  const apkUrl = new URL(value.apkUrl);
  if (apkUrl.protocol !== "https:") throw new Error("Production APK URL must use HTTPS.");
  return Object.freeze({ applicationId: value.applicationId, versionName: value.versionName,
    versionCode: value.versionCode, apkUrl: apkUrl.toString(), apkSha256: value.apkSha256.toLowerCase(),
    signingCertificateSha256: value.signingCertificateSha256.toLowerCase() });
}

export async function writeDeliveryArtifact(outputDirectory, fulfillmentId, release, licenseKey) {
  required(licenseKey, "licenseKey");
  const directory = path.join(outputDirectory, fulfillmentId);
  await mkdir(directory, { recursive: true });
  const target = path.join(directory, "customer-delivery.txt");
  const content = `DEXWORKSPACETOUCH\n\nPhiên bản: ${release.versionName} (${release.versionCode})\n\n` +
    `Tải ứng dụng:\n${release.apkUrl}\n\nAPK SHA-256:\n${release.apkSha256}\n\nLicense Key:\n${licenseKey}\n\n` +
    "Cách cài đặt và kích hoạt:\n1. Tải APK từ URL chính thức ở trên.\n" +
    "2. Cho phép cài ứng dụng từ nguồn đã dùng để tải APK nếu Android yêu cầu.\n3. Cài DexWorkspaceTouch và mở ứng dụng.\n" +
    "4. Nhập chính xác License Key ở trên. Sau khi kích hoạt, ứng dụng có thể hoạt động offline trong thời gian cho phép.\n\n" +
    "Cập nhật:\nTrong DexWorkspaceTouch, chọn Kiểm tra cập nhật, tải APK mới và cài đè bản hiện tại. KHÔNG gỡ ứng dụng trước khi cập nhật.\n" +
    "Gỡ ứng dụng có thể xóa workspace, dữ liệu cục bộ, device identity và token đã lưu.\n\n" +
    "Đổi điện thoại hoặc cài lại:\nLiên hệ hỗ trợ để reset thiết bị cũ trước khi kích hoạt trên thiết bị mới.\n\n" +
    "Hỗ trợ:\nEmail dexworkspacetouch.support@gmail.com và cung cấp mã đơn hàng/customer reference; không gửi License Key công khai.\n\n" +
    "CẢNH BÁO: File này chứa License Key của khách hàng. Hãy lưu/giao an toàn và xóa bản plaintext cục bộ khi không còn cần thiết.\n";
  await writeFile(target, content, { encoding: "utf8", flag: "wx" });
  return target;
}

function required(value, name) {
  if (typeof value !== "string" || value.trim().length === 0) throw new Error(`${name} is required.`);
}
