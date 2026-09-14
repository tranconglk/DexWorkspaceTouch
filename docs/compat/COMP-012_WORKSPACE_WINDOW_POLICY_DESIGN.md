# COMP-012 — Workspace Window Policy Design

Date: 2026-09-15

Branch: `release/1.0-beta`

Baseline: `961fd0e9a33d971a2fac53c78fa62c5442d47499` (`COMP-011 document third-party task reposition API limits`)

Scope: design and domain contract only. No production launcher behavior, flags, UI, version, publication, or commit change.

## A. Goals

Define exactly two per-application workspace policies:

1. `NEW_WINDOW`: use the existing production creation path and request the saved bounds/display. Workspace geometry has priority.
2. `REUSE_FOREGROUND_ONLY`: request reuse semantics to preserve an existing task/state when Android chooses to reuse it. It does not promise resize, reposition, or display migration.

There is deliberately no `REUSE_AND_REPOSITION`. COMP-011 established that a normal APK cannot reposition an arbitrary existing third-party task.

This milestone is **report-only**. Adding even a pure enum to the current persisted model would require coordinated changes to the strict canvas serializer, workspace schema, single-workspace transfer, library transfer, mappers, fixtures, and tests. Splitting that atomic compatibility change across milestones would create an incomplete model contract.

## B. Platform constraints

The design accepts the established COMP-010/011 constraints rather than attempting to bypass them:

- `NEW_TASK | MULTIPLE_TASK` is the current creation-oriented production flag category.
- `NEW_TASK` alone can cause Android to reuse the same task and retain its activity/state.
- a reused task can retain old geometry and can remain on the wrong display;
- launch bounds/display options are requests, not post-reuse task mutation;
- third-party task identity, bounds, and display cannot be reliably enumerated by the production app;
- no public API on the current S22 API 36 can repair a reused foreign task;
- API 37 `AppTask.moveTaskTo()` remains caller-owned and permission-restricted.

Therefore a policy expresses **desired launch semantics**, not a guaranteed runtime outcome.

## C. `WindowLaunchPolicy`

Recommended pure Kotlin domain type:

```kotlin
enum class WindowLaunchPolicy {
    NEW_WINDOW,
    REUSE_FOREGROUND_ONLY,
}
```

Placement recommendation:

```text
workspace/designer/model/WindowLaunchPolicy.kt
```

The policy belongs to each assigned application target:

```kotlin
data class AssignedApp(
    val packageName: String,
    val activityName: String?,
    val label: String,
    val windowLaunchPolicy: WindowLaunchPolicy = WindowLaunchPolicy.NEW_WINDOW,
)
```

`WorkspaceCell.app` is the current persisted per-target boundary, so this preserves mixed workspace intent without adding a second parallel target model. The enum must stay free of Android and Compose types. Persist its stable symbolic name, never its ordinal.

## D. Default and backward compatibility

The sole default is `NEW_WINDOW`.

- New `AssignedApp` values default to `NEW_WINDOW`.
- A legacy schema-1 app object with no policy decodes to `NEW_WINDOW`.
- Existing rows retain current ordering, bounds, identity, and launch behavior.
- No user migration is required.
- New schema-2 writes include an explicit `"windowLaunchPolicy":"NEW_WINDOW"` or `"REUSE_FOREGROUND_ONLY"` for every assigned app.

Missing is only accepted as legacy schema-1 input. For schema 2, the field is required. An unknown schema-2 enum string should fail closed as invalid/unsupported workspace data rather than silently changing launch semantics. A future schema version must define its own migration rule.

## E. Identity model

The future reuse candidate identity is:

```text
packageName + resolved launcher activity/component
```

This matches the current explicit-component launch contract:

- persisted `AssignedApp` stores `packageName` plus nullable `activityName`;
- `WorkspaceLaunchRequestFactory` resolves a missing activity deterministically from `InstalledAppCatalog`;
- a ready `AppLaunchTarget.identity` contains the resolved package/activity pair;
- `AppIdentity.toStableKey()` already represents both fields.

Package-only matching is insufficient because one package may expose several launcher activities or document behaviors. Android `taskId` must never be persisted: it is runtime-only, unstable across task/session/process/device changes, and unavailable through a reliable third-party production API.

## F. Planner model

