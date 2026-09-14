# COMP-003 — Samsung S22 One UI 8 DeX Bounds Reproduction

Date: 2026-09-14  
Branch: `release/1.0-beta`  
Investigation status: **Root cause confirmed: One UI rearranges correctly requested bounds**

This was a device-only investigation. No production source, launch flags, work-area algorithm, version, or published artifact was changed.

## A. Device and environment

| Field | Observed value |
|---|---|
| Manufacturer | Samsung |
| Model | SM-S908E (Galaxy S22 Ultra) |
| Android | 16 |
| SDK | 36 |
| One UI | 8.0 (`ro.build.version.oneui=80000`) |
| Build/PDA | `S908EXXSEGZH4` |
| Build fingerprint | `samsung/b0qxxx/b0q:16/BP2A.250605.031.A3/S908EXXSEGZH4:user/release-keys` |
| DeX connection | Wired HDMI (external port 4) |
| Runtime display ID | 8 (discovered; not hard-coded) |
| Physical/HWC display ID used for capture | 4 |
| DeX mode | 1920×1080, mode 39 |
| Activity display bounds | 1920×1080 |
| DeX logical density | 160 dpi |
| Device `wm size` | 1080×2316 |
| Device `wm density` | 450 dpi |

`dumpsys activity` reports display 8 with base/current/app dimensions 1920×1080. DeX reserves the bottom desktop area; observed task bottoms are predominantly 1008 or 1024 depending on the app/OEM decoration path.

## B. APK/build identity

| Field | Value |
|---|---|
| Installed package | `com.trancong.dexworkspacetouch` |
| Version | `1.0.0-beta.8` (9) |
| targetSdk | 37 |
| Build commit shown in About | `618c81599c47c8d616dbde54c4aac27fab4af572` |
| Local release APK | `release-output/DexWorkspaceTouch-1.0.0-beta.8-9.apk` |
| APK SHA-256 | `01237537AE3DF8C4577B954EE27E2F7A57CDB0E97573BB2FD5B5D23E2B8FA660` |
| Signer SHA-256 | `19ac0ea99125361b3c2083aaa44c8745ebad9c55fa967642c2b9e5a1086a45e7` (release-gate evidence) |

The already-installed production-equivalent build was used; no reinstall, version bump, or publication occurred.

## C. Workspace definitions

Existing device workspaces were preserved:

- Workspace 1: Chrome + Google, two equal columns.
- Workspace 2: Samsung Tips + Samsung News + Calculator, three equal columns.
- Workspace 3: Contacts + Samsung Browser + YouTube Music + Calculator, 2×2.

A persisted Workspace 4 was used with this exact left-to-right definition:

- Chrome: normalized `0.00–0.25`, width 0.25.
- Samsung Browser: normalized `0.25–0.50`, width 0.25.
- Samsung Tips: normalized `0.50–1.00`, width 0.50.

The library preview and editor evidence show the saved order and proportions. Evidence: `home-with-workspace4.png` and `ratio-25-25-50.png`.

## D. Test A — cold 25/25/50

Before launch, `am force-stop` was applied only to the three test packages; app data was not cleared:

- `com.android.chrome`
- `com.sec.android.app.sbrowser`
- `com.samsung.android.app.tips`

The preflight task query was empty. Workspace 4 was then launched once.

Result: **FAIL — the reported 25/25/50 visual pattern was reproduced.**

Saved order was Chrome 25%, Samsung Browser 25%, Tips 50%. Actual order/geometry was Chrome 25% left, Tips 50% center, Samsung Browser 25% right:

- Chrome task 10439: `Rect(0,16 - 464,1024)`, width 464.
- Samsung Browser task 10440: `Rect(1456,0 - 1920,1008)`, width 464.
- Tips task 10441: `Rect(488,6 - 1432,1014)`, width 944.

Thus the large third window retained its expected width but was placed in the middle, while the second 25% window moved to the right. This is not an 8 px decoration translation.

The COMP-002 JSON could not be exported. On this S22 DeX configuration, the About dialog lays out only the final Close/Copy row inside the reachable 1080-pixel activity coordinate area; the Export/Clear diagnostics row is clipped and absent from the accessibility hierarchy. No production UI change was made to work around it.

