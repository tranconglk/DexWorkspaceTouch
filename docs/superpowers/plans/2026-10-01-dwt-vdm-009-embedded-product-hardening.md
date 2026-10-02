# DWT-VDM-009 — Embedded Product Hardening / In-Process Recovery UX Implementation Plan

> **Trạng thái cuối (2026-10-02): DWT-VDM-009 — COMPLETE / PASS / CLOSED.** [Scope amendment 009](../specs/2026-10-01-dwt-vdm-009-in-process-scope-amendment.md) giữ route-scoped runtime/value-only Application state và fail-closed hung-Start boundary đã accepted. Không redesign remote protocol để bảo đảm authoritative cleanup của pending/hung Start. Process death vẫn là unsupported boundary theo [009R-D](../specs/2026-10-01-dwt-vdm-009r-d-process-death-recovery-decision.md).
>
> **Tasks 1–2 COMPLETE / targeted PASS; Task 3D COMPLETE / PASS; Task 3G, Task 3B và Tasks 4–9 COMPLETE / PASS / ACCEPTED.** Checkpoint lịch sử: Task 3 `BLOCKED_BY_RUNTIME_OWNERSHIP_MODEL`; Task 3A COMPLETE (report accepted); Task 3E COMPLETE / APPROVED, `BLOCKED_REQUIRES_RUNTIME_PROTOCOL_REDESIGN` cho authoritative permanently-hung remote Start cleanup; Task 3F APPROVED. Không có task implementation đang chờ trong scope 009.

> **Final verification:** [Task 8 — local gate PASS](../../../verification/dwt-vdm-009/local-gate.md); [Task 9 — S23/DeX production-signed device proof PASS](../../../verification/dwt-vdm-009/s23-hardening-proof.md). [Final review](../../../verification/dwt-vdm-009/final-review.md) là closure record. Giữ nguyên evidence lịch sử và implementation; closeout chỉ kiểm tra tài liệu, không chạy lại Gradle/tests/lint/device. Không reset/stash/clean/stage/commit/push. **Không tạo Task 10 hoặc mở milestone mới.**

**Goal:** Hardening lỗi sản phẩm, Shizuku UX/refresh, cleanup lifecycle và diagnostics của Embedded trong cùng process, không mở rộng runtime capability.

**Architecture:** Giữ gate năm pha 008 làm single serialization/phase authority và terminal cleanup result 006. Application product scope chỉ giữ gate, token/generation/operation identity và immutable typed issue/evidence/status/diagnostics; renderer/controller/execution adapter/session và Surface lifecycle thuộc route/Activity tạo run. Host cũ cleanup hữu hạn, có thể tạm giữ UI trong operation đó; host mới chỉ quan sát value state, không tiếp tục run cũ. Không tạo `EmbeddedProductRunOwner`, không persist hoặc tái dựng ownership sau process death.

**Tech stack:** Kotlin, Jetpack Compose/Navigation, StateFlow/coroutines, Shizuku APIs already present, JUnit 4, Gradle, PowerShell/ADB for S23 proof. No new dependencies by default.

**Spec:** [Scope amendment 009](../specs/2026-10-01-dwt-vdm-009-in-process-scope-amendment.md) là scope/acceptance hiện hành, dưới boundary của [009R-D](../specs/2026-10-01-dwt-vdm-009r-d-process-death-recovery-decision.md). [Spec 009 cũ](../specs/2026-10-01-dwt-vdm-009-embedded-product-hardening-design.md) chỉ cung cấp chi tiết hardening không xung đột.

## Product contract và evidence lịch sử

Nếu DWT process chết khi đang Embedded: không tuyên bố authoritative recovery sau restart; không suy cleanup thành công từ gate `IDLE`, process/root biến mất hoặc count rỗng; không persist ownership evidence giả; không global scan/cleanup. Đây là **UNSUPPORTED RECOVERY CASE** của Embedded **Thử nghiệm**, không phải PASS hoặc “đã sạch”. Không áp contract block Classic sau mọi process restart bất định; `CLEANUP_BLOCKED` vẫn giữ nguyên khi còn ownership/evidence trong cùng process.

[Task 0 cũ](../../../verification/dwt-vdm-009/task-0-process-death.md) là characterization lịch sử, không còn checkpoint/prerequisite của plan. 009R-I và 009R-A STOPPED, giữ evidence cũ theo 009R-D; Task 0B CANCELLED / NOT NEEDED. Không chạy lại probe, mở spec recovery hoặc yêu cầu Branch A/B mới để thực hiện hardening.

## Global constraints for Tasks 1–9

