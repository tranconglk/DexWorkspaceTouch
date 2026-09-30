# DWT-VDM-006 EmbeddedWorkspaceRunner Orchestration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Execute one real `EmbeddedWorkspacePlan` as a transactionally owned set of sequential, isolated `EmbeddedAppSession` instances without implementing Workspace layout.

**Architecture:** Add a runner under `workspace/execution/embedded/runtime` that receives a real plan plus source-keyed externally owned execution surfaces. It preflights the complete request, constructs target-only session inputs through an injected geometry policy, starts sessions sequentially, routes touch by source ID, and converges stop/rollback through bounded reverse-order cleanup. A narrow target-independent lifecycle extension in `feature/embeddedapp` gives the runner structured READY/ACTIVE/terminal evidence without leaking Workspace types into the generic runtime.

**Tech Stack:** Kotlin, Android `Surface`/`SurfaceView`, Compose proof UI, coroutines with `kotlinx-coroutines-test`, Shizuku UserService, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-09-28-dwt-vdm-006-embedded-workspace-runner-design.md`

## Global Constraints

- Baseline is `3d99f67fd0b9ee352be78e401cc3ebeb6ddba26d` on `release/1.0-beta`.
- `feature/embeddedapp` must not depend on Workspace, `sourceCellId`, `NormalizedBounds`, Room, persistence, Classic execution, Compose, or Workspace UI.
- `EmbeddedAppTarget` remains package name, component name, and `EmbeddedAppGeometry` only.
- Do not modify DWT-VDM-004 planning semantics or the Classic launcher.
- The runner receives host surfaces from outside and never creates Compose UI or `SurfaceView` widgets.
- Preserve `normalizedBounds` as plan/result metadata; do not convert it to pixels or use it for geometry/layout.
- Reject duplicate package/component identities before creating a session.
- Start sequentially by ascending `EmbeddedWorkspacePlanItem.order`; do not add parallel startup.
- Marshal session observer callbacks, start, stop, surface-loss, timeout, and every runner state transition onto one runner-owned orchestration queue before reading or mutating runner state.
- Re-check the current item's Surface immediately before calling session start.
- Cleanup always calls `stop()`, then idempotent `close()`, then awaits terminal state in reverse creation order.
- `cleanupTimeout` bounds the complete `stop() -> close() -> terminal` convergence. Session-port `stop()` and `close()` are non-blocking command submissions; no blocking join may escape the deadline.
- The only clean terminal session state is `STOPPED`; `CLEANUP_INCOMPLETE` and `REMOTE_DIED` must remain distinct.
- Inject `readyTimeout=10s`, `activeTimeout=30s`, and `cleanupTimeout=20s`; no timing literal belongs in runner logic and unit tests use virtual time rather than sleep.
- Touch is routed `sourceCellId -> runner-owned item -> session`; reject unknown and non-ACTIVE source IDs.
- Never enumerate or clean global remote sessions, reset/destroy the UserService, force-stop an app, retry startup, or touch an unrelated external session.
- Proof geometry is `900 x 675 @ 320 dpi` through an injected proof policy.
- Resolve the S23 ADB endpoint dynamically and require `SM-S918B / dm3q / Android 16 / API 36` before the one bounded device smoke.
- Preserve and do not stage unrelated work in `experiments/shizuku-task-probe/...`, `docs/compat/COMP-012R_HISTORY_ASSISTED_REUSE_DESIGN.md`, `AGENTS.md`, `.superpowers/`, and `verification/`.
- Do not modify version values, licensing, release outputs, persistence, saved run mode, or production Workspace UI.
- Do not commit or push until implementation, local gates, the one device smoke, scoped diff review, and human review are complete.

## Review Focus

- **Synchronous callback reentrancy:** READY or terminal callbacks may arrive during the initiating call; state must not regress or lose the event. Task 1 adds transition-history tests and Task 3 tests synchronous fake callbacks.
- **Timeout/callback race:** session callbacks and timeout events are marshalled onto the same orchestration queue; a callback arriving at the same logical deadline must produce one outcome and one cleanup path. Task 3 uses a virtual scheduler to test both queue orderings.
- **Stop/surface-loss convergence:** explicit stop and surface loss may race while an item starts; exactly one reverse cleanup operation must win. Task 3 tests both event orders.
- **Partial handle allocation:** factory creation or connect may fail before a session reaches ACTIVE; every created handle must still receive stop/close while later items remain uncreated. Task 3 covers each boundary.
- **External lease coexistence:** final runner stop must not imply service removal when an unrelated embedded session remains leased. Task 4 adapter tests and Task 6 device/state audit preserve manager ownership semantics.

---

### Task 1: Add a structured target-independent session lifecycle contract

**Files:**
- Modify: `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedapp/EmbeddedAppSessionState.kt`
- Modify: `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedapp/EmbeddedAppSession.kt`
- Create: `app/src/test/java/com/trancong/dexworkspacetouch/feature/embeddedapp/EmbeddedAppLifecycleCoordinatorTest.kt`
- Modify: existing EmbeddedApp session/connection tests as required by the new structured state

**Interfaces:**
- Produces: `EmbeddedSessionPhase`, `EmbeddedSessionFailure`, `EmbeddedSessionSnapshot`, `EmbeddedTouchEvent`, and a structured observer from `EmbeddedAppSession`.
- Produces semantics: `stop()` starts/joins scoped cleanup; `close()` joins the same cleanup and releases the lease once; terminal callback occurs after cleanup and lease release.
- Consumes: existing `EmbeddedAppSessionId`, connection manager lease, session worker, and remote session-scoped AIDL calls.

- [ ] **Step 1: Write lifecycle coordinator tests**

Add tests named for these assertions:

- `synchronous ready during connect remains READY`
- `successful start publishes STARTING then ACTIVE with display receipt`
- `start failure publishes FAILED with a stable failure value`
- `stop then close emits one STOPPING and one STOPPED after lease release`
- `close during an existing stop does not issue a second remote stop`
- `cleanup failure emits CLEANUP_INCOMPLETE`
- `binder death emits REMOTE_DIED and never STOPPED`
- `terminal callback delivered synchronously is retained`

- [ ] **Step 2: Run the focused tests and observe RED**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "*EmbeddedAppLifecycleCoordinatorTest" --console=plain
```

