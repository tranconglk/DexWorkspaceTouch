# DWT-VDM-006 — EmbeddedWorkspaceRunner Orchestration Design

**Date:** 2026-09-28
**Status:** Proposed for review
**Baseline:** `3d99f67fd0b9ee352be78e401cc3ebeb6ddba26d`
**Branch:** `release/1.0-beta`

## Purpose

DWT-VDM-006 adds the first orchestration layer between the pure `EmbeddedWorkspacePlan` introduced by DWT-VDM-004 and the isolated, multi-session `EmbeddedAppSession` runtime proven by DWT-VDM-005.

The milestone answers one question: can one real embedded Workspace plan start and stop a set of runner-owned sessions transactionally, in deterministic order, with scoped rollback and zero resource leakage, while leaving Workspace layout outside the session runtime?

A PASS proves orchestration only. It does not implement the visual Workspace renderer, production run-mode selection, same-target concurrency, arbitrary-N capacity, dynamic geometry, or crash recovery.

## Current implementation facts

The repository currently provides:

- `WorkspaceRunPlanner`, which resolves a `WorkspaceRunRequest` once and returns either the exact Classic request, an `EmbeddedWorkspacePlan`, or the original readiness rejection.
- `EmbeddedWorkspacePlan`, whose items preserve `sourceCellId`, explicit package/component, `normalizedBounds`, and deterministic `order`. Its constructor already requires non-blank Workspace identity, a non-empty item list, unique source IDs, and unique non-negative orders.
- `EmbeddedAppTarget`, which contains only package name, component name, and `EmbeddedAppGeometry`.
- `EmbeddedAppSession`, which owns one opaque session ID, one target, one ordered worker, one connection-manager lease, and one remote runtime identity.
- One process-level `EmbeddedAppServiceConnectionManager`, which shares the UserService binding while preserving one lease per session and removes the service only after authoritative remote emptiness.
- Per-session remote association, VirtualDevice, display, touchscreen, task, Surface copy, and cleanup state.
- The `Embedded Dual App` proof UI, which creates two unrelated sessions directly from two fixed targets. It proves the primitive but does not consume an `EmbeddedWorkspacePlan`.

`EmbeddedAppSession` currently reports `shellReady`, `busy`, `active`, free-form `status`, and `displayId` through a callback. This is enough for manual proof UI, but not enough for deterministic orchestration: a runner must distinguish structured start failure, successful ACTIVE, clean STOPPED, cleanup-incomplete, and remote death without parsing status text. DWT-VDM-006 therefore permits one narrow, target-independent lifecycle contract extension in `feature/embeddedapp`. It must not contain Workspace types or change remote ownership topology.

## Chosen architecture

The selected design is an injected session-port runner in `workspace/execution/embedded/runtime`.

```text
EmbeddedWorkspacePlan
        |
        v
EmbeddedWorkspaceRunner
        |
        +-- EmbeddedWorkspaceHostSlot keyed by sourceCellId
        +-- EmbeddedGeometryPolicy
        +-- EmbeddedWorkspaceSessionFactory
        |
        +-- RunnerItem A -> target + execution surface + session handle
        +-- RunnerItem B -> target + execution surface + session handle
```

The runner is the sole owner of orchestration state and of the session handles it creates. Each session remains the sole owner of one app's runtime resources. The runner never enumerates the global remote registry and never cleans sessions it did not create.

Two alternatives are rejected:

1. Putting plan/source/bounds fields into `EmbeddedAppSession` would invert dependency direction and permanently couple generic execution to Workspace placement.
2. Keeping orchestration in a Compose two-pane screen would make lifecycle, rollback, and ordering dependent on UI widgets and would be difficult to unit-test.

The injected port adds a small adaptation boundary while keeping the runner independent of Compose and directly testable with fake sessions.

## Package and dependency boundaries

New orchestration code belongs under:

```text
workspace/execution/embedded/runtime/
```

It may depend on:

- `workspace/execution/embedded/EmbeddedWorkspacePlan`
- `EmbeddedWorkspacePlanItem`
- the public target-independent contracts from `feature/embeddedapp`
- Android `Surface` only in the Android host-slot adapter, not in the pure state machine tests

Dependency direction is always:

```text
workspace/execution/embedded/runtime
    -> feature/embeddedapp
```

`feature/embeddedapp` must not import or store `EmbeddedWorkspacePlan`, `sourceCellId`, `NormalizedBounds`, Workspace identity, Room, persistence, or Classic execution types. `EmbeddedAppTarget` remains exactly package/component/geometry.

Classic planning and launching are unchanged. DWT-VDM-006 does not modify `WorkspaceRunPlanner`, Classic runtime routing, persisted Workspace models, or production run-mode UI.

## Runner input boundary

The runner consumes a prepared request:

```kotlin
data class EmbeddedWorkspaceExecutionRequest(
    val plan: EmbeddedWorkspacePlan,
    val hostSlots: List<EmbeddedWorkspaceHostSlot>,
)

interface EmbeddedWorkspaceExecutionSurface {
    val isValid: Boolean
}

data class EmbeddedWorkspaceHostSlot(
    val sourceCellId: String,
    val executionSurface: EmbeddedWorkspaceExecutionSurface,
)
```

The production Android surface adapter wraps one already-created `Surface` and exposes validity. It is the only layer that unwraps the Android `Surface` for the production session factory. Tests use a fake execution surface without Android UI objects.

The UI/controller owns `SurfaceView` creation, placement, holder callbacks, and destruction. The runner owns no Compose function or `SurfaceView`. A slot is an execution capability, not a placement model.

Mapping is exclusively by `sourceCellId`:

```text
plan item sourceCellId
    -> exactly one host slot
    -> exactly one RunnerItem
    -> exactly one opaque session ID/session handle
    -> one runtime receipt
```

List position, package name, and component name are never used as runner identity.

## Geometry policy and target construction

Geometry is injected:

```kotlin
fun interface EmbeddedGeometryPolicy {
    fun geometryFor(item: EmbeddedWorkspacePlanItem): EmbeddedAppGeometry
}
```

The proof policy returns the already-proven `900 x 675 @ 320 dpi` for every supported item. The fixed value lives in the proof/default policy, not in `EmbeddedWorkspaceRunner`, `EmbeddedWorkspacePlan`, or `EmbeddedAppTarget`.

After all preflight checks succeed, the runner adaptation layer constructs each target:

```kotlin
EmbeddedAppTarget(
    packageName = item.packageName,
    componentName = item.componentName,
    geometry = geometryPolicy.geometryFor(item),
)
```

No Workspace identity, source ID, order, or normalized bounds enters the target.

`normalizedBounds` remains attached to the plan item and runner result metadata so a later renderer can correlate results with placement. DWT-VDM-006 never converts bounds to pixels, sizes a Surface from bounds, derives VDM geometry from bounds, scales content, or changes container placement. Those responsibilities belong to DWT-VDM-007.

## Preflight validation

Preflight runs completely before the session factory is called. Any rejection allocates zero sessions and acquires zero UserService leases.

It validates:

1. The plan model exists and its constructor invariants remain valid: non-empty items, unique source IDs, unique non-negative orders, and non-blank resolved identities.
2. Item order is deterministic. The runner sorts by `order`; it additionally rejects any state that violates uniqueness rather than relying on incidental list order.
3. Host-slot source IDs are non-blank and unique.
4. The set of slot source IDs equals the set of plan source IDs exactly.
5. Every execution surface reports valid.
6. No slot refers to an unknown item and no item is omitted.
7. Every geometry-policy call succeeds and yields a valid `EmbeddedAppGeometry`.
8. Resolved target identity `(packageName, componentName)` is unique across the execution request.