Recommended pure domain seam:

```kotlin
enum class PlannedLaunchAction {
    CREATE_NEW,
    REQUEST_REUSE,
}

data class PlannedAppLaunch(
    val target: AppLaunchTarget,
    val requestedPolicy: WindowLaunchPolicy,
    val action: PlannedLaunchAction,
    val savedOrder: Int,
)

data class WorkspaceExecutionPlan(
    val workspaceId: String,
    val savedOrder: List<PlannedAppLaunch>,
    val executionOrder: List<PlannedAppLaunch>,
)

fun interface WorkspaceExecutionPlanner {
    fun plan(request: WorkspaceLaunchRequest): WorkspaceExecutionPlan
}
```

The exact file/package can follow the existing `workspace/launcher` organization, but these types remain pure Kotlin. The request factory transfers each persisted per-app policy onto the resolved `AppLaunchTarget`; the planner maps:

| Desired policy | Planned semantic action |
|---|---|
| `NEW_WINDOW` | `CREATE_NEW` |
| `REUSE_FOREGROUND_ONLY` | `REQUEST_REUSE` |

The planner does not contain Android flags and does not claim reuse succeeded. Lists should be defensive immutable snapshots (`toList()` on construction) following the existing immutable request/result conventions.

Desired policy, planned action, and observed outcome are separate concepts:

```text
REUSE_FOREGROUND_ONLY -> REQUEST_REUSE -> start request accepted/failed
```

Current public architecture cannot reliably promote “accepted” to `REUSED`, `REUSED_CORRECT_DISPLAY`, or `NEW_TASK_CREATED`. Do not add those strong outcome values until a supported observation source exists.

## G. Execution order

Recommendation for mixed-policy workspaces:

1. execute all `CREATE_NEW` targets first, stable-sorted by saved order;
2. execute all `REQUEST_REUSE` targets second, stable-sorted by saved order.

COMP-010 showed that a reused task can become the cascade seed and affect later new-window placement, whereas create-first was more predictable in the measured run. Reuse-last also lets reused windows come to the foreground after geometry-critical windows have been requested.

This is a planner rule, not a mutation of persisted layout. `savedOrder` continues to express the visual/user model. `executionOrder` is derived at runtime. For the example:

| Saved app | Policy | Planned action | Execution group |
|---|---|---|---:|
| Chrome | `REUSE_FOREGROUND_ONLY` | `REQUEST_REUSE` | 2 |
| Browser | `REUSE_FOREGROUND_ONLY` | `REQUEST_REUSE` | 2 |
| Calculator | `NEW_WINDOW` | `CREATE_NEW` | 1 |

The execution sequence is Calculator, Chrome, Browser, while the saved cell order remains Chrome, Browser, Calculator. Within each group the original order remains stable.

This recommendation should be encoded and unit-tested in the future planner milestone before enabling runtime reuse. Device diagnostics should validate that it remains preferable across the target One UI versions; the stored model must not depend on it.

## H. Wrong-display semantics

`REUSE_FOREGROUND_ONLY` carries this mandatory limitation:

> If a matching task exists on another display, Android may reuse that task and keep its current display, position, and size. DexWorkspaceTouch cannot reliably detect or correct that condition with public APIs.

Consequences:

- no display correctness guarantee;
- no geometry correctness guarantee;
- no automatic hidden task enumeration;
- no claim that tapping a DeX workspace necessarily moves the app to DeX;
- a successful `startActivity` call only means the launch request was accepted without a mapped exception.

## I. Persistence and migration

Current persistence facts:

- Room database version is 2;
- `WorkspaceEntity` stores one `canvasJson` string and a workspace `schemaVersion`;
- `DeterministicWorkspaceCanvasJsonSerializer` supports only schema 1 and rejects unknown keys;
- app identity/label are nested under each cell's `app` object;
- the current Room 1→2 migration only adds `isPinned`.

Minimal safe strategy:

1. Introduce workspace canvas schema 2, not an additional Room column.
2. Keep Room database version 2 because the SQL table shape does not change.
3. Decode existing workspace schema 1 with implicit `NEW_WINDOW`.
4. Encode all new/updated workspaces as schema 2 with explicit stable enum strings.
5. Make repository save/update paths advance the domain workspace schema version consistently.
6. Preserve schema-1 rows lazily until edited/saved; no destructive SQL rewrite is needed.
7. Update persistence tests to prove schema-1 fixtures decode identically and schema-2 round trips policy.

