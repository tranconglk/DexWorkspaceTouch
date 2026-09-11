import { isValidP256Spki } from "./device-public-key";
import type { ActivationRequest } from "./types";
import { validateLicenseKey } from "./license-key";

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const SHA256_LOWERCASE_HEX = /^[0-9a-f]{64}$/;
const CERTIFICATE_SHA256_HEX = /^[0-9a-f]{64}$/i;
const PACKAGE_NAME = /^[a-zA-Z][a-zA-Z0-9_]*(?:\.[a-zA-Z][a-zA-Z0-9_]*)+$/;

export type ActivationRequestValidation =
  | { readonly ok: true; readonly value: ActivationRequest }
  | { readonly ok: false; readonly code: "INVALID_REQUEST" | "INVALID_DEVICE_PUBLIC_KEY" };

export async function validateActivationRequest(
  input: unknown,
): Promise<ActivationRequestValidation> {
  if (!isRecord(input) || typeof input.licenseKey !== "string" || !validateLicenseKey(input.licenseKey).valid) {
    return { ok: false, code: "INVALID_REQUEST" };
  }
  if (!isRecord(input.device) || !isRecord(input.application)) {
    return { ok: false, code: "INVALID_REQUEST" };
  }
  const { device, application } = input;
  if (
    typeof device.installationId !== "string" || !UUID.test(device.installationId) ||
    typeof device.deviceHash !== "string" || !SHA256_LOWERCASE_HEX.test(device.deviceHash) ||
    typeof device.publicKey !== "string"
  ) {
    return { ok: false, code: "INVALID_REQUEST" };
  }
  if (
    typeof application.packageName !== "string" ||
    !PACKAGE_NAME.test(application.packageName) ||
    typeof application.versionName !== "string" ||
    application.versionName.length === 0 || application.versionName.length > 100 ||
    typeof application.versionCode !== "number" ||
    !Number.isSafeInteger(application.versionCode) || application.versionCode < 0 ||
    !isOptionalCertificateDigest(application.signingCertificateSha256)
  ) {
    return { ok: false, code: "INVALID_REQUEST" };
  }
  if (!await isValidP256Spki(device.publicKey)) {
    return { ok: false, code: "INVALID_DEVICE_PUBLIC_KEY" };
  }
  return { ok: true, value: input as unknown as ActivationRequest };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function isOptionalCertificateDigest(value: unknown): boolean {
  return value === undefined || value === null ||
    (typeof value === "string" && CERTIFICATE_SHA256_HEX.test(value));
}
