# DWT-REL-004 — Production release cut

Ngày kiểm định: 2026-10-08 (Asia/Saigon). Kết quả: **QUALIFIED** cho
`1.0.0-beta.9 / versionCode 10`.

## Source và phạm vi commit

- Baseline được chấp nhận: `release/1.0-beta` tại
  `f9ea68934847b156b4903b7106de4e1e3f7dbcbe`.
- Preflight và review cuối: nhánh local, remote-tracking và live origin đều cùng
  baseline trên; worktree mới sạch trước chỉnh sửa.
- Nhánh chuẩn bị: `release/dwt-rel-004-beta9`;
  worktree `.worktrees/dwt-rel-004-beta9`.
- Commit chuẩn bị duy nhất chứa tài liệu này có message
  `DWT-REL-004: prepare 1.0.0-beta.9`. SHA của commit được ghi trong báo cáo kết thúc
  và có thể lấy bằng `git log -1 --format=%H -- docs/release/DWT-REL-004_PRODUCTION_RELEASE_CUT.md`.
- Commit chỉ gồm `app/build.gradle.kts` và tài liệu provenance này.
  Diff source chính xác:

```diff
-        versionCode = 9
-        versionName = "1.0.0-beta.8"
+        versionCode = 10
+        versionName = "1.0.0-beta.9"
```

Application ID, signing identity, license/API/trusted keys, Room schema,
dependencies/toolchain, Classic, Workspace Control/Shizuku, Repair policy,
Embedded gate/authority/runtime đều giữ nguyên baseline. Không có refactor.
Checkout gốc vẫn ở `db69bed601eceea12259249c89f0f5276793e425`, giữ nguyên hai file
probe đã sửa và dữ liệu untracked; 18 worktree có trước được giữ nguyên.

## Build và artifact production

Build bằng `scripts/build-production-release.ps1 -AcknowledgeDirtyWorktree`,
configuration production và signing material chính thức hiện có. Không bỏ tests,
không tạo update manifest. Script exit 0; `BUILD SUCCESSFUL in 9m 44s`, 140 task
được thực thi.

| Kiểm định | Kết quả |
| --- | --- |
| Full JVM `testDebugUnitTest` | PASS: 163 suite, 1302 test, 0 failure/error/skipped |
| `lintDebug`, `assembleDebug` | PASS |
| `assembleDebugAndroidTest` | PASS; build APK instrumentation, không chạy lại historical device matrix |
| `lintVitalRelease` | PASS |
| `assembleRelease` / release Kotlin, resources, package | PASS |
| Production guard trên release package/assemble | PASS: HTTPS non-local, registry không rỗng, signing bắt buộc |
| `apksigner verify` và certificate đối chiếu official signer | PASS |
| `aapt2` manifest/package và actual DEX BuildConfig | PASS |
| `git diff --check` | PASS |

Artifact duy nhất dùng trên cả hai máy:

- Filename: `DexWorkspaceTouch-1.0.0-beta.9-10.apk`.
- Vị trí local: `release-output/` trong worktree chuẩn bị; Git ignored.
- Size: **28014969 bytes**.
- APK SHA-256: `e15b98de0be96ebe570fda9c25fe713784ac5dc07668489c75aa50cbd384fee5`.
- Official signer certificate SHA-256:
  `19ac0ea99125361b3c2083aaa44c8745ebad9c55fa967642c2b9e5a1086a45e7`.
- Package: `com.trancong.dexworkspacetouch`; version `1.0.0-beta.9 / 10`;
  build type `release`, channel `beta`, `debuggable=false`.
- Packaged license API:
  `https://dexworkspacetouch-license-production.dex-backend.workers.dev`.
- Trusted registry: chỉ `license-signing-v1`, algorithm `RS256`.
- Trusted public-key SPKI SHA-256:
  `ae8c0a5376ceef82fa3f0123e2788c21cc218bb19fb866aed83813745d392e74`.
- Không có debug/test/localhost license endpoint trong packaged configuration.

Build provenance: timestamp `2026-10-07T23:48:20Z`; embedded `BUILD_COMMIT` là
baseline `f9ea68934847b156b4903b7106de4e1e3f7dbcbe`, manifest `dirty=true` do
patch version hai dòng được review. SHA-256 của version patch:
`2188e0a79aa36b5db9044e23db4d6f3f75263611d0a4e3586869511d492c1bf8`.
SHA-256 file `app/build.gradle.kts` dùng build:
`bce5311f6ae97337e3d1039b032d7b27a6fec6e96974ec98b897e93c89c71686`.
APK được build trước commit chuẩn bị, đã đóng băng trước smoke và không rebuild
sau kiểm định. Commit chuẩn bị không phải embedded build SHA của APK này.

## Upgrade và bảo toàn dữ liệu

Theo thứ tự user chọn: S22 trước, S23 sau. Cả hai baseline installed APK đều là
beta.8/code 9 với official signer; SHA-256 APK cũ:
`449edff24904002cca254a28be2d4f06f6bf2dd0e49d56015280329c870d7311`.
Normal LicenseGate → Workspace Library đã được quan sát trước install.

Chỉ dùng `adb install -r` với cùng frozen APK production. Không uninstall,
clear data, downgrade, reset license hoặc thay signer. Sau install, AppId,
firstInstallTime, user-0 dataDir giữ nguyên; cold restart app qua LicenseGate
mở Library bình thường trên cả hai máy. Không đọc/export plaintext license/token.

