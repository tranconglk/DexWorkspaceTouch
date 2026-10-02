# DWT-VDM-009 — Task 4: Route disposal + recreation fail-closed observation

Ngày: 2026-10-02 (Asia/Saigon). Branch: `release/1.0-beta`.

**COMPLETE / targeted và affected regression PASS.** Authorization là prompt EXECUTE TASK 4 ONLY; trạng thái NOT AUTHORIZED cũ trong plan/spec đã được thay thế riêng cho Task 4. Tasks 5–9 vẫn NOT AUTHORIZED. Không bắt đầu Task 5.

## Thay đổi

Production trong `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/product/`:

- `EmbeddedWorkspaceProductScreen.kt`: quan sát `gate.status` trước allocation; graph route chỉ được tạo từ thao tác Start ở IDLE. Host mới non-IDLE chỉ hiển thị previous-run hoặc typed blocked status. Terminal bỏ graph cũ, không tự restart. Back dùng waiter có lifetime guard; readiness observer của host mới không truy cập execution cũ.
- `EmbeddedWorkspaceProductController.kt`: Back có một waiter navigation; Dispose gỡ callback ngay, cancel waiter navigation và join Close hiện có. Prepared graph chưa invoke cũng dùng một local Close để kết thúc runner; không tạo token/cleanup operation hoặc gửi local result vào gate. Giữ proof release và fences Task 3B.
- `EmbeddedProductRunGate.kt`: thêm `createExecutionIfIdle`, kiểm tra status/token/Classic dispatch trước gọi factory đồng bộ dưới lock. Không lưu factory hoặc graph trong gate; không sửa generation/operation/result acceptance.

Test mới: `app/src/test/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/product/EmbeddedProductLifecycleTest.kt` — 22 tests. Không cần sửa controller tests hoặc `TouchNavigation.kt`. Báo cáo này và logs kiểm chứng là material mới.

## RED thực tế

1. Baseline API hiện có: **25 tests, 1 FAIL**. Dispose trước Start vẫn giữ execution/readiness closure.
2. Ma trận lifecycle với API seam tái hiện đường eager allocation và `launch { requestExit(); onBack() }` trước hardening: **41 tests, 10 FAIL**. Failure hành vi: replacement factory chạy ở STARTING/ACTIVE/STOPPING/blocked; double Back tạo hai waiter; Dispose trước Start còn giữ graph. Các assertion quan sát factory side effects và controller thật, không chỉ UI text.
3. Audit prepared graph: **45 tests, 2 FAIL** vì Back/Dispose trước Start không gọi local Close; test dùng runner thật chứng minh đường Close bị bỏ qua.
4. `preRunnerRejectionAlsoClosesPreparedRunnerWithoutAnotherGateOperation`: **1/1 FAIL** trước sửa; graph chuẩn bị vẫn phải được kết thúc sau authoritative pre-runner rejection.

Lượt sandbox đầu không tới tests do không resolve Android Gradle plugin. Đã chạy cùng lệnh với quyền cache/SDK hiện có. Một lượt compile lỗi khai báo chèn trùng đã được sửa trước ma trận RED; không tính compile failure là behavioral proof. Không đổi dependency.

## GREEN và targeted tests

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests '*.EmbeddedProductLifecycleTest' --tests '*.EmbeddedWorkspaceProductControllerTest' --console=plain
```

**BUILD SUCCESSFUL; 46/46 PASS** theo XML đã đọc: lifecycle 22, controller 24; failures/errors/skipped đều 0. Kotlin production/tests biên dịch thành công trong command này.

Affected regression cuối cùng, chạy sau sửa prepared graph:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests '*.EmbeddedProductRunGateTest' --tests '*.EmbeddedProductIssueTest' --tests '*.EmbeddedWorkspaceProductRoutingTest' --tests '*.EmbeddedShizukuRefreshTest' --tests '*.EmbeddedWorkspaceRunnerTest.every true terminal clears runtime graph and ends root job while cache remains usable' --console=plain
```

