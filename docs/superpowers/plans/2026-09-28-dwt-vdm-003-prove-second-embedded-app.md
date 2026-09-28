# DWT-VDM-003 Prove Second Embedded App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a thin Samsung Calculator consumer that proves the existing `EmbeddedAppSession` and trusted-VDM runtime can host a second foreign application without changing the generic runtime.

**Architecture:** Add immutable Calculator target configuration and one app-specific Compose screen under `feature/embeddedcalculator`, then expose it through the existing Home/navigation structure. The adapter delegates Surface, session, touch, and cleanup behavior to `feature/embeddedapp`; no generic runtime, Workspace model, persistence, Classic runner, or Waze implementation changes are allowed.

**Tech Stack:** Kotlin, Jetpack Compose, Android `SurfaceView`, Navigation Compose, Shizuku-backed `EmbeddedAppSession`, JUnit 4, Gradle, PowerShell smoke-build tooling, ADB.

**Spec:** `docs/superpowers/specs/2026-09-28-dwt-vdm-003-prove-second-embedded-app-design.md`

## Global Constraints

- Baseline is `8f6bcc79478ce8fd6607a0db9f0c46211ef8b10a` on `release/1.0-beta`.
- The device-resolved target is exactly `com.sec.android.app.popupcalculator/com.sec.android.app.popupcalculator.Calculator`.
- Geometry remains exactly `900 x 675 @ 320 dpi`; this milestone does not test geometry independence.
- Do not modify any source under `app/src/main/**/feature/embeddedapp` or any existing source under `feature/embeddedwaze`.
- Do not add another AIDL service, UserService, VDM implementation, association helper, touchscreen implementation, task selector, or cleanup path.
- Do not add Workspace IDs, Workspace models, normalized bounds, placement coordinates, run modes, Room IDs, or persisted layout state to `EmbeddedAppTarget` or the Calculator adapter.
- Preserve `experiments/shizuku-task-probe/...`, `docs/compat/COMP-012R_HISTORY_ASSISTED_REUSE_DESIGN.md`, `AGENTS.md`, and `.superpowers/`.
- Do not change version name/code, application ID, licensing, backend, release outputs, or Classic Workspace behavior.
- If Calculator requires a change to `feature/embeddedapp`, a focus workaround, app-specific cleanup, or ambiguous task handling, stop at that first blocker and do not fix it in DWT-VDM-003.
- Run exactly one Calculator device session after all build/signing/device gates pass. Do not run a Waze device smoke when generic and Waze sources remain unchanged.
- Resolve the S23 ADB serial dynamically from `adb devices -l` plus `ro.product.model=SM-S918B` and `ro.product.device=dm3q`; store the selected endpoint and use it consistently for every later ADB command. Multiple endpoints for the same physical S23 are not multiple devices, but ambiguous matching physical devices require STOP.
- A DWT-VDM-003 PASS proves target independence only. The runtime remains single-session; do not claim multi-session readiness or that `EmbeddedWorkspaceRunner` is implemented or ready.
- Do not commit until all implementation, build, signing, device smoke, cleanup, and scoped-diff checks pass. Do not push.

## Review Focus

- **Target drift:** re-resolving Calculator must yield exactly one launcher and the exact approved component; Task 4 stops otherwise.
- **Runtime boundary drift:** the implementation diff must contain no change under `feature/embeddedapp`; Tasks 3 and 6 enforce this with path checks.
- **Waze navigation regression:** the existing Waze route, composable registration, Home callback, and Home action must remain present; Task 3 adds an explicit static regression gate.
- **Pre-existing Calculator task safety:** device evidence must identify the probe task only by pre/post task-ID delta plus target/display identity; Task 5 fails closed on ambiguity.
- **Cleanup after partial UI lifecycle events:** Surface destruction and Compose disposal must delegate to the same existing session stop/close behavior; Task 2 mirrors the proven adapter lifecycle and Task 5 verifies zero orphans.

---

### Task 1: Add the immutable Calculator target adapter