Duplicate target identity is an embedded-runtime capability rejection, not a planning rejection. `EmbeddedWorkspacePlanner` continues to allow two cells with the same component, preserving Classic and pure planning semantics. The runner reports `DuplicateTargetIdentity` before creating the first session.

Preflight errors preserve their category and relevant source IDs. They are distinct from upstream `WorkspaceRunPlanningResult.Rejected` and from a runtime start failure.

## Session port and lifecycle contract

The orchestration core uses the smallest testable port:

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

The production factory creates one `EmbeddedAppSession` and adapts the execution-surface wrapper to its Android `Surface`. Unit tests use deterministic fake handles.

The generic embedded session contract must expose structured, target-independent snapshots, for example:

```kotlin
enum class EmbeddedSessionPhase {
    IDLE, CONNECTING, READY, STARTING, ACTIVE,
    STOPPING, STOPPED, FAILED, CLEANUP_INCOMPLETE, REMOTE_DIED
}

data class EmbeddedSessionSnapshot(
    val phase: EmbeddedSessionPhase,
    val displayId: Int = -1,
    val failure: EmbeddedSessionFailure? = null,
)
```

Exact names may follow existing conventions, but the runner must not parse `EmbeddedAppState.status`. `ACTIVE` is emitted only after remote start succeeds. `STOPPED` is emitted only after scoped remote cleanup and release of that session's connection-manager lease finish cleanly. The runner invokes `handle.stop()` and then idempotent `handle.close()`; `close()` joins or converges with the existing stop and must never issue a second remote stop. The runner then awaits one terminal snapshot: `STOPPED`, `CLEANUP_INCOMPLETE`, or `REMOTE_DIED`. A cleanup failure or cleanup deadline with retained authoritative certainty emits `CLEANUP_INCOMPLETE`; loss of authoritative certainty emits `REMOTE_DIED`. Existing Waze, Calculator, and dual proof screens may continue rendering human-readable status derived from the same structured lifecycle.

The structured extension stays inside `feature/embeddedapp` and contains no Workspace metadata. It does not replace per-session internal serialization or the connection manager.

## Runner-owned item and runtime receipt

For each item, the runner retains:

```text
sourceCellId
plan item (including normalizedBounds and order)
constructed EmbeddedAppTarget
host execution surface
opaque EmbeddedAppSessionId
session handle
latest structured session snapshot
startup/cleanup outcome
```

The runtime receipt exposed by runner results includes source ID, opaque session ID, target identity, order, phase, and display ID when known. It does not expose or duplicate remote mutable ownership internals. Remote task/input/VDM ownership remains inside `EmbeddedAppSession` and the remote runtime.

## Runner state machine

The runner is a one-run owner:

```text
IDLE
  -> PREFLIGHT
      -> STARTING
          -> ACTIVE
          -> ROLLING_BACK
              -> FAILED_CLEAN
              -> RECOVERY_REQUIRED
  -> STOPPING
      -> STOPPED
      -> RECOVERY_REQUIRED
```

Rules:

- One runner instance accepts `start()` once. A second start is a deterministic duplicate-call result and creates nothing.
- `ACTIVE` means every ordered plan item has reached structured session phase `ACTIVE`.
- During `STARTING`, zero or more earlier items may be ACTIVE and the current item may be connecting/starting.
- A failed or stopped runner is not reusable. A new run requires a new runner instance and fresh slots.
- `stop()` before `start()` transitions directly to `STOPPED` without allocating.
- Repeated `stop()` while `STOPPING` or after a terminal state joins/returns the same terminal outcome and never repeats session cleanup.
- Surface loss, startup failure, or stop-during-start converges through the same rollback/stop coordinator. Competing cleanup paths cannot run concurrently.

## Ordering and threading

The runner owns one orchestration queue/worker. It never blocks the UI thread.

Startup order is `plan.items.sortedBy(order)`. For each item:

1. create exactly one session handle
2. connect it
3. await structured `READY` within `readyTimeout`
4. re-check the mapped execution surface immediately before start
5. if still valid, start it with that surface
6. await structured `ACTIVE` within `activeTimeout`
7. only then advance to the next item

