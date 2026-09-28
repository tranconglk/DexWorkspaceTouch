# DWT-VDM-005: Multi-Session Ownership and Lifecycle Proof

## Purpose

DWT-VDM-005 defines the smallest architecture and proof that permits more than one generic `EmbeddedAppSession` to be active at the same time. The device proof uses Waze and Samsung Calculator, started sequentially and then kept active concurrently.

This milestone proves the execution primitive below the Workspace layer:

```text
EmbeddedAppSession A -> Waze
EmbeddedAppSession B -> Calculator
```

It does not consume `EmbeddedWorkspacePlan`, implement `EmbeddedWorkspaceRunner`, choose virtual geometry from Workspace bounds, or claim arbitrary N-app Workspace execution. The proven geometry remains `900 x 675 @ 320 dpi` for both targets.

## Current implementation and the single-session assumption

The current implementation cannot safely host two sessions. The restriction is distributed across the client, AIDL service, remote state, cleanup, and Shizuku binding rather than contained in one guard.

### App-side session and binding

Each `EmbeddedAppSession` currently creates its own:

- `ServiceConnection`
- Shizuku binder/permission listeners
- single-thread worker
- `SessionStopGate`
- `EmbeddedAppState`
- identical `Shizuku.UserServiceArgs`

The args use:

```text
component = EmbeddedAppUserService
daemon = false
processNameSuffix = embedded_app
tag = dwt-vdm-002
```

Shizuku API 13.1.5 caches `ShizukuServiceConnection` by tag when a tag is present, otherwise by service class name. Therefore two current `EmbeddedAppSession` instances using `dwt-vdm-002` attach client callbacks to the same cached Shizuku connection and the same remote UserService, rather than proving independent service instances.

`EmbeddedAppSession.close()` currently calls:

```text
stopSession() when locally active
unbindUserService(args, connection, remove=true)
```

In Shizuku 13.1.5, `remove=true` calls `removeUserService` without scoping the removal to one local callback. One session close can therefore remove the shared remote service required by another session. The API's `remove=false` path also clears the cached connection's callback set, so independent unbind calls are not a safe reference-counting mechanism.

The shared binding lifetime must consequently be owned above individual sessions.

### AIDL contract

`IEmbeddedAppService` currently exposes:

```text
startSession(surface, package, component, width, height, density)
sendTouch(action, x, y, pressure, eventTime)
stopSession()
destroy()
```

No operation identifies a session. `sendTouch()` and `stopSession()` necessarily operate on the one remote global state. `destroy()` performs global cleanup and exits the process.

### Remote UserService

`EmbeddedAppUserService` stores one nullable `association`. `startSession()` requires `association == null`, creates one association, and delegates to one global `EmbeddedAppVdm`. A second start against the same service is rejected.

All service methods are `@Synchronized`. This prevents data races for the one-session implementation, but it also means a slow startup or cleanup globally blocks touch delivery for every future session if the same structure is extended mechanically.

A start failure invokes `stopInternal()`, which cleans the global VDM and association. With two sessions, this would destroy whichever session occupies that global state rather than only the failed start.

### Remote VDM and task state

`EmbeddedAppVdm` is a Kotlin singleton. It stores one copy of every mutable resource:

- `device`
- `displayCallback`
- `receivedSurface`
- `displayId` and `deviceId`
- `beforeTaskIds`
- `activeTarget` and `activeTaskId`
- `touchToken`, `returnedInput`, and `inputDeviceId`
- `touchClosed`
- `cleanupProgress`

Starting B would either hit the `device == null`/`activeTarget == null` guards or overwrite state needed by A. Touch has no routing key. Task snapshots and recorded task IDs are global. Cleanup advances one global `CleanupProgress`, closes one global input/device, removes one global task, releases one Surface copy, and clears all global references.

### Consumers

`EmbeddedWazeScreen` and `EmbeddedCalculatorScreen` are thin consumers and each constructs one `EmbeddedAppSession`. Their Surface callbacks stop their own session and their Compose disposal calls `session.close()`. These consumers are safe only because they have been exercised sequentially. If both screens/sessions coexist today, they still converge on the same Shizuku tag, same remote service, and same remote singleton state.

## Topology decision

### Selected shell topology: one shared UserService with a session registry

DWT-VDM-005 selects one non-daemon `EmbeddedAppUserService` process with a registry keyed by opaque session ID.

```text
process-level EmbeddedAppServiceConnectionManager
                    |
          one Shizuku connection/lease owner
                    |
          EmbeddedAppUserService UID 2000
                    |
        sessionId -> RemoteSessionRuntime
```

