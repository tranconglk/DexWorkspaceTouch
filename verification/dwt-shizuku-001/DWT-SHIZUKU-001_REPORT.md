# DWT-SHIZUKU-001 — PASS

Ngày: 2026-10-07 (Asia/Saigon).

**PASS — PRODUCT INTEGRATION VIABLE / READY FOR COMMIT REVIEW.** Tích hợp nguồn/regression đã PASS; **S22 real product PASS retained** và **S23 real product PASS**. Toàn bộ source/artifact qualification được giữ nguyên trong bước S23; không cần implementation bổ sung, không commit/push/tag.

## Quyết định cuối — S23 product continuation

- Precheck lại package/version/signer: cùng `com.trancong.dexworkspacetouch`, `1.0.0-beta.8 (9)`, certificate chính thức `19ac0ea99125361b3c2083aaa44c8745ebad9c55fa967642c2b9e5a1086a45e7`. Update `adb install -r` PASS, không uninstall/clear data; APK sau cài khớp frozen artifact.
- License/session hiện hữu vượt gate sau startup bình thường; app ID/firstInstallTime giữ nguyên. Hai export Library trước/sau update có **13 workspace cũ giống toàn bộ nội dung**. Fixture thêm qua UI, không sửa shortcut cũ hoặc ghi DB trực tiếp.
- S23: SM-S918B, Android 16 / SDK 36, **One UI 8.5 (`80500`)**, DeX display 6, 1920×1200, usable 1920×1144, density 160 dpi.
- AUTOMATIC / Classic Open qua UI thật → **LAYOUT_CORRECT**. Calculator task `5506`: requested/actual `[8,8,952,1136]`; Clock task `5507`: requested/actual `[968,8,1912,1136]`; cả hai **0 px deviation**.
- Trace 20.181 giây / 60 samples, cả hai task ổn định 17.137 giây: **0 resize, 0 unrelated task mutation, 0 Shizuku app START, 0 Automatic Repair attempt**. Zero attempt được đối chiếu runtime assessment với policy `REPAIR_AVAILABLE` không đổi và accepted zero-attempt regression; không inject RPC counter.
- **36/36 frozen source/test hashes không đổi**, APK hash vẫn `708e4df09c1aea4e232e5655b981ef457b96d3b5d85fb36397fee9e1ccd14b68`. S22 evidence/source/regression/build đã PASS được giữ nguyên; không build hoặc test lại không cần thiết.
- `release/1.0-beta`, version/signing/production guard không đổi; không publish release. Không commit/push/tag. **STOP tại commit review**.
- [Báo cáo S23](s23-product-proof/S23_PRODUCT_QUALIFICATION_REPORT.md), [summary/verifier result](s23-product-proof/s23-qualification-summary.json).

Các mục phía dưới giữ nguyên checkpoint S22/baseline trước khi S23 hoàn tất; trạng thái PARTIAL/deferred trong đó là lịch sử.

## Cập nhật device continuation — 2026-10-07

- Tái sử dụng config licensing và signer DWT hiện có, build qualification cho package thật; production guard giữ nguyên. `assembleRelease` (bao gồm compileReleaseKotlin/signing) PASS nhờ cấu hình hợp lệ tự nhiên sẵn có. Không thay source/build/version/signing config hoặc publish release.
- S22 debug qualifier khác signer được thay sau khi người dùng cho phép rõ ràng. Người dùng nhập đúng license, kích hoạt bình thường; UI sản phẩm và cold startup qua gate: PASS.
- Automatic: Classic sai → automatic ExistingWorkspaceRepair → **3 repaired, 0 correct**, IDs `11362/11363/11364` giữ nguyên.
- Suggest: **Repair available**, zero mutation trước click; click Repair thật → **3 repaired, 0 correct**, IDs `11365/11366/11367` giữ nguyên.
- OFF: không suggestion/auto, bounds giữ nguyên trước click; Manual Repair thật → **3 repaired, 0 correct**, IDs `11368/11369/11370` giữ nguyên. OFF zero-assessment được đối chiếu với early return và accepted zero-command regression; không instrument RPC nội bộ trên APK không debuggable.
- Các trace có **0 unrelated bounds changes / 0 Shizuku app START**. **36/36 frozen source/test hashes không đổi**; giữ hiệu lực regression 188 tests / 19 suites, không chạy lại.
- [Báo cáo S22](s22-product-proof/S22_PRODUCT_QUALIFICATION_REPORT.md), [summary](s22-product-proof/s22-qualification-summary.json), [artifact/licensing](artifact-license-unblock/ARTIFACT_LICENSE_UNBLOCK_REPORT.md).
- S23 được giữ nguyên và hoãn; milestone tổng **PARTIAL**. Không commit/push/tag.