Expected: FAIL because the structured lifecycle types/coordinator do not exist.

- [ ] **Step 3: Define the public target-independent values**

In `EmbeddedAppSessionState.kt`, add exact contracts equivalent to:

```kotlin
enum class EmbeddedSessionPhase {
    IDLE, CONNECTING, READY, STARTING, ACTIVE,
    STOPPING, STOPPED, FAILED, CLEANUP_INCOMPLETE, REMOTE_DIED,
}

data class EmbeddedSessionFailure(val code: String, val message: String?)

data class EmbeddedSessionSnapshot(
    val phase: EmbeddedSessionPhase,
    val displayId: Int = -1,
    val failure: EmbeddedSessionFailure? = null,
)

data class EmbeddedTouchEvent(
    val action: Int,
    val x: Float,
    val y: Float,
    val pressure: Float,
    val eventTimeNanos: Long,
)
```

Keep these types free of Android UI and Workspace types.

- [ ] **Step 4: Implement the lifecycle transition coordinator**

Add a small pure coordinator that rejects state regression, deduplicates stop/close, and records one terminal result. `READY -> CONNECTING`, terminal -> non-terminal, and `STOPPED` before lease release are forbidden.

- [ ] **Step 5: Adapt `EmbeddedAppSession` to publish structured snapshots**

Preserve its human-readable `EmbeddedAppState` callback for existing screens, or derive it from structured state. Add a structured observer/constructor parameter used by the future factory. Implement `sendTouch(event: EmbeddedTouchEvent)` as the typed equivalent of the current raw touch call.

Make `close()` converge with an existing stop on the session worker and publish terminal state only after the lease has been released. Do not change AIDL topology or remote resource ownership.