**Files:**
- Create: `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedcalculator/CalculatorEmbeddedTarget.kt`
- Create: `app/src/test/java/com/trancong/dexworkspacetouch/feature/embeddedcalculator/CalculatorEmbeddedTargetTest.kt`

**Interfaces:**
- Consumes: `EmbeddedAppTarget(packageName: String, componentName: String, geometry: EmbeddedAppGeometry)`.
- Produces: `val CALCULATOR_EMBEDDED_TARGET: EmbeddedAppTarget`.

- [ ] **Step 1: Reconfirm the read-only target gate**

Run:

```powershell
$online = adb devices | Select-Object -Skip 1 | ForEach-Object {
    if ($_ -match '^(\S+)\s+device$') { $matches[1] }
} | Where-Object { $_ }
$matchingSerials = foreach ($serial in $online) {
    $model = (adb -s $serial shell getprop ro.product.model).Trim()
    $device = (adb -s $serial shell getprop ro.product.device).Trim()
    if ($model -eq 'SM-S918B' -and $device -eq 'dm3q') { $serial }
}
if (-not $matchingSerials) { throw 'No online SM-S918B/dm3q endpoint' }
# If several matching endpoints exist, confirm they represent the same physical
# phone from stable device identity already used by this project; otherwise STOP.
$dev = $matchingSerials[0]
$dev | Set-Content .superpowers/sdd/2026-09-28-dwt-vdm-003-prove-second-embedded-app/device-serial.txt
adb -s $dev shell getprop ro.build.version.incremental
adb -s $dev shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER com.sec.android.app.popupcalculator
```

Expected: `SM-S918B`, `dm3q`, `S918BXXSAFZH3`, and exactly one launcher result: `com.sec.android.app.popupcalculator/.Calculator`. Otherwise stop with `DWT_VDM_003_BLOCKED_SECOND_APP_TARGET_UNAVAILABLE`.

- [ ] **Step 2: Write the failing target test**

Create `CalculatorEmbeddedTargetTest` with one test named `usesResolvedSamsungCalculatorTargetAndControlGeometry`. Assert:

```kotlin
assertEquals("com.sec.android.app.popupcalculator", CALCULATOR_EMBEDDED_TARGET.packageName)
assertEquals(
    "com.sec.android.app.popupcalculator.Calculator",
    CALCULATOR_EMBEDDED_TARGET.componentName,
)
assertEquals(EmbeddedAppGeometry(900, 675, 320), CALCULATOR_EMBEDDED_TARGET.geometry)
```

