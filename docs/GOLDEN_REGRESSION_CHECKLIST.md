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