Các mục/bảng phía dưới giữ nguyên checkpoint integration ban đầu trước khi unblock artifact/license; những trạng thái license/release/S22 NOT VERIFIED trong đó là lịch sử.

## Cây làm việc và inventory

- Worktree: `D:/AndroidStudioProjects/DexWorkspaceTouch/.worktrees/dwt-shizuku-001`, detached HEAD tại release baseline `b04288e69a0f8c2057e185b68a1df9ae8fcd0b80`.
- Tham chiếu R&D: `4e8bca37d904ef6f537d10a52403262e5f4e742a`.
- Không merge/cherry-pick chuỗi R&D; không đổi branch chính hoặc chạm thay đổi người dùng đang có.
- [Inventory đầy đủ](../../docs/codex/DWT-SHIZUKU-001_INVENTORY.md): A=15, B=13, C=8, D=469, E=12; hoàn tất trước chỉnh sửa.
- 36 tệp source/test được chọn hoặc thích nghi từ các khái niệm R&D; danh sách cuối báo cáo.

## Kết quả sản phẩm

`Classic -> OFF/SUGGEST/AUTOMATIC -> ExistingWorkspaceRepair -> dedicated Shizuku UserService`.

- Settings “Workspace Repair”: Off / Suggest / Automatic, luôn nối từ CarRoute; thiếu hoặc sai khóa SharedPreferences mặc định SUGGEST.
- Dock có hàng Repair; Manual chỉ xử lý task đang tồn tại, không khởi chạy app bị thiếu.
- Suggest: một assessment có stabilization/deadline sau Classic Success; zero mutation trước click.
- Automatic: chỉ REPAIR_AVAILABLE, gọi cùng `ExistingWorkspaceRepair.run()`; layout đúng zero mutation.
- OFF: không assessment/auto; Manual vẫn dùng được.
- Giữ invalidate khi workspace/display/policy/gate thay đổi; không có vòng polling task nền.
- Thiếu Shizuku/quyền được chuyển thành trạng thái unavailable. Manual có toast và yêu cầu quyền qua thao tác người dùng nếu chưa từ chối; Suggest/Automatic không yêu cầu quyền tự động.
- Callback Repair được cô lập để không chặn Classic hoặc đổi Classic Success thành lỗi.
- UI đã chứng minh ở mức source/build; quan sát UI dock/settings qua luồng sản phẩm có license: **NOT VERIFIED**.

## Tách phụ thuộc và transport

- Tách dữ liệu task, parser, canonical component, identity, tolerance và dumpsys command thành `WorkspaceTaskCorrelation.kt`.
- Interface/result dùng tên `WorkspaceCommandShell`/`WorkspaceCommandResult`.
- Không còn phụ thuộc `ShizukuWorkspacePrototype`, `PrototypeProcessTransport`, `ShizukuPrototypeShell`, `newProcess`, navigation SW-001, isolated applicationId init scripts hoặc verification artifacts trong đường sản phẩm.
- Chỉ có shared admission gate/status từ Embedded; không thêm phụ thuộc runtime/lease/session/ownership Embedded.
- Dedicated UserService/AIDL giữ nguyên contract R&D; whitelist chỉ `dumpsys activity activities` và `am task resize`.
- Deadline, cancellation, Binder death/rebind, chống replay/no mutation retry giữ nguyên. Không arbitrary shell/app-launch.
- Bỏ ghi file dump/evidence swc001/002/003 khỏi controller sản phẩm; report/state vẫn trong StateFlow.

## R&D cố ý không nhập

- `AndroidShizukuWorkspacePrototype.kt`, `PrototypeProcessTransport.kt`, launch/new-task selection/identifier generation của `ShizukuWorkspacePrototype.kt`.
- `PrototypeProcessTransportTest.kt`, `ShizukuWorkspacePrototypeTest.kt`, `ShizukuWorkspacePrototypeDeviceTest.kt`, `ShizukuS22DifferentialDeviceTest.kt`.
- Thay đổi R&D trong `HomeScreen.kt` và route “Mở bằng Shizuku (Experimental / SW-001)” của `TouchNavigation.kt`; Home/Proof baseline giữ nguyên.
- `experiments/dwt-sw-001/isolated-app-id.init.gradle`.
- 469 tệp bằng chứng R&D không được nhập hoặc dùng làm kết quả của task này.
- Bốn harness E ExistingRepair/ProductionTransport/AutoPolicy/Suggestion không được nhập nguyên trạng: chúng có package/fixture/prototype-shell phụ thuộc R&D. Qualification sản phẩm chưa hoàn tất; không dùng testbed .sw001 thay cho product.

## Verification

