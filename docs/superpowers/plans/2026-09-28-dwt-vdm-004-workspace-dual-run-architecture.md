# DWT-VDM-004 Workspace Dual-Run Architecture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a pure runtime planning boundary that interprets one persisted Workspace as either the existing Classic launch request or an Embedded execution plan without changing persistence or executing Embedded sessions.

**Architecture:** Extend the existing resolved `WorkspaceLaunchRequest` target with its source cell ID, then use `WorkspaceLaunchRequestFactory` as the single validation and component-resolution boundary. A new pure `WorkspaceRunPlanner` returns the unchanged request for Classic or maps it to an immutable `EmbeddedWorkspacePlan`; it does not launch apps, create sessions, or alter the live Home route.

**Tech Stack:** Kotlin, JUnit 4, existing Workspace domain/launcher models, Gradle.

**Spec:** `docs/superpowers/specs/2026-09-28-dwt-vdm-004-workspace-dual-run-architecture-design.md`

## Global Constraints

- Baseline is `c74d8777a4112f28a36939554acdfad09e91358f` on `release/1.0-beta`.
- One persisted Workspace remains the only layout source; do not add Room entities, columns, migrations, serializer fields, preferences, or a separate Embedded Workspace model.
- `WorkspaceRunMode` is runtime-only and applies to the whole Workspace; do not persist it or add per-item mixed modes.
- Reuse `WorkspaceLaunchRequestFactory` for all canvas validation and component resolution; the Embedded path must not add a readiness/business rule that can make Classic and Embedded disagree for the same input.
- Preserve Workspace names and other resolved fields exactly. Defensive assertions may only repeat invariants already guaranteed by current domain/factory contracts.
- Keep normalized Workspace placement separate from virtual-display width, height, and density. Do not place VDM geometry in `EmbeddedWorkspacePlan`.
- Do not add Workspace IDs, source cell IDs, normalized bounds, placement state, Room IDs, or run mode to `EmbeddedAppTarget`.
- Do not create `EmbeddedWorkspaceRunner`, `WorkspaceExecutionCoordinator`, `EmbeddedAppSession`, Binder/Shizuku objects, VirtualDevices, displays, Surfaces, or any execution resource.
- Do not change the live Classic Home/navigation flow, Classic launch behavior, `feature/embeddedapp`, `feature/embeddedwaze`, or `feature/embeddedcalculator`.
- This milestone proves planning only and does not claim multi-session readiness.
- No device smoke is required. Do not commit until the scoped implementation and verification are reviewed; do not push.
- Preserve unrelated existing changes under `experiments/shizuku-task-probe`, `docs/compat/COMP-012R_HISTORY_ASSISTED_REUSE_DESIGN.md`, `.superpowers`, and `AGENTS.md`.

## Review Focus

- **Readiness divergence:** a `LaunchReadiness.Ready` input must yield either `Classic` or `Embedded`, never a new Embedded-only rejection; Task 3 tests both modes over the same source input.
- **Nullable assigned activity:** component discovery must remain exclusively in `WorkspaceLaunchRequestFactory`; Task 3 tests that both modes receive the same deterministically resolved component.
- **Identity loss:** duplicate app components in different cells must retain distinct source cell IDs and order; Tasks 1 and 2 test this mapping.
- **Layer inversion:** runtime planning must consume Workspace domain values without a dependency from a lower-level planner package to `WorkspaceLibraryItem` or UI; Task 3 fixes the request signature and Task 4 runs source checks.
- **Execution leakage:** planning must not call a launcher or reference Android/Embedded runtime resources; Tasks 2 and 4 enforce pure types and dependency boundaries.

---

### Task 1: Preserve source cell identity in the resolved launch request

**Pre-code invariant check:** `WorkspaceCell.init` already requires `id.isNotBlank()`, and `WorkspaceCanvasTest.blankCellIdIsRejected` covers that behavior. Therefore the `AppLaunchTarget.sourceCellId` non-blank assertion repeats an upstream invariant and does not narrow Classic acceptance.

**Files:**
- Modify: `app/src/main/java/com/trancong/dexworkspacetouch/workspace/launcher/model/WorkspaceLaunchRequest.kt`
- Modify: `app/src/main/java/com/trancong/dexworkspacetouch/workspace/launcher/WorkspaceLaunchRequestFactory.kt`
- Modify: `app/src/test/java/com/trancong/dexworkspacetouch/workspace/launcher/WorkspaceLaunchRequestFactoryTest.kt`
- Modify: `app/src/test/java/com/trancong/dexworkspacetouch/workspace/launcher/WorkspaceLaunchModelsTest.kt`
- Modify fixtures at existing `AppLaunchTarget(...)` call sites only as required to compile.

**Interfaces:**
- Consumes: `WorkspaceCell.id`, existing `AppIdentity`, `NormalizedBounds`, and target order.
- Produces: `AppLaunchTarget(sourceCellId: String, identity: AppIdentity, bounds: NormalizedBounds, order: Int)`.

