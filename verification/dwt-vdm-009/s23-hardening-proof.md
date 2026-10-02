# DWT-VDM-009 — Task 9: S23 / DeX hardening proof

**Verdict: PASS.** Chỉ thực hiện Task 9 theo authorization hiện hành. Dừng để final DWT-VDM-009 review.

## Thời gian và baseline

- Ngày: **2026-10-02 (Asia/Saigon, UTC+07:00)**. Chuẩn bị identity từ 12:35; device/UI proof 12:40–12:58.
- Branch: `release/1.0-beta`; HEAD: `2c77e6af40a879604897e328f5f6a46441e9e69e`.
- APK được build từ working tree chứa các Tasks trước đã accepted, không chỉ từ committed HEAD. Giữ nguyên các thay đổi có trước; không stage/commit/push.
- Đã đọc scope amendment, plan, local gate và báo cáo Tasks 3G/3B/4/5/6/7. Task 8 PASS được giữ làm prerequisite; không chạy lại unit/lint/full gate.
- Không sửa production/test code. Scope audit **PASS: 826/826** tệp tracked/untracked không ignored có trước giữ nguyên SHA-256; branch/HEAD/index giữ nguyên. Tệp mới chỉ là report/evidence Task 9 và capture trong thư mục smoke riêng Task 9. [scope-audit.json](task9-evidence/scope-audit.json).

## Artifact và cài đặt

| Thuộc tính | Giá trị / kết quả |
| --- | --- |
| Workflow | `scripts/build-device-smoke.ps1`: **PASS**; `assembleRelease` BUILD SUCCESSFUL, exit 0 |
| Configuration | Production configuration, production signing; các kiểm tra BuildConfig/packaged DEX của workflow **PASS** |
| Smoke APK | `D:\AndroidStudioProjects\DexWorkspaceTouch\smoke-output\DWT-device-smoke.apk` |
| Bản artifact riêng Task 9 | `smoke-output/task9-20261002/DWT-task9-production-smoke.apk` |
| Artifact SHA-256 | `246b7df53d14935fbd92925ba7521f7f6216c4ca3b46d16175bbfa25fd970af6` |
| Installed readback | `smoke-output/task9-20261002/installed-base.apk`, đọc lại qua `pm path` và `adb pull` |
| Installed APK SHA-256 | `246b7df53d14935fbd92925ba7521f7f6216c4ca3b46d16175bbfa25fd970af6` — **PASS**, khớp artifact |
| Expected/artifact/installed signer SHA-256 | `19ac0ea99125361b3c2083aaa44c8745ebad9c55fa967642c2b9e5a1086a45e7` — **PASS**, signature verification và fingerprint đều khớp |
| Package | `com.trancong.dexworkspacetouch` |
| Version | `1.0.0-beta.8`; versionCode `9`, giữ nguyên trước/sau install |
| Same-signer safety | APK đang cài được đọc lại và xác minh cùng signer **trước** install; `adb install -r`: **PASS**, Success |
| Dữ liệu | Thư viện workspace hiện có còn hiển thị sau upgrade; không uninstall/clear data/reset license |
| Smoke cũ | Lưu tại `smoke-output/task9-20261002/pre-task9-smoke.apk` trước khi workflow tạo smoke mới |

Build sandbox ban đầu không resolve được Android Gradle plugin 9.2.1. Chạy lại đúng workflow với quyền cache/SDK hiện có thành công; không đổi dependency, signing hoặc policy PowerShell toàn máy. Logs build nằm trong thư mục smoke Task 9.

## Môi trường và kịch bản

