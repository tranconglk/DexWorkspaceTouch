import type { BoundActivationRequest } from "./types";
import { DEVICE_KEY_ALGORITHM } from "./types";

export interface LicenseRecord {
  readonly id: string;
  readonly status: "NEW" | "ACTIVE" | "REVOKED" | "EXPIRED";
  readonly max_devices: number;
  readonly expires_at: number | null;
  readonly activated_at: number | null;
}

export interface DeviceRecord {
  readonly id: string;
  readonly installation_id: string;
  readonly device_hash: string;
  readonly public_key: string;
  readonly key_algorithm: string;
  readonly revoked_at: number | null;
}

export interface ChallengeRecord {
  readonly id: string;
  readonly license_id: string;
  readonly nonce_hash: string;
  readonly challenge: string;
  readonly installation_id: string;
  readonly device_hash: string;
  readonly public_key: string;
  readonly package_name: string;
  readonly version_name: string;
  readonly version_code: number;
  readonly signing_certificate_sha256: string | null;
  readonly created_at: number;
  readonly expires_at: number;
  readonly consumed_at: number | null;
}

export interface RefreshChallengeRecord {
  readonly id: string;
  readonly license_id: string;
  readonly device_id: string;
  readonly challenge: string;
  readonly nonce_hash: string;
  readonly installation_id: string;
  readonly device_hash: string;
  readonly package_name: string;
  readonly signing_certificate_sha256: string;
  readonly created_at: number;
  readonly expires_at: number;
  readonly consumed_at: number | null;
}

export class LicenseRepository {
  constructor(private readonly database: D1Database) {}

  async findLicenseByHash(licenseKeyHash: string): Promise<LicenseRecord | null> {
    return this.database.prepare(
      "SELECT id, status, max_devices, expires_at, activated_at FROM licenses WHERE license_key_hash = ?",
    ).bind(licenseKeyHash).first<LicenseRecord>();
  }

  async findLicenseById(licenseId: string): Promise<LicenseRecord | null> {
    return this.database.prepare(
      "SELECT id, status, max_devices, expires_at, activated_at FROM licenses WHERE id = ?",
    ).bind(licenseId).first<LicenseRecord>();
  }

  async createChallenge(record: ChallengeRecord): Promise<void> {
    await this.database.prepare(
      `INSERT INTO license_challenges
       (id, license_id, nonce_hash, purpose, created_at, expires_at, consumed_at,
        challenge, installation_id, device_hash, public_key, package_name, version_name,
        version_code, signing_certificate_sha256)
       VALUES (?, ?, ?, 'ACTIVATION', ?, ?, NULL, ?, ?, ?, ?, ?, ?, ?, ?)`,
    ).bind(record.id, record.license_id, record.nonce_hash, record.created_at, record.expires_at,
      record.challenge, record.installation_id, record.device_hash, record.public_key,
      record.package_name, record.version_name, record.version_code,
      record.signing_certificate_sha256).run();
  }

  async findChallenge(challengeId: string): Promise<ChallengeRecord | null> {
    return this.database.prepare(
      `SELECT id, license_id, nonce_hash, challenge, installation_id, device_hash, public_key,
              package_name, version_name, version_code, signing_certificate_sha256,
              created_at, expires_at, consumed_at
       FROM license_challenges WHERE id = ? AND purpose = 'ACTIVATION'`,
    ).bind(challengeId).first<ChallengeRecord>();
  }

  async claimChallenge(challengeId: string, now: number): Promise<boolean> {
    const result = await this.database.prepare(
      `UPDATE license_challenges SET consumed_at = ?
       WHERE id = ? AND purpose = 'ACTIVATION' AND consumed_at IS NULL AND expires_at > ?`,
    ).bind(now, challengeId, now).run();
    return (result.meta.changes ?? 0) === 1;
  }

  async findDevice(licenseId: string, installationId: string): Promise<DeviceRecord | null> {
    return this.database.prepare(
      "SELECT id, installation_id, device_hash, public_key, key_algorithm, revoked_at FROM devices WHERE license_id = ? AND installation_id = ?",
    ).bind(licenseId, installationId).first<DeviceRecord>();
  }

  async findDeviceById(licenseId: string, deviceId: string): Promise<DeviceRecord | null> {
    return this.database.prepare(
      "SELECT id, installation_id, device_hash, public_key, key_algorithm, revoked_at FROM devices WHERE license_id = ? AND id = ?",
    ).bind(licenseId, deviceId).first<DeviceRecord>();
  }

