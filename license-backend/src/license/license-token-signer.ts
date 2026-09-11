import { base64UrlEncode, base64UrlEncodeUtf8 } from "./base64url";
import { TOKEN_ALGORITHM, TOKEN_TYPE, type LicenseTokenClaims } from "./license-token";

const SIGNING_ALGORITHM = { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" } as const;

export class LicenseTokenSigner {
  private constructor(
    private readonly signingKey: CryptoKey,
    private readonly keyId: string,
  ) {}

  static async create(privateKeyPem: string, keyId: string): Promise<LicenseTokenSigner> {
    if (keyId.trim().length === 0) throw new Error("Signing key ID is unavailable.");
    const keyBytes = decodePkcs8Pem(privateKeyPem);
    const key = await crypto.subtle.importKey(
      "pkcs8",
      keyBytes,
      SIGNING_ALGORITHM,
      false,
      ["sign"],
    );
    const algorithm = key.algorithm as KeyAlgorithm & { readonly modulusLength?: number };
    if (
      key.type !== "private" ||
      key.algorithm.name !== "RSASSA-PKCS1-v1_5" ||
      algorithm.modulusLength === undefined || algorithm.modulusLength < 2048
    ) {
      throw new Error("Signing key does not meet the required algorithm policy.");
    }
    return new LicenseTokenSigner(key, keyId);
  }

  async sign(claims: LicenseTokenClaims): Promise<string> {
    const header = { alg: TOKEN_ALGORITHM, typ: TOKEN_TYPE, kid: this.keyId };
    const encodedHeader = base64UrlEncodeUtf8(JSON.stringify(header));
    const encodedPayload = base64UrlEncodeUtf8(JSON.stringify(claims));
    const signingInput = `${encodedHeader}.${encodedPayload}`;
    const signature = await crypto.subtle.sign(
      "RSASSA-PKCS1-v1_5",
      this.signingKey,
      new TextEncoder().encode(signingInput),
    );
    return `${signingInput}.${base64UrlEncode(new Uint8Array(signature))}`;
  }
}

function decodePkcs8Pem(pem: string): ArrayBuffer {
  const match = /^-----BEGIN PRIVATE KEY-----\s+([A-Za-z0-9+/=\s]+?)\s+-----END PRIVATE KEY-----\s*$/u.exec(pem.trim());
  if (match?.[1] === undefined) throw new Error("Signing key PEM is malformed.");
  const base64 = match[1].replace(/\s/gu, "");
  if (base64.length === 0 || base64.length % 4 !== 0) throw new Error("Signing key PEM is malformed.");
  try {
    return Uint8Array.from(atob(base64), (character) => character.charCodeAt(0)).buffer as ArrayBuffer;
  } catch (error: unknown) {
    if (error instanceof DOMException) throw new Error("Signing key PEM is malformed.");
    throw error;
  }
}
