# DWT-VDM-005 Multi-Session Ownership and Lifecycle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Permit Waze and Samsung Calculator to run concurrently as two isolated `EmbeddedAppSession` instances through one shared Shizuku UserService, then prove that stopping either session cannot affect the other.

**Architecture:** A process-level connection manager owns one Shizuku binding and reference-counted client leases. The remote service owns a session registry keyed by opaque UUIDs; every entry has its own runtime, association, VirtualDevice, display, input device, task receipt, Surface copy, lock, and cleanup progress. Final service removal requires both zero local leases and an authoritative remote `getServiceState()` result proving zero live or potentially-live resources.

**Tech Stack:** Kotlin, AIDL, Shizuku 13.1.5, Samsung Android 16 hidden VDM/input APIs, Jetpack Compose, `SurfaceView`, JUnit 4, Gradle, PowerShell, ADB.

**Spec:** `docs/superpowers/specs/2026-09-28-dwt-vdm-005-multi-session-ownership-lifecycle-design.md`

## Global Constraints

- Preserve the target-independent and Workspace-independent boundary of `feature/embeddedapp`; do not import `EmbeddedWorkspacePlan`, `WorkspaceRunPlanner`, `WorkspaceRunMode`, `NormalizedBounds`, Room, or Workspace placement models.
- Session identity is an app-generated opaque UUID and is never derived from package/component identity.
- Use one shared Shizuku UserService and one process-level binding manager; do not create one service/tag/process per session.
- Each session separately owns association, VirtualDevice, trusted display, touchscreen, target task, Surface copy, state, and cleanup progress.
- Keep `900 x 675 @ 320 dpi`, trusted VDM, touchscreen-before-launch, DOWN pressure `255f`, MOVE/UP/CANCEL behavior, exact task matching, and cleanup order from the proven runtime.
- Cleanup order remains input, exact task, VirtualDevice/display, exact association, references.
- Final `remove=true` must never rely only on local lease state. It requires a successful authoritative remote service-state query proving no active, starting, stopping, cleanup-incomplete, or live-resource session.
- `connect()` must acquire/own the session's manager lease before participating in the shared bind; `start()` reuses that lease and never creates binding ownership. A connected-but-never-started session releases the same lease on `close()`.
- A failed state query, uncertain/dead binder, or non-empty remote result fails closed and must not remove the UserService.
- Do not implement auto-recovery after binder death, parallel startup proof, same-target duplicates, arbitrary-N claims, Workspace execution, dynamic geometry, persistence, or production UX.
- Preserve existing Waze and Calculator routes as thin consumers; add only their mechanical migration to the shared manager/session-ID contract.
- Preserve unrelated work under `experiments/shizuku-task-probe/...`, `docs/compat/COMP-012R_HISTORY_ASSISTED_REUSE_DESIGN.md`, `AGENTS.md`, and unrelated `.superpowers` content.
- Do not change version name/code, application ID, licensing, release outputs, Classic Workspace behavior, or `scripts/build-device-smoke.ps1` unless an objective build blocker requires it.
- Run exactly one dual-session smoke on a dynamically resolved `SM-S918B / dm3q / S918BXXSAFZH3 / Android 16 / API 36` endpoint after all local gates pass. Do not hard-code an ADB serial.
- Do not commit until implementation, verification, the bounded device proof, cleanup audit, and scoped review are complete and reviewed. Do not push.

## Review Focus

- **Remote/local lifetime disagreement:** zero local leases with a failed or non-empty `getServiceState()` must retain the UserService; Task 4 tests all fail-closed branches.
- **Partial B startup:** failure after each resource boundary must clean only B's recorded resources while A remains active; Task 2 exercises every boundary.
- **Concurrent per-session work:** B's slow startup/configuration wait must not block A touch, while operations for the same ID remain ordered; Tasks 2 and 3 test independent serialization.
- **Stale or wrong identity:** unknown IDs and duplicate active IDs must never fall back to another runtime or allocate resources; Tasks 1 and 3 pin this behavior.
- **Binder death:** every live client must become `REMOTE_DIED`, new remote operations must stop, and service removal must remain unauthorized until authoritative reconciliation; Task 4 tests the fan-out and fail-closed state.