- [ ] **Step 3: Run the focused test and observe RED**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "*CalculatorEmbeddedTargetTest" --console=plain
```

Expected: FAIL because `CALCULATOR_EMBEDDED_TARGET` does not exist.

- [ ] **Step 4: Implement the minimal target constant**

Create `CalculatorEmbeddedTarget.kt` in `feature.embeddedcalculator`. Define only `CALCULATOR_EMBEDDED_TARGET` with the exact package, fully qualified component, and `EmbeddedAppGeometry(900, 675, 320)`. Do not add launch logic or Workspace fields.

- [ ] **Step 5: Run the focused test and observe GREEN**

Run the command from Step 3.

Expected: PASS.

- [ ] **Step 6: Verify the adapter boundary**

Run:

```powershell
rg -n "Workspace|workspaceId|normalizedBounds|Room|RunMode|left|top|right|bottom" app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedcalculator/CalculatorEmbeddedTarget.kt
```

Expected: no matches.

### Task 2: Add the Calculator experimental screen

**Files:**
- Create: `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedcalculator/EmbeddedCalculatorScreen.kt`
- Reference only: `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedwaze/EmbeddedWazeScreen.kt`

**Interfaces:**
- Consumes: `CALCULATOR_EMBEDDED_TARGET`, `EmbeddedAppSession`, `EmbeddedAppState`, `mapPoint`, `virtualAction`, and `pressureFor`.
- Produces: `@Composable fun EmbeddedCalculatorScreen(activity: Activity, onBack: () -> Unit)`.

- [ ] **Step 1: Create the screen with the proven adapter lifecycle**

Implement `EmbeddedCalculatorScreen(activity: Activity, onBack: () -> Unit)` with:

- title `Embedded Calculator (Experimental)`
- Back, Connect Shizuku, Start Calculator, and Stop controls
- one `AndroidView` containing one `SurfaceView`
- `setFixedSize(target.geometry.width, target.geometry.height)`
- `fillMaxWidth().aspectRatio(4f / 3f)`
- one remembered `EmbeddedAppSession(activity.applicationContext, target) { state = it }`
- `session.start()` in `DisposableEffect` and `session.close()` in `onDispose`
- `surfaceDestroyed` calling `session.stop()` only while active
- touch mapping through the existing generic helpers and `session.touch(...)`

Do not extract a generic Compose screen, add Calculator-specific touch logic, embed fixed touch coordinates, or change Waze source.

- [ ] **Step 2: Compile the new adapter**

Run:

```powershell
.\gradlew.bat compileDebugKotlin --console=plain
```

Expected: PASS.

- [ ] **Step 3: Verify delegation and absence of duplicated runtime**

Run:

```powershell
rg -n "EmbeddedAppSession|mapPoint|virtualAction|pressureFor|setFixedSize" app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedcalculator/EmbeddedCalculatorScreen.kt
rg -n "createVirtualDevice|createVirtualDisplay|createVirtualTouchscreen|AssociationShell|IEmbeddedAppService|removeTask|sendTouchEvent" app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedcalculator
```

Expected: the first command finds delegation points; the second command finds no duplicated runtime implementation.

### Task 3: Integrate navigation and protect Embedded Waze

**Files:**
- Modify: `app/src/main/java/com/trancong/dexworkspacetouch/navigation/TouchNavigation.kt`
- Modify: `app/src/main/java/com/trancong/dexworkspacetouch/ui/screens/HomeScreen.kt`
- Modify only if compilation requires the callback: `app/src/main/java/com/trancong/dexworkspacetouch/ui/screens/HomeScreenPreviews.kt`

**Interfaces:**
- Consumes: `EmbeddedCalculatorScreen(activity, onBack)`.
- Produces: private route `embedded-calculator` and Home callback `onOpenEmbeddedCalculator: () -> Unit`.

- [ ] **Step 1: Add the route and destination**

In `TouchNavigation.kt`, import `EmbeddedCalculatorScreen`, add `Routes.EmbeddedCalculator = "embedded-calculator"`, register one `composable(Routes.EmbeddedCalculator)`, and navigate back with `navController.popBackStack()`.

- [ ] **Step 2: Add the Home callback and action**

Add `onOpenEmbeddedCalculator: () -> Unit = {}` to `HomeScreen`. Add one Calculator action in both existing wide and compact Home layouts without removing or renaming Waze actions. Wire the callback from the Home destination to `navController.navigate(Routes.EmbeddedCalculator)`.

Use `Embedded Calculator` in the wide layout and `Calculator` in the compact layout unless the existing layout requires the shorter `Calc` label to avoid an objective compilation/layout constraint. Do not add a generic picker or mode selector.

- [ ] **Step 3: Update only required previews/call sites**

Because the callback has a default, do not edit previews or unrelated call sites unless compilation proves it necessary. If an edit is required, pass a no-op callback only.

- [ ] **Step 4: Run the navigation compile gate**

Run:

```powershell
.\gradlew.bat compileDebugKotlin --console=plain
```

Expected: PASS.

- [ ] **Step 5: Run the Waze static regression guard**

Run:

```powershell
$navigation = Get-Content -Raw app/src/main/java/com/trancong/dexworkspacetouch/navigation/TouchNavigation.kt
$homeSource = Get-Content -Raw app/src/main/java/com/trancong/dexworkspacetouch/ui/screens/HomeScreen.kt
if (-not ($navigation.Contains('const val EmbeddedWaze = "embedded-waze"') -and
          $navigation.Contains('composable(Routes.EmbeddedWaze)') -and
          $navigation.Contains('onOpenEmbeddedWaze = { navController.navigate(Routes.EmbeddedWaze) }') -and
          $homeSource.Contains('onOpenEmbeddedWaze: () -> Unit = {}') -and
          $homeSource.Contains('onClick = onOpenEmbeddedWaze'))) { exit 1 }
