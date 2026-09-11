const KID_PATTERN = /^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/u;
const ALGORITHM = { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" } as const;

type RegistryEntry = { readonly kid: string; readonly algorithm: "RS256"; readonly spkiBase64: string };

export class TrustedLicenseSigningKeys {
  private constructor(
    private readonly keys: ReadonlyMap<string, CryptoKey>,
    readonly fingerprints: ReadonlyMap<string, string>,
  ) {}

  static async fromJson(value: string): Promise<TrustedLicenseSigningKeys> {
    let parsed: unknown;
    try { parsed = JSON.parse(value); } catch { throw new Error("Trusted signing-key registry is malformed."); }
    if (!Array.isArray(parsed) || parsed.length === 0) throw new Error("Trusted signing-key registry is empty.");
    const entries = parsed.map(parseEntry);
    const keys = new Map<string, CryptoKey>();
    const fingerprints = new Map<string, string>();
    for (const entry of entries) {
      if (keys.has(entry.kid)) throw new Error("Duplicate trusted signing-key ID.");
      const bytes = decodeBase64(entry.spkiBase64);
      const key = await crypto.subtle.importKey("spki", bytes, ALGORITHM, true, ["verify"]);
      const algorithm = key.algorithm as KeyAlgorithm & { readonly modulusLength?: number };
      if (key.type !== "public" || algorithm.name !== ALGORITHM.name ||
          algorithm.modulusLength === undefined || algorithm.modulusLength < 2048) {
        throw new Error("Trusted signing key does not meet the RSA policy.");
      }
      keys.set(entry.kid, key);
      fingerprints.set(entry.kid, hex(await crypto.subtle.digest("SHA-256", bytes)));
    }
    return new TrustedLicenseSigningKeys(keys, fingerprints);
  }

  resolve(kid: string): CryptoKey | undefined { return this.keys.get(kid); }
  get size(): number { return this.keys.size; }
}

export function singleTrustedKeyRegistry(kid: string, publicKeyPem: string): string {
  const match = /^-----BEGIN PUBLIC KEY-----\s+([A-Za-z0-9+/=\s]+?)\s+-----END PUBLIC KEY-----\s*$/u.exec(publicKeyPem.trim());
  if (match?.[1] === undefined) throw new Error("Verification key PEM is malformed.");
  return JSON.stringify([{ kid, algorithm: "RS256", spkiBase64: match[1].replace(/\s/gu, "") }]);
}

function parseEntry(value: unknown): RegistryEntry {
  if (typeof value !== "object" || value === null || Array.isArray(value)) throw new Error("Trusted signing-key entry is malformed.");
  const object = value as Record<string, unknown>;
  if (Object.keys(object).sort().join(",") !== "algorithm,kid,spkiBase64" ||
      typeof object.kid !== "string" || !KID_PATTERN.test(object.kid) ||
      object.algorithm !== "RS256" || typeof object.spkiBase64 !== "string") {
    throw new Error("Trusted signing-key entry is invalid.");
  }
  return object as RegistryEntry;
}

function decodeBase64(value: string): ArrayBuffer {
  if (value.length === 0 || value.length % 4 !== 0 || !/^[A-Za-z0-9+/]+={0,2}$/u.test(value)) {
    throw new Error("Trusted signing-key Base64 is malformed.");
  }
  try { return Uint8Array.from(atob(value), (character) => character.charCodeAt(0)).buffer as ArrayBuffer; }
  catch { throw new Error("Trusted signing-key Base64 is malformed."); }
}

function hex(value: ArrayBuffer): string {
  return Array.from(new Uint8Array(value), (byte) => byte.toString(16).padStart(2, "0")).join("");
}
