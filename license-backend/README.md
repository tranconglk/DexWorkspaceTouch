# DexWorkspaceTouch License Backend

Cloudflare Worker and D1 licensing service for DexWorkspaceTouch. It implements challenge/proof activation, signed license tokens, and authenticated operator APIs. It is not a customer portal and contains no payment or PII model.

## Architecture

- `src/index.ts`: ES module Worker entry point and request IDs.
- `src/http`: small Web Platform API router, JSON responses, and JSON body parsing.
- `src/license`: pure TypeScript license-key contract matching Android LIC-001.
- `migrations`: versioned D1/SQLite migrations.
- `test`: Workers-runtime Vitest tests using an isolated local D1 binding.

The Worker uses only `Request`, `Response`, `URL`, Web Crypto, and the D1 binding. No web framework is installed.

## Prerequisites

- Node.js 22 or newer. Development verification used Node.js 24 LTS.
- npm.

Install and verify:

```powershell
npm ci
npm run typecheck
npm test
npm run deploy:dry-run
```

Run locally:

```powershell
npm run db:migrate:local
npm run dev
```

Public endpoints are `GET /v1/health`, `POST /v1/license/challenge`, and `POST /v1/license/activate`. Admin endpoints are documented below. Other paths return JSON `NOT_FOUND`; unsupported methods return `METHOD_NOT_ALLOWED` with an `Allow` header.

## Local admin setup

Generate a strong operator token and copy it into an ignored `.dev.vars` file:

```powershell
npm run admin:token
Copy-Item .dev.vars.example .dev.vars
```

Replace only the placeholders locally, then start the Worker. In a separate shell, provide the API URL and token through environment variables (never a command-line `--token` argument):

```powershell
npm run db:migrate:local
npm run dev

$env:DWT_LICENSE_ADMIN_API_URL = "http://127.0.0.1:8787"
$env:DWT_LICENSE_ADMIN_TOKEN = "<local-admin-token>"
npm run admin -- create --max-devices 1
npm run admin -- list
npm run admin -- show <licenseId>
npm run admin -- revoke <licenseId>
npm run admin -- devices <licenseId>
npm run admin -- reset-device <licenseId> <deviceId>
```

The HTTP operations are:

- `POST /v1/admin/licenses`
- `GET /v1/admin/licenses?limit=50&offset=0`
- `GET /v1/admin/licenses/:licenseId`
- `POST /v1/admin/licenses/:licenseId/revoke`
- `GET /v1/admin/licenses/:licenseId/devices`
- `POST /v1/admin/licenses/:licenseId/devices/:deviceId/reset`

Every admin route requires `Authorization: Bearer <LICENSE_ADMIN_TOKEN>` and fails closed if the Worker secret is absent. Production configuration must use `wrangler secret put LICENSE_ADMIN_TOKEN`; do not put its value in source, Wrangler variables, logs, or the Android APK. The CLI requires HTTPS except for loopback local development and does not access D1 directly.

Activation also fails closed unless the exact configured Android package and one certificate from `ANDROID_ALLOWED_SIGNING_CERT_SHA256` match the identity bound into the challenge. The allowlist is a strict comma-separated set of SHA-256 fingerprints and supports direct-distribution plus future Play app-signing certificates. The currently verified direct release certificate is documented in [the LIC-010 production runbook](../docs/LIC-010_PRODUCTION_RUNBOOK.md); the debug certificate is not allowed.

License creation uses Web Crypto randomness, canonicalizes the key, and stores only its peppered HMAC-SHA-256 lookup hash. The plaintext License Key is returned only in the create response and cannot be recovered later. Store it securely when it is printed.

Device reset revokes and retains the old binding, frees its active device slot, and preserves an audit event. It does not accept or assign a new public key. A new installation must enter the same License Key and complete the LIC-008 challenge and Android Keystore proof before it can receive a new binding and token.

License revoke and device reset stop future challenge/activation flows, but cannot instantly invalidate a signed token already operating offline. Such a token may remain usable until its signed effective local end under the LIC-005/LIC-007 policy. No unrevoke, hard-delete, key recovery, or force-bind endpoint exists.

