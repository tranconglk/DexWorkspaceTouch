import type { Env } from "../env";

const MAX_AUTHORIZATION_LENGTH = 1024;
const BEARER_PATTERN = /^Bearer ([^\s]+)$/i;

export async function isAdminAuthorized(request: Request, env: Env): Promise<boolean> {
  const expected = env.LICENSE_ADMIN_TOKEN;
  if (typeof expected !== "string" || expected.length === 0) return false;
  const header = request.headers.get("Authorization");
  if (header === null || header.length > MAX_AUTHORIZATION_LENGTH) return false;
  const match = BEARER_PATTERN.exec(header);
  if (match === null) return false;
  const provided = match[1]!;
  const [expectedDigest, providedDigest] = await Promise.all([digest(expected), digest(provided)]);
  let difference = 0;
  for (let index = 0; index < expectedDigest.length; index += 1) {
    difference |= expectedDigest[index]! ^ providedDigest[index]!;
  }
  return difference === 0;
}

async function digest(value: string): Promise<Uint8Array> {
  return new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value)));
}
