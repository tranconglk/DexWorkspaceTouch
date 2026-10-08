# DWT-LAYOUT-001 — Workspace gutter 4 px

**DWT-LAYOUT-001 = PASS — READY FOR VISUAL REVIEW.**
Chưa commit/push/tag/publish.

## Phạm vi và Git

- Base: `1d7cf5eb7b6ed2741d92eb7f22882e82e1d86b68`.
- Branch: `layout/dwt-layout-001-minimal-gutter`.
- Worktree: `.worktrees/dwt-layout-001-minimal-gutter`.
- `release/1.0-beta` vẫn sạch, ở base trên, đồng bộ origin `0/0`.
- Không đổi version: `1.0.0-beta.9 / 10`. APK này chỉ để qualification;
  geometry mới cần version mới sau khi qualification được chấp nhận.
- Không commit, push, tag, tạo update manifest hoặc publish.

## Nguồn hình học và công thức

Nguồn authoritative: `platform/launch/bounds/LaunchBoundsCalculator.kt`.
Classic (`AndroidSingleAppLauncher`) và assessment/Repair
(`ExistingWorkspaceRepair`) đều gọi calculator này.

Gọi `R(n) = roundToInt(origin + n × usableSize)`.
Cũ: cạnh trái/trên = `R(n) + marginPx`, cạnh phải/dưới =
`R(n) - marginPx` cho mọi cạnh. `marginPx = roundToInt(8 dp × density)`;
trên hai DeX fixture density 1, mỗi cạnh inset 8 px, tạo khe `8 + 8 = 16 px`.
Vì vậy S23 có `[8,8,952,1136] / [968,8,1912,1136]` và S22 có
`[8,8,472,1016] / [488,8,952,1016] / [968,8,1912,1016]`.

Mới: `OUTER_MARGIN_PX = 8`, `INTERNAL_GUTTER_PX = 4`.
Cạnh normalized 0/1 dùng viền ngoài 8 px; cạnh trong dùng 2 px.
Cạnh trái/trên cộng inset, cạnh phải/dưới trừ inset. Shared divider dùng
cùng `R(n)`, nên hai cạnh ở `R(n)-2 / R(n)+2`; tâm divider và tỷ lệ
normalized giữ nguyên. Giữ clamp theo work area và typed failure
`INSUFFICIENT_SPACE` khi vùng quá nhỏ. Không tạo bounds đảo hoặc diện tích 0.

Đổi cấu hình margin từ dp sang pixel là cần thiết để giữ viền 8 px ở mọi
density. Cấu hình này không được lưu trong workspace/Room; không migrate
normalized definitions hoặc dữ liệu workspace.

## File nguồn thay đổi chính xác

Prefix production: `app/src/main/java/com/trancong/dexworkspacetouch/`.

- `platform/launch/bounds/LaunchBoundsCalculator.kt`
- `platform/launch/bounds/LaunchBoundsConfig.kt`
- `platform/launch/bounds/LaunchMargin.kt`

Prefix test: `app/src/test/java/com/trancong/dexworkspacetouch/`.

- `platform/launch/bounds/WorkspaceGutterGeometryTest.kt` (mới)
- `platform/launch/shizuku/WorkspaceGutterConsumerTest.kt` (mới)
- `platform/launch/bounds/LaunchBoundsCalculatorTest.kt`
- `platform/launch/bounds/LaunchMarginTest.kt`
- `platform/launch/android/AndroidSingleAppLauncherTest.kt`
- `platform/launch/shizuku/ExistingWorkspaceRepairTest.kt`
- `platform/launch/shizuku/ExistingWorkspaceAssessmentTest.kt`
- `feature/car/overlay/WorkspaceRepairPolicyTest.kt` (chỉ expected fixture)

Ngoài 11 file code/test trên, thêm báo cáo này. Build logs, helper qualification,
source hashes, trace JSON và screenshots nằm trong local Git-ignored
`smoke-output/dwt-layout-001/`; không sửa script build đã chấp nhận.

## Expected bounds

| Fixture | Cell/app | Bounds mới |
| --- | --- | --- |
| S23 1920×1144, 50/50 | Calculator | `[8,8,958,1136]` |
| S23 1920×1144, 50/50 | Clock | `[962,8,1912,1136]` |
| S22 1920×1024, 25/25/50 | Chrome | `[8,8,478,1016]` |
| S22 1920×1024, 25/25/50 | Browser | `[482,8,958,1016]` |
| S22 1920×1024, 25/25/50 | Tips | `[962,8,1912,1016]` |

