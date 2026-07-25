# DexWorkspaceTouch 1.0.0 Beta 2

## Beta scope

This beta covers the touch-first Designer, Workspace Library, installed-app picker, Samsung DeX
launch flow, local Room persistence, and manual workspace transfer/backup flows. A workspace
supports at most five cells/windows.

The intended release tag is `v1.0.0-beta.2`. The existing published `v1.0.0-beta.1` tag is retained
because it points to a commit carrying older `0.1.0 (1)` Android metadata.

Device verification is recorded in `RELEASE_CHECKLIST.md`. Do not claim two-device verification
until both the Samsung Galaxy S23 Ultra and Note 8 hardware running a Note 9 ROM have completed the
matrix using the final signed APK.

## Importing files

Samsung My Files does not consistently offer DexWorkspaceTouch for custom file extensions. Use
Share and choose DexWorkspaceTouch. Google Files can also be used when direct open is unavailable.

## Before upgrading

Create a `.dwtbundle` backup from the Library and save it outside the app. Android Auto Backup is
disabled. Keep the previous APK and the manual backup if rollback may be required.

An APK signed with the production key cannot upgrade an installed build signed with the Android
debug key. That result is a signature mismatch, not a successful upgrade test. A genuine upgrade
test requires the same signing identity/lineage on both versions.

## Known limitations and release blockers

- Direct open in Samsung My Files is inconsistent; prefer Share or Google Files.
- Some apps do not support multiple instances even when Android accepts the launch request.
- Window placement depends on the target app, Samsung DeX, and device ROM.
- Launcher, adaptive, round, monochrome, and splash resources now use the approved DexWorkspaceTouch branding concept; final two-device visual verification remains pending.
- Production signing credentials have not yet been supplied.
- The project license has not yet been selected.
- There is no About screen yet.

## Final artifact record

Complete only after a signed build is produced:

- APK: pending
- APK SHA-256: pending
- Signer certificate SHA-256: pending
- Note 8 smoke test: pending final signed artifact
- S23 Ultra smoke test: pending final signed artifact
