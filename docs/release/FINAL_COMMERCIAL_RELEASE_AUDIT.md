# DexWorkspaceTouch Final Commercial Release Audit

Audit date: 2026-09-12 (Asia/Saigon)  
Repository: `D:\AndroidStudioProjects\DexWorkspaceTouch`  
Branch/commit: `release/1.0-beta` at `1f0958a6dc7bf67ed225ea2af8add4041debf751` (UI-007)  
Mode: read-only; no deploy, production mutation, version bump, commit, or push

## A. Executive summary

Commercial readiness is **82%** for a controlled early-customer beta. It is **not ready for immediate delivery of the current UI-007 build**.

The product, license protocol, production backend, protected operator portal, fulfillment tooling, update worker, signing gate, and customer lifecycle are substantially implemented. A clean local release at UI-007 passed every configured Android gate and used the approved production certificate. Backend/portal/update tests and typechecks pass.

The immediate blocker is artifact identity: the public manifest already advertises `1.0.0-beta.7` / code 8, but its APK hash differs from the clean UI-007 build with that same version. The immutable public beta.7 object must not be overwritten. UI-007 requires a higher version and a new immutable published artifact.

Before the first customer, synchronize stale release/sales/customer documentation, back up the 14 unpushed release-branch commits, and record final upgrade/device evidence. UI-007 remains the accepted visual baseline; this audit found no reason for UI-008.

## B. Current release candidate

Clean verification artifact:

| Field | Value |
|---|---|
| Path | `release-output/DexWorkspaceTouch-1.0.0-beta.7-8.apk` |
| Application ID | `com.trancong.dexworkspacetouch` |
| Version | `1.0.0-beta.7` (8) |
| Size | 26,949,833 bytes |
| APK SHA-256 | `e2896e6471ac90512b9814b2f3288d0d1f7567c8218a53d8ad6bb9264b9e3f4b` |
| Signer SHA-256 | `19ac0ea99125361b3c2083aaa44c8745ebad9c55fa967642c2b9e5a1086a45e7` |
| Git commit | `1f0958a6dc7bf67ed225ea2af8add4041debf751` |
| Dirty | `false` |
| Built at | `2026-09-12T11:37:36Z` |
| Type | release, non-debuggable, production config/signing required |

This is a valid local verification artifact, but **not publishable under code 8**. It must not be mistaken for or uploaded over the hosted APK.

Public manifest state:

- `1.0.0-beta.7` (8), published `2026-09-11T09:34:39Z`;
- size 26,835,145 bytes;
- SHA-256 `249eb25d28144979a1c9caf38ad9faa4756c0dac6fdb06da94d2ebed9c5db5f6`;
- approved signer fingerprint above;
- immutable URL `.../releases/1.0.0-beta.7/DexWorkspaceTouch-1.0.0-beta.7-8.apk`.

The hosted manifest/object are internally coherent (HTTP 200, matching length/type, range support) but predate current UI-007 bytes.

## C. Existing REL/LIC milestone inventory

### DONE

- **LIC-001–LIC-016:** license domain, Android identity/Keystore, activation/device proof, backend binding, signed entitlement, startup gate, admin reset/revoke, production deployment, silent refresh, abuse controls, key rotation, direct APK update, and upgrade continuity. Evidence: implementation/tests and `docs/LIC-010_PRODUCTION_RUNBOOK.md`.
- **LIC-017:** masked activation input, `FLAG_SECURE`, obscured-touch rejection, and diagnostic redaction; see `docs/LIC-017_ACTIVATION_CREDENTIAL_PROTECTION.md`.
- **REL-001:** customer fulfillment through `license-backend/scripts/fulfillment.mjs`, `fulfillment-core.mjs`, tests, and `CUSTOMER_FULFILLMENT_RUNBOOK.md`.
- **REL-002:** Cloudflare Access-protected mobile operator portal; see `docs/REL-002_MOBILE_OPERATOR_PORTAL.md`.
- Direct APK build/publish and R2 delivery foundations exist in `scripts/` and `update-delivery/`.
- UI-001–UI-007 are complete; UI-007 is accepted.

### PARTIAL

- Commercial cutover: components exist, but HEAD lacks a unique publishable version/artifact.
- Release-document synchronization: detailed runbooks are usable; primary/short checklists conflict with current code.
- Production dry-run automation: `license-backend` `npm run check` dry-runs default/local Wrangler configuration, not explicitly `--env production`.
- Hardware matrix: recent S23 and existing legacy evidence exist; final candidate upgrade/Note9 smoke remains.

### NOT STARTED

