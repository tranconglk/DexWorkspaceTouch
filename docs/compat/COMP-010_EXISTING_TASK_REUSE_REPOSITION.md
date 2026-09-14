# COMP-010 — Existing Task Reuse & Reposition Feasibility

## A. Baseline

- Baseline commit: `210294d5923e04a2a791c298f4a6428d8edba528` (`COMP-009 document AOSP desktop cascade reconstruction`).
- Branch: `release/1.0-beta`.
- Device: Samsung Galaxy S22 Ultra (`SM-S908E`), Android 16, One UI 8, wired Samsung DeX.
- Runtime external display: ID `13`, unique ID `local:4`, `1920x1080 @ 60 Hz`; usable work area `[0,0-1920,1024]`.
- Investigation only. A release-signed temporary Activity selected production or reuse flags while keeping the existing launcher, explicit component, launcher intent/category, bounds calculator, work-area path, display routing, and sequencing. The harness and injectable flag seam were removed afterward.

## B. Android flag semantics

Production uses:

`FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK`

The isolated reuse candidate used:

`FLAG_ACTIVITY_NEW_TASK`

`NEW_TASK` permits Android to select an existing task associated with the target Activity/affinity. `MULTIPLE_TASK` requests a separate task even when a suitable one exists. Neither flag promises that `ActivityOptions.setLaunchBounds()` will resize an already existing task, nor that reuse is constrained to the requested display.

No `CLEAR_TASK`, `CLEAR_TOP`, `REORDER_TO_FRONT`, `NEW_DOCUMENT`, or `RESET_TASK_IF_NEEDED` flag was tested.

## C. Production MULTIPLE_TASK control

Chrome was first launched after force-stop with a left-half request. COMP-009 cascade placement produced:

- task `10822`
- ActivityRecord `27926863`
- actual bounds `[976,16-1920,1024]` (half BOTTOM_RIGHT)

Without closing task 10822, a second production launch requested right-quarter and produced:

- new task `10823`
- new ActivityRecord `156339965`
- actual bounds `[728,6-1192,1014]` (quarter CENTER)

Both task 10822 and 10823 remained visible. Task count increased from one to two, and the new task was inserted at the top of `freeformTasksInZOrder`. This confirms current production behavior creates another window and enters the new-task cascade path.

## D. NEW_TASK reuse result

After resetting to one Chrome task, production created task `10824`, ActivityRecord `214878634`, at `[976,16-1920,1024]`. A `NEW_TASK`-only relaunch requesting right-quarter returned:

`Warning: Activity not started, its current task has been brought to the front`

After relaunch:

- task remained `10824`
- ActivityRecord remained `214878634`
- Chrome task count remained one
- bounds remained `[976,16-1920,1024]`
- repository entry was moved/retained at the top rather than a new entry being inserted

Task-reuse classification: **R1 — TRUE_REUSE** on the same display.

Bounds classification: **B3 — BRING_TO_FRONT_ONLY**.

## E. State retention

State retention was measured separately from task reuse. Across all same-display Chrome transitions, both task ID `10824` and ActivityRecord `214878634` remained unchanged; Android reported bring-to-front rather than Activity start. Browser similarly retained task `10825` / ActivityRecord `4988192`. Calculator retained task `10826` / ActivityRecord `176326657`.

This is strong lifecycle evidence that the existing root Activity instance and in-app state were retained. No browsing URL or sensitive user content was captured or recorded. State retention was repeatable 3/3 for each tested package.

## F. Bounds-transition matrix

The existing Chrome task began at actual `[976,16-1920,1024]` (944x1008). Every reuse request kept that exact rect:

| Transition/request | Requested rect | Before | After | Task | Resize | Reposition |
|---|---|---|---|---:|---|---|
| left-half -> right-half | `[968,8-1912,1016]` | `[976,16-1920,1024]` | `[976,16-1920,1024]` | 10824 | no | no |
| right-half -> left-quarter | `[8,8-472,1016]` | same | same | 10824 | no | no |
| left-quarter -> center-quarter | `[728,8-1192,1016]` | same | same | 10824 | no | no |
| center-quarter -> right-quarter | `[1448,8-1912,1016]` | same | same | 10824 | no | no |
| right-quarter -> center-half | `[488,8-1432,1016]` | same | same | 10824 | no | no |

