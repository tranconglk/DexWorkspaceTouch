export interface AdminLicenseSummary {
  readonly licenseId: string;
  readonly status: "NEW" | "ACTIVE" | "REVOKED" | "EXPIRED";
  readonly maxDevices: number;
  readonly activeDeviceCount: number;
  readonly createdAt: number;
  readonly updatedAt: number;
  readonly activatedAt: number | null;
  readonly expiresAtEpochSeconds: number | null;
  readonly revokedAt: number | null;
}

export interface AdminDeviceSummary {
  readonly deviceId: string;
  readonly status: "ACTIVE" | "RESET";
  readonly fingerprint: string;
  readonly activatedAt: number;
  readonly lastSeenAt: number;
  readonly resetAt: number | null;
}
