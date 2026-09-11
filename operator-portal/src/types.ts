export interface Env {
  FULFILLMENT_DB: D1Database;
  RELEASES?: Fetcher;
  LICENSE_ADMIN?: Fetcher;
  LICENSE_ADMIN_API_URL: string;
  LICENSE_ADMIN_TOKEN: string;
  UPDATE_MANIFEST_URL: string;
  CF_ACCESS_TEAM_DOMAIN: string;
  CF_ACCESS_AUD: string;
  OPERATOR_EMAIL_ALLOWLIST: string;
}

export interface OperatorIdentity { readonly email: string }

export interface ProductionRelease {
  readonly versionName: string;
  readonly versionCode: number;
  readonly apkUrl: string;
  readonly apkSha256: string;
  readonly apkSize: number;
  readonly signingCertificateSha256: string;
}

export interface FulfillmentRecord {
  readonly fulfillmentId: string;
  readonly licenseId: string;
  readonly orderReference: string;
  readonly customerReference: string | null;
  readonly status: string;
  readonly deliveryStatus: string;
  readonly maxDevices: number;
  readonly expiresAt: number | null;
  readonly initialReleaseVersion: string;
  readonly initialReleaseVersionCode: number;
  readonly initialApkUrl: string;
  readonly apkSha256: string;
  readonly apkSize: number;
  readonly signingCertificateSha256: string;
  readonly createdAt: string;
  readonly updatedAt: string;
  readonly replacesLicenseId: string | null;
  readonly replacedByLicenseId: string | null;
}

export interface AdminLicenseCreated {
  readonly licenseId: string;
  readonly licenseKey: string;
  readonly maxDevices: number;
  readonly expiresAtEpochSeconds: number | null;
}
