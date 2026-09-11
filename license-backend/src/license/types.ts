export const DEVICE_KEY_ALGORITHM = "EC_P256";

export interface ActivationRequest {
  readonly licenseKey: string;
  readonly device: {
    readonly installationId: string;
    readonly deviceHash: string;
    readonly publicKey: string;
  };
  readonly application: {
    readonly packageName: string;
    readonly versionName: string;
    readonly versionCode: number;
    readonly signingCertificateSha256?: string | null;
  };
}

export interface ActivationResponse {
  readonly licenseId: string;
  readonly deviceId: string;
  readonly status: "ACTIVE";
  readonly activatedAt: number;
  readonly serverTime: number;
  readonly licenseToken?: string;
}

export type BoundActivationRequest = Omit<ActivationRequest, "licenseKey">;

export interface ActivationProofRequest {
  readonly proofVersion: 1;
  readonly challengeId: string;
  readonly signature: string;
}

export type ProofFailureCode = ActivationFailureCode |
  "CHALLENGE_INVALID" | "CHALLENGE_EXPIRED" | "CHALLENGE_USED" | "PROOF_INVALID";

export type ActivationFailureCode =
  | "APPLICATION_NOT_ALLOWED"
  | "LICENSE_INVALID"
  | "LICENSE_REVOKED"
  | "LICENSE_EXPIRED"
  | "DEVICE_MISMATCH"
  | "DEVICE_LIMIT_REACHED";

export type ActivationResult =
  | {
      readonly ok: true;
      readonly value: ActivationResponse;
      readonly licenseExpiresAtEpochSeconds: number | null;
    }
  | { readonly ok: false; readonly code: ActivationFailureCode };