- [ ] **Step 6: Run focused and existing embedded-session tests**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*EmbeddedAppLifecycleCoordinatorTest" --tests "*EmbeddedAppSessionStateTest" --tests "*EmbeddedAppServiceConnectionManagerTest" --tests "*SessionConnectionCoordinatorTest" --tests "*SessionStopCoordinatorTest" --console=plain
```

Expected: PASS.

### Task 2: Define runner inputs, preflight, geometry, timeout, and result models

**Files:**
- Create: `app/src/main/java/com/trancong/dexworkspacetouch/workspace/execution/embedded/runtime/EmbeddedWorkspaceRuntimeModels.kt`
- Create: `app/src/main/java/com/trancong/dexworkspacetouch/workspace/execution/embedded/runtime/EmbeddedWorkspacePreflight.kt`
- Create: `app/src/test/java/com/trancong/dexworkspacetouch/workspace/execution/embedded/runtime/EmbeddedWorkspacePreflightTest.kt`

**Interfaces:**
- Consumes: `EmbeddedWorkspacePlan`, `EmbeddedWorkspacePlanItem`, `EmbeddedAppTarget`, and `EmbeddedAppGeometry`.
- Produces: execution request/host-slot/surface abstractions, `EmbeddedGeometryPolicy`, `EmbeddedWorkspaceRunnerTimeoutPolicy`, preflight result/rejection types, runner state/result/receipt/cleanup models.

- [ ] **Step 1: Write preflight tests**

Cover:

- exact plan/slot set passes and returns items sorted by order
- missing slot rejects before geometry/factory work
- extra or unknown slot rejects
- duplicate host-slot source ID rejects
- invalid execution surface rejects
- duplicate package/component identity rejects while the pure plan remains valid
- geometry exception/invalid geometry rejects before allocation
- constructed targets contain only package/component/policy geometry
- normalized bounds remain equal in prepared/result metadata and do not affect geometry

Every rejection test asserts the session-factory invocation count remains zero.

- [ ] **Step 2: Run the focused test and observe RED**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*EmbeddedWorkspacePreflightTest" --console=plain
```

Expected: FAIL because runtime request and preflight types do not exist.

- [ ] **Step 3: Implement host-slot and policy contracts**

Define:

```kotlin
interface EmbeddedWorkspaceExecutionSurface { val isValid: Boolean }
data class EmbeddedWorkspaceHostSlot(
    val sourceCellId: String,
    val executionSurface: EmbeddedWorkspaceExecutionSurface,
)
data class EmbeddedWorkspaceExecutionRequest(
    val plan: EmbeddedWorkspacePlan,
    val hostSlots: List<EmbeddedWorkspaceHostSlot>,
)
fun interface EmbeddedGeometryPolicy {
    fun geometryFor(item: EmbeddedWorkspacePlanItem): EmbeddedAppGeometry
}
data class EmbeddedWorkspaceRunnerTimeoutPolicy(
    val readyTimeout: Duration,
    val activeTimeout: Duration,
    val cleanupTimeout: Duration,
)
```

Expose `PROOF_EMBEDDED_WORKSPACE_TIMEOUTS` with `10.seconds`, `30.seconds`, and `20.seconds`; runner logic consumes the policy and contains no numeric timing values.

- [ ] **Step 4: Implement typed preflight and result models**

Use explicit sealed results for `Prepared` and rejection causes (`MissingSlot`, `UnknownSlot`, `DuplicateSlot`, `InvalidSurface`, `DuplicateTargetIdentity`, `GeometryRejected`, `InvalidPlan`). Define runner phases and run results from the spec, plus ordered item receipts and separate rollback outcomes.

- [ ] **Step 5: Implement preflight**

Validate the entire request and resolve all geometry before invoking any session factory. Key slots by `sourceCellId`, compare exact sets, reject duplicates, then return ordered prepared items containing plan metadata, target, and execution surface.