- No `REL-003` exists in repository source/docs.
- No SBOM generation or Gradle `verification-metadata.xml` is present.
- No broad stable (non-beta) commercial rollout has been cut.

### OBSOLETE

- `docs/RELEASE_CHECKLIST.md` is beta.2/code 3 history, not a current execution checklist.
- Old beta commands in historical runbooks must not be copied as current release inputs.

## D. Android release status

`app/build.gradle.kts` defines application/namespace `com.trancong.dexworkspacetouch`, minSdk 28, target/compileSdk 37, beta.7/code 8, non-debuggable release, and `minifyEnabled=false`. Release signing is resolved outside source and required by the production script. Production backend/trusted-key checks are build gates.

`build-production-release.ps1` runs unit tests, lint, debug APK, androidTest APK, release APK, package/version/signer checks, SHA-256/size/source metadata, and rejects an unacknowledged dirty tree. Outputs use versioned names under ignored `release-output/`.

R8 being off is not a pilot correctness blocker, but increases size/reverse-engineering exposure. Dependency verification and SBOM are broader-release hardening gaps. Keeping beta for a small supported pilot is appropriate; stable should follow at least one proven real update/fulfillment cycle.

## E. Signing/security status

**Signing: PASS.** The clean pipeline enforced production signing and verified certificate `19ac0e…45e7`. The candidate is neither debug-signed nor unsigned. No keystore was changed.

**Secret hygiene: SAFE for the current tracked snapshot.** `.gitignore` excludes local properties, keystores, APK/AAB/build output, `.env*` except examples, `.dev.vars`, local keys, Wrangler state, logs/captures/recordings, databases, fulfillment records/output. Filename/history-name and current-content pattern scans identified only documentation, public configuration, source references, and test fixtures—not a production private key/live credential. Public keys, Access AUD/domain, D1 IDs, worker URLs, and allowlists are identifiers, not authentication secrets.

This was not a forensic scan of every historical blob. A secret ever known to enter history must be rotated. Current evidence does not independently require rotation.

Confirmed invariants: backend-only RS256 private key; Android trusted public registry only; P-256 proof key in Keystore; token signature/`kid`/claims/package/device/time verification; backend stores peppered License Key HMAC, not plaintext; masked/secure/redacted activation; backend admin authorization; Access JWT issuer/audience/signature/email validation and same-origin POST protection; no reviewed production path logs full credentials.

## F. Licensing Android status

Production path: `MainActivity` → `LicenseGate` → `LicenseRuntimeCoordinator` → identities/challenge → Keystore proof → activation API → RS256 verification → private token store → entitlement.

- No valid stored entitlement shows activation. Binding occurs only after successful challenge/proof; there is no Device ID pre-binding.
- Stored tokens are locally verified. Startup/foreground refresh follows policy rather than requiring online activation on every launch.
- Locally valid entitlement has bounded offline grace (current policy/docs: seven days refreshed online).
- Authoritative revoked/expired/device-mismatch refresh clears/rejects entitlement; invalid signature/claims/binding/time fail locally.
- Same-signer in-place upgrade preserves app data, installation identity, Keystore key, and token.
- Uninstall removes private state and normally the Keystore entry; reactivation may require resetting the old device binding.

The token is in app-private `SharedPreferences`, not encrypted storage. Cryptographic token verification plus the Keystore proof key make this acceptable for the present model; encrypted storage review is optional hardening.

## G. Backend status

1. `license-backend/`: production Worker, D1/migrations, activation/challenge/refresh/admin APIs, device slots, revoke/reset, replay/rate limits, signed tokens and rotation registry.
2. `operator-portal/`: separate fulfillment D1, service bindings, Access validation, email allowlist, one-time delivery and audit events.
3. `update-delivery/`: public read-only GET/HEAD over private R2; no public upload route.

Local/dev and production bindings are distinct. Required production secrets are named, not stored in source.

Verification without deploy/database mutation:

- license backend: typecheck PASS; 19 files/130 tests PASS; default/local dry-run PASS. Production-specific dry-run is not part of `check`.
- operator portal: typecheck PASS; 2 files/17 tests PASS; dry-run PASS.
- update delivery: typecheck PASS; 1 file/7 tests PASS; dry-run PASS.
- production license health: HTTP 200/ok.
- unauthenticated portal request: HTTP 302 to configured Cloudflare Access, with no-store/private headers.

## H. Fulfillment/operator status

The normal operator can use the mobile portal; the CLI remains the emergency path. Portal actions include create, find/detail, mark delivered, reset device, revoke/cancel, and replacement. Access protects the edge; portal code verifies JWT/audience/issuer/email; backend admin credentials stay server-side.