| Kiểm tra | Kết quả | Bằng chứng |
|---|---|---|
| Source closure / product UI wiring / protected baseline | PASS | [source-check.txt](source-check.txt), [verify-source.ps1](verify-source.ps1) |
| RED: callback Repair phá Classic/dock result | 2 lỗi assertion đúng kỳ vọng | [red-results](red-results/), red.log |
| Regression vùng Repair/preferences/transport/Classic/gate/cleanup/dock | PASS: 188 tests, 19 suites, 0 failures/errors/skips | [unit-summary.json](unit-summary.json), [XML](unit-results/) |
| :app:assembleDebug | PASS | debug-green.log |
| :app:assembleDebugAndroidTest | PASS | debug-green.log |
| :app:compileReleaseKotlin | NOT VERIFIED: packageReleaseResources bị guard chặn do thiếu production URL; không vô hiệu hóa guard | regression-build.log |
| git diff --check | PASS | Exit 0 |
| S22 cài APK product debug đúng package | PASS | [s22-product-install-retry.txt](s22-product-install-retry.txt) |
| S22 adapter SharedPreferences thật | PASS: 2 tests | [s22-preferences-device.txt](s22-preferences-device.txt) |
| S22 startup MainActivity bình thường | PASS tới LicenseGate; cần kích hoạt | [startup XML](s22-product-startup.xml), [startup result](s22-product-startup.txt) |
| S22 Classic sai layout / Automatic sửa 3/3 / Manual / Suggest trước click / OFF | NOT VERIFIED: license gate | startup XML |
| S23 Classic đúng / Automatic zero mutation | NOT VERIFIED: ADB offline, reconnect timeout | [s23-product-install.txt](s23-product-install.txt), [s23-reconnect.txt](s23-reconnect.txt) |

Regression command dùng Gradle 9.4.1 cache hiện có, offline; không đổi wrapper/dependency/toolchain. Các filter: WorkspaceRepair*, ExistingWorkspace*, ShizukuCommandTransport*, WorkspaceCommand*, CarFloatingDockCoordinatorTest, CarWorkspaceLaunchPlatformTest, WorkspaceLaunchViewModelTest, ClassicWorkspaceAdmissionTest, ClassicWorkspaceRuntimeIsolationTest, EmbeddedProductRunGateTest, EmbeddedCleanupBlockedUxTest, EmbeddedWorkspaceProductRoutingTest, CarOverlaySessionTest, CarWorkspaceShortcutPreferencesTest, CarWorkflowExecutionRunnerTest. Các warning deprecation từ harness Embedded hiện có không được sửa.

[Manifest APK và SHA-256](artifact-manifest.json). APK product: `app/build/outputs/apk/debug/app-debug.apk`; applicationId `com.trancong.dexworkspacetouch`, versionCode 9 / versionName 1.0.0-beta.8. Không tạo artifact release đã ký.

Mặc định/round-trip/preserve shortcut của settings được chứng minh bằng unit tests và adapter SharedPreferences thật. Upgrade APK tại chỗ của một cài đặt product đã có license: **NOT VERIFIED**.

## Thiết bị và license/startup qualification

- S22: `192.168.1.183:5555`, SM-S908E, SDK 36, One UI `80000`; [device metadata](s22-device.json).
- Dùng package product debug bình thường; cài app và test APK, chạy riêng prefs adapter test; sau đó mở MainActivity qua launcher intent bình thường.
- Màn hình thực tế: “Kích hoạt bản quyền”, “Cần kích hoạt”, “Ứng dụng chưa được kích hoạt”. Dừng trước luồng workspace vì chưa có cơ chế license/qualification được xác nhận.
- Không inject license, không thay LicenseGate, không bypass startup, không thay URL/trusted key, không dùng applicationId cô lập.
- Prefs test chỉ kiểm tra storage, không chứng nhận Classic/Repair hoặc vượt gate để chạy workspace.
- S23: `192.168.1.106:5555`, SM-S918B. Preflight thấy package version 9 beta.8 không debuggable; lúc cài app, transport ADB offline và reconnect timeout. Chưa kiểm tra được tương thích chữ ký; không gỡ app/dữ liệu.
- Đã yêu cầu thông tin cơ chế license/qualification được chấp nhận. Cần activation/qualification hợp lệ trên S22 và khôi phục ADB S23 để thực hiện proof còn thiếu.

## Ranh giới release

- Baseline commit, build/version/signing config, production URL/keys, licensing, Room/schema và Embedded authority/ProductRunGate/CLEANUP_BLOCKED không đổi.
- Toàn bộ thay đổi task chưa commit, sẵn sàng review trong worktree riêng.
- Không bump version, ký release, publish, tag, push hoặc commit.
- Kết quả milestone: **PARTIAL**. Dừng device qualification tại license/startup boundary; không suy diễn các bằng chứng R&D thành PASS product.

## Tệp source/test cuối cùng

