# LIC-010 Production Operations Runbook

This document contains non-secret operational metadata and procedures. Never add a license pepper, RSA private key, admin token, smoke License Key, keystore password, or Cloudflare API token here.

## Current verified Android signing identity

- Package: `com.trancong.dexworkspacetouch`
- Direct release keystore: operator-managed outside the repository
- Alias: `dexworkspacetouch`
- Direct APK signing certificate SHA-256: `19:AC:0E:A9:91:25:36:1B:3C:20:83:AA:A4:4C:87:45:EB:AD:9C:55:FA:96:76:42:C2:B9:E5:A1:08:6A:45:E7`
- Valid through: 2053-12-04
- Backend canonical value: `19ac0ea99125361b3c2083aaa44c8745ebad9c55fa967642c2b9e5a1086a45e7`

The debug signer is deliberately not allowlisted. Before a Play rollout, obtain the certificate of the APK actually delivered by Play. If it differs, add that app-signing certificate to the comma-separated backend allowlist and deploy the backend configuration before releasing the Play build. Do not substitute the upload-key certificate unless it is also the runtime APK signer.

## Production prerequisites and record

Fill these from official tool output; do not invent them:

- Cloudflare account: `Tranconglk@gmail.com's Account` (`6bf03d8371c371496c9edcaca90ce41b`)
- Worker name: `dexworkspacetouch-license-production`
- Worker version/deployment ID: `e96efb6c-57b0-42da-b32d-bb93a959350f` (LIC-013)
- Production hostname: `dexworkspacetouch-license-production.dex-backend.workers.dev`
- D1 database: `dex-workspace-touch-license-prod` / `6d7b7e58-2f3a-477c-bdea-fe819bf34c09` / APAC (primary verification served from HKG)
- Applied migrations: `0001_initial.sql`, `0002_activation_challenge_proof.sql`, `0003_admin_operations.sql`
- LIC-011 migration: `0004_license_refresh.sql`
- License token `kid`: `license-signing-v1`
- Android release `DWT_LICENSE_SIGNING_KEY_ID` must match the backend
  `LICENSE_SIGNING_KEY_ID`. Both default to `license-signing-v1`; set both explicitly
  when rotating signing keys.
- Production RSA public-key fingerprint: `ae8c0a5376ceef82fa3f0123e2788c21cc218bb19fb866aed83813745d392e74`
- Android version: `1.0.0-beta.3` (`versionCode=4`)
- Release APK path: `app/build/outputs/apk/release/app-release.apk`
- Verified production build date: 2026-09-10

`wrangler.production.example.jsonc` is a template only. Copy it to an ignored/operator-controlled production config and replace every `REPLACE_WITH_*` value with real Wrangler output. Never deploy the example file.

## Controlled first deployment

1. Run `npm run typecheck`, `npm test`, and `npm run deploy:dry-run`.
2. Run Android debug regression and verify the release signer with `gradlew signingReport`.
3. Run `wrangler whoami` and have the operator confirm the account.
4. Create the production D1 only after confirmation. Record the returned name, ID, and chosen location.
5. Configure the real production file with its explicit D1 binding; production must not inherit a local/staging binding.
6. Run `wrangler d1 migrations list <database> --remote --config <production-config>`.
7. Apply migrations through Wrangler, then list them again and verify no pending migration remains.
8. Query `sqlite_master` read-only to verify `licenses`, `devices`, `license_events`, `license_challenges`, and their indexes. Do not seed a license with raw SQL.
9. Confirm secure backups of all material below before issuing a real license.
10. Configure `LICENSE_KEY_PEPPER`, `LICENSE_SIGNING_PRIVATE_KEY`, and `LICENSE_ADMIN_TOKEN` through a supported Wrangler secret workflow. Do not put secret values in command arguments or source.
11. Verify the production package, certificate allowlist, D1 binding, HTTPS hostname, and required secrets before deployment.
12. Deploy once deliberately, record the Worker version/deployment identifiers, then perform the smoke matrix.

For later releases: tests → dry-run → migration compatibility review → version upload/preview where supported → version deployment → monitor. Challenge and activation are a multi-request protocol, so review version skew before gradual rollout of protocol changes.

## Secret and signing backup checklist

