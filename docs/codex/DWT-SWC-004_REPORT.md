# DWT-SWC-004 — Báo cáo review kiến trúc và hành vi

Ngày: 2026-10-08. Worktree: `.worktrees/dwt-swc-004`. Branch: `feature/dwt-swc-004-auto-repair-orchestration`.
Baseline: `766c8e013306c34f53b22ad1b4e51ce11602c91a` (`release/1.0-beta`).

**DWT-SWC-004 = PASS — READY FOR ARCHITECTURE/BEHAVIOR REVIEW**

Implementation, automated GREEN và bounded qualification S22/S23/Note 9 đã hoàn tất. Dừng trước commit theo review gate của task.

## Nguyên nhân và ownership

Trước thay đổi, factory của `CarFloatingDockCoordinator` tạo `AndroidExistingWorkspaceRepair` với display provider của Dock. `hide()`, trạng thái overlay khác Shown và `dispose()` lần lượt invalidate/dispose Repair controller. Library gửi callback Classic qua Dock; shortcut gửi callback riêng trong platform. Vì vậy lifecycle/display của Dock tham gia cả eligibility lẫn lifetime của Auto.

Luồng cũ: Library/Car/shortcut → callback qua Dock hoặc platform → controller được factory Dock tạo → session assessment/Auto → `ExistingWorkspaceRepair`. Dock ẩn/hủy có thể làm mất context hoặc hủy công việc.

Luồng mới: Library/Car/shortcut → `PostClassicWorkspaceLaunchRuntime` dùng chung → Classic start và accepted Success với display snapshot hợp lệ → controller thuộc Application/process scope → `WorkspaceRepairSession` → `ExistingWorkspaceRepair`. Runtime Activity và runtime target-display đều dùng boundary này. Dock chỉ hiển thị state và nhận explicit Manual click; show/hide/dispose không invalidate/dispose controller. Không thêm service, IPC, recovery journal hoặc nút Repair mới trong Library.

Auto chỉ nhận Success gắn với một Classic start của DWT và cùng repair geometry. Partial, Failure, cancellation hoặc display thay đổi không trigger Auto. Snapshot của attempt hiện tại vẫn được chuẩn bị cho Manual; explicit Dock Manual lấy display hiện tại của Dock, tránh sử dụng display cũ.

## Preference, migration và capability

- API hiện tại là `StateFlow<Boolean>` / `setAutoRepairEnabled(Boolean)`; default false.
- Tái sử dụng SharedPreferences `car_workspace_shortcuts`, key mới `autoRepairEnabled`; lưu `"true"`/`"false"` theo storage interface String hiện có. Không đổi Room/schema.
- Khi key mới chưa có: chỉ legacy `workspaceRepairMode=AUTOMATIC` chuyển true; SUGGEST, OFF, thiếu hoặc hỏng chuyển false và được ghi lại. Key mới luôn ưu tiên; giá trị không hợp lệ hoặc kiểu khác String fail safe false.
- Enum cũ chỉ giữ để đọc migration; không còn radio/policy Suggest hoạt động. Shortcut và visible-slot count giữ nguyên.
- Capability là `READY`, `PERMISSION_MISSING`, `NOT_RUNNING`, `UNAVAILABLE`, lấy từ `ProductionShizukuCommandTransport.binding.availability()` và trạng thái closed của cùng transport. Không bind/execute command để đọc capability; không định nghĩa readiness thứ hai. Android binding vẫn kiểm tra binder, permission và UID shell 2000.
- UI có Switch “Tự động sửa bố cục Workspace” và runtime message Shizuku riêng. Capability không được persist vào preference, không tự bật/tắt Auto. Refresh lúc mở/resume UI chỉ cập nhật state hiển thị.

## Bằng chứng hành vi