---

### Task 1: Define session identity and authoritative lifecycle models

**Files:**
- Modify: `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedapp/EmbeddedAppModels.kt`
- Create: `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedapp/EmbeddedAppSessionState.kt`
- Create: `app/src/test/java/com/trancong/dexworkspacetouch/feature/embeddedapp/EmbeddedAppSessionStateTest.kt`

**Interfaces:**
- Produces: `@JvmInline value class EmbeddedAppSessionId(val value: String)`, `fun newEmbeddedAppSessionId(): EmbeddedAppSessionId`, `enum class RemoteSessionPhase`, `data class EmbeddedAppServiceState`, and pure removal predicate `canRemoveEmbeddedAppService(...)`.
- `EmbeddedAppServiceState` contains `activeSessionCount`, `startingSessionCount`, `stoppingSessionCount`, and `liveResourceSessionCount`; all are non-negative and `provesEmpty` is true only when all are zero.

- [ ] **Step 1: Write failing model tests**

Add tests that assert generated IDs are non-blank and distinct, target identity does not affect IDs, negative service counts are rejected, `provesEmpty` requires all four counts to be zero, and removal is false for zero leases when query failed, binder is uncertain, work is in flight, local sessions are non-terminal, or any remote count is non-zero.

- [ ] **Step 2: Run the focused tests and observe RED**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "*EmbeddedAppSessionStateTest" --console=plain
```

Expected: FAIL because the new types/functions do not exist.

- [ ] **Step 3: Implement the minimal pure models**

Keep UUID creation app-side and keep Android/Binder/Shizuku types out of this file. Make the removal predicate consume explicit local and authoritative-remote facts so callers cannot authorize removal from lease count alone.

- [ ] **Step 4: Run focused tests and observe GREEN**

Run the Step 2 command. Expected: PASS.

### Task 2: Split the remote VDM singleton into per-session resource ownership

**Files:**
- Replace responsibility in: `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedapp/remote/EmbeddedAppVdm.kt`
- Create: `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedapp/remote/RemoteSessionRuntime.kt`
- Create: `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedapp/remote/RemoteSessionRegistry.kt`
- Modify: `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedapp/remote/AssociationShell.kt`
- Modify: `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedapp/EmbeddedAppRemotePolicy.kt`
- Create: `app/src/test/java/com/trancong/dexworkspacetouch/feature/embeddedapp/RemoteSessionRegistryTest.kt`
- Modify: `app/src/test/java/com/trancong/dexworkspacetouch/feature/embeddedapp/EmbeddedAppRemotePolicyTest.kt`

**Interfaces:**
- Consumes: `EmbeddedAppSessionId`, `RemoteSessionPhase`, `EmbeddedAppServiceState`.
- Produces: `RemoteSessionRegistry.reserve/get/stop/remove/serviceState`; one `RemoteSessionRuntime` per ID; narrow resource-operation interfaces/fakes used by pure ownership tests.

- [ ] **Step 1: Write failing registry and ownership tests**

Cover two IDs reaching ACTIVE with separate ownership receipts, duplicate ID rejection before allocation, unknown ID touch/stop failure, touch routing to only the selected input, stop(B) leaving A unchanged, repeated stop idempotence, cleanup(B) never advancing A, and aggregate service state counting retained resources as live.

- [ ] **Step 2: Add boundary-by-boundary failure tests**

Parameterize B failure after association, device, display, input, launch, and task selection. Assert only already-recorded B resources clean in the proven order and A's receipt/state/counters do not change.

- [ ] **Step 3: Run focused tests and observe RED**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*RemoteSessionRegistryTest" --tests "*EmbeddedAppRemotePolicyTest" --console=plain
```

Expected: FAIL because registry/per-session runtime APIs do not exist.

- [ ] **Step 4: Implement the registry and per-session runtime**