Because no SQL column changes, this is an application-level JSON schema evolution rather than a Room `Migration(2, 3)`. If implementation instead chooses a dedicated normalized target table later, that would require a Room version bump and is not the minimal plan.

Unknown policy strings in schema 2 should produce `WorkspacePersistenceException.SerializationFailure` or an explicit unsupported-value failure. They must not be mapped to `NEW_WINDOW`, because that could silently turn a future safety policy into a create request.

## J. Copy, snapshot, export, and import behavior

Policy is part of `AssignedApp`, so normal Kotlin data-class copies of a canvas retain it automatically unless a caller explicitly replaces the app. The following paths must be covered when implemented:

- `WorkspaceLibraryViewModel.duplicateWorkspace()` currently reuses `source.canvas`; policy will be retained.
- rename/pin/library projection also transport the same canvas and therefore retain policy.
- “create similar” or any future deep copy must copy the entire `AssignedApp`, not reconstruct only package/activity/label.
- UI snapshots/previews render canvas geometry and app label/icon; policy need not affect their visuals, but the underlying canvas must retain it.
- `WorkspaceExport` and `WorkspaceImportPayload` already carry `WorkspaceCanvas`; canvas schema 2 must carry policy.
- `DeterministicWorkspaceTransferSerializer` currently hard-codes format/workspace schema 1 and must gain a compatible schema-2 path.
- `DeterministicWorkspaceLibraryBundleSerializer` delegates to the single-workspace serializer and currently also requires schema 1; it must accept/emit the coordinated version.
- importing legacy `.dwt`/`.dwtbundle` schema 1 maps missing policy to `NEW_WINDOW`.
- exporting schema 2 must explicitly include policy, and round-trip tests must prove it survives.

Do not silently strip policy on export, import, duplication, template assignment, editor reassignment, or library bundle operations.

## K. Car Mode and Floating Dock impact

Home, Car Mode, and Floating Dock already converge on the shared workspace pipeline:

```text
WorkspaceRepository
  -> WorkspaceLaunchRequestFactory
  -> WorkspaceLaunchRuntime / AndroidWorkspaceLauncher
```

`RepositoryCarWorkspaceLaunchPlatform` loads the same `Workspace`, uses the same request factory, and invokes the same runtime. Therefore the per-target policy and planner must live in the shared workspace pipeline. No Car-specific policy enum, catalog override, or alternate executor is needed.

When later enabled, the same saved workspace produces the same plan whether launched from Home, CarScreen, or Floating Dock. Car shortcut configuration continues to reference only `workspaceId`.

## L. Future executor mapping

The Android boundary maps semantic actions to flag categories:

| Planned action | Future Android mapping |
|---|---|
| `CREATE_NEW` | current `NEW_TASK | MULTIPLE_TASK` production path |
| `REQUEST_REUSE` | future `NEW_TASK` reuse-candidate path |

Android `Intent` constants stay in `ActivitySingleAppLaunchPlatform` or a small Android mapper adjacent to it, never in `WindowLaunchPolicy`, Room, transfer, or planner models.

The existing component verification, normalized bounds calculation, display host validation, launch delay, diagnostics session, cancellation behavior, partial-failure aggregation, and sequencing framework remain shared. COMP-012 does not modify any of them.

Future diagnostics can safely record:

- `requestedPolicy`;
- `plannedAction`;
- a non-bitwise `launchFlagsCategory` such as `CREATE_NEW` or `REUSE_CANDIDATE`;
- platform start result already captured by the current diagnostics.

They must not record app content/state or claim a task outcome that cannot be observed.

## M. User-facing wording

No UI is added in COMP-012. Recommended future Vietnamese labels:

- `NEW_WINDOW`: **Mở cửa sổ mới**
- `REUSE_FOREGROUND_ONLY`: **Dùng lại cửa sổ đang mở**

Recommended help text:

> Dùng lại cửa sổ đang mở giúp giữ trạng thái ứng dụng. Android có thể giữ nguyên vị trí, kích thước hoặc màn hình hiện tại.