2×2 trên 1920×1144: `[8,8,958,570]`, `[962,8,1912,570]`,
`[8,574,958,1136]`, `[962,574,1912,1136]`; khe ngang/dọc đều 4 px.
Single cell giữ `[8,8,1912,1136]`.

## Tests và build

| Kiểm chứng | Kết quả |
| --- | --- |
| Baseline calculator/margin/Classic/assessment/Repair | PASS 55/55 |
| RED: 13 geometry + 5 consumer tests trên implementation cũ | 18 chạy; 16 FAIL đúng geometry/density cũ, 2 đã PASS |
| Targeted GREEN, gồm policy regression | PASS 87/87 |
| Full app `:app:testDebugUnitTest --offline` | PASS 1320/1320; 0 failure/error/skipped |
| `:app:assembleRelease --offline` | PASS, 2m59s |
| `lintVitalRelease` trong build trên | PASS |
| `apksigner verify` và official signer | PASS |
| Package/version và production config trong packaged DEX | PASS, giữ nguyên |
| Scoped diff whitespace check | PASS |

Tests bao phủ 50/50, 25/25/50, 2×2, full canvas, unequal/T junction,
thirds, odd dimensions, work-area insets, interior cells, tiny-cell failure,
one-pixel valid region, no-overlap và density invariance. Consumer tests
chạy Classic thật và assessment/Repair thật với platform/shell giả ở boundary;
assert bounds literal, task identity, không resize unrelated task và không mutate
layout đã đúng. Full regression bao gồm policy/gate/correlation hiện hữu.

APK local: `smoke-output/DWT-device-smoke.apk`.
SHA-256: `5733dbff7d06e4d68b309018dc039595e8e81067c638dac8aa970776092697c9`.
Signer SHA-256:
`19ac0ea99125361b3c2083aaa44c8745ebad9c55fa967642c2b9e5a1086a45e7`.
Hash APK đã cài trên cả hai thiết bị khớp byte với APK qualification này.
Source là base cộng patch chưa commit; `source-manifest.json` ghi hash chính xác
11 file code/test, đã kiểm tra không đổi sau build/test. Đây không phải final
release artifact sạch của DWT-REL-004.

## S23 / DeX

PASS, SM-S918B, display 9, usable 1920×1144. Cài `adb install -r`, vào
Library bình thường qua gate hiện hữu, dùng workspace đã lưu
`S23 Calculator Clock 50 50`. Policy AUTOMATIC vốn có giữ nguyên;
bật dock qua UI cho qualification và tắt lại sau scenario.

Một lần Classic Open qua UI; trace 66 mẫu trong 20.092 giây sau trigger:

| App | Task ID | Bounds lần xuất hiện đầu = cuối |
| --- | --- | --- |
| Calculator | 5602 | `[8,8,958,1136]` |
| Clock | 5603 | `[962,8,1912,1136]` |

2/2 exact, giữ task/ActivityRecord identity, bounds change 0; final layout ổn
định 19.373 giây. Unrelated bounds/display change 0 qua 130 đối chiếu.
Dock thực tế: `Layout already correct` / LAYOUT_CORRECT. Không bấm Manual
Repair, không shell resize. Zero unnecessary Repair được chứng minh ở cấp
hành vi cùng regression policy đã chạy PASS; internal RPC counters trong APK
non-debuggable: **NOT VERIFIED trực tiếp**.

- [Screenshot S23 4 px sạch](../../smoke-output/dwt-layout-001/s23-4px-clean.png)
- [Dock LAYOUT_CORRECT](../../smoke-output/dwt-layout-001/s23-dock.png)
- [Task summary](../../smoke-output/dwt-layout-001/s23-summary.json)

Visual review S23: hai khung/title bar phân biệt rõ; khe 4 px và viền 8 px;
không thấy overlap title bar/border, bóng làm hai khung hòa vào nhau hoặc
nội dung bị cửa sổ bên cạnh cắt. Screenshot sạch chụp sau tắt dock.

## S22 / DeX