- [ ] **Step 6: Run focused and DWT-VDM-004 planner tests**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*EmbeddedWorkspacePreflightTest" --tests "*EmbeddedWorkspacePlannerTest" --tests "*WorkspaceRunPlannerTest" --console=plain
```

Expected: PASS with unchanged Classic/pure planning behavior.

### Task 3: Implement sequential orchestration, bounded rollback, touch routing, and surface-loss convergence

**Files:**
- Create: `app/src/main/java/com/trancong/dexworkspacetouch/workspace/execution/embedded/runtime/EmbeddedWorkspaceSessionPort.kt`
- Create: `app/src/main/java/com/trancong/dexworkspacetouch/workspace/execution/embedded/runtime/EmbeddedWorkspaceRunner.kt`
- Create: `app/src/test/java/com/trancong/dexworkspacetouch/workspace/execution/embedded/runtime/EmbeddedWorkspaceRunnerTest.kt`

**Interfaces:**
- Consumes: Task 1 structured session snapshot/touch types and Task 2 prepared request/results/timeouts.
- Produces: `EmbeddedWorkspaceSessionFactory`, `EmbeddedWorkspaceSessionHandle`, and `EmbeddedWorkspaceRunner.start/stop/sendTouch/surfaceLost`.
- Serialization invariant: every public command, timeout, and observer snapshot becomes an event on one runner-owned queue before it can mutate runner state.
- Cleanup invariant: `stop()` and `close()` submit non-blocking commands; one `cleanupTimeout` encloses both submissions and the terminal wait.

- [ ] **Step 1: Write fake-session orchestration tests**

Use `kotlinx.coroutines.test.runTest` and a controllable fake factory/handle. Assert:

- factory creation and start follow ascending plan order
- A must publish ACTIVE before B start is invoked
- synchronous READY/ACTIVE callbacks are marshalled onto the runner queue and are not lost
- all ACTIVE is the only transition to runner ACTIVE
- C failure causes `stop -> close -> terminal` for C, B, A in that exact order
- C primary failure remains separate from B cleanup failure
- external fake session receives zero calls
- repeated start creates no duplicate handle
- repeated stop performs one cleanup sequence
- stop before start creates nothing and returns STOPPED
- stop during B STARTING prevents C creation and cleans B/A
- explicit stop racing surface loss converges on one cleanup sequence
- binder death returns `RecoveryRequired`
- surface invalid immediately before B start prevents `B.start` and rolls back B/A
- `sendTouch(cellA)` reaches only A and `sendTouch(cellB)` only B
- unknown and non-ACTIVE source IDs reject touch

- [ ] **Step 2: Add virtual-time timeout tests**

Advance the test scheduler without real sleep and assert:

- READY timeout returns StartFailed for the current source and performs reverse cleanup
- ACTIVE timeout does the same with no retry
- cleanup timeout cannot return clean STOPPED
- cleanup timeout with retained certainty returns CleanupIncomplete
- remote-death/uncertain cleanup returns RecoveryRequired
- callback at the deadline and timeout callback are both runner-queue events and produce exactly one deterministic result according to queue order
- a fake handle whose stop/close completion never arrives is bounded by the same cleanup timeout; the runner remains responsive and reports non-clean termination

- [ ] **Step 3: Run the runner tests and observe RED**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*EmbeddedWorkspaceRunnerTest" --console=plain
```

Expected: FAIL because runner/session-port types do not exist.

- [ ] **Step 4: Define the narrow session port**

Use signatures equivalent to:

```kotlin
interface EmbeddedWorkspaceSessionFactory {
    fun create(
        target: EmbeddedAppTarget,
        observer: (EmbeddedSessionSnapshot) -> Unit,
    ): EmbeddedWorkspaceSessionHandle
}

interface EmbeddedWorkspaceSessionHandle {
    val sessionId: EmbeddedAppSessionId
    fun connect()
    fun start(surface: EmbeddedWorkspaceExecutionSurface)
    fun sendTouch(event: EmbeddedTouchEvent): Boolean
    fun stop()
    fun close()
}
```