The requested transition names describe the intended sequence; because the first reuse request was ignored, the actual “before” rect necessarily stayed at the original half BOTTOM_RIGHT for later rows.

Conclusion: `setLaunchBounds()` had no observable resize or reposition effect on an existing reused Chrome task.

## G. DesktopRepository and cascade behavior

For a same-display reuse:

- no task ID was inserted;
- the existing ID moved or remained at the top of `freeformTasksInZOrder`;
- the existing bounds were unchanged;
- no new-task DesktopTaskPosition coordinate was applied.

Therefore reuse bypasses the new-task cascade placement **for the reused task**, but only by preserving its old geometry. The reused task becomes the current top desktop task and can seed COMP-009 cascade behavior for the next newly created app.

Identical right-quarter geometry illustrates the distinction:

- production `MULTIPLE_TASK`: created task 10823 and cascade selected quarter CENTER;
- reuse `NEW_TASK`: selected task 10824 and retained old half BOTTOM_RIGHT bounds.

## H. Two-Workspace transition

Conceptual Workspace A used production creation:

- Chrome requested left-half -> task `10830`, actual half BOTTOM_RIGHT `[976,16-1920,1024]`
- Browser requested right-half -> task `10831`, actual half TOP_LEFT `[0,0-944,1008]`

Workspace B used same-display reuse for Chrome/Browser and production creation for Calculator:

| Package | Before A | After A | After B | Result |
|---|---:|---:|---:|---|
| Chrome | 0 tasks | 1: `10830` | 1: `10830` | reused, old bounds retained |
| Browser | 0 tasks | 1: `10831` | 1: `10831` | reused, old bounds retained |
| Calculator | 0 tasks | 0 | 1: `10832` | newly created |

The ideal no-duplicate count `Chrome 1 -> 1`, `Browser 1 -> 1`, `Calculator 0 -> 1` was achieved. The ideal Workspace B geometry was not achieved because existing browser tasks ignored new bounds.

## I. Reuse-first vs create-first

### Strategy 1: reuse existing apps, then create missing app

- Chrome `10830` and Browser `10831` retained both IDs and old bounds.
- Reusing Browser last put it at the top of the repository.
- New Calculator task `10832` was created at `[0,16-464,1024]`.
- Final repository prefix: `[10832,10831,10830,10829,...]`.

The new app's placement was affected by the last reused task, so reuse-first makes the newly created app depend on the chosen reuse order.

### Strategy 2: create missing app, then reuse existing apps

- Workspace A created Chrome `10833` at half BOTTOM_RIGHT and Browser `10834` at half TOP_LEFT.
- Calculator was created first as task `10835` and retained requested middle-quarter `[488,8-952,1016]`.
- Chrome and Browser were then reused without changing bounds.
- Final repository prefix: `[10834,10835,10833,10829,...]`.

Create-first made the new-app launch depend on the stable DWT host rather than whichever existing target was reused last, and was more predictable in this run. It still did not solve repositioning of existing tasks, and Calculator's request-retention differs from the browser-app cascade behavior. This is evidence for investigation, not a production ordering recommendation.

## J. App-specific matrix

| App | Launcher component | Runtime launch mode | Reuse 3/3 | Activity retained | New bounds applied | Classification |
|---|---|---:|---|---|---|---|
| Chrome | `com.android.chrome/com.google.android.apps.chrome.Main` | `4` (`singleInstancePerTask` on current Android) | yes | yes | no | R1 / B3 |
| Samsung Internet | `com.sec.android.app.sbrowser/.SBrowserMainActivity` | `2` (`singleTask`) | yes | yes | no | R1 / B3 |
| Samsung Calculator | `com.sec.android.app.popupcalculator/.Calculator` | `0` (`standard`) | yes | yes | no | R1 / B3 |