## Activation API

`POST /v1/license/activate` requires `Content-Type: application/json` and the Android LIC-001 shape:

```json
{
  "licenseKey": "APP-7K3M-9QTX-2PL8",
  "device": {
    "installationId": "00000000-0000-4000-8000-000000000000",
    "deviceHash": "64-lowercase-hex-characters",
    "publicKey": "base64-x509-spki"
  },
  "application": {
    "packageName": "com.trancong.dexworkspacetouch",
    "versionName": "1.0",
    "versionCode": 1,
    "signingCertificateSha256": "optional-64-hex-characters"
  }
}
```

A successful response contains stable `licenseId`, `deviceId`, `status`, `activatedAt`, and `serverTime` fields under `data`, plus the additive `licenseToken` field. Activation responses use `Cache-Control: no-store`.

Malformed requests use `INVALID_REQUEST` or `INVALID_DEVICE_PUBLIC_KEY`. Business rejections use the Android-compatible `LICENSE_INVALID`, `LICENSE_REVOKED`, `LICENSE_EXPIRED`, and `DEVICE_MISMATCH` codes, plus `DEVICE_LIMIT_REACHED` to distinguish an exhausted slot count. Database/internal failures return `SERVER_ERROR` without SQL details.

License lookup canonicalizes the supplied key and computes lowercase-hex HMAC-SHA-256 with the `LICENSE_KEY_PEPPER` Worker secret. Activation fails closed if that binding is absent or blank; it never falls back to plain SHA-256 or a built-in secret.

New-device binding uses a conditional `INSERT ... SELECT` that checks the active-device count in SQL. The insert, license status/timestamps, and audit event run in one transactional D1 `batch()`. Dependent statements use `EXISTS` on the newly generated device ID, so a rejected conditional insert cannot produce a false activation or event. The database uniqueness constraint protects same-installation races.

Reactivating the same installation with the same hash/public key is idempotent and preserves its original device ID and activation time. A changed critical identity or a revoked binding is not overwritten. Revoked bindings are not automatically reused; a later admin/reset milestone must define that flow.

The submitted signing-certificate digest is shape-validated but not allowlisted yet because the production Play/release certificate policy has not been selected.

## D1 schema

All timestamps are integer Unix epoch **seconds**, matching Android `LicenseTokenClaims`. The initial migration creates:

- `licenses`: keyed by opaque text ID; stores only a unique keyed lookup hash, never a plaintext license key.
- `devices`: installation identity, SHA-256 device signal, X.509 SPKI public key, algorithm identifier, and license binding.
- `license_events`: minimal audit records without license-key plaintext.
- `license_challenges`: hashed nonces and expiry/consumption state for a later anti-replay milestone.

Foreign keys, status/check constraints, uniqueness rules, and focused lookup/expiry indexes are defined in `migrations/0001_initial.sql`. Production license deletion is intentionally not exposed; future behavior should use revoke/restore.

## Remote D1 setup

No remote database is created or claimed by LIC-003. When authenticated with Cloudflare:

```powershell
npx wrangler login
npx wrangler d1 create license-db
```

Copy the returned real database ID into `wrangler.jsonc`, replacing `REPLACE_WITH_REMOTE_D1_DATABASE_ID`, then apply migrations:

```powershell
npm run db:migrate:remote
```

Deploy only after reviewing the account and binding:

```powershell
npm run deploy
```

## Secrets policy

No credentials or secrets belong in Git, `wrangler.jsonc`, or any committed env file. Local `.dev.vars` is ignored and may hold development-only values. Configure production secrets interactively:

```powershell
npx wrangler secret put LICENSE_KEY_PEPPER
npx wrangler secret put LICENSE_SIGNING_PRIVATE_KEY
npx wrangler secret put LICENSE_ADMIN_TOKEN
```

