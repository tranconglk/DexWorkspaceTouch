import type { ProductionRelease } from "./types";

const SHA256 = /^[a-f0-9]{64}$/iu;
const APPLICATION_ID = "com.trancong.dexworkspacetouch";
const PRODUCTION_SIGNER = "19ac0ea99125361b3c2083aaa44c8745ebad9c55fa967642c2b9e5a1086a45e7";

export async function loadProductionManifest(url: string, fetchImpl: typeof fetch = fetch): Promise<ProductionRelease> {
  let manifestUrl: string;
  try { manifestUrl = secureUrl(url, "manifest"); }
  catch { throw new ManifestError("MANIFEST_URL_INVALID", "Production manifest URL is invalid"); }
  let response: Response;
  try { response = await fetchImpl(manifestUrl); }
  catch { throw new ManifestError("MANIFEST_FETCH_FAILED", "Production manifest is unavailable"); }
  if (!response.ok) throw new ManifestError("MANIFEST_HTTP_ERROR", "Production manifest is unavailable");
  const value = await response.json().catch(() => null) as Record<string, unknown> | null;
  if (!value || value.applicationId !== APPLICATION_ID || typeof value.versionName !== "string" || !value.versionName ||
      !Number.isSafeInteger(value.versionCode) || (value.versionCode as number) < 1 ||
      typeof value.apkUrl !== "string" || !Number.isSafeInteger(value.apkSize) || (value.apkSize as number) < 1 ||
      !SHA256.test(String(value.apkSha256 ?? "")) || String(value.signingCertificateSha256 ?? "").toLowerCase() !== PRODUCTION_SIGNER) {
    throw new ManifestError("MANIFEST_INVALID", "Production manifest is malformed");
  }
  let apkUrl: string;
  try { apkUrl = secureUrl(value.apkUrl, "APK"); }
  catch { throw new ManifestError("MANIFEST_INVALID", "Production manifest is malformed"); }
  return Object.freeze({
    versionName: value.versionName,
    versionCode: value.versionCode as number,
    apkUrl,
    apkSha256: String(value.apkSha256).toLowerCase(),
    apkSize: value.apkSize as number,
    signingCertificateSha256: String(value.signingCertificateSha256).toLowerCase(),
  });
}

export class ManifestError extends Error {
  constructor(readonly code: string, message: string) { super(message); }
}

function secureUrl(value: string, name: string): string {
  const url = new URL(value);
  if (url.protocol !== "https:" || !url.hostname) throw new Error(`${name} URL must use HTTPS`);
  return url.toString();
}