There is no parallel startup. Session internals keep their own ordered workers, so once several items are ACTIVE their input/runtime operations remain independent as proven in DWT-VDM-005.

Callbacks are correlated through the runner-owned handle and `sourceCellId`; status text and package identity are not correlation keys.
### Bounded orchestration timeouts

The runner receives an injected `EmbeddedWorkspaceRunnerTimeoutPolicy` with `readyTimeout`, `activeTimeout`, and `cleanupTimeout`. Production values are chosen in the implementation plan from existing proven startup/cleanup windows; the runner contains no timing literals. Tests use a controllable scheduler/clock and never `sleep`.

A READY or ACTIVE deadline creates the primary `StartFailed` for the current source ID, invokes stop/close for the current handle, and rolls back earlier handles without retry. Cleanup deadlines apply to rollback, normal stop, stop-during-start, and surface-loss convergence. Timeout never authorizes a false clean result.

## Runner-owned touch routing

The runner exposes `sendTouch(sourceCellId, event)` and resolves only its own item table. It accepts touch only when the item is in structured `ACTIVE` state and forwards the generic `EmbeddedTouchEvent` to that item's handle. Unknown source IDs and non-ACTIVE items are rejected without touching any session. External sessions are unreachable because they are absent from the runner-owned map.

Coordinate mapping remains in the host/proof UI, which knows the SurfaceView dimensions. The runner receives already-mapped embedded-display coordinates and does not learn Workspace bounds or layout geometry.
## Startup failure and rollback

If item C fails after A and B became ACTIVE, the runner records C as the primary failure, stops accepting later starts, and rolls back in reverse creation/start order:

```text
C -> B -> A
```

The current failed/partially-owned item is included because its session may have acquired a lease or remote resources even without reaching ACTIVE. For each handle, the runner calls `stop()`, calls idempotent `close()` to join/converge with that stop, then awaits a terminal snapshot within `cleanupTimeout`. All safe rollback attempts run even if one cleanup fails. A cleanup timeout cannot produce clean `STOPPED`: it becomes `CleanupIncomplete` while authoritative remote certainty remains, or `RecoveryRequired` when that certainty is lost.

The runner never calls service-wide destroy, enumerates all remote sessions, unbinds Shizuku directly, force-stops an app, retries C, or resets the shared UserService. The shared manager remains responsible for final service lifetime.

An unrelated session created outside this runner is absent from the runner item collection and is therefore never stopped or closed.

## Result model

Results are explicit rather than Boolean:

```text
EmbeddedWorkspaceRunResult.Started
EmbeddedWorkspaceRunResult.PreflightRejected
EmbeddedWorkspaceRunResult.StartFailed
EmbeddedWorkspaceRunResult.Stopped
EmbeddedWorkspaceRunResult.CleanupIncomplete
EmbeddedWorkspaceRunResult.RecoveryRequired
EmbeddedWorkspaceRunResult.DuplicateCall
```

A start-failed result preserves:

- failed `sourceCellId`
- primary failure code/message
- receipts for items that reached ACTIVE
- receipt for the partial item when created
- ordered per-item rollback outcomes
- whether every runner-owned session reached a clean terminal state

Rollback cleanup failures are separate entries and never overwrite the primary start failure. `CleanupIncomplete` means ordinary scoped cleanup ran but at least one item did not prove clean termination. `RecoveryRequired` is reserved for loss of authoritative remote certainty, including binder death.

Successful `Started` includes ordered item receipts and preserved plan metadata, including normalized bounds for future placement correlation. It does not claim those bounds were applied.

## Normal stop

`runner.stop()` prevents new item startup and cleans only runner-owned handles in reverse creation order. For a fully active A/B run the order is B then A.

For each item it calls `stop()`, calls idempotent `close()` to join the same cleanup, and awaits one terminal snapshot within `cleanupTimeout`. Repeated stop returns or awaits the same operation. It never issues duplicate remote stop/close calls.

