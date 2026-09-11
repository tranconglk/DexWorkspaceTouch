import { p256DerToP1363 } from "../crypto/ecdsa-signature";
import type { Clock } from "./clock";
import { base64UrlDecode, base64UrlEncode } from "./base64url";
import { importP256Spki } from "./device-public-key";
import { hashLicenseKey } from "./license-key-hash";
import { LicenseActivationService } from "./activation";
import { ApplicationIdentityPolicy } from "./application-identity-policy";
import { LicenseRepository, type ChallengeRecord } from "./repository";
import type { ActivationProofRequest, ActivationRequest, ActivationResponse, BoundActivationRequest, ProofFailureCode } from "./types";

export const ACTIVATION_CHALLENGE_TTL_SECONDS = 120;
export const DEVICE_PROOF_VERSION = 1;

export type ChallengeIssueResult =
  | { readonly ok: true; readonly value: { challengeId: string; challenge: string; expiresAt: number; serverTime: number; proofVersion: 1 } }
  | { readonly ok: false; readonly code: ProofFailureCode };

export type ProofCompletionResult =
  | { readonly ok: true; readonly value: ActivationResponse; readonly licenseExpiresAtEpochSeconds: number | null; readonly binding: BoundActivationRequest }
  | { readonly ok: false; readonly code: ProofFailureCode };

export class LicenseProofService {
  constructor(
    private readonly repository: LicenseRepository,
    private readonly clock: Clock,
    private readonly applicationPolicy: ApplicationIdentityPolicy,
    private readonly createId: () => string = () => crypto.randomUUID(),
    private readonly randomBytes: () => Uint8Array = secureChallenge,
  ) {}

  async issueChallenge(request: ActivationRequest, context: { pepper: string }): Promise<ChallengeIssueResult> {
    const now = this.clock.nowEpochSeconds();
    if (!this.applicationPolicy.allows(request.application)) return failure("APPLICATION_NOT_ALLOWED");
    const license = await this.repository.findLicenseByHash(await hashLicenseKey(request.licenseKey, context.pepper));
    if (license === null) return failure("LICENSE_INVALID");
    if (license.status === "REVOKED") return failure("LICENSE_REVOKED");
    if (license.status === "EXPIRED" || license.expires_at !== null && license.expires_at <= now) {
      return failure("LICENSE_EXPIRED");
    }
    const nonce = this.randomBytes();
    if (nonce.length !== 32) throw new Error("Challenge generator must return exactly 32 bytes.");
    const challenge = base64UrlEncode(nonce);
    const record: ChallengeRecord = {
      id: this.createId(), license_id: license.id, nonce_hash: await sha256Base64Url(nonce), challenge,
      installation_id: request.device.installationId, device_hash: request.device.deviceHash,
      public_key: request.device.publicKey, package_name: request.application.packageName,
      version_name: request.application.versionName, version_code: request.application.versionCode,
      signing_certificate_sha256: request.application.signingCertificateSha256 ?? null,
      created_at: now, expires_at: now + ACTIVATION_CHALLENGE_TTL_SECONDS, consumed_at: null,
    };
    await this.repository.createChallenge(record);
    return { ok: true, value: {
      challengeId: record.id, challenge, expiresAt: record.expires_at,
      serverTime: now, proofVersion: DEVICE_PROOF_VERSION,
    } };
  }

  async completeProof(request: ActivationProofRequest, requestId: string): Promise<ProofCompletionResult> {
    const now = this.clock.nowEpochSeconds();
    const challenge = await this.repository.findChallenge(request.challengeId);
    if (challenge === null) return failure("CHALLENGE_INVALID");
    if (challenge.consumed_at !== null) return failure("CHALLENGE_USED");
    if (now >= challenge.expires_at) return failure("CHALLENGE_EXPIRED");
    if (!this.applicationPolicy.allows({
      packageName: challenge.package_name,
      versionName: challenge.version_name,
      versionCode: challenge.version_code,
      signingCertificateSha256: challenge.signing_certificate_sha256,
    })) return failure("APPLICATION_NOT_ALLOWED");
    let proof: Uint8Array;
    try {
      proof = p256DerToP1363(base64UrlDecode(request.signature, 128));
    } catch {
      return failure("PROOF_INVALID");
    }
    let valid: boolean;
    try {
      if (typeof challenge.challenge !== "string" || typeof challenge.public_key !== "string") {
        return failure("CHALLENGE_INVALID");
      }
      const nonce = base64UrlDecode(challenge.challenge, 64);
      if (nonce.length !== 32) return failure("CHALLENGE_INVALID");
      const publicKey = await importP256Spki(challenge.public_key);
      valid = await crypto.subtle.verify(
        { name: "ECDSA", hash: "SHA-256" }, publicKey, asArrayBuffer(proof), asArrayBuffer(nonce),
      );
    } catch {
      return failure("PROOF_INVALID");
    }
    if (!valid) return failure("PROOF_INVALID");
    if (!await this.repository.claimChallenge(challenge.id, now)) {
      const latest = await this.repository.findChallenge(challenge.id);
      return failure(latest?.consumed_at !== null ? "CHALLENGE_USED" : "CHALLENGE_EXPIRED");
    }
    const binding: BoundActivationRequest = { device: {
      installationId: challenge.installation_id,
      deviceHash: challenge.device_hash,
      publicKey: challenge.public_key,
    }, application: {
      packageName: challenge.package_name,
      versionName: challenge.version_name,
      versionCode: challenge.version_code,
      signingCertificateSha256: challenge.signing_certificate_sha256,
    } };
    const activation = await new LicenseActivationService(this.repository, this.clock).activateAuthorized(
      challenge.license_id,
      binding,
      { requestId },
    );
    return activation.ok ? { ...activation, binding } : activation;
  }
}

export function validateActivationProofRequest(input: unknown): ActivationProofRequest | null {
  if (typeof input !== "object" || input === null || Array.isArray(input)) return null;
  const value = input as Record<string, unknown>;
  if (value.proofVersion !== DEVICE_PROOF_VERSION || typeof value.challengeId !== "string" ||
      value.challengeId.length === 0 || value.challengeId.length > 128 ||
      typeof value.signature !== "string" || value.signature.length === 0 || value.signature.length > 128) return null;
  return value as unknown as ActivationProofRequest;
}

function secureChallenge(): Uint8Array {
  return crypto.getRandomValues(new Uint8Array(32));
}

async function sha256Base64Url(value: Uint8Array): Promise<string> {
  return base64UrlEncode(new Uint8Array(await crypto.subtle.digest("SHA-256", asArrayBuffer(value))));
}

function asArrayBuffer(value: Uint8Array): ArrayBuffer {
  return value.slice().buffer as ArrayBuffer;
}

function failure<T extends ProofFailureCode>(code: T): { readonly ok: false; readonly code: T } {
  return { ok: false, code };
}
