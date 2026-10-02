# DWT-VDM-009 — Task 6: Typed diagnostics + explicit copy

Ngày: 2026-10-02 (Asia/Saigon). Nhánh: `release/1.0-beta`.

**Task 6 hoàn tất / targeted GREEN / affected regression PASS.** Authorization hiện hành là yêu cầu EXECUTE TASK 6 ONLY; trạng thái NOT AUTHORIZED lịch sử trong plan/spec không thay thế authorization này. Không có blocker trong Task 6. Dừng tại đây; không bắt đầu Tasks 7–9.

## File thay đổi

Trong `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/product/`:

- Mới `EmbeddedProductDiagnostics.kt`: DTO bất biến và pure copy từ một `ProductRunStatus`, chỉ các trường allowlist.
- Mới `EmbeddedProductDiagnosticFormatter.kt`: formatter v1 thuần giá trị; seam copy thuộc UI chỉ gọi clipboard writer khi `onUserTap()`.
- Sửa `EmbeddedProductRunGate.kt`: tách phép chiếu nguyên nhân blocked thành `cleanupBlockedCause()` dùng chung cho diagnostics và UI. Không đổi gate admission, operation ordering, release authority hoặc callback fences.
- Sửa `EmbeddedWorkspaceProductScreen.kt`: nút **Sao chép chẩn đoán** ở màn hình product, gồm nhánh blocked chỉ đọc Application status trước loader/readiness/execution. Home đã có đường Xem trạng thái Embedded từ Task 5; không cần sửa Home/Navigation.

Test mới: `app/src/test/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/product/EmbeddedProductDiagnosticFormatterTest.kt` — 32 test.

Báo cáo và các artifact `task-6-*` là evidence mới. [Diff gate so với baseline](task-6-gate.diff), [diff screen so với baseline](task-6-screen.diff). SHA256 xác nhận **311 tệp có trước ngoài hai tệp được phép sửa giữ nguyên**; [scope audit](task-6-scope-audit.txt).