## E. Test B — warm same 25/25/50

**NOT RUN by task rule.** Test A did not pass, so the conditional warm-same test was not started. The cold tasks were left available for inspection.

## F. Test C — warm different workspace

**NOT RUN by task rule.** Test A did not pass, so no 50/25/25 companion workspace was created or launched.

## G. Test D — two-app 50/50

Workspace 1 was launched twice without closing the first pair. Both launches produced new tasks, confirming `NEW_TASK | MULTIPLE_TASK` is effective for these two targets on this One UI build.

Expected request from a 1920×1024 usable area with 8 px inward margin:

- Chrome: `Rect(8,8 - 952,1016)`
- Google: `Rect(968,8 - 1912,1016)`

Observed cold task state:

- Chrome task 10432: `Rect(0,16 - 944,1024)`
- Google task 10433: `Rect(976,0 - 1920,1008)`

Observed warm reopen:

- Chrome task 10434: `Rect(0,16 - 944,1024)`
- Google task 10435: `Rect(976,0 - 1920,1008)`

The geometry is stable between launches, but the OEM moves each requested rectangle by 8 px diagonally outward in opposite directions. Width and height remain 944×1008. This is an actual/request boundary adjustment, not a 25/50 ratio swap.

## H. Test E — four-app 2×2

**INCONCLUSIVE.** The launch was requested while old task pairs and setup windows were still present. One UI displayed its five-visible-app limit message and minimized older windows. The resulting screenshot cannot be treated as a clean 2×2 cold launch. Existing task state was captured, but it is intentionally excluded from the pass/fail table.

## I. Requested versus actual

| Test | App | Seq | Task | Display | Normalized | Requested | Actual | Delta L/T/R/B | Result |
|---|---|---:|---:|---:|---|---|---|---|---|
| A cold | Chrome | 1 | 10439 | 8 | 0–0.25 / 0–1 | expected `8,8,472,1016`* | `0,16,464,1024` | `-8,+8,-8,+8` | PASS width; translated |
| A cold | Samsung Browser | 2 | 10440 | 8 | 0.25–0.50 / 0–1 | expected `488,8,952,1016`* | `1456,0,1920,1008` | `+968,-8,+968,-8` | **FAIL position/order** |
| A cold | Tips | 3 | 10441 | 8 | 0.50–1 / 0–1 | expected `968,8,1912,1016`* | `488,6,1432,1014` | `-480,-2,-480,-2` | **FAIL position/order** |
| D cold | Chrome | 1 | 10432 | 8 | 0–0.5 / 0–1 | `8,8,952,1016` | `0,16,944,1024` | `-8,+8,-8,+8` | FAIL exact; ratio preserved |
| D cold | Google | 2 | 10433 | 8 | 0.5–1 / 0–1 | `968,8,1912,1016` | `976,0,1920,1008` | `+8,-8,+8,-8` | FAIL exact; ratio preserved |
| D warm | Chrome | 1 | 10434 | 8 | 0–0.5 / 0–1 | `8,8,952,1016` | `0,16,944,1024` | `-8,+8,-8,+8` | FAIL exact; stable |
| D warm | Google | 2 | 10435 | 8 | 0.5–1 / 0–1 | `968,8,1912,1016` | `976,0,1920,1008` | `+8,-8,+8,-8` | FAIL exact; stable |

`*` The A/D requested rectangles are reconstructed from the persisted normalized model, current `LaunchBoundsCalculator` policy, 1920×1024 usable region, and 8 px margin. A matching exported COMP-002 JSON was not obtainable, so these are sanity-check expectations and must not be represented as diagnostic-log values.

## J. Task ID analysis

- Cold 25/25/50: Chrome 10439, Samsung Browser 10440, Tips 10441. Preflight contained no target tasks.
- Cold 50/50: Chrome 10432, Google 10433.
- Warm reopen: Chrome 10434, Google 10435.
- Old tasks remained in the task list after warm reopen.
- Test A created three distinct new tasks. `FLAG_ACTIVITY_MULTIPLE_TASK` also created new tasks for both packages in Test D; no reuse was observed in the measured launches.
- The retained old tasks and One UI's visible-window limit materially contaminated the attempted 2×2 run.

