# COMP-009 — AOSP Desktop Cascade State Reconstruction

## A. Baseline

- Baseline commit: `2998ec202d6455ddd284f5b9e330eea5bbcbe295` (`COMP-008 document unstable One UI DeX placement state`).
- Branch: `release/1.0-beta`.
- Device: Samsung Galaxy S22 Ultra (`SM-S908E`), Android 16, One UI 8, wired Samsung DeX.
- Runtime external display: ID `13` (discovered for this attachment, not assumed), unique ID `local:4`, mode `1920x1080 @ 60 Hz`, orientation `0`, launch density `1.0`.
- Stable usable desktop frame: `[0,0-1920,1024]`.
- The investigation reused the production single-app launch path. The temporary coordinator and receiver were removed after measurement; no production launcher behavior was changed.
- Raw device evidence is intentionally untracked under `build/comp-009/`.

## B. AOSP reference mechanism

The investigation model follows current AOSP desktop-windowing behavior:

1. Classify the previous bounds as `TOP_LEFT`, `TOP_RIGHT`, `BOTTOM_LEFT`, `BOTTOM_RIGHT`, or `CENTER`. A full-height window is not classified as a top or bottom corner merely because both opposing edges touch the frame.
2. Build a `CENTER` candidate for the destination size:
   - `x = (frame.width - window.width) / 2`
   - `y = frame.top + (frame.height - window.height) * 0.375`
3. If the previous position is `CENTER` and the center candidate has moved beyond the required separation threshold, retain `CENTER`.
4. Otherwise choose `previousPosition.next()` in this cycle:
   `CENTER -> BOTTOM_RIGHT -> TOP_LEFT -> BOTTOM_LEFT -> TOP_RIGHT -> CENTER`.
5. The AOSP threshold comparison is directional: it checks whether one corresponding edge moved into visible empty space by more than the threshold; it is not Euclidean center distance.

References used:

- `DesktopTaskPosition.kt`, revision `bafc1f5a4e19842582a55ec1aae02e00d0b12f64`: <https://android.googlesource.com/platform/frameworks/base/+/bafc1f5a4e19842582a55ec1aae02e00d0b12f64/libs/WindowManager/Shell/src/com/android/wm/shell/desktopmode/DesktopTaskPosition.kt>
- `DesktopRepository.kt`, revision `a4dc88d7199beb4416663c581e070c08ff20ace1`: <https://android.googlesource.com/platform/frameworks/base/+/a4dc88d7199beb4416663c581e070c08ff20ace1/libs/WindowManager/Shell/src/com/android/wm/shell/desktopmode/DesktopRepository.kt>

The first file is the basis for `cascadeWindow`, `prevBoundsMovedAboveThreshold`, classification, and the position cycle. The second is the basis for the ordered freeform-task state and `addOrMoveFreeformTaskToTop` behavior. These sources establish the reference model; they do not prove Samsung uses an unchanged copy.

## C. Samsung dump visibility

Samsung exposes enough state for causal testing through the read-only SystemUI auxiliary dump. Its `DesktopTasksController` / `DesktopRepository` sections contain:

- `activeTasks`
- `visibleTasks`
- `freeformTasksInZOrder`
- `minimizedTasks`

The ordinary `SystemUIService` and `--list` outputs were less useful. Normal user-build logcat contained many `DesktopTasksController`/bounds records, but no explicit `DesktopTaskPosition`, `cascadeWindow`, repository-order, or useful ProtoLog decision message. No privileged ProtoLog mode was enabled.

## D. Desktop task inventory

The baseline repository for display 13 contained approximately 20 retained freeform tasks. All were minimized and `visibleTasks` was empty, while the ordered repository still began:

`[10804, 10795, 10794, 10789, 10788, ... , 10764]`

Activity, window, recents, and SystemUI dumps were cross-checked for component, display, freeform mode, visibility, focus, bounds, minimization, and ordering. An important result is that force-stopping or closing the visible test targets did **not** empty `freeformTasksInZOrder`; old COMP-007/008 tasks remained in the desktop repository. Therefore “clean target packages” was not equivalent to a clean desktop cascade state.

## E. DesktopTaskPosition pixel signatures

For frame `[0,0-1920,1024]`:

| Destination size | CENTER | BOTTOM_RIGHT | TOP_LEFT | BOTTOM_LEFT | TOP_RIGHT |
|---|---|---|---|---|---|
| 464x1008 (quarter) | `[728,6-1192,1014]` | `[1456,16-1920,1024]` | `[0,0-464,1008]` | `[0,16-464,1024]` | `[1456,0-1920,1008]` |
| 624x1008 (third) | `[648,6-1272,1014]` | `[1296,16-1920,1024]` | `[0,0-624,1008]` | `[0,16-624,1024]` | `[1296,0-1920,1008]` |
| 944x1008 (half) | `[488,6-1432,1014]` | `[976,16-1920,1024]` | `[0,0-944,1008]` | `[0,16-944,1024]` | `[976,0-1920,1008]` |

