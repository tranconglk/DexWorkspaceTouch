# DWT-REL-004 — Production release cut

> Qualification lịch sử được giữ nguyên bên dưới. Clean final artifact, smoke và
> provenance hiện hành nằm trong mục **Final release integration và clean artifact**.

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

## Final release integration và clean artifact — 2026-10-08

Phần qualification ở trên là bằng chứng lịch sử của APK `e15b98de…` build trước
release-preparation commit. **APK final hiện hành là clean rebuild dưới đây**;
không dùng hash/provenance của qualification APK làm provenance final.

### Integration

- Release local, remote-tracking và live origin trước integration:
  `f9ea68934847b156b4903b7106de4e1e3f7dbcbe`.
- Commit release-preparation được chấp nhận:
  `d57ec5774f3c2a60404157639ff5774b62299f58`; chỉ version bump trong
  `app/build.gradle.kts` và tài liệu này; là một commit trực tiếp trên baseline.
- Worktree integration mới: `.worktrees/dwt-rel-004-final`, nhánh
  `release/1.0-beta`; sạch trước integration. `merge --ff-only` PASS, không conflict.
- Normal non-force push chỉ `release/1.0-beta` PASS. Sau push/fetch, local và
  origin cùng `d57ec5774f3c2a60404157639ff5774b62299f58`, ahead/behind `0/0`.

### Clean production build và artifact đóng băng

Source build: `HEAD=d57ec5774f3c2a60404157639ff5774b62299f58`; tracked tree,
index và `git status --porcelain` sạch trước/sau build. Không chỉnh source trước
build. Tái sử dụng signer và production inputs đã duyệt; `local.properties` chỉ
ở local và Git ignored. Không đổi version/signing/license/registry/backend.

`scripts/build-production-release.ps1 -SkipTests` exit 0, **BUILD SUCCESSFUL in
4m 20s**, 53 task thực thi. Không chạy lại full qualification đã chấp nhận; script
không tạo update manifest. Các gate final:

| Gate | Kết quả |
| --- | --- |
| `assembleRelease` | PASS |
| `lintVitalRelease` | PASS |
| Production guard: HTTPS non-local, registry không rỗng, signing bắt buộc | PASS |
| `apksigner verify` và official signer fingerprint | PASS |
| `aapt2` package/version/non-debuggable manifest | PASS |
| Static fields từ BuildConfig trong actual packaged DEX | PASS |
| Source clean và release manifest `dirty=false` | PASS |

- Local final APK: `.worktrees/dwt-rel-004-final/release-output/DexWorkspaceTouch-1.0.0-beta.9-10.apk`.
- Size: **28014969 bytes**; APK đã đóng băng trước final smoke.
- Final APK SHA-256:
  `c55a6e61d4332586324e58db1aa9d3ef7e3b5532020cafca180614062514e1eb`.
- Package: `com.trancong.dexworkspacetouch`; `1.0.0-beta.9 / versionCode 10`.
- Actual DEX `BUILD_COMMIT` và release-manifest `gitCommit`:
  `d57ec5774f3c2a60404157639ff5774b62299f58`.
- Release-manifest **`dirty=false`**; timestamp `2026-10-08T00:44:36Z`.
- Actual DEX `BUILD_TYPE=release`, `BUILD_CHANNEL=beta`, `DEBUG=false`;
  manifest **`debuggable=false`**.
- Official APK signer certificate SHA-256 không đổi:
  `19ac0ea99125361b3c2083aaa44c8745ebad9c55fa967642c2b9e5a1086a45e7`.
- Packaged production license API không đổi:
  `https://dexworkspacetouch-license-production.dex-backend.workers.dev`.
- Actual DEX trusted registry không đổi: một key `license-signing-v1`, `RS256`.
- Trusted public-key SPKI SHA-256 không đổi:
  `ae8c0a5376ceef82fa3f0123e2788c21cc218bb19fb866aed83813745d392e74`.

### Bounded final smoke

S23 chạy trước vì DeX đã nối sẵn; S22 chạy sau khi chuyển kết nối.
S23 chạy một lượt; S22 có một lượt chưa đạt 3/3 và đúng một lượt bổ sung được
user cho phép sau đóng riêng fixture qua UI. Không chạy lại full qualification.
Cài cùng final APK bằng `adb install -r`;
không uninstall, clear data, downgrade, reset license hay lấy plaintext token.

S23 (SM-S918B), DeX display 8: cold startup hiện LicenseGate xác minh rồi Library
bình thường; Embedded UI entry vẫn không có trên Library/Files/Car. Calculator /
Clock 50/50 chạy Classic với policy AUTOMATIC; trace **20.206 giây, 54 mẫu**.
Calculator task 5573 bounds `[8,8,952,1136]`; Clock task 5574
`[968,8,1912,1136]`. Đúng từ lần quan sát đầu và không đổi; cuối 2/2 đúng,
ổn định 17.028 giây. Dock thực tế **`Layout already correct` / LAYOUT_CORRECT**;
target bounds change = 0, unrelated bounds/display change = 0 qua 972 đối chiếu,
Shizuku app START = 0. Không bấm Manual Repair, không shell resize; giữ AUTOMATIC
và trả Floating Dock về OFF qua UI.

Zero unnecessary mutation được xác minh ở cấp hành vi bằng assessment thực tế,
geometry đúng và không đổi, kết hợp regression
`automaticCorrectLayoutOnlyReadsOnceAndDoesNotInvokeRepair` đã PASS trong
qualification được chấp nhận, không chạy lại suite. Internal RPC counters trong
signed non-debuggable APK: **NOT VERIFIED trực tiếp**; không inject instrumentation
hoặc đổi product để đọc counters.

