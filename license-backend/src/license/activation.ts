import type { Clock } from "./clock";
import { LicenseRepository, type DeviceRecord, type LicenseRecord } from "./repository";
import type { ActivationResult, BoundActivationRequest } from "./types";
import { DEVICE_KEY_ALGORITHM } from "./types";

export interface ActivationContext {
  readonly requestId: string;
}

export class LicenseActivationService {
  constructor(
    private readonly repository: LicenseRepository,
    private readonly clock: Clock,
    private readonly createId: () => string = () => crypto.randomUUID(),
  ) {}

  async activateAuthorized(
    licenseId: string,
    request: BoundActivationRequest,
    context: ActivationContext,
  ): Promise<ActivationResult> {
    const now = this.clock.nowEpochSeconds();
    const license = await this.repository.findLicenseById(licenseId);
    if (license === null) return failure("LICENSE_INVALID");
    if (license.status === "REVOKED") return failure("LICENSE_REVOKED");
    if (license.status === "EXPIRED" || (license.expires_at !== null && license.expires_at <= now)) {
      return failure("LICENSE_EXPIRED");
    }

    const existing = await this.repository.findDevice(license.id, request.device.installationId);
    if (existing !== null) {
      return this.activateExisting(license, existing, request, context.requestId, now);
    }

    const deviceId = this.createId();
    try {
      const inserted = await this.repository.tryBindDevice(
        license,
        request,
        deviceId,
        this.createId(),
        now,
        eventMetadata(context.requestId, request, "NEW_BINDING"),
      );
      if (!inserted) return failure("DEVICE_LIMIT_REACHED");
      return success(license, deviceId, now, now);
    } catch (error: unknown) {
      // A same-installation concurrent request may win the UNIQUE constraint.
      const racedDevice = await this.repository.findDevice(license.id, request.device.installationId);
      if (racedDevice === null) throw error;
      return this.activateExisting(license, racedDevice, request, context.requestId, now);
    }
  }

  private async activateExisting(
    license: LicenseRecord,
    device: DeviceRecord,
    request: BoundActivationRequest,
    requestId: string,
    now: number,
  ): Promise<ActivationResult> {
    if (
      device.revoked_at !== null ||
      device.device_hash !== request.device.deviceHash ||
      device.public_key !== request.device.publicKey ||
      device.key_algorithm !== DEVICE_KEY_ALGORITHM
    ) {
      return failure("DEVICE_MISMATCH");
    }
    const activatedAt = await this.repository.refreshExistingDevice(
      license,
      device.id,
      now,
      this.createId(),
      eventMetadata(requestId, request, "IDEMPOTENT"),
    );
    return success(license, device.id, activatedAt, now);
  }
}

function success(
  license: LicenseRecord,
  deviceId: string,
  activatedAt: number,
  serverTime: number,
): ActivationResult {
  return {
    ok: true,
    value: { licenseId: license.id, deviceId, status: "ACTIVE", activatedAt, serverTime },
    licenseExpiresAtEpochSeconds: license.expires_at,
  };
}

function failure(code: Exclude<ActivationResult, { ok: true }>["code"]): ActivationResult {
  return { ok: false, code };
}

function eventMetadata(
  requestId: string,
  request: BoundActivationRequest,
  activationType: "NEW_BINDING" | "IDEMPOTENT",
): string {
  return JSON.stringify({
    requestId,
    packageName: request.application.packageName,
    versionName: request.application.versionName,
    versionCode: request.application.versionCode,
    activationType,
  });
}