The recurring historical coordinates `728,6`, `1456,16`, `976,16`, `488,6`, `0,0`, and `0,16` are exact position signatures. They are better explained by selection of a different `DesktopTaskPosition` than by a generic ±8 px decoration translation.

## F. Threshold analysis

The installed Samsung SystemUI resource was inspected read-only. `SystemUI.apk` defines:

`dimen/freeform_required_visible_empty_space_in_header = 48dp`

At this external display's launch density `1.0`, the effective candidate threshold is 48 px. This is Samsung-device evidence, not an assumption imported from upstream AOSP.

## G. Individual launch transitions

The temporary coordinator was a visible freeform DWT-owned Activity, task `10808`, bounds `[441,161-1479,863]`. It was top of `freeformTasksInZOrder` immediately before each of the ten foreground launches and classifies as `CENTER`. Each target was launched exactly once per transition through the production launcher path; no target was manually moved or resized.

Requested bounds below are production-normalized bounds converted with the usual 8 px workspace margin: quarter-left `[8,8-472,1016]`, quarter-middle `[488,8-952,1016]`, quarter-right `[1448,8-1912,1016]`, half-left `[8,8-952,1016]`, half-center `[488,8-1432,1016]`, and half-right `[968,8-1912,1016]`.

## H. Prediction matrix

| # | Target / request | New task | Previous task / position | CENTER threshold result | Predicted position / rect | Actual rect | Error | Result |
|---:|---|---:|---|---|---|---|---:|---|
| 1 | Chrome quarter-left | 10809 | 10808 / CENTER | separated | CENTER `[728,6-1192,1014]` | `[728,6-1192,1014]` | 0 px | strong |
| 2 | Browser quarter-middle | 10810 | 10808 / CENTER | separated | CENTER `[728,6-1192,1014]` | `[728,6-1192,1014]` | 0 px | strong |
| 3 | Tips half-right | 10811 | 10808 / CENTER | not separated | BOTTOM_RIGHT `[976,16-1920,1024]` | `[976,16-1920,1024]` | 0 px | strong |
| 4 | Chrome quarter-left | 10812 | 10808 / CENTER | separated | CENTER `[728,6-1192,1014]` | `[728,6-1192,1014]` | 0 px | strong |
| 5 | Browser quarter-middle | 10813 | 10808 / CENTER | separated | CENTER `[728,6-1192,1014]` | `[728,6-1192,1014]` | 0 px | strong |
| 6 | Tips half-right | 10814 | 10808 / CENTER | not separated | BOTTOM_RIGHT `[976,16-1920,1024]` | `[976,16-1920,1024]` | 0 px | strong |
| 7 | Chrome half-center | 10815 | 10808 / CENTER | not separated | BOTTOM_RIGHT `[976,16-1920,1024]` | `[976,16-1920,1024]` | 0 px | strong |
| 8 | Browser quarter-right | 10816 | 10808 / CENTER | separated | CENTER `[728,6-1192,1014]` | `[728,6-1192,1014]` | 0 px | strong |
| 9 | Tips half-left | 10817 | 10808 / CENTER | not separated | BOTTOM_RIGHT `[976,16-1920,1024]` | `[976,16-1920,1024]` | 0 px | strong |
| 10 | Chrome quarter-left | 10818 | 10808 / CENTER | separated | CENTER `[728,6-1192,1014]` | `[728,6-1192,1014]` | 0 px | strong |

For a quarter center candidate versus the coordinator, the horizontal edge displacement is 287 px, greater than 48; `CENTER` remains selected. For a half candidate, corresponding horizontal edges differ by only 47 px and the directional vertical tests do not exceed 48; `CENTER.next()` produces `BOTTOM_RIGHT`.

Measured accuracy: **10/10 exact position-class matches (100%), 10/10 strong coordinate matches, 0 compatible-only matches, 0 failures**.

## I. COMP-008 run-2 reconstruction

Observed:

1. A retained requested quarter bounds `[8,8-472,1016]`.
2. B moved to quarter CENTER `[728,6-1192,1014]`.
3. C moved to half BOTTOM_RIGHT `[976,16-1920,1024]`.

A's inset bounds touch no frame corner and therefore classify as `CENTER`. B's quarter-center candidate is separated from A by far more than 48 px, so the AOSP rule retains `CENTER`; the prediction is exact. C's half-center candidate overlaps B in the threshold sense (the relevant edge movements do not exceed 48), so `CENTER.next()` is `BOTTOM_RIGHT`; again the prediction is exact. The historical capture did not include a pre-A repository dump, so the reason the first requested A was retained is bounded to its missing initial seed/no-cascade path rather than falsely assigned to a package property.

## J. COMP-008 runs-1/3 reconstruction

The sequence quarter CENTER -> quarter BOTTOM_RIGHT -> half TOP_LEFT is the exact AOSP cycle:

- A starts at CENTER.
- The equal-size CENTER candidate for B overlaps A, so B becomes `CENTER.next() = BOTTOM_RIGHT`.
- C sees a previous `BOTTOM_RIGHT`; because the previous position is not CENTER, the rule selects `BOTTOM_RIGHT.next() = TOP_LEFT`.

The sequence and pixels are fully explained. The old evidence did not preserve the pre-A task ordering, so its first seed cannot be named retrospectively; current controlled runs demonstrate that a visible CENTER DWT host is one sufficient seed for an initial CENTER result.

## K. Seed-task analysis

In the controlled foreground experiment the seed was unambiguous: DWT coordinator task `10808`, not Chrome, Browser, Tips, desktop home, or a package-specific state. It remained the first repository entry immediately before every launch. The destination's requested horizontal location did not determine the result; its size and relationship to the seed did.

Minimized historical tasks remain recorded, but the visible/top freeform task was the predictive previous task in every controlled observation. This explains why force-stop-only runs appeared unstable: they changed target process/task state without necessarily controlling the actual desktop predecessor.

## L. DWT host effect

Two additional background launches removed the visible DWT Activity as intermediary while retaining a valid explicit external-display launch context:

| Case | Top previous desktop task | Predicted | Actual | Result |
|---|---|---|---|---|
| Background 1 | Browser task 10816, quarter CENTER `[728,6-1192,1014]` | Chrome quarter BOTTOM_RIGHT `[1456,16-1920,1024]` | task 10819 at `[1456,16-1920,1024]` | exact |
| Background 2 | Chrome task 10819, quarter BOTTOM_RIGHT `[1456,16-1920,1024]` | Browser quarter TOP_LEFT `[0,0-464,1008]` | task 10820 at `[0,0-464,1008]` | exact |

This normal focus/Z-order change produced the predicted `CENTER -> BOTTOM_RIGHT -> TOP_LEFT` progression. It falsifies the idea that the DWT coordinator is always a hidden prerequisite; instead, a visible DWT host simply becomes the current cascade seed when it is the top desktop freeform task.

Across foreground and background checks the model produced 12/12 exact transitions.

## M. Empty-desktop control

**Not available as a valid control.** Closing/force-stopping only the test applications left a large retained `activeTasks`/`minimizedTasks` repository and therefore did not establish an empty desktop. Clearing arbitrary user tasks, data, or hidden repository state would violate the investigation constraints. No observation is labelled “empty desktop.”

## N. AOSP vs Samsung deviations

| Observation | Classification |
|---|---|
| Position cycle and exact pixel coordinates in 12 controlled transitions | `EXACT_AOSP_MATCH` |
| Center-candidate threshold decision | `EXACT_AOSP_MATCH` |
| Samsung SystemUI resource name/value, 48dp | `EXACT_AOSP_MATCH` for this build |
| Repository exposes active/visible/minimized and ordered freeform tasks | `AOSP_COMPATIBLE` |
| Old minimized tasks persist after target force-stop/visible cleanup | `SAMSUNG_VARIATION` in operational behavior/visibility |
| COMP-008 run-2 first A retaining inset requested bounds | `UNEXPLAINED` initial condition because the required pre-A dump does not exist; later transitions are exact |

There was no systematic failure in the controlled matrix. The unresolved historical first target is missing-state uncertainty, not a measured contradiction.

## O. Final mechanism verdict

**B — AOSP CASCADE MODEL CONFIRMED WITH SAMSUNG VARIATIONS.**

The core mechanism is now predictable when the immediately preceding/top desktop freeform task and bounds are known: 12/12 controlled predictions matched both position class and coordinates exactly. COMP-008 run 2 after its first target and the full run-1/3 sequence follow the same model. Verdict B rather than A is used because Samsung's proprietary fork retains extensive minimized repository state and the historical pre-seed for the one request-retained first launch cannot be recovered.

## P. Production implications

The result establishes a causal model, not a production workaround. A future public-API approach might theoretically make launches more deterministic only by controlling a DexWorkspaceTouch-owned visible Activity/task and its normal lifecycle/focus state. That avenue would still need separate validation across devices and sessions.

There is no safe general solution based on manipulating unrelated user tasks, clearing desktop state, injecting a fake task, changing ordering, or adding delays. The background experiment also shows why an application-context launch can inherit whichever third-party task is currently top. Understanding the mechanism does not make the repository seed publicly writable or reliably controllable.

No workaround, launch-order change, bounds change, delay, hidden API, Accessibility, Shizuku, root operation, or device-specific production branch was added in COMP-009.

## Q. Remaining unknowns

- Which exact Samsung branch/revision implements the observed AOSP-compatible logic is proprietary.
- The initial seed/state for the first A in historical COMP-008 run 2 was not captured.
- A genuinely empty Samsung desktop could not be established safely, so first-launch behavior with an empty repository remains unknown.
- The ordering policy between retained minimized tasks and a future launch when no desktop Activity is visible needs a separately controlled session/reconnect study if production design ever depends on it.
- Cross-device and cross-One-UI equivalence is not established by this S22 Ultra result.