- [ ] **Step 5: Implement the one-run runner on one coroutine scope/dispatcher**

Constructor dependencies are preflight, factory, timeout policy, and one injected orchestration dispatcher/scope. Public `start`, `stop`, `sendTouch`, and `surfaceLost` calls, session observer callbacks (including synchronous callbacks), and timeout signals are marshalled as events onto that single queue before runner state is read or mutated. `start(request)` preflights first, owns only handles it creates, awaits structured phases with bounded virtual-time-compatible deadlines, and re-checks `surface.isValid` immediately before each `handle.start`.

- [ ] **Step 6: Implement one shared reverse cleanup path**

Rollback, normal stop, stop-during-start, and surface loss all call the same reverse-order convergence function. For each created handle, one `withTimeout(cleanupTimeout)` encloses the complete `stop()` submission, idempotent `close()` submission, and terminal-snapshot wait. The port contract requires both commands to return without blocking; asynchronous cleanup completion arrives through the marshalled observer event. On timeout record a non-clean outcome and continue safe cleanup. Never call global service APIs.

- [ ] **Step 7: Implement runner-owned touch routing**

`sendTouch(sourceCellId, event)` looks up only the runner item map, requires its latest phase to be ACTIVE, and forwards to that handle. It returns a typed accepted/rejected result without coordinate conversion.

- [ ] **Step 8: Run runner and preflight tests**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*EmbeddedWorkspaceRunnerTest" --tests "*EmbeddedWorkspacePreflightTest" --console=plain
```

Expected: PASS with no test sleeps.

### Task 4: Add the Android production surface/session adapters

**Files:**
- Create: `app/src/main/java/com/trancong/dexworkspacetouch/workspace/execution/embedded/runtime/AndroidEmbeddedWorkspaceExecutionSurface.kt`
- Create: `app/src/main/java/com/trancong/dexworkspacetouch/workspace/execution/embedded/runtime/AndroidEmbeddedWorkspaceSessionFactory.kt`
- Create: `app/src/test/java/com/trancong/dexworkspacetouch/workspace/execution/embedded/runtime/AndroidEmbeddedWorkspaceSessionAdapterTest.kt`
- Modify: existing EmbeddedApp/Waze/Calculator/Dual tests only where structured lifecycle compatibility requires it

**Interfaces:**
- Consumes: Task 1 `EmbeddedAppSession`; Task 3 session port.
- Produces: Android wrapper around an externally owned `Surface` and factory/handle adapter used by the proof controller.

- [ ] **Step 1: Write adapter contract tests**

Test through a narrow fake session constructor seam:

- factory passes target unchanged into one `EmbeddedAppSession`
- wrapper validity reflects the underlying Surface validity query
- start unwraps the exact Surface supplied by the host slot
- touch forwards the exact `EmbeddedTouchEvent`
- stop then close delegates once and preserves terminal callback order
- two adapter handles do not share session identity
- adapter exposes no Workspace field to `EmbeddedAppSession`

- [ ] **Step 2: Run focused tests and observe RED**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*AndroidEmbeddedWorkspaceSessionAdapterTest" --console=plain
```

- [ ] **Step 3: Implement the Android adapters**

The surface wrapper stores the externally owned `Surface`; it never releases it. The factory takes application `Context`, creates exactly one generic session per call, and translates structured callbacks without owning runner state.