Store independently in an operator-controlled encrypted/offline system:

- exact `LICENSE_KEY_PEPPER` and creation date;
- exact PKCS#8 `LICENSE_SIGNING_PRIVATE_KEY`, matching SPKI public key/fingerprint, creation date, and `kid`;
- `LICENSE_ADMIN_TOKEN` and creation date;
- Android app-signing keystore, alias, certificate fingerprint, creation date if known, and passwords separately;
- Cloudflare account/Worker name, production hostname, D1 database name/ID/location;
- current release metadata.

Do not create a committed archive containing these values. Temporary secret files must live outside the repository or in an ignored directory and must be removed after upload. Confirm an independent backup before customer issuance.

## Recovery consequences

- Pepper lost: existing plaintext customer License Keys cannot be looked up through their stored HMAC. Restore the exact pepper; generating another value is not recovery.
- RSA private key v1 lost: existing tokens remain verifiable until expiry, but the backend cannot issue new v1 tokens. Restore the exact key. Runtime key rotation belongs to a later milestone.
- Admin token lost: an operator with Cloudflare access may replace it. This does not change customer-key lookup or token signing.
- Android signing key lost: direct-distribution update lineage may be permanently broken. Restore the operator backup; never replace it casually.

## D1 Time Travel recovery

Record a bookmark/timestamp and inspect it first:

```text
wrangler d1 time-travel info <database> --remote --config <production-config>
```

Use `wrangler d1 time-travel restore --help` to confirm the syntax supported by the installed Wrangler version. Before any production restore, independently confirm the exact database, target bookmark/timestamp, incident approval, and application downtime/consistency plan. Never run a restore merely to test this runbook; use local or staging for drills.

Worker rollback and D1 recovery are separate lifecycles. A Worker rollback does not undo migrations or data. Before choosing a known-good version from `wrangler versions list` / `wrangler deployments list`, confirm that its code is compatible with the current D1 schema. Do not run destructive down-migrations.

## Production smoke matrix

Record PASS/FAIL independently for HTTPS health, unauthenticated admin rejection, authenticated admin access, license creation, Android challenge, ECDSA proof, RS256 token verification, Active gate, offline restart/OfflineGrace, safe device listing, reset, revoke, rejected post-revoke challenge, and sensitive-log review.

### First deployment result — 2026-09-10

- HTTPS health, admin authorization boundaries, license creation: PASS.
- Synthetic challenge/proof, RS256 verification, safe device listing and reset: PASS.
- Signed release activation on S23 Ultra (SM-S918B), Active startup gate and offline restart within grace: PASS.
- Smoke license revoke: PASS. The plaintext smoke key was removed after revocation.
- Rejected post-revoke challenge: not rerun after plaintext-key disposal; server status was verified as `REVOKED`.
- Sensitive-log review: FAIL for the first run because an ADB UI diagnostic exposed the disposable smoke key in operator tool output. The license was revoked and its local plaintext file permanently removed. Never dump activation-field text in later production diagnostics.

### LIC-011 rollout result — 2026-09-10

- D1 migration `0004_license_refresh.sql`: PASS; table and both state/expiry indexes verified read-only in APAC/HKG.
- Production Worker version: `ba9ab941-4165-446a-9888-f3f8eb516394`.
- Existing health and activation routes after additive deploy: PASS.
- Refresh endpoint without bearer: `401 TOKEN_INVALID`, `Cache-Control: no-store`.
- S23 Ultra signed-release activation with a dedicated short-expiry smoke license: PASS.
- S23 Keystore refresh proof and `TOKEN_REFRESHED` audit event: PASS.
- Restart offline using the refreshed token: PASS.
- Online license revoke caused the root License Gate to uncompose the main UI: PASS.
- Online device reset caused the root License Gate to uncompose the main UI: PASS.
- Both disposable smoke licenses were revoked; both plaintext key files were permanently removed.

Create the smoke license through `npm run admin -- create --max-devices 1`, never raw D1. Keep its key outside logs and Git. After device reset/rebind coverage, revoke the smoke license rather than deleting it. Previously issued offline tokens can remain usable until their signed local end; revoke/reset is not instant offline invalidation.

## LIC-011 silent refresh