## K. Activity/manifest observations

| App | Launch component | Runtime activity | Task affinity observed |
|---|---|---|---|
| Chrome | `com.android.chrome/com.google.android.apps.chrome.Main` | `org.chromium.chrome.browser.ChromeTabbedActivity` | `10251:com.android.chrome` |
| Google | `com.google.android.googlequicksearchbox/.SearchActivity` | `com.google.android.apps.search.googleapp.activity.GoogleAppActivity` | `10242:com.google.android.googlequicksearchbox.googleapp` |
| Tips | `com.samsung.android.app.tips/.TipsMainActivity` | same | `10305:com.samsung.android.app.tips` |
| Calculator | `com.sec.android.app.popupcalculator/.Calculator` | same | `10298:com.sec.android.app.popupcalculator` |
| Samsung News | `com.samsung.android.app.spage/.main.LauncherActivity` | `.news.main.MainActivity` after transition | package-specific affinity shown by task |

The activity dump confirms explicit launcher components and flags `0x18000000`. Exact manifest `launchMode` and `documentLaunchMode` values were not exposed in the captured activity dump and are therefore not asserted here.

## L. Display/work-area analysis

- Raw display coordinate space is 1920×1080 at origin (0,0).
- Workspace task layouts consistently use the desktop region ending near y=1024, leaving the DeX taskbar.
- The current engine's 8 dp margin is 8 physical pixels at the DeX logical density of 160 dpi.
- Equal-thirds discovery tasks were observed at `0,0–624,1008`, `648,6–1272,1014`, and `1296,16–1920,1024`. Their widths remain 624, consistent with the requested equal-column size after margins, while One UI shifts the windows inside/outside the nominal edges.
- Evidence does not show a 1920/1080 coordinate-space scale error. It shows OEM decoration/snap adjustment around correctly sized rectangles.

## M. Sequencing analysis

The engine launched explicit components sequentially in saved order: Chrome, Samsung Browser, Tips. Final stacking order was Tips, Browser, Chrome in `dumpsys`, while spatial order became Chrome, Tips, Browser. This is consistent with a One UI window-placement/reflow decision occurring during the later launches, but per-step bounds were not captured and COMP-002 JSON timing was not exportable. Therefore the exact transition point remains unknown.

## N. S23 comparison

The established S23 baseline at 1920×1200 passed cold and warm three-app launches with exact retained layouts. The S22 differs in DeX height (1080), logical desktop/window decoration behavior, and its five-visible-app handling. Architecture and intent flags remain the same. This run does not establish whether the reported S22 25/25/50 → 25/50/25 symptom is caused by those differences.

## O. Initial root-cause decision (superseded by Section R)

**F — STILL INSUFFICIENT EVIDENCE.** The user-visible bug is reproduced, but the required requested-bounds record is missing, so responsibility cannot yet be assigned conclusively.

What is established:

- The current S22 reproduces the 25/25/50 → 25/50/25 spatial result on a clean cold launch.
- The expected 464/464/944 widths are preserved, but the 944-wide Tips task is centered and Browser is moved right.
- DexWorkspaceTouch creates distinct target tasks.
- One UI 8 adjusts otherwise correctly sized 50/50 bounds by 8 px toward outer edges.
- Equal-third tasks also show OEM position/decor offsets without a column-width permutation.

What is not established:

- Whether the platform received the reconstructed 25/25/50 request; COMP-002 JSON export was blocked by the clipped diagnostics control.
- Whether One UI, a target app constraint, or sequencing changes the middle/right widths.
- Whether the problem is cold-only, warm-only, or target-specific.

Accordingly, neither “DexWorkspaceTouch calculation is wrong” nor “One UI is solely responsible” is proven by this run.

## P. Recommended next investigation/fix options

No production fix is recommended until the missing asymmetric run is captured.

