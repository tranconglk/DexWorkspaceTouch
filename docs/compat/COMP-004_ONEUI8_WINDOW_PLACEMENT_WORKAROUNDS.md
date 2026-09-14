# COMP-004 — One UI 8 DeX Window Placement Workaround Investigation

Date: 2026-09-14  
Branch: `release/1.0-beta`  
Classification: **D. NO RELIABLE PUBLIC-API WORKAROUND FOUND**

## A. Confirmed baseline

The test device was a Samsung Galaxy S22 Ultra SM-S908E, Android 16 / SDK 36, One UI 8, with wired DeX at 1920x1080 and a 1920x1024 usable work area. External display ID was discovered as `10`; it was not encoded in product code.

COMP-003 established that DexWorkspaceTouch requested Chrome left 25%, Samsung Browser middle 25%, and Samsung Tips right 50% correctly. One UI produced Chrome left, Tips center, and Browser right.

## B. Temporary harness

A temporary, explicit-only `Comp004HarnessActivity` was built into a locally signed release-equivalent APK. It constructed an in-memory `WorkspaceLaunchRequest` and reused:

`AndroidWorkspaceLauncher -> AndroidSingleAppLauncher -> ActivitySingleAppLaunchPlatform -> ActivityOptions.setLaunchBounds()`

The production normalized-bounds calculator, 8 px margin, work-area provider, explicit launcher components, `NEW_TASK | MULTIPLE_TASK`, inherited external-display routing, and normal 400 ms sequencing path were retained. Only target `order`, prefix length, and inter-app delay were variable. No persisted Workspace was mutated.

Prefix runs were used for per-step observation so the normal 400 ms delay did not need to be lengthened merely to obtain dumpsys evidence. The harness was removed from source after the experiments.

## C. Per-launch-step behavior

Current order A (`Chrome -> Browser -> Tips`) produced:

| Prefix | Chrome | Browser | Tips | Observation |
|---|---|---|---|---|
| 1 | `728,6-1192,1014` | — | — | Chrome's requested left-quarter width was preserved, but One UI centered it immediately. |
| 2 | `728,6-1192,1014` | `1456,16-1920,1024` | — | Browser was placed at the right edge; Chrome did not move. |
| 3 | `728,6-1192,1014` | `1456,16-1920,1024` | `0,0-944,1008` | Tips was placed as a left-side half-width window; existing tasks did not move. |

The divergence therefore begins at launch #1. In this harness run the platform substituted spatial placement for every new task rather than accepting the requested absolute x coordinate; the final defect is not a single app-3 reflow event.

## D. Six launch-order permutations

The requested rectangle associated with each app never changed.

| Permutation | Launch order | Chrome actual | Browser actual | Tips actual | Final spatial assignment | Match |
|---|---|---|---|---|---|---|
| A | C -> B -> T | `728,6-1192,1014` | `1456,16-1920,1024` | `0,0-944,1008` | Tips-left, Chrome-center, Browser-right | FAIL |
| B | C -> T -> B | `728,6-1192,1014` | `0,0-464,1008` | `976,16-1920,1024` | Browser-left, Chrome-center, Tips-right | FAIL |
| C | B -> C -> T | `1456,16-1920,1024` | `728,6-1192,1014` | `0,0-944,1008` | Tips-left, Browser-center, Chrome-right | FAIL |
| D | B -> T -> C | `0,0-464,1008` | `728,6-1192,1014` | `976,16-1920,1024` | Chrome-left, Browser-center, Tips-right, but Browser is shifted +240 px and overlaps Tips | FAIL |
| E | T -> C -> B | `0,0-464,1008` | `0,16-464,1024` | `976,16-1920,1024` | Chrome and Browser overlap at left | FAIL |
| F | T -> B -> C | `0,16-464,1024` | `0,0-464,1008` | `976,16-1920,1024` | Chrome and Browser overlap at left | FAIL |

No permutation achieved the requested three non-overlapping rectangles within the accepted approximately 8 px Samsung decoration translation. D preserved semantic left-to-right app order, but its middle quarter was displaced by 240 px and overlapped the right half, so it is not a workaround candidate.

## E. Large-first and right-to-left hypotheses

Large-first permutations E/F failed: both 25% tasks were placed on the same left slot. Right-to-left F (`Tips -> Browser -> Chrome`) failed for the same reason. Launching the 50% task first did not reserve a stable right half plus two distinct remaining quarter slots.

## F. Delay matrix

Permutation B was tested at 400, 650, 900, and 1400 ms between apps (current, +250, +500, +1000). Every run produced the same placement:

- Browser: `0,0-464,1008`
- Chrome: `728,6-1192,1014`
- Tips: `976,16-1920,1024`

Stabilization delay did not affect placement. There is no evidence supporting a production delay increase.

## G. Second app set

The two closest ordering patterns were also sampled with Google Search, Calculator, and Samsung News using the same 25/25/50 normalized geometry. The platform again substituted slot-like placement instead of retaining all requested absolute positions. Samsung News was reported non-resizable by task metadata, so this set is supporting rather than definitive evidence; nevertheless, the behavior is not limited to Samsung Browser and Tips.

## H. Mirror geometries

The requested 50/25/25 and 25/50/25 follow-ups were not run. The task made them conditional on finding a reliable 25/25/50 sequence, and none of the six permutations passed even once.

## I. Repeatability

No successful permutation existed to qualify for the required three cold repetitions. Delay runs reproduced permutation B's failure four times across the tested timing values, showing that its incorrect assignment was stable rather than timing-sensitive.

## J. Candidate workaround

No launch-order or order-plus-delay candidate meets the acceptance criteria. Public `setLaunchBounds()` requests preserve requested widths but One UI 8 applies its own x-placement/slot heuristic. Changing saved order or adding delay would produce wrong or overlapping workspaces and is not suitable for production.

## K. Compatibility risk

- **S23:** high regression risk from applying any order heuristic; the established S23 DeX path already retains asymmetric layouts correctly.
- **Note9:** high and insufficiently characterized risk because its legacy work-area compatibility path is separate; ordering cannot fix a coordinate-space boundary.
- Device-model hard-coding is not recommended. Samsung/SDK/DeX identification alone does not prove the placement behavior. Any future strategy should be gated by observed capability or post-launch verification, neither of which is implemented here.

## L. Recommendation

Keep current production behavior unchanged. Do not reorder targets and do not increase launch delay. A future investigation may evaluate post-launch task-bound verification/correction, but that requires public-API feasibility and lifecycle/security analysis as a separate milestone. No workaround was implemented by COMP-004.

Ignored device evidence is stored under `build/comp-004/`. Screenshot capture by logical runtime display ID was rejected by Samsung's `screencap` command (which expects a physical capture identifier); dumpsys activity evidence remains authoritative for task IDs, display association, windowing mode, and actual bounds.
