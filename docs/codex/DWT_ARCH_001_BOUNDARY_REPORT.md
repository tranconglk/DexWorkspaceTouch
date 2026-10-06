# DWT-ARCH-001 — báo cáo ranh giới kiến trúc

Ngày: 2026-10-06 (Asia/Saigon).
Trạng thái: **STOP — architecture/authority boundary**. Chưa READY FOR COMMIT.

## Phạm vi và Git

- Baseline: `b50dae5361c62d40767cd38c80b63dffa7b7ea9c`.
- Branch: `architecture/dwt-arch-001`.
- Worktree riêng: `D:\AndroidStudioProjects\DexWorkspaceTouch-ARCH-001`; sạch trước khảo sát.
- Không chỉnh sửa file trong original dirty worktree; không commit/push/tag.
- Mục tiêu: đóng băng Embedded, cắt dependency thực thi khỏi Classic; giữ nguyên authority, cleanup và fail-closed đã chấp nhận.

## Ranh giới STOP và bằng chứng

Classic trong Library hiện chạy theo chuỗi:

`HomeScreen → EmbeddedWorkspaceProductRouting.openClassic → EmbeddedProductRunGate.tryDispatchClassic → WorkspaceLaunchViewModel → AndroidWorkspaceLaunchRuntime → Classic launcher`.

- `TouchNavigation.kt:626–632`: callback Classic đi qua product routing; nút chạy chỉ được bật ở `IDLE`.
- `EmbeddedWorkspaceProductRouting.kt:7`: `openClassic` gọi `gate.tryDispatchClassic`.
- `EmbeddedProductRunGate.kt:225–235`: dispatch bị từ chối khi còn token, phase khác `IDLE`, hoặc đang dispatch Classic; lock và `classicDispatching` bảo vệ admission đồng thời.
- `HomeScreen.kt:821–822,858–860`: Classic và Embedded cùng phụ thuộc `workspaceRunActionsEnabled`.

Đây là invariant được bảo vệ bởi test đã nằm trong baseline được chấp nhận:

- `EmbeddedProductRunGateTest.kt:61`: `incompleteCleanupBlocksBothModesAndStaleCallback`.
- `EmbeddedProductRunGateTest.kt:75`: `classicDispatchAndEmbeddedAcquireCannotOverlap`.
- `EmbeddedProductRunGateTest.kt:163`: `everyNonIdlePhaseBlocksClassicAndAnotherEmbeddedRun`, bao gồm `CLEANUP_BLOCKED`.
- `EmbeddedWorkspaceProductRoutingTest.kt:20`: `shizukuStateDoesNotGateEntryButBlockedCleanupDoes`, kiểm tra callback Classic không chạy sau cleanup incomplete.

Cho Classic dispatch trực tiếp hoặc bỏ điều kiện enable sẽ bỏ qua admission đang có authority và làm yếu fail-closed. Bọc gate bằng interface khác vẫn giữ dependency admission này. Theo mục A và IMPORTANT SAFETY BOUNDARY của đặc tả, dừng trước khi sửa implementation; không thay test để cho phép bypass.

## Kết quả khảo sát source trước khi sửa