```

Expected: exit code 0. This is the required regression proof that the Waze route registration, Home callback, and Home action remain present.

- [ ] **Step 6: Prove generic and Waze source are unchanged**

Run:

```powershell
git diff --exit-code 8f6bcc79478ce8fd6607a0db9f0c46211ef8b10a -- app/src/main/aidl/com/trancong/dexworkspacetouch/feature/embeddedapp app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedapp app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedwaze app/src/test/java/com/trancong/dexworkspacetouch/feature/embeddedapp app/src/test/java/com/trancong/dexworkspacetouch/feature/embeddedwaze
```

Expected: no diff and exit code 0. If any change is required, stop and report the blocker; do not continue to build or device smoke.

### Task 4: Run build, signing, and device preflight gates

**Files:**
- No source changes expected.
- Generated smoke artifact remains untracked and outside the commit.

**Interfaces:**
- Consumes: completed Calculator adapter and navigation integration.
- Produces: one verified production-config smoke APK eligible for the single device proof.

- [ ] **Step 1: Run all local build gates**

Run:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain
git diff --check
```

Expected: Gradle `BUILD SUCCESSFUL`; lint has zero errors; diff check exits 0.

- [ ] **Step 2: Repeat the boundary gates**

Run Task 3 Steps 5 and 6 again.

Expected: Waze guard exits 0 and generic/Waze source diff remains empty.

