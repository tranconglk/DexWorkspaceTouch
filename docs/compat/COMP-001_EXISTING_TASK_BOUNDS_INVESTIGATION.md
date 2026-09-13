# COMP-001 — Existing Task Bounds Investigation for Samsung DeX

## A. Executive summary

The launcher does **not** reposition or resize an already-existing task. It issues a new explicit launcher-activity request with `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK` and `ActivityOptions.launchBounds`. For an ordinary `standard` launcher activity, Android documents this pair as starting a new task instead of searching for and bringing forward a matching task. The old task is neither queried nor modified.

DexWorkspaceTouch does not inspect the target activity's `launchMode`, `documentLaunchMode`, `taskAffinity`, `allowTaskReparenting`, or related task metadata. A target manifest or Samsung DeX policy can therefore defeat fresh-task assumptions or constrain the resulting window. Android's public `setLaunchBounds()` contract describes the activity being launched; it does not guarantee resizing a reused task. There is no post-launch task lookup, actual-bounds verification, or corrective resize operation.

The S22 permutation from expected `25 / 25 / 50` to observed `25 / 50 / 25` is not produced by the deterministic conversion code: each target retains its normalized rectangle, targets are sorted by explicit order, and each edge is mapped independently with `roundToInt`, a fixed margin, and clamping. A global work-area error would normally offset/scale every rectangle instead of permuting widths.

**Final decision: D. INSUFFICIENT EVIDENCE — NEED COLD/WARM TEST.** Existing-task/app task behavior remains plausible, but `MULTIPLE_TASK` makes simple standard-activity reuse less certain than the initial hypothesis.

## B. Current launch pipeline

Production Home/Library path:

1. `WorkspaceLibraryCard` invokes `onOpen` from its Open button (`workspace/library/ui/WorkspaceLibraryCard.kt`).
2. `HomeScreen` maps it to `onLaunchWorkspace(workspace)` (`ui/screens/HomeScreen.kt`, around lines 729–767).
3. `TouchNavigation` calls `WorkspaceLaunchViewModel.launchWorkspace(workspace, launchRuntime, launchHostToken)` (`navigation/TouchNavigation.kt`, lines 448–449).
4. `WorkspaceLaunchViewModel.launchWorkspace()` creates readiness, checks the environment, and invokes `runtime.launch(request)` in `viewModelScope` (`workspace/launcher/presentation/WorkspaceLaunchViewModel.kt`).
5. `WorkspaceLaunchRequestFactory.create()` validates the canvas/components, retains cell order as `AppLaunchTarget.order`, and copies each cell's `NormalizedBounds` unchanged (`workspace/launcher/WorkspaceLaunchRequestFactory.kt`).
6. `AndroidWorkspaceLaunchRuntime` composes `AndroidWorkspaceLauncher(AndroidSingleAppLauncher(ActivitySingleAppLaunchPlatform(...)))` (`platform/launch/android/AndroidWorkspaceLaunchRuntime.kt`).
7. `AndroidWorkspaceLauncher.launch()` sorts by `order` and launches sequentially (`platform/launch/android/AndroidWorkspaceLauncher.kt`).
8. `AndroidSingleAppLauncher.launch()` gets a work-area snapshot, verifies the component, calculates and sanity-checks pixel bounds, then calls `platform.start()` (`platform/launch/android/AndroidSingleAppLauncher.kt`).
9. `ActivitySingleAppLaunchPlatform.start()` builds the intent/options and calls `activity.startActivity()` on `Dispatchers.Main.immediate` (`platform/launch/android/ActivitySingleAppLaunchPlatform.kt`).

Car/Floating Dock workspace execution reuses the same request factory, single-app launcher, bounds calculator, and sequential workspace launcher. Its overlay runtime uses `DisplayTargetWorkspaceLaunchRuntime` with an explicitly selected live external display; it is not a second workspace engine.

## C. Intent flags

The exact workspace target intent is:

- `Intent.ACTION_MAIN`
- `Intent.CATEGORY_LAUNCHER`
- explicit `ComponentName(packageName, activityName)`
- `FLAG_ACTIVITY_NEW_TASK` (`0x10000000`)
- `FLAG_ACTIVITY_MULTIPLE_TASK` (`0x08000000`)

