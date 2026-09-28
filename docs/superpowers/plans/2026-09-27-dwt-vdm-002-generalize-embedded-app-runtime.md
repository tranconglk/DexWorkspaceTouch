# DWT-VDM-002 Generalize Embedded App Runtime Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract the verified Waze VDM implementation into one reusable embedded foreign-app runtime while preserving the existing Waze route, touch semantics, and cleanup contract.

**Architecture:** Move target-independent models, Shizuku session/AIDL bridge, association lifecycle, trusted VDM/display, virtual input, launching, and cleanup into `feature/embeddedapp`. Keep `feature/embeddedwaze` as a thin UI and immutable Waze target configuration that delegates to the generic runtime.

**Tech Stack:** Kotlin, Jetpack Compose, Android AIDL, Shizuku UserService, hidden Android 16 VirtualDevice and virtual-input Binder APIs, JUnit 4, Gradle.

**Spec:** `docs/superpowers/specs/2026-09-27-dwt-vdm-002-generalize-embedded-app-runtime-design.md`

## Global Constraints

- Baseline is `04f07348f2c55a99809c39065efbfa00ceccdd72` on `release/1.0-beta`.
- Preserve Waze geometry `900 x 675 @ 320 dpi`.
- Preserve single-pointer DOWN pressure `255f`, MOVE/UP behavior, and CANCEL action `3` with PALM tool type `5`.
- Preserve cleanup order: input close and removal verification, target task removal, VirtualDevice close, association removal, reference release.
- Cleanup must be idempotent and shared by Stop, Surface destruction, Compose disposal, startup failure, and remote destruction.
- Do not add generic UI, discovery, persistence, multiple sessions, resizing, focus workarounds, or any out-of-scope integration.
- The generic runtime must remain independent from Workspace models and placement. `EmbeddedAppTarget` must not contain workspace IDs, `WorkspaceLibraryItem`, normalized or absolute workspace bounds, Classic/Embedded run mode, Room IDs, or persistence IDs. Its geometry describes only the VirtualDisplay owned by one session; a future EmbeddedWorkspaceRunner may map the same persisted Workspace items into container placement without requiring a separate persisted model.
- Geometry validation must reuse DWT-VDM-001 technical limits. Do not invent a product-level size policy. `900 x 675 @ 320 dpi` remains valid; any new upper bound requires an existing Android/VDM constraint and a focused unit test.
- Do not modify licensing, Classic Workspace, Car, Dock, backend, release workflow, version metadata, or public artifacts.
- Do not modify or stage `experiments/shizuku-task-probe`, `docs/compat/COMP-012R_HISTORY_ASSISTED_REUSE_DESIGN.md`, or `AGENTS.md`.
- Do not commit until all build gates and the one S23 regression smoke pass. Do not push.

## Review Focus

- Invalid or mismatched package/component input must fail before launching or deleting any task; covered in Task 2 validation tests.
- Repeated Stop, Surface destruction, and Compose disposal must not double-close resources; covered in Task 1 cleanup idempotency tests and Task 3 session tests.
- Startup failure after only some resources exist must still follow the same remaining cleanup order; covered in Task 2 cleanup transition tests.
- A pre-existing Waze task must never be removed; covered in Task 2 target-selection tests.
- CANCEL must use PALM while DOWN/MOVE/UP remain FINGER; covered in Task 1 touch-contract tests.

---

### Task 1: Generic models, geometry, touch contract, and cleanup sequence

**Files:**
- Create: `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedapp/EmbeddedAppModels.kt`
- Create: `app/src/test/java/com/trancong/dexworkspacetouch/feature/embeddedapp/EmbeddedAppModelsTest.kt`
- Modify or remove after migration: `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedwaze/EmbeddedWazeModels.kt`
- Modify or remove after migration: `app/src/test/java/com/trancong/dexworkspacetouch/feature/embeddedwaze/EmbeddedWazeModelsTest.kt`

**Interfaces:**
- Produces: `EmbeddedAppGeometry(width: Int, height: Int, densityDpi: Int)`, `EmbeddedAppTarget(packageName: String, componentName: String, geometry: EmbeddedAppGeometry)`, `VdmPoint`, `mapPoint(...)`, `virtualAction(Int)`, `virtualToolType(Int)`, `pressureFor(Int, Float)`, and an idempotent cleanup progress model.

