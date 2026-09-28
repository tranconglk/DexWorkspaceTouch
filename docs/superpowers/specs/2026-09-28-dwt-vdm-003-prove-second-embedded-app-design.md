# DWT-VDM-003: Prove a Second Embedded App

## Purpose

Prove that the generic trusted-VDM runtime introduced by DWT-VDM-002 can host a second foreign application without changing that runtime. Samsung Calculator is the second consumer. This milestone validates target independence only; it does not add Workspace integration, a generic app chooser, multiple sessions, persistence, or a new embedding architecture.

The long-term product model remains one persisted Workspace interpreted by either the existing Classic runner or a future Embedded runner:

```text
                 SAME PERSISTED WORKSPACE
                           |
                 +---------+---------+
                 |                   |
          CLASSIC RUNNER      EMBEDDED RUNNER
                 |                   |
       existing DeX windows   EmbeddedAppSession(s)
```

DWT-VDM-003 adds neither runner selection nor `EmbeddedWorkspaceRunner`. It only proves that `EmbeddedAppSession` is reusable as the future Embedded runner's execution primitive.

## Baseline and worktree protection

Implementation will start from commit `8f6bcc79478ce8fd6607a0db9f0c46211ef8b10a` on `release/1.0-beta`.

The following pre-existing work remains outside this milestone and must not be reset, stashed, cleaned, edited, or committed:

- `experiments/shizuku-task-probe/...`
- `docs/compat/COMP-012R_HISTORY_ASSISTED_REUSE_DESIGN.md`
- `AGENTS.md`
- `.superpowers/`

There is no version change, public beta artifact, release publication, licensing change, backend change, or application ID change.

## Device-resolved target

Read-only discovery was performed on the connected Samsung S23 Ultra before this spec was written:

- model: `SM-S918B`
- device: `dm3q`
- firmware: `S918BXXSAFZH3`
- Android: 16 / API 36
- installed package: `com.sec.android.app.popupcalculator`
- installed version: `12.5.10.28` (`versionCode=1251028000`)
- resolved launcher component: `com.sec.android.app.popupcalculator/.Calculator`
- fully qualified component class: `com.sec.android.app.popupcalculator.Calculator`
- launcher query result: exactly one matching activity

The adapter therefore uses exactly:

```kotlin
EmbeddedAppTarget(
    packageName = "com.sec.android.app.popupcalculator",
    componentName = "com.sec.android.app.popupcalculator.Calculator",
    geometry = EmbeddedAppGeometry(900, 675, 320),
)
```

No fallback application is permitted. If this component is unavailable or no longer resolves uniquely during implementation or smoke preparation, the milestone stops with `DWT_VDM_003_BLOCKED_SECOND_APP_TARGET_UNAVAILABLE`.

## Architecture

### Existing generic runtime remains unchanged

`feature/embeddedapp` already owns all target-independent behavior:

- `EmbeddedAppTarget` and `EmbeddedAppGeometry`
- `EmbeddedAppSession` and app-side state
- generic AIDL and shell UserService
- temporary association lifecycle
- trusted VirtualDevice and Surface-backed display
- virtual touchscreen and touch-event construction
- explicit component launch
- new-task identification and cleanup identity
- ordered, idempotent cleanup

DWT-VDM-003 makes no behavioral or source modification under `feature/embeddedapp`. Reuse is demonstrated by supplying a different immutable target to the same public session contract. If Calculator cannot work without modifying this package, implementation stops at that first blocker and reports the failed generic assumption instead of changing the runtime.

### New Calculator adapter

Add a thin package:

```text
feature/embeddedcalculator/
    CalculatorEmbeddedTarget.kt
    EmbeddedCalculatorScreen.kt
```

`CalculatorEmbeddedTarget.kt` contains the only Calculator-specific package, component, and fixed geometry constants. `EmbeddedCalculatorScreen.kt` follows the proven experimental SurfaceView lifecycle and constructs:

```kotlin
EmbeddedAppSession(
    applicationContext,
    CALCULATOR_EMBEDDED_TARGET,
    stateCallback,
)
```

