export const TOKEN_VERSION = 1;
export const OFFLINE_GRACE_SECONDS = 7 * 24 * 60 * 60;
export const TOKEN_ALGORITHM = "RS256";
export const TOKEN_TYPE = "DWT-LICENSE";

export interface LicenseTokenClaims {
  readonly tokenVersion: 1;
  readonly licenseId: string;
  readonly deviceId: string;
  readonly installationId: string;
  readonly deviceHash: string;
  readonly packageName: string;
  readonly issuedAtEpochSeconds: number;
  readonly expiresAtEpochSeconds: number;
  readonly offlineValidUntilEpochSeconds: number;
}

export function createLicenseTokenClaims(input: {
  readonly licenseId: string;
  readonly deviceId: string;
  readonly installationId: string;
  readonly deviceHash: string;
  readonly packageName: string;
  readonly issuedAtEpochSeconds: number;
  readonly licenseExpiresAtEpochSeconds: number | null;
}): LicenseTokenClaims {
  const graceEnd = input.issuedAtEpochSeconds + OFFLINE_GRACE_SECONDS;
  const effectiveEnd = input.licenseExpiresAtEpochSeconds === null
    ? graceEnd
    : Math.min(graceEnd, input.licenseExpiresAtEpochSeconds);
  return {
    tokenVersion: TOKEN_VERSION,
    licenseId: input.licenseId,
    deviceId: input.deviceId,
    installationId: input.installationId,
    deviceHash: input.deviceHash,
    packageName: input.packageName,
    issuedAtEpochSeconds: input.issuedAtEpochSeconds,
    expiresAtEpochSeconds: effectiveEnd,
    offlineValidUntilEpochSeconds: effectiveEnd,
  };
}