This topology matches the actual Shizuku cache semantics, avoids one shell process per embedded app, scales beyond exactly two sessions, and makes service destruction an explicit process-level decision. Cleanup isolation comes from per-session runtime ownership rather than process isolation.

The alternative of one UserService process per session would require a unique tag and process suffix per session. It could isolate the current singleton accidentally, but would multiply privileged processes, leave more stale-service possibilities, make reconnect/reconciliation harder, and encode process topology into every app session. It also avoids rather than fixes the generic runtime's global ownership model. It is rejected for this milestone.

### Selected VirtualDevice topology: one VirtualDevice per session

Each session owns exactly one VirtualDevice, one trusted virtual display, and one virtual touchscreen. This is the already-proven primitive and provides direct task/display/input ownership. A shared VirtualDevice would weaken cleanup and display-policy isolation without evidence that it improves this use case, so it is rejected.

### Selected association topology: one temporary association per session

Each remote session creates and owns one `APP_STREAMING` association. It removes only that association after closing its VirtualDevice. This preserves the proven cleanup sequence and makes stop(B) independent from A.

A shared association is technically conceivable for multiple VirtualDevices, but its deletion lifetime would become service-global and current runtime evidence does not prove whether deleting it while another device remains active is harmless. Cleanup isolation takes priority over reducing association count.

Association shell mutations must pass through one small remote `AssociationCoordinator` lock because all sessions modify the same system association list. The coordinator generates collision-resistant locally administered MAC values, creates/lists/removes one association atomically, and records `associationId + MAC -> sessionId`. It does not own VDM or task state and does not serialize complete session startup.

## Session identity

The app creates an opaque session ID before binding or remote start. The representation is a randomly generated UUID string wrapped by a validated `EmbeddedAppSessionId` value type.

App-side generation is selected because the identity must already exist when the app connection manager registers a client, correlates callbacks, and submits `startSession`. A remote-generated ID would require an unscoped pre-session allocation call and an additional orphan-allocation cleanup path.

The ID:

- is not derived from package, component, pane index, or Workspace cell
- is unique for the lifetime of the DWT process with negligible collision risk
- permits two sessions with the same target identity at the architecture level
- is immutable for one `EmbeddedAppSession`
- is included in every session-scoped AIDL operation and result

An empty, malformed, stale, or unknown ID fails closed. Repeating start for an ID already in `STARTING`, `ACTIVE`, or `STOPPING` returns a typed duplicate-state failure and never creates a second resource set. A stopped ID is not silently reused; a new app-side session receives a new ID.

The proof UI may label panes A and B for humans, but those labels are not session IDs.

## Session-scoped remote contract

The AIDL contract becomes conceptually:

```text
getUid()
startSession(sessionId, surface, packageName, componentName, width, height, densityDpi)
sendTouch(sessionId, action, x, y, pressure, eventTimeNanos)
stopSession(sessionId)
getSessionState(sessionId)                 // diagnostic/query only
getServiceState()                          // authoritative aggregate registry/resource state
destroy()                                  // UserService lifecycle callback, never a single-session operation
```

Exact result transport may remain `Bundle` for mechanical compatibility, but every result includes `sessionId`, success/failure, and a stable failure code. `startSession` success also returns the ownership receipt needed for diagnostics: association ID/MAC, VirtualDevice ID, display ID, input device ID, and target task ID.

`sendTouch` and `stopSession` look up exactly one registry entry. Unknown/stale IDs return `UNKNOWN_SESSION`; they do not fall back to a current session. `stopSession` is idempotent for a known terminal entry or retained tombstone. The implementation may retain bounded terminal tombstones until the client releases its lease, but tombstones contain no live Android resources.

`getServiceState()` is an authoritative read-only query over the remote registry. It returns enough aggregate state to distinguish an empty, terminal registry from any session that is active, starting, stopping, failed with retained resources, or otherwise potentially live. The contract includes at least:

```text
activeSessionCount
startingSessionCount
stoppingSessionCount
liveResourceSessionCount
```

An equivalent representation is acceptable only when it proves the same invariant without exposing the complete registry or its session IDs. A session with incomplete cleanup counts as live until every owned Android resource has been released and its registry entry contains no potentially-live ownership receipt.

There is no client-callable global stop. The generated UserService `destroy()` remains the process teardown hook. It snapshots all registry entries, performs best-effort cleanup for each, records failures, clears the registry, and only then exits/returns according to Shizuku lifecycle semantics.

