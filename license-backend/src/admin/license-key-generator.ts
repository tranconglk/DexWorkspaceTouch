import { canonicalizeLicenseKey } from "../license/license-key";

const ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

export function generateLicenseKey(randomBytes: () => Uint8Array = secureRandom): string {
  const bytes = randomBytes();
  if (bytes.length < 12) throw new Error("License key generator requires at least 12 random bytes.");
  let randomPart = "";
  for (let index = 0; index < 12; index += 1) randomPart += ALPHABET[bytes[index]! & 31];
  return canonicalizeLicenseKey(`DWT-${randomPart.slice(0, 4)}-${randomPart.slice(4, 8)}-${randomPart.slice(8)}`);
}

function secureRandom(): Uint8Array {
  return crypto.getRandomValues(new Uint8Array(12));
}
