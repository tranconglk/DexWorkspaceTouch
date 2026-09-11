import type { Env, OperatorIdentity } from "./types";

const encoder = new TextEncoder();
let cachedKeys: { expiresAt: number; keys: JsonWebKey[] } | null = null;

export async function authenticateAccess(request: Request, env: Env): Promise<OperatorIdentity | null> {
  const assertion = request.headers.get("Cf-Access-Jwt-Assertion");
  if (!assertion) return null;
  const parts = assertion.split(".");
  if (parts.length !== 3) return null;
  try {
    const header = JSON.parse(decodeText(parts[0]!)) as { alg?: string; kid?: string };
    const claims = JSON.parse(decodeText(parts[1]!)) as Record<string, unknown>;
    if (header.alg !== "RS256" || typeof header.kid !== "string") return null;
    const keys = await accessKeys(env.CF_ACCESS_TEAM_DOMAIN);
    const jwk = keys.find((candidate) => candidate.kid === header.kid && candidate.kty === "RSA");
    if (!jwk) return null;
    const key = await crypto.subtle.importKey("jwk", jwk, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]);
    const signature = decode(parts[2]!);
    const valid = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", key, signature.slice().buffer as ArrayBuffer,
      encoder.encode(`${parts[0]}.${parts[1]}`));
    if (!valid || !validClaims(claims, env)) return null;
    const email = String(claims.email).toLowerCase();
    const trustedHeader = request.headers.get("Cf-Access-Authenticated-User-Email")?.toLowerCase();
    if (trustedHeader && trustedHeader !== email) return null;
    const allowed = env.OPERATOR_EMAIL_ALLOWLIST.split(",").map((value) => value.trim().toLowerCase()).filter(Boolean);
    return allowed.includes(email) ? { email } : null;
  } catch {
    return null;
  }
}

function validClaims(claims: Record<string, unknown>, env: Env): boolean {
  const now = Math.floor(Date.now() / 1000);
  const expectedIssuer = `https://${env.CF_ACCESS_TEAM_DOMAIN.replace(/^https?:\/\//u, "").replace(/\/$/u, "")}`;
  const audience = Array.isArray(claims.aud) ? claims.aud : [claims.aud];
  return claims.iss === expectedIssuer && audience.includes(env.CF_ACCESS_AUD) &&
    typeof claims.exp === "number" && claims.exp > now &&
    (typeof claims.nbf !== "number" || claims.nbf <= now) &&
    typeof claims.email === "string" && claims.email.length > 0;
}

async function accessKeys(teamDomain: string): Promise<AccessJwk[]> {
  if (cachedKeys && cachedKeys.expiresAt > Date.now()) return cachedKeys.keys;
  const domain = teamDomain.replace(/^https?:\/\//u, "").replace(/\/$/u, "");
  const response = await fetch(`https://${domain}/cdn-cgi/access/certs`);
  if (!response.ok) throw new Error("Access key discovery failed");
  const payload = await response.json() as { keys?: AccessJwk[] };
  if (!Array.isArray(payload.keys)) throw new Error("Access key response is invalid");
  cachedKeys = { keys: payload.keys, expiresAt: Date.now() + 5 * 60_000 };
  return payload.keys;
}

interface AccessJwk extends JsonWebKey { kid?: string }

export function mutationAllowed(request: Request): boolean {
  if (request.method !== "POST" || request.headers.get("X-DWT-CSRF") !== "1") return false;
  const origin = request.headers.get("Origin");
  return origin !== null && origin === new URL(request.url).origin;
}

function decodeText(value: string): string { return new TextDecoder().decode(decode(value)); }
function decode(value: string): Uint8Array {
  const normalized = value.replace(/-/gu, "+").replace(/_/gu, "/");
  const binary = atob(normalized.padEnd(Math.ceil(normalized.length / 4) * 4, "="));
  return Uint8Array.from(binary, (character) => character.charCodeAt(0));
}