| Yêu cầu | Bằng chứng |
|---|---|
| ON + READY + Success + REPAIR_AVAILABLE → một run | `WorkspaceRepairPolicyTest.automaticWrongLayoutInvokesSameEngineOnceAndRetainsTaskIdentity`; smoke S22 |
| LAYOUT_CORRECT → không run/resize | `automaticCorrectLayoutOnlyReadsOnceAndDoesNotInvokeRepair`; smoke S23 report=null |
| OFF → không assessment/Auto, Manual vẫn chạy | `offDoesNotAssessAndManualRepairStillUsesExistingEngine`; smoke S22 OFF rồi native Dock Manual |
| Dock chưa show/show/ẩn/dispose tương đương | `CarFloatingDockCoordinatorTest.automaticLaunchRepairsEquallyWithDockNeverShownShownHiddenOrDisposed`; smoke Dock đóng trên S22/S23/Note 9 |
| Capability unavailable → safe skip | `unavailableAtTriggerSkipsTransportAndLaterReadyDoesNotRepair`, `permissionLostDuringStabilizationSkipsAssessmentWithoutRetry`, transport tests: không bind/execute |
| Manual unavailable → typed state, không mutation | Cùng unavailable policy test kiểm tra cả Manual và ba failure capability |
| Readiness tới muộn/resume không sửa tự phát | Test late READY không assessment/run, kể cả completion gửi lại; production resume chỉ refresh UI, không gọi Classic completion/Repair |
| Partial/unresolved/unavailable → không Auto run | `unresolvedOrUnavailableNeverInvokeAutomaticRepair`; boundary test Partial/Failure/cancellation |
| Một launch không có attempt thứ hai | Generation + started/completed generation; second-launch/context/control tests; không readiness listener hay retry loop |
| Entry points thống nhất | `PostClassicWorkspaceLaunchRuntimeTest.libraryViewModelAndCarShortcutPlatformUseSamePostSuccessBoundary` dùng ViewModel/Car platform thực; wiring Activity/target-display dùng cùng decorator |
| Preference đổi trong Manual không làm mất kết quả | `changingAutoPreferenceDuringManualRepairPreservesManualResult` |
| ON trước accepted Success được áp dụng | `preferenceAtAcceptedSuccessControlsAutoEvenWhenChangedDuringClassicLaunch` |
| Host-window diagnostic đổi không bỏ nhầm Auto | `hostWindowChangesDoNotDiscardSuccessOnSameRepairGeometry`; display geometry thật đổi vẫn safe skip |

Automatic không gọi permission request, mở Shizuku/settings/app thiếu, hoặc phát Toast kết quả Manual. Assessment một lần sau stabilization; chỉ REPAIR_AVAILABLE mới gọi run một lần. Cancellation, gate/control/context checks vẫn fail closed. Manual không bật Auto. Chỉ `ExistingWorkspaceRepair` có authority mutation của product.

## Verification tự động và review

- RED trước implementation: default/migration, active Suggest và Dock lifecycle; RED bổ sung cho generation và ba phát hiện behavioral trong review. Compilation failure không được tính là behavioral RED.
- `:app:testDebugUnitTest`: **PASS 1.384 test / 168 suite, 0 failure, 0 error, 0 skipped**. XML cuối tại `release-output/swc004/unit-results`; không đổi production sau kết quả này.
- Targeted cuối: **PASS 64 test** (Dock 10, Policy 18, Session 10, Preferences 6, ExistingWorkspaceRepair 14, PostClassic boundary 6). Production transport suite 19 test nằm trong full regression.
- Migration/preferences suite: **PASS 6 test**, gồm legacy AUTOMATIC/SUGGEST/OFF, absent/corrupt, key mới ưu tiên, persistence/recreation/refresh và shortcut không thay đổi.
- S22 SharedPreferences instrumentation: **PASS 2 test** trong APK namespace cách ly.
- `assembleDebug` + `assembleDebugAndroidTest`: **PASS**. APK instrumentation cuối rebuilt sau các sửa harness Android 10 và case wrong-layout Note 9; không thay production. Kết quả S22/S23 được giữ vì các sửa cuối chỉ ảnh hưởng nhánh Note 9/Android 10.
- Review độc lập phát hiện bốn vấn đề Important: Manual display stale/thiếu sau attempt không Success; preference đổi trong Manual làm mất result; ON giữa Classic start/Success bị mất identity; so sánh toàn Activity snapshot quá chặt. Đã sửa trong scope và thêm regression. Không còn blocker đã biết từ review này.
- `git diff --check`: **PASS**, kiểm tra cuối sau khi chốt tài liệu.

## Qualification thiết bị

APK debug cách ly `com.trancong.dexworkspacetouch.swc004`; override appId chỉ ở `verification/dwt-swc-004/isolated-app.init.gradle`, không sửa production Gradle/version/signing. Harness dùng production runtime/controller/transport/parser/engine. Cấp permission cho APK test là explicit setup trước Classic, không thuộc Auto pipeline.

