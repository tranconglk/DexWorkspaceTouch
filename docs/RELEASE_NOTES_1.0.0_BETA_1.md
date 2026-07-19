# DexWorkspaceTouch 1.0.0 Beta 1

## Beta scope

This beta covers the touch-first Designer, Workspace Library, installed-app picker, Samsung DeX launch flow, local Room persistence, and manual workspace transfer/backup flows. A workspace supports at most five cells/windows.

Tested devices:

- Samsung Galaxy S23 Ultra.
- Samsung Galaxy Note 8 hardware running a Note 9 ROM.

Compatibility outside these devices and their tested ROM configurations is not guaranteed.

## Importing files

Samsung My Files does not consistently offer DexWorkspaceTouch for custom file extensions. Use:

1. Select the `.dwt` or `.dwtbundle` file.
2. Choose **Share**.
3. Choose **DexWorkspaceTouch**.

Google Files can also be used when direct open from Samsung My Files is unavailable.

## Before upgrading

Create a `.dwtbundle` backup from the Library and save it outside the app. Android Auto Backup is disabled for this beta, so the Room database is not restored automatically. Rollback is not automatic; keep the previous APK and a manual bundle backup if rollback may be required.

## Known limitations

- Direct-open handling of custom extensions in Samsung My Files is inconsistent; prefer Share or Google Files.
- This beta is not a Play Store production release.
- Some apps do not support multiple instances even when Android accepts the launch request.
- Window placement and multi-instance behavior depend on the app, Samsung DeX, and the device ROM.
- The launcher icon is currently an Android placeholder and must be replaced before a public-facing release.
- There is no About screen yet; version metadata can be inspected through Android App info.
