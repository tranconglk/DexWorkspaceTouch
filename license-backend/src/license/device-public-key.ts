const MAX_PUBLIC_KEY_BASE64_LENGTH = 1024;
const BASE64 = /^[A-Za-z0-9+/]+={0,2}$/;

export async function isValidP256Spki(publicKeyBase64: string): Promise<boolean> {
  if (
    publicKeyBase64.length === 0 ||
    publicKeyBase64.length > MAX_PUBLIC_KEY_BASE64_LENGTH ||
    publicKeyBase64.length % 4 !== 0 ||
    !BASE64.test(publicKeyBase64)
  ) {
    return false;
  }
  try {
    const binary = atob(publicKeyBase64);
    const encoded = Uint8Array.from(binary, (character) => character.charCodeAt(0));
    const key = await importP256SpkiBytes(encoded);
    return key.type === "public" &&
      key.algorithm.name === "ECDSA" &&
      "namedCurve" in key.algorithm &&
      key.algorithm.namedCurve === "P-256";
  } catch (error: unknown) {
    if (error instanceof DOMException || error instanceof TypeError) {
      return false;
    }
    throw error;
  }
}

export async function importP256Spki(publicKeyBase64: string): Promise<CryptoKey> {
  if (!await isValidP256Spki(publicKeyBase64)) throw new Error("Invalid P-256 public key.");
  const binary = atob(publicKeyBase64);
  return importP256SpkiBytes(Uint8Array.from(binary, (character) => character.charCodeAt(0)));
}

function importP256SpkiBytes(encoded: Uint8Array): Promise<CryptoKey> {
  return crypto.subtle.importKey(
    "spki", encoded.slice().buffer as ArrayBuffer, { name: "ECDSA", namedCurve: "P-256" }, false, ["verify"],
  );
}
