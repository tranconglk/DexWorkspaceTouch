import { canonicalizeLicenseKey } from "./license-key";

const HMAC_ALGORITHM = { name: "HMAC", hash: "SHA-256" } as const;

export async function hashLicenseKey(rawLicenseKey: string, pepper: string): Promise<string> {
  if (pepper.trim().length === 0) {
    throw new Error("License key pepper is unavailable.");
  }
  const canonical = canonicalizeLicenseKey(rawLicenseKey);
  const encoder = new TextEncoder();
  const key = await crypto.subtle.importKey(
    "raw",
    encoder.encode(pepper),
    HMAC_ALGORITHM,
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign("HMAC", key, encoder.encode(canonical));
  return [...new Uint8Array(signature)]
    .map((value) => value.toString(16).padStart(2, "0"))
    .join("");
}
