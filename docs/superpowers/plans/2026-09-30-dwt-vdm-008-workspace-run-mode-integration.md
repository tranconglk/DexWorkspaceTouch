# DWT-VDM-008 Workspace Run Mode Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task by task. Steps use checkbox (`- [ ]`) syntax for tracking. The user requested no staging or commits.

**Goal:** Let one persisted Workspace run by explicit Classic or experimental Embedded action, with safe product gating and unchanged execution engines.

**Architecture:** The existing Classic `WorkspaceLaunchViewModel` remains the primary path. A product route loads a Workspace by ID, applies pure Embedded eligibility, checks non-allocating readiness, then feeds the existing 007 renderer/controller and 006 runner. An application-scoped product gate survives route loss and blocks conflicting execution until a clean terminal result.

**Tech Stack:** Kotlin, Jetpack Compose, Navigation, Room repository, coroutines/StateFlow, Shizuku state APIs, JUnit.

**Spec:** `docs/superpowers/specs/2026-09-30-dwt-vdm-008-workspace-run-mode-integration-design.md`

## Global Constraints

- `CLASSIC` stays the default; `Mở` invokes the current Classic launch call after the product gate permits it.
- `Embedded` needs the selected-card action `Mở Embedded (Thử nghiệm)` and a separate `Bắt đầu Embedded` press.
- Route carries URI-encoded `workspaceId` only; repository loads the current row; no duplicate Workspace model or Room migration.
- Product eligibility allows 1–2 app targets, rejects 0 and >2, duplicate resolved package/component and unsupported overlap; Classic limits remain unchanged.
- Before Start, readiness may inspect state and host Surface but must not acquire a UserService lease, bind/start UserService, create session or allocate VirtualDevice/input/task.
- Shizuku is optional for Classic and the library; no silent Classic fallback.
- Product gate lives above route scope and releases only after known zero allocation or authoritative clean terminal result; `CLEANUP_BLOCKED` prevents conflicting Classic/Embedded execution.
- Classic dispatch and Embedded acquisition are serialized by the same gate. The Classic permit covers intent acceptance and invocation of the existing launch call only, then releases; it does not own Classic runtime.
- The selected-card Embedded action is enabled when the product gate permits entry, regardless of Shizuku, eligibility or Surface state; failures are explained in the product route.
- Do not change 006 runner, 007 renderer/mapper, Classic launcher/flags/bounds, `feature/embeddedapp`, license/security, production configuration or proof routes unless a concrete integration mismatch is demonstrated.
- Do not stage, commit, push, build or run a device test as part of this planning task; the execution phase follows the verification steps below.

## Review Focus

1. Delete/edit selected Workspace before destination loads: load exactly the passed ID or show `MissingWorkspace`, then validate current content (Task 1).
2. Shizuku binder/permission changes between screen display and Start: recheck with zero embedded allocation; failure never invokes Classic (Task 3).
3. Two rapid Classic/Embedded intents or route recreation: one accepted intent during the critical dispatch window; stale callback cannot unlock or activate a newer run (Tasks 2 and 4).
4. Start fails with partial rollback or remote death: map cleanup uncertainty to `CLEANUP_BLOCKED`, including primary Classic action (Tasks 2 and 5).
5. Surface becomes invalid after readiness: existing preflight rejects before session allocation and product gate releases only on its typed clean result (Tasks 3 and 4).

---

## File map

- Create `feature/embeddedworkspace/product/EmbeddedWorkspaceEligibility.kt`: pure policy over a resolved plan and existing launch readiness.
- Create `feature/embeddedworkspace/product/EmbeddedProductRunGate.kt`: application-scoped run token/state; no session handle.
- Create `feature/embeddedworkspace/product/EmbeddedWorkspaceReadiness.kt`: pure readiness decision and injected non-allocating capability port.
- Create `feature/embeddedworkspace/product/AndroidEmbeddedCapabilityProbe.kt`: read-only Android/Shizuku state adapter.
- Create `feature/embeddedworkspace/product/EmbeddedWorkspaceProductScreen.kt`: route UI, product errors and 007 host/renderer composition.
- Modify `DexWorkspaceTouchApplication.kt`: own one product gate and expose existing process scope for cleanup that outlives a route.
- Modify `WorkspaceLibraryCard.kt`, `HomeScreen.kt`, `TouchNavigation.kt`: secondary action, gated routing and ID-only destination.
- Add focused unit tests next to product code; adjust existing UI/navigation tests only where the new action changes their contract.

### Task 1: Pure static eligibility and current-row loading

