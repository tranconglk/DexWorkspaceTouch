# DWT-LAYOUT-001 — Parser Repair Android 10

Ngày báo cáo: 2026-10-08. Bản sửa và phép thử Note 9: **PASS**.
Publication: **HOLD**. Mã chưa commit, chưa tích hợp vào release.

## Thay đổi và phạm vi

Worktree: `.worktrees/dwt-layout-001-minimal-gutter`, base `1d7cf5e`.
Chỉ sửa hai file production:

- `WorkspaceTaskCorrelation.kt`: nhận `Task{...}` và `TaskRecord{...}`;
  với Android 10, lấy bounds từ preamble `Task id #...` có ID khớp,
  mode từ Stack hiện tại, visibility từ ActivityRecord khớp component/user/task.
- `ExistingWorkspaceRepair.kt`: nhận identity TaskRecord và đóng context tại
  ranh giới display/stack/task/running activities.

Thêm `Android10TaskCorrelationTest.kt` với 11 test behavioral. Giữ nguyên
exact component, user 0, external display, visible/freeform, Task/ActivityRecord
identity, unique candidate/task ID, kiểm tra ngay trước resize và stable readback.
Không lấy `hasBeenVisible`, display configuration hoặc activity khác làm bằng
chứng visibility. Không đổi policy, admission, transport/protocol hoặc geometry.

11 file gutter có sẵn khớp hash manifest trước nhiệm vụ. Không đổi license,
signing config, production config, Room, version hoặc worktree release final.

## Kiểm chứng local

| Kiểm tra | Kết quả |
| --- | --- |
| RED trên parser cũ | 11 test chạy, 8 FAIL do không nhận TaskRecord, 3 PASS |
| Shizuku + WorkspaceRepair regression | PASS 100/100; 0 failure/error/skipped |
| `:app:assembleRelease --offline`, qua script qualification có sẵn | PASS, 1m46s |
| `lintVitalRelease`, APK signature/official signer, package/version/config | PASS |
| Hash APK cài trên Note 9 bằng APK vừa build | PASS |
| Scoped diff whitespace | PASS |

APK qualification: `smoke-output/dwt-layout-001/note9-android10/DWT-note9-android10-parser.apk`.
SHA-256: `43df7b947e0416e7507a976a4277c48a05f34ba913a6a240dd693157cb119264`.
Package `com.trancong.dexworkspacetouch`, beta.9/code 10, official signer
`19ac0ea99125361b3c2083aaa44c8745ebad9c55fa967642c2b9e5a1086a45e7`.
APK trước sửa đã được giữ riêng; đây là qualification artifact từ source có patch,
không phải final release artifact. Không chạy lại full suite hoặc S22/S23;
regression hiện tại được giới hạn ở vùng parser/Shizuku/Repair bị ảnh hưởng.

## Note 9 thực tế

Chỉ áp dụng cho **SM-N960N / hadesROM Android 10** đã kiểm tra, DeX display 2,
usable 1920×1028. Cài bằng `adb install -r`; gate hiện hữu và Library mở được,
các workspace đã lưu vẫn hiện diện. Không clear data/reset license.

| Phép thử | Kết quả |
| --- | --- |
| Chrome/YouTube 50/50 | PASS; task 3336/3337, bounds `[8,8,958,1020]` / `[962,8,1912,1020]`, ổn định ≥18.644s |
| 50/50 → Chrome/Calculator/TikTok, Automatic | PASS; Chrome được thu về `[8,8,638,1020]`, dock `1 repaired, 2 correct`; cả layout ổn định ≥14.954s |
| Manual khi policy SUGGEST | PASS; sai lệch Calculator tạo bằng kéo viền UI: `[962,8,1278,1020]` → `[642,8,1278,1020]`; dock `1 repaired, 2 correct`, ổn định ≥19.451s |

Bounds ba cột cuối: Chrome task 3336 `[8,8,638,1020]`, Calculator task 3338
`[642,8,1278,1020]`, TikTok task 3339 `[1282,8,1912,1020]`. Hai khe 4 px,
không còn overlap Chrome/Calculator. TikTok thực tế dùng package
`com.ss.android.ugc.trill`.

317 mẫu qua ba trace. Task IDs, Task object và ActivityRecord identities giữ
nguyên; Chrome giữ identity xuyên chuyển 50/50 → ba cột; ba task giữ identity
đến Manual. Không thấy bounds/display change của task ngoài fixture qua
**195 đối chiếu** (YouTube: Automatic 77, Manual 118). Không shell resize.
Crash buffer của app PID hiện tại có 0 dòng; không suy rộng sang toàn bộ lịch sử.

Đã trả **SUGGEST / Floating Dock OFF** qua UI và giữ APK qualification trên máy.

Evidence nằm tại `smoke-output/dwt-layout-001/note9-android10/`: `summary.json`,
ba trace JSON, `automatic-dock.png`, `manual-dock-after.png`, `final-clean.png`,
`policy-final.png`, `dock-off-final.png`, `build.log`, `android10-green.xml`,
`source-manifest.json`. RED/GREEN logs ở thư mục cha.

Internal RPC counters trong APK non-debuggable: **NOT VERIFIED trực tiếp**;
task effects được kiểm chứng bằng trace và regression behavioral. Không thay đổi
publication HOLD, commit/tag/push hoặc lịch sử bằng chứng trước nhiệm vụ.