All three reused by task affinity/component routing with `NEW_TASK` and preserved Activity instances. This makes same-display task reuse broadly promising rather than browser-specific. New-task placement itself was app-sensitive: Calculator retained requested production bounds where Chrome/Browser commonly entered cascade. `documentLaunchMode` was not exposed decisively in the captured public dumps and is not inferred.

## K. Display-affinity behavior

A controlled negative test created Chrome task `10836` on phone display 0:

- before: task `10836`, ActivityRecord `35863201`, fullscreen `[0,0-1080,2316]`, visible on display 0;
- external DWT host then issued `NEW_TASK`-only with explicit external launch options;
- after: the same phone task `10836` remained on display 0, fullscreen, and became hidden;
- no Chrome task was created or moved to display 13.

Thus plain `NEW_TASK` reuse is not display-safe. It may select an existing wrong-display task despite the external `setLaunchDisplayId`, then fail to produce a usable DeX window. This violates the strong-candidate requirement and cannot be repaired merely by choosing a package-level reuse key.

## L. Reuse identity analysis

Recommended future logical identity is **package name + resolved launcher component**, additionally scoped by active display/task observation where a public and reliable observation mechanism exists.

- Package-only is too broad for packages with multiple launcher Activities or legitimate document/multi-window tasks.
- Package + launcher component matches the explicit launch contract and distinguishes alternate entry points.
- Actual task/root/base Activity identity is necessary for final selection and display safety, but ordinary third-party apps cannot enumerate/control arbitrary tasks through a stable public API. It should not be represented as a persistent database ID.

Any future implementation must explicitly handle multiple matching tasks and wrong-display tasks rather than assume `NEW_TASK` will select the intended one.

## M. S22 compatibility impact

Same-display reuse has two real advantages:

1. it avoids duplicate windows and preserves user state;
2. it reduces the number of **new-task** cascade decisions when switching between Workspaces.

It does not make saved Workspace geometry take effect: reused tasks remain at their prior position and size. It can also change the seed for subsequently created apps. Most importantly, a task already on the phone makes the current approach fail external-display routing. Consequently reuse is a UX and partial DeX-compatibility opportunity, but not a useful standalone replacement for current creation semantics.

## N. S23 and Note9 scope

No S23 Ultra was connected; only the S22 device was present, so the optional S23 subset was not run.

No Note9 device test was required or run. Android 10 supports the basic `NEW_TASK` task-selection behavior, but the S22 findings must not be projected onto legacy DeX. The Note9 work-area path remains independent, and bounds-on-reuse plus cross-display behavior require separate validation before any policy could include Android 10.

## O. Production architecture implications

No production policy was implemented. If revisited, the model should make policy explicit, for example `REUSE_EXISTING` versus `NEW_WINDOW`, but the measured evidence does **not** support `REUSE_EXISTING` as the unconditional default.

A future default should remain `NEW_WINDOW`/current behavior until all of the following exist:

- public, reliable same-display task discovery or an app-owned task registry with verified display affinity;
- a way to detect wrong-display selection before suppressing creation;
- explicit semantics for multiple/document tasks;
- a separate, public reposition mechanism, or an honest policy that reuse preserves old geometry;
- tested fallback to create-new without destructive lifecycle behavior.

Failure detection is only partially possible today: a new task ID can be observed in investigation dumps, but production cannot rely on `dumpsys` or privileged repository access. `startActivity()` returning successfully does not report whether bounds were ignored or the wrong-display task was selected.

## P. Final verdict

**B — REUSE RELIABLE; REPOSITION PLATFORM-LIMITED.**

On the S22, three materially different apps reused the same task and Activity instance 3/3 without duplication. However, `setLaunchBounds()` acted as bring-to-front only for existing tasks, and a phone-display task was reused instead of moved/duplicated onto DeX. Reuse therefore preserves state and reduces duplicate/cascade creation, but it does not yet satisfy the product's Workspace switching or display-safety requirements.

No production launcher, flags, delay, schema, policy, or fallback was changed by COMP-010.
