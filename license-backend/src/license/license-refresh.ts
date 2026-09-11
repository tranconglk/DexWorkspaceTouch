import { p256DerToP1363 } from "../crypto/ecdsa-signature";
import type { Clock } from "./clock";
import { base64UrlDecode, base64UrlEncode } from "./base64url";
import { importP256Spki } from "./device-public-key";
import { ApplicationIdentityPolicy } from "./application-identity-policy";
import { DEVICE_KEY_ALGORITHM } from "./types";
import { LicenseRepository, type RefreshChallengeRecord } from "./repository";
import type { LicenseTokenClaims } from "./license-token";

export const REFRESH_CHALLENGE_TTL_SECONDS = 120;
export const REFRESH_PROOF_VERSION = 1;
export type RefreshFailureCode = "TOKEN_INVALID" | "TOKEN_EXPIRED" | "UNKNOWN_KEY" |
  "CHALLENGE_INVALID" | "CHALLENGE_EXPIRED" | "CHALLENGE_USED" | "PROOF_INVALID" |
  "LICENSE_REVOKED" | "LICENSE_EXPIRED" | "DEVICE_REVOKED" | "DEVICE_MISMATCH" | "APPLICATION_NOT_ALLOWED";

export interface RefreshApplicationIdentity {
  readonly packageName: string;
  readonly signingCertificateSha256: string;
}

export class LicenseRefreshService {
  constructor(
    private readonly repository: LicenseRepository,
    private readonly clock: Clock,
    private readonly applicationPolicy: ApplicationIdentityPolicy,
    private readonly createId: () => string = () => crypto.randomUUID(),
    private readonly randomBytes: () => Uint8Array = () => crypto.getRandomValues(new Uint8Array(32)),
  ) {}

  async issueChallenge(claims: LicenseTokenClaims, application: RefreshApplicationIdentity) {
    const check = await this.authorize(claims, application);
    if (!check.ok) return check;
    const now = this.clock.nowEpochSeconds();
    const nonce = this.randomBytes();
    if (nonce.length !== 32) throw new Error("Challenge generator must return exactly 32 bytes.");
    const record: RefreshChallengeRecord = {
      id: this.createId(), license_id: claims.licenseId, device_id: claims.deviceId,
      challenge: base64UrlEncode(nonce), nonce_hash: await digest(nonce),
      installation_id: claims.installationId, device_hash: claims.deviceHash,
      package_name: application.packageName, signing_certificate_sha256: application.signingCertificateSha256,
      created_at: now, expires_at: now + REFRESH_CHALLENGE_TTL_SECONDS, consumed_at: null,
    };
    await this.repository.createRefreshChallenge(record);
    return { ok: true as const, value: { challengeId: record.id, challenge: record.challenge,
      expiresAt: record.expires_at, serverTime: now, proofVersion: REFRESH_PROOF_VERSION } };
  }

  async completeProof(challengeId: string, signature: string, requestId: string) {
    const now = this.clock.nowEpochSeconds();
    const challenge = await this.repository.findRefreshChallenge(challengeId);
    if (challenge === null) return failure("CHALLENGE_INVALID");
    if (challenge.consumed_at !== null) return failure("CHALLENGE_USED");
    if (now >= challenge.expires_at) return failure("CHALLENGE_EXPIRED");
    const claims = challengeClaims(challenge);
    const application = { packageName: challenge.package_name, signingCertificateSha256: challenge.signing_certificate_sha256 };
    const before = await this.authorize(claims, application);
    if (!before.ok) return before;
    let valid = false;
    try {
      const proof = p256DerToP1363(base64UrlDecode(signature, 128));
      const nonce = base64UrlDecode(challenge.challenge, 64);
      if (nonce.length !== 32) return failure("CHALLENGE_INVALID");
      const publicKey = await importP256Spki(before.device.public_key);
      valid = await crypto.subtle.verify({ name: "ECDSA", hash: "SHA-256" }, publicKey,
        proof.slice().buffer as ArrayBuffer, nonce.slice().buffer as ArrayBuffer);
    } catch { return failure("PROOF_INVALID"); }
    if (!valid) return failure("PROOF_INVALID");
    if (!await this.repository.claimRefreshChallenge(challenge.id, now)) {
      const latest = await this.repository.findRefreshChallenge(challenge.id);
      return failure(latest?.consumed_at !== null ? "CHALLENGE_USED" : "CHALLENGE_EXPIRED");
    }
    const finalCheck = await this.authorize(claims, application);
    if (!finalCheck.ok) return finalCheck;
    await this.repository.recordTokenRefresh(claims.licenseId, claims.deviceId, now, this.createId(), requestId);
    return { ok: true as const, claims, licenseExpiresAtEpochSeconds: finalCheck.license.expires_at, serverTime: now };
  }