- Package `com.trancong.dexworkspacetouch`; minSdk 28, target/compile SDK 37; manual DI. Retain `CLASSIC` default and explicit `Mở Embedded (Thử nghiệm)` → `Bắt đầu Embedded`.
- Gate phases remain exactly `IDLE`, `STARTING`, `ACTIVE`, `STOPPING`, `CLEANUP_BLOCKED`; issue/evidence are separate. Classic remains Shizuku-independent. No silent fallback.
- No >2-app support, same-target concurrency, mixed mode, Room/schema migration, dynamic geometry, keyboard/IME, clipboard/audio, runner/renderer rewrite, release/license changes, production URL/key changes or dependency upgrade.
- No product cleanup Retry/reconcile; `Kiểm tra lại` is only pre-Start readiness. `CLEANUP_BLOCKED` giữ token và typed value evidence trong process; sau terminal incomplete/uncertain không cần live execution/controller handle. No global cleanup or deletion inferred from package name.
- Process-death recovery, cross-process ownership reconstruction/reconcile, durable recovery journal, pending/commit protocol, recovery enrollment and authoritative cleanup after main-process restart are OUT OF SCOPE. Không sửa AIDL hoặc runtime 006 để tiếp tục recovery; không thêm persistent marker/ownership evidence.
- Remote pre-reserve cancellation/tombstone protocol, concurrent Start/Stop remote operation identities, rewrite `RemoteSessionRuntime` monitor model, authoritative cleanup của permanently hung remote Start, AIDL cancellation protocol và UserService kill/global cleanup đều OUT OF SCOPE. Không redesign `RemoteSessionRuntime` / `RemoteSessionRegistry` / UserService protocol chỉ để Stop dọn pending/hung Start có thẩm quyền.
- Application product state dài hạn chỉ chứa giá trị; không giữ controller/runner/session/renderer, Activity/View/Surface, cleanup closure hoặc Job/Deferred capture graph runtime/UI. Cleanup coroutine đã tồn tại của host cũ được tạm giữ graph chỉ trong một operation terminal hữu hạn; không lưu vào Application RunOwner mới hoặc cho host mới tái sử dụng. Sau terminal, cleanup scope phải kết thúc và bỏ graph route. Bỏ guarantee cleanup độc lập UI vô điều kiện; không tạo `EmbeddedProductRunOwner`.
- Gate giữ `RunToken`, generation tăng đơn điệu trong process, operation identity/sequence và phase có thẩm quyền. Callback phải khớp token/generation/operation; stop intent từ chối late Started. Gate/status của host cũ non-IDLE chặn token mới và dispatch Classic/Embedded xung đột.
- `releaseWithoutAllocation` chỉ cho `NOT_INVOKED / PRE-RUNNER_REJECTED`, có proof chưa gọi runner và không có execution cũ còn owned. `INVOKED / RESULT_OR_UNCERTAIN`: null/exception/missing result không chứng minh no-allocation; exact-run `PreflightRejected`/authoritative clean đi qua normal result handling. Hung/pending Start sau invocation không được đổi category hoặc gọi `releaseWithoutAllocation` từ timeout/detach. Contract đã được Task 3B thực hiện và accepted; Task 3F trước đó chỉ sửa tài liệu.
- Protect `experiments/shizuku-task-probe/...`, `AGENTS.md`, `docs/compat/COMP-012R_HISTORY_ASSISTED_REUSE_DESIGN.md`, `smoke-output/`, existing `verification/` material and `.superpowers/` if present. No reset, stash, clean, staging, commit or push.
- Tasks 1–2, Task 3D và Tasks 3G/3B–9 đã hoàn tất; giữ evidence, không chạy lại do sửa tài liệu hoặc mở rộng PASS connection-readiness sang Start. Các định nghĩa, RED/implementation/verification commands và STOP rules bên dưới được giữ làm **lịch sử triển khai trước cấp phép**, không phải yêu cầu execution mới. Trạng thái cuối và báo cáo accepted quyết định completion.

## Bounded cleanup — fail-closed hung-Start và local lifetime

