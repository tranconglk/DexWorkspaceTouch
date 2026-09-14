# COMP-007 — DeX Session Boundary & Reconnect Validation

## Baseline and method

COMP-006 commit `d369c70e8a9a9f0b5fbe128403a92c144f03a89c` concluded `A — SELF-PROBE IS PREDICTIVELY RELIABLE` for six geometries inside one S22 DeX session. COMP-007 tests repeatability and physical session boundaries; it adds no production detector, cache, or launcher behavior.

Device: Samsung S22 Ultra `SM-S908E`, Android 16 / One UI 8, wired DeX. The external display was discovered dynamically for every attachment. Three temporary owned Activities and a coordinator reused the production `AndroidWorkspaceLauncher` → `AndroidSingleAppLauncher` → `ActivitySingleAppLaunchPlatform` chain, including work-area calculation, margins, bounds conversion, component checks, flags, sequencing, and `ActivityOptions.setLaunchBounds()`. Actual bounds came from `dumpsys`; requested bounds and display metadata came from COMP-002 diagnostics. The harness was removed after testing.

## Same-session repeatability

Initial COMP-007 session: display ID `11`, `uniqueId=local:4`, `1920x1080 @ 60 Hz`, usable work area `[0,0][1920,1024]`. Each case was repeated three times.

| Case | Requested | Owned actual, runs 1–3 | Third-party actual, runs 1–3 | Agreement |
|---|---|---|---|---|
| Quarter left | `[8,8][472,1016]` | Probe A `[728,6][1192,1014]` every run | Chrome `[728,6][1192,1014]` every run | 3/3 |
| Half non-anchor | `[248,8][1192,1016]` | Probe A `[976,16][1920,1024]` every run | Chrome `[976,16][1920,1024]` every run | 3/3 |
| 25/25/50 | A `[8,8][472,1016]`; B `[488,8][952,1016]`; C `[968,8][1912,1016]` | A center quarter; B right quarter; C left half every run | Chrome center quarter; Browser right quarter; Tips left half every run | 3/3 |

The same-session result was pixel-stable and matched COMP-006.

## Physical reconnect #1

- Display ID `12`; name `Màn hình HDMI`; `uniqueId=local:4`
- Mode `1920x1080 @ 60.000004 Hz`; work area `[0,0][1920,1024]`
- Activity host display `12`; orientation `0`

| Case | Owned actual | Third-party actual | Prediction |
|---|---|---|---|
| Quarter left | `[0,16][464,1024]` | Chrome `[0,16][464,1024]` | Correct: `ABSOLUTE_ACCEPTED` with 8 px translation |
| Half non-anchor | `[0,16][944,1024]` | Chrome `[0,16][944,1024]` | Correct: `POSITION_SUBSTITUTED` |
| 25/25/50 | left quarter / right quarter / center half; no owned overlap | Chrome left quarter / Browser right quarter / Tips left half; Chrome–Tips overlap | Incorrect: missed `OVERLAP_OR_CASCADE` |

Accuracy: `2/3` (66.7%). Placement changed despite the same unique ID, resolution, work area, orientation, and physical display.

For the post-reconnect observer test, quarter-left Chrome was `[0,16][464,1024]` both before a probe and after probe plus cleanup. No observer effect was detected.

## Physical reconnect #2

- Display ID `13`; name `Màn hình HDMI`; `uniqueId=local:4`
- Mode `1920x1080 @ 60.000004 Hz`; work area `[0,0][1920,1024]`
- Activity host display `13`

Reconnect #2 reproduced reconnect #1 exactly: quarter-left and half non-anchor matched, while owned 25/25/50 had no overlap and third-party 25/25/50 overlapped. Accuracy was again `2/3`. The post-reconnect mismatch was therefore not a one-off.

## Resolution / mode change

**NOT AVAILABLE.** The display advertises alternate supported modes, but no exported/public DeX resolution-setting Activity was found. The DeX settings provider requires privileged `WRITE_SECURE_SETTINGS`. No hidden API, forced mode, or service restart was used, so no resolution-change claim is made.

## Display ID and session signature

Runtime display ID changed with every observed attachment: COMP-006 `10`, initial COMP-007 `11`, reconnect #1 `12`, reconnect #2 `13`. `uniqueId=local:4`, name, resolution, refresh, work area, and orientation stayed constant.

Changing display ID is strong invalidation evidence on this device, but an unchanged ID would not prove continuity. A future memory-only attachment/config signature should combine runtime display ID, `Display.uniqueId`, mode width/height/refresh, density, usable work-area rectangle, orientation, and device/SDK qualifiers. No public field exposes Samsung's hidden collision/placement state.

## Public session-boundary mechanisms

Potential public invalidation signals in the current architecture are:

- `DisplayManager.DisplayListener` display added/removed/changed callbacks;
- Activity `onMovedToDisplay(displayId, configuration)`;
- Activity/Compose configuration changes for orientation, density, and window configuration;
- fresh `WindowMetrics` and runtime display discovery after those events.

No permanent listener was added.

## Cache evaluation

| Strategy | Assessment |
|---|---|
| No cache | Safest policy, though the current probe still misclassifies the tested post-reconnect multi-window case. |
| Session-local memory cache | Preferable to persistence and must invalidate on display/config events, but not ready as a general capability result at 66.7% post-reconnect accuracy. |
| Persistent cache | Unsafe. Reconnect changed placement while physical-display metadata remained the same. |

## Final verdict

**D. INSUFFICIENT EVIDENCE**

Same-session behavior was stable and physical reconnect clearly changed the placement regime. Persistent caching can be rejected. Verdict A cannot be selected because a freshly rerun owned probe mispredicted 25/25/50 after both reconnects. Verdict B is false because results changed across reconnect; verdict C is false because the measured initial-session behavior was stable.

## Production recommendation

Do not implement a general production self-probe capability profile from the current model. Keep capability `UNVERIFIED` and do not persist results. Further investigation must explain why same-package owned multi-window Activities receive a different post-reconnect sequence from third-party packages. Public display and configuration events should invalidate any future memory-only result, but invalidation alone does not fix the predictive mismatch.
