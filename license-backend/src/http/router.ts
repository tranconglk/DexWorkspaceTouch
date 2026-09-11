import type { Env } from "../env";
import { isAdminPath, routeAdmin } from "../admin/admin-router";
import { validateActivationRequest } from "../license/activation-request";
import { LicenseProofService, validateActivationProofRequest } from "../license/activation-proof";
import { systemClock } from "../license/clock";
import { ApplicationIdentityPolicy } from "../license/application-identity-policy";
import { LicenseRepository } from "../license/repository";
import { createLicenseTokenClaims } from "../license/license-token";
import { LicenseTokenSigner } from "../license/license-token-signer";
import { LicenseTokenVerifier } from "../license/license-token-verifier";
import { LicenseRefreshService, validateRefreshApplication, validateRefreshProof, type RefreshFailureCode } from "../license/license-refresh";
import type { ActivationFailureCode } from "../license/types";
import { parseJsonBody } from "./body";
import { jsonError, jsonSuccess } from "./response";
import { actorKey, allowed, rateLimited, sourceIp } from "../security/rate-limit";
import { hashLicenseKey } from "../license/license-key-hash";
import { singleTrustedKeyRegistry, TrustedLicenseSigningKeys } from "../license/trusted-signing-keys";

const HEALTH_PATH = "/v1/health";
const ACTIVATE_PATH = "/v1/license/activate";
const CHALLENGE_PATH = "/v1/license/challenge";
const REFRESH_CHALLENGE_PATH = "/v1/license/refresh/challenge";
const REFRESH_PATH = "/v1/license/refresh";
const MAX_AUTHORIZATION_CHARS = 16_400;