[Scope amendment, mục bounded UI retention](../specs/2026-10-01-dwt-vdm-009-in-process-scope-amendment.md#bounded-ui-retention-và-terminal-result) giữ **source evidence lịch sử Task 3S trước Task 3G/3B**: product dùng READY 10 giây, ACTIVE 30 giây và cleanup 20 giây mỗi owned item. Runner cleanup đảo owned order, tiến từng item theo terminal snapshot hoặc timeout; cap 1–2 app cho ngân sách chờ 20/40 giây sau `beginCleanup()` cộng dispatch/scheduling. Mô tả `finishCleanup()`/`onHostDisposed()` và Start mutex tại checkpoint đó không phải current lifetime verdict; evidence hiện hành nằm trong Task 3G/3B và Task 8. Không hứa wall-clock 40 giây từ disposal.

Đây là bound của terminal wait, không phải bằng chứng mọi Binder task/capture/runner reference đã detach. Task 3A và Task 3E giữ nguyên. Nếu Embedded Start đã vượt invocation boundary mà không thu được authoritative cleanup trong bounded product cleanup window hiện có, terminal là **INCOMPLETE / UNCERTAIN**, gate trở thành/giữ **`CLEANUP_BLOCKED`**. Không claim clean, không gọi `releaseWithoutAllocation`, không cho Classic hoặc Embedded run khác, không Retry/reconcile/global-clean. `UNKNOWN_SESSION`, empty inventory, Binder death, timeout và local detach không chứng minh safety; remote Start có thể còn pending và allocate muộn, nên gate vẫn blocked. 009 không yêu cầu authoritative cleanup của permanently hung Start.

Remote ownership uncertain không cho phép giữ UI lifetime vô hạn. Start IPC task đã được Task 3G isolate, chỉ giữ transport/value objects cần cho IPC, không giữ trực tiếp/gián tiếp `EmbeddedAppSession`, runner/controller/renderer, Activity/View/Compose state hoặc route callbacks. Sau terminal materialization: copy evidence → detach notification consumers → complete route waiters → clear runner prepared/owned Surface/session holders → phối hợp đóng admission/giải quyết pending replies và terminate actor/timers → release route/controller graph. Chỉ cached terminal value result và value-only `CLEANUP_BLOCKED` evidence còn cần giữ; Application gate/status không giữ runtime graph. Late Start completion bị drop, không đổi gate/cache hoặc revive run. Không cần terminate chính hung Binder transaction; không giả Surface release/local detach là remote cleanup acknowledgement.

Task 3G và Task 3B đều **COMPLETE / PASS / ACCEPTED**; client-side lifetime prerequisite đã được chứng minh bằng source/compiled-field và local tests, được Task 8 audit lại. Task 3F APPROVED là checkpoint tài liệu lịch sử. Native/vendor Surface lifetime và heap/GC proof vẫn **NOT VERIFIED / OUT OF SCOPE**; remote protocol không được mở rộng.

## Review focus đã được kiểm chứng

Five edge cases to pin to named task tests: (1) late `Started` after stop intent hoặc stale token/generation/cleanup operation không được activate/release run (Task 3B); (2) Shizuku permission granted while screen remains open must reach Ready without bind (Task 2, đã PASS); (3) route recreation trong STARTING/ACTIVE/STOPPING không tạo controller/token mới, reattach Surface hoặc close hai lần; terminal scope phải bỏ route graph (Task 4); (4) missing resource provenance must format as `unknown`, never an inferred ID (Task 6); (5) direct proof route navigation during product ownership must remain gated (Task 7).

## Lịch sử triển khai — định nghĩa và checklist trước cấp phép

Các mục Tasks 1–9 bên dưới giữ nguyên định nghĩa, checklist và lệnh verification của kế hoạch. Dấu `- [ ]`, `expect RED/PASS`, `Run/Rerun` và STOP rules là nội dung lịch sử trước cấp phép, **không biểu thị công việc còn pending và không yêu cầu chạy lại**. Mỗi dòng **Status cuối** cùng evidence liên kết ghi kết quả đã accepted; kết quả lịch sử chỉ có giá trị trong phạm vi từng lượt lúc đó.

## Task 1 — Typed product issue and recovery model

**Status cuối:** COMPLETE / targeted PASS. Các ghi chú NOT VERIFIED của lượt Task 1 bên dưới là lịch sử; final local/device evidence thuộc Task 8/9.

**Files:** Create `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/product/EmbeddedProductIssue.kt`, `EmbeddedProductRecovery.kt`; create `app/src/test/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/product/EmbeddedProductIssueTest.kt`. Modify `EmbeddedWorkspaceProductController.kt` and `EmbeddedWorkspaceProductScreen.kt` only to consume the new model; reuse `EmbeddedWorkspaceEligibility.kt`, `EmbeddedWorkspaceReadiness.kt` and `EmbeddedWorkspaceRuntimeModels.kt` without widening them.

**Interface:** `EmbeddedProductIssue` sealed type; `AllocationEvidence` (`NONE_CONFIRMED`, `POSSIBLE_OR_OWNED`, `UNKNOWN`); `CleanupEvidence` (`NOT_NEEDED`, `CLEAN_CONFIRMED`, `INCOMPLETE`, `UNCERTAIN`); `EmbeddedProductRecovery(phase: ProductRunPhase, issue: EmbeddedProductIssue?, allocationEvidence: AllocationEvidence, cleanupEvidence: CleanupEvidence, permittedActions: Set<EmbeddedRecoveryAction>)`. Mapper consumes existing eligibility/readiness/runner result plus source cell and returns typed issue/evidence/actions. Exact issue cases: `MissingWorkspace`, `LaunchNotReady`, `UnsupportedLayout`, `UnsupportedEmbeddedItemCount`, `DuplicateTarget`, `UnsupportedPlatform`, `GeometryUnavailable`, `ShizukuUnavailable`, `ShizukuPermissionMissing`, `RendererNotReady`, `RuntimeStartFailed`, `SurfaceLost`, `RemoteDied`, `RuntimeCleanupIncomplete`, `CleanupOutcomeUncertain`. Không thêm `RecoveryCheckRequired` cho process restart; `RemoteDied` ở đây là UserService mất khi DWT process còn sống.

**Action-policy clarification đã duyệt:** `NONE_CONFIRMED` không tự cho phép Classic. Chỉ offer/dispatch khi gate không có operation sở hữu, phase `IDLE` và allocation được xác nhận absent; hoặc đúng run có authoritative clean và gate đã trở lại `IDLE`. `NONE_CONFIRMED` trong `STARTING/ACTIVE/STOPPING/CLEANUP_BLOCKED` không bao giờ offer/dispatch Classic. Gate/operation state có thẩm quyền hơn snapshot allocation tạm thời; kiểm tra lại khi dispatch.

- [x] **RED test:** `EmbeddedProductIssueTest` maps each named pre-start cause to its distinct code/action; runtime start failure, surface loss and remote death are each paired with clean versus uncertain cleanup; `DuplicateCall`/missing callback never map to clean; explicit Classic requires IDLE, no gate owner and authoritative absent/clean evidence. Capability pass on an unverified device must still say experimental and must not claim device verification. Run `./gradlew.bat :app:testDebugUnitTest --tests "*.EmbeddedProductIssueTest" --console=plain`; new type/mapper references failed at test compilation before implementation.
- [x] **Implement:** Add typed mapping and action policy without using display strings for branching. Preserve the five gate phases and current eligibility cap. Render issue-specific short copy through the typed model, not a generic failure screen.
- [x] **Targeted verify:** Rerun that class; PASS. Inspect scoped diff for new model/controller/screen/test; `git diff --check` and checks covering untracked files PASS.

**Kết quả Task 1 (2026-10-01):** 17 `EmbeddedProductIssueTest`, 8 `EmbeddedWorkspaceProductControllerTest`, 3 `EmbeddedProductRunGateTest`: **28/28 PASS**, không skipped/failure/error. Command: `.\gradlew.bat :app:testDebugUnitTest --tests "*.EmbeddedProductIssueTest" --tests "*.EmbeddedWorkspaceProductControllerTest" --tests "*.EmbeddedProductRunGateTest" --console=plain`.

RED bổ sung đã bắt và sửa: readiness failure không được ghi đè unresolved allocation/cleanup thành absent; source chẩn đoán phải lấy từ outcome lỗi thay vì outcome sạch đầu tiên, và giữ source remote đã biết khi result cấp run thiếu source. Result thiếu/`DuplicateCall`, terminal outcome thiếu/trùng/sai owned set và rollback mâu thuẫn không cho Classic. UI không hiện Classic khi non-IDLE; tap kiểm tra lại policy, dispatch vẫn qua gate 008 hiện có. Không triển khai Task 2+, không stage/commit/push. Device/UI thực tế và full local gate: **NOT VERIFIED**, thuộc các task sau.

**STOP/invariants:** Stop if any issue cannot be mapped without guessing runner allocation/cleanup outcome; record `UNKNOWN`, not clean. Do not change Classic launcher, runner, renderer, Room or Shizuku service manager.

## Task 2 — Shizuku hardening and zero-allocation refresh

**Status cuối:** COMPLETE / targeted PASS. Device/UI NOT VERIFIED tại lượt Task 2 là lịch sử; Task 9 đã chứng minh kịch bản unavailable → Ready trong cùng process, không mở rộng sang mọi callback riêng lẻ.

**Files:** Modify `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/product/AndroidEmbeddedCapabilityProbe.kt`, `EmbeddedWorkspaceReadiness.kt`, `EmbeddedWorkspaceProductScreen.kt`; modify `app/src/test/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/product/EmbeddedWorkspaceReadinessTest.kt`; create `EmbeddedShizukuRefreshTest.kt` in the same test package. `TouchNavigation.kt` only if lifecycle observation cannot be local to the route.

**Interface:** Probe snapshot stays read-only and returns platform/binder/permission plus optional install/denial detail **only when public APIs substantiate it**. A route-scoped refresh trigger merges Shizuku binder/permission callbacks, `ON_RESUME`, and explicit `Kiểm tra lại`; every trigger calls `EmbeddedCapabilityProbe.snapshot()` and existing host readiness, never `acquire/connect/bind` or Start. Permission request is a separate explicit user action.

- [x] **RED test:** `EmbeddedShizukuRefreshTest` covers stopped/unavailable, permission missing/denied, permission granted after route opens, callback after disposal, and unchanged Ready behavior; fakes count lease acquisition, connect, bind, session and VDM creation and assert **zero** before Start, including automatic permission request/Start. Extended `EmbeddedWorkspaceReadinessTest` for distinctions actually supportable. The two-class command below failed at `compileDebugUnitTestKotlin` on the new refresh/detail/`withRecovery` references before production implementation.
- [x] **Implement:** Add callback registration/removal at route lifetime, `ON_RESUME` and explicit refresh; recheck at Start. Show `Mở Shizuku` only when safe launch intent resolves; request permission only on `Cấp quyền Shizuku` tap. If install/denial cannot be verified, retain generic unavailable/missing state. Do not poll.
- [x] **Targeted verify:** Reran the exact two classes: PASS, including zero-allocation assertions. Affected Task-1 issue/controller regression: 25/25 PASS. Scoped code diff inspected; `git diff --check`, including the untracked refresh test, PASS.

**Kết quả Task 2 (2026-10-01):** `.\gradlew.bat :app:testDebugUnitTest --tests "*.EmbeddedShizukuRefreshTest" --tests "*.EmbeddedWorkspaceReadinessTest" --console=plain`: **BUILD SUCCESSFUL**. Regression: `.\gradlew.bat :app:testDebugUnitTest --tests "*.EmbeddedProductIssueTest" --tests "*.EmbeddedWorkspaceProductControllerTest" --console=plain`: **25/25 PASS**, không skipped/failure/error.

Public Shizuku API 13.1.5 xác nhận binder available/unavailable và permission granted/missing; callback đúng request code trong subscription hiện tại bổ sung denial đã quan sát. Không kết luận “chưa cài” hoặc phân biệt chắc chắn server dừng khi binder unavailable. Launch intent chỉ cho phép hiện `Mở Shizuku` khi resolve đúng package/activity exported và enabled. Ba listener binder-received/binder-dead/permission-result được đăng ký một lần mỗi attach, gỡ khi dispose cùng observer lifecycle; token subscription loại callback sau dispose hoặc từ lần attach cũ.

`lateShizukuCallbackCannotExposeClassicInStartingActiveStoppingOrCleanupBlocked` PASS: callback làm readiness Ready nhưng không sửa ownership/allocation/cleanup evidence, không thêm Classic vào `EmbeddedProductRecovery.permittedActions`, và gate vẫn từ chối dispatch ở cả bốn pha. Projection readiness chỉ cập nhật lỗi môi trường trước Start khi policy hiện tại đã cho phép Classic; unknown/runtime evidence giữ nguyên. Zero-allocation trước Start: lease/connect/bind/session/VDM = 0, automatic permission request/Start = 0; positive control chỉ explicit Start mới đi qua fake allocation boundary. Giữ nguyên Task-1 issue/recovery/controller, service manager, proof paths và runtime 006/007. Device/UI thực tế: **NOT VERIFIED**; dừng trước Task 3, không stage/commit/push.

**STOP/invariants:** Stop if a proposed distinction requires binding merely to probe or a hidden API. Leave Library and Classic free of Shizuku checks; leave `EmbeddedAppServiceConnectionManager` and proof paths unchanged.

## Task 3 — BLOCKED_BY_RUNTIME_OWNERSHIP_MODEL (lịch sử)

Task 3 cũ yêu cầu Application-owned cleanup/control độc lập UI; implementation đó không còn được yêu cầu. Giữ kết luận BLOCKED lịch sử và không tạo `EmbeddedProductRunOwner`/`EmbeddedProductRunOwnerTest`. Mô hình thay thế đã được thực hiện và accepted tại Tasks 3G/3B, không sửa kết luận Task 3 cũ.

## Task 3A — COMPLETE / architecture report accepted (lịch sử)

[Báo cáo UI-independent cleanup boundary](../specs/2026-10-01-dwt-vdm-009-task3-ui-independent-cleanup-boundary.md) được giữ nguyên, không chỉnh lại kết luận hoặc evidence. Báo cáo COMPLETE tại checkpoint đó không chứng minh graph release của mô hình mới; authorization, implementation và acceptance Tasks 3G/3B–9 thuộc các lượt sau.

## Task 3B — Gate generation/operation ordering and route-scoped execution hardening

**Status cuối:** COMPLETE / PASS / ACCEPTED — [Task 3B evidence](../../../verification/dwt-vdm-009/task-3b-gate-operation-ordering.md).

**Prerequisite đã đáp ứng:** Task 3G PASS / ACCEPTED có evidence rằng (1) hung Start IPC không giữ route/runtime callbacks; (2) local terminal Incomplete/Uncertain detach được route graph; (3) runner terminate và chỉ giữ cached value result; (4) stale Start completion không đổi `CLEANUP_BLOCKED` hoặc revive run. Đây là client-side lifetime evidence, không phải remote cleanup acknowledgement. Task 3B không triển khai remote cleanup protocol.

**Files:** Modify `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/product/EmbeddedProductRunGate.kt`, `EmbeddedWorkspaceProductController.kt`, `EmbeddedWorkspaceProductScreen.kt` cùng thư mục và `app/src/main/java/com/trancong/dexworkspacetouch/DexWorkspaceTouchApplication.kt` chỉ cho value-only gate/status DI và lifetime wiring. Extend `app/src/test/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/product/EmbeddedProductRunGateTest.kt`, `EmbeddedWorkspaceProductControllerTest.kt`. Không tạo RunOwner; không sửa runner/session/renderer 006/007 trong task này.

**Interface:** Gate giữ token identity, generation tăng đơn điệu trong process, operation identity/sequence, invocation category và authoritative phase; publish immutable typed issue/evidence/status qua read-only `StateFlow`. Route controller/execution adapter giữ runtime và operation cleanup của chính route. Callback/result gửi tới gate chỉ chứa giá trị, khớp token/generation/operation; không giữ publisher closure hoặc pending reply có đường ngược tới route trong Application state. Task 4/5/6 consume value status này, không consume execution handle.

- [ ] **RED test:** `EmbeddedProductRunGateTest` chứng minh late `Started` sau stop intent/STOPPING bị từ chối; stale token/generation/start hoặc cleanup operation không activate/release run mới; gate non-IDLE chặn token và dispatch xung đột. `EmbeddedWorkspaceProductControllerTest` chứng minh double Start một invocation; Start+Back ghi stop intent khi Start đang pending rồi một cleanup; double Back/dispose join cùng operation. Contract tests: `NOT_INVOKED/PRE-RUNNER_REJECTED` mới dùng `releaseWithoutAllocation`; null/exception/missing result sau `INVOKED` giữ uncertain, exact-run `PreflightRejected`/authoritative clean đi qua normal result acceptance. Hung Start không có authoritative cleanup trong bounded window → INCOMPLETE / UNCERTAIN, CLEANUP_BLOCKED; không Classic/new Embedded/Retry/reconcile và không nhả gate theo unknown/empty/death/timeout/detach hoặc late completion. Run `.\gradlew.bat :app:testDebugUnitTest --tests "*.EmbeddedProductRunGateTest" --tests "*.EmbeddedWorkspaceProductControllerTest" --console=plain`; expect RED.
- [ ] **Implement:** Thêm generation/operation/invocation metadata và typed value status tại gate/control boundary; ghi stop intent trước khi chờ Start result, join một cleanup operation tại route. Mark invocation trước runner boundary; thay nullable no-allocation shortcut bằng explicit category có proof. Không giữ mutex qua việc chờ Start theo cách chặn stop intent. Publish kết quả immutable trước khi cleanup scope cũ kết thúc; loại stale callbacks theo exact identity. Giữ controller/renderer/session/adapter và Job cleanup ngoài Application product state.
- [ ] **Targeted verify:** Rerun hai lớp trên; expect PASS. Inspect scoped diff/reference graph: Application product state chỉ giá trị; operation của route có local terminal hữu hạn, bỏ route waiter/callback sau terminal kể cả blocked. Typed evidence vẫn quan sát được khi bỏ controller; không cần hung Binder kết thúc hoặc remote ownership clean. Đây là checklist verification lịch sử; evidence PASS thuộc Task 3B, không suy từ Task 3F.

**STOP/invariants (lịch sử trước cấp phép):** Không promote renderer/controller/runner/session hoặc cleanup closure/Job lên Application; không dựng RunOwner dưới tên khác. Nếu local terminal/graph detachment prerequisite chưa đạt tại checkpoint trước Task 3G, phải STOP ở lifetime blocker; prerequisite hiện đã PASS / ACCEPTED. Không chờ hung Binder/remote clean vô hạn hoặc mở rộng 006/007. Gate là single phase authority; không remote cleanup protocol, Retry/reconcile/persistence/process-death recovery.

## Task 3D — COMPLETE / PASS (connection-readiness evidence giữ nguyên)

Giữ nguyên kết quả và evidence Task 3D hiện có; không chạy lại, chỉnh production/tests hoặc suy PASS đó thành Start IPC/terminal lifetime PASS. Task 3F không thay đổi connection-readiness boundary.

## Task 3E — COMPLETE / APPROVED (historical runtime boundary)

[Báo cáo hung Start IPC / terminal detachment](../specs/2026-10-01-dwt-vdm-009-task3e-start-terminal-boundary.md) giữ nguyên. Quyết định `BLOCKED_REQUIRES_RUNTIME_PROTOCOL_REDESIGN` áp dụng cho guarantee **authoritative hung-Start cleanup**: remote Start có thể giữ monitor và Stop trước reserve không fence allocation muộn. Giữ báo cáo làm evidence cho runtime milestone riêng; 009 dùng fail-closed boundary, không triển khai remote candidate designs hoặc coi redesign đó là prerequisite được cấp phép.

## Task 3F — APPROVED (checkpoint tài liệu lịch sử)

**Lịch sử trước cấp phép implementation:** Task 3F chỉ cập nhật scope amendment và plan: fail-closed hung-Start boundary, local lifetime contract, blocker Task 3B tại thời điểm đó và prerequisite Task 3G. Verification chỉ links/whitespace/contract/status consistency; Tasks 1–2, Task 3D evidence và Task 3E report không đổi. Không build/test/device hoặc production changes trong lượt 3F; checkpoint này đã APPROVED. Blocker lifetime sau đó được giải quyết tại Task 3G, Task 3B đã accepted.

## Task 3G — Client Start Isolation + Terminal Runtime Detachment

**Status cuối:** COMPLETE / PASS / ACCEPTED — [Task 3G evidence](../../../verification/dwt-vdm-009/task-3g-client-start-isolation.md). Implementation sau Task 3F chỉ ở client-side boundary; không có remote protocol redesign.

- Immutable/detachable Start IPC task; hung task không capture session/route hoặc runtime/UI callbacks, chỉ giữ required transport/value objects.
- Terminal copy → detach notification consumers → complete route waiters → clear prepared/owned Surface/session holders và release route/controller graph.
- Coordinated runner admission shutdown/pending reply handling, actor/timer termination và cached terminal result fast path.
- Late Start completion bị drop; không đổi terminal result/`CLEANUP_BLOCKED` hoặc revive run. Unresolved remote ownership vẫn `CLEANUP_BLOCKED`, không Retry/reconcile/global-clean.

Evidence Task 3G đã chứng minh bốn client-side lifetime conditions của Task 3B bằng local tests/source audit; Task 8 architecture audit PASS. Hung Binder transaction vẫn có thể pending nếu không giữ old route/runtime graph. Không yêu cầu authoritative cleanup của permanently hung remote Start, remote pre-reserve cancellation/tombstones, concurrent remote operation identities, monitor rewrite, AIDL cancellation hoặc UserService kill. Native/vendor Surface lifetime và heap/GC proof vẫn **NOT VERIFIED / OUT OF SCOPE**.

## Task 4 — Route disposal and recreation fail-closed observation

**Status cuối:** COMPLETE / PASS / ACCEPTED — [Task 4 evidence](../../../verification/dwt-vdm-009/task-4-route-lifecycle.md). Forced Activity recreation device proof vẫn NOT VERIFIED; giữ local evidence, không suy PASS từ normal Back.

**Files:** Modify `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/product/EmbeddedWorkspaceProductScreen.kt`, `EmbeddedWorkspaceProductController.kt`, `EmbeddedProductRunGate.kt` cùng thư mục chỉ cho value-status observation và `app/src/main/java/com/trancong/dexworkspacetouch/navigation/TouchNavigation.kt`; create `app/src/test/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/product/EmbeddedProductLifecycleTest.kt`; extend `EmbeddedWorkspaceProductControllerTest.kt`.

**Interface:** Host cũ sở hữu renderer/controller/execution/Surface và request cleanup một lần khi dispose trong STARTING/ACTIVE/STOPPING. Host mới quan sát gate/value-only status từ Task 3B; không tạo replacement controller cho token cũ, attach Surface hoặc cấp token mới khi old gate non-IDLE. Back/close/dispose join cùng cleanup; chỉ navigation do Back mới pop một lần sau clean, callback của host đã dispose không điều khiển UI mới. Recreation chỉ trong cùng DWT process, không phải Application tiếp tục execution cũ.

- [ ] **RED test:** `EmbeddedProductLifecycleTest` dùng fake old/new hosts cho disposal trong STARTING/ACTIVE/STOPPING, pending Start+Back, double Back, stale result và recreation trước/sau terminal. Assert một cleanup, không replacement controller/Surface/new token, Classic và Embedded conflict bị chặn; clean → IDLE → run mới được phép; incomplete/uncertain → CLEANUP_BLOCKED chỉ value status, không Retry/recreate handle. Assert cleanup scope kết thúc và route waiter/callback được bỏ sau cả clean lẫn blocked, host mới không dùng operation cũ. Run `.\gradlew.bat :app:testDebugUnitTest --tests "*.EmbeddedProductLifecycleTest" --tests "*.EmbeddedWorkspaceProductControllerTest" --console=plain`; expect RED.
- [ ] **Implement:** Chọn status surface trước khi tạo runtime khi gate thuộc host cũ: STARTING/ACTIVE/STOPPING hiện previous-run/cleanup và chờ terminal; CLEANUP_BLOCKED hiện value-only cause/evidence/support. Host cũ fail closed, cleanup hội tụ trong bound hiện có và bỏ scope/route graph sau terminal; gate là authority, không button state. Chỉ IDLE mới tạo route execution mới; không yêu cầu UI-independent cleanup handle.
- [ ] **Targeted verify:** Rerun hai lớp trên; expect PASS. Inspect scoped diff/lifetime paths cho graph release và không có runtime handle trong Application status.

**STOP/invariants:** Nếu tests không thiết lập được bounded convergence/release, giữ gate fail closed và STOP. Không Surface reattachment, duplicate cleanup, renderer/runner rewrite hoặc đổi Classic `ActivityOptions`.

## Task 5 — `CLEANUP_BLOCKED` product UX

**Status cuối:** COMPLETE / PASS / ACCEPTED — [Task 5 evidence](../../../verification/dwt-vdm-009/task-5-cleanup-blocked-ux.md). Deliberate CLEANUP_BLOCKED failure injection trên device vẫn NOT VERIFIED.

**Files:** Modify `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/product/EmbeddedWorkspaceProductScreen.kt`, `EmbeddedProductRunGate.kt` cùng thư mục chỉ cho typed value status, `app/src/main/java/com/trancong/dexworkspacetouch/navigation/TouchNavigation.kt`, `app/src/main/java/com/trancong/dexworkspacetouch/ui/screens/HomeScreen.kt`; create `app/src/test/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/product/EmbeddedCleanupBlockedUxTest.kt`.

**Interface:** `CLEANUP_BLOCKED` consume chỉ Application-scoped **value data** của Task 3B: cause, cleanup evidence và support guidance; `permittedActions` loại Start, Classic và cleanup Retry. Nếu rời route, Home hoặc status surface hiện blocked state trong khi gate vẫn giữ token, dù execution/controller cũ đã được bỏ sau terminal. State sống qua UI recreation trong cùng process, không persistence qua restart; không cần retained handle.

- [ ] **RED test:** `EmbeddedCleanupBlockedUxTest` asserts `CleanupIncomplete`, `RecoveryRequired` and unknown exception each show distinct cause/evidence; no “Thử dọn lại”, “Đã sửa xong”, Classic or Start action is offered; route exit requires an app-scoped visible recovery state; a plain `Kiểm tra lại` cannot unlock gate. Bỏ fake execution/controller sau terminal rồi recreate UI vẫn render cùng value evidence; không constructor execution hay close/Retry call. Run `:app:testDebugUnitTest --tests "*.EmbeddedCleanupBlockedUxTest" --console=plain`; expect RED.
- [ ] **Implement:** Render blocked state từ typed value recovery/status; thêm chỉ safe navigation/support/copy. Gate/value status độc lập route disposal; không lookup execution/controller để render hoặc reconcile.
- [ ] **Targeted verify:** Rerun class; expect PASS. Inspect scoped diff.

**STOP/invariants:** Stop if any proposed button cannot perform a safe real action. No product reconcile/Retry, global scanner, force-stop, association deletion or Classic fallback.

## Task 6 — Typed diagnostics and explicit copy

**Status cuối:** COMPLETE / PASS / ACCEPTED — [Task 6 evidence](../../../verification/dwt-vdm-009/task-6-typed-diagnostics.md).

**Files:** Create `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/product/EmbeddedProductDiagnostics.kt`, `EmbeddedProductDiagnosticFormatter.kt`; modify `EmbeddedProductRunGate.kt` chỉ cho immutable value snapshots và `EmbeddedWorkspaceProductScreen.kt` cùng thư mục; create `app/src/test/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/product/EmbeddedProductDiagnosticFormatterTest.kt`.

**Interface:** Immutable `EmbeddedProductDiagnostics` holds workspace ID, gate phase, shortened run/generation/operation, readiness/Shizuku state, typed error, source cell, immutable result values, per-cell cleanup, owned IDs **only with provenance**, UTC time. Snapshot chỉ chứa values/results/provenance, không execution/controller/session, lazy supplier, Throwable, closure/Job/Deferred hoặc object làm old route graph reachable. `EmbeddedProductDiagnosticFormatter.format(snapshot): String` emits `DWT Embedded diagnostics v1` in stable field order; unknown IDs render `unknown`. Copy uses Android clipboard only on explicit `Sao chép chẩn đoán` tap.

- [ ] **RED test:** `EmbeddedProductDiagnosticFormatterTest` asserts exact stable v1 order, deterministic UTC formatting, `unknown` for IDs without provenance, bounded length, and absence of license token/keys, Binder/Surface, stack trace, logcat, Workspace canvas and unnecessary package identity. Bỏ route/execution trước format/copy vẫn dùng được snapshot, field graph chỉ chứa giá trị. Test no snapshot → “Chưa có chẩn đoán Embedded”. Run `:app:testDebugUnitTest --tests "*.EmbeddedProductDiagnosticFormatterTest" --console=plain`; expect RED.
- [ ] **Implement:** Assemble bản sao typed immutable snapshots tại gate/status transitions từ receipts/results có sẵn; không lấy thông tin bằng retained execution/controller hoặc supplier capture route, không bịa VDM/task ID. Format allowlisted fields; wire user-driven copy in route UI.
- [ ] **Targeted verify:** Rerun class; expect PASS. Inspect scoped diff and formatter field allowlist.

**STOP/invariants:** Unknown provenance stays unknown. No raw Binder, Surface, secret, license material, large exception text, or automatic export. Do not change license/security subsystem.

## Task 7 — Normal product UI and developer proof access

**Status cuối:** COMPLETE / PASS / ACCEPTED — [Task 7 evidence](../../../verification/dwt-vdm-009/task-7-proof-access.md).

**Files:** Modify `app/src/main/java/com/trancong/dexworkspacetouch/ui/screens/HomeScreen.kt`, `app/src/main/java/com/trancong/dexworkspacetouch/navigation/TouchNavigation.kt`; create `app/src/test/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/product/EmbeddedProofAccessTest.kt`. Keep existing proof screen files and route strings intact.

**Interface:** Ordinary Home exposes Library `Mở` and `Mở Embedded (Thử nghiệm)`; a deliberate developer/experimental entry reveals the five proof actions. Direct route strings `embedded-waze`, `embedded-calculator`, `embedded-dual-app`, `embedded-workspace-runner`, `embedded-workspace-layout/{workspaceId}` remain navigable. Route entry checks product gate when proof execution may conflict.

- [ ] **RED test:** `EmbeddedProofAccessTest` verifies ordinary Home does not expose five proof controls, developer entry does, each direct route name remains defined, and direct proof navigation while product gate holds STARTING/ACTIVE/STOPPING/CLEANUP_BLOCKED is rejected, kể cả chỉ còn value-only blocked status sau khi bỏ execution/controller. Run `:app:testDebugUnitTest --tests "*.EmbeddedProofAccessTest" --console=plain`; expect RED.
- [ ] **Implement:** Move only presentation of existing proof buttons to the deliberate developer section and guard route entry; retain route definitions and callbacks. Leave Workspace Library `Mở`/Embedded action semantics unchanged.
- [ ] **Targeted verify:** Rerun class; expect PASS. Inspect scoped diff and route-name search.

**STOP/invariants:** Do not delete proof routes/screens, bypass product gate, alter Classic launcher or make Embedded default.

## Task 8 — Regression and full local gate

**Status cuối:** COMPLETE / PASS / ACCEPTED — [final local gate](../../../verification/dwt-vdm-009/local-gate.md). Focused **477 tests PASS**; full **1159 tests PASS**; `assembleDebug` PASS; `lintDebug` PASS (**0 errors, 46 warnings**); architecture audit, forbidden-scope audit và security sanity PASS. Không chạy lại gate trong closeout.

**Files/kết quả:** Không sửa product/tests trong Task 8. Đã tạo `verification/dwt-vdm-009/local-gate.md` và evidence liên kết; giữ nguyên các báo cáo trước và báo cáo này trong closeout. Các bước dưới là checklist triển khai lịch sử.

**RED baseline/check first:** Review Tasks 1–2 và 3B–7 test results/scoped diffs; failure, long-lived Application runtime/UI retention hoặc cleanup scope không kết thúc sau terminal là RED và chặn gate này. Không dùng guarantee cleanup độc lập UI hoặc RunOwner test làm prerequisite. Run affected test classes first, then 006 runner, 007 renderer, 008 product integration and Classic Workspace regression tests using their existing class filters. Do not rerun successful checks after unrelated documentation edits.

- [ ] **Step 1 — Focused regression:** Run the named 009 tests plus `EmbeddedWorkspaceRunnerTest`, `EmbeddedWorkspaceRendererCoordinatorTest`, `EmbeddedWorkspaceRunnerControllerTest`, 008 product tests and `WorkspaceLaunchViewModelTest`/affected Classic tests; record PASS/FAIL per command.
- [ ] **Step 2 — Full local gate:** Only after focused PASS run `.\gradlew.bat testDebugUnitTest assembleDebug lintDebug --console=plain`; require exit 0 and no failed tasks. Run `git diff --check` and inspect scoped diff.
- [ ] **Step 3 — Architecture scan:** Verify Classic launcher/`ActivityOptions` and flags unchanged; no Room migration, >2 expansion, 006/007 rewrite, product cleanup Retry/reconcile, durable journal/persistent ownership marker, pending/commit, recovery enrollment, cross-process reconstruction/reconcile, AIDL recovery edits, global cleanup hoặc mandatory Shizuku. Application product state chỉ chứa gate/token/generation/operation và immutable values; không RunOwner/runtime/UI/cleanup handle dài hạn. Runtime thuộc host cũ; chỉ một bounded cleanup operation tạm giữ UI, scope kết thúc sau terminal và host mới không reuse/reattach. Record evidence in `local-gate.md`.

**STOP/invariants:** Any failed gate or forbidden diff stops before Task 9. Fix only current-scope failures and rerun affected checks; no release, licensing, signing or dependency edits.

## Task 9 — Small production-signed S23 device proof

**Status cuối:** COMPLETE / PASS / ACCEPTED — [final S23/DeX production-signed proof](../../../verification/dwt-vdm-009/s23-hardening-proof.md). Production-config/signing, artifact/readback hash và expected signer match, same-signer in-place install PASS; S23 Ultra/DeX unavailable → Ready không restart, explicit Start → STARTING → ACTIVE, Back → STOPPED / CLEAN_CONFIRMED / IDLE, explicit clipboard diagnostics và Home/developer presentation PASS. Forced recreation/failure injection không được thực hiện, vẫn NOT VERIFIED.

**Files/kết quả:** Không sửa product/tests trong Task 9. Đã tạo `verification/dwt-vdm-009/s23-hardening-proof.md` và evidence liên kết; giữ nguyên báo cáo, evidence trước và `smoke-output/` trong closeout. Các bước dưới là checklist device proof lịch sử, không chạy lại.

**RED preconditions first:** Task 8 local PASS is required; không có prerequisite process-death proof hoặc Branch A/B. Use the existing `scripts/build-device-smoke.ps1` production-config/production-signed workflow; do not substitute ordinary debug APK. Record smoke APK SHA-256, installed `base.apk` SHA-256, signer SHA-256 and require `19ac0ea99125361b3c2083aaa44c8745ebad9c55fa967642c2b9e5a1086a45e7`. If signing/session unavailable or hashes do not match, record `NOT VERIFIED` and stop before claiming device PASS.

- [ ] **Step 1 — Artifact and identity:** Build via existing smoke workflow, verify signer/hash, install with same-signer in-place path, read back installed APK hash and compare. No uninstall/clear data/version changes.
- [ ] **Step 2 — One failure-oriented proof:** On S23 Ultra DeX, khi chưa có run, stop/unavailable Shizuku safely → correct Embedded message and zero allocation; start Shizuku manually → callback/`ON_RESUME` or explicit refresh reaches Ready without DWT restart; explicit Start → ACTIVE; normal host/Back cleanup → một terminal clean, gate IDLE và zero owned orphan; inspect/copy value diagnostics; ordinary Home lacks proof clutter while developer route remains reachable. Record only new 009 behavior and positively owned IDs; DWT process còn sống suốt scenario. Không yêu cầu Application owner tiếp tục execution cũ hoặc ép Activity recreation nếu chưa có cách chứng minh an toàn, deterministic.
- [ ] **Step 3 — Result:** Mark each observation PASS/FAIL/NOT VERIFIED. `CLEANUP_BLOCKED` và recreation có thể giữ unit/integration evidence nếu không có safe device proof; ghi phần device đó **NOT VERIFIED**, không suy từ Back proof. Do not deliberately induce cleanup failure or rerun 006–008 full matrices.

**STOP/invariants:** Stop on signer mismatch, orphan, cleanup uncertainty or unsafe device state; report exact blocker. Do not publish release, change signing/license, force-stop/clear package, or run global cleanup. Nếu DWT process chết ngoài ý muốn, ghi unsupported boundary và dừng scenario; không ghi recovery PASS hoặc suy “đã sạch”.

## Plan self-review

Scope đã hoàn tất: typed issue/evidence (1), Shizuku/readiness (2), client Start isolation/terminal runtime detachment prerequisite (3G), gate generation/operation/invocation contract và route execution ordering (3B), disposal/recreation fail-closed value observation và bounded local terminal release (3B/4), value-only blocked UX (5), immutable diagnostics/provenance không giữ route graph (6), proof guarding (7), regression (8), normal in-process host/Back cleanup và gate device proof (9). Hung Start chưa clean có thẩm quyền trong bounded window phải INCOMPLETE / UNCERTAIN và CLEANUP_BLOCKED, vẫn kết thúc local UI lifetime. Không requirement authoritative cleanup của permanently hung Start, RunOwner hoặc UI-independent cleanup handle, không Task 0/Branch A/B prerequisite; remote protocol redesign và process-death recovery OUT OF SCOPE.

Tasks 1–2 COMPLETE / targeted PASS; Task 3D COMPLETE / PASS connection-readiness; Tasks 3G/3B/4/5/6/7/8/9 COMPLETE / PASS / ACCEPTED. Task 3/3A/3E/3F giữ đúng trạng thái checkpoint lịch sử ở đầu plan; Task 3E không chứng minh authoritative hung-Start cleanup khả thi. Task 8 và Task 9 là final verification đã accepted; không còn Task 1–9 pending.

**NOT VERIFIED / OUT OF SCOPE:** forced Activity recreation device proof; deliberate CLEANUP_BLOCKED failure injection trên device; native/vendor Surface lifetime; heap/GC proof; authoritative cleanup của permanently hung remote Start; process-death recovery. Các giới hạn này không mở lại acceptance đã đóng. Không có Task 10 hoặc implementation cycle mới.

**DWT-VDM-009 = COMPLETE / PASS / CLOSED.**