`fulfillment.mjs` delegates to `FulfillmentService`/`FulfillmentStore`. Creation validates the live manifest, creates a backend license, stores a non-secret fulfillment record, and produces ignored `customer-delivery.txt` with APK URL/hash, instructions, and one-time plaintext key.

Plaintext is exposed only on create/replacement, is unrecoverable from backend, is absent from fulfillment/D1 records, resides transiently in portal memory or local delivery output, and should be privately delivered then deleted locally after receipt. Records retain references, initial release metadata, delivery/lifecycle state, and audit events. Reset frees a device slot; revoke invalidates; replacement revokes old and creates new credentials.

## I. Update system status

Manifest: `https://dexworkspacetouch-updates.dex-backend.workers.dev/update-manifest.json`.

`UpdateManifestClient` enforces allowed HTTPS, schema/app ID, higher version code, expected signer fingerprint, SHA syntax, size bounds, and release metadata. Invalid/offline/network becomes non-fatal `Unavailable`; equal/lower code becomes `Current` and is not offered as downgrade.

The app hands the HTTPS APK URL to browser/installer. It **does not download and hash APK bytes itself**. Integrity is instead covered by publish-time remote download/hash verification, manifest metadata, HTTPS, and Android's same-signer upgrade enforcement. This boundary should be documented accurately.

The publisher uses immutable keys, uploads/verifies APK first, checks remote length/type/range/hash, backs up the manifest, then publishes manifest last. No update was published in this audit.

## J. First customer workflow

| Step | Status | Evidence/caveat |
|---|---|---|
| Build signed release | READY, version-blocked | Full production gate exists. |
| Publish APK/manifest | MANUAL | Safe APK-first/manifest-last script; authenticated R2 operation. |
| Create license | READY/operator | Portal or `fulfillment -- create --order ...`. |
| Deliver APK/key | READY/MANUAL | One-time private delivery and plaintext cleanup are human duties. |
| Customer install | READY/MANUAL | Official APK/browser installer. |
| Activation/binding | READY | Challenge + proof + signed entitlement. |
| Normal/offline use | READY | Local verification + bounded grace. |
| Update | READY/MANUAL | Check opens official higher-version APK; install over app. |
| Continuity | READY with caveat | Same-signer upgrade preserves state; uninstall does not. |
| Reset/replace/revoke | READY/operator | Portal/CLI, audit trail, refresh enforcement. |

Do not use the flow for a paid customer until P0 and P1 items below are closed.

## K. Upgrade/reinstall continuity

- Install higher code over existing same-application/signer build; Android preserves Room/preferences/identity/Keystore/token.
- Do not uninstall for update: uninstall may erase workspaces, settings, identity, token and proof key.
- After uninstall/phone replacement, reset old binding if needed, then activate with retained key.
- Lost keys cannot be recovered; replacement revokes old license and creates a new key.
- Revocation is enforced at refresh; bounded token/grace duration limits offline continuation.
- This audit did not perform a destructive downgrade/upgrade cycle.

## L. Documentation status

Current foundations: `RELEASE_RUNBOOK.md`, `RELEASE_SIGNING.md`, `R2_APK_UPDATE_GUIDE.md`, `CUSTOMER_FULFILLMENT_RUNBOOK.md`, `REL-002_MOBILE_OPERATOR_PORTAL.md`, `LIC-010_PRODUCTION_RUNBOOK.md`, `LIC-017_ACTIVATION_CREDENTIAL_PROTECTION.md`, and `PRIVACY.md`.

Contradictions/gaps:

1. `RELEASE_CHECKLIST.md` asserts beta.2/code 3 and obsolete artifact/tag data.
2. `SALES_CHECKLIST.md` says `replace-license <fulfillmentId>`, but actual `fulfillment.mjs` calls `createOptions()` and requires `--order`; the detailed runbook form is correct.
3. `USER_GUIDE_VI.md` retains stale English/old floating-dock labels after UI-007.
4. Historical hard-coded beta values can be mistaken for current commands.
5. Generated delivery text relies on the seller's existing channel and does not state one concrete support address.

## M. Device verification status

Safe audit checks: S23 Ultra `SM-S918B` connected; installed app beta.7/code 8; external DeX display 1920×1200 at dynamic display ID 142. No reinstall, data clear, license/workspace mutation, or production operation occurred.

Final S23 checklist:

