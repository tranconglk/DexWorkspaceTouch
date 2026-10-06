# DWT-VDM-011-D1 — blocked run forensic checkpoint

**Outcome: D1-C.** Nguyên nhân Start cụ thể vẫn NOT VERIFIED: raw exception/stage và kết quả remote rollback không được giữ trong evidence đã capture của 011. Raw message từng có thể tồn tại tạm thời trong session/runner; kết luận này nói về evidence có thể đọc lại, không khẳng định mọi object trong heap đã biến mất.

**DWT-VDM-011 = IN PROGRESS / BLOCKED_BY_UNRESOLVED_OWNERSHIP.** Product qualification vẫn STOP. D1 chỉ đọc, không production fix hoặc device retest; 009/010 vẫn CLOSED.

| Trục kết luận | Trạng thái | Căn cứ |
|---|---|---|
| START ROOT CAUSE | **NOT VERIFIED** | Xác định được failure path `onStartCompleted` non-success → lifecycle `START_FAILED` trước ACTIVE; không phân biệt negative remote Bundle với exception của IPC, không biết raw exception/stage |
| AUTHORITATIVE RELEASE | **NOT VERIFIED** | Chỉ có exact partialReceipt FAILED và rollback INCOMPLETE/START_FAILED; không tìm thấy exact-owned CLEAN được tạo/chấp nhận trong evidence đã đọc |
| DEVICE RETEST ELIGIBILITY | **NOT ELIGIBLE** | Không authoritative clean cho run này; không có accepted late-unlock/recovery path trong gate blocked |

## Run chính xác và correlation

Workspace `DWT-VDM-011 CAL+WAZE 20261005-R01`, ID `fc3b92c6-900a-4111-89ef-910ab90d400e`, source `cell_a`, owned SID `db621ed2-c769-408e-b901-7679ee03bd4f`. Product generation **3**, product Start operation **5**, product cleanup operation **unknown**. IPC/lease operation, connection generation/registration/binder identity không được capture; **không đồng nhất product op 5 với IPC op**.

[Diagnostic nguyên văn](../dual-blocked-diagnostic.txt) là record giá trị của exact run. [Correlation JSON](exact-run-correlation.json) giữ timestamp/provenance và các giới hạn:

| Mốc | Evidence có timestamp | Ý nghĩa và giới hạn |
|---|---|---|
| 2026-10-05 16:54:35.215 UTC | [Single fresh clean](../single-fresh-clean-diagnostic.txt) | Generation 2, SID khác; không release cho blocked SID |
| 2026-10-05 23:58 +07, độ chính xác phút | [Dual Ready](../screenshots/dual-refreshed.png), đồng hồ UI | Selected dual trước explicit Start; không phải timestamp IPC |
| 2026-10-05 23:59 +07, độ chính xác phút | [Dual blocked](../screenshots/dual-after-start.png), đồng hồ UI | UI blocked sau một explicit Start; không suy exception từ độ trễ |
| 2026-10-05 17:00:11.940 UTC = 2026-10-06 00:00:11.940 +07 | Product diagnostic | Snapshot `START_FAILED/INCOMPLETE/CLEANUP_BLOCKED` exact SID/gen/op; không phải thời điểm raw failure |
| 2026-10-06 00:08 +07, độ chính xác phút | [Library cuối](../screenshots/final-blocked-library.png), đồng hồ UI | Banner unresolved; navigation không phải clean |
| 2026-10-06 02:01:28 UTC | [Read-only system observation](read-only-system-observation.json), device `date -u` | D1 inventory/log observation, không thay đổi ownership và không release authority |

Không có timestamp exact của Start IPC entry/completion, remote allocation hoặc remote rollback. Lifecycle history hiện hành là list snapshot không có timestamp, không phải persistent journal.

## A — Start failure được xác định đến đâu

Đây là suy luận từ diagnostic của real run kết hợp source unchanged tại `db69bed601eceea12259249c89f0f5276793e425`; không phải raw remote trace mới.

