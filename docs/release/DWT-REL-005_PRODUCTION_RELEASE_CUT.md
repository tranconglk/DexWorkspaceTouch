# DWT-REL-005 — Production release cut

Ngày kiểm định: 2026-10-08 (Asia/Saigon).

**Qualification = PASS / FINAL ARTIFACT READY.**
Publication = HOLD. Tag = NOT AUTHORIZED.

## Source và artifact

| Trường | Giá trị |
|---|---|
| Accepted baseline / release local và origin trước sửa | 1a5d512159cce74d0c7bdf653c1ecb700b8f6ecc |
| Preflight ahead/behind | 0/0; release worktree và index sạch |
| Release-cut branch | release/dwt-rel-005-beta10 |
| RELEASE_SOURCE_SHA | 395b73539b86988387828229fc6dc1de0bfbf4af |
| Source commit | DWT-REL-005: prepare 1.0.0-beta.10 |
| Package | com.trancong.dexworkspacetouch |
| Version / versionCode | 1.0.0-beta.10 / 11 |
| BUILD_COMMIT trong DEX của APK | 395b73539b86988387828229fc6dc1de0bfbf4af |
| HEAD lúc build | RELEASE_SOURCE_SHA |
| Dirty lúc build | false; tracked tree và index sạch |
| Build type / channel / debuggable | release / beta / false |
| Build timestamp UTC | 2026-10-08T09:58:30Z |
| APK filename | DexWorkspaceTouch-1.0.0-beta.10-11.apk |
| APK size | 28031353 bytes |
| FINAL_APK_SHA256 | 95627b8ee33e8e66ab245ebbad7fff9d58d48e617b2cacc49b009a72d0cc4fa0 |
| Official signer SHA-256 | 19ac0ea99125361b3c2083aaa44c8745ebad9c55fa967642c2b9e5a1086a45e7 |

Source diff duy nhất so với accepted baseline, tại app/build.gradle.kts:

~~~diff
-        versionCode = 10
-        versionName = "1.0.0-beta.9"
+        versionCode = 11
+        versionName = "1.0.0-beta.10"
~~~

Commit chứa tài liệu này là commit provenance chỉ có docs, với subject
"DWT-REL-005: record beta.10 production qualification". Sau fast-forward,
RELEASE_BRANCH_TIP là SHA của commit docs đó, khác RELEASE_SOURCE_SHA.
SHA tip/local/origin chính xác được ghi trong báo cáo hoàn tất sau push.
APK vẫn được build từ source SHA nêu trên; không rebuild do thay đổi tài liệu.

Worktree chứa artifact cục bộ:
D:/AndroidStudioProjects/DexWorkspaceTouch/.worktrees/dwt-rel-005-beta10.
APK và release-manifest.json nằm trong release-output/ được Git ignore.

## Build, kiểm thử và production config

| Gate | Kết quả / bằng chứng |
|---|---|
| Release-relevant JVM | PASS — 26 suites, 207 tests; 0 failures/errors/skipped |
| Full JVM | PASS — 167 suites, 1368 tests; 0 failures/errors/skipped |
| assembleRelease | PASS |
| lintVitalRelease | PASS; analyze/report/vital tasks hoàn tất |
| Production guard | PASS — HTTPS production URL, trusted registry đầy đủ, bắt buộc production signing |
| Package/manifest | PASS — aapt2: đúng package, beta.10/11, non-debuggable |
| Signing | PASS — apksigner verify và certificate đúng official signer |
| Packaged provenance | PASS — trích actual BuildConfig trong DEX, đối chiếu release manifest và APK hash |
| License config / trusted keys | PASS — so actual packaged fields với APK production đang cài trước nâng cấp; bằng nhau |

Lệnh release-relevant: :app:testDebugUnitTest với các filter
com.trancong.dexworkspacetouch.platform.launch.shizuku.*,
com.trancong.dexworkspacetouch.feature.car.overlay.WorkspaceRepair*,
com.trancong.dexworkspacetouch.license.*.
Full regression: :app:testDebugUnitTest không filter.
Production build: scripts/build-production-release.ps1 -SkipTests;
SkipTests chỉ tránh chạy lại các suite đã PASS riêng trước build.

