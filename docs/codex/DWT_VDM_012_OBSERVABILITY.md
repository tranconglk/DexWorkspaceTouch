# DWT-VDM-012 — Embedded Start observability

Hai miền evidence độc lập: app tại `filesDir/dwt-vdm-012-evidence`, remote tại
`/data/local/tmp/dwt-vdm-012-evidence`. Remote không chuyển evidence qua Binder.
Application gate và ProductExecutionValue giữ contract cũ, không đọc file.

## Giới hạn và privacy

- Queue 64 record; mỗi JSON record tối đa 4096 byte (newline thêm một byte).
- Mỗi namespace tối đa 4 segment, mỗi segment 128 KiB; quota áp dụng qua writer/process restart.
- Append-only trong segment. Rotation chỉ loại segment diagnostics 012, có `evicted_segments`.
- `process_epoch`, sequence, UTC milliseconds, monotonic nanoseconds, PID/UID và provenance trên mỗi record.
- Overflow có sequence gap/`dropped_before`; field bị bỏ/cắt có counters; unsafe message có `message_reason`.
- Message dùng allowlist kỹ thuật; message lạ, output command/dumpsys, mã lỗi lạ bị bỏ.
- Sink chỉ giữ scalar copies; không giữ Session/Surface/Binder/Activity/controller/gate/result graph/callback runtime.
- Sink lỗi không retry IPC/cleanup, không thay result, không cung cấp acknowledgement authoritative.

## Correlation và ý nghĩa

`host.link` nối host identity số với graph chẩn đoán. `product.operation` nối graph với
product token identity số/generation/start operation. `session.link` nối graph với
cell/SID. Remote nhận SID bằng protocol hiện có và tự ghi process epoch/PID/UID.
Offline join bằng SID; identity hash và graph ID chỉ làm correlation, không làm authority.
Product/service generation và operation có namespace field riêng.

Allocation record phân biệt attempted, handle returned và ID đã quan sát; `NOT_VERIFIED`
không có nghĩa chưa allocation. `live_resources_flag`/input phase chỉ là state cục bộ.
Reference reset, close returned và runner cleanup outcome không thay thế inventory hệ thống.
Runner receipts, partialReceipt và outcomes được ghi thành record riêng trước product projection.
`runner.result.end` đóng bản sao result bằng số record con đã emit; đây chỉ là marker diagnostics.
Reader ghép result theo graph/process epoch, ghép outcome theo cell/SID, rồi yêu cầu
projection/acceptance quan sát được. Marker không chứng minh durability hoặc authoritative clean.

## Reader offline

Build local unit-test classes rồi chạy:

```powershell
./scripts/Read-EmbeddedEvidence.ps1 -Artifacts @('app-segment.jsonl', 'remote-segment.jsonl')
```

Reader giới hạn 8 artifact/1 MiB, 4096 lines và 4096 byte/line. Reader không được runtime/gate gọi.
Nếu policy máy chặn script, lấy classpath bằng task chỉ đọc rồi gọi Java CLI trực tiếp;
không cần thay execution policy:

```powershell
$readerClasspath = ./gradlew.bat -I scripts/embedded-evidence-reader.init.gradle :app:embeddedEvidenceReaderClasspath --offline -q
$readerClasspath = $readerClasspath | Where-Object { $_.Contains([System.IO.Path]::PathSeparator.ToString()) -and $_ -match 'compileDebugUnitTestKotlin|kotlin-classes' } | Select-Object -Last 1
java -cp $readerClasspath com.trancong.dexworkspacetouch.diagnostics.embedded.offline.EvidenceReader app-segment.jsonl remote-segment.jsonl
```

Missing/partial sequence, dropped/evicted/unsafe/truncated fields hoặc thiếu linkage/timeline
cho `NOT_VERIFIED`. `OBSERVED` chỉ mô tả coverage chẩn đoán; CLI luôn xuất authority `NOT_VERIFIED`.
Không có lệnh cleanup/unlock/recovery trong reader.
Reader kiểm tra toàn bộ top-level schema/identifiers và field allowlist trước export;
payload lạ cho `NOT_VERIFIED`, không được đưa vào raw cause output.

Giới hạn đã biết: nếu một record mang `dropped_before=N` lại thất bại khi ghi, writer
hiện chỉ cộng một write loss; số mất có thể đếm thiếu. Sequence gap vẫn cho `NOT_VERIFIED`.
Không dùng counter này như tổng số mất chính xác.

## Controlled Android proof

`EmbeddedEvidenceRetentionProbe` là debug-only entrypoint chạy bằng shell `app_process`.
Không install APK lên app hiện có, không bind UserService/session/registry. Probe dùng Surface
rỗng để guard VDM từ chối trước allocation; cleanup chỉ trên instance không có tài nguyên.
Một fake service local trả Android Bundle để kiểm tra capture 1433 ký tự trước projection 512,
unsafe payload bị bỏ. Không tạo Start thật hoặc unresolved ownership run.

Remote artifact writable/exportable được kiểm chứng bằng probe shell UID 2000. Artifact app
plumbing của probe cũng chạy dưới shell UID 2000, không phải proof app-private storage hoặc
Shizuku-hosted service end-to-end. Các trường hợp đó, real allocation/rollback và process-death
tail durability chưa được kiểm chứng trên thiết bị; không suy ra từ controlled proof.

011 SID/evidence lịch sử không được query, recover, clean, explain hoặc retry trong 012.
Không đổi AIDL/Bundle contract, mapper/gate, FAILED, late cleanup, reconcile, timeout/algorithm,
launch, renderer, Room, license/security, signing/version hoặc publication.
