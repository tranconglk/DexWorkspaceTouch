import type { FulfillmentRecord, OperatorIdentity, ProductionRelease } from "./types";

export class FulfillmentRepository {
  constructor(private readonly db: D1Database) {}

  async find(reference: string): Promise<FulfillmentRecord | null> {
    const row = await this.db.prepare(`SELECT * FROM fulfillments WHERE fulfillment_id = ? OR order_reference = ? OR license_id = ? OR customer_reference = ? LIMIT 1`)
      .bind(reference, reference, reference, reference).first<Record<string, unknown>>();
    return row ? map(row) : null;
  }

  async findActiveOrder(orderReference: string): Promise<FulfillmentRecord | null> {
    const row = await this.db.prepare(`SELECT * FROM fulfillments WHERE order_reference = ? AND status NOT IN ('REVOKED','CANCELLED','REPLACED') LIMIT 1`)
      .bind(orderReference).first<Record<string, unknown>>();
    return row ? map(row) : null;
  }

  async list(query: string | null, limit = 50): Promise<FulfillmentRecord[]> {
    const term = query?.trim();
    const statement = term
      ? this.db.prepare(`SELECT * FROM fulfillments WHERE fulfillment_id LIKE ? OR order_reference LIKE ? OR license_id LIKE ? OR customer_reference LIKE ? ORDER BY updated_at DESC LIMIT ?`)
        .bind(...Array(4).fill(`%${term}%`), limit)
      : this.db.prepare("SELECT * FROM fulfillments ORDER BY updated_at DESC LIMIT ?").bind(limit);
    const result = await statement.all<Record<string, unknown>>();
    return result.results.map(map);
  }

  async events(fulfillmentId: string): Promise<unknown[]> {
    const result = await this.db.prepare(`SELECT event_type AS eventType, occurred_at AS occurredAt, operator_identity AS operatorIdentity,
      request_id AS requestId, device_id AS deviceId, related_license_id AS relatedLicenseId
      FROM fulfillment_events WHERE fulfillment_id = ? ORDER BY occurred_at ASC`).bind(fulfillmentId).all();
    return result.results;
  }

  async create(input: { fulfillmentId: string; licenseId: string; orderReference: string; customerReference: string | null;
    maxDevices: number; expiresAt: number | null; release: ProductionRelease; identity: OperatorIdentity; now: string }): Promise<FulfillmentRecord> {
    await this.db.batch([
      this.db.prepare(`INSERT INTO fulfillments (fulfillment_id, license_id, order_reference, customer_reference, status,
        delivery_status, max_devices, expires_at, initial_release_version, initial_release_version_code, initial_apk_url,
        apk_sha256, apk_size, signing_certificate_sha256, created_at, updated_at)
        VALUES (?, ?, ?, ?, 'LICENSE_CREATED', 'PENDING', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`)
        .bind(input.fulfillmentId, input.licenseId, input.orderReference, input.customerReference, input.maxDevices,
          input.expiresAt, input.release.versionName, input.release.versionCode, input.release.apkUrl,
          input.release.apkSha256, input.release.apkSize, input.release.signingCertificateSha256, input.now, input.now),
      event(this.db, input.fulfillmentId, "LICENSE_CREATED", input.identity.email, input.now),
    ]);
    return (await this.find(input.fulfillmentId))!;
  }

  async markDelivered(record: FulfillmentRecord, identity: OperatorIdentity, now: string): Promise<FulfillmentRecord> {
    if (record.deliveryStatus === "DELIVERED") return record;
    await this.db.batch([
      this.db.prepare("UPDATE fulfillments SET status = 'DELIVERED', delivery_status = 'DELIVERED', updated_at = ? WHERE fulfillment_id = ?")
        .bind(now, record.fulfillmentId),
      event(this.db, record.fulfillmentId, "DELIVERED", identity.email, now),
    ]);
    return (await this.find(record.fulfillmentId))!;
  }

  async recordReset(record: FulfillmentRecord, deviceId: string, identity: OperatorIdentity, now: string): Promise<void> {
    await this.db.batch([
      this.db.prepare("UPDATE fulfillments SET updated_at = ? WHERE fulfillment_id = ?").bind(now, record.fulfillmentId),
      event(this.db, record.fulfillmentId, "DEVICE_RESET", identity.email, now, deviceId),
    ]);
  }

  async recordRevoked(record: FulfillmentRecord, cancelled: boolean, identity: OperatorIdentity, now: string): Promise<FulfillmentRecord> {
    await this.db.batch([
      this.db.prepare("UPDATE fulfillments SET status = ?, updated_at = ? WHERE fulfillment_id = ?")
        .bind(cancelled ? "CANCELLED" : "REVOKED", now, record.fulfillmentId),
      event(this.db, record.fulfillmentId, "LICENSE_REVOKED", identity.email, now),
    ]);
    return (await this.find(record.fulfillmentId))!;
  }

  async linkReplacement(oldRecord: FulfillmentRecord, newRecord: FulfillmentRecord, identity: OperatorIdentity, now: string): Promise<void> {
    await this.db.batch([
      this.db.prepare("UPDATE fulfillments SET status = 'REPLACED', replaced_by_license_id = ?, updated_at = ? WHERE fulfillment_id = ?")
        .bind(newRecord.licenseId, now, oldRecord.fulfillmentId),
      this.db.prepare("UPDATE fulfillments SET replaces_license_id = ?, updated_at = ? WHERE fulfillment_id = ?")
        .bind(oldRecord.licenseId, now, newRecord.fulfillmentId),
      event(this.db, oldRecord.fulfillmentId, "LICENSE_REPLACED", identity.email, now, null, newRecord.licenseId),
    ]);
  }
}

function event(db: D1Database, fulfillmentId: string, type: string, identity: string, now: string,
  deviceId: string | null = null, relatedLicenseId: string | null = null): D1PreparedStatement {
  return db.prepare(`INSERT INTO fulfillment_events (event_id, fulfillment_id, event_type, occurred_at, operator_identity,
    device_id, related_license_id) VALUES (?, ?, ?, ?, ?, ?, ?)`)
    .bind(crypto.randomUUID(), fulfillmentId, type, now, identity, deviceId, relatedLicenseId);
}

function map(row: Record<string, unknown>): FulfillmentRecord {
  return {
    fulfillmentId: String(row.fulfillment_id), licenseId: String(row.license_id), orderReference: String(row.order_reference),
    customerReference: nullable(row.customer_reference), status: String(row.status), deliveryStatus: String(row.delivery_status),
    maxDevices: Number(row.max_devices), expiresAt: row.expires_at === null ? null : Number(row.expires_at),
    initialReleaseVersion: String(row.initial_release_version), initialReleaseVersionCode: Number(row.initial_release_version_code),
    initialApkUrl: String(row.initial_apk_url), apkSha256: String(row.apk_sha256), apkSize: Number(row.apk_size),
    signingCertificateSha256: String(row.signing_certificate_sha256), createdAt: String(row.created_at), updatedAt: String(row.updated_at),
    replacesLicenseId: nullable(row.replaces_license_id), replacedByLicenseId: nullable(row.replaced_by_license_id),
  };
}
function nullable(value: unknown): string | null { return value === null || value === undefined ? null : String(value); }