1. **Entry Embedded:** workspace được chọn có nút `Mở Embedded (Thử nghiệm)` (`WorkspaceLibraryCard.kt:177–182`); callback Library điều hướng product route theo đúng ID (`TouchNavigation.kt:631`). Menu nhà phát triển có Waze, Calculator, Dual App, Runner và Workspace Layout (`HomeScreen.kt:84–95,459–479`), qua proof admission ở `TouchNavigation.kt:186–194`.
2. **Phần shared hợp lý:** repository workspace, canvas/app identity, `WorkspaceLaunchRequestFactory`, `WorkspaceLaunchRequest` và readiness. `EmbeddedWorkspaceLayoutLoader.kt:15–27` đọc workspace rồi chuyển request thành Embedded plan; không ghi persistence. `WorkspaceEntity.kt` lưu dữ liệu workspace và canvas, không có execution-mode selector.
3. **Dependency Classic → Embedded:** Application tạo `EmbeddedProductRunGate` (`DexWorkspaceTouchApplication.kt:53`); navigation tạo product routing và quan sát gate status (`TouchNavigation.kt:178–183`). Home nhận `ProductRunStatus` và chiếu cleanup-blocked UI. Core Classic ViewModel/runtime/launcher được khảo sát không có import Embedded runner/session/VDM hoặc Surface ownership; dependency chính nằm ở admission và UI composition.
4. **Tự chọn/fallback:** trong đường Classic được khảo sát, không thấy tự chuyển sang Embedded. Nút `Mở` gọi Classic riêng; entry Embedded là thao tác riêng. Product execution factory chỉ được gọi sau thao tác Start của người dùng (`EmbeddedWorkspaceProductScreen.kt:168–174,378–383`). `LaunchedEffect` ở dòng 255–258 thực hiện Start khi renderer sẵn sàng sau khi người dùng đã tạo execution, không phải fallback Classic.
5. **Khởi tạo runtime:** runner/session factory/renderer/product controller được tạo trong `createProductRouteExecution` (`EmbeddedWorkspaceProductScreen.kt:186–213`), không trong callback Classic. Classic vẫn khởi tạo/đọc gate; gate lưu projection dữ liệu về phase/session/cleanup nhưng không phải runtime handle.
6. **Phần Embedded-only hiện đi vào product path:** global gate, routing chung, gate status và cleanup-blocked UI/enablement. Các label product/layout/runner đã có `Thử nghiệm` hoặc `Experimental`; Waze/Calculator/Dual App có label riêng trong menu `Nhà phát triển (Thử nghiệm)`. Chưa áp dụng chính sách FROZEN mới.

## Trước/sau và thay đổi

| Nội dung | Trước | Sau checkpoint |
| --- | --- | --- |
| Classic launch | Classic callback qua global Embedded gate | Giữ nguyên vì STOP |
| Embedded entry | Nút riêng trong Library và menu proof | Giữ nguyên vì STOP |
| Dependency được cắt | Chưa có | Không cắt dependency nào |
| Dependency giữ lại | Gate/routing/status; shared workspace data/request | Giữ gate để bảo toàn admission; shared model tiếp tục hợp lý |

- Source files changed: **không có**.
- Test files changed: **không có**.
- Chỉ thêm báo cáo này; historical diagnostics/evidence giữ nguyên.
- Schema/protocol/ownership/result authority/ProductExecutionValue/gate semantics: **không thay đổi**.
- Embedded implementation: **không bị xóa**.
- Shizuku: **không triển khai mới**; các tích hợp Embedded có sẵn giữ nguyên.

## Verification và PASS criteria

- Khảo sát source và assertions của test trong baseline: bằng chứng tĩnh cho STOP, không phải kết quả chạy test.
- Git baseline/branch và không có diff tracked: kiểm tra ở checkpoint bàn giao.
- Tests/build/device launch/Embedded build: **NOT VERIFIED** — không chạy vì đã gặp mandatory STOP trước khi thay đổi implementation.
- Mục tiêu tách hoàn toàn Classic khỏi gate Embedded: **BLOCKED**, chưa đạt PASS.
- Không có behavioral RED mới vì đây là accepted invariant, không được coi là defect trong phạm vi hiện tại.

## Lựa chọn tiếp tục sau quyết định phạm vi

1. Điều chỉnh phạm vi ARCH-001 để chấp nhận gate admission chung như ngoại lệ an toàn; tiếp tục cô lập runtime/UI và đóng băng Embedded nhưng bảo toàn toàn bộ semantics hiện tại.
2. Nếu yêu cầu Classic hoàn toàn độc lập với gate, cần đặc tả riêng được cho phép thay đổi authority/admission khi Embedded còn allocation hoặc cleanup chưa xác nhận, cùng tiêu chí test cho concurrency và fail-closed. Báo cáo này không lựa chọn hoặc triển khai kiến trúc thay thế.