- [ ] **Step 1: Add failing source-identity assertions**

In `WorkspaceLaunchRequestFactoryTest.valid workspace creates deterministic request and preserves bounds`, assert `result.request.targets.map { it.sourceCellId } == listOf("top", "bottom")`. Extend the duplicate-app test to assert the two source IDs remain `left` and `right`. In `WorkspaceLaunchModelsTest`, add a test that a blank `sourceCellId` is rejected.

- [ ] **Step 2: Run the focused tests and observe RED**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "*WorkspaceLaunchRequestFactoryTest" --tests "*WorkspaceLaunchModelsTest" --console=plain
```

Expected: FAIL because `AppLaunchTarget.sourceCellId` does not exist.

- [ ] **Step 3: Add source identity to the resolved model and factory**

Add `sourceCellId: String` as the first named field of `AppLaunchTarget`, with `require(sourceCellId.isNotBlank())`. In `WorkspaceLaunchRequestFactory`, set `sourceCellId = cell.id`. Update existing test fixtures/call sites to supply stable non-blank IDs without changing launch assertions or production launcher logic.

- [ ] **Step 4: Run focused and affected Classic tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "*WorkspaceLaunchRequestFactoryTest" --tests "*WorkspaceLaunchModelsTest" --tests "*AndroidWorkspaceLauncherTest" --tests "*AndroidSingleAppLauncherTest" --tests "*WorkspaceLaunchViewModelTest" --tests "*WorkspaceLaunchUiMapperTest" --console=plain
```

Expected: PASS; existing Classic target order, bounds, identity, and results remain unchanged.

### Task 2: Add the pure Embedded Workspace plan

**Files:**
- Create: `app/src/main/java/com/trancong/dexworkspacetouch/workspace/execution/embedded/EmbeddedWorkspacePlan.kt`
- Create: `app/src/main/java/com/trancong/dexworkspacetouch/workspace/execution/embedded/EmbeddedWorkspacePlanner.kt`
- Create: `app/src/test/java/com/trancong/dexworkspacetouch/workspace/execution/embedded/EmbeddedWorkspacePlannerTest.kt`

**Interfaces:**
- Consumes: `WorkspaceLaunchRequest` with resolved `AppLaunchTarget` values from Task 1.
- Produces: `EmbeddedWorkspacePlan(workspaceId: String, workspaceName: String, items: List<EmbeddedWorkspacePlanItem>)`, `EmbeddedWorkspacePlanItem(sourceCellId: String, packageName: String, componentName: String, normalizedBounds: NormalizedBounds, order: Int)`, and `EmbeddedWorkspacePlanner.plan(request: WorkspaceLaunchRequest): EmbeddedWorkspacePlan`.

- [ ] **Step 1: Write failing pure-mapping tests**

Create tests named:

- `preserves workspace identity component bounds source identity and order`
- `sorts plan items by existing target order`
- `keeps duplicate components as distinct source cells`
- `plan model graph contains no Android or embedded runtime types`

Use a request whose target list is intentionally out of order. Assert exact field preservation, sorted output order, and that duplicate components remain two plan items with distinct source IDs. For the dependency test, inspect declared field types and assert none begin with `android.` or contain `EmbeddedAppSession`, `EmbeddedAppTarget`, `VirtualDevice`, `Surface`, or `WorkspaceLibraryItem`.

