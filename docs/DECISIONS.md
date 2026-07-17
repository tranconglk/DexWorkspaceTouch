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