Normal activation remains unchanged: a License Key starts the activation challenge, the
device signs the 32-byte nonce with its Android Keystore P-256 key, and the backend returns
an RS256 license token. The plaintext License Key is never stored.

For an already licensed installation, startup verifies the stored token locally and opens
the application immediately in `OfflineGrace`. Network refresh happens afterward and is due
when either 24 hours have elapsed since token issuance or no more than 72 hours remain before
the token's effective local end.

Refresh uses this protocol:

```text
existing signed token as Authorization bearer
-> backend RS256 verification and database authorization check
-> one-time 32-byte refresh challenge (120-second TTL)
-> Android Keystore P-256 proof
-> atomic challenge consumption and final license/device check
-> new RS256 token
-> Android full token verification
-> atomic replacement of the stored token
```

The backend and Android key resolver must use the exact configured `kid`, currently
`license-signing-v1`. There is no legacy `v1` fallback. The backend verification SPKI must
match the existing production signing private key; its recorded SHA-256 fingerprint is
`ae8c0a5376ceef82fa3f0123e2788c21cc218bb19fb866aed83813745d392e74`.

Timeout, offline, DNS and 5xx responses retain the old valid token and keep `OfflineGrace`.
An authoritative online `LICENSE_REVOKED`, `LICENSE_EXPIRED`, `DEVICE_REVOKED`,
`DEVICE_MISMATCH`, or `APPLICATION_NOT_ALLOWED` response clears the stored token and blocks
the application at the root License Gate. A reset/revocation cannot be learned while the
device is offline; enforcement occurs on the next successful refresh contact or when the
signed token reaches its hard expiry. Expired tokens cannot be silently refreshed and require
normal License Key activation.

## LIC-012 abuse protection and security observability

Production uses five independent Cloudflare Workers Rate Limiting bindings. Each binding has
a distinct production namespace and a 60-second window:

| Boundary | Actor key source | Limit |
| --- | --- | ---: |
| Activation | HMAC of the normalized License Key, or activation challenge ID | 10 |
| Unauthenticated public traffic | `CF-Connecting-IP` | 60 |
| Refresh | verified license/device IDs, or refresh challenge ID | 10 |
| Authenticated admin | the single current operator principal | 120 |
| Unauthorized admin | `CF-Connecting-IP` | 20 |

The default development/test configuration deliberately uses separate namespace IDs and high
limits. Never reuse those namespaces for production. A limiter rejection returns HTTP 429 with
stable code `RATE_LIMITED`, `Retry-After: 60`, `Cache-Control: no-store`,
`X-Content-Type-Options: nosniff`, and `X-Request-ID`. Binding absence or runtime failure fails
closed with HTTP 500. Android treats refresh 429/5xx as transient and keeps a still-valid token
in `OfflineGrace`; activation keeps the entered key and shows a retry-later message. The admin
CLI does not retry automatically and prints the status, stable code, request ID, and safe
`Retry-After` value.

Security logs are structured JSON containing only `event`, `route`, `status`, `code`, and
`requestId`. Do not add License Keys, bearer tokens, proof signatures, device/installation IDs,
challenge IDs, request bodies, raw IP addresses, or admin credentials. Tail sampling can omit
events under load and is not an audit database; durable license/device changes remain in D1
audit events.

For a controlled rate-limit smoke test, create a dedicated disposable license, send the minimum
number of challenge requests needed to cross only its activation threshold, confirm one 429 and
stop immediately. Do not test the public-IP threshold against shared traffic. Revoke the smoke
license and permanently remove its plaintext key afterward. Check the Worker tail for the five
allowed log fields and correlate by `X-Request-ID`; never paste activation UI text or credentials
into diagnostics.

### LIC-012 rollout result — 2026-09-10

- Production Worker version: `a99769c0-4984-415a-a977-800a53ea4429`.
- Five distinct production bindings and configured limits: PASS by Wrangler deploy output.
- HTTPS health after deploy: PASS (`200` with request ID).
- Unauthorized-admin baseline and safe structured log: PASS (`401 ADMIN_UNAUTHORIZED`;
  application log contained only the five allowlisted fields).
