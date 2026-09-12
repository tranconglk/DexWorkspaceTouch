# REL-003 — First Paid Customer Release Cutover

Status: **COMPLETE — READY FOR FIRST PAID CUSTOMER**

This record contains release evidence only. No customer fulfillment was created, no license key was generated, and no secret is recorded here.

## Source and release identity

- Branch: `release/1.0-beta`
- Preparation commit: `618c81599c47c8d616dbde54c4aac27fab4af572`
- Version name / code: `1.0.0-beta.8` / `9`
- Application ID: `com.trancong.dexworkspacetouch`
- Build working tree: clean
- Build timestamp: `2026-09-12T12:56:44Z`
- Support channel: `dexworkspacetouch.support@gmail.com`
- APK: `release-output/DexWorkspaceTouch-1.0.0-beta.8-9.apk`
- APK size: `26,949,837` bytes
- APK SHA-256: `355bb38bac796cb7d0f728c4b8fd7d8b0f709cfdbb7920595877fec25e049764`
- Signing certificate SHA-256: `19ac0ea99125361b3c2083aaa44c8745ebad9c55fa967642c2b9e5a1086a45e7`
- Release build: non-debuggable

## Verification gates

Android verification passed at the release source commit:

- `testDebugUnitTest --rerun-tasks`
- `lintDebug`
- `assembleDebug`
- `assembleDebugAndroidTest`
- `assembleRelease`
- production release verification script

Production-specific dry-runs passed without deploying or mutating production:

- `license-backend`: typecheck, 130 tests, Wrangler production dry-run
- `operator-portal`: typecheck, 17 tests, Wrangler production dry-run
- `update-delivery`: typecheck, 7 tests, Wrangler production dry-run

The preparation commit was pushed before production publication. At the pre-publish gate, local and `origin/release/1.0-beta` were synchronized (`0` behind, `0` ahead), the working tree was clean, and the beta.8 object returned HTTP 404.

## S23 Ultra in-place upgrade smoke

- Device: Samsung S23 Ultra (`SM-S918B`)
- Install path: in-place `adb install -r`; no uninstall and no data clear
- Baseline: public `1.0.0-beta.7` / code `8`
- Installed result: `1.0.0-beta.8` / code `9`
- Existing installation identity retained: first-install timestamp was unchanged
- License entitlement retained: app opened without an unexpected activation gate
- Workspace data and pinned workspace state retained
- External DeX mode: `1920 x 1200`
- Dynamic external display ID during this run: `142`
- Home, Editor, App Picker, Car Mode, and Update screen opened successfully
- One-app workspace: YouTube launched full usable width on display 142
- Two-app workspace: ePass and ServiceFare launched 50/50 on display 142 with bounds `[8,8][952,1136]` and `[968,8][1912,1136]`
- Floating Dock: shown on display 142, expanded and collapsed successfully
- Drag/snap: collapsed dock moved from left to right edge, ending at `(1848,384)` for a `72 x 72` window
- Self-hide presentation remained available at the right edge after idle timeout
- Desktop shortcut bootstrap: after Hide, `CarDockShortcutActivity` reopened a new dock window on display 142 at the default left edge

The installed beta.8 Update screen was exercised. Public manifest comparison also proves code 9 is not lower than the currently published code 9. A downgrade test was intentionally not performed.

## Note9 status

**NOT EXECUTED — HARDWARE UNAVAILABLE.**

This was not a code blocker in the final commercial audit. If Note9 remains part of the supported product promise, its final physical-device regression remains an open P1 validation item.

## Production publication

Publication order was preserved:

1. Upload the new versioned beta.8 APK object.
2. Verify the object endpoint and identity.
3. Publish `update-manifest.json` last.
4. Re-fetch and verify the public manifest and APK.
5. Re-fetch beta.7 and confirm it was unchanged.

Public immutable APK URL:

`https://dexworkspacetouch-updates.dex-backend.workers.dev/releases/1.0.0-beta.8/DexWorkspaceTouch-1.0.0-beta.8-9.apk`

Public manifest URL:

`https://dexworkspacetouch-updates.dex-backend.workers.dev/update-manifest.json`

Verified public response and manifest values:

- HTTP status: `200`
- Content-Type: `application/vnd.android.package-archive`
- Cache-Control: `public, max-age=31536000, immutable`
- Application ID: `com.trancong.dexworkspacetouch`
- Version name / code: `1.0.0-beta.8` / `9`
- APK size: `26,949,837` bytes
- Downloaded APK SHA-256: `355bb38bac796cb7d0f728c4b8fd7d8b0f709cfdbb7920595877fec25e049764`
- Signing certificate SHA-256: `19ac0ea99125361b3c2083aaa44c8745ebad9c55fa967642c2b9e5a1086a45e7`
- Manifest publication timestamp: `2026-09-12T12:56:44Z`

## Rollback and immutability evidence

The prior beta.7 object remains available and unchanged:

- Version name / code: `1.0.0-beta.7` / `8`
- URL: `https://dexworkspacetouch-updates.dex-backend.workers.dev/releases/1.0.0-beta.7/DexWorkspaceTouch-1.0.0-beta.7-8.apk`
- Size before and after beta.8 publication: `26,835,145` bytes
- SHA-256 before and after publication: `249eb25d28144979a1c9caf38ad9faa4756c0dac6fdb06da94d2ebed9c5db5f6`

Rollback means restoring the manifest according to the release runbook. Immutable APK objects must not be overwritten or deleted.

## Fulfillment status

No real paid order was supplied during REL-003. No dummy production fulfillment was created. The Access-protected operator portal is ready for a real order, and the release is therefore **READY FOR FIRST PAID CUSTOMER**, not “customer delivered.”