**Files:** Create `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/product/EmbeddedWorkspaceEligibility.kt`; test `app/src/test/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/product/EmbeddedWorkspaceEligibilityTest.kt`. Reuse `EmbeddedWorkspaceLayoutLoader` without altering proof behavior.

**Interfaces:** `suspend fun loadEligible(workspaceId: String, geometryPolicy: EmbeddedGeometryPolicy): EmbeddedEligibilityResult` in a product loader consuming the existing `EmbeddedWorkspaceLayoutLoader.load(workspaceId)`; `sealed interface EmbeddedEligibilityResult { Ready(snapshot: EmbeddedWorkspaceGeometrySnapshot); Rejected(reason: EmbeddedEligibilityFailure) }`. `EmbeddedEligibilityFailure` includes `MissingWorkspace`, `LaunchNotReady(LaunchReadiness)`, `UnsupportedLayout`, `DuplicateTarget`, `UnsupportedEmbeddedItemCount(count)`, `GeometryUnavailable`. Pure `fun evaluate(plan: EmbeddedWorkspacePlan): EmbeddedEligibilityFailure?` implements product-only plan rules; a successful result resolves one frozen snapshot using the existing geometry policy before constructing the renderer.

- [ ] **Step 1: Write failing tests.** Assert `loadEligible("selected")` uses only `getById("selected")`; deleted ID returns `MissingWorkspace`; edited row is revalidated; 0/1/2/>2 targets, same resolved `(packageName, componentName)`, unsupported overlap and invalid normalized bounds produce typed results; Classic `WorkspaceRunPlannerTest` behavior remains untouched.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests "*.EmbeddedWorkspaceEligibilityTest"`; expect failure for missing API.
- [ ] **Step 3: Implement** product loader and `EmbeddedWorkspaceEligibility.evaluate(plan)`. Call the existing 007 loader, then apply product rules and `EmbeddedWorkspaceGeometrySnapshot.resolve(plan, geometryPolicy)`; do not duplicate repository/launch factory/planner logic or place the 2-app cap in mapper/runner. Do not catch arbitrary exceptions as successful eligibility.
- [ ] **Step 4: Run** the same targeted test; expect PASS.

### Task 2: Product run gate above route scope

**Files:** Create `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/product/EmbeddedProductRunGate.kt`; modify `app/src/main/java/com/trancong/dexworkspacetouch/DexWorkspaceTouchApplication.kt`; test `app/src/test/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/product/EmbeddedProductRunGateTest.kt`.

**Interfaces:** `EmbeddedProductRunGate.tryAcquireEmbedded(workspaceId: String): RunToken?`, `tryDispatchClassic(dispatch: () -> Unit): Boolean`, `acceptResult(token, EmbeddedWorkspaceRunResult)`, `markStopping(token)`, `markUncertain(token)`, `releaseWithoutAllocation(token)`, `canEnterEmbedded(): Boolean`, `state: StateFlow<ProductRunPhase>`. `tryDispatchClassic` and `tryAcquireEmbedded` use one lock; the Classic permit includes callback invocation and releases on return or throw. `ProductRunPhase = IDLE|STARTING|ACTIVE|STOPPING|CLEANUP_BLOCKED`; `tryAcquireEmbedded` enters STARTING, `Started` enters ACTIVE, stop intent enters STOPPING, authoritative clean enters IDLE, uncertain cleanup enters CLEANUP_BLOCKED. Token identity rejects stale callbacks. Application creates exactly one gate and `createEmbeddedProductRunScope(): CoroutineScope`, a child of its existing process scope that survives route disposal while cleanup work is pending; gate never stores runner/session/Surface.

- [ ] **Step 1: Write failing tests.** Two acquires cannot coexist; concurrent/rapid `tryDispatchClassic { existingClassicLaunchCall() }` and `tryAcquireEmbedded(id)` cannot both enter during the Classic dispatch window; `CLEANUP_BLOCKED` rejects Classic; route recreation/disposal does not release token; stale `Started` cannot make a newer run ACTIVE and stale cleanup cannot unlock it. Assert IDLE → STARTING → Started/ACTIVE → markStopping/STOPPING → authoritative-clean/IDLE, plus uncertain/CLEANUP_BLOCKED. `PreflightRejected`, a `StartFailed` with `allOwnedSessionsClean=true`, and `Stopped` with all `Clean` outcomes release; `CleanupIncomplete`, `RecoveryRequired`, `StartFailed(false)` and unknown outcome block. Include cancellation after Start enters runner as uncertain, never as zero allocation.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests "*.EmbeddedProductRunGateTest"`; expect missing API failure.
- [ ] **Step 3: Implement** atomic token/state transitions and short-lived Classic dispatch permit. Treat `DuplicateCall` as uncertain unless an earlier authoritative result already closed the same token. Map runner outcomes, not its ambiguously named phase, into product state. Expose gate and child-scope factory from `DexWorkspaceTouchApplication` using existing manual DI.
- [ ] **Step 4: Run** the targeted gate test; expect PASS.