- `app/src/androidTest/java/com/trancong/dexworkspacetouch/feature/car/CarWorkspaceShortcutPreferencesDeviceTest.kt`
- `app/src/main/aidl/com/trancong/dexworkspacetouch/platform/launch/shizuku/IWorkspaceCommandService.aidl`
- `app/src/main/java/com/trancong/dexworkspacetouch/DexWorkspaceTouchApplication.kt`
- `app/src/main/java/com/trancong/dexworkspacetouch/feature/car/CarScreen.kt`
- `app/src/main/java/com/trancong/dexworkspacetouch/feature/car/CarWorkspaceShortcut.kt`
- `app/src/main/java/com/trancong/dexworkspacetouch/feature/car/CarWorkspaceShortcutPreferences.kt`
- `app/src/main/java/com/trancong/dexworkspacetouch/feature/car/WorkspaceRepairMode.kt`
- `app/src/main/java/com/trancong/dexworkspacetouch/feature/car/overlay/AndroidCarOverlayPlatform.kt`
- `app/src/main/java/com/trancong/dexworkspacetouch/feature/car/overlay/CarDockRepairControl.kt`
- `app/src/main/java/com/trancong/dexworkspacetouch/feature/car/overlay/CarFloatingDockCoordinator.kt`
- `app/src/main/java/com/trancong/dexworkspacetouch/feature/car/overlay/CarOverlaySession.kt`
- `app/src/main/java/com/trancong/dexworkspacetouch/feature/car/overlay/WorkspaceRepairSession.kt`
- `app/src/main/java/com/trancong/dexworkspacetouch/feature/car/platform/CarWorkspaceLaunchPlatform.kt`
- `app/src/main/java/com/trancong/dexworkspacetouch/navigation/TouchNavigation.kt`
- `app/src/main/java/com/trancong/dexworkspacetouch/platform/launch/shizuku/AndroidExistingWorkspaceRepair.kt`
- `app/src/main/java/com/trancong/dexworkspacetouch/platform/launch/shizuku/AndroidShizukuCommandTransport.kt`
- `app/src/main/java/com/trancong/dexworkspacetouch/platform/launch/shizuku/ExistingWorkspaceRepair.kt`
- `app/src/main/java/com/trancong/dexworkspacetouch/platform/launch/shizuku/ShizukuCommandTransport.kt`
- `app/src/main/java/com/trancong/dexworkspacetouch/platform/launch/shizuku/WorkspaceCommandDeadline.kt`
- `app/src/main/java/com/trancong/dexworkspacetouch/platform/launch/shizuku/WorkspaceCommandDispatcher.kt`
- `app/src/main/java/com/trancong/dexworkspacetouch/platform/launch/shizuku/WorkspaceCommandProcess.kt`
- `app/src/main/java/com/trancong/dexworkspacetouch/platform/launch/shizuku/WorkspaceCommandUserService.kt`
- `app/src/main/java/com/trancong/dexworkspacetouch/platform/launch/shizuku/WorkspaceTaskCorrelation.kt`
- `app/src/main/java/com/trancong/dexworkspacetouch/workspace/launcher/presentation/WorkspaceLaunchViewModel.kt`
- `app/src/test/java/com/trancong/dexworkspacetouch/feature/car/WorkspaceRepairModePreferencesTest.kt`
- `app/src/test/java/com/trancong/dexworkspacetouch/feature/car/overlay/CarFloatingDockCoordinatorTest.kt`
- `app/src/test/java/com/trancong/dexworkspacetouch/feature/car/overlay/WorkspaceRepairPolicyTest.kt`
- `app/src/test/java/com/trancong/dexworkspacetouch/feature/car/overlay/WorkspaceRepairSessionTest.kt`
- `app/src/test/java/com/trancong/dexworkspacetouch/feature/car/platform/CarWorkspaceLaunchPlatformTest.kt`
- `app/src/test/java/com/trancong/dexworkspacetouch/platform/launch/shizuku/ExistingWorkspaceAssessmentTest.kt`
- `app/src/test/java/com/trancong/dexworkspacetouch/platform/launch/shizuku/ExistingWorkspaceRepairTest.kt`
- `app/src/test/java/com/trancong/dexworkspacetouch/platform/launch/shizuku/ProductionShizukuCommandTransportTest.kt`
- `app/src/test/java/com/trancong/dexworkspacetouch/platform/launch/shizuku/WorkspaceCommandDeadlineTest.kt`
- `app/src/test/java/com/trancong/dexworkspacetouch/platform/launch/shizuku/WorkspaceCommandDispatcherTest.kt`
- `app/src/test/java/com/trancong/dexworkspacetouch/platform/launch/shizuku/WorkspaceCommandProcessTest.kt`
- `app/src/test/java/com/trancong/dexworkspacetouch/workspace/launcher/presentation/WorkspaceLaunchViewModelTest.kt`