S22 (SM-S908E), DeX display 22: cold startup qua MainActivity/LicenseGate hiện
hữu vào Library bình thường; Embedded UI entry vẫn không có trên Library/Files/Car.
Lượt đầu **chưa PASS**: sau reconnect DeX, task Tips cũ và mới cùng visible;
dock báo `Repair: 2/3; Partial: 1 repaired, 1 correct; tips: UNRESOLVED`. Trace
20.032 giây/52 mẫu được giữ nguyên. Matcher production yêu cầu task exact-component,
user-0, visible/freeform duy nhất; từ chối resize Tips ambiguous là fail-closed.
Không sửa source hoặc làm yếu correlation để bỏ qua tình huống này.

User cho phép **đúng một lượt bổ sung** sau đóng riêng cửa sổ Chrome/Browser/Tips
qua nút X trong UI, không force-stop fixture, không xóa data/reset license.
Precondition: không còn fixture visible; task Chrome lịch sử invisible còn trong
snapshot được ghi nhận, không chọn làm repair target. Helper final đối chiếu các
candidate visible duy nhất, cùng điều kiện production `ExistingTaskCorrelation`;
không bỏ duplicate visible. Raw trace lượt đầu và retry đều giữ ở local ignored output.

Lượt bổ sung PASS: Classic + AUTOMATIC 25/25/50; **20.204 giây, 54 mẫu**:

| App | Task ID | Bounds đầu quan sát | Bounds cuối |
| --- | --- | --- | --- |
| Chrome | 11437 | `[728,6,1192,1014]` | `[8,8,472,1016]` |
| Samsung Browser | 11438 | `[1456,16,1920,1024]` | `[488,8,952,1016]` |
| Tips | 11439 | `[0,0,944,1008]` | `[968,8,1912,1016]` |

Cuối **3/3 đúng**, giữ identity của ba target trong repair, ổn định liên tục
15.733 giây; dock thực tế **`Repaired / 3 repaired, 0 correct`**.
Không có unrelated bounds/display change; task Chrome invisible lịch sử cũng
không bị thay bounds/display. Shizuku app START = 0; không bấm Manual Repair,
không dùng shell resize. Đã trả policy về **SUGGEST** và Floating Dock về **OFF**
qua UI, có xác nhận màn hình sau smoke.

### Bảo toàn và byte equality final

| Kiểm định sau `adb install -r` final | S22 | S23 |
| --- | --- | --- |
| Cold startup qua LicenseGate vào Library, không yêu cầu activate lại | PASS | PASS |
| AppId, firstInstallTime, dataDir giữ nguyên | PASS | PASS |
| Canonical Library count/content hash trước-sau | PASS: 1 workspace | PASS: 14 workspace |
| Pin và shortcut UI hiện hữu | PASS: không pin, 6 slot | PASS: 1 pin, 8 slot |
| Embedded product UI vẫn absent | PASS | PASS |
| Final bounded smoke | PASS, sau lượt bổ sung được user duyệt | PASS: LAYOUT_CORRECT, zero unnecessary mutation ở cấp hành vi |
| Policy / Floating Dock sau smoke | SUGGEST / OFF | AUTOMATIC / OFF |
| Installed APK byte equality với frozen final APK, kiểm tra lại sau smoke | PASS | PASS |

Canonical Library SHA-256 trước/sau:

- S22: `837215b7ffe17f01943ca80af636f864e7551a654e376a0934e43dbdd5d577fc`.
- S23: `37f77bd44768a1aa90196026d9fc8d78e1ccb39be9c50da6b12b1721c47d7c57`.

Fingerprint được tính ngay trên thiết bị từ Sao lưu Library qua UI, chỉ chuẩn hóa
envelope export timestamp về 0; host chỉ nhận count/hash. Không kéo raw Library
backup hoặc đọc private DB/license/token. Cài thay thế cùng signer; không uninstall,
clear data, reset license hay thay device binding.

Installed APK trên **cả S22 và S23** đều có SHA-256:
`c55a6e61d4332586324e58db1aa9d3ef7e3b5532020cafca180614062514e1eb`.
Host frozen APK kiểm tra lại sau smoke vẫn đúng hash trên; installed signer được
chứng minh bằng byte equality với artifact đã qua official signature verification.

### Final provenance commit và ranh giới phát hành

Chỉ cập nhật tài liệu này trong commit riêng
`DWT-REL-004: record clean final artifact and smoke`, sau final build/smoke.
SHA commit tài liệu được ghi rõ trong final report; không phải source SHA của APK.
Không rebuild APK vì tài liệu thay đổi; embedded BUILD_COMMIT vẫn là `d57ec577…`,
source build sạch và `dirty=false` như đã kiểm chứng.

APK/build outputs và bằng chứng thiết bị chỉ local, Git ignored. Không commit APK,
private key/password, plaintext license/token, raw Library backup hoặc private device data.
Không đổi backend, signer/key/trusted registry, production URL hoặc bump version.

**DWT-REL-004 = COMPLETE / PASS / FINAL ARTIFACT READY** với final smoke và giới hạn
hành vi/counters nêu trên. `release/1.0-beta` được normal push và kiểm tra đồng bộ
lại sau commit tài liệu; final SHA/remote/ahead-behind được ghi trong final report.

**Tag = NOT AUTHORIZED. Publication = NOT AUTHORIZED. STOP.**