Production license API giữ nguyên:
https://dexworkspacetouch-license-production.dex-backend.workers.dev.
Trusted registry giữ nguyên một key license-signing-v1, RS256;
SPKI SHA-256 = ae8c0a5376ceef82fa3f0123e2788c21cc218bb19fb866aed83813745d392e74.
Signing config, trusted key bytes, channel, update URL và các trường production
ổn định đều bằng baseline. Không thay backend, dependency/toolchain,
Room/schema, protocol/whitelist, Classic, Repair, geometry, parser,
ProductRunGate, CLEANUP_BLOCKED hoặc admission/cancellation.

## Update tại chỗ và bảo toàn dữ liệu

Cả ba máy nâng từ production beta.9/code 10 bằng adb install -r cùng một
final APK; không uninstall, clear data, reset license/workspace/settings.
App ID, firstInstallTime và dataDir giữ nguyên trên từng máy.
Cold MainActivity đi qua LicenseGate hiện có và vào Library mà không yêu cầu
kích hoạt lại. License hiện có tiếp tục hợp lệ.

Fingerprint Library được tính trên thiết bị từ bản export UI trước/sau nâng
cấp, chỉ chuẩn hóa exportedAtEpochMillis về 0. Nội dung workspace được so bằng
SHA-256; không chuyển raw backup về máy tính hoặc Git. Export chứa tên/canvas/app; ghim và shortcut/settings được đối chiếu riêng bằng UI.

| Thiết bị | Library trước/sau | Ghim trước/sau | Shortcut/settings | Installed-byte equality |
|---|---|---|---|---|
| S23 / SM-S918B / Android 16 | 14/14; canonical hash bằng nhau | 1/1, giữ nguyên | 8 slots, thứ tự/gán giữ nguyên; SUGGEST trước/sau | PASS |
| S22 / SM-S908E / Android 16 | 1/1; canonical hash bằng nhau | 0/0 | 6 slots, 1 slot được gán; giữ nguyên; SUGGEST trước/sau | PASS |
| Note 9 / SM-N960N / hadesROM / Android 10 | 9/9; canonical hash bằng nhau | 0/0, đối chiếu UI | 6 slots, thứ tự/gán giữ nguyên; OFF trước/sau nâng cấp | PASS |

Hash base.apk sau qualification trên từng máy đều bằng FINAL_APK_SHA256.
Do byte equality, cả ba cùng package/version, packaged provenance,
production config và official signer đã xác minh của final APK.
Embedded product entry vắng mặt trong Library/menu và Car Mode đã quan sát;
Workspace Repair vẫn hiện diện. Settings trước/sau được đối chiếu bằng UI.
Cuối các lượt smoke: cả ba SUGGEST / Dock OFF.
Note 9 chuyển OFF sang SUGGEST theo yêu cầu kết thúc task.

## S23 — Calculator / Clock 50/50

PASS: Classic exact từ quan sát đầu tiên; Automatic assessment
LAYOUT_CORRECT ("Layout already correct"), không có bounds mutation quan sát
được hoặc Repair không cần thiết.

| App | Task ID | Bounds đầu = cuối = expected | Identity variants |
|---|---:|---|---:|
| Calculator | 5669 | [8,8,958,1136] | 1 |
| Clock | 5670 | [962,8,1912,1136] | 1 |

Trace hợp lệ: 88 samples / 20111 ms; bounds ổn định 19906/19667 ms.
174 phép so task ngoài fixture, 0 bounds/display changes.
Outer margin 8 px, internal gutter 4 px.
Restore SUGGEST / Dock OFF được kiểm tra bằng UI.

Điều kiện chuẩn bị được ghi riêng: Classic ban đầu đã exact nhưng Shizuku
daemon chưa chạy. Khởi động daemon bằng lệnh của Shizuku đang cài, không
clear quyền/pairing/data. Lượt mở bổ sung khi bộ cửa sổ cũ còn tồn tại tạo
duplicate fixture task; Repair fail-closed với unresolved assessment.
Đóng các cửa sổ smoke cũ bằng nút đóng DeX rồi lấy trace hợp lệ ở trên.
Không sửa sản phẩm; không dùng trace duplicate để kết luận PASS.