**BUILD SUCCESSFUL; 51/51 PASS** theo XML: gate 16, issue 17, routing 3, Shizuku refresh 14, Task 3G terminal/cache 1; failures/errors/skipped đều 0. Chỉ một regression Task 3G được chọn; targeted controller/lifecycle integration đã kiểm chứng blocked observation với runner terminate.

## Counts và navigation

Các fake host gọi admission/controller production thật. Counters tách riêng controller construction, execution construction, Surface/host allocation, Start, Close, generation/token acquire, navigation, old callback injection và new status collection.

| Tình huống | Controller/execution/Surface của host mới | Start/Close của host cũ | Token mới | Navigation pop |
| --- | --- | --- | --- | --- |
| Recreation STARTING/ACTIVE/STOPPING | 0 / 0 / 0 | 1 / 1 | 0 | old 0, new 0 |
| Recreation CLEANUP_BLOCKED | 0 / 0 / 0 | không gọi lại Start/Close | 0 | 0 |
| Clean → IDLE, chưa có thao tác Start mới | 0 / 0 / 0 | 1 / 1 | 0 | Dispose 0 |
| User Start sau clean → IDLE | 1 / 1 / 1 | cleanup cũ không lặp | 1, token/generation khác | old 0 |
| Pending Start + Back + recreation | 0 / 0 / 0 | 1 / 1 | 0 | old 0, new 0 |
| Double Back khi host còn sống | không tạo thêm graph | 1 / 1 | 0 | đúng 1 sau clean |
| Double Back + Dispose + recreation | 0 / 0 / 0 | 1 / 1 | 0 | old 0, new 0 |
| Prepared graph, chưa Start, Back + Dispose | graph local cũ được Close và terminate | 0 / 1 | 0 | disposed host 0 |

Token được đếm bằng status collection theo generation, độc lập với Start count. Repeated Dispose trả cùng Job; cleanup operation ID không đổi; old scope/root hoàn tất, không còn children. Clean lẫn incomplete/uncertain đều bỏ execution/readiness/navigation callback.

## PASS criteria và stale callbacks

| Tiêu chí | Bằng chứng | Kết quả |
| --- | --- | --- |
| STARTING recreation | `recreateWhileStartingDoesNotReplaceExecution` | PASS |
| ACTIVE recreation | `recreateWhileActiveDoesNotReplaceExecution` | PASS |
| STOPPING recreation | `recreateWhileStoppingDoesNotReplaceExecution` | PASS |
| CLEANUP_BLOCKED recreation và bỏ old graph | `recreateAfterBlockedSurvivesDroppingOldRuntime` | PASS |
| Authoritative clean → IDLE, không auto restart | `cleanTerminalAllowsOneFreshRunOnlyAfterUserStart` | PASS |
| Pending Start + Back + recreation | `pendingStartBackAndRecreationJoinOneCleanupWithoutOldNavigation` | PASS |
| Double Back + recreation | `doubleBackAndRecreationDoNotPopNewHost` | PASS |
| Dispose + recreation trước terminal | `disposeBeforeCleanupTerminalKeepsObserverValueOnly` | PASS |
| Old terminal clean/incomplete/uncertain sau new host | ba `oldTerminal...AfterRecreation...` tests; gate đúng phase, old/new pop 0, new chỉ nhận status values | PASS |
| Old late Started sau new host | `oldLateStartedAfterNewHostCannotActivateOrRelease`; Stop intent/blocked status không đổi | PASS |
| Stale cleanup sau run mới | `staleCleanupCallbackCannotReleaseNewerRun`; exact old identity và generation giả đều bị từ chối | PASS |
| Blocked không cần controller/runtime sống | blocked graph test và hung-Start integration | PASS |
| Clean cho phép đúng một controller/run mới sau user Start | fresh run test; constructor/Start/token counters | PASS |
| Non-IDLE không replacement controller | `everyNonIdlePhaseRejectsFactoryBeforeAnyAllocationOrToken` | PASS |
| Non-IDLE không replacement Surface/execution | cùng test, counters riêng và constructor path audit | PASS |
| Non-IDLE không token mới/Classic dispatch | cùng test, generation collector và `tryDispatchClassic` | PASS |
| Old readiness/Shizuku callback | disposed refresh callback không đọc probe hoặc đổi state; new refresh chỉ đổi capability, không đổi ownership/status hoặc cho Classic | PASS |
| Back sau Dispose không giữ/gọi navigation cũ | `backAfterDisposeCannotRetainOrInvokeOldNavigation` | PASS |
| Pre-runner rejection/Back trước Start kết thúc prepared runner | hai runner-backed tests; root completed, scope null, không có cleanup operation mới | PASS |

