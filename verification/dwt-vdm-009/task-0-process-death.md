# DWT-VDM-009 — Task 0 process-death ownership characterization

**Status:** `BRANCH_B_STOP` — đã đạt ACTIVE, crash main PID và quan sát read-only; cần review kiến trúc ownership riêng trước Tasks 1–9.

## Phạm vi và artifact

- Thiết bị: Samsung SM-S918B, Android API 36, DeX display 87 tại preflight.
- Source baseline: `release/1.0-beta`, `2c77e6af40a879604897e328f5f6a46441e9e69e`.
- APK production-signed của proof 008: `.superpowers/sdd/2026-09-30-dwt-vdm-008-workspace-run-mode-integration/DWT-VDM-008-device-smoke.apk`.
- SHA-256 APK: `4e3bc163cc4adf88308d11ab0393ed39e56f8b55b83712ba46334fcacab37a81`.
- SHA-256 `base.apk` trên S23: `4e3bc163cc4adf88308d11ab0393ed39e56f8b55b83712ba46334fcacab37a81` (đọc bằng `adb shell sha256sum`); byte-identical với APK 008.
- Signer SHA-256: `19ac0ea99125361b3c2083aaa44c8745ebad9c55fa967642c2b9e5a1086a45e7` (`apksigner verify --verbose --print-certs`, v2 verified).
- Source equivalence: ledger 008 ghi full local gate PASS rồi build production-signed từ cùng worktree chứa tích hợp 008; proof tiếp theo trên chính APK đó ghi không rebuild, reinstall hay sửa mã. Commit baseline `2c77e6a` chứa các file tích hợp 008. Đây là **suy luận từ ledger/build provenance**, không phải phép so sánh binary với source. BuildConfig generated trong workspace còn `BUILD_COMMIT=5fc5492` từ trước nên **không dùng** trường đó làm bằng chứng source equivalence. Bằng chứng build/proof là `.superpowers/sdd/2026-09-30-dwt-vdm-008-workspace-run-mode-integration/progress.md` (Task 6). Nếu phát hiện thay đổi source sau build và trước commit, phải dừng và rút lại equivalence.

## Phương pháp được chọn tại preflight

- `adb shell am help` trên S23 ghi `crash [--user <USER_ID>] <PACKAGE|PID>`: chấp nhận PID cụ thể; `kill <PACKAGE>` được mô tả là kill các background process của package, nên không dùng.
- AOSP `ActivityManagerShellCommand.runCrash` parse số thành `pid`, đặt `packageName=null` và gọi `crashApplication` cho PID đó: <https://android.googlesource.com/platform/frameworks/base/+/1e2758256a391be23c537725f0a7785e4fb5b7d0/services/core/java/com/android/server/am/ActivityManagerShellCommand.java>.
- Chỉ khi main PID và `:embedded_app` PID khác nhau, run ACTIVE và các ID tài nguyên được ghi nhận mới gọi `adb shell am crash <main PID>`. Lệnh không dùng package name, force-stop, clear data, uninstall hay Shizuku shutdown. Xác nhận sau lệnh main PID cũ mất; theo dõi remote PID riêng. Remote có thể chết sau đó do non-daemon client binder death, đây là hành vi cần đo, không phải lệnh quét package.

## Preflight trước khi Shizuku chạy

- DWT main PID khi mở route: `2437` (`adb shell pidof com.trancong.dexworkspacetouch`). PID này chỉ là snapshot trước Start, phải đo lại ngay trước lệnh crash nếu phép thử tiếp tục.
- `:embedded_app`/UserService PID: chưa có, vì chưa Start. Shizuku app process `moe.shizuku.privileged.api` có PID `29982`, nhưng **đó không chứng minh Shizuku server đang chạy**.
- Product route trên DeX display 87, Workspace `DWT-VDM-007 Proof 5-8 3-8`: `IDLE`; màn hiển thị “Shizuku chưa chạy hoặc chưa kết nối; Classic vẫn dùng được.” Sau thao tác `Kiểm tra lại` vẫn cùng trạng thái. Ảnh: `dex-ready.png`, `dex-refresh.png`.
- Trước Start: `dumpsys virtualdevice` báo `Number of active virtual devices: 0`; `cmd companiondevice list 0` không có association `com.android.shell` (chỉ các entry 1–3 của ứng dụng khác).
- Session IDs, owned VirtualDevice/display/task IDs, association identity/MAC, run token: **chưa được tạo/không quan sát được**. Không gán các ID từ proof 008 cho lần chạy mới.

## Sau khi Shizuku được khởi động — 2026-10-01 +07