- [ ] **Step 1: Write failing generic model tests**

Add tests named `mapsCenterIntoConfiguredGeometry`, `clampsEveryDisplayEdge`, `mapsDownMoveUpCancel`, `downUsesPressure255`, `cancelUsesPalmAndOtherActionsUseFinger`, `cleanupAdvancesInRequiredOrder`, and `cleanupCompletionIsIdempotent`. Assert exact `900 x 675`, action values `0..3`, pressure `255f`, PALM `5`, and cleanup order `INPUT, TASK, DEVICE, ASSOCIATION, REFERENCES`.

- [ ] **Step 2: Run the focused tests and verify RED**

Run: `./gradlew.bat testDebugUnitTest --tests "*.EmbeddedAppModelsTest"`

Expected: FAIL because generic models do not exist.

- [ ] **Step 3: Implement generic models**

Create the interfaces above. Inspect and preserve the DWT-VDM-001 geometry constraints: accept positive width, height, and density without inventing a product-level upper bound; keep `900 x 675 @ 320 dpi` valid. Require the component to belong to the package, clamp mapped coordinates to configured width/height, and make marking an already completed cleanup step a no-op while rejecting out-of-order first completion. Keep Workspace IDs, models, placement bounds, run modes, Room IDs, and persistence IDs out of `EmbeddedAppTarget`.

- [ ] **Step 4: Run the focused tests and verify GREEN**

Run: `./gradlew.bat testDebugUnitTest --tests "*.EmbeddedAppModelsTest"`

Expected: PASS.

### Task 2: Generic remote bridge and runtime

**Files:**
- Create: `app/src/main/aidl/com/trancong/dexworkspacetouch/feature/embeddedapp/remote/IEmbeddedAppService.aidl`
- Create: `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedapp/remote/AssociationShell.kt`
- Create: `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedapp/remote/EmbeddedAppUserService.kt`
- Create: `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedapp/remote/EmbeddedAppVdm.kt`
- Create: `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedapp/remote/ShellDisplayLaunch.kt`
- Test: `app/src/test/java/com/trancong/dexworkspacetouch/feature/embeddedapp/EmbeddedAppRemotePolicyTest.kt`
- Remove after migration: `app/src/main/aidl/com/trancong/dexworkspacetouch/feature/embeddedwaze/remote/IEmbeddedWazeService.aidl`
- Remove after migration: Waze-named remote runtime files under `feature/embeddedwaze/remote/`

**Interfaces:**
- Consumes: `EmbeddedAppTarget`, `EmbeddedAppGeometry`, and generic touch helpers from Task 1.
- Produces: generic AIDL methods `getUid()`, `startSession(Surface, packageName, componentName, width, height, densityDpi)`, `sendTouch(...)`, `stopSession()`, and `destroy()`; one `EmbeddedAppVdm` implementation.

- [ ] **Step 1: Write failing remote-policy tests**

Add tests for package/component matching, valid positive geometry, pre-existing-task exclusion, new-task matching by configured package/component/display, ambiguous-task rejection, and partial cleanup order. Keep policy helpers pure so these tests do not need Android services.

- [ ] **Step 2: Run remote-policy tests and verify RED**

Run: `./gradlew.bat testDebugUnitTest --tests "*.EmbeddedAppRemotePolicyTest"`

Expected: FAIL because generic remote policy/runtime does not exist.

- [ ] **Step 3: Move the AIDL and association implementation**

Create `IEmbeddedAppService` with the exact generic start fields. Move `AssociationShell` without behavioral change; retain the APP_STREAMING profile and exact association removal behavior.

- [ ] **Step 4: Extract `EmbeddedAppVdm` and explicit launcher**

Move the proven reflection/Binder implementation. Replace every Waze/same-app branch and hard-coded package/component with the active `EmbeddedAppTarget`. Preserve trusted-only requested flags, touchscreen-before-launch, dispatcher readiness behavior, input event construction, S23 `IVirtualInputDevice` operations, and task snapshot logic.

- [ ] **Step 5: Centralize remote cleanup**