A clean `STOPPED` result requires every owned handle to report clean terminal cleanup. If one handle is cleanup-incomplete, remaining handles still receive safe cleanup and the result is `CleanupIncomplete`. If binder death prevents authoritative cleanup, the result is `RecoveryRequired`, not a false clean stop.

## Stop during startup

When stop is requested during item N startup:

1. set the runner's stop-request flag on its orchestration queue
2. prevent creation/start of item N+1 and later items
3. invoke the same stop/close convergence for the in-flight handle without waiting indefinitely for its original start callback
4. await its terminal cleanup only until `cleanupTimeout`, then continue safe reverse cleanup of earlier items
5. terminate as `STOPPED`, `CleanupIncomplete`, or `RecoveryRequired`

The startup callback checks runner generation/terminal intent before advancing. Startup and rollback therefore cannot advance different ends of the item list concurrently.

## Surface loss

The host/controller reports `surfaceLost(sourceCellId)` to the runner. Loss before allocation fails preflight. Loss during STARTING or ACTIVE is a whole-run fail-closed event:

- stop launching later items
- record the lost source ID as the primary run failure
- run scoped reverse-order cleanup for all created items
- produce clean failed/stopped or cleanup-incomplete/recovery-required evidence

DWT-VDM-006 does not replace a Surface, reparent content, recreate a slot automatically, or restart a session. An explicit user stop racing surface loss joins the same one-time cleanup operation.

## Binder death

If any runner-owned session reports `REMOTE_DIED`, the runner:

- marks the run `RECOVERY_REQUIRED`
- prevents later session creation/start
- preserves all item/session receipts and the primary source context
- requests no normal operation against the dead binder
- allows only safe local close bookkeeping supported by the session contract
- does not claim a clean rollback or final zero-orphan baseline

No crash recovery is designed in this milestone. The DWT-VDM-005 fail-closed service semantics remain authoritative.

## Proof harness

Add an experimental `Embedded Workspace Runner` route with two fixed panes. The UI creates two `SurfaceView` instances and supplies two host slots; it does not create sessions itself.

The proof input is a local, non-persisted `WorkspaceLaunchRequest` containing Waze and Samsung Calculator targets. It is passed through the real `EmbeddedWorkspacePlanner` to obtain the real `EmbeddedWorkspacePlan` type. The harness then passes that plan and its two source-keyed slots to `EmbeddedWorkspaceRunner`.

This is preferred over adding sample persistence or depending on whichever user Workspace happens to exist. It proves the production plan model and planner boundary deterministically while leaving Room and Workspace library data unchanged.

The screen may retain a fixed two-column layout solely to expose valid surfaces and visible pixels. It must not read `normalizedBounds` to place or size the panes. One runner-level Start and one runner-level Stop control replace per-pane orchestration controls. Touch mapping within each already-fixed pane continues through the associated session and proven target geometry.

The proof targets are explicit:

- Waze at `900 x 675 @ 320 dpi`
- Samsung Calculator at `900 x 675 @ 320 dpi`

Both values come from the injected proof geometry policy.

## Device acceptance

Before pressing Start, dynamically resolve an `SM-S918B / dm3q / Android 16 / API 36` endpoint and verify that Shizuku is running, DWT has the availability and permission required by the existing EmbeddedApp runtime, no DWT embedded VirtualDevice/input/UserService resource or Waze/Calculator probe task exists, and the association baseline is recorded. If any precondition fails, stop before Start without consuming the acceptance smoke. The earlier Task 6 attempt is `BLOCKED_BY_PRECONDITION` because Shizuku was not running; device acceptance remains unverified. After preflight passes, permit exactly one fresh bounded smoke on the same unchanged source. Prefer the byte-identical existing APK with SHA-256 `4906eae8501040de0d8aad58cdd3836c0bad95b49e2e38f69a25945fa69c50c3`, after rechecking its hash and signer. The host may remain on display 0 or DeX/external display, but must remain on its pre-start display.

