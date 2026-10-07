# DWT-SHIZUKU-001 — S23 real-product proof

**PASS — PRODUCT INTEGRATION VIABLE / READY FOR COMMIT REVIEW.** Ngày 2026-10-07, Asia/Saigon. S22 real-product PASS được giữ nguyên; S23 real-product proof đã PASS. Không cần sửa implementation.

## Update compatibility và bảo toàn dữ liệu

- Package đang cài và qualification APK: `com.trancong.dexworkspacetouch`, `1.0.0-beta.8` / versionCode `9`.
- Cả hai APK được `apksigner verify` xác minh cùng certificate SHA-256 `19ac0ea99125361b3c2083aaa44c8745ebad9c55fa967642c2b9e5a1086a45e7`.
- APK cũ SHA-256: `6370cd122f8c5750e230e56c15af5f5ea7a0d252fa019371648c54ab1715852b`.
- Qualification APK giữ nguyên SHA-256: `708e4df09c1aea4e232e5655b981ef457b96d3b5d85fb36397fee9e1ccd14b68`.
- Chỉ dùng `adb install -r` sau precheck; update tại chỗ thành công. Không uninstall, clear data, downgrade hoặc đổi signer/version/config.
- Sau update, APK đã cài khớp chính xác bytes của artifact. App ID `10612`, data directory và `firstInstallTime=2026-09-10 10:58:54` giữ nguyên.
- MainActivity khởi động bình thường vào Workspace Library trước/sau update; license/session hiện hữu vẫn vượt gate, không nhập lại key, không bypass hoặc đọc token/plaintext license.
- **13 workspace cũ bảo toàn**: xuất Library qua UI trước/sau update và so sánh toàn bộ tên/schema/cells/bounds/app assignments, chỉ bỏ timestamp export. Nội dung giống nhau. Workspace ghim và 8 lối tắt cũ vẫn có trong UI; không đổi các slot.
- Thêm duy nhất fixture qualification Calculator/Clock bằng **Tệp → Nhập workspace**; không ghi trực tiếp Room/SharedPreferences. Không thao tác alarm hoặc dữ liệu Calculator/Clock.

Bằng chứng: [precheck](precheck.json), [update result](update-result.json), [preservation](preservation-proof.json), [UI trước](before-update-library.png), [UI sau](after-update-library.png), `library-before.dwtbundle` / `library-after.dwtbundle`.

## Môi trường chính xác

| Thuộc tính | Giá trị |
|---|---|
| Thiết bị | Samsung Galaxy S23 Ultra, SM-S918B |
| ADB | `192.168.1.106:5555` |
| Android / SDK | Android 16 / 36 |
| One UI | **8.5**, property `80500` |
| Build fingerprint | `samsung/dm3qxxx/dm3q:16/BP4A.251205.006/S918BXXSAFZH3:user/release-keys` |
| Samsung DeX | display logic `6`, physical `4632669056402301701` |
| Resolution / density | `1920 × 1200`, 160 dpi |
| Navigation bar | `[0,1144,1920,1200]`, bottom inset 56 px |
| Work area / cell inset | `[0,0,1920,1144]`, inset 8 px mỗi cạnh ô |
| Shizuku | 13.5, adb server shell UID 2000; quyền DWT đã có và giữ nguyên |

One UI thực tế là 8.5, được ghi đúng theo thiết bị; fixture vẫn là Calculator/Clock 50–50 đã được chấp nhận.

## Product UI và requested vs actual

Chọn radio **AUTOMATIC** trong Car Mode. Mở fixture bằng nút **Mở** thật trong Workspace Library trên DeX. Không dùng `.sw001`, callback giả, test hook, shell resize hoặc ép bố cục. Trước launch không có task Calculator/Clock; không force-stop hai ứng dụng fixture.

Classic Open → hai cửa sổ vốn đúng → assessment → dock thật **Repair / Layout already correct**, tương ứng `LAYOUT_CORRECT`. Không bấm Manual Repair.

| Ứng dụng | Task ID | Requested / expected | Actual từ lần quan sát đầu đến cuối | Max deviation |
|---|---:|---|---|---:|
| Calculator | 5506 | `[8,8,952,1136]` | `[8,8,952,1136]` | **0 px** |
| Clock | 5507 | `[968,8,1912,1136]` | `[968,8,1912,1136]` | **0 px** |

Các task là freeform, user 0, display 6, visible. [Fixture](S23-Calculator-Clock-50-50.dwt), [Automatic radio](automatic-mode-selected.png), [Classic Open button](proof-ready-dock-expanded.png), [UI kết quả](automatic-correct-result.png).

## Zero-mutation proof

- Trace **20.181 giây / 60 samples**; cả hai task đồng thời ổn định **17.137 giây** sau khi xuất hiện.
- **0 task bounds changes / 0 task resize**: toàn bộ observations của cả hai task đều bằng expected ngay từ đầu, không cần tolerance.
- **0 unrelated mutation**: tập task ngoài fixture, IDs/component/user/display/freeform/bounds giữ nguyên; không tạo/xóa task ngoài fixture. Thay đổi focus/visibility thông thường do Classic Open không được coi là resize/mutation cửa sổ.
- **0 Shizuku app START** trong trace; chỉ có START của Calculator và Clock.
- **0 Automatic Repair attempt**: runtime dock xác nhận `LAYOUT_CORRECT`; source `WorkspaceRepairSession` không đổi chỉ gọi Automatic khi `REPAIR_AVAILABLE`. Accepted regression `automaticCorrectLayoutOnlyReadsOnceAndDoesNotInvokeRepair` đã PASS; không có Manual Repair click.

Đây là kết hợp bằng chứng runtime assessment, task/event trace và policy đã freeze. Không inject counter hoặc instrument RPC nội bộ vào APK không debuggable. Số zero attempt/resize được chứng minh bằng trạng thái runtime cùng nhánh admission không thể gọi Repair khi `LAYOUT_CORRECT`, đối chiếu trace stable bounds; không giả nhận một bộ đếm RPC đã được đọc.

Phần setup đã mở Shizuku Manager để quan sát quyền DWT đã bật, rồi đóng trước khoảng đo; không thay permission. Con số zero Shizuku launch áp dụng cho **product proof**, không gộp thao tác setup vào runtime.

[Trace](automatic-correct-classic-task-trace.json), [runtime observation](assessment-ui-observation.json), [summary](s23-qualification-summary.json), [verifier](verify-proof.mjs). Chạy `node verify-proof.mjs`: **PASS**.

## Freeze và quyết định cuối

- **36/36 source/test files khớp snapshot; zero source changes.** Không sửa license/security, authority/ProductRunGate, Embedded/VDM, Room hoặc build/version/signing/release config.
- Artifact qualification giữ nguyên hash, không build lại. S22 evidence giữ nguyên; **S22 PASS retained**.
- Regression/source/build đã chấp nhận giữ hiệu lực do bytes không đổi; không chạy lại Manual/Suggest/OFF trên S23 hoặc các suites đã PASS.
- `release/1.0-beta` và version `1.0.0-beta.8 (9)` không thay đổi; không có release/publication mới.
- S23 giữ fixture đã nhập; AUTOMATIC và dock dùng cho proof vẫn đang hiển thị để review. Không đổi 8 shortcut cũ.
- **DWT-SHIZUKU-001: PASS — PRODUCT INTEGRATION VIABLE / READY FOR COMMIT REVIEW.**
- Không commit/push/tag. Dừng tại checkpoint review.