## RED và GREEN

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests '*.EmbeddedProductDiagnosticFormatterTest' --console=plain
```

Sandbox không resolve được Android Gradle plugin 9.2.1; chạy cùng lệnh với quyền cache/SDK hiện có, không đổi dependency. Lỗi môi trường không tính là behavioral RED.

Sau khi viết test, tạo API/DTO scaffold tối thiểu để biên dịch; formatter ban đầu chưa xuất text, copy seam chưa gọi writer. **RED: 32 test, 27 FAIL, 5 PASS, errors/skipped 0**; lỗi hành vi ở formatter/copy, provenance và giới hạn. Các invariant value-only/list copy vốn được scaffold đáp ứng vẫn PASS; không cố ý làm hỏng gate để tạo RED. [RED XML](task-6-red.xml), [RED log](task-6-red.log).

Sau triển khai: **GREEN cuối cùng: 32/32 PASS**, failures/errors/skipped 0. `compileDebugKotlin` và unit-test compilation thành công trong targeted build. Test bỏ controller/execution được tăng cường để thực sự Start → ACTIVE → host disposal → terminal blocked, scope root completed; chỉ sau khi bỏ các reference mới dựng diagnostics từ Application status. [GREEN XML](task-6-green.xml), [GREEN log](task-6-green.log).

Exact full-text assertions bao phủ IDLE, STARTING, ACTIVE, STOPPING, CLEANUP_BLOCKED/incomplete và CLEANUP_TIMEOUT sau terminal. Các test còn lại kiểm tra RemoteDied/RecoveryRequired, CleanupOutcomeUncertain, unknown/provenance, locale/time zone, list mutation, toàn bộ DTO graph, dữ liệu bị loại, bounds, copy trước/sau tap, transitions và stale callbacks. Formatter nullable có thông báo chính xác `Chưa có chẩn đoán Embedded`; không copy khi không có snapshot/text.

## DTO chính xác và authority

`EmbeddedProductDiagnostics` có đúng các instance fields:

```text
snapshotEpochMillis: Long
workspaceId: EmbeddedDiagnosticId
phase: ProductRunPhase
generation: Long
startOperationId: Long?
cleanupOperationId: Long?
invocationCategory: ProductInvocationCategory
resultKind: ProductResultKind?
issueCode: String?
issueSourceCellId: EmbeddedDiagnosticId
blockedCause: EmbeddedCleanupBlockedCause?
allocationEvidence: AllocationEvidence
cleanupEvidence: CleanupEvidence
items: List<EmbeddedDiagnosticItem>
omittedItemCount: Int
cleanupOutcomes: List<EmbeddedDiagnosticCleanup>
omittedCleanupCount: Int
```

`EmbeddedDiagnosticId`: `value: String?`, `provenance: EmbeddedDiagnosticProvenance?`.

`EmbeddedDiagnosticItem`: `sourceCellId`, `ownedSessionId`, `displayId` (ba `EmbeddedDiagnosticId`), `order: Int`, `phase: EmbeddedSessionPhase`.

`EmbeddedDiagnosticCleanup`: `sourceCellId: EmbeddedDiagnosticId`, `evidence: CleanupEvidence`, `failureCode: String?`.

`issueCode` lấy từ `EmbeddedProductIssue.code` đã được sealed typed mapping, không parse thông báo UI. Failure code chỉ giữ allowlist nêu dưới. Tất cả instance fields là final; constructors của snapshot/ID là private. Không có field giữ `ProductRunStatus`, `RunToken`, receipt, runtime result, failure object hoặc supplier gốc.

Authority: `EmbeddedProductDiagnostics.from(status, snapshotAtUtc)` sao chép một status giá trị. Production materialize snapshot ngay trong callback user tap từ status Application được màn hình collect; không lưu diagnostics hoặc callback mới trong gate/Application. Các transition/callback sau đó không thay đổi snapshot đã tạo. Nhánh CLEANUP_BLOCKED không đọc controller/runtime để tạo diagnostics.

## Provenance và unknown

| Trường | Nguồn duy nhất |
| --- | --- |
| workspace ID | `ProductRunStatus.token.workspaceId`, nhãn `PRODUCT_RUN_WORKSPACE` |
| generation / Start / cleanup operation | scalar operation values trong `ProductRunStatus` |
| issue source cell | typed issue `sourceCellId`, nhãn `PRODUCT_ISSUE` |
| item source cell / owned session / display | `ProductItemStatus` do `ProductExecutionValue.from` copy từ receipt, nhãn `OWNED_ITEM_RESULT` |
| cleanup source cell | `ProductCleanupStatus` copy từ cleanup outcome, nhãn `CLEANUP_RESULT` |

Không suy ID từ package/component/workspace/source cell. Display ID âm, ID trống, không có provenance hoặc không đạt chính sách chuỗi thành `unknown;provenance=unknown`. Session ID chỉ xuất khi có giá trị receipt đã copy; cleanup outcome không được dùng để suy session ID. `run_identity`, `vdm_id`, `task_id`, `remote_resource_id` luôn `unknown;provenance=unknown` vì status không có giá trị có thẩm quyền tương ứng. Readiness/Shizuku luôn `unknown` vì Application status chưa lưu chúng; không truy vấn capability/runtime để bù. Không dùng identity hash/memory address hoặc bịa token identity.

## Bounds, UTC và allowlist

- Snapshot giữ tối đa **2 item + 2 cleanup outcome**, công bố số entry bị bỏ bằng `omitted`.
- Mỗi ID tối đa **256 UTF-16 code units**. ID quá dài bị bỏ nguyên giá trị, không cắt thành ID dễ nhầm. Loại ID có control/whitespace/space separator, surrogate không hợp lệ hoặc delimiter `=`/`;`; không chia đôi surrogate pair.
- Không copy package/component, workspace model/canvas hoặc chuỗi message tùy ý. Issue code là tập hữu hạn từ typed mapper. Cleanup failure code chỉ cho phép: `CLEANUP_TIMEOUT`, `CLEANUP_INCOMPLETE`, `CLEANUP_FAILED`, `REMOTE_DIED`, `SURFACE_LOST`, `SURFACE_INVALID`, `UNKNOWN_SESSION`, `RECOVERY_REQUIRED`, `START_FAILED`. Mã khác là `unknown`.
- Formatter v1 có giới hạn hợp đồng **8192 ký tự**, bảo đảm bằng bounds của factory/schema; không cắt cuối text làm mất/biến dạng ID. Test đầu vào 10.000 item/outcome và ID dài xác minh bounds.
- Thời gian được caller cấp bằng `Instant`; DTO giữ epoch milliseconds, formatter dùng `Instant.ofEpochMilli(...).toString()` với hậu tố UTC `Z`, độ chính xác millisecond. Production dùng `Instant.now()` tại user tap. Không phụ thuộc locale/time zone hoặc ambiguous local date. Không persistence.

## Ví dụ output v1 chính xác

Case terminal `CLEANUP_TIMEOUT` có receipt/session/display provenance; full text này được exact assertion kiểm tra trong test terminal/drop-controller:

```text
DWT Embedded diagnostics v1
snapshot_utc=2026-10-02T03:04:05.123Z
workspace_id=ws;provenance=PRODUCT_RUN_WORKSPACE
phase=CLEANUP_BLOCKED
run_identity=unknown;provenance=unknown
generation=1
start_operation_id=1
cleanup_operation_id=2
invocation=RESULT_OR_UNCERTAIN
readiness=unknown
shizuku=unknown
result_kind=INCOMPLETE
issue_code=RuntimeCleanupIncomplete
issue_source_cell_id=cell;provenance=PRODUCT_ISSUE
blocked_cause=TIMEOUT
allocation_evidence=POSSIBLE_OR_OWNED
cleanup_evidence=INCOMPLETE
vdm_id=unknown;provenance=unknown
task_id=unknown;provenance=unknown
remote_resource_id=unknown;provenance=unknown
items=1;omitted=0
item[0].source_cell_id=cell;provenance=OWNED_ITEM_RESULT
item[0].owned_session_id=session;provenance=OWNED_ITEM_RESULT
item[0].display_id=10;provenance=OWNED_ITEM_RESULT
item[0].order=0
item[0].phase=ACTIVE
cleanup_outcomes=1;omitted=0
cleanup[0].source_cell_id=cell;provenance=CLEANUP_RESULT
cleanup[0].evidence=INCOMPLETE
cleanup[0].failure_code=CLEANUP_TIMEOUT
```

Không có trailing newline. Item phase là receipt value ở thời điểm authoritative result; không được sửa thành một trạng thái runtime giả sau terminal.

## Copy và CLEANUP_BLOCKED

Android clipboard chỉ được gọi trong `OutlinedButton.onClick` của nút **Sao chép chẩn đoán**. Formatter không ghi clipboard. Seam `EmbeddedProductDiagnosticCopyAction` nhận immutable String và injected writer: constructor/format không ghi, mỗi `onUserTap()` có text gọi writer đúng một lần, null không ghi. Writer/callback thuộc UI, không reachable từ diagnostics/gate/status.

Test copy giữ nguyên chính object `gate.status.value`, phase, ownership và operation evidence; Classic/Embedded mới tiếp tục bị chặn. Safe actions Task 5 vẫn BACK/VIEW_STATUS; copy support-only tách khỏi ownership action policy. Không thêm Start/Classic/Retry/reconcile/force-stop/cleanup-again/manual-unlock vào blocked branch. Không auto-copy, file export trong app, background export, upload hoặc network diagnostics.

## Security và reference-graph audit

[Source security scan](task-6-security-audit.txt) của hai file diagnostics/formatter không có match cho license, Authorization, key material, Cloudflare/admin/Bearer, stackTrace/Throwable/logcat. `status.token.workspaceId` chỉ là workspace giá trị của RunToken, không phải license/activation token. Field allowlist và tests loại raw failure message, exception/stack/Binder/Surface representations, package/component payload và unknown code dump. Không PackageManager lookup/app enumeration, raw Bundle/reflection dump hoặc device serial.

[Compiled javap audit](task-6-javap.txt) kiểm tra gate, status, RunToken, item/cleanup status và bốn DTO classes. Application vẫn chỉ có gate đã tồn tại; gate chỉ giữ lock, hai StateFlow pairs, sequences và Classic Boolean, không thêm execution owner. `ProductRunStatus` có typed scalar/value payload. Pure copy từ payload tạo diagnostics riêng; không có supplier hoặc retained status/result/RunToken trong DTO.

Toàn bộ instance graph snapshot → identifier/item/cleanup → String/Long/Int/enum/immutable copied lists được recursive value-graph test kiểm tra. Không có path từ diagnostic snapshot tới controller, runner, session handle, renderer, execution adapter, Binder, Surface/View/Activity/Context, Job/Deferred, Flow, callback hoặc Throwable. StateFlow hiện có thuộc gate, không thuộc diagnostic DTO. Lists có backing copy riêng và không cho consumer mutate; input list mutation, later gate transition và stale old callback không đổi snapshot.

Đây là source/compiled-field/unit-test audit, **không phải heap/device proof**.

## Affected regression

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests '*.EmbeddedCleanupBlockedUxTest' --tests '*.EmbeddedProductRunGateTest' --tests '*.EmbeddedProductIssueTest' --tests '*.EmbeddedWorkspaceProductRoutingTest' --tests '*.EmbeddedShizukuRefreshTest' --tests '*.EmbeddedProductLifecycleTest' --tests '*.EmbeddedWorkspaceProductControllerTest' --tests '*.EmbeddedWorkspaceRunnerTest.every true terminal clears runtime graph and ends root job while cache remains usable' --console=plain
```