1. [EmbeddedStartIpcTask](../../../app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedapp/EmbeddedStartIpcTask.kt), lines 26–35: gọi `startSession`; nếu remote Bundle `success=false`, giữ `exception` tối đa 512 ký tự. Exception khi gọi/đọc IPC được thay bằng `START_IPC_FAILED`, mất type/message gốc. Completion không giữ raw reply, failureCode, resource IDs hay rollback acknowledgement.
2. [EmbeddedAppSession](../../../app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedapp/EmbeddedAppSession.kt), lines 239–258: chỉ nhận matching private Start operation/mailbox; nhánh không success tạo `EmbeddedSessionFailure("START_FAILED", value.error)`. Vì diagnostic giữ cleanup failureCode `START_FAILED`, đường này được xác định; actual raw `value.error` **không có trong captured product evidence**.
3. [Runner](../../../app/src/main/java/com/trancong/dexworkspacetouch/workspace/execution/embedded/runtime/EmbeddedWorkspaceRunner.kt), lines 215–253, 497–504: snapshot FAILED làm StartFailure/rollback; `StartFailed.failure` và `Incomplete.failure` có thể giữ raw message, còn partialReceipt chỉ giữ identity/phase/display. Chạy item tuần tự, chỉ tạo item sau khi item trước ACTIVE (lines 291–367).
4. [Recovery mapper](../../../app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/product/EmbeddedProductRecovery.kt), lines 97–110 và [ProductExecutionValue](../../../app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/product/EmbeddedProductRunGate.kt), lines 118–184: raw failure message không được copy vào Application value; mọi Start failure thường thành typed `RuntimeStartFailed`. Cleanup chỉ giữ code. [Diagnostics](../../../app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/product/EmbeddedProductDiagnostics.kt), lines 38–80, đọc một value snapshot đã sanitize, không có raw cause field.

**Loại trừ ở mức published failure code:** đây không phải preflight rejection, CREATE_FAILED, CONNECT_FAILED, local SURFACE_INVALID, START_CAPACITY_EXHAUSTED hay ACTIVE_TIMEOUT terminal của runner. Không loại trừ lỗi Surface hoặc timeout *bên trong remote Start*; các lỗi đó vẫn có thể bị bọc thành START_FAILED. Không suy input-wait timeout từ thời gian vài giây.

[RemoteSessionRuntime](../../../app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedapp/remote/RemoteSessionRuntime.kt), lines 19–47, có nhiều stage: target/geometry → association → VDM/display → input → input stability → target launch. Catch mọi Throwable, gọi `runCatching { stop() }`, rồi trả original exception string. Không giữ stage, không giữ stop exception/result trong failed reply, không trả IDs đã allocate khi failed. Thiếu raw message nên không chọn được stage của SID này; duplicate registry response hoặc transport exception cũng không được phân biệt bởi product projection.

011 dùng normal `assembleRelease` ([build script](../../../scripts/build-device-smoke.ps1), line 65), không diagnostic overlay/debug host. Observer 010 nằm trong `app/src/debug` và isolated mirror được bật riêng; không lấy các record B/C hay SID cũ của 010 làm trace của 011.

## B/C — ownership và accepted authority chain

| Layer | Source path/lines | Điều được chứng minh và dữ liệu thiếu |
|---|---|---|
| Remote Start result | RemoteSessionRuntime 43–46, 83–84; EmbeddedAppUserService 13–18 | SID gắn vào reply; negative reply không báo remote rollback CLEAN/INCOMPLETE. Actual reply/remote registry state của SID này chưa capture |
| Session lifecycle | EmbeddedAppSession 239–258; EmbeddedAppSessionState 134–189 | FAILED terminal, display mặc định -1. `requestStop/requestClose` từ FAILED không sang STOPPING; `cleanupSucceeded` chỉ chấp nhận STOPPING |
| Session close/private manager | EmbeddedAppSession 319–350; ServiceConnectionManager 47–66, 321–355 | Close worker có thể Stop exact SID và private operationFinished trả clean khi hết in-flight; **không có record giá trị đó cho SID này**. Possible success không được biến thành bằng chứng thực tế |
| Runner result / rollback | EmbeddedWorkspaceRunner 407–450, 497–549 | Republished FAILED được map Incomplete cùng failure; partialReceipt FAILED; terminal cache đóng admission, detach notifications, clear owned handles. Remote clean đến sau không tự sửa frozen result |
| ProductExecutionValue | EmbeddedProductRunGate 129–183 | StartFailed chỉ authoritative khi `allOwnedSessionsClean` và exact owned cells/outcomes đầy đủ, duy nhất, tất cả Clean. Captured outcome INCOMPLETE không đạt điều kiện |
| Gate acceptance | EmbeddedProductRunGate 297–332; ProductController 205–222 | Gate fence token identity/generation/op; result được copy rồi runtime graph detach. Khi CLEANUP_BLOCKED, gate từ chối kết quả đến muộn; không có cleanup operation mới/late-unlock |