The smoke proves:

1. clean baseline
2. real planner produces Waze + Calculator `EmbeddedWorkspacePlan`
3. two fixed host surfaces map exactly by source ID
4. runner preflight passes before session creation
5. Waze reaches ACTIVE before Calculator start begins
6. both sessions become ACTIVE on distinct trusted displays/inputs/tasks
7. both render while DWT stays on its original host display
8. one visible interaction succeeds in each pane without cross-session effect
9. runner-level Stop cleans Calculator then Waze
10. final authoritative state has zero runner probe tasks, displays, VirtualDevices, virtual inputs, associations, and UserService when no external lease exists

No forced rollback device test is required. Reverse rollback, stop-during-start, duplicate targets, surface loss, and cleanup-failure evidence are covered with fake-session unit tests. The smoke does not repeat the full DWT-VDM-005 CANCEL/input matrix unless the implementation changes the generic input layer.

## Test strategy

Focused tests must cover:

1. exact plan/slot coverage passes preflight
2. missing slot rejects before factory invocation
3. extra slot rejects before factory invocation
4. duplicate slot/source mapping fails closed
5. duplicate package/component rejects before factory invocation
6. geometry failure rejects before allocation
7. a surface invalidated after preflight but before its item start is never passed to the handle and triggers rollback
8. targets contain only package/component/geometry
9. sorted plan order determines session creation/start order
10. A reaches ACTIVE before B start begins
11. all items ACTIVE is the only route to runner ACTIVE
12. C failure rolls C/B/A back in reverse order
13. external fake session is never touched
14. primary failure and rollback failures remain separate
15. repeated stop is idempotent
16. stop during STARTING prevents later starts
17. surface loss cleans the whole owned run once
18. binder death returns recovery-required, not clean stop
19. READY, ACTIVE, and cleanup deadlines use a fake scheduler, trigger no retry, and never report false clean completion
20. touch(cellA) reaches only A; touch(cellB) reaches only B; unknown/non-ACTIVE IDs are rejected
21. normalized bounds remain preserved as metadata and unused by geometry/layout
22. runtime package has no Compose, SurfaceView, Room, persistence, or Classic imports
23. `feature/embeddedapp` gains no Workspace dependency
24. existing DWT-VDM-004 planner tests remain green
25. existing DWT-VDM-005 embedded-session tests remain green
26. proof navigation keeps existing Waze, Calculator, and Dual routes registered

## Deferred to DWT-VDM-007

DWT-VDM-007 will define the Workspace renderer/container layer that translates `normalizedBounds` into host placement and Surface sizing. It may introduce container lifecycle policy, Z-order, clipping, and the mapping between Workspace canvas dimensions and actual host pixels.

DWT-VDM-006 deliberately does not decide:

- normalized-bounds-to-pixel conversion
- production Workspace screen integration
- saved/default run mode
- mixed Classic/Embedded execution
- same-target concurrent sessions
- arbitrary-N capacity or performance
- dynamic VDM geometry, resize, or density
- Surface replacement or live re-layout
- focus/Z-order policy
- crash recovery
- audio, clipboard, IME/keyboard, mouse redesign, or multi-touch

## Architecture self-review

The design has been checked against the milestone constraints:

- Workspace models flow only into the Workspace runtime package.
- `EmbeddedAppTarget` remains package/component/geometry only.
- Fixed proof geometry is injected by policy, not hard-coded in the runner.
- Duplicate target execution is rejected before session allocation.
- Startup is sequential and deterministic.
- Rollback touches only the runner's own handle collection.
- No service-global reset, retry, or force-stop exists.
- Normalized bounds are metadata only and never Surface pixels.
- Compose and `SurfaceView` remain in the proof UI.
- Classic planning, persistence, and production run-mode UI remain unchanged.
- The design claims two-target orchestration on the tested device, not arbitrary-N performance or same-target support.
- The design is complete and internally consistent.