**BUILD SUCCESSFUL, exit 0; 126/126 PASS**, failures/errors/skipped 0 từ XML đã đọc và lưu trước khi bị ghi đè:

| Class/filter | PASS |
| --- | ---: |
| EmbeddedCleanupBlockedUxTest | 29 |
| EmbeddedProductRunGateTest | 16 |
| EmbeddedProductIssueTest | 17 |
| EmbeddedWorkspaceProductRoutingTest | 3 |
| EmbeddedShizukuRefreshTest | 14 |
| EmbeddedProductLifecycleTest | 22 |
| EmbeddedWorkspaceProductControllerTest | 24 |
| EmbeddedWorkspaceRunnerTest — terminal/cache filter | 1 |

[Regression log](task-6-regression.log), [XML summary](task-6-regression-summary.json). Một test terminal/cache Task 3G bao phủ terminal clear graph/root completion và cached result dùng được; test diagnostics bổ sung xác minh pure Application status copy sau terminal/drop-controller. Không sửa runtime/session/renderer/AIDL/protocol hoặc test Task 3G.

`git diff --check`: **PASS, exit 0**; [evidence](task-6-diff-check.txt). Diff gate/screen đã được review so với working-tree baseline để tách khỏi thay đổi Task trước.

## NOT VERIFIED và điểm dừng

Clipboard/user tap/Activity recreation trên thiết bị thật, heap/GC/native lifetime và real Binder scheduling: **NOT VERIFIED**. Clipboard seam đã unit-test và production wiring đã source-audit; không claim Compose/device automation. Full Task 8 gate và device proof không chạy theo scope. Authoritative cleanup của permanently hung Start và process-death recovery không được tuyên bố.

Không đổi licensing/security/release/dependency/database, Home/Navigation, runtime 006/renderer 007/remote protocol, không persistence/Retry/reconcile. Không reset/stash/clean/stage/commit/push. **STOP AFTER TASK 6. Không có blocker; Tasks 7–9 chưa được thực hiện.**
