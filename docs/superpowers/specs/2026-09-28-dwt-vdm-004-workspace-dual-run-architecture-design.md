# DWT-VDM-004: Workspace Dual-Run Architecture

## Purpose

DWT-VDM-004 establishes a runtime-only architectural bridge from the existing Workspace domain to two execution paths:

```text
                 SAME PERSISTED WORKSPACE
                           |
                  runtime run choice
                           |
                 +---------+---------+
                 |                   |
              CLASSIC             EMBEDDED
                 |                   |
       existing launch path   pure execution plan
```

This milestone stops at planning. It does not implement `EmbeddedWorkspaceRunner`, start `EmbeddedAppSession`, bind Shizuku, create a VirtualDevice or display, render a SurfaceView, launch an application, or introduce multi-session lifecycle behavior.

The central invariant is that one persisted Workspace remains the only layout source. Classic and Embedded are runtime interpretations of that same Workspace. There is no second database model, duplicated snapshot, persisted execution mode, or per-item mixed mode.

## Current architecture

### Persisted source of truth

The persisted source is `workspace.persistence.domain.Workspace`, stored by Room as one `WorkspaceEntity` row in the `workspaces` table. The entity contains Workspace metadata and one serialized `WorkspaceCanvas` in `canvasJson`. `WorkspaceEntityMapper` maps between the entity and the same domain object; `WorkspaceCanvasSerializer` owns canvas serialization.

The layout hierarchy is:

```text
Workspace
  id, name, metadata
  canvas: WorkspaceCanvas
    cells: List<WorkspaceCell>
      id
      bounds: NormalizedBounds
      app: AssignedApp?
        packageName
        activityName?
        label
```

`WorkspaceLibraryItem` is a UI/library projection of this same Workspace. It carries the same ID, name, canvas, and metadata; `Workspace.toLibraryItem()` performs the projection. It is not a second persisted model.

Room currently has exactly one Workspace entity type. DWT-VDM-004 does not alter `WorkspaceEntity`, `DexWorkspaceDatabase`, its schema version, migrations, DAO, serializer, repository, or mappers.

### Existing Classic pipeline

The Home route supplies a `WorkspaceLibraryItem` to `WorkspaceLaunchViewModel`. The current Classic path is:

```text
WorkspaceLibraryItem
  -> WorkspaceLaunchRequestFactory.create(id, name, canvas)
  -> LaunchReadiness
  -> WorkspaceLaunchRequest
  -> AndroidWorkspaceLaunchRuntime
  -> AndroidWorkspaceLauncher
  -> AndroidSingleAppLauncher
  -> ActivitySingleAppLaunchPlatform
  -> ActivityOptions / DeX activity launch
```

`WorkspaceLaunchRequestFactory` validates the canvas, rejects empty or invalid cells, checks the target limit, resolves installed applications to an explicit `AppIdentity`, preserves `canvas.cells` list order as `AppLaunchTarget.order`, and returns `LaunchReadiness.Ready` only when every target is launchable.

`AndroidWorkspaceLauncher` sorts by `AppLaunchTarget.order` and sequences the existing single-app launches. `AndroidSingleAppLauncher` converts normalized Workspace bounds to display work-area pixel bounds. `ActivitySingleAppLaunchPlatform` performs the Android launch.

This pipeline remains the Classic baseline. DWT-VDM-004 does not replace its readiness rules, bounds calculation, sequencing, diagnostics, ActivityOptions, or result aggregation.

## Considered approaches

### Selected: share the resolved Workspace request, then branch at planning

Use the existing `WorkspaceLaunchRequestFactory` as the common validation and target-resolution boundary. Add the missing source cell identity to `AppLaunchTarget`, then:

- Classic receives the same `WorkspaceLaunchRequest` and delegates to the existing runtime unchanged.
- Embedded maps that resolved request through a pure `EmbeddedWorkspacePlanner` into an `EmbeddedWorkspacePlan`.

This avoids duplicating validation or component resolution, keeps Classic semantics intact, and preserves source identity, bounds, and order for future Embedded execution.

### Rejected: plan directly from persisted Workspace

A planner over raw `Workspace` or `WorkspaceLibraryItem` would be pure only until it needed to resolve nullable `activityName`. Reimplementing installed-app resolution would duplicate `WorkspaceLaunchRequestFactory` and allow Classic and Embedded readiness behavior to drift.

### Rejected: introduce a complete execution coordinator now

A coordinator that executes both modes would require defining Embedded lifecycle and multi-session semantics that are explicitly unproven. It would also disturb the proven Classic UI/runtime path solely to create symmetry. DWT-VDM-004 therefore defines contracts and planning results, while deferring execution orchestration.

