# Release Checklist

## Source and metadata

- [ ] Work on `release/1.0-beta`.
- [ ] Working tree is clean after the approved release commit.
- [ ] Confirm `versionName=1.0.0-beta.1` and `versionCode=2` exceed every distributed build.
- [ ] Resolve the existing premature `v1.0.0-beta.1` tag; do not publish it as-is because it points to `0.1.0 (1)` metadata.
- [ ] Confirm app label is `DexWorkspaceTouch`.
- [ ] Replace the placeholder launcher icon and verify adaptive, round, monochrome, and Android 12 splash presentation on Samsung launchers.
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

## Release

- [ ] Review `RELEASE_NOTES_1.0.0_BETA_1.md`, privacy text, known limitations, and rollback notes.
- [ ] Create the release commit only after approval.
- [ ] Create the corrected annotated tag only after the signed artifact is verified.
- [ ] Push branch/tag only after approval.
- [ ] Preserve the previous APK and manual `.dwtbundle` backup for rollback.