The screen delegates Shizuku connection, session start, Surface transport, touch transport, stop, disposal, and cleanup to `EmbeddedAppSession`. It may duplicate the small amount of app-specific Compose screen structure already used by Embedded Waze. This milestone does not introduce a generic product screen merely to remove UI duplication.

### Navigation integration

The minimum integration adds:

- one `EmbeddedCalculator` route in `TouchNavigation.kt`
- one Home action labeled **Embedded Calculator (Experimental)** or the existing compact equivalent
- one callback parameter in `HomeScreen` and its previews/call sites as required by the existing navigation structure

Existing Embedded Waze behavior and source remain unchanged except for an objective import or call-site adjustment that compilation proves necessary. Classic Workspace navigation and launch behavior remain unchanged.

## Geometry and Surface lifecycle

Calculator uses the same scientific-control geometry as Waze:

- virtual width: `900` pixels
- virtual height: `675` pixels
- density: `320` dpi
- SurfaceView aspect ratio: `4:3`
- fixed Surface buffer: `900 x 675`

The screen maps its current SurfaceView coordinates through the existing generic `mapPoint` helper. It does not add a second geometry option, dynamic resize, orientation handling, scaling policy, Workspace placement, or normalized bounds.

The Surface callback behavior matches the proven adapter contract: a valid Surface enables session start, and Surface destruction requests the existing session stop path when active. Compose disposal closes the same session; generic stop gating prevents duplicate remote cleanup.

## Runtime data flow

1. Home navigates to **Embedded Calculator (Experimental)**.
2. The screen creates one `EmbeddedAppSession` with `CALCULATOR_EMBEDDED_TARGET`.
3. The user connects Shizuku through the existing session API.
4. Start passes the Calculator target and the Surface through the existing generic AIDL service.
5. The generic runtime creates one temporary APP_STREAMING association, one trusted VirtualDevice, one trusted Surface-backed display, and one virtual touchscreen.
6. After input configuration settles, the runtime launches the exact resolved Calculator component on the VDM display.
7. Existing task-selection logic records only the new matching Calculator task, excludes every task present before launch, and fails closed on ambiguity.
8. SurfaceView touch events travel through the existing coordinate, action, pressure, tool-type, and AIDL path.
9. Stop or disposal invokes the existing cleanup state machine.

No Calculator-specific branch enters the AIDL service, UserService, VDM runtime, launcher, task selector, or cleanup implementation.

## Touch proof

Touch semantics remain unchanged:

- one pointer only
- DOWN uses action `0`, finger tool type, and pressure `255f`
- UP uses action `1`
- MOVE uses action `2`
- CANCEL uses action `3` and PALM tool type `5`
- no focus workaround
- no keyboard or IME automation

The device proof uses a deterministic, human-visible Calculator interaction. The operator will tap digit and operator controls through the embedded SurfaceView and verify that the Calculator display changes accordingly, for example `1`, `+`, `2`, `=` producing a visible result. Exact coordinates are selected from the runtime Calculator UI rather than hard-coded in product source. A separate bounded touch ending in CANCEL verifies that cancellation causes no exception and does not trigger a second action or retry.

## Task and display proof

Before start, capture existing Calculator task IDs. After start, verify:

- one new task belongs to `com.sec.android.app.popupcalculator`
- its launched component is `com.sec.android.app.popupcalculator/.Calculator`
- its task ID was absent from the pre-start snapshot
- its display ID equals the trusted VDM display ID
- the host DWT activity remains on the same pre-start host display
- the host display may be display 0 or a DeX/external display
- the host DWT activity does not migrate to the VDM display
- the Calculator runs on a distinct trusted VDM display
- the Calculator window is drawn and visible inside the SurfaceView
- the virtual touchscreen descriptor is associated with the same VDM display
- the display is `900 x 675 @ 320 dpi` and trusted

If the generic runtime rejects, misidentifies, or moves this target, stop without changing `feature/embeddedapp`. The report must distinguish a generic assumption failure from Calculator-specific policy or lifecycle behavior.

## Cleanup proof

The Calculator screen uses only the existing generic cleanup path. Evidence must show this order:

1. close the virtual input device exactly once
2. verify its InputDevice descriptor disappears while the VirtualDevice remains alive
3. remove only the newly recorded Calculator task
4. close the VirtualDevice exactly once, removing its display and policy controller
5. remove only the temporary association created for this session
6. clear/release session references

After cleanup there must be no probe association, VirtualDevice, VDM display, virtual touchscreen, or new Calculator task. Pre-existing Calculator tasks, if any, must remain untouched. DWT is not uninstalled, its data is not cleared, and Calculator is not force-stopped.

## App-specific files and allowed changes

New app-specific files:

- `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedcalculator/CalculatorEmbeddedTarget.kt`
- `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedcalculator/EmbeddedCalculatorScreen.kt`
- `app/src/test/java/com/trancong/dexworkspacetouch/feature/embeddedcalculator/CalculatorEmbeddedTargetTest.kt`

Minimum integration changes are allowed in:

- `app/src/main/java/com/trancong/dexworkspacetouch/navigation/TouchNavigation.kt`
- `app/src/main/java/com/trancong/dexworkspacetouch/ui/screens/HomeScreen.kt`
- preview or call-site files only when required by the new Home callback

No source file under `feature/embeddedapp` is in the allowed implementation set.

## Tests

Add focused Calculator adapter tests that assert:

- package is exactly `com.sec.android.app.popupcalculator`
- component is exactly `com.sec.android.app.popupcalculator.Calculator`
- geometry is exactly `900 x 675 @ 320 dpi`

Do not duplicate generic coordinate, input, task-selection, or cleanup tests. The existing full generic and Waze suites must continue to pass unchanged.

## Build and installation gates

Before the device proof, run:

- `testDebugUnitTest`
- `lintDebug`
- `assembleDebug`
- `git diff --check`
- `scripts/build-device-smoke.ps1`

Verify the production-config smoke APK with the existing signing workflow, then install it with `adb install -r`. Never uninstall DWT, clear its data, change its application ID, modify version name/code, or create a public release artifact.

## One device smoke

Run exactly one Calculator session after all gates pass:

1. Open Home, then **Embedded Calculator (Experimental)**.
2. Confirm the route and `900 x 675` SurfaceView are visible.
3. Connect and start exactly one session.
4. Verify trusted VDM ownership, display geometry, new Calculator task identity, Calculator pixels in the SurfaceView, and touchscreen/display association.
5. Perform the deterministic visible Calculator interaction through the SurfaceView.
6. Verify MOVE/UP behavior through that interaction and perform one CANCEL sequence without exception.
7. Stop once and verify the complete cleanup order and zero orphan resources.

No Waze smoke is required because `feature/embeddedapp` and Embedded Waze remain unchanged.

## Immediate-stop conditions

Stop the milestone without runtime modification or workaround at the first occurrence of any of these conditions:

- Samsung Calculator is unavailable or its launcher no longer resolves uniquely.
- Implementation requires any source or behavioral change under `feature/embeddedapp`.
- The generic runtime cannot launch the exact Calculator component.
- The runtime cannot uniquely associate the newly launched Calculator task with the configured target and VDM display.
- Calculator leaves the trusted display, does not render in the SurfaceView, or requires a new focus/input workaround.
- A build, signing, install, device identity, permission, or license gate fails.
- Touch proof would require keyboard, IME, coordinates embedded in product source, retry, or another input architecture.
- Cleanup would require a Calculator-specific branch or cannot restore the resource baseline.

When stopped because the generic runtime must change, report the exact assumption, observed evidence, and classification as either a generic-runtime defect or an app-specific incompatibility. Do not fix it in DWT-VDM-003.

## Acceptance

DWT-VDM-003 passes only when Samsung Calculator uses the existing generic runtime without changes under `feature/embeddedapp`; its exact resolved component launches as a new task on the trusted `900 x 675 @ 320 dpi` display; its pixels render in the SurfaceView; existing virtual touch causes a visible Calculator state change and CANCEL causes no exception; cleanup removes only the probe resources in the proven order; all build gates pass; and no unrelated or Workspace model is changed.

This result establishes that `EmbeddedAppSession` is target-independent enough for a future `EmbeddedWorkspaceRunner`. It does not create a second persisted Workspace model and does not implement Workspace placement or run-mode selection.
