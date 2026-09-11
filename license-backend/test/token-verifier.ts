import type { LicenseTokenClaims } from "../src/license/license-token";

export async function verifyTestToken(token: string, publicKeyPem: string, expectedKeyId: string): Promise<LicenseTokenClaims> {
  const segments = token.split(".");
  if (segments.length !== 3 || segments.some((segment) => segment.length === 0)) throw new Error("Token must have three segments.");
  const [headerSegment, payloadSegment, signatureSegment] = segments as [string, string, string];
  const header = JSON.parse(decodeUtf8(headerSegment)) as Record<string, unknown>;
  if (header.alg !== "RS256" || header.typ !== "DWT-LICENSE" || header.kid !== expectedKeyId) throw new Error("Token header is not trusted.");
  const publicKey = await crypto.subtle.importKey("spki", decodePem(publicKeyPem), { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]);
  const verified = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", publicKey, base64UrlDecode(signatureSegment), new TextEncoder().encode(`${headerSegment}.${payloadSegment}`));
  if (!verified) throw new Error("Token signature is invalid.");
  return JSON.parse(decodeUtf8(payloadSegment)) as LicenseTokenClaims;
}

export function encodeJsonSegment(value: unknown): string {
  return encodeBytes(new TextEncoder().encode(JSON.stringify(value)));
}

function decodeUtf8(value: string): string { return new TextDecoder().decode(base64UrlDecode(value)); }
function base64UrlDecode(value: string): ArrayBuffer {
  if (!/^[A-Za-z0-9_-]+$/u.test(value)) throw new Error("Invalid Base64URL.");
  const base64 = value.replaceAll("-", "+").replaceAll("_", "/").padEnd(Math.ceil(value.length / 4) * 4, "=");
  return Uint8Array.from(atob(base64), (character) => character.charCodeAt(0)).buffer as ArrayBuffer;
}
function encodeBytes(value: Uint8Array): string {
  return btoa(String.fromCharCode(...value)).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/u, "");
}
function decodePem(pem: string): ArrayBuffer {
  const body = pem.replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "").replace(/\s/gu, "");
  return Uint8Array.from(atob(body), (character) => character.charCodeAt(0)).buffer as ArrayBuffer;
}
