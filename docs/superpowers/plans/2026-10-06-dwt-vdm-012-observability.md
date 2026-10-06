# DWT-VDM-012 Implementation Plan

> For agentic workers: use superpowers:executing-plans; execute inline without commits.

Goal: Lưu evidence bounded cho Embedded Start mới; diagnostics không điều khiển execution.
Architecture: Hai writer value-only app/remote; ghép offline bằng SID và graph linkage. Mapper/gate/AIDL giữ nguyên.
Tech Stack: Kotlin/JVM, Android, JUnit, JSON có sẵn; không thêm dependency.
Spec: task IMPLEMENTATION AUTHORIZED trong attachment của người dùng ngày 2026-10-06; discovery trong hội thoại.
Base: release/1.0-beta db69bed601eceea12259249c89f0f5276793e425.

## Global Constraints
- Không tác động SID lịch sử 011; không sửa evidence lịch sử.
- Không đổi protocol/Bundle contract, authority, FAILED, late completion, reconcile, renderer hoặc recovery.
- Không lưu handle, gate/controller/Activity, secret, full dumpsys, arbitrary command output.
- Không commit/push/tag; không sửa dirty worktree gốc.
- Writer chỉ ghi; runtime/gate không đọc artifact. Remote path chưa VERIFIED, không dùng bridge mới.

## Review Focus
- Sink throw/full phải giữ result/gate/cleanup count và detachable Start task.
- IDs chưa quan sát hoặc references bị xóa không được suy ra resource absence.
- Missing tail/segment, queue drop và truncation phải cho NOT_VERIFIED.
- Bounds áp dụng toàn namespace qua restart, không chỉ từng SID.
- Secondary rollback error và raw IPC error phải được ghi trước projection.

## 012-A — schema/sink/offline reader
Files: diagnostics/embedded/{EmbeddedEvidenceEvent,EvidenceRecorder,EvidenceFileStore,EmbeddedEvidence}.kt; offline EvidenceReader; tests tương ứng.
Interfaces: EvidenceRecorder.emit(event, sid, cell, graph, fields); immutable scalar records; bounded drain/store; safe process facade.
- [x] RED: characterization existing runner/IPC thiếu evidence; model/retention contracts qua reflection để tránh compilation RED.
- [x] Implement schema: process epoch, sequence, UTC/monotonic, provenance, strict allowed fields/messages, omission/truncation.
- [x] Implement bounded queue (64), records (4096 bytes), storage (4 x 128KiB/domain), diagnostics-only rotation/loss indicators.
- [x] Implement offline reader/correlation; missing evidence luôn NOT_VERIFIED, không kết luận authoritative clean.
- [x] GREEN targeted tests; giữ log RED/GREEN.

## 012-B — remote hooks
Files: RemoteSessionRuntime, EmbeddedAppVdm, AssociationShell, ShellDisplayLaunch.
Consumes: value-only facade của A, SID có sẵn. No new protocol fields.
- [x] RED: injected original/secondary rollback causes thiếu evidence và stage correlation.
- [x] Hook stage/allocation/input samples/catches/launch/rollback/pre-reset; không thêm query/retry.
- [x] GREEN local observations/call order; controlled non-owning shell retention nếu device có sẵn.

## 012-C — app/session/runner/product hooks
Files: Application, StartIpcTask, EmbeddedAppSession, runner, product controller/route factory.
Consumes: facade và graph ID giá trị. ProductExecutionValue/gate không sửa.
- [x] RED: distinct causes và raw IPC/late delivery evidence thiếu.
- [x] Hook trước projection/fence; graph→product op và graph→cell/SID; per-result receipt/outcome provenance.
- [x] GREEN: disabled/full/throwing sink identical result/value/gate/count/late behavior.

## 012-D — verification/closeout
- [x] Targeted GREEN rồi affected regression, scoped compile/lint khi cần.
- [x] Offline reader missing/truncated/privacy/bounds proof.
- [x] Controlled remote path/export proof; không real Calculator/Waze Start.
- [x] Scoped diff/source authority audit và fresh review; sửa Critical/Important trong scope.
- [x] Report acceptance từng tiêu chí, NOT_VERIFIED; READY FOR COMMIT chỉ khi đủ evidence.

## Execution ledger
Pre-flight: B/C consume immutable facade from A; no result type or protocol interface change.
Ruling: Không dùng commit-based skill scripts vì user cấm commit; ledger và logs nằm verification/dwt-vdm-012.

Fresh review: một reviewer read-only trên diff hiện tại, không commit-based scripts hoặc device work.
Verdict ban đầu NOT READY: không Critical, ba Important; không yêu cầu vòng reviewer thứ hai.

| Finding | Ruling và evidence |
|---|---|
| Important: thiếu tail sau runner.result vẫn OBSERVED | Sửa bằng marker diagnostics result.end/count và yêu cầu product projection/acceptance; contiguous-prefix/missing-product-tail RED → GREEN. Không thêm acknowledgement runtime. |
| Important: top-level/identifier không sanitize trước export | Sửa bằng toàn bộ schema allowlist và copy đã validate; unknown key/unsafe identifiers RED → GREEN. |
| Important: result/outcome hai cell gắn sai SID | Result coverage theo graph/process epoch; outcome theo cell/receipt SID; hai test RED → GREEN. |
| Minor: write loss của record mang dropped_before làm đếm thiếu | Defer; sequence gap vẫn NOT_VERIFIED, count không dùng làm tổng mất chính xác. Không ảnh hưởng authority. |

Declined-to-judge rulings:
- Build/test: implementer xác minh bằng logs và JUnit XML, không suy từ review.
- Android filesystem/export: controlled shell probe đã ghi và pull JSONL; actual app-private/Shizuku end-to-end/timing/concurrency stress NOT_VERIFIED.
- Historical/device ownership: không kiểm chứng hoặc tác động; ngoài scope, không cần để đóng local observability.

Compiler errors trong quá trình xây plumbing được sửa; không tính là behavioral RED.
Reader wrapper bị host execution policy chặn: không thay policy; Java CLI offline đã chạy thành công.
Classpath selector được cập nhật cho đường dẫn compileDebugUnitTestKotlin của toolchain hiện tại.
Java CLI trên hai artifact controlled trả NOT_VERIFIED vì thiếu timeline/linkage/result tail và có message redaction.
Final gate sau sửa Important: 1196 tests, 0 failures/errors/skipped; assembleDebug và lintDebug PASS.
Chỉ cập nhật docs/script reader sau gate; không có source edit tiếp theo cần chạy lại suite.
Checkpoint: docs/codex/DWT_VDM_012_CHECKPOINT.md. Không commit/push/tag.
