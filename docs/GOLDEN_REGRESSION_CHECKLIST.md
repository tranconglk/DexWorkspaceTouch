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