## S22 — Chrome / Samsung Browser / Tips 25/25/50

PASS với AUTOMATIC; Dock báo "Repaired — 3 repaired, 0 correct".

| App | Task ID | Bounds đầu | Final = expected | Bounds changes | Identity variants |
|---|---:|---|---|---:|---:|
| Chrome | 11469 | [725,6,1195,1014] | [8,8,478,1016] | 1 | 1 |
| Browser | 11470 | [1444,16,1920,1024] | [482,8,958,1016] | 1 | 1 |
| Tips | 11471 | [0,0,950,1008] | [962,8,1912,1016] | 1 | 1 |

Một bounded device smoke: 64 samples / 20232 ms, final 3/3 exact;
ổn định lần lượt 16629/16077/15515 ms.
189 phép so task ngoài fixture, 0 bounds/display changes.
Task ID/token, display, user và matched fixture activity token giữ nguyên.
Outer margin 8 px, hai internal gutter 4 px.

Tips thêm Activity Samsung Account vào history của cùng task.
Bộ phân tích ban đầu so toàn bộ history báo 2 history variants; kiểm tra
raw trace xác nhận TipsMainActivity token và task identity không đổi.
Phân tích lại đúng matched fixture identity trên cùng trace: PASS.
Không chạy thêm device smoke hoặc bỏ qua việc task thay identity.

ActivityTaskManager ghi đúng 3 fixture START từ DWT app UID 10347,
0 START từ shell UID. Whitelist production giữ nguyên;
test whitelistRejectsLaunchShellAndMalformedResizeBeforeBinding PASS,
bao gồm từ chối am start trước binding.
Không quan sát app launch qua Shizuku transport.
Restore SUGGEST / Dock OFF được kiểm tra bằng UI.

## Note 9 — Android 10 TaskRecord smoke

PASS: một bounded Automatic smoke hợp lệ với fixture Chrome/Calculator/TikTok;
final 3/3 exact, LAYOUT_CORRECT, không cần Manual.

| App | Task ID | Bounds đầu = cuối = expected | Identity variants |
|---|---:|---|---:|
| Chrome | 3392 | [8,8,638,1020] | 1 |
| Calculator | 3393 | [642,8,1278,1020] | 1 |
| TikTok | 3394 | [1282,8,1912,1020] | 1 |

130 samples / 20179 ms; stable 19988/19652/19265 ms.
387 phép so task ngoài fixture, 0 bounds/display changes;
0 target bounds changes. Outer margin 8 px, internal gutter 4 px.
TaskRecord candidate khớp exact component, user 0, DeX display, freeform và
task/activity identity; chỉ candidate duy nhất có bounds được tính.
Assessment thực tế chứng minh transport/parser sử dụng được các task đã
xác nhận. Shizuku không bị reset.

Một trace chuẩn bị không hợp lệ được lưu riêng: HDMI bị tháo/cắm,
DexController disable display và ActivityManager kill DWT trước trigger.
Trace đó không mở fixture và không được tính là qualification.
Sau khi DeX/Library foreground ổn định, lấy một smoke hợp lệ nêu trên.
Restore SUGGEST / Dock OFF được kiểm tra bằng UI.

## Giới hạn bằng chứng và tích hợp

Các bộ đếm RPC nội bộ trong signed non-debuggable APK: NOT VERIFIED trực tiếp.
Không suy luận số lần gọi RPC từ số lần đổi bounds; kết luận dựa trên task
trace, assessment UI, UID launch và JVM regression đã PASS.

Log build/test, JSON trace/analysis/install result, ảnh UI và APK chỉ lưu trong
các đường dẫn được Git ignore. Tài liệu này không chứa key/token plaintext,
private signing material, raw Library backup, ảnh riêng hoặc APK binary.

Qualification đầy đủ PASS là điều kiện cho fast-forward giữ nguyên nội dung
source/provenance vào release/1.0-beta, rồi normal non-force push chỉ nhánh đó.
Không rebuild/test lại chỉ vì commit docs. Worktree artifact được giữ để
review; các thay đổi sẵn có tại checkout người dùng không bị chỉnh/reset.

Tag = NOT AUTHORIZED
Publication = HOLD

STOP.