Global/default presentation should show **Mở cửa sổ mới**. Each app target may override it. A future workspace-level “Áp dụng cho tất cả” is only an editor command that writes the chosen policy to every target; it must not introduce redundant global persisted state or precedence rules.

If a global persisted default is ever added, precedence must be explicit: per-target stored value wins, then workspace default, then product default `NEW_WINDOW`. This extra state is not recommended for the first implementation.

Do not expose `NEW_TASK`, `MULTIPLE_TASK`, task affinity, task ID, or “reposition” promises to users.

## N. Fallback limitations

Decision: **NO AUTOMATIC FALLBACK YET**.

After `REQUEST_REUSE`, production cannot reliably determine whether Android reused a correct-display task, reused a wrong-display task, or created a task. It therefore cannot safely decide that the result is “unusable” and retry `CREATE_NEW`. A retry could still reuse the same task or create an unwanted duplicate.

Only a future supported observation/control boundary could justify automatic fallback. Until then, the user's selected policy is executed once and its documented limitations apply.

## O. Recommended implementation phases

These are proposals only; COMP-012 executes none of them.

### COMP-013A — Domain and persistence

- add `WindowLaunchPolicy` to `AssignedApp`, default `NEW_WINDOW`;
- add policy to resolved `AppLaunchTarget`;
- implement canvas schema 1 legacy decode and schema 2 explicit encode/decode;
- coordinate repository schema version, single/library transfer, templates/copies, and tests;
- keep runtime behavior mapped to current creation path for both values until executor support lands.

### COMP-013B — Planner and executor mapping

- add `WorkspaceExecutionPlanner`, planned action, and separate saved/execution order;
- stable create-first/reuse-second planning;
- map semantic actions to Android flag categories at the platform boundary;
- preserve existing bounds/display/component validation and result aggregation;
- do not add automatic fallback.

### COMP-013C — Editor policy control

- add per-app selector with the approved Vietnamese labels/help;
- default new assignments to `NEW_WINDOW`;
- optionally implement non-persisted “Áp dụng cho tất cả” editor operation;
- preserve touch/accessibility/design-system requirements.

### COMP-013D — Diagnostics and device smoke

- add requested policy/planned action/flag category to diagnostics;
- verify mixed-policy ordering on S22/S23 DeX;
- validate cold/warm and wrong-display behavior without overstating observable outcomes;
- verify Home, Car Mode, and Floating Dock use the identical plan.

## P. Final design decision

1. **Policy scope:** stored per assigned app target (`AssignedApp` in `WorkspaceCell`).
2. **Default:** `NEW_WINDOW`.
3. **Legacy mapping:** schema-1 missing policy → `NEW_WINDOW`, without user action.
4. **Reuse identity:** package name plus deterministically resolved explicit launcher component; never persisted task ID.
5. **Planner action:** `NEW_WINDOW` → `CREATE_NEW`; `REUSE_FOREGROUND_ONLY` → `REQUEST_REUSE`.
6. **Execution order:** stable create-new targets first, then stable reuse-request targets; saved visual order remains unchanged.
7. **Runtime outcome:** only request acceptance/failure can currently be asserted; no fabricated reuse/display outcome.
8. **Wrong display:** explicitly possible and neither pre-detectable nor repairable through current public APIs.
9. **Automatic fallback:** no.
10. **Persistence:** workspace canvas JSON schema 2 with stable enum strings; schema-1 compatibility; no SQL/Room version change required by the minimal design.
11. **Copy/export:** policy travels with the full canvas/assigned target through duplicate, single export, library export, and import.
12. **Car/Dock:** reuse the shared repository → request factory → planner → runtime pipeline; no Car-specific policy.
13. **UI wording:** “Mở cửa sổ mới” and “Dùng lại cửa sổ đang mở,” with explicit geometry/display caveat.
14. **Production readiness:** design is ready for phased implementation, but production reuse is **not enabled** and must not be enabled until domain/persistence, planner/executor, UI, and device-validation phases are completed.

Verification for this report-only milestone:

- `testDebugUnitTest`: PASS
- `lintDebug`: PASS
- `assembleDebug`: PASS
- combined Gradle result: `BUILD SUCCESSFUL` (`55 actionable tasks`, 19 executed, 36 up-to-date)
- no source, schema, launcher, flag, UI, version, or release artifact was changed
