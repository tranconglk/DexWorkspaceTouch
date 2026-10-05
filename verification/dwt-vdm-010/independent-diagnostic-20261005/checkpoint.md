# DWT-VDM-010 — Independent diagnostic testbed checkpoint, 2026-10-05

**IN PROGRESS / STOP tại completion condition D: testbed mới bị chặn trước Start. 010 chưa PASS, chưa ready for commit.**

Đúng **một** Calculator + Waze instrumentation invocation đã chạy. Không có remote Start hoặc session mới được tạo. Failure mới là `java.lang.IllegalStateException: Product action not available: Kiểm tra lại`, không phải Calculator Start exception.

## Historical blocked run — evidence only

SID `0bf0ecec-9e2c-4196-8edb-a076b688edb1` tiếp tục **AUTHORITATIVE RELEASE = NOT VERIFIED**; raw Start root cause cũng NOT VERIFIED. Không retry/reconcile/cleanup/unlock, không query UNKNOWN_SESSION hoặc empty inventory làm release authority.

[Checkpoint lịch sử 02/10](../device-20261002/checkpoint.md) và [điều tra blocker trước quyết định testbed mới](../device-blocker-20261005/checkpoint.md) được giữ nguyên. User đã phê duyệt môi trường independent; quyết định này chỉ cấp authority cho ownership mới nếu được thiết lập, không chuyển authority cho run cũ.

## Thay đổi và lý do

Thêm [debug observer](../../../app/src/debug/java/com/trancong/dexworkspacetouch/qualification/QualificationDiagnostics.kt) và [source mirror generator](../../../experiments/dwt-vdm-010-host-harness/Prepare-Diagnostics.py). Mirror chỉ được bật qua [init script](../../../experiments/dwt-vdm-010-host-harness/isolated-app-id.init.gradle) với `-Pdwt.vdm010.diagnostics=true`; source production cho build thường không chỉnh sửa.

[Manifest](diagnostic-overlay-manifest.json): 267 source files được copy; bốn bản copy có 16 điểm insertion diagnostic. Strip toàn bộ điểm insertion phục hồi **nguyên byte** source gốc. Mirror cài vào cả Java và Kotlin source sets; release tasks bị từ chối khi bật overlay.

Capture có các điểm:

- Runner session ownership và raw snapshot trước value mapping: SID/cell, timestamp/PID, host/token identity, generation/Start operation, execution-Surface identity/isValid.
- Local native Surface tại Start entry; raw Start completion trước fence; runner snapshot vẫn cho biết accepted lifecycle failure.
- Remote Start entry/input-wait entry/return/failure và native launch Surface guard.
- Input-stability sample: display ID, section/length/hash, contains-finger, equals-previous, equal counter và deadline còn lại; section cap 32.000 ký tự có explicit truncation flag.
- Runner Started/terminal result: exact receipts, partialReceipt, raw failure code/message và rollback/cleanup outcomes trước ProductExecutionValue.

Observer giữ bản sao giá trị và gate đọc-only, không host/Surface/session handles; không ghi gate hoặc đưa raw message vào Application status. Remote records liên kết SID với local owner metadata; không thêm protocol/AIDL fields. Logging có thể ảnh hưởng timing; không claim runtime/vendor lifetime guarantee.

[Real debug host](../../../app/src/debug/java/com/trancong/dexworkspacetouch/qualification/EmbeddedRealProductHarnessActivity.kt) ghi NEW_INDEPENDENT_TESTBED và run ID. [C instrumentation](../../../app/src/androidTest/java/com/trancong/dexworkspacetouch/qualification/EmbeddedRealProductLifecycleDeviceTest.kt) guard overlay trước Start, fail-fast khi CLEANUP_BLOCKED, namespace output theo run ID, giữ output lịch sử.

[Runner](../../../experiments/dwt-vdm-010-host-harness/Run-Qualification.ps1) bắt buộc một allowlisted real method và independent run ID, guard signer/package/target/version, lưu diagnostics dù FAIL; APK đã cài giống hash thì không reinstall. Plan có marker exclusive cho một invocation; không tự retry.

## A — LOCAL / FAKE

Build debug + androidTest với actual Kotlin overlay: **PASS**.

Hai [XML qualification](EmbeddedHostLifecycleQualificationTest.xml) / [diagnostic replay](EmbeddedStartFailureEvidenceQualificationTest.xml): **7 tests PASS**, 0 failures/errors/skips. Chạy lại đúng hai classes vì runner dùng mirror có observer mới; không full gate/009/unrelated regression.

Guard byte-roundtrip: **PASS**, bốn original source hashes khớp. Runtime/gate behavior của fake lifecycle cases giữ nguyên.

Compile/command errors và wiring failure của harness là lịch sử diagnostics, không được gọi là production behavioral RED.