**Exact-owned CLEAN đã tạo và được accepted: không tìm thấy trong retained evidence đã kiểm tra.** Không có remote rollback outcome/Stop acknowledgement, lifecycle STOPPED, runner Clean hoặc accepted ProductExecutionValue authoritativeClean cho SID này. Các clean SID Calculator trước đó không thuộc owned set/generation này.

Điều này **không chứng minh remote rollback thất bại hoặc tài nguyên còn sống**. Remote rollback có thể đã thành công; private manager có thể từng xử lý stop result, nhưng không có record/operation provenance để chứng minh. `FAILED` không đồng nghĩa remote allocation không xảy ra; receipt display `unknown` không đồng nghĩa chưa có display.

Ownership/resource state có bằng chứng: local partialReceipt SID trên, `cell_a`, order 0, FAILED; allocation `POSSIBLE_OR_OWNED`; cleanup `INCOMPLETE`, code `START_FAILED`; chỉ một item và outcome, omitted=0. Waze không có owned receipt/ACTIVE evidence; với sequential runner và first-item failure không có bằng chứng Waze Start. Remote reservation/association/VDM/display/input/task IDs, phase hiện tại và resource release **NOT VERIFIED**.

Tên input **suy dẫn**, không phải descriptor đã capture: `DWT-VT-374c84acc55595406a1e22d8`, từ unchanged `virtualInputDeviceName(SID)`. Dump input D1 exit 0, exact-name match 0. Đây chỉ là observation hiện tại; absence/inventory/handle close/process/PID/navigation/timeout/UNKNOWN_SESSION không phải release authority. Không gọi getSessionState qua lease/harness mới, không gọi reconcile để tạo bằng chứng mới.

## Log inventory và information retention

[Main/system collection](log-collection.json): dump theo mốc 23:42 và các tag `TaskViewLab.VDMSurface`, `DWT.EmbeddedAppLaunch`, `AndroidRuntime` exit 0, zero lines. Kiểm all retained data của cùng tags cũng zero lines ([all-retained dump](device-logcat-all-retained.txt)); [buffer metadata](log-buffer-info.txt) ghi main/system hữu hạn. Last retained log timestamp quan sát là 09:01:28 ngày 06/10; chỉ timestamp được lưu, không thông điệp unrelated.

[Native/crash collection](native-log-collection.json): crash buffer, DEBUG/libc/tombstoned/AndroidRuntime tags; cửa sổ 23:58–00:01 +07 chứa zero retained lines. Không clear log. Empty dump không chứng minh ban đầu không có exception hoặc crash; không suy root cause/release từ log retention.

Tìm exact SID trong toàn bộ text/json/xml/log/md của captured 011 verification và ignored raw scratch chỉ thấy diagnostic/derived checkpoint/summary/system observation. Không có raw Start reply, raw lifecycle message, receipt/result journal hoặc remote rollback ack đã capture. Bản [source authority audit](source-authority-audit.json) lưu 18 source/test/script hashes và line references. Không dump heap, attach debugger, cài APK/observer hoặc tạo ownership để tìm dữ liệu tạm còn trong process.

## Diagnostic characterization, defect assessment và fix boundary