## Remote registry and per-session runtime

The shared service owns:

```text
sessionId -> RemoteSessionRuntime
```

`RemoteSessionRuntime` replaces the mutable singleton `EmbeddedAppVdm`. Each instance owns only one session's:

- target and geometry
- remote Surface copy
- before-launch task ID snapshot
- association
- VirtualDevice and callbacks
- trusted display ID
- virtual touchscreen token/object/input device ID
- recorded target task ID
- startup state and cleanup progress
- per-session lock

Low-level reflection helpers, task enumeration, command execution, and VDM service lookup may remain stateless shared helpers. No shared helper may contain `currentDevice`, `currentTask`, `currentInput`, `currentAssociation`, or a mutable current target.

### Remote state machine

```text
RESERVED
  -> STARTING
       -> ACTIVE
       -> FAILED -> STOPPING -> STOPPED
ACTIVE
  -> STOPPING -> STOPPED
```

The registry reserves the ID before allocating the first resource. Reservation and duplicate checks occur under the short registry lock. Resource creation occurs through that entry's per-session lock, outside the registry lock. A start result is published only after association, VirtualDevice, display, input, stable input configuration, launch, and exact target-task recording succeed.

Touch is accepted only in `ACTIVE`. Stop is accepted in `STARTING`, `ACTIVE`, `FAILED`, or `STOPPING`, converges on the same cleanup, and is idempotent. A stopped entry is removed or reduced to a tombstone only after all references are cleared.

## Thread safety and serialization

Binder may invoke the UserService on multiple threads. The design uses two small synchronization scopes:

1. A registry lock protects reserve/lookup/remove and terminal tombstones. It is never held while creating devices, polling system state, launching activities, sending input, or cleaning resources.
2. Each `RemoteSessionRuntime` has one lock or single-session serialized queue. Start/touch/stop for the same ID cannot race, and touch event order is preserved.

Operations for A and B may proceed independently. In particular, B's input-configuration wait must not block A touch delivery. The association coordinator serializes only association-list mutations. No general concurrency framework or cross-session global worker is required.

The app side retains one ordered worker/queue per `EmbeddedAppSession`, preserving DOWN/MOVE/UP/CANCEL order. The process-level service connection manager serializes only bind/unbind and lease bookkeeping.

## App-side service connection and lifetime

Introduce one process-level `EmbeddedAppServiceConnectionManager`. Individual `EmbeddedAppSession` objects acquire a lease from it rather than calling Shizuku bind/unbind themselves.

The manager owns:

- the one `UserServiceArgs`
- the one Shizuku `ServiceConnection`
- Shizuku binder and permission listeners
- the current `IEmbeddedAppService` binder
- lease/session listener registration
- bind state and remote-death fan-out
- the final-remove decision

`EmbeddedAppSession.connect()` lazily registers its session ID/listener and acquires its manager lease before participating in permission/bind work. Concurrent calls converge on one bind, and the same lease remains owned through later `start()` calls. A UserService binding must never exist without at least one owning process-level lease/registration. `start()` starts only this session ID remotely; it does not create binding ownership. When connected, the manager reports the same UID-2000 binder to all current leases.

A session that connects but never starts still owns a lease and releases it through `close()`. With A and B connected, the manager has two leases over one shared bind; closing never-started B leaves A's lease and service intact. Closing the last never-started session follows the same authoritative `getServiceState()` gate before final removal.

`EmbeddedAppSession.close()` means:

1. reject new touch/start work for this session
2. if this ID may own remote resources, enqueue/await its idempotent `stopSession(sessionId)` on the session worker
3. release only its client registration and manager lease
4. remove its state callback/listeners and stop its worker after cleanup completion

It never invokes global `destroy()` and never directly calls `unbindUserService(..., remove=true)`.

The manager may remove the UserService only when all of these are true:

- lease count is zero
- no start/stop call is in flight
- every locally known session is terminal and has no live remote resources
- an authoritative `getServiceState()` result proves that the remote registry contains no active, starting, stopping, cleanup-incomplete, or otherwise potentially-live session/resource

Only then does the manager perform the single final `unbindUserService(args, connection, remove=true)`. One client close while another lease is active only removes that client registration.

Final UserService removal never relies solely on local lease bookkeeping or locally cached session state. If `getServiceState()` fails, the binder identity/liveness is uncertain, or the returned state reports any live or potentially-live entry, the manager fails closed and does not call `unbindUserService(..., remove=true)`. It retains a non-removed/disconnected state that requires later authoritative reconciliation; it does not guess that remote cleanup completed.