1. **Make the existing diagnostic export reachable without changing launch behavior, then repeat only Test A.** Benefit: provides the decisive requested rectangles for the already-reproduced defect. Risk: none if achieved through device/UI configuration; any code change must be a separately authorized diagnostics-only task.
2. **If requested is correct but actual is permuted, add a post-launch task-bound verification experiment in a separate diagnostic build.** Benefit: distinguishes launch-time acceptance from later OEM reflow. Risk: low for an investigation build; no production policy should change yet.
3. **If requested itself is permuted, isolate serialization/editor cell ordering with a repository test fixture.** Benefit: targets calculation/data ordering. Risk: low; S23 regression risk depends on the eventual fix, not this diagnostic step.

## Q. Remaining uncertainty and evidence

Primary evidence directory (ignored, not committed): `build/comp-003/`.

Key files:

- `baseline-display.txt`
- `external-home.png`
- `home-with-workspace4.png`, `ratio-25-25-50.png`
- `A-preflight-target-tasks.txt` (empty), `A-cold.png`
- `comp003-A-activities.txt`, `comp003-A-windows.txt`, `comp003-A-display.txt`
- `D-50-50-cold.png`, `D-50-50-warm.png`
- `comp003-D-cold-activities.txt`, `comp003-D-warm-activities.txt`
- `comp003-D-cold-windows.txt`, `comp003-D-warm-windows.txt`
- `comp003-D-cold-display.txt`, `comp003-D-warm-display.txt`
- `E-2x2.png`, `comp003-E-activities.txt`, `comp003-E-windows.txt`, `comp003-E-display.txt` (inconclusive run)

## R. COMP-002A decisive cold Test A

COMP-002A made the existing diagnostics controls reachable without changing workspace launch behavior. The About dialog now scrolls as one surface, and both diagnostics actions expose explicit accessibility descriptions. The signed production-equivalent `1.0.0-beta.8` (9) APK was installed in place on the same S22 Ultra; no app data was cleared and no release was published.

The decisive run used external display ID `10`, mode `1920x1080`, and the measured usable work area `Rect(0,0 - 1920,1024)`. Diagnostics were cleared first. Only Chrome, Samsung Browser, and Samsung Tips were force-stopped, the target-task preflight was empty, and Workspace 4 was launched once cold.

Authoritative COMP-002 JSON: ignored device evidence `build/comp-003/A3-diagnostic.json`, session `9f6b6cab-7641-4968-952e-54edff5bbe32`, timestamp `1789386624937`.

| App | Seq | Task | Normalized width | Requested width | Actual width | Requested rect | Actual rect | Delta L/T/R/B | Result |
|---|---:|---:|---:|---:|---:|---|---|---|---|
| Chrome | 1 | 10496 | 0.25 | 464 | 464 | `8,8,472,1016` | `0,16,464,1024` | `-8,+8,-8,+8` | PASS width; translated |
| Samsung Browser | 2 | 10497 | 0.25 | 464 | 464 | `488,8,952,1016` | `1456,0,1920,1008` | `+968,-8,+968,-8` | **FAIL position/order** |
| Samsung Tips | 3 | 10498 | 0.50 | 944 | 944 | `968,8,1912,1016` | `488,6,1432,1014` | `-480,-2,-480,-2` | **FAIL position/order** |

All three diagnostic entries report `SUCCESS`, explicit launcher components, flags `0x18000000`, the same display/work-area data, and sequential launch timestamps. New task IDs `10496`, `10497`, and `10498` confirm a cold launch rather than task reuse.

The requested rectangles are correct and preserve the saved 25/25/50 order. The actual rectangles preserve each app's requested width, but One UI moves the third 50% window into the center and the second 25% window to the right. This is materially larger than the accepted +/-8 px decoration translation.

**Final classification: ROOT CAUSE CONFIRMED - ONE UI WINDOW BEHAVIOR.**

Tests B and C remain not run under the focused-task rule because decisive cold Test A failed. No production bounds, launch sequencing, repository, or workspace behavior was changed by COMP-002A.

The earlier no-JSON limitation and insufficient-evidence decision are superseded by the authoritative COMP-002A capture in Section R.