- Thiết bị: Samsung S23 Ultra, model `SM-S918B`; Android `16`, API `36`; One UI property `ro.build.version.oneui=80500`.
- Samsung DeX: **PASS**, quan sát desktop/UI thật trên màn hình ngoài 1920×1200; logical display `124`.
- Shizuku ban đầu: **đang chạy**, version `13.5`, adb mode; DWT đã có quyền. Hai client proof khác được cấp quyền không có process chạy tại checkpoint trước Stop.
- Không thu hồi quyền DWT. Dừng Shizuku qua menu **Dừng Shizuku → OK** khi chưa có Embedded run; khôi phục bằng **Bắt đầu** của luồng wireless debugging hiện có. Không ghép nối mới hoặc thay đổi quyền của app khác.
- Dùng đúng **một workspace/layout có sẵn**, Library `DWT-VDM-007 Proof` / tên product `DWT-VDM-007 Proof 5-8 3-8`, gồm Waze và Calculator. Một lượt Classic để kiểm tra independence; **một** explicit Embedded Start và **một** Back cleanup của run đó.
- DWT PID `23705` giữ nguyên tại các checkpoint sau launch, Shizuku Stop/restore, Start, cleanup và kết thúc. Không restart/force-stop DWT.

## Quan sát trực tiếp

PASS UI dưới đây dựa trên màn hình DeX thật và clipboard UI thật. Nội dung chẩn đoán được đọc từ đúng mục clipboard Samsung bằng accessibility UI text sau user tap; không lấy từ unit-test output hoặc logcat.

| Quan sát yêu cầu | Trạng thái | Bằng chứng |
| --- | --- | --- |
| APK/signature/installed identity | PASS | Hash readback và signer khớp bảng trên |
| Cài đè an toàn cùng signer | PASS | Pre-install signer đúng; install -r Success; metadata/readback đúng |
| A — typed unavailable readiness | PASS | [UI unavailable](task9-evidence/unavailable.png): “Shizuku chưa chạy hoặc chưa kết nối.” |
| A — mở UI không tự Start | PASS | Chỉ readiness/actions, không vùng app Embedded; Start disabled khi unavailable |
| A — không tự xin quyền | PASS | Không xuất hiện permission dialog khi mở product/developer UI; không bấm Cấp quyền |
| A — Classic độc lập khi IDLE | PASS | Bấm Mở Classic lúc Shizuku dừng: [hai cửa sổ thật](task9-evidence/classic.png), Calculator render; DWT báo đã gửi yêu cầu mở 2 ứng dụng. Đóng hai cửa sổ qua nút X bình thường |
| B — unavailable → Ready không restart | PASS | Trở lại DWT sau normal Shizuku Start: [UI Ready](task9-evidence/ready.png), Start enabled; cùng PID. Đây là refresh khi foreground/ON_RESUME; không tách riêng callback khỏi ON_RESUME |
| B — refresh không tự Start | PASS | UI Ready vẫn chỉ có readiness/actions trước explicit tap; không có renderer/app region |
| C — explicit Start / STARTING | PASS | Một tap Bắt đầu Embedded; [UI khởi động](task9-evidence/starting.png) ghi “Đang khởi động Embedded.”, runner PREFLIGHT, Start/refresh disabled |
| C — ACTIVE | PASS | Quan sát trực tiếp 12:52: product ghi ACTIVE và “Embedded đang chạy (Thử nghiệm).”, cả Waze/Calculator trong vùng Embedded. Receipt clipboard sau cleanup còn hai item phase ACTIVE; không lưu ảnh có nội dung bản đồ cá nhân vào evidence cuối |
| D — normal Back/host cleanup | PASS | Một tap Quay lại trên host ACTIVE; vùng Embedded biến mất và UI về Home. Chẩn đoán terminal có STOPPED, CLEAN_CONFIRMED cho cả hai owned item |
| D — một logical cleanup operation | PASS | generation=1, start_operation_id=1, cleanup_operation_id=2; một cleanup operation cho lượt Start này, hai outcome theo thứ tự đảo cell_b → cell_a |
| D — gate IDLE / authoritative clean | PASS | Chẩn đoán clipboard: phase=IDLE, result_kind=STOPPED, cleanup_evidence=CLEAN_CONFIRMED |
| D — không có owned orphan được báo | PASS | Hai outcome CLEAN_CONFIRMED, không blocked/uncertain/orphan warning trong kịch bản bình thường. Không mở rộng kết luận này sang resource lifetime ngoài evidence của run |
| E — clipboard chỉ thay đổi sau tap | PASS | Đặt/copy/paste chuỗi mẫu qua UI. [Trước tap](task9-evidence/clipboard-before.png), clipboard vẫn là DWT-TASK9-CLIPBOARD-SENTINEL sau refresh/Start/cleanup; sau tap Sao chép chẩn đoán có toast Đã sao chép và [paste UI](task9-evidence/clipboard-after.png) có header v1 |
| E — inspect header/security/provenance | PASS | Đọc đủ 38 dòng của đúng clipboard entry: header DWT Embedded diagnostics v1; không thấy secret/license/Binder/Surface/stack trace; unknown/provenance nhất quán với Task 6 |
| F — Home không hiện trực tiếp năm proof control | PASS | [Home](task9-evidence/home.png) chỉ có entry Nhà phát triển (Thử nghiệm) |
| F — developer sheet mở/đóng, không auto Start/permission | PASS | [Sheet](task9-evidence/developer-sheet.png) có năm proof control; mở/đóng không vào proof route, không renderer hoặc permission dialog; không chạy năm route |
| Activity recreation device proof | NOT VERIFIED | Không ép recreation; không có procedure an toàn, deterministic được xác lập cho lượt này. Giữ Task 4 local evidence |
| CLEANUP_BLOCKED device failure injection | NOT VERIFIED | Không cố gây hung Start/failed cleanup. Giữ deterministic evidence Tasks 3G/3B/4/5 |