Move all mutable `EmbeddedAppVdm` ownership fields into `RemoteSessionRuntime`. Keep reflection/task/shell helpers stateless. Use a short registry lock only for reserve/lookup/remove/snapshot; serialize slow work with a lock/queue owned by the selected runtime. Serialize only association-list mutation in `AssociationShell`.

- [ ] **Step 5: Preserve exact startup and cleanup semantics**

Record each resource immediately after creation. Preserve task snapshot plus exact component/display selection, input configuration gate, and cleanup order. A failed cleanup keeps the entry/resource count live; do not clear receipts that are still needed for scoped cleanup.

- [ ] **Step 6: Run focused tests and observe GREEN**

Run the Step 3 command. Expected: PASS.

### Task 3: Make the AIDL UserService registry session-scoped

**Files:**
- Modify: `app/src/main/aidl/com/trancong/dexworkspacetouch/feature/embeddedapp/remote/IEmbeddedAppService.aidl`
- Modify: `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedapp/remote/EmbeddedAppUserService.kt`
- Create: `app/src/test/java/com/trancong/dexworkspacetouch/feature/embeddedapp/EmbeddedAppUserServiceContractTest.kt`

**Interfaces:**
- Produces AIDL methods: `startSession(String sessionId, in Surface surface, ...)`, `sendTouch(String sessionId, ...)`, `stopSession(String sessionId)`, `getSessionState(String sessionId)`, `getServiceState()`, and lifecycle `destroy()`.
- Every session result includes `sessionId`, `success`, stable failure code, and applicable ownership IDs. `getServiceState()` returns only authoritative aggregate counts.

- [ ] **Step 1: Write failing contract tests**

Test malformed/unknown/stale IDs, duplicate active start, correct ID echo, no current-session fallback, two IDs active together, concurrent B startup not blocking A touch, idempotent stop, and aggregate state before/during/after cleanup.

- [ ] **Step 2: Run focused tests and observe RED**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*EmbeddedAppUserServiceContractTest" --console=plain
```

Expected: FAIL against the old global AIDL contract.

- [ ] **Step 3: Update AIDL and delegate by session ID**

Validate UID and ID before registry access. Return typed failures rather than throwing across Binder for expected state errors. `destroy()` snapshots and best-effort stops every remaining runtime; no ordinary session call invokes it.

- [ ] **Step 4: Implement authoritative `getServiceState()`**

Build its `Bundle` from one registry snapshot and include the four required counts. Do not expose session IDs or accept client-supplied emptiness claims.

- [ ] **Step 5: Run contract and remote-policy tests**

Run Task 2 Step 3 plus Task 3 Step 2. Expected: PASS.

### Task 4: Centralize the app-side Shizuku binding and fail-closed final removal

**Files:**
- Create: `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedapp/EmbeddedAppServiceConnectionManager.kt`
- Create: `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedapp/EmbeddedAppServiceLease.kt`
- Modify: `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedapp/EmbeddedAppSession.kt`
- Create: `app/src/test/java/com/trancong/dexworkspacetouch/feature/embeddedapp/EmbeddedAppServiceConnectionManagerTest.kt`
- Modify: `app/src/test/java/com/trancong/dexworkspacetouch/feature/embeddedapp/EmbeddedAppSessionPolicyTest.kt`

**Interfaces:**
- Produces: one process-level manager with `acquire(sessionId, listener): EmbeddedAppServiceLease`; one cached binder/bind operation requested only through an owned lease; per-lease release; remote-death fan-out; final-removal decision based on `getServiceState()`.
- `EmbeddedAppSession` owns one generated ID and one ordered worker. Its first `connect()` lazily acquires the lease before bind participation; `start()` reuses it, every remote operation sends the ID, and `close()` stops the ID if needed before releasing the same lease. It never directly binds/unbinds/destroys the service.

- [ ] **Step 1: Write failing connection-manager tests**

Assert `connect()` acquires before bind, two connected sessions cause one bind and two leases, closing never-started B leaves one lease/service for A, closing a connected-but-never-started final session returns leases to zero and queries remote state, empty authoritative state permits exactly one `remove=true`, and query exception, dead/uncertain binder, in-flight work, local non-terminal state, or any remote count blocks removal.

- [ ] **Step 2: Write failing session and binder-death tests**

Assert each session forwards only its ID, preserves touch order, close is idempotent, Surface destruction stops only its owner, and binder death marks all live sessions `REMOTE_DIED`, rejects new calls, retains receipts, and never claims cleanup/removal.

- [ ] **Step 3: Run focused tests and observe RED**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*EmbeddedAppServiceConnectionManagerTest" --tests "*EmbeddedAppSessionPolicyTest" --console=plain
```