- Nhấn `Kiểm tra lại`; màn Embedded cho phép Start (`dex-after-shizuku.png`). Sau khi Start, `dex-active.png` hiển thị ACTIVE với Waze + Calculator.
- Trước crash: main PID `2437` UID `10612`; UserService `:embedded_app` PID `22162` UID shell `2000`; Shizuku server PID `20426`. Hai tiến trình DWT tách biệt.
- `dumpsys virtualdevice`: device `58` → association `67` → display `88` → Waze task `4728`; device `59` → association `68` → display `89` → Calculator task `4729`. VirtualDevice Log START 10:21:52.905 và 10:21:53.367. `cmd companiondevice list 0`: association `67` MAC `02:57:12:66:61:e5`, association `68` MAC `02:57:12:66:63:bd`. Trước Start không có association shell và active VDM = 0. Ownership xác định qua delta + association ID, không theo package đơn lẻ.
- Session IDs và RunToken: `UNKNOWN`; bản production không hiển thị qua UI/dumpsys đã đọc. Không suy luận từ tên VirtualInputDevice dạng hash.

### Crash main PID và đọc tài nguyên cũ trước restart

Dùng duy nhất `adb shell am crash 2437`, sau khi `adb shell am help` và [AOSP runCrash](https://android.googlesource.com/platform/frameworks/base/+/1e2758256a391be23c537725f0a7785e4fb5b7d0/services/core/java/com/android/server/am/ActivityManagerShellCommand.java) xác nhận đối số số được dùng làm PID. Lệnh in `Shell does not have permission to crash packages for user 150` nhưng shell exit code `0`; log thiết bị xác nhận main PID **thực sự** crash lúc 10:22:46.659 với `CrashedByAdbException: shell-induced crash`, và ActivityManager ghi PID 2437 died lúc 10:22:46.734. Cảnh báo quyền user 150 là giới hạn phép thử, không diễn giải exit code là proof riêng lẻ.

| Tài nguyên đã ghi | Snapshot read-only đầu tiên sau crash | Log / lần đọc read-only tiếp |
| --- | --- | --- |
| Main PID `2437` | Không còn trong `ps` | Crash lúc 10:22:46.659–.734 |
| UserService PID `22162` | **Còn trong `ps`** | `System.exit(0)` lúc 10:22:46.938; lần đọc sau không còn |
| VirtualDevice `58/59` | Active = `0` | Close lúc 10:22:46.814/.907 |
| Guest display `88/89` | `dumpsys activity` còn liệt kê trong transition | `dumpsys display` sau không còn |
| Guest task `4728/4729` | **Còn liệt kê**, `visible=false` | Remote `removeTask success` lúc 10:22:46.810/.906; lần đọc sau không còn |
| Association `67/68` | Không còn trong `cmd companiondevice list 0` | System removal lúc 10:22:46.850/.925 |
| Session IDs / RunToken | `UNKNOWN` | Không đối chiếu ID riêng được |

Hệ thống tự Start main PID `23121` cho `ShizukuProvider` lúc 10:22:46.817 rồi kill khi remove task lúc 10:22:47.346; không có restart do người thử. Timestamp riêng từng dòng snapshot CLI không ghi được vì cú pháp `adb shell date '+...'` bị lỗi; mốc trên lấy từ log thiết bị. Không Start run mới.

### Quyết định Branch B — STOP

Snapshot đầu sau main death vẫn thấy UserService và guest task cũ; session IDs là `UNKNOWN`. Log cho thấy cleanup hoàn tất rất nhanh ở **lượt ACTIVE cụ thể này**, nên **không kết luận có rò rỉ kéo dài**. Nhưng tiêu chí Branch A yêu cầu chứng minh đầy đủ ownership/absence trước restart và an toàn ở cửa sổ remote allocation; bằng chứng hiện tại chưa đạt. Cảnh báo quyền user 150 cần được giải thích trước khi dùng phương pháp này làm proof lặp lại.

`EmbeddedProductRunGate.kt` giữ RunToken trong main process; `EmbeddedAppSession.kt` giữ session ID trong main; `RemoteSessionRegistry.kt` giữ registry trong remote process memory. Main có thể chết sau `PREPARED`/`STARTING` → remote allocation → trước khi IDs quay về main. Main mới không có mapping bền vững từ run sang đúng session/VDM/display/task/association để reconcile an toàn. Không quét/xóa mọi tài nguyên `com.android.shell` vì package không chứng minh ownership của run.

Cần **review kiến trúc recovery-journal/ownership riêng** trước Tasks 1–9: ghi bền ID ownership trước khi remote báo allocation success, hoặc tái khám phá chính xác bằng stable run ID. Marker best-effort ở main không giải quyết cửa sổ crash. Không thêm marker, scanner, cleanup toàn cục, sửa runtime 006, restart DWT, hoặc thực hiện Tasks 1–9 trong checkpoint này.
