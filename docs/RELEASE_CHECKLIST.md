# Production Release Checklist

Read current version values from `app/build.gradle.kts` and the generated release manifest. Values
in historical release notes are evidence only and must not be copied into a new release.

## Source and metadata

- [ ] Work on `release/1.0-beta`.
- [ ] Working tree is clean after the approved release commit.
- [ ] Confirm `versionName` and `versionCode` match the approved release cut and exceed every distributed build.
- [ ] Confirm the versioned R2 APK URL has never been used for different bytes.
- [ ] Fetch the configured remote and confirm the release branch is not behind or diverged.
- [ ] Confirm app label is `DexWorkspaceTouch`.
- [x] Replace the placeholder launcher icon with approved adaptive, round, monochrome, legacy, and Android 12 splash resources.
- [ ] Verify final launcher and splash presentation after reinstall on both Samsung test devices.
- [ ] Complete legal review: this repository currently has no LICENSE or NOTICE file.

## Verification

- [ ] `testDebugUnitTest` passes.
- [ ] `lintDebug` has no errors and all warnings are reviewed.
- [ ] `assembleDebug`, `assembleRelease`, and `assembleDebugAndroidTest` pass.
- [ ] Room schema 1 and 2 JSON files are tracked and unchanged unless a migration was intentionally approved.
- [ ] Migration 1→2 passes on both test devices; no destructive fallback exists.
- [ ] `.dwt` and `.dwtbundle` format version 1 regression tests pass.
- [ ] Import/export and Library backup/restore smoke tests pass.
- [ ] Workspace launch smoke test passes.

## Signing and artifacts

- [ ] Configure the release keystore outside Git as described in `RELEASE_SIGNING.md`.
- [ ] Build with `DWT_REQUIRE_RELEASE_SIGNING=true`.
- [ ] Run `signingReport` and verify the expected release certificate.
- [ ] Run `apksigner verify --verbose --print-certs` and verify v2/v3 schemes as appropriate.
- [ ] Confirm package `com.trancong.dexworkspacetouch`, version name/code, `debuggable=false`, and `testOnly=false`.
- [ ] Record APK SHA-256 and archive the signed artifact securely.

## Device matrix

- [ ] Install/upgrade, cold start, and Room upgrade on S23 Ultra.
- [ ] Install/upgrade, cold start, and Room upgrade on Note 8 hardware/Note 9 ROM.
- [ ] Test DeX launch and window placement on both devices.
- [ ] Test `.dwt` Share/import and export on both devices.
- [ ] Test `.dwtbundle` backup/restore in both directions.
- [ ] Verify no debug benchmark or launch diagnostics component exists in the release manifest/APK.
- [ ] Verify About shows the final version, channel, commit/date fallback, and current display wording.
- [ ] Copy diagnostics on both device/display modes and confirm no personal or workspace data appears.
- [ ] Verify circle, rounded-square, squircle, Samsung launcher, and themed-icon masks do not crop the approved mark.
- [ ] Verify API 28â€“30 cold start has no placeholder/white flash in phone and DeX window modes.
- [ ] Verify API 31+ system splash in phone, DeX windowed, and DeX maximized modes.

## Release

- [ ] Review the current release notes, privacy text, customer instructions, known limitations, and rollback notes.
- [ ] Create the release commit only after approval.
- [ ] Create the corrected annotated tag only after the signed artifact is verified.
- [ ] Push branch/tag only after approval.
- [ ] Preserve the previous APK and manual `.dwtbundle` backup for rollback.
- [ ] Push/back up the verified source before production publication.
- [ ] Upload a new immutable APK object first and publish `update-manifest.json` last.
- [ ] Re-fetch the public manifest/APK and compare URL, version, size, SHA-256, and signer.

Record candidate-specific artifact and device evidence in the active `REL-*` release document, not
as reusable values in this checklist.

Customer support: `dexworkspacetouch.support@gmail.com`.