## Runtime-only run choice

Introduce a domain-level runtime enum:

```kotlin
enum class WorkspaceRunMode {
    CLASSIC,
    EMBEDDED,
}
```

and an invocation value equivalent to:

```kotlin
data class WorkspaceRunRequest(
    val workspaceId: String,
    val workspaceName: String,
    val canvas: WorkspaceCanvas,
    val mode: WorkspaceRunMode,
)
```

These types belong in a Workspace execution/planning package, not in persistence, `WorkspaceLibraryItem`, or `EmbeddedAppTarget`. The planning input uses Workspace domain values rather than depending on the UI/library projection; a higher-level caller may adapt a `WorkspaceLibraryItem` at the boundary. `WorkspaceRunMode` represents a choice made for one execution request. It is not saved to Room, SharedPreferences, a Workspace, or an item.

The mode applies to the entire Workspace. DWT-VDM-004 defines no mixed Classic/Embedded item selection.

## Common resolved request boundary

The existing `WorkspaceLaunchRequest` already carries Workspace ID/name plus resolved targets with explicit app identity, normalized bounds, and deterministic order. One gap prevents it from preserving complete Workspace item identity: `AppLaunchTarget` does not carry the source `WorkspaceCell.id`.

The future implementation should add:

```kotlin
data class AppLaunchTarget(
    val sourceCellId: String,
    val identity: AppIdentity,
    val bounds: NormalizedBounds,
    val order: Int,
)
```

`WorkspaceLaunchRequestFactory` sets `sourceCellId = cell.id`. The Classic launchers continue to consume `identity`, `bounds`, and `order`; adding source identity must not change their launch semantics.

This is a common Workspace execution field, not an Embedded runtime field. It belongs on the resolved Workspace target because both execution paths may need diagnostics and stable correspondence to the source layout.

## Planning boundary

A small run-planning boundary should produce one of these outcomes:

```kotlin
sealed interface WorkspaceRunPlanningResult {
    data class Classic(val request: WorkspaceLaunchRequest) : WorkspaceRunPlanningResult
    data class Embedded(val plan: EmbeddedWorkspacePlan) : WorkspaceRunPlanningResult
    data class Rejected(val readiness: LaunchReadiness) : WorkspaceRunPlanningResult
}
```

Conceptually:

```text
WorkspaceRunRequest
  -> existing WorkspaceLaunchRequestFactory
  -> non-Ready: Rejected(existing readiness)
  -> Ready + CLASSIC: Classic(the exact resolved request)
  -> Ready + EMBEDDED: Embedded(pure planner.plan(the same resolved request))
```

The Classic result contains the unchanged resolved request and is suitable for delegation to the current `WorkspaceLaunchRuntime`. DWT-VDM-004 does not replace the current Home launch callback or execute this result in production UI. The boundary is introduced and unit-tested without switching the live Classic route.

The Embedded mapping itself remains a pure transformation:

```text
WorkspaceLaunchRequest -> EmbeddedWorkspacePlan
```

It requires no Android `Context`, PackageManager, Room repository, Compose state, Binder, Shizuku, Surface, VirtualDevice, or `EmbeddedAppSession`.

## Embedded plan model

The declarative model is equivalent to:

```kotlin
data class EmbeddedWorkspacePlan(
    val workspaceId: String,
    val workspaceName: String,
    val items: List<EmbeddedWorkspacePlanItem>,
)

data class EmbeddedWorkspacePlanItem(
    val sourceCellId: String,
    val packageName: String,
    val componentName: String,
    val normalizedBounds: NormalizedBounds,
    val order: Int,
)
```

Every field is derived from the common resolved request:

- `workspaceId` and `workspaceName` preserve source Workspace identity.
- `sourceCellId` preserves item identity.
- package and component preserve the already-resolved explicit `AppIdentity`.
- `normalizedBounds` is the exact source object/value; the planner does not convert it to pixels.
- `order` is the existing deterministic index assigned from `WorkspaceCanvas.cells`.

The Embedded planner must not introduce a product/domain readiness rule beyond invariants already guaranteed by the existing Workspace domain and `WorkspaceLaunchRequestFactory`. It preserves fields such as Workspace name exactly. A defensive constructor assertion is allowed only when the current upstream domain or resolved-request contract already guarantees that invariant; it must not make Classic and Embedded disagree for the same Workspace.