Surface destruction stops only the session owning that Surface. Compose disposal closes its two-session screen sessions independently; whichever closes first cannot unbind the shared service. Activity recreation follows the same idempotent path.

## Resource ownership table

| Resource | Created by | Owner | Released by | Shared? | Effect of stop(B) |
|---|---|---|---|---|---|
| Shizuku UserService process | Shizuku after manager's first bind | Process-level connection manager / Shizuku lifecycle | Manager after final lease and empty registry; Shizuku on death | Yes, across sessions | Remains alive for A |
| App-side service binding | Connection manager | Connection manager | Final lease release after empty-registry confirmation | Yes | B lease removed; binding retained |
| Session ID | App-side `EmbeddedAppSession` factory | One app session and matching remote entry | Tombstone/reference removal after stop/close | No | Only B ID becomes terminal |
| APP_STREAMING association | B's remote runtime through coordinator | B runtime | B cleanup after B device close | No | Only B association removed |
| VirtualDevice | B remote runtime | B runtime | B cleanup | No | A device unchanged |
| Trusted virtual display | B VirtualDevice/runtime | B runtime | Closing B VirtualDevice | No | A display unchanged |
| Virtual touchscreen | B VirtualDevice/runtime | B runtime | First step of B cleanup | No | A input unchanged |
| Target task | System launch recorded by B runtime | B runtime by exact task ID/display/target receipt | B cleanup | No | A task excluded and unchanged |
| Remote Surface copy | Binder transfer into B runtime | B runtime | B reference cleanup after device close | No | A Surface copy unchanged |
| Local SurfaceView/Surface | Proof pane/UI | Its UI pane | Android/UI lifecycle | No | A pane unchanged |
| Cleanup state | B `RemoteSessionRuntime` | B runtime | Cleared with B terminal entry | No | A cleanup state does not advance |

## Per-session startup and cleanup

### Startup

For one reserved session ID:

1. validate UID, ID, target, geometry, and Surface
2. snapshot all current task IDs
3. create and record that session's association
4. create and record that session's VirtualDevice
5. create its trusted display using its Surface
6. create its touchscreen associated with that display
7. wait for that display's stable finger configuration
8. launch its explicit target on that display
9. select exactly one new task matching component and display, excluding the snapshot
10. record the exact task ID and transition to `ACTIVE`

If A is already active when B snapshots tasks, A's task is simply in B's exclusion set. B additionally requires B's explicit component and B's distinct display, so it cannot adopt A's task.

### Cleanup

Each runtime retains the proven order:

1. close its `IVirtualInputDevice` exactly once
2. verify its input disappears while its VirtualDevice remains alive
3. locate/remove only its recorded task ID after verifying expected display and target package
4. close only its VirtualDevice, removing its display
5. remove only its recorded association
6. release its remote Surface copy and clear its references

Cleanup steps and completion flags are stored inside that runtime. Repeating stop(B) observes completed steps and does not advance A. A failure in one B cleanup step is recorded, later safe B steps are still attempted in order, and no global fallback cleanup is allowed.

### Partial-start failure

Every resource is recorded in B's runtime immediately after creation. If B fails after any intermediate step, B transitions to `FAILED` and executes B's cleanup state machine using only recorded B resources. The registry entry remains addressable until cleanup finishes or reports a scoped cleanup failure.

A remains registered, active, rendered, and touchable. The service does not call a global `stopInternal`, close shared state, remove an unverified task, destroy the UserService, or retry B automatically.

## Input isolation

Each proof pane maps local SurfaceView coordinates to its own target's `900 x 675` geometry and calls `sendTouch(itsSessionId, ...)`. The remote registry resolves that ID to exactly one returned `IVirtualInputDevice`, which is already associated with that runtime's display.

The established semantics remain:

- one pointer, ID 0
- DOWN pressure `255f`
- MOVE/UP use existing pressure behavior
- CANCEL action `3`
- CANCEL uses PALM tool type `5`; other actions use FINGER
- no global-focus workaround

Unknown, stopped, starting, or mismatched session IDs fail without sending input. There is no fallback to another active input device. Input B can therefore never route to A through a current/global pointer.

## Task isolation and duplicate-target limit

Every runtime owns its pre-launch snapshot and recorded task ID. Startup requires one unambiguous new task matching explicit package, explicit component, and that session's display. Cleanup revalidates recorded task ID, display, and package after internal activity navigation before removal.