| Thiết bị | Kết quả và bounds [left,top,right,bottom] |
|---|---|
| S22 / Android 16, display 25 | **PASS** Auto ON, Dock Hidden, 25/25/50: 3/3 exact, 2 REPAIRED + 1 CORRECT. `[8,8,478,1016]`, `[482,8,958,1016]`, `[962,8,1912,1016]`; task 11526/11527/11528 retained. Auto OFF launch: không assessment/report; deliberately wrong fixture rồi native Dock Repair: 3 REPAIRED, task 11529/11530/11531 retained, Auto vẫn OFF. Unrelated same-identity tasks không đổi bounds/display. |
| S23 / Android 16, display 8 | **PASS** Auto ON, Dock Hidden, Calculator/Clock 50/50, `LAYOUT_CORRECT`, report=null (không run). `[8,8,958,1136]`, `[962,8,1912,1136]`; task 5681/5682. Unrelated same-identity tasks không đổi bounds/display. |
| Note 9 / Android 10, display 2 | **PASS** Auto ON, Dock Hidden, Chrome/Calculator/TikTok ba cột. Case Classic đúng sẵn: `LAYOUT_CORRECT`, không run. Case mutation: explicit harness đặt riêng Chrome mới về `[8,8,958,1020]` trước assessment; Auto thật trả `REPAIR_AVAILABLE` → 1 REPAIRED + 2 CORRECT, final 3/3 exact: `[8,8,638,1020]`, `[642,8,1278,1020]`, `[1282,8,1912,1020]`. Task 3410/3411/3412 và exact task/activity identity retained; gutter 4 px, outer left 8 px. Unrelated same-identity tasks không đổi bounds/display. TikTok đúng fixture accepted: `com.ss.android.ugc.trill/com.ss.android.ugc.aweme.splash.SplashActivity`. |

Raw dumps/state/restore ở `release-output/swc004/{s22,s23,note9}/files/swc004-evidence`; case Note 9 đúng sẵn lưu riêng `note9-correct`. Log smoke/build/RED/GREEN và XML ở cùng evidence root (ignored, không stage). Hai attempt S22 đầu không được tính PASS: harness đọc state quá sớm; attempt tiếp có duplicate fixture tasks nên engine fail closed. Giữ log thất bại; sửa harness chờ terminal và đóng đúng cửa sổ mới của attempt trước, không nới parser/correlation.

Note 9 có hai lỗi harness trước launch (API `windowsOnAllDisplays` chỉ từ Android 11 và package TikTok vùng khác); đã sửa nhánh SDK < 30 và dùng package fixture accepted. Một attempt thực tiếp theo trả transport `TIMEOUT`, assessment UNAVAILABLE/report=null, không tự retry/mutation. Nguyên nhân timeout ở bind hay command chưa định vị; không tăng deadline hoặc thay policy. Đóng riêng task mới theo exact token/single-task stack, rồi một launch smoke mới PASS; case wrong-layout riêng tiếp theo cũng PASS. Log thất bại giữ nguyên `swc004-note9-attempt1/2/3.log`, evidence timeout tại `note9-attempt3`. Đây là quan sát vận hành cần lưu khi review, không được suy diễn thành Shizuku NOT_RUNNING.

Harness khôi phục Auto ban đầu (false ở cả ba APK test), ẩn Dock, xóa workspace test và phục hồi bounds của fixture pre-existing chỉ khi exact identity vẫn khớp. Đã đóng riêng các task smoke mới trên Note 9, xác nhận không còn active smoke tasks sau asynchronous removal. Task S22/S23 còn trong lịch sử nhưng `visible=false/visibleRequested=false` sau chuyển DeX; không xóa Desk root chung hay task khác để dọn lịch sử. APK qualification và quyền test vẫn được giữ cho review. Không clear production app data, pairing hoặc daemon Shizuku.

Unavailable-Shizuku trên thiết bị thật: **NOT VERIFIED**; không thay đổi setup Shizuku của người dùng để tái tạo. Safe skip/typed state/không bind/execute đã được chứng minh bằng unit tests; không suy diễn thành device PASS. UI end-to-end của từng entry point và `ProductUiWithoutEmbeddedTest`: **NOT VERIFIED** trong phiên này; entry-point contract được kiểm tra tại shared orchestration boundary theo task. Signed release APK: **NOT VERIFIED**, ngoài scope.

## Production files thay đổi chính xác

Tất cả đường dẫn dưới `app/src/main/java/com/trancong/dexworkspacetouch/`:

1. `DexWorkspaceTouchApplication.kt` — process ownership và wiring controller dùng chung.
2. `feature/car/CarScreen.kt` — Switch Boolean và capability message.
3. `feature/car/CarWorkspaceShortcut.kt` — Boolean preference API.
4. `feature/car/CarWorkspaceShortcutPreferences.kt` — migration/persistence fail safe.
5. `feature/car/WorkspaceRepairMode.kt` — đánh dấu enum legacy-only.
6. `feature/car/overlay/CarDockRepairControl.kt` — typed Shizuku state.
7. `feature/car/overlay/CarFloatingDockCoordinator.kt` — bỏ Auto lifetime theo Dock, giữ explicit Manual/display hiện tại.
8. `feature/car/overlay/WorkspaceRepairSession.kt` — Boolean/capability admission, one attempt và generation safety.
9. `navigation/TouchNavigation.kt` — shared runtime wiring, preference/capability UI riêng.
10. `platform/launch/android/AndroidWorkspaceLaunchRuntime.kt` — Activity entry point decorator.
11. `platform/launch/android/DisplayTargetWorkspaceLaunchRuntime.kt` — Car/Dock target-display decorator.
12. `platform/launch/shizuku/AndroidExistingWorkspaceRepair.kt` — process context, accepted launch snapshot, Manual display, observers độc lập Dock.
13. `platform/launch/shizuku/AndroidShizukuCommandTransport.kt` — expose runtime capability.
14. `platform/launch/shizuku/ShizukuCommandTransport.kt` — reuse admission availability để đọc capability.
15. `platform/launch/shizuku/ShizukuRuntimeState.kt` — **mới**, capability enum/mapping/message.
16. `workspace/launcher/PostClassicWorkspaceLaunchRuntime.kt` — **mới**, shared Success boundary và repair geometry comparator.