- [ ] **Step 4: Run all embedded runtime tests**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*EmbeddedApp*" --tests "*EmbeddedWorkspace*" --tests "*WazeEmbeddedTargetTest" --tests "*CalculatorEmbeddedTargetTest" --console=plain
```

Expected: PASS, including DWT-VDM-005 connection/ownership tests.

### Task 5: Add the real-plan two-surface proof harness

**Files:**
- Create: `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/EmbeddedWorkspaceProofPlan.kt`
- Create: `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/EmbeddedWorkspaceRunnerController.kt`
- Create: `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/EmbeddedWorkspaceRunnerScreen.kt`
- Create: `app/src/test/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/EmbeddedWorkspaceProofPlanTest.kt`
- Create: `app/src/test/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/EmbeddedWorkspaceRunnerControllerTest.kt`
- Modify: `app/src/main/java/com/trancong/dexworkspacetouch/navigation/TouchNavigation.kt`
- Modify: `app/src/main/java/com/trancong/dexworkspacetouch/ui/screens/HomeScreen.kt`

**Interfaces:**
- Consumes: real `EmbeddedWorkspacePlanner`, Task 3 runner, Task 4 adapters, existing Waze/Calculator explicit identities.
- Produces: experimental route `embedded-workspace-runner` and one runner-level Start/Stop proof UI.

- [ ] **Step 1: Write proof-plan and controller tests**

Assert:

- proof input is a real `WorkspaceLaunchRequest`
- `EmbeddedWorkspacePlanner` produces two ordered items: Waze then Calculator
- source IDs are stable and unique
- normalized bounds exist but do not determine pane size or proof geometry
- geometry policy returns `900 x 675 @ 320 dpi` for both
- Start is disabled until exactly two valid source-keyed surfaces exist
- controller calls `runner.start()` once and runner-level Stop once
- Surface loss is forwarded with the correct source ID
- touch mapping is performed in the pane and forwarded to `runner.sendTouch(sourceCellId, event)`
- existing Waze, Calculator, and Dual route strings/actions remain registered

- [ ] **Step 2: Run focused tests and observe RED**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*EmbeddedWorkspaceProofPlanTest" --tests "*EmbeddedWorkspaceRunnerControllerTest" --console=plain
```

- [ ] **Step 3: Implement deterministic real-plan construction**

Construct a local non-persisted `WorkspaceLaunchRequest` with two `AppLaunchTarget` entries using the approved explicit Waze and Samsung Calculator components, stable source IDs, deterministic order, and normalized bounds. Pass it through `EmbeddedWorkspacePlanner`; do not duplicate a fake plan model or write persistence.

- [ ] **Step 4: Implement the proof controller**

The controller owns one runner and two externally supplied slot wrappers. It exposes runner state/results, delegates one Start/Stop, forwards source-specific touch and Surface loss, and closes the runner once on disposal.

- [ ] **Step 5: Implement the fixed two-pane Compose screen**

Create two `SurfaceView` panes in UI code, each fixed at the proven 4:3 proof size and keyed by source ID. Do not read normalized bounds for layout. Use pane-local `mapPoint` and pressure/action helpers to create `EmbeddedTouchEvent`; all session access goes through the runner/controller.

- [ ] **Step 6: Add experimental navigation without changing production Workspace launch**

Add the new route and Home diagnostic action only. Preserve Classic launch callbacks and the existing Waze, Calculator, and Dual experimental routes.

- [ ] **Step 7: Run proof, navigation, planner, and runtime tests**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*EmbeddedWorkspace*" --tests "*WorkspaceRunPlannerTest" --tests "*EmbeddedApp*" --console=plain
```

Expected: PASS.

### Task 6: Run final local gates, architecture scans, production smoke build, and one S23 runner proof

**Files:**
- No planned source changes.
- Verification captures remain untracked.

**Interfaces:**
- Consumes: completed Tasks 1-5.
- Produces: local/build/signing/device evidence and a reviewed scoped diff; no commit before human review.

- [ ] **Step 1: Run full local verification**

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug lintDebug --console=plain
git diff --check
```

Require `BUILD SUCCESSFUL`, zero lint errors, and no whitespace errors.

- [ ] **Step 2: Run architecture boundary scans**

Verify:

- no `workspace` import in `feature/embeddedapp`
- no Compose/SurfaceView/Room/persistence/Classic import in the runtime package
- no `NormalizedBounds` conversion or geometry derivation in runner/proof UI
- no package-specific branch in runner
- no direct Shizuku bind/unbind, service destroy, global registry enumeration, force-stop, or retry in runner
- fixed geometry appears only in proof geometry policy
- all waits use the injected timeout policy
- session factory is not invoked on any preflight rejection

