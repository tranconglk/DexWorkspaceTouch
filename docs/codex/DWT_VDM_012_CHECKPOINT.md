# DWT-VDM-012 — READY FOR COMMIT

Implementation theo task IMPLEMENTATION AUTHORIZED; chưa commit/push/tag.
Worktree: `D:/AndroidStudioProjects/DexWorkspaceTouch-VDM-012`.
Branch: `implementation/dwt-vdm-012`, base `db69bed601eceea12259249c89f0f5276793e425`.
Dirty worktree gốc được giữ nguyên; status cuối vẫn là thay đổi pre-existing.

## Changed

- Hai miền evidence value-only app/remote, writer process-local, join SID offline.
- Schema immutable: process epoch, PID/UID, sequence, UTC/monotonic, stage, scalar linkage và loss/privacy indicators.
- Queue 64 record; record 4096 byte; storage mỗi namespace 4 × 128 KiB, append-only trong segment, rotation diagnostics-only có eviction marker.
- Remote hooks tại stage/substage, original catches, allocation progress, input samples có sẵn, launch, rollback/secondary exceptions và pre-reset.
- App hooks trước IPC projection 512, mailbox/fence, session lifecycle, runner receipts/partialReceipt/outcomes, product operation/projection/acceptance.
- Reader offline validate toàn bộ schema, ghép result theo graph/epoch và outcome theo cell/SID, yêu cầu result.end/count và product tail.
- Debug-only probe non-owning và công cụ Java/PowerShell offline; không thêm dependency hoặc build configuration production.

Các file hiện hữu thay đổi: Application; EmbeddedStartIpcTask; EmbeddedAppSession;
RemoteSessionRuntime; EmbeddedAppVdm; AssociationShell; ShellDisplayLaunch;
EmbeddedWorkspaceRunner; EmbeddedWorkspaceProductController; EmbeddedWorkspaceProductScreen.
Các file mới nằm trong diagnostics/embedded, tests tương ứng, debug qualification, scripts,
docs và verification/dwt-vdm-012.

## Verification

Lệnh cuối sau sửa review:

```text
gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --console=plain
```

- PASS: 1196 tests, 0 failures, 0 errors, 0 skipped; assembleDebug; lintDebug.
- Log: `verification/dwt-vdm-012/post-review-final-gate.log`.
- Behavioral RED/GREEN đã có cho distinct causes, stage/correlation, rollback retention,
  bounded retention/privacy, missing evidence. Disabled/full/throwing sink invariant GREEN
  cùng result/value/gate/cleanup-count/late-completion characterization.
- Compilation errors trong lúc xây plumbing không được tính là behavioral RED.
- Source audit và scoped diff: không thay AIDL, existing Bundle contract, mapper/gate,
  ownership/result/FAILED authority, late completion, recovery/reconcile, renderer,
  input algorithm/timeout, cleanup order, Room, license/security, signing/version/release.
- Runtime/gate không import hoặc gọi reader; file content chỉ được công cụ offline đọc.

## Controlled Android evidence

Shell UID 2000 chạy debug `app_process` entrypoint; không install vào app hiện có,
không bind UserService/registry/session, không tạo real Start hoặc real ownership run.
Surface rỗng bị guard từ chối trước allocation; cleanup chỉ trên instance không có tài nguyên.

```text
012_RAW_REPLY_CAPTURE raw_chars=1433 projected_chars=512 unsafe_message_omitted=true
012_NON_OWNING_RETENTION uid=2000 pid=14338 segments=1 bytes=7684
```

- Remote writable/exportable path thực tế: `/data/local/tmp/dwt-vdm-012-evidence`.
- JSONL đã pull vào `verification/dwt-vdm-012/remote-retention`.
- App-domain fake IPC plumbing chạy dưới cùng shell UID, export vào `app-plumbing`.
- Remote original catch ghi IllegalStateException / Received Surface is invalid /
  display_validation; pre-reference-reset giữ IDs `NOT_VERIFIED`, input phase NOT_ATTEMPTED.