export async function route(request: Request, env: Env, requestId: string): Promise<Response> {
  const url = new URL(request.url);
  if (isAdminPath(url.pathname)) return routeAdmin(request, env, requestId);
  if (url.pathname === HEALTH_PATH) {
    if (request.method !== "GET") {
      return jsonError(405, "METHOD_NOT_ALLOWED", "Method not allowed.", { Allow: "GET" });
    }
    await signingService(env);
    return jsonSuccess({
      service: "dexworkspacetouch-license",
      version: 1,
    });
  }
  if (url.pathname === REFRESH_CHALLENGE_PATH) {
    if (request.method !== "POST") return jsonError(405, "METHOD_NOT_ALLOWED", "Method not allowed.", { Allow: "POST" });
    const token = bearerToken(request);
    if (token === null) return jsonError(401, "TOKEN_INVALID", "License credential is invalid.", { "Cache-Control": "no-store" });
    const verifier = await tokenVerifier(env);
    const verified = await verifier.verify(token, systemClock.nowEpochSeconds());
    if (!verified.ok) return refreshError(verified.code);
    if (!await allowed(env.REFRESH_LIMITER, actorKey.refreshDevice(verified.claims.licenseId, verified.claims.deviceId))) return rateLimited();
    const body = await parseJsonBody(request);
    if (!body.ok) return jsonError(body.code === "PAYLOAD_TOO_LARGE" ? 413 : body.code === "UNSUPPORTED_MEDIA_TYPE" ? 415 : 400,
      body.code, "Refresh request is invalid.", { "Cache-Control": "no-store" });
    const application = validateRefreshApplication(body.value);
    if (application === null) return jsonError(400, "INVALID_REQUEST", "Refresh request is invalid.", { "Cache-Control": "no-store" });
    const result = await new LicenseRefreshService(new LicenseRepository(env.DB), systemClock, applicationPolicy(env))
      .issueChallenge(verified.claims, application);
    return result.ok ? jsonSuccess(result.value, 200, { "Cache-Control": "no-store" }) : refreshError(result.code);
  }
  if (url.pathname === REFRESH_PATH) {
    if (request.method !== "POST") return jsonError(405, "METHOD_NOT_ALLOWED", "Method not allowed.", { Allow: "POST" });
    const body = await parseJsonBody(request);
    if (!body.ok) return jsonError(body.code === "PAYLOAD_TOO_LARGE" ? 413 : body.code === "UNSUPPORTED_MEDIA_TYPE" ? 415 : 400,
      body.code, "Refresh proof is invalid.", { "Cache-Control": "no-store" });
    const proof = validateRefreshProof(body.value);
    if (proof === null) return jsonError(400, "INVALID_REQUEST", "Refresh proof is invalid.", { "Cache-Control": "no-store" });
    if (!await allowed(env.REFRESH_LIMITER, actorKey.refreshProof(proof.challengeId))) return rateLimited();
    const signer = await signingService(env);
    const result = await new LicenseRefreshService(new LicenseRepository(env.DB), systemClock, applicationPolicy(env))
      .completeProof(proof.challengeId, proof.signature, requestId);
    if (!result.ok) return refreshError(result.code);
    const claims = createLicenseTokenClaims({ licenseId: result.claims.licenseId, deviceId: result.claims.deviceId,
      installationId: result.claims.installationId, deviceHash: result.claims.deviceHash,
      packageName: result.claims.packageName, issuedAtEpochSeconds: result.serverTime,
      licenseExpiresAtEpochSeconds: result.licenseExpiresAtEpochSeconds });
    return jsonSuccess({ licenseToken: await signer.sign(claims), serverTime: result.serverTime }, 200, { "Cache-Control": "no-store" });
  }
  if (url.pathname === CHALLENGE_PATH) {
    if (request.method !== "POST") {
      return jsonError(405, "METHOD_NOT_ALLOWED", "Method not allowed.", { Allow: "POST" });
    }
    if (!await allowed(env.PUBLIC_IP_LIMITER, actorKey.publicIp(sourceIp(request)))) return rateLimited();
    const body = await parseJsonBody(request);
    if (!body.ok) {
      const status = body.code === "UNSUPPORTED_MEDIA_TYPE" ? 415 : body.code === "PAYLOAD_TOO_LARGE" ? 413 : 400;
      return jsonError(status, body.code, "Request body must be valid JSON.");
    }
    const validation = await validateActivationRequest(body.value);
    if (!validation.ok) {
      return jsonError(400, validation.code, "Activation request is invalid.");
    }
    if (env.LICENSE_KEY_PEPPER === undefined || env.LICENSE_KEY_PEPPER.trim().length === 0) {
      throw new Error("Required license key pepper binding is unavailable.");
    }
    const limiterHash = await hashLicenseKey(validation.value.licenseKey, env.LICENSE_KEY_PEPPER);
    if (!await allowed(env.ACTIVATION_LIMITER, actorKey.activationLicense(limiterHash))) return rateLimited();
    const result = await new LicenseProofService(new LicenseRepository(env.DB), systemClock,
      applicationPolicy(env)).issueChallenge(validation.value, { pepper: env.LICENSE_KEY_PEPPER });
    if (result.ok) return jsonSuccess(result.value, 200, { "Cache-Control": "no-store" });
    return activationError(result.code);
  }
  if (url.pathname === ACTIVATE_PATH) {
    if (request.method !== "POST") {
      return jsonError(405, "METHOD_NOT_ALLOWED", "Method not allowed.", { Allow: "POST" });
    }
    if (!await allowed(env.PUBLIC_IP_LIMITER, actorKey.publicIp(sourceIp(request)))) return rateLimited();
    const body = await parseJsonBody(request);
    if (!body.ok) {
      const status = body.code === "UNSUPPORTED_MEDIA_TYPE" ? 415 : body.code === "PAYLOAD_TOO_LARGE" ? 413 : 400;
      return jsonError(status, body.code, "Request body must be valid JSON.");
    }
    const proof = validateActivationProofRequest(body.value);
    if (proof === null) return jsonError(400, "INVALID_REQUEST", "Activation proof request is invalid.");
    if (!await allowed(env.ACTIVATION_LIMITER, actorKey.activationProof(proof.challengeId))) return rateLimited();
    if (env.LICENSE_SIGNING_PRIVATE_KEY === undefined || env.LICENSE_SIGNING_PRIVATE_KEY.trim().length === 0) {
      throw new Error("Required signing key binding is unavailable.");
    }
    // Preflight before proof consumption or activation mutation.
    const signer = await LicenseTokenSigner.create(env.LICENSE_SIGNING_PRIVATE_KEY, env.LICENSE_SIGNING_KEY_ID);
    const result = await new LicenseProofService(new LicenseRepository(env.DB), systemClock, applicationPolicy(env))
      .completeProof(proof, requestId);
    if (result.ok) {
      const claims = createLicenseTokenClaims({
        licenseId: result.value.licenseId,
        deviceId: result.value.deviceId,
        installationId: result.binding.device.installationId,
        deviceHash: result.binding.device.deviceHash,
        packageName: result.binding.application.packageName,
        issuedAtEpochSeconds: result.value.serverTime,
        licenseExpiresAtEpochSeconds: result.licenseExpiresAtEpochSeconds,
      });
      const licenseToken = await signer.sign(claims);
      return jsonSuccess({ ...result.value, licenseToken }, 200, { "Cache-Control": "no-store" });
    }
    return activationError(result.code);
  }
  return jsonError(404, "NOT_FOUND", "Route not found.");
}