- [ ] **Step 4: Implement the shared manager and leases**

Centralize the exact existing `UserServiceArgs`, Shizuku listeners, permission/bind convergence, binder storage, and death handling. Require a live lease token for bind participation. Serialize only manager bind/lease bookkeeping. Keep session workers independent.

- [ ] **Step 5: Migrate `EmbeddedAppSession` mechanically**

Generate the ID at construction, lazily acquire exactly one lease on first `connect()`, reuse it in `start()`, pass the ID through start/touch/stop, release it even when never started, and retain immutable start receipts for diagnostics. Do not add target-specific or Workspace logic.

- [ ] **Step 6: Run focused tests and observe GREEN**

Run the Step 3 command. Expected: PASS.

### Task 5: Preserve existing adapters and add the two-pane proof harness

**Files:**
- Modify: `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedwaze/EmbeddedWazeScreen.kt`
- Modify: `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedcalculator/EmbeddedCalculatorScreen.kt`
- Create: `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddeddual/EmbeddedDualAppScreen.kt`
- Create: `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddeddual/EmbeddedDualAppController.kt`
- Create: `app/src/test/java/com/trancong/dexworkspacetouch/feature/embeddeddual/EmbeddedDualAppControllerTest.kt`
- Modify: `app/src/main/java/com/trancong/dexworkspacetouch/navigation/TouchNavigation.kt`
- Modify: `app/src/main/java/com/trancong/dexworkspacetouch/ui/screens/HomeScreen.kt`

**Interfaces:**
- Produces: route `embedded-dual-app`, Home action `Embedded Dual App (Experimental)`, and a proof-only controller composing exactly two ordinary sessions.
- The controller contains no registry/runtime implementation; it starts/stops Waze and Calculator independently and surfaces each session's state/IDs.

- [ ] **Step 1: Write failing proof-controller tests**

Assert A then B startup, independent state reporting, stop(B) leaving A active, reverse stop order in pure tests, partial B failure leaving A active, and idempotent disposal. Use fake sessions; do not encode a two-entry limit in generic code.

- [ ] **Step 2: Run the focused test and observe RED**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*EmbeddedDualAppControllerTest" --console=plain
```

- [ ] **Step 3: Mechanically migrate existing screens**

Keep their targets, geometry, mapping, controls, and lifecycle. Replace only assumptions made obsolete by the shared manager/session-scoped API.

- [ ] **Step 4: Add the proof screen and navigation**

Give each pane its own `SurfaceView` and `EmbeddedAppSession`, status/display IDs, Start, and Stop. Layout is diagnostic and fixed; do not consume Workspace plans/bounds or extract a Workspace renderer.

- [ ] **Step 5: Run focused and existing adapter tests**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*EmbeddedDualAppControllerTest" --tests "*EmbeddedApp*" --tests "*WazeEmbeddedTargetTest" --tests "*CalculatorEmbeddedTargetTest" --console=plain
```

Expected: PASS.

### Task 6: Run full local, boundary, signing, and device preflight gates

**Files:**
- No planned source changes.
- Generated artifacts remain untracked and outside any future commit.

