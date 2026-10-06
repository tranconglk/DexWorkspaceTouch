# DWT-ARCH-001 — Freeze & Isolate Embedded

Ngày: 2026-10-06 (Asia/Saigon).
Trạng thái: **READY FOR COMMIT** theo SCOPE REVISION của người dùng.
Baseline: `b50dae5361c62d40767cd38c80b63dffa7b7ea9c`.
Branch: `architecture/dwt-arch-001`.
Worktree: `D:\AndroidStudioProjects\DexWorkspaceTouch-ARCH-001`.

## Chính sách và quyết định kiến trúc

Embedded được phân loại **EXPERIMENTAL/FROZEN**: giữ implementation để nghiên cứu, chỉ truy cập có chủ đích; không phải default, không phải fallback bắt buộc hoặc hướng phát triển sản phẩm chính. ARCH-001 không bổ sung capability VDM, recovery, reconcile hoặc Shizuku.

Giữ `EmbeddedProductRunGate` như admission coordinator dùng chung. `CLEANUP_BLOCKED`, ownership Embedded đang tồn tại, lock và mutual exclusion giữa Embedded acquire/Classic dispatch tiếp tục có authority. Không đổi tên gate hoặc package.

Báo cáo [ranh giới ban đầu](DWT_ARCH_001_BOUNDARY_REPORT.md) được giữ như checkpoint của phạm vi trước sửa đổi. SCOPE REVISION đã chấp nhận dependency admission chung, giải quyết lý do STOP đó; không thay đổi invariant để vượt qua STOP.

## Classic và Embedded trước/sau

| Phần | Trước | Sau |
| --- | --- | --- |
| Classic | Action → shared gate → WorkspaceLaunchViewModel → AndroidWorkspaceLaunchRuntime → Classic launcher | Giữ nguyên đường chạy và admission |
| Embedded Library | Nút riêng trên workspace được chọn, nhãn Thử nghiệm | Nút riêng giữ nguyên; nhãn Experimental/Frozen |
| Embedded proof | Mở thủ công menu nhà phát triển, chọn proof | Giữ entry thủ công; cả năm label và màn hình có Experimental/Frozen |
| Product execution | Factory chỉ gọi sau thao tác Start trên route Embedded | Giữ nguyên factory và Start flow |
| Cleanup chưa xác nhận | Chặn cả Classic và Embedded, cho phép quan sát status | Giữ nguyên fail-closed và status |

Source đã có isolation thực thi cần thiết: callback Classic không tạo Embedded runner/session/renderer/Surface/VDM. `EmbeddedWorkspaceProductScreen` chứa `createProductRouteExecution`; observation content gọi factory từ thao tác Start. Effect chờ renderer ready chỉ tiếp tục thao tác Start đã được người dùng yêu cầu. Các proof Waze/Calculator/Dual App vẫn chỉ dựng graph khi truy cập route có chủ đích; remote session launch nằm ở nút Start. Không tự chuyển từ Classic sang Embedded khi môi trường hoặc launch thất bại.

## Dependency được cắt và cố ý giữ

- **Không cắt dependency mới, không invent refactor:** các dependency runtime đã nằm ngoài đường Classic ở baseline. Bổ sung test bảo vệ ranh giới có sẵn.
- Giữ gate, routing admission, status DTO và cleanup-blocked UI cần cho an toàn. Application gate chỉ giữ projection giá trị; không biến status thành runtime ownership handle.
- Giữ shared repository, workspace canvas/app identity, request factory, launch request và readiness; không thay persistence hoặc thêm execution-mode selector.
- Giữ `WorkspaceLauncher`/`WorkspaceLaunchRuntime` và model hiện có; chúng không yêu cầu Embedded runtime. Chưa thêm admission/launcher cho Shizuku.
- Giữ nguyên diagnostics/evidence Embedded và code ownership/result authority.

## Thay đổi source/test

Tám source file chỉ thay chuỗi UI; không thay control flow:

- `app/src/main/java/com/trancong/dexworkspacetouch/ui/screens/HomeScreen.kt`
- `app/src/main/java/com/trancong/dexworkspacetouch/workspace/library/ui/WorkspaceLibraryCard.kt`
- `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/product/EmbeddedWorkspaceProductScreen.kt`
- `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/EmbeddedWorkspaceRunnerScreen.kt`
- `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/EmbeddedWorkspaceLayoutScreen.kt`
- `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedwaze/EmbeddedWazeScreen.kt`
- `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedcalculator/EmbeddedCalculatorScreen.kt`
- `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddeddual/EmbeddedDualAppScreen.kt`

Ba test file:

- Thêm `feature/embeddedworkspace/product/ClassicWorkspaceAdmissionTest.kt`: 5 test chạy real routing/gate/request factory/Classic ViewModel, thay boundary Android bằng recording runtime. Kiểm tra request workspace chính xác, admission, launch failure/environment failure không fallback, cleanup-blocked không chạy readiness/runtime, và entry Embedded chỉ điều hướng.
- Thêm `feature/embeddedworkspace/product/ClassicWorkspaceRuntimeIsolationTest.kt`: kiểm tra dependency bytecode lớp Classic, gồm factory/coroutine, không tham chiếu Embedded execution packages, Surface hoặc VirtualDisplay. Đọc cả layout thư mục hoặc JAR; kiểm tra các lớp bắt buộc có mặt để không PASS vì tập rỗng.
- Bổ sung `feature/embeddedworkspace/product/EmbeddedProofAccessTest.kt`: cả năm control chỉ xuất hiện sau mở menu có chủ đích và có label Experimental/Frozen. Assertions authority/fail-closed cũ giữ nguyên.

Test paths ở trên nằm dưới `app/src/test/java/com/trancong/dexworkspacetouch/`.

## Verification

- Baseline targeted: `EmbeddedProductRunGateTest`, `EmbeddedWorkspaceProductRoutingTest`, `WorkspaceLaunchViewModelTest`: **PASS**.
- RED: 11 test mới/đích, 6 test Classic/isolation PASS, 5 test label FAIL đúng vì thiếu Experimental hoặc Frozen; không có compile failure.
- GREEN/affected regression: `:app:testDebugUnitTest` với các vùng `feature.embeddedworkspace.*`, `feature.embeddedapp.*`, `workspace.execution.embedded.*`, `workspace.launcher.*`, và `AndroidWorkspaceLauncherTest`: **PASS — 46 suites, 483 tests, 0 failures, 0 errors, 0 skipped**.
- `:app:assembleDebug`: **PASS**; APK tại `app/build/outputs/apk/debug/app-debug.apk`.
- `git diff --check`: **PASS**.
- Scope audit đối chiếu baseline: **PASS — tất cả 8 source file chỉ đổi string literals**. Navigation, Application, gate, routing, persistence, AIDL và schema không có diff.
- Tự review diff source/test theo phạm vi sửa đổi: không phát hiện blocker.

Một số kết quả quan trọng trong GREEN:

| Test class | Tests | Failures |
| --- | ---: | ---: |
| EmbeddedProductRunGateTest | 16 | 0 |
| EmbeddedWorkspaceProductRoutingTest | 3 | 0 |
| EmbeddedWorkspaceProductControllerTest | 24 | 0 |
| EmbeddedProductLifecycleTest | 22 | 0 |
| EmbeddedHostLifecycleQualificationTest | 6 | 0 |
| EmbeddedProofAccessTest | 70 | 0 |
| EmbeddedWorkspaceRunnerTest | 19 | 0 |
| ClassicWorkspaceAdmissionTest | 5 | 0 |
| ClassicWorkspaceRuntimeIsolationTest | 1 | 0 |

## PASS criteria

| Tiêu chí | Kết quả/bằng chứng |
| --- | --- |
| Incomplete cleanup vẫn chặn hai mode | PASS — gate/routing/cleanup/lifecycle tests |
| Classic dispatch và Embedded acquire không chồng lấn | PASS — gate concurrency test và Classic admission integration |
| Classic launch vẫn qua admission an toàn | PASS — real gate → request factory → Classic ViewModel integration |
| Classic không dựng/gọi Embedded execution machinery | PASS — compiled dependency guard + khảo sát navigation/factory giữ nguyên |
| Embedded entry/manual Start có chủ đích | PASS — proof access/lifecycle tests và source Start/factory boundary |
| Không tự fallback Classic → Embedded | PASS — launch/environment failure integration và core bytecode isolation |
| Experimental/Frozen presentation | PASS — 5 menu label tests, review copy toàn bộ entry/title, debug compile/build |
| Existing Embedded authority/gate tests | PASS — affected regression 483 test |
| Không đổi dữ liệu workspace ngoài ý muốn | PASS — source chỉ đổi copy; persistence/schema không có diff |

**NOT VERIFIED:** chạy trên thiết bị và render UI thực tế, release build/signing. Không thực hiện device qualification campaign; các bằng chứng PASS ở trên là unit/integration test, kiểm tra dependency compiled và review source, không phải chứng nhận thiết bị.

## Giữ nguyên và Git

Không thay schema, protocol/AIDL, ProductExecutionValue, gate/ownership/result authority, cleanup semantics, license/security, dependency/toolchain, version hoặc release configuration. Embedded implementation **không bị xóa**. Shizuku **không được triển khai mới**.

Không chỉnh sửa original dirty worktree. Chưa commit/push/tag. Dừng tại READY FOR COMMIT; các test PASS vẫn có hiệu lực sau khi thêm báo cáo này vì không đổi source/test thêm.