async function tokenVerifier(env: Env): Promise<LicenseTokenVerifier> {
  const configured = env.LICENSE_TRUSTED_PUBLIC_KEYS_JSON?.trim() ||
    (env.LICENSE_SIGNING_PUBLIC_KEY_V1?.trim() ? singleTrustedKeyRegistry(env.LICENSE_SIGNING_KEY_ID, env.LICENSE_SIGNING_PUBLIC_KEY_V1) : "");
  if (configured.length === 0) {
    throw new Error("Required verification key binding is unavailable.");
  }
  return LicenseTokenVerifier.fromRegistry(await TrustedLicenseSigningKeys.fromJson(configured));
}

async function signingService(env: Env): Promise<LicenseTokenSigner> {
  if (env.LICENSE_SIGNING_PRIVATE_KEY === undefined || env.LICENSE_SIGNING_PRIVATE_KEY.trim().length === 0) {
    throw new Error("Required signing key binding is unavailable.");
  }
  const signer = await LicenseTokenSigner.create(env.LICENSE_SIGNING_PRIVATE_KEY, env.LICENSE_SIGNING_KEY_ID);
  const now = systemClock.nowEpochSeconds();
  const probe = await signer.sign(createLicenseTokenClaims({ licenseId: "preflight", deviceId: "preflight",
    installationId: "preflight", deviceHash: "preflight", packageName: env.APP_PACKAGE_NAME,
    issuedAtEpochSeconds: now, licenseExpiresAtEpochSeconds: now + 60 }));
  const verified = await (await tokenVerifier(env)).verify(probe, now);
  if (!verified.ok) throw new Error("Active signing key does not match its trusted public key.");
  return signer;
}

function bearerToken(request: Request): string | null {
  const value = request.headers.get("Authorization");
  if (value === null || value.length > MAX_AUTHORIZATION_CHARS || !value.startsWith("Bearer ")) return null;
  const token = value.slice(7);
  return token.length > 0 && !/\s/u.test(token) ? token : null;
}

function refreshError(code: RefreshFailureCode): Response {
  const status = code === "TOKEN_INVALID" || code === "TOKEN_EXPIRED" || code === "UNKNOWN_KEY" ? 401
    : code === "CHALLENGE_USED" || code === "DEVICE_MISMATCH" ? 409 : 403;
  return jsonError(status, code, "License refresh was rejected.", { "Cache-Control": "no-store" });
}

function applicationPolicy(env: Env): ApplicationIdentityPolicy {
  return ApplicationIdentityPolicy.create(env.APP_PACKAGE_NAME, env.ANDROID_ALLOWED_SIGNING_CERT_SHA256);
}

function activationError(code: ActivationFailureCode | "CHALLENGE_INVALID" | "CHALLENGE_EXPIRED" | "CHALLENGE_USED" | "PROOF_INVALID"): Response {
  const status = code === "DEVICE_MISMATCH" || code === "DEVICE_LIMIT_REACHED" || code === "CHALLENGE_USED" ? 409 : 403;
  return jsonError(status, code, "Activation was rejected.");
}