- Controlled live 429: INCONCLUSIVE. Twenty-six rapid calls from one actor remained `401`.
  Testing stopped without increasing production traffic. Cloudflare documents this API as
  permissive and eventually consistent, so burst traffic may temporarily exceed the configured
  threshold. Deterministic fake-binding tests cover both rejection and binding-failure paths;
  repeat live validation later with paced isolated staging traffic instead of a shared
  production IP.
- Signed release update and cold launch on S23 Ultra (`SM-S918B`): PASS; process remained alive.

## License Token Signing Key Rotation

This procedure applies only to the backend RS256 license-token signing key. It does not rotate
the Android APK signing certificate, the device P-256 Keystore key, or the License Key HMAC
pepper. These are three independent trust systems. Never place a private key in the trusted
public-key registry and never fetch a trusted key from an activation/refresh response.

### Phase 0 — current state

The backend signs with `license-signing-v1`; Android and the backend trust exactly that kid.
The identifier is exact and case-sensitive. `v1` is not an alias. Record the active kid, trusted
kid count, and canonical SPKI SHA-256 fingerprint before any change.

### Phase 1 — distribute trust before signing

Generate v2 in an isolated operator environment and back up its private key before use. Add only
the v2 public SPKI to the Android signed-build registry, so clients trust v1+v2 while the server
still signs v1. Release the client and verify that enough supported installations have received
the trust update. Do not change the server signer yet.

### Phase 2 — overlap and signer cutover

Configure the backend verifier to trust v1+v2. Verify the active-kid membership and private/public
match preflight, then change the active kid and private-key secret together to v2. New activation
and refresh tokens use v2; existing v1 bearer tokens remain accepted and can silently refresh to
v2. If verification fails during overlap, switch the active signer back to v1 while retaining
both public keys.

### Phase 3 — retention window

Keep v1 verification trust and retain the backed-up v1 private key for rollback for at least the
maximum v1 token lifetime, plus operational safety margin. Monitor old-client population and
unknown-kid failures. Active signer selection never invalidates a locally stored token by itself.

### Phase 4 — optional retirement

Remove v1 trust only after every legitimately issued v1 token has expired and unsupported old
clients are within the approved retirement policy. Test removal in staging first. Never destroy
the v1 private-key backup immediately after switching to v2.

The safe order is therefore: generate/back up v2 → ship client trust v1+v2 → add server trust
v1+v2 → confirm rollout → sign v2 → wait out all v1 tokens → optionally retire v1. Pepper
rotation requires a separate dual-HMAC lookup/data-migration design and is outside this procedure.

### LIC-013 rollout result — 2026-09-10

- Production Worker version: `e96efb6c-57b0-42da-b32d-bb93a959350f`.
- Active signer: `license-signing-v1`; trusted kids: `[license-signing-v1]`; count: 1.
- Active public SPKI SHA-256: `ae8c0a5376ceef82fa3f0123e2788c21cc218bb19fb866aed83813745d392e74`.
- Post-deploy health signer/trust preflight: PASS. No production key, pepper, admin token,
  application certificate allowlist, D1 schema, or rate-limit namespace/threshold changed.
- Signed Android release update and cold launch on S23 Ultra: PASS.
- Fresh production activation/refresh/offline smoke was not run because no operator admin
  credential or disposable smoke License Key was present in the execution environment. Existing
  protocol regression and the generated v1→v2 refresh simulation passed locally.

### LIC-014 direct APK release result — 2026-09-10

- Release: `1.0.0-beta.3` (`versionCode=4`).
- Artifact: `release-output/DexWorkspaceTouch-1.0.0-beta.3-4.apk` (generated/ignored).
- APK SHA-256: `1daf7c152bb5ae04b5226187fb6d6315c6208ebcdbe92f3da314624623ded8bc`.
- APK signer SHA-256 matched the existing production signer.
- S23 Ultra true package update from versionCode 3 to 4 via `adb install -r`: PASS;
  package first-install timestamp remained unchanged and cold launch passed.
- Workspace, device-key and license-token continuity: NOT RUN because the installation was
  unactivated and no representative licensed workspace state was available before the update.
- Fresh-install destructive test and production activation smoke: NOT RUN; no uninstall was
  performed and no disposable production license/admin credential was available.
- Backend deployment: NOT REQUIRED. No backend source/configuration or production crypto changed
  as part of LIC-014.
