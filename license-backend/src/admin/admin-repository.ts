import type { AdminDeviceSummary, AdminLicenseSummary } from "./types";

interface LicenseRow {
  id: string; status: AdminLicenseSummary["status"]; max_devices: number; active_device_count: number;
  created_at: number; updated_at: number | null; activated_at: number | null; expires_at: number | null; revoked_at: number | null;
}
interface DeviceRow { id: string; device_hash: string; created_at: number; last_seen_at: number; revoked_at: number | null }

export class AdminLicenseRepository {
  constructor(private readonly database: D1Database) {}

  async createLicense(input: { id: string; hash: string; maxDevices: number; expiresAt: number | null; now: number; eventId: string; metadata: string }): Promise<void> {
    await this.database.batch([
      this.database.prepare(`INSERT INTO licenses
        (id, license_key_hash, status, max_devices, created_at, expires_at, updated_at)
        VALUES (?, ?, 'NEW', ?, ?, ?, ?)`)
        .bind(input.id, input.hash, input.maxDevices, input.now, input.expiresAt, input.now),
      this.database.prepare(`INSERT INTO license_events
        (id, license_id, device_id, event_type, created_at, metadata)
        VALUES (?, ?, NULL, 'LICENSE_CREATED', ?, ?)`)
        .bind(input.eventId, input.id, input.now, input.metadata),
    ]);
  }

  async listLicenses(limit: number, offset: number): Promise<readonly AdminLicenseSummary[]> {
    const result = await this.database.prepare(`${licenseSelect()}
      ORDER BY l.created_at DESC, l.id ASC LIMIT ? OFFSET ?`).bind(limit, offset).all<LicenseRow>();
    return result.results.map(toLicense);
  }

  async findLicense(licenseId: string): Promise<AdminLicenseSummary | null> {
    const row = await this.database.prepare(`${licenseSelect()} WHERE l.id = ?`).bind(licenseId).first<LicenseRow>();
    return row === null ? null : toLicense(row);
  }

  async revokeLicense(licenseId: string, now: number, eventId: string, metadata: string): Promise<boolean | null> {
    const existing = await this.findLicense(licenseId);
    if (existing === null) return null;
    if (existing.status === "REVOKED") return false;
    await this.database.batch([
      this.database.prepare(`UPDATE licenses SET status = 'REVOKED', revoked_at = ?, updated_at = ?
        WHERE id = ? AND status IN ('NEW', 'ACTIVE')`).bind(now, now, licenseId),
      this.database.prepare(`INSERT OR IGNORE INTO license_events
        (id, license_id, device_id, event_type, created_at, metadata)
        SELECT ?, id, NULL, 'LICENSE_REVOKED', ?, ? FROM licenses WHERE id = ? AND status = 'REVOKED'`)
        .bind(eventId, now, metadata, licenseId),
    ]);
    return true;
  }

  async listDevices(licenseId: string): Promise<readonly AdminDeviceSummary[] | null> {
    if (await this.findLicense(licenseId) === null) return null;
    const result = await this.database.prepare(`SELECT id, device_hash, created_at, last_seen_at, revoked_at
      FROM devices WHERE license_id = ? ORDER BY created_at ASC, id ASC`).bind(licenseId).all<DeviceRow>();
    return result.results.map((row) => ({ deviceId: row.id, status: row.revoked_at === null ? "ACTIVE" : "RESET",
      fingerprint: row.device_hash.slice(0, 8), activatedAt: row.created_at,
      lastSeenAt: row.last_seen_at, resetAt: row.revoked_at }));
  }

  async resetDevice(licenseId: string, deviceId: string, now: number, eventId: string, metadata: string): Promise<boolean | null> {
    const device = await this.database.prepare("SELECT id, revoked_at FROM devices WHERE id = ? AND license_id = ?")
      .bind(deviceId, licenseId).first<{ id: string; revoked_at: number | null }>();
    if (device === null) return null;
    if (device.revoked_at !== null) return false;
    await this.database.batch([
      this.database.prepare("UPDATE devices SET revoked_at = ? WHERE id = ? AND license_id = ? AND revoked_at IS NULL")
        .bind(now, deviceId, licenseId),
      this.database.prepare(`INSERT OR IGNORE INTO license_events
        (id, license_id, device_id, event_type, created_at, metadata)
        SELECT ?, license_id, id, 'DEVICE_RESET', ?, ? FROM devices
        WHERE id = ? AND license_id = ? AND revoked_at IS NOT NULL`)
        .bind(eventId, now, metadata, deviceId, licenseId),
    ]);
    return true;
  }
}

function licenseSelect(): string {
  return `SELECT l.id, l.status, l.max_devices, l.created_at, COALESCE(l.updated_at, l.created_at) AS updated_at,
    l.activated_at, l.expires_at, l.revoked_at,
    (SELECT COUNT(*) FROM devices d WHERE d.license_id = l.id AND d.revoked_at IS NULL) AS active_device_count
    FROM licenses l`;
}

function toLicense(row: LicenseRow): AdminLicenseSummary {
  return { licenseId: row.id, status: row.status, maxDevices: row.max_devices,
    activeDeviceCount: row.active_device_count, createdAt: row.created_at,
    updatedAt: row.updated_at ?? row.created_at, activatedAt: row.activated_at,
    expiresAtEpochSeconds: row.expires_at, revokedAt: row.revoked_at };
}