- [ ] **Step 1: Run local verification**

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug lintDebug --console=plain
git diff --check
```

Expected: `BUILD SUCCESSFUL`, zero lint errors, diff check exit 0.

- [ ] **Step 2: Run architecture boundary scans**

Require no Workspace/planning/persistence imports in `feature/embeddedapp`, no Waze/Calculator package constants in generic runtime, no global mutable `currentDevice/currentTask/currentInput/currentAssociation`, no direct per-screen Shizuku bind/unbind, and exactly one final manager-owned `remove=true` path guarded by the remote state query.

- [ ] **Step 3: Build and verify the production-config smoke APK**

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\build-device-smoke.ps1
```

Require signer SHA-256 `19ac0ea99125361b3c2083aaa44c8745ebad9c55fa967642c2b9e5a1086a45e7`; do not change version values or release outputs.

- [ ] **Step 4: Resolve and pin the S23 endpoint**

Enumerate `adb devices -l`; query each online endpoint for model/device/build/API. Select one endpoint proving `SM-S918B / dm3q / S918BXXSAFZH3 / Android 16 / API 36`, store it for the session, and use `adb -s $dev` consistently. Multiple endpoints for the same phone are not separate devices; ambiguous distinct matching devices require STOP.

- [ ] **Step 5: Verify targets, baseline, and install safely**

Require the approved Waze and Calculator components to resolve. Record DWT host display, associations, VirtualDevices, displays, virtual inputs, UserService state, and all existing target task IDs. Install only with `adb -s $dev install -r <smoke.apk>`; never uninstall or clear data.

### Task 7: Run exactly one dual-session S23 proof and final scoped review

**Files:**
- No source changes during the device proof.
- Captures remain untracked verification artifacts.

- [ ] **Step 1: Start A then B and prove coexistence**

Open `Embedded Dual App (Experimental)`. Start Waze A once, then Calculator B once. Require two ACTIVE session IDs, distinct trusted displays/inputs/tasks/associations, both `900 x 675 @ 320 dpi`, visible pixels in both panes, and DWT remaining on its pre-start host display.

- [ ] **Step 2: Prove input isolation**

Interact once with Waze and observe only Waze change. Enter `7 x 8 = 56` in Calculator and observe only Calculator change. Send one bounded CANCEL through one pane. Require correct display/input routing, no hover/drop/task move, and no effect on the other pane.

- [ ] **Step 3: Stop B while A remains live**

Stop Calculator B once. Verify only B input/task/device/display/association disappear. Require A to retain the same session/display/input/task, remain rendered, and respond to one post-B-cleanup Waze interaction. Confirm the shared UserService was not removed.

- [ ] **Step 4: Stop A and prove authoritative final teardown**

Stop Waze A once. Capture the final `getServiceState()` result proving all four counts zero before the single `remove=true`. Verify no probe association, VirtualDevice, display, touchscreen, Waze probe task, Calculator probe task, or shared UserService remains; preserve every baseline resource/task.

- [ ] **Step 5: Stop at the first real blocker**

No retry, reverse-order device run, alternate target, focus workaround, topology change, or second smoke. If authoritative emptiness cannot be proven, leave the service unremoved and report cleanup/removal blocked rather than forcing it.

- [ ] **Step 6: Inspect the complete scoped diff**

Run `git status --short`, `git diff --check`, complete diff review for all 005 files, focused Workspace-boundary scans, and `git diff --stat`. Confirm protected pre-existing files and generated artifacts remain unstaged/unmodified by 005.

- [ ] **Step 7: Prepare the result for review without committing**

Report test/build/signing/device evidence, per-session ownership IDs, stop(B)-while-A-active evidence, authoritative final-state proof, cleanup baseline, exact changed files, and deliberately untouched/uncommitted files. Do not commit or push until the human reviews the final result.

## Self-review result

- Every spec requirement maps to Tasks 1-7, including the authoritative remote-state amendment and fail-closed final removal.
- The AIDL names, session-ID flow, service-state fields, registry/runtime ownership, connection leases, binder-death state, and proof route are consistent across tasks.
- The five Review Focus risks each have an explicit focused test or device gate.
- The plan keeps the single device run bounded while using pure tests for reverse stop order and partial-start failures.
- The plan does not introduce Workspace execution, same-target claims, arbitrary-N claims, recovery architecture, or a second persistence model.