PASS, SM-S908E, display 23, usable 1920×1024. Người dùng chuyển DeX sang S22
sau khi S23 xong. Cài cùng APK bằng `install -r`, Library và workspace đã lưu
`S22 Chrome Browser Tips 25 25 50` vẫn mở bình thường. Baseline SUGGEST/OFF
được ghi bằng UI; tạm chọn AUTOMATIC/bật dock cho qualification.

Một lần Classic Open qua UI; trace 54 mẫu trong 20.118 giây sau trigger:

| App | Task ID | Bounds lần xuất hiện đầu | Bounds sau Automatic Repair |
| --- | --- | --- | --- |
| Chrome | 11444 | `[725,6,1195,1014]` | `[8,8,478,1016]` |
| Browser | 11445 | `[1444,16,1920,1024]` | `[482,8,958,1016]` |
| Tips | 11446 | `[0,0,950,1008]` | `[962,8,1912,1016]` |

3/3 exact, task IDs và Task/ActivityRecord identity giữ nguyên. Mỗi task có
đúng một thay đổi bounds; final layout ổn định 15.573 giây. Dock thực tế:
`Repaired / 3 repaired, 0 correct`. Không bấm Repair thủ công hoặc shell resize.
Không quan sát resize unrelated task; trace S22 không có task ứng dụng ngoài
fixture đủ điều kiện đối chiếu (0 comparison). Consumer regression có task
unrelated hiện hữu và assert task đó không bị thay đổi. Không làm yếu correlation
hoặc protocol để đạt kết quả.

Đã trả policy về **SUGGEST**, Floating Dock về **OFF** qua UI, có screenshot
xác nhận. Installed byte hash khớp APK qualification.

- [Screenshot S22 4 px sạch](../../smoke-output/dwt-layout-001/s22-4px-clean.png)
- [Dock Automatic Repair 3/3](../../smoke-output/dwt-layout-001/s22-dock.png)
- [Task summary](../../smoke-output/dwt-layout-001/s22-summary.json)
- [SUGGEST được restore](../../smoke-output/dwt-layout-001/s22-policy-restored.png)
- [Floating Dock OFF](../../smoke-output/dwt-layout-001/s22-dock-off.png)

Visual review S22: hai khe 4 px rõ, viền ngoài 8 px; từng border/title bar vẫn
phân biệt được, không thấy overlap hoặc bóng làm layout xấu hơn. Nội dung nằm
trong từng cửa sổ; không bị cửa sổ kế bên cắt. Không chạy lại scenario.

## Bảo toàn và giới hạn

Không đổi Classic/Shizuku architecture, correlation, Manual/Suggest/Automatic
policy hoặc default SUGGEST, EmbeddedProductRunGate/CLEANUP_BLOCKED,
Embedded runtime/routes, Room/schema, licensing, signing, UserService protocol,
whitelist hoặc version. Không reset license, uninstall/clear data hoặc đọc
private DB/token. Workspaces đã lưu vẫn dùng normalized definitions hiện hữu.

Review thực hiện trong cùng agent theo scope nhỏ của AGENTS.md; không dùng
subagent. Rà soát production diff và các consumer; không thấy lỗi blocker
trong scope code. Visual 2×2 trên thiết bị: **NOT VERIFIED**, được kiểm chứng
bằng behavioral tests theo yêu cầu test; device fixtures bắt buộc là hai
scenario S23/S22 nêu trên. Không thực hiện optional experiment 2 px/0 px.

## PASS criteria

- Outer frame 8 px, internal gutter 4 px: PASS trên cả S22/S23.
- Divider giữ tâm/tỷ lệ, rounding deterministic: PASS qua behavioral tests.
- 2×2 ngang/dọc, arbitrary unequal cells, tiny safe failure, no invalid bounds:
  PASS qua behavioral tests.
- Classic/assessment/Repair dùng cùng expected geometry: PASS.
- Saved workspace compatible, không migration: PASS từ source diff và hai
  fixture đã lưu được mở trên APK mới.
- S23 Classic exact/LAYOUT_CORRECT, zero unnecessary Repair ở cấp hành vi: PASS.
- S22 Automatic Repair exact 3/3, identity retained: PASS.
- Visual screenshots cả hai thiết bị và kiểm tra border/shadow/content: PASS.
- Full app regression, build, signer và scope lock: PASS.
- `release/1.0-beta` và artifact DWT-REL-004 được giữ nguyên; không commit/tag/
  publish: PASS. STOP.