  private async authorize(claims: LicenseTokenClaims, application: RefreshApplicationIdentity) {
    const now = this.clock.nowEpochSeconds();
    if (application.packageName !== claims.packageName || !this.applicationPolicy.allows({
      packageName: application.packageName, versionName: "refresh", versionCode: 0,
      signingCertificateSha256: application.signingCertificateSha256,
    })) return failure("APPLICATION_NOT_ALLOWED");
    const license = await this.repository.findLicenseById(claims.licenseId);
    if (license === null) return failure("TOKEN_INVALID");
    if (license.status === "REVOKED") return failure("LICENSE_REVOKED");
    if (license.status === "EXPIRED" || license.expires_at !== null && license.expires_at <= now) return failure("LICENSE_EXPIRED");
    const device = await this.repository.findDeviceById(claims.licenseId, claims.deviceId);
    if (device === null || device.revoked_at !== null) return failure("DEVICE_REVOKED");
    if (device.key_algorithm !== DEVICE_KEY_ALGORITHM || device.installation_id !== claims.installationId ||
        device.device_hash !== claims.deviceHash) return failure("DEVICE_MISMATCH");
    return { ok: true as const, license, device };
  }
}

export function validateRefreshApplication(input: unknown): RefreshApplicationIdentity | null {
  if (typeof input !== "object" || input === null || Array.isArray(input)) return null;
  const value = input as Record<string, unknown>;
  if (Object.keys(value).some((key) => !["packageName", "signingCertificateSha256"].includes(key)) ||
      typeof value.packageName !== "string" || typeof value.signingCertificateSha256 !== "string") return null;
  return value as unknown as RefreshApplicationIdentity;
}

export function validateRefreshProof(input: unknown): { challengeId: string; signature: string } | null {
  if (typeof input !== "object" || input === null || Array.isArray(input)) return null;
  const value = input as Record<string, unknown>;
  if (Object.keys(value).some((key) => !["challengeId", "proofVersion", "signature"].includes(key)) ||
      value.proofVersion !== REFRESH_PROOF_VERSION || typeof value.challengeId !== "string" ||
      value.challengeId.length < 1 || value.challengeId.length > 128 || typeof value.signature !== "string" ||
      value.signature.length < 1 || value.signature.length > 128) return null;
  return { challengeId: value.challengeId, signature: value.signature };
}

function challengeClaims(challenge: RefreshChallengeRecord): LicenseTokenClaims {
  return { tokenVersion: 1, licenseId: challenge.license_id, deviceId: challenge.device_id,
    installationId: challenge.installation_id, deviceHash: challenge.device_hash, packageName: challenge.package_name,
    issuedAtEpochSeconds: challenge.created_at, expiresAtEpochSeconds: challenge.expires_at,
    offlineValidUntilEpochSeconds: challenge.expires_at };
}
async function digest(value: Uint8Array): Promise<string> {
  return base64UrlEncode(new Uint8Array(await crypto.subtle.digest("SHA-256", value.slice().buffer as ArrayBuffer)));
}
function failure<T extends RefreshFailureCode>(code: T): { readonly ok: false; readonly code: T } { return { ok: false, code }; }