### Task 3: Zero-allocation pre-start readiness

**Files:** Create `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/product/EmbeddedWorkspaceReadiness.kt` and `AndroidEmbeddedCapabilityProbe.kt`; test `app/src/test/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/product/EmbeddedWorkspaceReadinessTest.kt`.

**Interfaces:** `interface EmbeddedCapabilityProbe { fun snapshot(): EmbeddedCapabilitySnapshot }` reads API/capability, Shizuku binder and permission without connecting. `fun evaluate(capability: EmbeddedCapabilitySnapshot, geometryReady: Boolean, rendererReady: Boolean, controllerCanStart: Boolean): EmbeddedReadinessResult` returns `Ready` or typed `UnsupportedPlatform`, `ShizukuUnavailable`, `ShizukuPermissionMissing`, `GeometryUnavailable`, `RendererNotReady`. The product host supplies booleans from its frozen snapshot and 007 state; the pure evaluator does not import Compose/renderer types.

- [ ] **Step 1: Write failing tests.** Probe fake records zero calls to session factory/connection manager; missing binder or permission rejects Embedded only; retry with recovered snapshot becomes Ready without app restart; invalid viewport/Surface/controller state rejects; Shizuku changes between display and Start are observed on Start recheck; Surface invalid after recheck is still rejected by 006 preflight before allocation.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests "*.EmbeddedWorkspaceReadinessTest"`; expect missing API failure.
- [ ] **Step 3: Implement** pure evaluator and Android read-only probe (API/capability, `Shizuku.pingBinder()` and `Shizuku.checkSelfPermission()` only when safe). Never call `EmbeddedAppServiceConnectionManager.acquire/connect`, `Shizuku.requestPermission`, session factory or runner from readiness. Explicit user permission request belongs to product UI action. UserService connection failure after Start maps to `RuntimeStartFailed` with the runner cleanup outcome.
- [ ] **Step 4: Run** targeted readiness tests; expect PASS.

### Task 4: Product host, Start and cleanup lifecycle

**Files:** Create `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/product/EmbeddedWorkspaceProductScreen.kt` and `EmbeddedWorkspaceProductController.kt`; test `app/src/test/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/product/EmbeddedWorkspaceProductControllerTest.kt`. Reuse existing `EmbeddedWorkspaceGeometrySnapshot`, `EmbeddedWorkspaceRenderer`, `EmbeddedWorkspaceRendererCoordinator`, `EmbeddedWorkspaceRunnerController` and `EmbeddedWorkspaceRunner` unchanged.

**Interfaces:** `EmbeddedWorkspaceProductController.load(workspaceId)`, `start()`, `requestExit()`, `onHostDisposed()` with observable product UI state. It consumes Tasks 1–3 and the app-scoped gate/scope. `requestExit()` returns only after clean terminal state; on uncertain cleanup it returns `CLEANUP_BLOCKED` and the destination remains/reopens a status view. No serialized plan is passed by navigation.

- [ ] **Step 1: Write failing tests.** Loaded deleted ID cannot Start; action alone never starts runner; Start rechecks readiness then acquires one token; failed pre-start check leaves zero allocation and no Classic dispatch; UserService failure after Start becomes `RuntimeStartFailed`; Back IDLE exits; Back ACTIVE/STARTING/STOPPING converges on one `close()` and waits; route disposal launches cleanup in app scope and does not release gate early; route recreation reads the same gate without auto-navigation; Surface loss/remote death with incomplete cleanup blocks conflicting launches; clean stop allows new generation and Classic.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests "*.EmbeddedWorkspaceProductControllerTest"`; expect missing API failure.
- [ ] **Step 3: Implement** product controller/screen as a host of 007 components, not a second renderer. Preserve the eligibility result's one frozen geometry snapshot and the 007 generation guard. Construct runner and cleanup work in the same application-child scope; route disposal must not cancel it while cleanup is pending. Once an operation terminates, cancel its idle child scope even if the app-scoped gate remains `CLEANUP_BLOCKED`. A later supported reconcile creates new app-scoped work for the same run token. Recreated UI reads gate state and shows blocked status; no auto-navigation from application scope. Move only product lifecycle waiting/feedback into new code. If no supported reconcile exists, show blocked diagnostics rather than inventing global cleanup or falsely enabling Retry.
- [ ] **Step 4: Run** targeted controller test and `./gradlew :app:compileDebugKotlin`; expect PASS.