- [ ] **Step 3: Build the production-config smoke APK**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\build-device-smoke.ps1
```

Expected: script exits 0 and produces the documented smoke APK without changing version name/code or release output.

- [ ] **Step 4: Verify signer and device identity**

Run `apksigner verify --print-certs` on the generated smoke APK and require SHA-256:

```text
19ac0ea99125361b3c2083aaa44c8745ebad9c55fa967642c2b9e5a1086a45e7
```

Load the stored endpoint into `$dev` and require:

```text
SM-S918B / dm3q / S918BXXSAFZH3 / Android 16 / API 36
```

Re-run the exact Calculator launcher query from Task 1 with `adb -s $dev` and require one exact match. Stop at the first mismatch.

- [ ] **Step 5: Snapshot the device baseline and install safely**

Before install, record active associations, VirtualDevices, displays, virtual input devices, DWT task/display, and all Calculator task IDs. Install only with:

```powershell
adb -s $dev install -r <smoke-apk>
```

Expected: `Success`; existing DWT data/license remains intact. Never uninstall or clear data.

### Task 5: Run exactly one Calculator device proof

**Files:**
- No source changes permitted during this task.
- Device captures/logs remain untracked verification artifacts.

**Interfaces:**
- Consumes: installed smoke APK and the device baseline from Task 4.
- Produces: launch, render, touch, task-identity, and cleanup evidence for one Calculator session.

Load `$dev` from this plan's `device-serial.txt` before the first command and use only `adb -s $dev` for the entire smoke. Do not switch endpoints mid-session.

- [ ] **Step 1: Reach the route without starting a session**

Open Home and select **Embedded Calculator (Experimental)**. Verify the route title, controls, and visible 4:3 SurfaceView. Do not start if the route or Surface is invalid.

- [ ] **Step 2: Start one session and verify the runtime gates**

Connect Shizuku if needed and press Start Calculator exactly once. Capture logs plus `dumpsys virtualdevice`, `display`, `activity activities`, and `input`. Require:

- remote UID 2000
- one new association, VirtualDevice, trusted display, and virtual touchscreen
- display `900 x 675 @ 320 dpi`
- touchscreen associated with that display
- exactly one new Calculator task ID not present in the baseline
- exact Calculator component/package on the VDM display
- host DWT activity remains on its pre-start host display, which may be display 0 or a DeX/external display
- host DWT does not migrate to the VDM display
- Calculator runs on a distinct trusted VDM display
- Calculator drawn visibly inside the SurfaceView

If task matching is absent or ambiguous, the app leaves the display, or runtime modification appears necessary, stop with the first blocker and do not retry.

- [ ] **Step 3: Perform the deterministic visible touch proof**

Using the runtime Calculator pixels, identify safe centers for `1`, `+`, `2`, and `=`. Tap them through the SurfaceView and visually verify the Calculator display changes to the expected expression/result. Input must travel through the existing screen-to-VDM mapping; do not place coordinates in source, use keyboard/IME, or retry failed coordinates.

- [ ] **Step 4: Verify MOVE/UP and CANCEL semantics**

The visible interaction must include normal DOWN/MOVE/UP delivery through the SurfaceView. Then perform one bounded DOWN/CANCEL sequence over a safe Calculator area. Require no exception, hover substitution, dropped target, task move, or unintended second action. Do not retry.

- [ ] **Step 5: Stop once and verify ordered cleanup**

Press Stop once. Correlate logs and dumpsys evidence for:

1. virtual input close exactly once
2. input descriptor removed while VirtualDevice remains alive
3. only the new Calculator task removed
4. VirtualDevice close exactly once and display removed
5. the probe association removed
6. session references released

Require no orphan association, VirtualDevice, display, touchscreen, or new Calculator task. Any Calculator task present in the baseline must remain.

- [ ] **Step 6: Record the bounded conclusion**

If every gate passes, record `DWT-VDM-003: PASS — second target reuse verified`. State explicitly that the result proves target independence for sequential single-session use only and does not prove multi-session readiness.

### Task 6: Final scoped review and commit

**Files:**
- Stage only the Calculator adapter/test, minimum navigation/Home integration, this plan, and the approved spec.
- Deliberately leave protected worktree files and all generated artifacts uncommitted.

**Interfaces:**
- Consumes: complete build and device evidence.
- Produces: one scoped DWT-VDM-003 commit; no push.

- [ ] **Step 1: Inspect the complete working diff**

Run:

```powershell
git status --short
git diff --check
git diff -- app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedcalculator app/src/test/java/com/trancong/dexworkspacetouch/feature/embeddedcalculator app/src/main/java/com/trancong/dexworkspacetouch/navigation/TouchNavigation.kt app/src/main/java/com/trancong/dexworkspacetouch/ui/screens/HomeScreen.kt app/src/main/java/com/trancong/dexworkspacetouch/ui/screens/HomeScreenPreviews.kt docs/superpowers/specs/2026-09-28-dwt-vdm-003-prove-second-embedded-app-design.md docs/superpowers/plans/2026-09-28-dwt-vdm-003-prove-second-embedded-app.md
```

Expected: only approved DWT-VDM-003 changes in the scoped diff.

- [ ] **Step 2: Re-run the source-boundary and Waze regression checks**

Run Task 3 Steps 5 and 6.

Expected: both PASS. Also run:

```powershell
rg -n "createVirtualDevice|createVirtualDisplay|createVirtualTouchscreen|AssociationShell|IEmbeddedAppService|removeTask|sendTouchEvent" app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedcalculator
```

Expected: no duplicated runtime implementation.

- [ ] **Step 3: Stage only approved files and inspect the index**

Stage exact paths only. Do not use broad `git add app` or `git add .`. Then run:

```powershell
git diff --cached --check
git diff --cached --stat
git diff --cached
```

Expected: no protected or generated file is staged.

- [ ] **Step 4: Commit after full PASS**

Commit with:

```powershell
git commit -m "feat: prove embedded Calculator runtime reuse"
```

Do not push.

- [ ] **Step 5: Report final state**

Report commit SHA, exact committed files, build/signing/device evidence, Calculator task/display/touch/cleanup proof, protected files left uncommitted, `git status --short`, and `Push: NO`. End with the explicit scope statement: DWT-VDM-003 proves second-target reuse in the existing single-session runtime; multi-session readiness remains unproven and belongs to a later milestone.