| Kiểm định | S22 (SM-S908E) | S23 (SM-S918B) |
| --- | --- | --- |
| Upgrade beta.9/code 10, package và signer | PASS | PASS |
| Cold LicenseGate / Library | PASS | PASS |
| Library count và canonical content trước/sau | PASS: 1 workspace | PASS: 14 workspace |
| Pin state | PASS: không ghim | PASS: 1 ghim, identity fingerprint trùng |
| Shortcut/settings | PASS: 6 slot, assignments giữ nguyên | PASS: 8 slot, assignments giữ nguyên |
| Policy sau smoke | SUGGEST, trả về qua UI | AUTOMATIC, đúng baseline user đã chọn |
| Floating Dock sau smoke | OFF, đúng baseline | OFF, đúng baseline |
| Embedded product UI entry | Không có trên Home/Library/Files/Car | Không có trên Home/Library/Files/Car |

Canonical Library SHA-256 trước/sau trùng nhau:

- S22: `837215b7ffe17f01943ca80af636f864e7551a654e376a0934e43dbdd5d577fc`.
- S23: `37f77bd44768a1aa90196026d9fc8d78e1ccb39be9c50da6b12b1721c47d7c57`.

Phương pháp: dùng chức năng Sao lưu Library của app, chỉ tính count và SHA-256
ngay trên thiết bị; chuẩn hóa riêng timestamp envelope export về 0. Serializer
deterministic giữ names/layouts/apps trong digest; không chuyển raw bundle sang
host. Export không chứa database IDs hay pin/settings: các mục đó được đối chiếu
riêng bằng metadata, normal UI và fingerprint pin/shortcut assignments. Không
đọc private DB. Workspace mang tên lịch sử VDM là dữ liệu đã có, không phải
Embedded product entry; không mở các route Embedded.

## Smoke S22 — Classic → Automatic Repair

Chrome / Samsung Browser / Tips, tỷ lệ 25/25/50; một lần Classic Open từ Library.
DeX display 21, usable area 1920×1024; trace 20.199 giây, 51 mẫu.

| App | Task ID giữ nguyên | Bounds đầu quan sát | Bounds cuối |
| --- | --- | --- | --- |
| Chrome | 11429 | `[728,6,1192,1014]` | `[8,8,472,1016]` |
| Samsung Browser | 11430 | `[1456,16,1920,1024]` | `[488,8,952,1016]` |
| Tips | 11431 | `[968,8,1912,1016]` | `[968,8,1912,1016]` |

PASS: Classic sai layout được quan sát, Automatic đạt **3/3 đúng**, ổn định
15.116 giây. Dock thực tế hiện `Repaired / 2 repaired, 1 correct`: Tips đã đúng
và được bỏ qua. Không có unrelated bounds/display change qua 1020 đối chiếu,
không có Shizuku app START, không bấm Manual Repair hay shell resize. Policy
được trả về SUGGEST và dock tắt qua UI.

## Smoke S23 — Classic + Automatic, layout đã đúng

Calculator / Clock 50/50; một lần Classic Open từ Library. DeX display 8,
usable area 1920×1144; trace 20.052 giây, 53 mẫu.

| App | Task ID giữ nguyên | Bounds từ lần đầu đến cuối |
| --- | --- | --- |
| Calculator | 5569 | `[8,8,952,1136]` |
| Clock | 5570 | `[968,8,1912,1136]` |

PASS: **LAYOUT_CORRECT**, ổn định 16.774 giây, target bounds change = 0;
unrelated bounds/display change = 0 qua 954 đối chiếu; Shizuku app START = 0.
Dock mở/đóng bình thường, mục Repair hiện diện, assessment thực tế
`Layout already correct`; không kích hoạt Manual Repair trên layout đã đúng.
Policy AUTOMATIC giữ nguyên, dock trả về OFF.

Bằng chứng zero unnecessary mutation kết hợp dock LAYOUT_CORRECT, geometry
đúng và không đổi, cùng regression
`automaticCorrectLayoutOnlyReadsOnceAndDoesNotInvokeRepair` PASS trong full suite.
Internal RPC/mutation counters trong signed non-debuggable APK: **NOT VERIFIED**
trực tiếp; không inject instrumentation hoặc sửa product để lấy counters.
Manual Repair trên S23 không chạy lại ngoài smoke được yêu cầu.

## Review cuối và trạng thái release

Sau cả hai máy PASS, frozen artifact hash được kiểm tra lại. Installed APK trên
S22/S23 đều đúng SHA-256 `e15b98de…`; official signer đồng nhất được chứng minh
bằng byte equality với artifact đã qua `apksigner verify`. Cả hai package là
beta.9/code 10, non-debuggable. Không dùng qualification/debug APK cho upgrade.
Source review chỉ có version patch và provenance; APK, keystore, credentials,
license/token, screenshot/private backup/raw customer data không được commit.

**DWT-REL-004 = READY FOR RELEASE COMMIT / QUALIFIED**.
Một release-preparation commit theo phạm vi đã được user cho phép; không merge
hoặc di chuyển `release/1.0-beta`. Không push, tag, GitHub Release, upload APK,
publication, thay backend/key/channel hoặc store deployment.

**Tag = NOT AUTHORIZED. Publication = NOT AUTHORIZED.**

Automatic approval review đã từ chối thao tác kéo raw Library backup về host
vì payload có thể chứa dữ liệu riêng tư và chưa được ủy quyền cụ thể cho việc
chuyển payload đó. Kiểm định bảo toàn được hoàn tất bằng fingerprint trên thiết
bị; không thử lại thao tác đã bị từ chối.
