const MAX_LENGTH = 128;
const SUPPORTED_FORMAT = /^[A-Z][A-Z0-9]{1,15}(?:-[A-Z0-9]{4}){3}$/;

export interface LicenseKeyValidation {
  readonly canonical: string;
  readonly valid: boolean;
}

/** Mirrors Android LicenseKey.parse(): trim, uppercase, then validate the complete value. */
export function normalizeLicenseKey(rawValue: string): string {
  return rawValue.trim().toUpperCase();
}

export function validateLicenseKey(rawValue: string): LicenseKeyValidation {
  const canonical = normalizeLicenseKey(rawValue);
  return {
    canonical,
    valid: canonical.length <= MAX_LENGTH && SUPPORTED_FORMAT.test(canonical),
  };
}

export function canonicalizeLicenseKey(rawValue: string): string {
  const result = validateLicenseKey(rawValue);
  if (!result.valid) {
    throw new Error("License key format is invalid.");
  }
  return result.canonical;
}

export function redactLicenseKey(rawValue: string): string {
  const canonical = canonicalizeLicenseKey(rawValue);
  return `${canonical.slice(0, canonical.indexOf("-"))}-••••-••••-${canonical.slice(-4)}`;
}