## Clipboard terminal và owned evidence

[Copied diagnostics](task9-evidence/copied-diagnostics.txt) là text của đúng clipboard entry được đọc sau explicit tap, snapshot UTC **2026-10-02T05:54:16.967Z** (12:54:16.967 Asia/Saigon).

- `phase=IDLE`, `result_kind=STOPPED`, `cleanup_evidence=CLEAN_CONFIRMED`.
- `generation=1`, `start_operation_id=1`, `cleanup_operation_id=2`, `invocation=RESULT_OR_UNCERTAIN`.
- Hai receipt item có provenance `OWNED_ITEM_RESULT`; owned session/display của cell_a và cell_b được ghi trong file chẩn đoán. Hai cleanup outcome có provenance `CLEANUP_RESULT`, đều `CLEAN_CONFIRMED`.
- `allocation_evidence=POSSIBLE_OR_OWNED` là receipt/evidence của lượt đã allocate; đọc cùng terminal cleanup evidence. Item phase ACTIVE là receipt value, không phải tuyên bố item còn chạy sau cleanup.
- `workspace_id=unknown` sau gate release; `run_identity/vdm_id/task_id/remote_resource_id/readiness/shizuku` giữ unknown theo schema Task 6. Không suy ID hay readiness từ nguồn khác để thay text đã copy.
- Đã đọc toàn bộ text và kiểm tra các pattern secret/Authorization/Bearer/private key/license/activation/Binder/Surface/stack trace: không có match. Không lưu raw clipboard history hoặc raw accessibility dump.

## Kết thúc và giới hạn

Không có blocker hoặc device FAIL trong kịch bản đã chạy. Thiết bị kết thúc tại Home, search đã xóa và keyboard/sheet đã đóng; Shizuku đã được khôi phục. Không có Embedded run mới sau cleanup.

Không sửa production/tests, kiến trúc, runtime/protocol, Room/schema, license/security, trusted keys/production URLs, signing hoặc version. Không uninstall/clear app data; không process-death probe, global scan/cleanup, UserService kill, failure injection, matrix/stress/soak. Không publish release/reset/stash/git clean/stage/commit/push; không mở milestone tiếp theo.

**Task 9 PASS. STOP FOR FINAL DWT-VDM-009 REVIEW.**