- [ ] **Step 3: Verify the production-config smoke artifact**

Prefer the already-built `smoke-output/DWT-device-smoke.apk` without rebuilding if its SHA-256 is exactly `4906eae8501040de0d8aad58cdd3836c0bad95b49e2e38f69a25945fa69c50c3`. Reverify its package and signer before installation; the signer SHA-256 must remain:

```text
19ac0ea99125361b3c2083aaa44c8745ebad9c55fa967642c2b9e5a1086a45e7
```

Run the existing production smoke script only if the existing artifact cannot be used under its workflow. Do not modify version name/code or release outputs.

- [ ] **Step 4: Resolve the S23 and pass the Shizuku preflight before Start**

Enumerate `adb devices -l`, query online endpoints, and select one endpoint proving `SM-S918B / dm3q / Android 16 / API 36`. Use that serial consistently. Before pressing runner Start, establish that Shizuku is running and that DWT has the Shizuku availability and permission required by the existing EmbeddedApp runtime. Record the association baseline and host display/task. Require no pre-existing DWT embedded VirtualDevice, virtual input, or UserService resource and no pre-existing Waze/Calculator probe task. Do not silently clean unexplained resources.

If any precondition fails, stop before pressing Start. No acceptance smoke is consumed. The earlier Task 6 attempt reached `READY_TIMEOUT` and rollback `REMOTE_UNAVAILABLE` while Shizuku was not running; record it as `BLOCKED_BY_PRECONDITION`, with device acceptance still unverified.

- [ ] **Step 5: Run exactly one fresh bounded acceptance smoke after preflight PASS**

Open `Embedded Workspace Runner (Experimental)` and verify:

1. real plan contains Waze then Calculator
2. exact slot coverage and preflight PASS
3. Waze reaches READY then ACTIVE before Calculator start
4. Calculator reaches READY then ACTIVE; both have distinct sessions, VirtualDevices, trusted displays, virtual inputs, and tasks
5. both render while host stays on its pre-start display
6. one visible interaction succeeds in Waze
7. Calculator visibly produces `7 × 8 = 56`, without cross-session input effect
8. runner-level Stop cleans Calculator then Waze
9. authoritative final state returns to baseline with zero runner orphan and UserService removed when no external lease exists

The prior `BLOCKED_BY_PRECONDITION` attempt does not consume this one fresh acceptance smoke. After preflight PASS, press runner Start exactly once. At the first real blocker, stop: no retry, workaround, alternate target, or second run.

- [ ] **Step 6: Review the complete scoped diff after a PASS smoke**

Run `git status --short`, `git diff --check`, `git diff --stat`, and inspect the full DWT-VDM-006 diff. Confirm protected unrelated files and generated artifacts remain unstaged.

- [ ] **Step 7: Report for human review without committing or pushing**

Report exact changed files, test/build/signing evidence, runner ordering and ownership receipts, device visual result, reverse cleanup order, authoritative final baseline, limitations, and deliberately uncommitted files. Wait for a separate scoped-commit instruction.

## Self-review result

- Every amended spec requirement maps to a task and focused test, including stop/close terminal ordering, injected bounded timeouts, runner-owned touch routing, and just-in-time Surface validation.
- Every runner event crosses one orchestration queue before state mutation, and cleanupTimeout encloses the complete non-blocking stop/close/terminal convergence.
- Types and names are consistent across Tasks 1-6: structured session snapshots feed the session port; preflight produces prepared items; runner owns handles; Android adapters unwrap Surface; proof UI talks only to the runner/controller.
- The five Review Focus races/failure modes have explicit tests in Tasks 1, 3, and 4.
- The plan preserves DWT-VDM-004 Classic/planning behavior and DWT-VDM-005 remote ownership topology.
- The plan contains no renderer, persistence, run-mode product UI, same-target claim, arbitrary-N claim, retry, crash recovery, or implementation commit before review.
