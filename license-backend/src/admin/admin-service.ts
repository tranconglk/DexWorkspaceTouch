import type { Clock } from "../license/clock";
import { hashLicenseKey } from "../license/license-key-hash";
import { AdminLicenseRepository } from "./admin-repository";
import { generateLicenseKey } from "./license-key-generator";

const MAX_CREATE_ATTEMPTS = 5;

export class AdminLicenseService {
  constructor(private readonly repository: AdminLicenseRepository, private readonly clock: Clock,
    private readonly pepper: string, private readonly createId: () => string = () => crypto.randomUUID(),
    private readonly createKey: () => string = () => generateLicenseKey()) {}

  async create(maxDevices: number, expiresAt: number | null, requestId: string) {
    const now = this.clock.nowEpochSeconds();
    for (let attempt = 0; attempt < MAX_CREATE_ATTEMPTS; attempt += 1) {
      const licenseKey = this.createKey();
      const licenseId = this.createId();
      try {
        await this.repository.createLicense({ id: licenseId, hash: await hashLicenseKey(licenseKey, this.pepper),
          maxDevices, expiresAt, now, eventId: this.createId(), metadata: metadata(requestId) });
        return { licenseId, licenseKey, status: "NEW" as const, maxDevices,
          expiresAtEpochSeconds: expiresAt, createdAt: now };
      } catch (error: unknown) {
        if (!isUniqueConstraint(error) || attempt === MAX_CREATE_ATTEMPTS - 1) throw error;
      }
    }
    throw new Error("License generation failed.");
  }

  list(limit: number, offset: number) { return this.repository.listLicenses(limit, offset); }
  show(id: string) { return this.repository.findLicense(id); }
  devices(id: string) { return this.repository.listDevices(id); }
  revoke(id: string, reason: string | null, requestId: string) {
    return this.repository.revokeLicense(id, this.clock.nowEpochSeconds(), this.createId(), metadata(requestId, reason));
  }
  resetDevice(licenseId: string, deviceId: string, reason: string | null, requestId: string) {
    return this.repository.resetDevice(licenseId, deviceId, this.clock.nowEpochSeconds(), this.createId(), metadata(requestId, reason));
  }
}

function metadata(requestId: string, reason?: string | null): string {
  return JSON.stringify(reason === undefined || reason === null ? { requestId } : { requestId, reason });
}
function isUniqueConstraint(error: unknown): boolean {
  return error instanceof Error && /unique constraint/i.test(error.message);
}