  async createRefreshChallenge(record: RefreshChallengeRecord): Promise<void> {
    await this.database.prepare(
      `INSERT INTO license_refresh_challenges
       (id, license_id, device_id, challenge, nonce_hash, installation_id, device_hash,
        package_name, signing_certificate_sha256, created_at, expires_at, consumed_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL)`,
    ).bind(record.id, record.license_id, record.device_id, record.challenge, record.nonce_hash,
      record.installation_id, record.device_hash, record.package_name, record.signing_certificate_sha256,
      record.created_at, record.expires_at).run();
  }

  async findRefreshChallenge(challengeId: string): Promise<RefreshChallengeRecord | null> {
    return this.database.prepare(
      `SELECT id, license_id, device_id, challenge, nonce_hash, installation_id, device_hash,
              package_name, signing_certificate_sha256, created_at, expires_at, consumed_at
       FROM license_refresh_challenges WHERE id = ?`,
    ).bind(challengeId).first<RefreshChallengeRecord>();
  }

  async claimRefreshChallenge(challengeId: string, now: number): Promise<boolean> {
    const result = await this.database.prepare(
      `UPDATE license_refresh_challenges SET consumed_at = ?
       WHERE id = ? AND consumed_at IS NULL AND expires_at > ?`,
    ).bind(now, challengeId, now).run();
    return (result.meta.changes ?? 0) === 1;
  }

  async recordTokenRefresh(licenseId: string, deviceId: string, now: number, eventId: string, requestId: string): Promise<void> {
    await this.database.batch([
      this.database.prepare("UPDATE devices SET last_seen_at = ? WHERE id = ? AND license_id = ? AND revoked_at IS NULL")
        .bind(now, deviceId, licenseId),
      this.database.prepare("UPDATE licenses SET last_check_at = ?, updated_at = ? WHERE id = ?")
        .bind(now, now, licenseId),
      this.database.prepare(
        "INSERT INTO license_events (id, license_id, device_id, event_type, created_at, metadata) VALUES (?, ?, ?, 'TOKEN_REFRESHED', ?, ?)",
      ).bind(eventId, licenseId, deviceId, now, JSON.stringify({ requestId })),
    ]);
  }

  async refreshExistingDevice(
    license: LicenseRecord,
    deviceId: string,
    now: number,
    eventId: string,
    metadata: string,
  ): Promise<number> {
    await this.database.batch([
      this.database.prepare(
        "UPDATE devices SET last_seen_at = ? WHERE id = ? AND revoked_at IS NULL",
      ).bind(now, deviceId),
      this.database.prepare(
        "UPDATE licenses SET status = CASE WHEN status = 'NEW' THEN 'ACTIVE' ELSE status END, activated_at = COALESCE(activated_at, ?), last_check_at = ? WHERE id = ?",
      ).bind(now, now, license.id),
      this.database.prepare(
        "INSERT INTO license_events (id, license_id, device_id, event_type, created_at, metadata) VALUES (?, ?, ?, 'REFRESHED', ?, ?)",
      ).bind(eventId, license.id, deviceId, now, metadata),
    ]);
    return license.activated_at ?? now;
  }

  async tryBindDevice(
    license: LicenseRecord,
    request: BoundActivationRequest,
    deviceId: string,
    eventId: string,
    now: number,
    metadata: string,
  ): Promise<boolean> {
    const results = await this.database.batch([
      this.database.prepare(
        `INSERT INTO devices
          (id, license_id, installation_id, device_hash, public_key, key_algorithm, created_at, last_seen_at)
         SELECT ?, id, ?, ?, ?, ?, ?, ?
         FROM licenses
         WHERE id = ?
           AND status IN ('NEW', 'ACTIVE')
           AND (expires_at IS NULL OR expires_at > ?)
           AND (SELECT COUNT(*) FROM devices WHERE license_id = ? AND revoked_at IS NULL) < max_devices`,
      ).bind(
        deviceId,
        request.device.installationId,
        request.device.deviceHash,
        request.device.publicKey,
        DEVICE_KEY_ALGORITHM,
        now,
        now,
        license.id,
        now,
        license.id,
      ),
      this.database.prepare(
        `UPDATE licenses
         SET status = 'ACTIVE', activated_at = COALESCE(activated_at, ?), last_check_at = ?
         WHERE id = ? AND EXISTS (SELECT 1 FROM devices WHERE id = ? AND license_id = ?)`,
      ).bind(now, now, license.id, deviceId, license.id),
      this.database.prepare(
        `INSERT INTO license_events (id, license_id, device_id, event_type, created_at, metadata)
         SELECT ?, ?, ?, 'ACTIVATED', ?, ?
         WHERE EXISTS (SELECT 1 FROM devices WHERE id = ? AND license_id = ?)`,
      ).bind(eventId, license.id, deviceId, now, metadata, deviceId, license.id),
    ]);
    return (results[0]?.meta.changes ?? 0) === 1;
  }
}
