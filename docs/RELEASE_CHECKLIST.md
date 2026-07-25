# Release Checklist

## Source and metadata

- [ ] Work on `release/1.0-beta`.
- [ ] Working tree is clean after the approved release commit.
- [ ] Confirm `versionName=1.0.0-beta.2` and `versionCode=3` exceed every distributed build.
- [x] Preserve the published `v1.0.0-beta.1` tag; the intended next tag is `v1.0.0-beta.2`.
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

- [ ] Review `RELEASE_NOTES_1.0.0_BETA_2.md`, privacy text, known limitations, and rollback notes.
- [ ] Create the release commit only after approval.
- [ ] Create the corrected annotated tag only after the signed artifact is verified.
- [ ] Push branch/tag only after approval.
- [ ] Preserve the previous APK and manual `.dwtbundle` backup for rollback.