- Java CLI reader trên artifact đã xuất: `NOT_VERIFIED`; reasons OMITTED_OR_TRUNCATED,
  MISSING_TIMELINE, MISSING_RESULT_TAIL. Không suy cause/clean từ incomplete timeline.
- Report: `verification/dwt-vdm-012/android-offline-reader.json`.
- Hash artifact và scoped verification manifest: `verification/dwt-vdm-012/manifest.json`.

Host execution policy chặn PowerShell wrapper; không thay policy. Java CLI đã chạy
thành công, dùng classpath do init task read-only cung cấp. Classpath selector được
kiểm tra với compileDebugUnitTestKotlin của toolchain hiện tại.

## Acceptance

| # | Kết quả trong phạm vi kiểm chứng |
|---|---|
| 1 | PASS local: injected Start causes vẫn phân biệt trong evidence dù product projection giống nhau. |
| 2 | PASS local: real product controller/runner nối product generation/operation → graph → cell/SID → remote epoch/PID/UID; hai cell ghép đúng. |
| 3 | PASS representation/source audit và non-owning pre-reset: attempted, returned handle, ID observed và NOT_VERIFIED phân biệt; actual allocated-ID path trên device chưa kiểm chứng. |
| 4 | PASS local: original/secondary rollback failures giữ type/message/stage và call order; hooks giữ existing cleanup semantics. |
| 5 | PASS local: receipts/partialReceipt/outcomes copied trước projection; outcome dùng SID của cell tương ứng. |
| 6 | PASS local: disabled/full/throwing sink giữ result, gate, cleanup count và late-completion behavior. |
| 7 | PASS local: queue/record/storage quotas, append preservation, rotation qua writers, overflow/eviction/partial-tail indicators; shell storage/export plumbing PASS. |
| 8 | PASS schema/privacy/source audit: immutable scalar copies; message/code allowlists; unknown fields và unsafe top-level data không export; không giữ runtime handles/secrets/dumpsys/user payload. |
| 9 | PASS local/source audit: ProductExecutionValue/gate contract sanitized không sửa; mọi field projected value và gate state giữ nguyên qua sink modes. |
| 10 | PASS scoped audit/regression: không protocol/authority/recovery/renderer semantic change. |

PASS ở đây là local characterization/source audit và controlled plumbing được nêu;
không phải chứng minh inventory hệ thống hoặc authoritative cleanup.

## Review và giới hạn

Một fresh read-only reviewer phát hiện ba Important, không Critical. Verdict ban đầu
NOT READY. Cả ba được sửa với 5 behavioral assertion RED → GREEN:
missing result/product tail; unsafe root schema/identifiers; result/outcome hai cell.
Không có vòng reviewer thứ hai; full gate sau sửa PASS.

Minor deferred: khi record mang dropped_before=N tiếp tục mất do file write failure,
writer hiện chỉ cộng một write loss; counter có thể đếm thiếu. Sequence gap vẫn buộc
NOT_VERIFIED; không dùng counter này làm tổng mất chính xác hoặc authority.

Declined-to-judge của reviewer được xử lý: actual logs/XML và controlled filesystem/export
do implementer kiểm chứng; historical ownership ngoài scope; các proof dưới đây không suy đoán.

NOT_VERIFIED: app-private storage/export trên thiết bị; actual Shizuku-hosted service
end-to-end; real allocated-ID/rollback path; timing/concurrency stress; process-death tail
durability. Không cần một Start failure thật mới để hoàn tất milestone observability.

SID/evidence 011 không được query, explain, clean, retry hoặc recover. Không crossing STOP
boundary để giải quyết giới hạn diagnostics. Gate không đọc evidence hoặc dùng nó để unlock.

Khuyến nghị: READY FOR COMMIT; chờ authorization Git riêng. Không release/publication.