Tests generate an ephemeral RSA key and inject an explicitly test-only pepper. `.dev.vars.example` contains placeholders only. The signing-key ID is the non-secret Wrangler variable `LICENSE_SIGNING_KEY_ID=license-signing-v1`.

## Signed license token

LIC-005 issues a compact JWS-compatible token:

```text
base64url(header).base64url(payload).base64url(signature)
```

The server signs the original first two segments with RSA-2048, RSASSA-PKCS1-v1_5 and SHA-256 (`RS256`). Base64URL segments have no padding. The fixed header is `{"alg":"RS256","typ":"DWT-LICENSE","kid":"license-signing-v1"}`. The signing key ID supports future rotation independently of payload `tokenVersion=1`.

Claims map the Android LIC-001 names exactly: `licenseId`, `installationId`, `issuedAtEpochSeconds`, `expiresAtEpochSeconds`, `offlineValidUntilEpochSeconds`, `packageName`, and `tokenVersion`. The backend additionally binds `deviceId` and `deviceHash`. It never includes a license key/hash, pepper, public device key, customer PII, or private key.

The initial token lifetime and offline window are both seven days (`604800` seconds) from server issue time. Both are clamped to an earlier `licenses.expires_at`. The exact validity boundary is `now < expiresAt`; `now >= expiresAt` is expired. This equal initial expiry/offline boundary is deliberate for LIC-005; LIC-007 will use network state and these bounds to derive runtime states.

An already-issued offline token can remain cryptographically valid until its signed expiry even if an administrator revokes the license meanwhile. Seven days is therefore the current maximum practical offline revocation delay; immediate revocation cannot be promised while a device is fully offline.

The server token key is separate from the Android device identity key: server tokens use RSA/RS256, while device proof remains EC P-256/`SHA256withECDSA`. Android token verification and the trusted production SPKI public key will be added through a later build/release milestone; clients must never download a public key and trust it without a bundled trust anchor.

Signing configuration is imported and validated before any activation mutation. Missing, malformed, non-RSA, or RSA keys smaller than 2048 bits fail closed. No ephemeral/fallback key is generated inside the Worker. Unexpected signing failure after a successful bind returns `SERVER_ERROR`; retry remains safe because LIC-004 activation is idempotent.

### Local signing-key tool

Generate explicitly local/test key material:

```powershell
npm run keys:generate
npm run keys:verify
```

Outputs are PKCS#8 private PEM, X.509 SPKI public PEM, and lowercase SHA-256 fingerprint of the DER SPKI under ignored `.local-keys/`. These generated files are not production keys and must not be committed or copied into the APK.

For rotation, retain public key v1 while any v1 token remains valid, add a new `kid`/trusted public key mapping, then switch issuance. Pepper rotation is unrelated and can break existing license lookup unless a migration or dual-lookup plan is implemented; do not rotate it casually.

## Android compatibility contract

License keys are trimmed at their outer edges, uppercased, then validated against `[A-Z][A-Z0-9]{1,15}(?:-[A-Z0-9]{4}){3}` with a maximum normalized length of 128. Internal spaces are not removed. Shared fixed vectors live in `test/fixtures/license-key-vectors.json`.

Device records are designed for LIC-002 values: a random installation ID, lowercase SHA-256 device hash, Base64 X.509 SubjectPublicKeyInfo EC P-256 public key, and `SHA256withECDSA` proof in a later API. Raw Android ID, IMEI, MAC address, phone number, and private keys are not stored.

LIC-005 still only validates and binds the submitted device public key; it does not prove possession of the corresponding private key. Android `SHA256withECDSA` signatures are commonly ASN.1 DER encoded, while Web Crypto ECDSA signatures use IEEE-P1363/raw `r || s`. The challenge milestone must implement and test an explicit DER-to-P1363 conversion or another verified interoperable boundary.

## Milestone boundary

LIC-009 adds authenticated operator APIs and a local HTTP CLI. It does not deploy the Worker, add RBAC or a web dashboard, store customer PII, change the Android activation UI, implement direct device rebind, or provide immediate invalidation of already-issued offline tokens.