The current resolved-request contract already guarantees non-blank Workspace ID/name, a non-empty target list, unique target orders, non-negative orders, and explicit launchable component identities. The plan may assert those existing invariants and the new structural invariant that source cell IDs remain unique, because current canvas validation guarantees unique cell IDs. The planner emits items sorted by `order`, matching current Classic execution ordering. It does not invent spatial or alphabetical sorting.

## Ownership boundaries

### Workspace/layout layer owns

- Workspace and cell IDs
- persisted canvas and item sequence
- assigned package/activity identity
- `NormalizedBounds`
- validation of empty, duplicate, overlapping, or invalid cells
- runtime Workspace-level mode selection
- the future placement of Embedded surfaces/containers

### Embedded plan owns

- an immutable, validated execution description for one Workspace
- stable correspondence between source cells and resolved components
- preserved normalized placement and deterministic order

The plan does not own Android execution resources or virtual display geometry.

### `EmbeddedAppTarget` owns later

- one resolved package
- one explicit component
- one app's virtual execution geometry

`EmbeddedAppTarget` must not gain Workspace ID, source cell ID, `WorkspaceLibraryItem`, normalized bounds, placement coordinates, Room IDs, or `WorkspaceRunMode`. A later runner will combine a plan item with a separately chosen virtual-geometry policy to create an `EmbeddedAppTarget` and position its host container. DWT-VDM-004 defines neither policy.

## Geometry separation

Workspace bounds and VDM geometry are different coordinate systems:

```text
NormalizedBounds
  -> future Workspace container placement

virtual width/height/density
  -> future EmbeddedAppTarget execution environment
```

The proven `900 x 675 @ 320 dpi` Waze/Calculator configuration is evidence for those adapters only. The plan preserves normalized bounds and intentionally carries no VDM width, height, density, aspect ratio, scaling, or bounds-to-display conversion.

## Validation and failure model

Planning reuses current validation rather than creating silent fallbacks:

- Empty canvas -> `LaunchReadiness.EmptyWorkspace` -> `Rejected`.
- Empty cells -> `LaunchReadiness.EmptyCells` -> `Rejected`.
- Duplicate cell IDs, overlapping cells, or invalid canvas -> `LaunchReadiness.InvalidCanvas` -> `Rejected`.
- Too many cells -> `LaunchReadiness.TooManyTargets` -> `Rejected`.
- Missing package -> `LaunchReadiness.MissingApplications` -> `Rejected`.
- Missing, ambiguous, or non-launchable component after current deterministic resolution -> `LaunchReadiness.NonLaunchableApplications` -> `Rejected`.
- Duplicate resolved target identities are allowed when they originate from distinct valid cells; source cell identity remains unique. No new restriction is invented.
- Unsupported item type does not currently exist: `WorkspaceCanvas` contains only `WorkspaceCell`. If the domain gains new item variants later, the run-planning boundary must reject an unrecognized variant explicitly before planning.

`AssignedApp.activityName` may be null in persisted data. The common factory already resolves such assignments deterministically from `InstalledAppCatalog`; the Embedded plan only accepts the resulting explicit component. The pure planner never guesses or performs discovery.

Planning validation errors are complete before execution and contain no partially executable plan. Runtime failures such as VirtualDevice creation, activity launch, focus, rendering, or cleanup are outside this model and remain future runner concerns.

## Dependency direction

The intended dependency flow is:

```text
persistence Workspace
  -> library/domain projection
  -> existing launch request factory and readiness model
  -> runtime run-planning contracts
       -> CLASSIC: existing WorkspaceLaunchRequest
       -> EMBEDDED: pure EmbeddedWorkspacePlan
```

Workspace planning may depend on existing Workspace domain and launcher models. It must not depend on Android platform launch classes or `feature/embeddedapp`. Conversely, `feature/embeddedapp` remains unaware of Workspace models.

## Why no execution coordinator yet

DWT-VDM-004 should not install a production `WorkspaceExecutionCoordinator`. The live Classic path already works and an Embedded coordinator cannot execute safely until multi-session lifecycle, partial startup, rollback, cleanup, and rendering are designed and proven.

The contracts make a later coordinator straightforward:

```text
WorkspaceExecutionCoordinator (future)
  -> plan(runRequest)
  -> Classic: existing runtime.launch(request)
  -> Embedded: EmbeddedWorkspaceRunner.run(plan)
```

Deferring the coordinator prevents an artificial rewrite of Classic and prevents a planning milestone from acquiring runtime responsibilities.

## Why multi-session is not required

The output is immutable data. Creating an `EmbeddedWorkspacePlan` for multiple items does not create or imply concurrent `EmbeddedAppSession` instances. DWT-VDM-004 proves only that a Workspace can be translated into a complete plan while preserving identity, placement, and order.