Both Activity-hosted and display-target implementations use this pair. They do **not** add `CLEAR_TOP`, `CLEAR_TASK`, `REORDER_TO_FRONT`, `NEW_DOCUMENT`, `RESET_TASK_IF_NEEDED`, `SINGLE_TOP`, or another task flag.

Android documents `NEW_TASK` alone as capable of bringing a matching existing task forward. Paired `MULTIPLE_TASK` skips that search and requests a new task; it does not resize the previous task. Reference: [Android `Intent` flags](https://developer.android.com/reference/android/content/Intent).

## D. ActivityOptions behavior

`ActivitySingleAppLaunchPlatform.start()` performs:

1. revalidate foreground Activity and expected external display;
2. require stored activity name;
3. construct `ACTION_MAIN` + `CATEGORY_LAUNCHER`;
4. set explicit component;
5. add `NEW_TASK`, then `MULTIPLE_TASK`;
6. convert `PixelBounds` to `Rect`;
7. `ActivityOptions.makeBasic().setLaunchBounds(rect)`;
8. `setLaunchDisplayId(expectedDisplayId)` only for `LaunchDisplayRoutingMode.EXPLICIT`;
9. `activity.startActivity(intent, options.toBundle())`.

Home/Library uses default `INHERITED` routing: the host is verified as external, but `setLaunchDisplayId` is not added. Overlay/background `DisplayTargetSingleAppLaunchPlatform.start()` revalidates a non-default live display and always applies both launch bounds and display ID before `applicationContext.startActivity()`.

No windowing mode or other option is set. Android states that `setLaunchBounds` supplies screen-coordinate pixels for the activity to be launched and may be ignored without freeform/PiP support. It does not promise that a pre-existing task will be resized. Reference: [Android `ActivityOptions`](https://developer.android.com/reference/android/app/ActivityOptions).

## E. Existing task reuse analysis

DexWorkspaceTouch does not enumerate tasks, obtain a task ID, resize/reposition a task, move an old task to a display, or compare actual bounds after launch.

| Case | Active behavior |
|---|---|
| A. App not running | Requests new task with explicit component, bounds, and external routing. |
| B. Process running, no visible task | Same; process state is not inspected. |
| C. Task on same DeX display | Does not touch it; requests a separate task for ordinary modes. Manifest/OEM rules may still reuse/constrain. |
| D. Task on another display | Does not locate/move it; requests external launch with no postcondition check. |
| E. App visible/freeform | Does not resize it; requests another launcher start. |
| F. App manually resized | Old task remains as-is; another task/bounds is requested. |
| G. Task created by prior Workspace | No ownership/history recognition; same new launch request. |

Thus there is **no guarantee** that an old task is repositioned, resized, moved, or receives the new bounds. The limitation is the `startActivity` boundary and lack of a supported cross-app task-control step.

## F. Activity launch-mode analysis

`PackageManagerAdapter.verifyActivityExists()` calls `getActivityInfo()` only to prove the explicit component exists; the result is discarded. No code reads `ActivityInfo.launchMode`, `documentLaunchMode`, `taskAffinity`, `allowTaskReparenting`, `excludeFromRecents`, `resizeableActivity`, minimum window dimensions, or related metadata.

- `standard`: normally allows a fresh instance/task; current flags request it.
- `singleTop`: a fresh task is generally possible, subject to system/app selection.
- `singleTask`: system uniqueness/affinity can reuse an existing task and deliver `onNewIntent`; requested bounds are not a resize guarantee.
- `singleInstance`: unique-instance semantics strongly favor reuse/bring-to-front.
- `singleInstancePerTask`: multiple task instances are documented as possible with `MULTIPLE_TASK`/`NEW_DOCUMENT`, but device/app constraints remain.
- `documentLaunchMode="never"`, affinity, reparenting and other attributes can alter task behavior and are not modeled.

Reference: [Android `<activity>` attributes](https://developer.android.com/guide/topics/manifest/activity-element).

## G. Bounds calculation pipeline

`NormalizedBounds` is constrained to `[0,1]` with positive size. The request factory preserves it and assigns list index as order. For each app, `LaunchBoundsCalculator` computes:

- `left = round(originX + normalized.left * usableWidth) + marginPx`
- `right = round(originX + normalized.right * usableWidth) - marginPx`
- equivalent top/bottom formulas
- `marginPx = round(8dp * density)`
- clamp every edge to the usable rectangle
- reject collapsed/off-area bounds with typed `LAUNCH_REJECTED`

There is no minimum app/window-size logic and no sorting by width or x-position. The only gap is the fixed 8dp inward margin on every edge. Origin is `(insetLeft, insetTop)` in display coordinates.

Cells `[0,.25]`, `[.25,.5]`, `[.5,1]` remain approximately `25%`, `25%`, `50%` minus equal margins. No conversion branch can produce `25%`, `50%`, `25%`.

## H. Display/work-area analysis

Activity-hosted Home/Library:

- API 30+: `ActivityDisplayWorkAreaProvider.snapshotFromWindowMetrics()` uses `maximumWindowMetrics` for display bounds/insets, considers current/root candidates, rejects host-window candidates outside the display coordinate space, and resolves per-edge insets.
- API 28–29/Note9: uses `getRealMetrics`, root/system/stable/cutout candidates, `DisplayMetricsDeltaEvaluator`, and guarded `LegacyDisplayWorkAreaReferenceStore` fallback only when direct evidence is unavailable. Display IDs and coordinate spaces must match.

Overlay/background:

- API 30+: display/window context `maximumWindowMetrics` plus system-bar/cutout insets.
- API 29: `legacyExternalDisplayWorkArea()` uses `Display.Mode` physical dimensions; `Display.getSize()` contributes bottom inset only when width is within 75–125% of physical width, avoiding compatibility-scaled metrics.

S22 One UI 8 follows the modern path. Current evidence does not demonstrate a bad work area. A shared work-area error does not naturally explain reordered widths, and S23 is known-good. Do not change this boundary from the current feedback alone.

## I. Multi-app sequencing

Targets are sorted by ascending order and launched serially, never in parallel. Default coroutine delay is 400 ms exactly between targets and never after the final target. Non-display failures accumulate and launch continues; `DISPLAY_UNAVAILABLE` stops and marks remaining targets unavailable. Aggregation is deterministic `Success`, `PartialSuccess`, or `Failure`.

Later launches change focus/z-order, and Samsung DeX may apply placement policy during task creation. DexWorkspaceTouch never reapplies earlier bounds, so sequencing/focus is plausible as an OEM side effect, although source code itself never swaps rectangles.

## J. Existing test coverage

Coverage includes request validation/bounds preservation; deterministic component resolution; full/half/third/nested/inset conversion; rounding, margin, clamping and sanity; modern coordinate-space resolution; legacy metric/reference guards; display disappearance; sequencing/delay/cancellation/partial failure; golden custom four/five-cell flows; and device tests for external-display focus plus selected 50/50 Car/Dock launches.

The tests protect calculations better than runtime placement. Device tests generally check focus/display presence rather than all final task bounds for 1/2/3/4/custom layouts.

**COVERAGE GAP:** no test proves that an app already running at different bounds is reopened from the same/different Workspace and ends at the newly requested bounds. There is no target launch-mode matrix or One UI 8 warm-task test.

## K. S22 evidence analysis

| Hypothesis | Rank | Assessment |
|---|---|---|
| H1 Existing task reuse | MEDIUM | No old-task reposition exists, but `MULTIPLE_TASK` requests a fresh task for ordinary modes. Special modes/OEM reuse remain plausible. |
| H2 Samsung One UI 8 freeform behavior | HIGH | Device/OS-specific report; public launch bounds are requests, not post-launch control. |
| H3 App launchMode / `singleTask` | MEDIUM | Not inspected/logged and can defeat fresh-task assumptions. Target identities are unknown. |
| H4 Incorrect work-area metrics | LOW | Would normally offset/scale all rectangles; no S22 metric evidence supports it. |
| H5 Normalized conversion bug | NOT SUPPORTED | Per-cell bounds/order are preserved; quarter/half/third edges are unit-tested. |
| H6 Sequencing/focus side effect | MEDIUM | Serial launches alter focus and may trigger Samsung placement; code does not rewrite bounds. |
| H7 App-specific minimum size | MEDIUM | No min-size handling; DeX/app constraints can enlarge/displace a window. |

## L. Cold-vs-warm reproduction plan

Keep the same S22, DeX resolution, Workspace, assignment order, and apps. Capture screenshot and dumps after each run.

### TEST A — COLD

1. Remove target apps from DeX Recents; do not force-stop/clear data.
2. Open DexWorkspaceTouch.
3. Open the affected Workspace once.
4. Capture result and evidence.

### TEST B — WARM SAME WORKSPACE

1. Leave apps running.
2. Manually resize/reposition one app clearly.
3. Open the same Workspace again.
4. Capture result; compare task IDs and bounds.

### TEST C — WARM DIFFERENT WORKSPACE

1. Leave the same apps running.
2. Open another Workspace assigning them to different bounds/order.
3. Capture result; compare IDs and bounds.

Cold PASS + warm FAIL is strong task-reuse/app-policy evidence. Cold FAIL + warm FAIL points toward requested bounds, app constraints, work area, or Samsung placement. One consistently wrong app points toward app-specific metadata/constraints. A uniform offset/scale across all tasks would elevate work-area evidence.

## M. Diagnostic logging proposal

Do not implement in COMP-001. A diagnostic build should emit one bounded record per target:

```text
[WORKSPACE-LAUNCH]
workspaceId=<id> workspaceName=<name>
sequenceIndex=<n> timestamp=<UTC>
packageName=<package> activityName=<activity> component=<component>
activityLaunchMode=<value or unknown>
displayId=<dynamic> displayWidth=<px> displayHeight=<px>
workArea=<bounds/insets> selectedInsetSource=<source>
normalizedBounds=<l,t,r,b> requestedPixelBounds=<l,t,r,b>
intentFlags=0x18000000 launchPolicy=FRESH_TASK_REQUEST
startActivity=<SUCCESS or typed failure>
```

Do not log license keys, tokens, customer data, credentials, or secrets.

## N. Actual-bounds collection options

An ordinary third-party app cannot reliably enumerate exact bounds of other apps' tasks or resize them through public APIs. Production must not use hidden APIs, root, Shizuku, or ADB.

Tester commands:

```powershell
adb devices -l
adb shell dumpsys display
adb shell dumpsys window displays
adb shell dumpsys activity activities
adb shell dumpsys activity recents
adb shell dumpsys window windows
adb shell wm size
adb shell wm density
adb logcat -d -s DexSingleAppLaunch
```

Capture task IDs, `mDisplayId`, `bounds`/`mBounds`, `windowingMode`, `realActivity`, `topActivity`, focus, display mode, and work-area diagnostics before launch, after each target, and after the sequence.

## O. Potential fixes ranked

Do not implement before S22 evidence.

1. **A — keep current behavior.** Zero immediate S23/Note9 regression; symptom remains and bounds stay best-effort.
2. **C — launch-policy abstraction (`REUSE_EXISTING_TASK` / `FRESH_TASK_IF_POSSIBLE`).** Makes intent testable/visible, but public APIs still cannot guarantee arbitrary reused-task resize. Moderate regression risk if default changes.
3. **D — per-app compatibility policy.** Isolates proven special apps, but creates brittle version/OEM maintenance.
4. **E — user-selectable layout priority versus app-state priority.** Exposes the trade-off but adds UX/configuration complexity without creating missing OS guarantees.
5. **B — `NEW_TASK | MULTIPLE_TASK`.** Production already uses this pair. Making it more aggressive is not a new fix, cannot override every manifest/OEM rule, and risks duplicate tasks/Recents and state loss.

Never default to force-stop, kill, `CLEAR_TASK`, `CLEAR_TOP`, root, Shizuku, ADB, or hidden APIs merely to resize.

## P. Regression risks

Future task/flag changes can affect task duplication, Recents, back-stack/state, special launch modes, DeX focus/sequencing, Floating Dock background launches, inherited versus explicit display routing, and Android 10 behavior independently of work-area code.

The known-good S23 matrix (1 app, 2-app 50/50, 3 app, 4 app, custom layouts, external display, Car Mode, Floating Dock) needs final-task-bounds regression coverage. Keep the Note9 work-area path unchanged; even flags-only changes require Note9 device regression.

## Q. Recommended next action

Run TEST A/B/C on the S22 and collect exact package/activity identities, task IDs, and actual bounds before/after every launch. The next task should add diagnostic logging or a tester-only evidence collector, not alter production flags or work-area algorithms.

- Cold correct + warm reuses same task ID/bounds: design a narrow launch policy from that evidence.
- Cold and warm fail for one app: inspect that app's launch/resize metadata and minimum size.
- All tasks show a systematic offset/scale and requested bounds are already wrong: only then reopen work-area investigation.

**Final decision: INSUFFICIENT EVIDENCE — NEED COLD/WARM TEST.**
