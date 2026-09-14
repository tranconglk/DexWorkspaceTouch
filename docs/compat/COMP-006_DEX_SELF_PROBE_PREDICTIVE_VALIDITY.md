# COMP-006 — DeX Self-Probe Predictive Validity

## Scope and hypothesis

This investigation tests whether Activities owned by DexWorkspaceTouch predict the behavioral placement class that One UI 8 subsequently applies to third-party Activities in the same DeX session. It does not introduce a production compatibility profile, cache, launcher change, or customer UI.

Baseline: COMP-005 commit `a57e0688b1f07ee1a5656beb489bf83b0d4ae6ea` (`B — ANCHOR MODEL PARTIALLY SUPPORTED`).

## Device and session

- Device: Samsung Galaxy S22 Ultra (`SM-S908E`)
- OS: Android 16 / One UI 8
- Connection: wired DeX; ADB over Wi-Fi
- External display: dynamically discovered display ID `10`, `FLAG_EXTERNAL_DEX_HOSTING`
- Display mode: `1920 x 1080`
- Usable work area from production diagnostics: `[0,0][1920,1024]`
- Density used for placement: `1.0`
- Margin policy: `8 px`

No display ID was hard-coded into the probe launcher. The observed ID above is session evidence only.

## Temporary owned-Activity implementation

The test-only harness used a coordinator Activity and three ordinary Activities in package `com.trancong.dexworkspacetouch`. The coordinator constructed `WorkspaceLaunchRequest` objects and invoked the production `AndroidWorkspaceLauncher` → `AndroidSingleAppLauncher` → `ActivitySingleAppLaunchPlatform` path. The owned and third-party cases therefore used the same work-area source, normalized-bounds conversion, margin policy, `ActivityOptions.setLaunchBounds()`, display host, flags, and launch sequencing.

The owned Activities were separate explicit launcher components so existing production component validation was not bypassed. Actual bounds and task IDs came from `dumpsys activity activities`; COMP-002 JSON supplied requested bounds and launch metadata. All harness Activities and manifest declarations were removed after measurement.

## Cleanup behavior

Before each case, only the three target packages and temporary harness process were force-stopped; app data was not cleared. After every owned-probe launch, all owned probe tasks were force-stopped before the matched third-party launch.

An immediate `dumpsys` can retain records marked `isExiting`; after the transition settled, no active `Comp006Probe` task or Activity remained. The following third-party launch created new target tasks on display 10. Cleanup was reliable for this matrix and left no active probe window.

## Matched geometry results

Behavior classes describe One UI's response, not exact-pixel equality. A requested rectangle is substituted when its position differs materially; multi-window overlap/cascade takes precedence when requested disjoint regions become overlapping.

| Case | Requested pixel bounds | Owned actual bounds | Owned class | Third-party actual bounds | Third-party class | Prediction |
|---|---|---|---|---|---|---|
| Quarter left | `[8,8][472,1016]` | Probe A `[728,6][1192,1014]` | `POSITION_SUBSTITUTED` | Chrome `[728,6][1192,1014]` | `POSITION_SUBSTITUTED` | Correct |
| Quarter non-anchor | `[488,8][952,1016]` | Probe A `[728,6][1192,1014]` | `POSITION_SUBSTITUTED` | Chrome `[728,6][1192,1014]` | `POSITION_SUBSTITUTED` | Correct |
| Half center | `[488,8][1432,1016]` | Probe A `[976,16][1920,1024]` | `POSITION_SUBSTITUTED` | Chrome `[976,16][1920,1024]` | `POSITION_SUBSTITUTED` | Correct |
| Half non-anchor | `[248,8][1192,1016]` | Probe A `[976,16][1920,1024]` | `POSITION_SUBSTITUTED` | Chrome `[976,16][1920,1024]` | `POSITION_SUBSTITUTED` | Correct |
| 25/25/50 | A `[8,8][472,1016]`; B `[488,8][952,1016]`; C `[968,8][1912,1016]` | A `[728,6][1192,1014]`; B `[1456,16][1920,1024]`; C `[0,0][944,1008]` | `OVERLAP_OR_CASCADE` | Chrome `[728,6][1192,1014]`; Browser `[1456,16][1920,1024]`; Tips `[0,0][944,1008]` | `OVERLAP_OR_CASCADE` | Correct |
| 50/50 | A `[8,8][952,1016]`; B `[968,8][1912,1016]` | A `[976,16][1920,1024]`; B `[0,0][944,1008]` | `POSITION_SUBSTITUTED` | Chrome `[976,16][1920,1024]`; Browser `[0,0][944,1008]` | `POSITION_SUBSTITUTED` | Correct |

The 25/25/50 class reflects overlap between the center quarter (`x=728..1192`) and left half (`x=0..944`), although requested cells were disjoint. In 50/50, sizes were retained but launch-order positions were swapped.

## Observer-effect comparison

Each third-party geometry was measured once without a preceding self-probe and again after owned probe plus cleanup.

| Case | Baseline third-party actual | After-probe third-party actual | Material change |
|---|---|---|---|
| Quarter left | Chrome `[728,6][1192,1014]` | Chrome `[728,6][1192,1014]` | No |
| Quarter non-anchor | Chrome `[728,6][1192,1014]` | Chrome `[728,6][1192,1014]` | No |
| Half center | Chrome `[976,16][1920,1024]` | Chrome `[976,16][1920,1024]` | No |
| Half non-anchor | Chrome `[976,16][1920,1024]` | Chrome `[976,16][1920,1024]` | No |
| 25/25/50 | Chrome center quarter; Browser right quarter; Tips left half | Identical three rectangles | No |
| 50/50 | Chrome right half; Browser left half | Identical two rectangles | No |

No observer effect was detected: all baseline and after-probe rectangles were pixel-identical in the captured matrix.

## Predictive accuracy and false positives

- Correct behavioral-class predictions: `6 / 6`
- Accuracy: `100%`
- False `ABSOLUTE_ACCEPTED` predictions: `0`
- Wrong-display launches: `0`
- Active probe tasks left after settled cleanup: `0`

The acceptance threshold of at least 90% with zero false absolute predictions was met for the tested same-session matrix.

## Reconnect/session variation

A physical DeX reconnect or resolution recreation was not performed. Forcing a Samsung desktop-mode service restart from ADB would not be equivalent evidence and would unnecessarily disrupt the attached session. Consequently, this result does not validate a cached capability across DeX reconnects, resolution changes, or device reboots.

Any future production implementation must either probe the current session or separately validate cache invalidation. This investigation supplies no evidence for a persistent cross-session profile.

## Final verdict

**A. SELF-PROBE IS PREDICTIVELY RELIABLE**

This verdict is specifically scoped to the tested representative geometries and matched third-party applications in the same S22 One UI 8 DeX session. The owned probe predicted all third-party behavioral classes, produced no false absolute result, caused no observed placement change, and cleaned up without an active leftover task.

## Production recommendation

A session-local self-probe is technically viable as an input to a future compatibility decision. It should not be treated as proof that requested bounds will be honored: in this run it primarily predicted substitution/cascade behavior. Do not persist or reuse the result across sessions until reconnect/resolution variation is measured. Do not ship the temporary Activities used here; a production design remains a separate milestone.