[Accepted characterization](accepted-characterization.json) đối chiếu SHA-256 XML `A-start-failure-current.xml` với accepted 010 verification-review: **1/1 PASS reused**, zero failures/errors/skips; không execution mới. [Existing test](../../../app/src/test/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/product/EmbeddedStartFailureEvidenceQualificationTest.kt), lines 19–108, inject hai causes khác nhau; runner giữ hai raw failures khác nhau nhưng product status giống nhau, rollback Incomplete và gate blocked. Đây là **A local/fake**, không exception/clean proof của real SID này. Không cần test mới trùng chức năng hoặc rerun full suite.

Không xác định được một production Start defect cụ thể để sửa. Hai limitation có bằng chứng:

| Limitation | Behavioral evidence / RED | Smallest plausible follow-up, chưa triển khai | Accepted semantics |
|---|---|---|---|
| Raw cause/rollback outcome không có durable forensic copy trước value mapping | Existing characterization PASS chứng minh many-to-one. RED cho yêu cầu phân biệt cause có thể là assertion runtime `assertNotEquals` trên hai outputs đang bằng nhau; **RED mới chưa chạy**, không lấy compilation failure hay fake cause làm RED cho Start thật | Task diagnostic riêng có thể dùng existing scoped observer/value-copy sink, giữ raw cause/stage và rollback outcome kèm SID/cell/token/gen/op/timestamp trước projection trên testbed đủ điều kiện. Không đưa arbitrary raw message vào Application gate; không giúp phục hồi lịch sử đã không capture | Chỉ observation nếu giữ nguyên authority/runtime/protocol; cần scope mới, D1 không implement |
| FAILED terminal/runner freeze/gate blocked không có đường nhận cleanup acknowledgement về sau | Source guards và existing test chứng minh Incomplete terminal; **không có real late CLEAN để replay**. RED với fake exact ack chỉ kiểm contract, không chứng minh current SID đã release | Muốn mở lại run cũ cần thiết kế owner giữ/tiếp nhận exact cleanup acknowledgement trước terminal detach, hoặc một đường recovery authority được phê duyệt riêng; không xóa gate/đổi result bằng timeout hoặc absence | Đổi lifecycle/result/ownership/cleanup contract: **Charter STOP**, không production patch |

[Technical Autonomy Charter](../../../docs/codex/DWT_TECHNICAL_AUTONOMY_CHARTER.md), lines 64–95: đặc biệt boundaries **2 ownership/result authority**, **3 remote protocol/AIDL** nếu thêm authoritative fields, **6 cleanup/reconcile**, **15 renderer/runtime semantic contract**. Journal/recovery bổ sung còn chạm **4/5**. Không tự chọn kiến trúc hoặc chỉ bỏ FAILED khỏi terminal set để mở khóa.

## Scope verification và checkpoint disposition

Chỉ thêm forensic report/JSON/log dưới `verification/dwt-vdm-011/d1-forensics/`. [Scope audit](scope-preservation.json) kiểm source/build/test không đổi, no staged/commit, original HEAD/index/status và 150 pre-existing file hashes giữ nguyên; evidence 011 trước D1 giữ đúng manifest hash. Không sửa checkpoint lịch sử hay evidence 009/010.

D1 chỉ dùng logcat dump/buffer metadata, dumpsys input và date -u. Không Start/retry/cleanup retry/reconcile/global cleanup, kill/restart/reboot, clear data/uninstall/reinstall, Shizuku/UserService restart, license mutation hoặc workspace deletion. Không build/install/test tạo remote session. Không có behavioral RED/GREEN mới hoặc production fix.

**Next:** giữ 011 STOP. Nếu raw reply/snapshot hoặc exact cleanup acknowledgement gốc còn ở một capture chưa cung cấp, chỉ đọc và correlate đúng SID/owner/fence. Evidence hiện có chưa đủ; không fabricate/recreate clean. Nếu không có capture bổ sung, cần scope/design review cho retention/testbed/authority trước mọi retest. Một future independent run không xác định nguyên nhân hoặc release của blocked SID lịch sử.