## B — ANDROID CONTROLLED CAPTURE

[Test capture plumbing](../../../app/src/androidTest/java/com/trancong/dexworkspacetouch/qualification/EmbeddedDiagnosticCaptureDeviceTest.kt): **1/1 PASS**. [Run B theo plan](independent-run-plan.json) / [summary](summary.json) ghi artifact identity. App APK ở C khớp hash app APK đã được B kiểm tra.

B xác nhận timestamp/PID, synthetic host identity, exact product token/generation/Start op, SID/cell, raw code/message, native Surface logging, exact partialReceipt/Incomplete outcome trước value mapping. Dữ liệu failure/host/receipt là controlled; không Binder/VDM Start.

Self-test đầu tiên FAIL tại overlay-presence guard: Java source set đã đổi nhưng Kotlin còn source gốc. Đã xác định qua public DSL metadata, wire Kotlin source set vào mirror và PASS trước C. Không có Calculator/Waze Start trong các self-test.

B này chỉ chứng minh plumbing của capture; không real Activity recreation, input-stability behavior, remote failure/cleanup hoặc authoritative convergence. B 4/4 lifecycle trước đây giữ evidence riêng, không chạy lại.

## C — NEW INDEPENDENT S23 / DeX

Run ID **`vdm010-independent-d7804af0-fdef-4ca1-9e2c-5b1b815166ea`**. [Plan](independent-run-plan.json), [artifact/output](../android-real/20261005T043739747Z), [summary/hash](summary.json).

- DeX display **182**, main PID **25076**, host ID 1 / identity **49277327**. Object identity chỉ có ý nghĩa trong process đó; không so sánh với identity trùng số của process lịch sử.
- Fresh NEW gate: generation **0**, token/start/cleanup **null**, IDLE/NOT_NEEDED, items/outcomes rỗng trong toàn bộ trace.
- UI failure tại first readiness refresh `clickText("Kiểm tra lại")`, **trước** dòng click Start. Không tới createProductRouteExecution/runner session ownership.
- **Một instrumentation invocation; zero remote Start calls; zero new session IDs.** Lifecycle progression dừng; không recreate/fresh generation/Surface qualification.
- Local raw diagnostics file rỗng do không có execution/Start. [Log inventory](diagnostic-log-inventory.json) có năm records **B_DIAGNOSTIC_CAPTURE_SELFTEST**, zero C records; không nâng B records thành real native Start evidence.
- Host disposal vẫn thuộc NEW never-invoked run. IDLE/NOT_NEEDED ở đây không phải clean/release receipt cho run cũ.

[Target resolution](target-resolution-readonly.json) và [launcher lookup](launcher-resolution-readonly.json) sau invocation cho thấy cả hai component tồn tại đúng tên. Đây là current read-only context, không chứng minh failure-time UI/readiness state hoặc Start thành công.

## Blocker, architecture và risk

Exact observed pre-Start failure đã có stack/source order. `clickText` chỉ báo một lỗi chung sau deadline khi không tìm thấy node phù hợp, node/parent disabled hoặc ACTION_CLICK không thành công. Invocation không lưu failure-time accessibility tree/readiness snapshot, nên **nguyên nhân cụ thể giữa các khả năng đó NOT VERIFIED**. Không sửa selector/runtime dựa trên suy đoán.

Không có new in-scope production defect được chứng minh. Không production patch mới; hai fix 010 RED-verified trước đây giữ nguyên. Ownership/result authority, fail-closed gate, renderer/runtime semantics, geometry, AIDL/remote protocol, signing/config/version và 009 không đổi.

STOP hiện tại là **completion condition D** và giới hạn **một invocation** user đã phê duyệt. Không dùng failure pre-Start để tự cấp lượt C khác. Chưa cần đổi architecture; bất kỳ giải pháp đổi authority/runtime semantics/recovery vẫn chịu Charter STOP.

[Scope audit](scope-audit.json): worktree/branch/base đúng, không staged paths; original hai modified hashes khớp checkpoint, 148 untracked quan sát được bảo toàn. Không commit/push/tag; chỉ cài APK isolated cùng signer, không production install/uninstall/clear hoặc old-SID IPC.

## NOT VERIFIED / next

- Historical SID: raw Start exception, authoritative release/convergence.
- NEW testbed: exact UI/accessibility/readiness cause; runtime Start failure/behavior, remote input samples, actual execution-Surface checkpoints.
- Required Calculator + Waze ACTIVE/recreation/one cleanup/exact clean/fresh Start/Surface lifecycle.

Next: bổ sung pre-Start snapshot của windows/accessibility nodes và readiness text/state, xác nhận đường refresh/Start trên DeX 182. Nếu cần một real invocation nữa, phải có authorization mới cho lượt đó; không implicit retry. Không đề xuất production fix từ current UI error hoặc timing inference cũ.
