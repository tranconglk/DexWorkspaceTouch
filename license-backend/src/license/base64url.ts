export function base64UrlEncode(value: Uint8Array): string {
  let binary = "";
  for (const byte of value) binary += String.fromCharCode(byte);
  return btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/u, "");
}

export function base64UrlEncodeUtf8(value: string): string {
  return base64UrlEncode(new TextEncoder().encode(value));
}

const BASE64URL = /^[A-Za-z0-9_-]+$/u;

export function base64UrlDecode(value: string, maxEncodedLength = 4096): Uint8Array {
  if (value.length === 0 || value.length > maxEncodedLength || !BASE64URL.test(value)) {
    throw new Error("Invalid Base64URL value.");
  }
  const remainder = value.length % 4;
  if (remainder === 1) throw new Error("Invalid Base64URL length.");
  const padded = value.replaceAll("-", "+").replaceAll("_", "/") + "=".repeat((4 - remainder) % 4);
  const binary = atob(padded);
  return Uint8Array.from(binary, (character) => character.charCodeAt(0));
}
