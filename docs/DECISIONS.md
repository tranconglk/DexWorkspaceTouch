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
