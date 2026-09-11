import { base64UrlDecode } from "./base64url";
import { TOKEN_ALGORITHM, TOKEN_TYPE, TOKEN_VERSION, type LicenseTokenClaims } from "./license-token";
import { TrustedLicenseSigningKeys } from "./trusted-signing-keys";

const MAX_TOKEN_CHARS = 16_384;
const MAX_HEADER_CHARS = 2_048;
const MAX_PAYLOAD_CHARS = 8_192;
const MAX_SIGNATURE_CHARS = 4_096;

export type TokenVerificationFailure = "TOKEN_INVALID" | "TOKEN_EXPIRED" | "UNKNOWN_KEY";
export type TokenVerificationResult =
  | { readonly ok: true; readonly claims: LicenseTokenClaims }
  | { readonly ok: false; readonly code: TokenVerificationFailure };

export class LicenseTokenVerifier {
  private constructor(private readonly keys: TrustedLicenseSigningKeys) {}

  static async create(configuredKeys: ReadonlyMap<string, string>): Promise<LicenseTokenVerifier> {
    const entries = Array.from(configuredKeys, ([kid, pem]) => {
      const match = /^-----BEGIN PUBLIC KEY-----\s+([A-Za-z0-9+/=\s]+?)\s+-----END PUBLIC KEY-----\s*$/u.exec(pem.trim());
      if (match?.[1] === undefined) throw new Error("Verification key PEM is malformed.");
      return { kid, algorithm: "RS256", spkiBase64: match[1].replace(/\s/gu, "") };
    });
    return LicenseTokenVerifier.fromRegistry(await TrustedLicenseSigningKeys.fromJson(JSON.stringify(entries)));
  }

  static fromRegistry(keys: TrustedLicenseSigningKeys): LicenseTokenVerifier { return new LicenseTokenVerifier(keys); }

  async verify(encoded: string, now: number): Promise<TokenVerificationResult> {
    if (encoded.length < 1 || encoded.length > MAX_TOKEN_CHARS) return invalid("TOKEN_INVALID");
    const segments = encoded.split(".");
    if (segments.length !== 3 || segments.some((segment) => segment.length === 0 || segment.includes("="))) {
      return invalid("TOKEN_INVALID");
    }
    const [headerSegment, payloadSegment, signatureSegment] = segments as [string, string, string];
    if (headerSegment.length > MAX_HEADER_CHARS || payloadSegment.length > MAX_PAYLOAD_CHARS || signatureSegment.length > MAX_SIGNATURE_CHARS) {
      return invalid("TOKEN_INVALID");
    }
    let header: Record<string, unknown>;
    try { header = parseObject(headerSegment); } catch { return invalid("TOKEN_INVALID"); }
    if (header.alg !== TOKEN_ALGORITHM || header.typ !== TOKEN_TYPE || typeof header.kid !== "string") return invalid("TOKEN_INVALID");
    const key = this.keys.resolve(header.kid);
    if (key === undefined) return invalid("UNKNOWN_KEY");
    let signature: Uint8Array;
    try { signature = base64UrlDecode(signatureSegment, MAX_SIGNATURE_CHARS); } catch { return invalid("TOKEN_INVALID"); }
    const valid = await crypto.subtle.verify(
      "RSASSA-PKCS1-v1_5", key, signature.slice().buffer as ArrayBuffer,
      new TextEncoder().encode(`${headerSegment}.${payloadSegment}`),
    );
    if (!valid) return invalid("TOKEN_INVALID");
    let payload: Record<string, unknown>;
    try { payload = parseObject(payloadSegment); } catch { return invalid("TOKEN_INVALID"); }
    const claims = parseClaims(payload);
    if (claims === null) return invalid("TOKEN_INVALID");
    if (now >= claims.expiresAtEpochSeconds) return invalid("TOKEN_EXPIRED");
    if (claims.issuedAtEpochSeconds > now) return invalid("TOKEN_INVALID");
    return { ok: true, claims };
  }
}

function parseObject(segment: string): Record<string, unknown> {
  const value: unknown = JSON.parse(new TextDecoder().decode(base64UrlDecode(segment)));
  if (typeof value !== "object" || value === null || Array.isArray(value)) throw new Error("Expected object.");
  return value as Record<string, unknown>;
}

function parseClaims(value: Record<string, unknown>): LicenseTokenClaims | null {
  const strings = ["licenseId", "deviceId", "installationId", "deviceHash", "packageName"] as const;
  if (strings.some((name) => typeof value[name] !== "string" || (value[name] as string).length === 0)) return null;
  const numbers = ["issuedAtEpochSeconds", "expiresAtEpochSeconds", "offlineValidUntilEpochSeconds"] as const;
  if (numbers.some((name) => !Number.isSafeInteger(value[name]))) return null;
  if (value.tokenVersion !== TOKEN_VERSION) return null;
  return value as unknown as LicenseTokenClaims;
}

function invalid(code: TokenVerificationFailure): TokenVerificationResult { return { ok: false, code }; }