Use one cleanup state in `EmbeddedAppVdm`/UserService. Close input at most once, verify descriptor removal while device remains alive, remove only the matched new task, close the VirtualDevice once, then let UserService disassociate once and clear references. Continue later cleanup steps after recording the first failure.

- [ ] **Step 6: Implement the generic UserService**

Validate UID `2000`, target fields, geometry, Surface validity, and single-session state. Start association → VDM/display → touchscreen → settled finger config → explicit target launch. Route every failure and `destroy()` through the same cleanup path.

- [ ] **Step 7: Run generic model and remote-policy tests**

Run: `./gradlew.bat testDebugUnitTest --tests "*.EmbeddedApp*Test"`

Expected: PASS.

- [ ] **Step 8: Confirm generic runtime contains no Waze constants**

Run: `rg -n "com\.waze|FreeMapAppActivity|MainActivity" app/src/main/aidl/com/trancong/dexworkspacetouch/feature/embeddedapp app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedapp`

Expected: no matches.

### Task 3: Generic app-side session

**Files:**
- Create: `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedapp/EmbeddedAppSession.kt`
- Test: `app/src/test/java/com/trancong/dexworkspacetouch/feature/embeddedapp/EmbeddedAppSessionPolicyTest.kt`
- Remove after migration: `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedwaze/EmbeddedWazeSession.kt`

**Interfaces:**
- Consumes: `EmbeddedAppTarget` and `IEmbeddedAppService`.
- Produces: `EmbeddedAppState`; `EmbeddedAppSession.start()`, `connect()`, `startSession(Surface)`, `touch(...)`, `stop()`, and `close()`.

- [ ] **Step 1: Write failing session-policy tests**

Test that start requires a valid Surface and shell UID `2000`, active state begins only after remote success, repeated stop/close requests converge without scheduling duplicate remote cleanup, and disposal while active requests the same stop path.

- [ ] **Step 2: Run the session tests and verify RED**

Run: `./gradlew.bat testDebugUnitTest --tests "*.EmbeddedAppSessionPolicyTest"`

Expected: FAIL because generic session policy does not exist.

- [ ] **Step 3: Extract the app-side session**

Move the current binding/state logic to `EmbeddedAppSession`, parameterize it with `EmbeddedAppTarget`, bind `EmbeddedAppUserService`, and pass target/geometry through AIDL. Use one serialized stop/close guard so every lifecycle path converges without duplicate remote stop.

- [ ] **Step 4: Run the session tests and generic suite**

Run: `./gradlew.bat testDebugUnitTest --tests "*.EmbeddedApp*Test"`

Expected: PASS.

### Task 4: Waze thin adapter and route preservation

**Files:**
- Create: `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedwaze/WazeEmbeddedTarget.kt`
- Modify: `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedwaze/EmbeddedWazeScreen.kt`
- Test: `app/src/test/java/com/trancong/dexworkspacetouch/feature/embeddedwaze/WazeEmbeddedTargetTest.kt`
- Modify only if imports require it: `app/src/main/java/com/trancong/dexworkspacetouch/navigation/TouchNavigation.kt`

**Interfaces:**
- Consumes: `EmbeddedAppTarget`, `EmbeddedAppSession`, mapping and pressure helpers.
- Produces: `WAZE_EMBEDDED_TARGET` with the proven explicit launch component and `900 x 675 @ 320 dpi`.

- [ ] **Step 1: Write the failing Waze configuration test**

Assert the exact package/component already used by DWT-VDM-001 and geometry `900`, `675`, `320`.

- [ ] **Step 2: Run the Waze test and verify RED**

Run: `./gradlew.bat testDebugUnitTest --tests "*.WazeEmbeddedTargetTest"`

Expected: FAIL because the thin configuration does not exist.

- [ ] **Step 3: Implement Waze configuration and delegate the screen**

Keep the route title and controls unchanged. Use the target geometry for `SurfaceHolder.setFixedSize`, instantiate the generic session with the Waze target, and use generic mapping/action/pressure helpers in the touch listener.

- [ ] **Step 4: Remove obsolete Waze runtime files**

Delete the migrated Waze models, session, AIDL, and remote implementation. Confirm `feature/embeddedwaze` contains only screen/configuration and Waze-specific tests.