The architecture does not use target identity as session identity. Nevertheless, DWT-VDM-005 proves only distinct Waze and Calculator targets. Concurrent sessions for the same package/component remain empirically unproven. A future `EmbeddedWorkspaceRunner` must reject duplicate target identities before execution until a separate milestone proves task selection, app launch-mode behavior, retention, input, and cleanup for same-target duplicates.

## Remote process and binder death

### Orderly final teardown

The connection manager removes the UserService only after zero leases and an empty registry. The remote `destroy()` hook performs best-effort cleanup of every remaining entry before process exit. It is never invoked as part of one session's normal close.

### Unexpected remote death

Shizuku 13.1.5 links the cached service binder to death and calls `onServiceDisconnected` for registered client callbacks. The connection manager handles that notification once and fans it out to every session:

- clear the dead binder
- mark all non-terminal local sessions `REMOTE_DIED`
- disable touch/start/stop calls against the dead binder
- retain immutable ownership receipts returned by successful starts
- do not auto-restart sessions or guess that cleanup completed

Binder-owned VirtualDevices and inputs may be removed by system death handling, but the design does not treat that as proof that shell-created associations or target tasks were removed. Such a run is `RECOVERY_REQUIRED`, not a clean baseline. New starts remain blocked until a read-only ownership audit establishes baseline or a separately designed scoped recovery consumes the recorded receipts. General crash recovery is not implemented or claimed by DWT-VDM-005.

App-process death is likewise not claimed as clean proof. Normal Activity/Compose disposal must use the explicit close contract; the bounded device smoke exercises orderly cleanup.

## Minimal proof UI

Add one experimental route named `Embedded Dual App (Experimental)`. It contains two fixed proof panes:

- pane A: existing `WAZE_EMBEDDED_TARGET`
- pane B: existing `CALCULATOR_EMBEDDED_TARGET`
- one independent SurfaceView and `EmbeddedAppSession` per pane
- visible per-session status, display ID, start, and stop controls
- one shared connect state supplied by the connection manager

The panes may be stacked or placed side by side according to available host space. Their layout is fixed and diagnostic. It is not a Workspace renderer, does not consume normalized bounds, and does not establish future placement policy.

Existing Waze and Calculator experimental screens remain thin consumers. They receive only the mechanical migration needed to use session IDs and the shared connection manager; their target constants and product UI are not redesigned.

## One bounded S23 device proof

After focused tests, build/signing gates, and target availability checks pass, run exactly one dual-session smoke on a dynamically resolved `SM-S918B / dm3q / Android 16 / API 36` endpoint. Do not hard-code an ADB serial.

Before starting, record:

- DWT host display ID
- active associations, VirtualDevices, displays, and virtual inputs
- existing Waze and Calculator task IDs
- exact installed availability of the existing Waze and Calculator components

Then:

1. Open `Embedded Dual App (Experimental)`.
2. Start Waze A and verify `ACTIVE`, trusted display A, input A bound to display A, exact Waze task on A, and rendered pixels.
3. Start Calculator B.
4. Verify A and B are simultaneously `ACTIVE`; display A and B are distinct from each other and the host; each input descriptor matches its display; Waze remains on A; Calculator is on B; DWT remains on its pre-start host display.
5. Interact with Waze A and observe map movement without Calculator change.
6. Enter `7 x 8 = 56` through Calculator B without Waze change.
7. Send one bounded CANCEL through one pane and verify it is exception-free and does not affect the other session.
8. Stop Calculator B only.
9. Verify B input/task/device/display/association are gone while A remains `ACTIVE`, rendered on the same display A, and its input remains registered.
10. Perform one post-B-cleanup Waze interaction and observe response.
11. Confirm DWT is still on the original host display.
12. Stop Waze A.
13. Verify no probe association, VirtualDevice, VDM display, touchscreen, Waze probe task, or Calculator probe task remains.
14. Verify the shared UserService is removed only after the final lease closes.

No retry, reverse-order device run, alternate target, focus workaround, or second smoke occurs in this milestone. Reverse stop order and partial-failure behavior are covered with focused state/ownership tests.

## Fail-closed conditions

The future implementation stops at the first evidence that:

- a second session cannot coexist with A
- Shizuku connection lifetime cannot be safely centralized
- B startup changes A ownership/state
- B touch reaches A
- B cleanup changes A resources or state
- association ownership cannot be proven per session
- task selection is ambiguous
- one session failure requires a service-global reset
- remote close/destroy is triggered while another lease is active
- the selected per-session VDM topology is invalid on the S23 firmware