### Task 5: Library action, navigation and product errors

**Files:** Modify `app/src/main/java/com/trancong/dexworkspacetouch/workspace/library/ui/WorkspaceLibraryCard.kt`, `app/src/main/java/com/trancong/dexworkspacetouch/ui/screens/HomeScreen.kt`, `app/src/main/java/com/trancong/dexworkspacetouch/navigation/TouchNavigation.kt`; test affected Home/navigation behavior using existing test convention, plus `app/src/test/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/product/EmbeddedWorkspaceProductRoutingTest.kt`.

**Interfaces:** `WorkspaceLibraryCard(..., onOpenEmbedded: () -> Unit, embeddedOpenEnabled: Boolean, ...)`; `HomeScreen(..., onOpenEmbeddedWorkspace: (String) -> Unit, ...)`; route `embedded-workspace/{workspaceId}` calls product screen with decoded ID. `embeddedOpenEnabled` reflects selected-card/multi-select rules and `gate.canEnterEmbedded()` only; it never reads Shizuku/eligibility/readiness. Primary and fallback Classic invoke the unchanged `launchViewModel.launchWorkspace(workspace, launchRuntime, launchHostToken)` inside `gate.tryDispatchClassic { ... }`, not after a separate `canLaunchWorkspace()` read. Product errors preserve typed reason/cleanup code for diagnostics but show concise Vietnamese actions.

- [ ] **Step 1: Write failing tests.** Both pinned/regular selected cards expose `Mở Embedded (Thử nghiệm)`; multi-select hides it; primary `Mở` remains Classic; secondary passes exact ID to product route. Shizuku absent/permission missing or static eligibility failure still leaves the action enabled, opens product screen with typed reason, creates no Embedded allocation and never auto-calls Classic. Explicit fallback uses atomic Classic dispatch only when gate IDLE; `CLEANUP_BLOCKED` disables primary and fallback Classic as well as new Embedded; proof routes remain reachable.
- [ ] **Step 2: Run** targeted routing/UI tests; expect failure for missing action/route.
- [ ] **Step 3: Implement** the card action in its own full-width row below `[Mở] [Sửa]`, route binding and product copy. Keep current Classic launcher call/flags unchanged. Preserve existing experimental routes and diagnostics access.
- [ ] **Step 4: Run** targeted routing/UI tests and `./gradlew :app:compileDebugKotlin`; expect PASS.

### Task 6: Fresh verification thread, full local gate and production-signed S23 proof

**Files:** Test files from Tasks 1–5; no production changes unless a concrete test failure identifies one.

- [ ] **Step 1: In a fresh verification thread after Tasks 1–5 local PASS**, run `.\gradlew.bat testDebugUnitTest assembleDebug lintDebug --console=plain`; expect PASS. Run focused 006/007/008 regression tests only if the full local gate does not already show their individual result; do not rerun unchanged suites merely to duplicate evidence.
- [ ] **Step 2: Run** `git diff --check`, inspect scoped diff, and scan architecture boundaries: no Classic launcher/flags/bounds change, no runner/renderer/mapper or persistence/schema change, no Shizuku startup dependency, no duplicate Workspace/plan/renderer, cap only in product eligibility, product gate above route, no session/VDM ownership in product coordinator. Expect no violations. Do not stage/commit/push.
- [ ] **Step 3: Build** a fresh production-config, production-signed smoke APK using the existing `build-device-smoke.ps1` workflow. Do not install an ordinary debug APK for proof. Record smoke APK SHA-256, installed `base.apk` SHA-256 and signer SHA-256; required signer is `19ac0ea99125361b3c2083aaa44c8745ebad9c55fa967642c2b9e5a1086a45e7`. Stop and report `NOT VERIFIED` if the authorized signing workflow or safe S23 device session is unavailable; do not substitute a different signer or artifact.
- [ ] **Step 4: On S23 DeX**, use the same persisted Waze + Calculator Workspace: primary `Mở` launches Classic; explicit `Mở Embedded (Thử nghiệm)` opens product route with zero embedded allocation before `Bắt đầu Embedded`; Start uses 007 renderer/006 runner; both apps render/interact. Press **Back while ACTIVE** (Stop is not a substitute), observe Back wait for clean reverse cleanup and zero orphan, then primary `Mở` launches Classic again. Shizuku-unavailable device case is optional only if safe. Record ID, phases, cleanup result and artifact hashes; do not rerun full 006/007 device matrices.

## Handoff

Implement in task order with targeted tests first. The plan is ready for review; do not start implementation until the user approves this written plan. No commit step is included because the user's worktree protection forbids staging and commits.