- [ ] **Step 5: Run all focused tests**

Run: `./gradlew.bat testDebugUnitTest --tests "*embeddedapp*" --tests "*embeddedwaze*"`

Expected: PASS.

### Task 5: Static architecture and build verification

**Files:**
- Modify only objective compile/lint issues in DWT-VDM-002 files.
- Do not change `scripts/build-device-smoke.ps1` unless an objective refactor-induced build-path issue requires it.

**Interfaces:**
- Consumes: completed generic runtime and Waze adapter.
- Produces: buildable production-config smoke APK with unchanged package, version, signing, and licensing configuration.

- [ ] **Step 1: Verify single implementations and package boundaries**

Run targeted `rg` searches for `createVirtualDevice`, `createVirtualTouchscreen`, `sendTouchEvent`, `IVirtualDevice.close`, association commands, and Waze constants. Expected: each runtime primitive has one implementation under `feature/embeddedapp`; Waze constants exist only under `feature/embeddedwaze`.

- [ ] **Step 2: Run required Gradle gates**

Run sequentially:

```powershell
./gradlew.bat testDebugUnitTest
./gradlew.bat lintDebug
./gradlew.bat assembleDebug
```

Expected: all PASS.

- [ ] **Step 3: Run diff validation**

Run: `git diff --check`

Expected: no errors.

- [ ] **Step 4: Build the production-config smoke APK**

Run: `./scripts/build-device-smoke.ps1`

Expected: PASS with application ID `com.trancong.dexworkspacetouch` and the existing production signer; no version changes and no publication.

### Task 6: One S23 regression smoke

**Files:**
- No source changes unless the first blocker is an objective DWT-VDM-002 regression.
- Runtime evidence may be stored only in ignored/local diagnostic paths.

**Interfaces:**
- Consumes: production-config smoke APK from Task 5.
- Produces: device evidence for the final PASS/BLOCKED verdict.

- [ ] **Step 1: Verify device identity and install without data loss**

Confirm `SM-S918B / dm3q / S918BXXSAFZH3 / Android 16 / API 36`, verify APK certificate, then use `adb install -r`. Do not uninstall or clear data.

- [ ] **Step 2: Run exactly one Waze regression session**

Navigate Home → Embedded Waze (Experimental), connect Shizuku, and start once. Verify SurfaceView rendering, Waze on a `900 x 675` trusted VDM display, and one virtual touchscreen on that display.

- [ ] **Step 3: Verify input semantics**

Send one interaction sequence through the SurfaceView that exercises DOWN/MOVE/UP and one CANCEL path. Confirm map response, DOWN pressure `255f`, CANCEL PALM semantics, no exception, and no focus workaround.

- [ ] **Step 4: Stop and verify cleanup**

Stop once. Confirm input close/removal while VDM lives, exact new Waze task removal, one VirtualDevice close, one association removal, and no orphan input/display/device/task.

- [ ] **Step 5: Stop at the first blocker or record PASS**

Do not retry or investigate a different architecture. Capture only minimum evidence for a blocker.

### Task 7: Final diff review, scoped commit, and report

**Files:**
- Include only DWT-VDM-002 source, test, AIDL, design, and plan files.
- Exclude all preserved unrelated working-tree files.

- [ ] **Step 1: Inspect final working diff**

Run:

```powershell
git status --short
git diff --check
git diff --stat
git diff
```

Expected: only planned DWT-VDM-002 changes plus clearly identified preserved unrelated files.

- [ ] **Step 2: Stage only DWT-VDM-002 files**

Do not stage `experiments/shizuku-task-probe`, COMP-012R, `AGENTS.md`, build outputs, smoke APKs, or device logs.

- [ ] **Step 3: Verify staged diff**

Run:

```powershell
git diff --cached --check
git diff --cached --stat
git diff --cached
```

Expected: clean scoped diff with no unrelated work.

- [ ] **Step 4: Commit after full PASS only**

Run: `git commit -m "refactor: generalize embedded app VDM runtime"`

- [ ] **Step 5: Report final state**

Report architecture split, behavior and cleanup evidence, test/build/device results, exact files, commit SHA, preserved unrelated files, and `Push: NO`.
