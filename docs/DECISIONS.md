# Architecture Decisions

## ADR-001 — Dùng Normalized Bounds

Quyết định:
Dùng tọa độ Float trong khoảng 0f..1f.

Lý do:  
Độc lập với độ phân giải và kích thước cửa sổ.

## ADR-002 — Cell là Composable độc lập

Quyết định:  
Dùng Box/Layout với cell composable thay vì vẽ toàn bộ bằng Canvas.

Lý do:  
Dễ hỗ trợ touch, semantics, animation và trạng thái riêng.

## ADR-003 — Chưa port Launch Engine

Quyết định:  
Hoàn thiện Visual Designer MVP trước khi tích hợp lõi DeX.

Lý do:  
Giảm regression và giữ UI độc lập với platform.

## ADR-004 — Project cũ là reference implementation

DexWorkspaceManager chỉ dùng để tham khảo phần đã được kiểm chứng.

Không phát triển tính năng UX mới ở project cũ.

## ADR-005 — Không quản lý session

Ứng dụng chỉ thiết kế, lưu và mở workspace.

Không theo dõi hoặc quản lý phiên làm việc sau khi launch.

## ADR-006 — Touch-first

Không thiết kế hành động chính phụ thuộc chuột, bàn phím, hover hoặc keyboard shortcut.

## ADR-007 — Không lưu metadata có thể tra cứu lại

Quyết định:
Workspace chỉ lưu identity ứng dụng cần thiết. Label, icon và launchable state
được resolve từ installed-app catalog khi render hoặc thực thi.

Lý do:

- Label có thể thay đổi theo locale.
- Icon có thể thay đổi sau khi ứng dụng update.
- Tránh hai nguồn metadata và dữ liệu trùng lặp.
- Giúp persistence và export sau này gọn hơn.

Migration:
E3-001 giữ `AssignedApp.label` như cầu nối presentation tạm thời để không phá
Designer/Snapshot demo. E3-003 sẽ chuyển UI sang catalog resolution rồi thu gọn
`AssignedApp` còn identity.

## ADR-008 — Launch contract độc lập Android

Quyết định:
Domain mô tả mục tiêu launch bằng `AppIdentity`, normalized bounds và thứ tự cell.
Readiness và kết quả launch là các contract typed, không chứa Android type hoặc exception.

Lý do:

- Có thể kiểm thử hoàn toàn bằng JVM.
- Giữ Launch Engine tách khỏi Workspace Designer và UI.
- Một request áp dụng được cho nhiều kích thước và độ phân giải cửa sổ.
- Android implementation có thể thay đổi mà không làm thay đổi canvas domain.

Hệ quả:
Chuyển bounds sang pixel, chọn display và tạo `Intent` thuộc infrastructure. Cùng một
identity được phép xuất hiện ở nhiều target vì Designer hiện không cấm; Android
implementation sau này có trách nhiệm báo hạn chế về nhiều instance, không được âm thầm
bỏ target trùng.

## ADR-009 — Launch bounds dùng work area có bốn inset

Quyết định:
Launch bounds được tính từ snapshot work area gồm kích thước display và bốn inset
left/top/right/bottom. Calculator thuần Kotlin chuyển `NormalizedBounds` thành pixel bằng
usable rectangle, rounding deterministic và margin platform hướng vào trong.

Lý do:

- DeX taskbar và system bars có thể thay đổi kích thước hoặc cạnh hiển thị.
- Windowed và maximized có thể báo vùng làm việc khác nhau.
- Tránh cửa sổ ứng dụng bị taskbar/system UI che.
- Không phụ thuộc model thiết bị hay magic taskbar height.

Hệ quả:
Foreground launch host phải cung cấp snapshot inset mới trước launch sequence. Calculator
trả typed failure nếu margin hoặc rounding làm vùng không còn kích thước dương. Việc đọc
Android `WindowInsets` và map `PixelBounds` sang `Rect` thuộc infrastructure, không thuộc
canvas hoặc launch contract.

## ADR-010 — Work area lấy từ foreground external-display host

Quyết định:
Android work-area provider nhận foreground Activity đang gắn với external display và đọc lại
display, WindowMetrics, WindowInsets cùng density mỗi lần tạo snapshot. Provider không tự tìm
hoặc chuyển sang display khác.

Lý do:

- Dialog, diagnostics và launch sau này dùng cùng display host.
- Tránh Application Context không đại diện đúng external-display resources/insets.
- Ownership Activity và lifecycle rõ ràng, không cần singleton cache.
- Insets/display có thể thay đổi giữa hai lần launch nên không cache snapshot.
- Maximum WindowMetrics tránh giới hạn workspace theo host window khi Activity đang windowed.