It records the violated assumption and does not invent an alternate topology, retry, or workaround within DWT-VDM-005.

## Test strategy for implementation

Focused pure/state tests must prove:

1. app-generated IDs are opaque and unique; target identity is not used as the key
2. duplicate active ID start is rejected without allocating again
3. unknown ID touch/stop fails closed
4. two distinct IDs can both reach `ACTIVE`
5. association/device/display/input/task/Surface/cleanup fields belong to separate runtime records
6. touch(A) invokes only A input and touch(B) only B input
7. stop(B) leaves A state and ownership unchanged
8. cleanup(B) never advances cleanup(A)
9. B failure at every startup boundary cleans only the resources already recorded for B
10. repeated stop(B) is idempotent
11. final stop(A) leaves an empty registry and permits final service removal
12. one app-side lease close does not unbind/remove a service still leased by another session
13. `connect()` acquires the session's lease before bind participation; `start()` reuses it and never establishes connection ownership
14. a connected-but-never-started session releases its lease cleanly, while closing never-started B cannot remove the service still leased by connected A
15. final lease removal requires an authoritative `getServiceState()` result proving zero active, starting, stopping, cleanup-incomplete, or live-resource sessions
16. failed service-state query, uncertain binder, or any reported live/potentially-live remote entry prevents `remove=true` even when local lease count is zero
17. service-state aggregation counts a failed or terminal-looking entry with retained resources as live and does not expose full registry/session identities unnecessarily
18. binder death marks every active client `REMOTE_DIED`, blocks new operations, and reports recovery required without claiming cleanup or authorizing removal
19. task selection excludes all pre-existing tasks, including A, and records only the exact B component/display task
20. existing single-session Waze/Calculator model, input, target, and lifecycle tests remain green after mechanical migration
21. no Workspace execution/planning model enters `feature/embeddedapp`
22. no Waze or Calculator package/component constant enters generic runtime
23. proof controller does not hard-code exactly two sessions into the registry/connection implementation; exactly two exists only in probe UI/test composition

Android integration tests/device evidence remain necessary for actual simultaneous VDM/input/task behavior, but they do not replace these ownership tests.

## Workspace boundary and future consumption

`feature/embeddedapp` remains target-independent and Workspace-independent. It consumes only:

```text
EmbeddedAppSessionId
EmbeddedAppTarget
Surface
touch events
```

It must not depend on `EmbeddedWorkspacePlan`, `WorkspaceRunPlanner`, `WorkspaceRunMode`, `NormalizedBounds`, Room, or Workspace placement.

A later `EmbeddedWorkspaceRunner` may iterate a validated plan, choose a virtual-geometry policy, create one opaque session ID per plan item, supply one Surface/container per item, and coordinate multiple generic sessions. The runner owns the mapping from source cells/normalized bounds to containers; sessions continue to know nothing about Workspace identity or placement.

DWT-VDM-005 does not implement that runner because it first must prove that the lower-level session primitive can coexist and clean up independently. A pure plan plus an unsafe singleton runtime would not constitute executable Embedded Workspace architecture.

## What remains unproven after PASS

A PASS proves two distinct targets can be active concurrently with isolated session, association, VDM, display, input, task, Surface, and cleanup ownership on the tested S23 firmware. It does not prove:

- simultaneous parallel startup
- same-package/component duplicate sessions
- arbitrary N-session performance or system limits
- Embedded Workspace execution or normalized placement
- dynamic geometry, resize, density, or DPI policy
- mixed Classic/Embedded items
- crash recovery to zero-orphan baseline
- audio, clipboard, IME, keyboard, mouse, or multi-touch behavior
- production UX or persistence of run mode

## Architectural self-review

The selected design contains:

- no global `currentSession` or singleton mutable VDM resource set
- no package/component-derived session identity
- no Waze/Calculator branch in generic runtime
- no single-session close that invokes global destroy/remove
- explicit per-session association and task ownership
- session-keyed touch routing
- explicit process-level bind leases and final removal conditions
- authoritative remote service-state proof before final UserService removal
- fail-closed teardown when remote emptiness cannot be proven
- short registry locking plus per-session serialization
- no Workspace model in `feature/embeddedapp`
- no hard-coded two-entry registry limit
- an explicit same-target proof limitation
- no Workspace geometry or renderer work

The proof UI alone contains two fixed panes because the milestone needs two observable sessions. The runtime contracts and registry remain keyed collections without an exactly-two constraint.