- [ ] cold process and licensed launch without clearing data;
- [ ] Workspace Library, Editor, App Picker;
- [ ] one/two-app workspace external-display bounds;
- [ ] Car Mode workspace cards;
- [ ] Floating Dock show/tap/drag/snap/expand/collapse/self-hide/restore;
- [ ] production update check and license state;
- [ ] install strictly higher same-signer candidate over distributed build;
- [ ] verify workspace/device identity/entitlement continuity.

Legacy tests cover `LegacyExternalDisplayWorkArea` and coordinate boundaries. Existing task evidence records Note9/Android 10 DeX checks. Classification: **PASS FROM EXISTING EVIDENCE / NEEDS FINAL DEVICE SMOKE / NOT A CODE BLOCKER**. Run the final candidate on physical Note9 if it remains promised; do not change compatibility logic during audit.

## N. P0/P1/P2/P3 findings

### P0 BLOCKER

1. UI-007 bytes reuse an already-published version identity: public code-8 hash `249e…b5f6`, clean UI-007 code-8 hash `e289…3f4b`. Never overwrite the immutable beta.7 object. Cut a higher version before selling/delivery.

### P1 BEFORE FIRST CUSTOMER

1. Replace/synchronize the beta.2 release checklist and current release instructions.
2. Correct `SALES_CHECKLIST.md` replacement syntax and verify short commands against the parser.
3. Synchronize customer UI labels and provide an explicit support channel.
4. Push/back up accepted source after approval: branch is 14 commits ahead of the local remote-tracking ref. No fetch was run, so remote divergence beyond that is unknown.
5. Record final S23 same-signer upgrade/full safe smoke and Note9 core smoke if supported.
6. Record an explicit production-environment Wrangler dry-run; generic license `check` validates default/local config.

### P2 BEFORE BROADER RELEASE

1. Add dependency verification and an SBOM.
2. Evaluate R8/minification after compatibility testing.
3. Add in-app APK byte verification or clearly document the browser/installer boundary.
4. Remove unsafe stale default APK examples and complete the broader two-device regression matrix.

### P3 NICE TO HAVE

1. Review encrypted cached-token storage.
2. Automate a release evidence bundle (tests, checksums, signer, manifest, source metadata).
3. Add an operator release-version summary to reduce stale-doc mistakes.

## O. Recommended next milestone

**REL-003 — First Paid Customer Release Cutover**

No REL-003 exists in the audited repository. This is the smallest next milestone that closes the real boundary without reopening licensing, backend, Car, or UI architecture.

Scope: synchronize release/sales/customer docs; choose `1.0.0-beta.8` / code 9 (recommended) or another strictly higher beta identity; create/back up the approved release commit; execute production dry-runs/full release gate; record S23 and required Note9 same-signer upgrade smoke; publish a new immutable APK and manifest last; verify public bytes/metadata; perform one controlled paid fulfillment. Exclude stable promotion, UI redesign, protocol changes, and architecture replacement.

## P. Exact recommended order to reach first paid customer

1. Freeze UI-007; fix only P1 release/operator/customer docs.
2. Assign a higher beta identity (`1.0.0-beta.8`, code 9 recommended); never overwrite beta.7.
3. Commit/review/push the cutover and retain prior APK/manifest for rollback.
4. Run the production-signed Android gate and archive hash/signer/source/clean metadata.
5. Run all service checks plus explicit production Wrangler dry-runs; do not migrate without a reviewed migration.
6. Upgrade the distributed build in place on S23 and run the safe DeX matrix; repeat core final checks on Note9 if supported.
7. Publish new APK, verify remote bytes, publish manifest last, then re-fetch and compare URL/size/hash/signer/package/version.
8. Use the Access-protected portal to create the first fulfillment; privately deliver generated URL/hash/key/instructions and mark delivered after receipt.
9. Confirm activation/binding, normal use, update visibility, and support path; delete operator plaintext output when no longer needed.
10. Monitor refresh/health and retain reset/replacement/revoke/rollback procedures. Promote stable only after pilot continuity evidence.

## Verification record

- Android: **PASS** — `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `assembleDebugAndroidTest`, `assembleRelease`; 138 tasks, 2m56s.
- Release signer/package/version/clean-tree checks: **PASS**.
- License backend: **PASS** typecheck/130 tests/default dry-run; production-specific dry-run not executed by `check`.
- Portal: **PASS** typecheck/17 tests/dry-run; production Access redirect confirmed.
- Update delivery: **PASS** typecheck/7 tests/dry-run; public manifest/APK availability confirmed.
- Git at start: clean, UI-007 HEAD, branch ahead 14 of local `origin/release/1.0-beta` ref.
- Production mutations: none.