- [ ] **Step 2: Run the planner test and observe RED**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "*EmbeddedWorkspacePlannerTest" --console=plain
```

Expected: FAIL because the plan models and planner do not exist.

- [ ] **Step 3: Implement the immutable models and pure planner**

Define the interfaces above. Map only fields already present on the resolved request, require no Android/context dependencies, and sort by `order`. Constructor assertions may repeat the current upstream guarantees—non-blank request identity/name, non-empty targets, explicit non-blank package/component, unique source IDs/orders, and non-negative order—but must not invent another readiness outcome or product rule.

- [ ] **Step 4: Run the focused planner tests and observe GREEN**

Run the command from Step 2.

Expected: PASS.

### Task 3: Add the runtime-only dual-run planning boundary

**Files:**
- Create: `app/src/main/java/com/trancong/dexworkspacetouch/workspace/execution/WorkspaceRunMode.kt`
- Create: `app/src/main/java/com/trancong/dexworkspacetouch/workspace/execution/WorkspaceRunRequest.kt`
- Create: `app/src/main/java/com/trancong/dexworkspacetouch/workspace/execution/WorkspaceRunPlanningResult.kt`
- Create: `app/src/main/java/com/trancong/dexworkspacetouch/workspace/execution/WorkspaceRunPlanner.kt`
- Create: `app/src/test/java/com/trancong/dexworkspacetouch/workspace/execution/WorkspaceRunPlannerTest.kt`

**Interfaces:**
- Consumes: `WorkspaceLaunchRequestFactory.create(workspaceId, workspaceName, canvas)`, `EmbeddedWorkspacePlanner.plan(request)`, and `WorkspaceRunRequest(workspaceId: String, workspaceName: String, canvas: WorkspaceCanvas, mode: WorkspaceRunMode)`.
- Produces: `WorkspaceRunMode { CLASSIC, EMBEDDED }`, `WorkspaceRunPlanningResult.Classic(request)`, `.Embedded(plan)`, `.Rejected(readiness)`, and `WorkspaceRunPlanner.plan(request: WorkspaceRunRequest): WorkspaceRunPlanningResult`.

- [ ] **Step 1: Write failing branch/parity tests**

Create tests named:

- `classic returns the exact resolved request without mutation`
- `embedded plans from the same resolved request fields`
- `both modes preserve deterministic resolution for null assigned activity`
- `both modes return the same rejection for every non-ready readiness class`
- `planning invokes no launcher or execution resource`

Build requests from Workspace domain values rather than `WorkspaceLibraryItem`. Cover `EmptyWorkspace`, `EmptyCells`, `InvalidCanvas`, `TooManyTargets`, `MissingApplications`, and `NonLaunchableApplications`, asserting the planner returns `Rejected` with the exact existing `LaunchReadiness` value for either mode. Inject only the existing factory and pure Embedded planner; the API must expose no launcher dependency.

- [ ] **Step 2: Run the run-planner test and observe RED**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "*WorkspaceRunPlannerTest" --console=plain
```

Expected: FAIL because the run-planning contracts do not exist.

- [ ] **Step 3: Implement the planning boundary**

Define the interfaces above. `WorkspaceRunPlanner.plan` calls the factory exactly once. Return `Rejected(readiness)` for every non-`Ready` result; for `Ready`, return the exact contained request in Classic mode or pass that same request to `EmbeddedWorkspacePlanner` in Embedded mode. Do not catch model-contract failures and translate them into a new Embedded-only readiness state.

- [ ] **Step 4: Run focused execution-planning tests and observe GREEN**

Run the command from Step 2.

Expected: PASS.

### Task 4: Verify scope, regressions, and architectural boundaries

**Files:**
- Modify only if an objective failure is found: files already listed in Tasks 1–3.
- Review: all DWT-VDM-004 changed files and the approved spec/plan.

**Interfaces:**
- Consumes: completed models/planners and existing Classic test suite.
- Produces: verification evidence only; no production integration or device artifact.

- [ ] **Step 1: Run the complete unit-test gate**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run compile/static build gates**

Run:

```powershell
.\gradlew.bat assembleDebug lintDebug --console=plain
```

Expected: `BUILD SUCCESSFUL` with no new lint error.

- [ ] **Step 3: Prove no prohibited source boundary changed**

Run:

```powershell
$base = 'c74d8777a4112f28a36939554acdfad09e91358f'
git diff --exit-code $base -- app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedapp app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedwaze app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedcalculator
git diff --exit-code $base -- app/src/main/java/com/trancong/dexworkspacetouch/workspace/persistence
git diff --name-only $base -- app/src/main | rg "Room|Migration|WorkspaceEntity|WorkspaceCanvasSerializer|TouchNavigation|HomeScreen"
```

Expected: the first two commands return no diff; the final command returns no match. Stop if any prohibited change exists.

- [ ] **Step 4: Check dependency and execution leakage**

Run:

```powershell
rg -n "WorkspaceLibraryItem|EmbeddedApp(Target|Session)|VirtualDevice|Surface(View|Control)?|Shizuku|android\." app/src/main/java/com/trancong/dexworkspacetouch/workspace/execution
rg -n "WorkspaceRunMode|EmbeddedWorkspacePlan" app/src/main/java/com/trancong/dexworkspacetouch/workspace/persistence app/src/main/java/com/trancong/dexworkspacetouch/feature
```

Expected: no matches. Imports of Workspace launcher/domain models are allowed; Android and embedded runtime dependencies are not.

- [ ] **Step 5: Review the scoped diff and whitespace**

Run:

```powershell
git diff --check
git status --short
git diff -- app/src/main/java/com/trancong/dexworkspacetouch/workspace app/src/test/java/com/trancong/dexworkspacetouch/workspace docs/superpowers/specs/2026-09-28-dwt-vdm-004-workspace-dual-run-architecture-design.md docs/superpowers/plans/2026-09-28-dwt-vdm-004-workspace-dual-run-architecture.md
```

Expected: `git diff --check` is clean; the scoped diff contains only the approved planning models, source-cell extension, focused tests, spec amendment, and plan. Existing unrelated work remains unstaged and untouched.

- [ ] **Step 6: Report without device testing or commit**

Report test/build/static-boundary evidence, exact changed files, and the remaining unrelated worktree state. Do not run an S23 smoke, stage, commit, or push until the human partner reviews the implementation result.