## Invariant và Git/release

Engine, Android 10 TaskRecord parser, exact component/user/display/task/activity correlation, ambiguity fail closed, pre-resize revalidation, stable readback và uncertain-mutation policy giữ nguyên; suites tương ứng PASS trong full regression. Chỉ task có sẵn; không package fallback, không mở app thiếu, không retry sau uncertain mutation.

Không đổi margin 8 px/gutter 4 px, Classic launch semantics, command whitelist (`dumpsys activity activities`, `am task resize`), UserService protocol, ProductRunGate/CLEANUP_BLOCKED, Embedded runtime/routes/product UI absence, license/crypto/device binding/signing, backend, Room, dependency hoặc version. Baseline beta.10 / code 11 vẫn là qualification lịch sử chưa publish.

Original checkout và thay đổi riêng của người dùng được giữ nguyên. Feature HEAD chưa có commit mới; `release/1.0-beta` vẫn baseline. Không stage/commit/push/tag/publish.

**Publication = HOLD · Tag = NOT AUTHORIZED · Commit = NOT YET**

**STOP trước commit.** Qualification và report đã hoàn tất; không thực hiện bước Git/release tiếp theo trong task này.

## Checkpoint ủy quyền commit — 2026-10-08

Các trạng thái READY FOR REVIEW, Commit NOT YET và STOP trước commit ở trên ghi nhận checkpoint lịch sử trước ủy quyền. Người dùng đã chấp nhận architecture/behavior review: CRITICAL = 0, IMPORTANT = 0, MINOR = 0; PASS FOR COMMIT.

Pre-commit đã đối chiếu HEAD với baseline, xác nhận danh sách thay đổi khớp chính xác 16 production files ở trên và production không đổi sau qualification. Thời điểm sửa production cuối là `2026-10-08T13:19:27.1495041Z`, trước full regression hoàn tất `2026-10-08T13:21:40.4609293Z`; hash hai APK khớp evidence index. Kiểm tra fingerprint đầu vào bằng `:app:compileDebugKotlin --offline` với init script namespace cách ly trả BUILD SUCCESSFUL và compileDebugKotlin UP-TO-DATE. Không chạy lại các test/device đã PASS.

XML được đối chiếu lại: 1.384 test / 168 suite, 0 failure/error/skipped; sáu suite targeted có tổng 64 test PASS. State evidence S22 Auto và OFF/Manual, S23 correct/no mutation, Note 9 wrong-layout Auto khớp kết quả qualification đã chấp nhận. Giới hạn NOT VERIFIED nêu trên vẫn có hiệu lực.

Commit scope gồm **30 tệp: 16 production, 10 test, 1 harness namespace cách ly, 3 docs/evidence**. `verification/dwt-swc-004/COMMIT_MANIFEST.json` lưu danh mục chính xác, SHA-256 của 29 tệp payload còn lại và digest của evidence đã kiểm tra; manifest không tự hash chính nó. Init script thuộc harness DWT-SWC-004 và chỉ đổi applicationId của APK qualification. Không đưa APK/build outputs, raw device dumps/log bundles, private data, secrets/tokens hoặc harness không liên quan vào commit.

Các invariant được đối chiếu với baseline/scoped diff: outer 8 px / internal 4 px, Android 10 parser, exact task/activity correlation, Classic launch semantics, ProductRunGate/CLEANUP_BLOCKED, uncertain mutation policy, Shizuku whitelist, UserService protocol, Embedded UI/runtime, Room/schema, licensing/signing/backend, version `1.0.0-beta.10` / code `11` giữ nguyên. ExistingWorkspaceRepair vẫn là mutation authority duy nhất. Auto Boolean/migration, Dock-independent Auto, Manual khi Auto OFF và safe skip khi Shizuku unavailable giữ đúng behavior đã chấp nhận.

Ủy quyền chỉ tạo đúng một commit: `DWT-SWC-004: decouple automatic repair from dock lifecycle`. Không amend/push/integrate release/bump version/tag/publish. `release/1.0-beta` giữ baseline; Publication = HOLD; Tag = NOT AUTHORIZED. Sau commit và kiểm tra worktree/index sạch: STOP.