Hệ quả:
Activity chưa attached, đang kết thúc, display default/OFF/removed hoặc insets chưa sẵn sàng
đều trả unavailable thay vì đoán. API 28–29 vẫn cần device verification vì public API không có
maximum WindowMetrics. `DisplayWorkAreaSnapshot.displayId` chỉ thuộc platform adapter và không
được đưa vào domain launch request.

## ADR-012 — Launch tuần tự và partial success

Quyết định:

- Launch target tuần tự theo `AppLaunchTarget.order`; identity trùng không bị loại.
- Delay giữa target được cấu hình qua `LaunchSequencingPolicy`, mặc định 400 ms.
- Lỗi riêng của app tiếp tục sequence để cho phép partial success.
- `DISPLAY_UNAVAILABLE` dừng sequence; target hiện tại và mọi target còn lại đều
  nhận typed failure này.
- `CancellationException` luôn rethrow; không map sang `UNKNOWN` hoặc trả partial result.

Lý do:
Launch tuần tự giữ hành vi tương thích với implementation đã kiểm chứng và cho
phép báo cáo chính xác từng target. Display loss là failure cấp host nên không
còn cơ sở tin cậy để launch tiếp. Structured cancellation phải được bảo toàn.

Hệ quả:
400 ms chỉ là compatibility default, chưa phải giá trị tối ưu; cần device sweep sau
integration. Caller bị cancel sau khi một số target đã launch sẽ không nhận
`WorkspaceLaunchResult`; coroutine vẫn bị cancel đúng contract.

## ADR-011 — Legacy DeX work-area reference

Quyết định:
API 28–29 được phép tái sử dụng trusted full-display work area trong RAM khi freeform host trên
cùng external display không cung cấp display-space insets. Direct candidate luôn được ưu tiên;
reference chỉ là fallback.

Lý do:

- Samsung DeX cũ không luôn expose taskbar qua root insets trong freeform window.
- Work area của desktop không phụ thuộc kích thước host window.
- Tránh hard-code taskbar height, device model hoặc fallback rectangle.

Reference chỉ được auto-capture từ `ROOT_SYSTEM_INSETS`, `ROOT_STABLE_INSETS` hoặc
`DISPLAY_METRICS_DELTA` đã accepted. Key gồm display ID, real dimensions và density. Display
disconnect, ID/resolution đổi hoặc density đổi đáng kể làm reference bị xóa hoặc từ chối.

Giới hạn:

- Reference chỉ tồn tại trong RAM và không sống qua process death.
- Lần đầu process mở ở windowed có thể chưa có trusted candidate. Provider trả unavailable và UI
  hướng dẫn phóng to ứng dụng một lần; không tự ép fullscreen.
- API 30+ không capture hoặc resolve legacy reference.

## ADR-013 — Launch từ Workspace Library qua typed readiness/result

Quyết định:

- `WorkspaceLaunchRequestFactory` luôn chạy trước Android infrastructure launch.
- UI chỉ gửi `WorkspaceLibraryItem` và render typed state; UI không tạo `Intent` hoặc `Rect`.
- Toàn ứng dụng chỉ có một launch sequence tại một thời điểm.
- Activity-scoped runtime không được lưu trong ViewModel. Activity dispose sẽ cancel sequence
  đang dùng host cũ thay vì giữ Activity đã destroy.
- API 28–29 không có direct/trusted work area hiển thị hướng dẫn phóng to một lần;
  không force fullscreen và không dùng model thiết bị.
- Raw Android exception và technical message không đi ra UI.

Hệ quả:
Launch state và legacy reference RAM sống qua resize/configuration change trong
`WorkspaceLaunchViewModel`, nhưng launcher luôn được tạo lại từ foreground Activity. UI có
trạng thái checking, launching, completed, readiness error, environment error và cancelled rõ ràng.

## ADR-014 — Workspace persistence dùng Room với JSON canvas

Quyết định:

- Database version 1 có một bảng `workspaces`.
- `WorkspaceCanvas` được serialize deterministic vào cột `canvasJson`; không tách cell/app
  thành nhiều bảng trong version đầu.
- Domain `Workspace` không có Room/Android annotation và tách biệt với `WorkspaceEntity`.
- `WorkspaceRepository` là boundary; UI không nhận Entity, DAO hoặc Room exception.
- Serializer custom JSON thuần Kotlin giữ field order, cell order, app identity, nullable activity
  và label; schema không hỗ trợ hoặc JSON lỗi trả typed persistence failure.

Lý do:
Cấu trúc một bảng đơn giản cho version đầu, giảm migration complexity và không coupling
UI/domain với Room. Serializer có thể được tái sử dụng cho import/export sau này mà không
đưa Android JSON API vào domain.

Trade-off:
Không tối ưu cho query theo từng cell hoặc package app. Chỉ tách bảng/index khi có nhu cầu
query thật và migration được thiết kế rõ ràng.