The ability to execute one plan item does not establish how multiple sessions share Shizuku, VirtualDevices, displays, surfaces, input, focus, rollback, or cleanup. Those remain explicit later milestones.

## Why persistence does not change

The existing Workspace already contains everything this milestone must preserve: Workspace identity, ordered cells, assigned app identity, and normalized bounds. Run mode is invocation state and the Embedded plan is derived ephemeral data. Persisting either would duplicate source-of-truth state and create migration and synchronization risks without serving this milestone.

Therefore there is no Room entity, column, DAO change, serializer field, migration, schema-version update, or preference.

## UI scope

No production UI is required. The current Home launch action remains Classic and unchanged. DWT-VDM-004 only defines the runtime API a later UI may call with `WorkspaceRunMode.CLASSIC` or `WorkspaceRunMode.EMBEDDED`.

There is no mode selector, default preference, per-item selector, app picker, or Embedded workspace screen in this milestone.

## Test strategy

All verification is local unit/static testing; no S23 smoke is required.

Focused tests should prove:

1. `WorkspaceRunMode` exists only in runtime planning code and is absent from Room entities, serializers, persisted Workspace, and `EmbeddedAppTarget`.
2. A Classic run request uses `WorkspaceLaunchRequestFactory` readiness and returns the exact resolved `WorkspaceLaunchRequest` without changing targets, order, identity, or bounds.
3. Embedded planning consumes the same resolved request produced from the same `WorkspaceLibraryItem`/canvas as Classic.
4. `sourceCellId`, package, explicit component, and normalized bounds are preserved exactly.
5. Plan items preserve existing `AppLaunchTarget.order` deterministically.
6. Null source activity names are resolved only by the existing request factory; the pure planner receives an explicit component.
7. Empty, invalid, missing, and non-launchable inputs return the corresponding deterministic `Rejected` readiness and produce no partial plan.
8. Duplicate cell identity is rejected by current canvas validation; distinct cells targeting the same component remain distinct plan items.
9. Embedded planning adds no readiness/business validation beyond the existing domain/factory contract; the same factory result cannot be accepted for Classic and rejected for Embedded.
10. `EmbeddedWorkspacePlanner` has no Android, Room, Shizuku, Surface, Binder, VirtualDevice, Compose, or `EmbeddedAppSession` dependency.
11. Planning invokes no launcher and creates no execution resource.
12. Existing Classic launcher tests continue to pass after adding `sourceCellId`.
13. Static source-boundary checks prove no changes under `feature/embeddedapp`, `feature/embeddedwaze`, or `feature/embeddedcalculator`, and no Room schema/migration changes.

The implementation plan should use focused tests first, followed by the affected Workspace launcher suite and `git diff --check`. Device testing is unnecessary unless implementation unexpectedly introduces an Android integration dependency; that discovery would be a design violation and should stop the milestone.

## Deferred work

DWT-VDM-005 and later must separately design and prove:

- `EmbeddedWorkspaceRunner`
- multiple simultaneous or sequential `EmbeddedAppSession` ownership
- Shizuku and VirtualDevice sharing policy
- one-versus-many VDM/display topology
- SurfaceView/container rendering and normalized-bounds placement
- virtual geometry and density selection
- partial startup, rollback, multi-app cleanup, and idempotency
- Z-order, focus, audio, keyboard/IME, mouse, multi-touch, and task lifecycle
- product UX for runtime mode selection
- any future mixed-mode requirement

No deferred concern is represented as an implemented capability in DWT-VDM-004.

## Expected implementation boundary

The later implementation plan may add a focused Workspace execution/planning package, pure models/planner, tests, and the minimal `sourceCellId` extension in the existing resolved request/factory and related fixtures. It must not modify:

- Room schema, migrations, entities, serializers, or repository behavior
- live Classic navigation or launcher behavior
- `feature/embeddedapp`
- `feature/embeddedwaze`
- `feature/embeddedcalculator`
- licensing, backend, signing, or release configuration

## Acceptance

DWT-VDM-004 is complete when the codebase can represent a runtime-only Workspace-level Classic/Embedded choice; Classic planning yields the existing resolved launch request unchanged; Embedded planning yields a pure, validated plan from that same request with exact item identity, explicit component, normalized bounds, and order; invalid content fails closed through existing readiness semantics; no runtime resources are created; persistence and all embedded runtime features remain unchanged; and tests prove the boundary without a device.

This architecture permits one persisted Workspace to be interpreted by the existing Classic runner today and by a future `EmbeddedWorkspaceRunner` later, without creating separate persisted Workspace models or claiming multi-session readiness.