## Hung Start boundary

`hungStartDisposeRecreatesBlockedUiAfterBoundedLocalTerminalWithoutHandle` dùng runner thật, session fake không phát Start/cleanup terminal. Start invoked → Back/Dispose → 20 giây virtual cleanup timeout → `CleanupIncomplete`, `CLEANUP_TIMEOUT`, `CLEANUP_BLOCKED`.

Runner root/children kết thúc; runnerScope/sessionFactory và notification consumer được bỏ. Controller bỏ execution/readiness/back callback, route scope hoàn tất. Host mới 0 controller/execution/Surface/token; không Classic, không Start, không cleanup lại. Cached Stop vẫn dùng được; inject late ACTIVE/Started không mutate cache/status. Không cần reply Start để đạt local terminal. Đây là local lifetime proof; không suy thành authoritative remote cleanup.

## Reference-graph audit

Source và compiled fields (`task-4-value-graph-javap.log`) đã kiểm tra:

```text
Application.embeddedProductRunGate
  → lock + StateFlow(status/phase) + scalar sequences/Classic flag
  → ProductRunStatus
  → token(workspaceId), scalar identities, enums, typed issue, immutable copied item/outcome lists
```

Không có đường từ gate/status tới old controller, runner, session, renderer, Surface/View/Activity, execution adapter, old navigation callback, cleanup Job/Deferred hoặc factory closure. `createExecutionIfIdle` chỉ gọi factory trên stack dưới lock; không thêm instance field.

Host mới khởi tạo state local `execution = null`. Observation content chỉ đọc gate/status cùng capability probe/refresh của host mới. Constructor runner/renderer/product chỉ nằm trong factory sau user Start và gate IDLE check; Surface composable chỉ xuất hiện cho graph route đã được admitted. Status thuộc token khác hoặc terminal không render renderer. Terminal transition bỏ graph và không gọi factory. Không reattach Surface hoặc tạo controller cho old token.

Back coroutine bytecode chỉ capture controller route, không capture đối số `onBack`. Callback nằm trong field route, được đặt null ngay khi Dispose và sau waiter terminal. Cleanup Deferred/Job vẫn thuộc controller cũ, không được gửi sang host mới. Process scope hiện có chỉ có thể tạm giữ coroutine cleanup hữu hạn của host cũ, theo ngoại lệ đã duyệt; không thêm Application cleanup handle/RunOwner. Prepared graph chưa invoke được Close qua API hiện có, không đổi remote/gate authority.

## Bảo toàn và kiểm tra diff

Scoped diff đã đối chiếu bản working-tree baseline trước Task 4, không chỉ HEAD. **147 existing changed/untracked files ngoài ba production edits giữ nguyên SHA256**, gồm toàn bộ Task 3D/3G, controller/gate tests hiện có, tài liệu/evidence cũ và experiment material.

`git diff --check`: **PASS**. Whitespace của file test/báo cáo mới được kiểm tra riêng. Không reset/stash/clean/stage/commit/push. Không sửa runtime 006/007, Application, navigation, license/security, Room/database, dependency hoặc release/signing. Không Retry/reconcile hoặc process-death recovery.

## NOT VERIFIED và điểm dừng

Activity recreation/Surface attach trên thiết bị thật, native/vendor resource lifetime, heap/GC trên thiết bị và real Binder scheduling: **NOT VERIFIED**. Các allocation/attachment counters của lifecycle là fake instrumentation; source audit xác nhận production path, không thay thế device proof. Authoritative cleanup của permanently hung remote Start: **NOT VERIFIED**, vẫn fail closed. Full Task 8 gate và device proof không chạy theo yêu cầu.

Không có blocker trong Task 4 theo evidence trên. **STOP AFTER TASK 4. Tasks 5–9 NOT AUTHORIZED.**
