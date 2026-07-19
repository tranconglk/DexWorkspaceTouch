# Golden Regression Checklist

## Pre-test

- [ ] Record date, device/API/ROM, DeX resolution, APK version and SHA-256.
- [ ] Test both clean install and upgrade without clearing existing workspaces.
- [ ] Confirm external DeX display and visible taskbar.

## Workspace workflow

- [ ] Create, split horizontal/vertical, assign apps and save.
- [ ] Restart process; confirm canvas and assignments remain.
- [ ] Edit then cancel; confirm persisted workspace is unchanged.
- [ ] Edit and save, rename, then delete with confirmation.

## Persistence

- [ ] Restart process and install an upgrade APK without data loss.
- [ ] Upgrade a v1 database to v2; confirm every existing workspace remains and starts unpinned.
- [ ] Pin, force-stop/reopen, rename, and edit/save; confirm pin and workspace content remain.
- [ ] Duplicate a pinned workspace; confirm the source stays pinned and the copy starts unpinned.
- [ ] Unpin and restart; confirm it returns to the regular section.
- [ ] If corruption debug tooling is available, confirm one bad row does not hide valid rows.

## Launch

- [ ] Open a persisted two-app and four-app workspace; verify order and bounds.
- [ ] Remove one assigned app; verify partial/missing-app feedback.
- [ ] Disconnect the display mid-sequence; verify remaining targets stop cleanly.
- [ ] Confirm launched windows avoid the taskbar/system work area.

## S23 Ultra

- [ ] Windowed and maximized modes at 1920×1200.
- [ ] Six-column Library grid, resize without lost selection/state.

## Note 8 legacy ROM

- [ ] Fullscreen auto-capture and windowed trusted-reference fallback.
- [ ] Taskbar remains visible; bounds remain correct.
- [ ] Restart directly in windowed mode without a reference; guidance is shown.

## Pass/Fail record

- Date / APK SHA-256:
- S23 result:
- Note 8 result:
- Failures and reproduction links:
- [ ] Export one five-cell workspace to `.dwt`, import it as a new unpinned ID, recreate, and compare canvas/readiness.
- [ ] Transfer S23 Ultra → Note 8 and Note 8 → S23 Ultra using Quick Share; record received MIME.
- [ ] Save/open through Files, Drive and USB where available; verify Unicode filenames.
- [ ] Reject corrupted, oversized and unsupported-version files without creating a Room row.
- [ ] Library 100 workspace cuộn hết trên S23 Ultra và Note 8, không OOM/crash.
- [ ] App đã gỡ dùng fallback ổn định; icon cache hit/eviction đúng giới hạn 64.
- [ ] Card hiển thị đúng “x ô • y ứng dụng”, relative time và giữ nguyên sau recreation.
- [ ] Backup Library → clear test database → restore preserves every canvas/app and resets all pins.
- [ ] Restore the same bundle twice creates new IDs/names without overwrite.
- [ ] Malformed, future-version and oversized bundles preview/restore nothing.
- [ ] Open `.dwt` and `.dwtbundle` by ACTION_VIEW from cold and warm app states; each event shows one preview.
- [ ] Record provider package, reported MIME, extension and detected envelope for My Files, Downloads,
  Quick Share, Drive, Zalo and Bluetooth where available; do not record payload or full URI.
- [ ] Open the same URI twice, resize with preview open, and recreate the Activity; no consumed preview repeats.
- [ ] Open an external file while Designer has an unsaved draft; transfer waits and the draft remains intact.
- [ ] Reject malformed, future-version and oversized external files before any Room write.
- [ ] Samsung My Files: select and Share one `.dwt` to DexWorkspaceTouch; preview and confirm import.
- [ ] Samsung My Files: select and Share one `.dwtbundle`; preview and confirm atomic restore.
- [ ] Google Files ACTION_VIEW and Share both continue to work for `.dwt` and `.dwtbundle`.
- [ ] ACTION_SEND cold start, warm start, Designer pending, resize, and intentional same-file reshare
  each preserve the one-delivery/one-preview policy.

Observed provider MIME:

- 2026-07-19, Note 8 legacy ROM (`SM-N960N`): Downloads document provider reported
  `application/octet-stream` for the received `.dwt` file. Content URI path exposed only a document
  ID, not the filename extension; octet-stream fallback is therefore required on this device.
- 2026-07-19, the same device's Samsung My Files opened `market://search?q=dwtbundle` and emitted no
  file ACTION_VIEW. MIME-less `.dwt`/`.dwtbundle` extension filters are required for this legacy path.
- 2026-07-19, Samsung My Files ACTION_SEND passed on S23 Ultra and Note 8 legacy ROM for both formats:
  `application/octet-stream`, EXTRA_STREAM present, one matching ClipData URI, read-grant flag set,
  and correct single/bundle envelope detection. Direct-open remains provider-dependent; Share is the
  supported Samsung fallback.
