# DexWorkspaceTouch UX Guidelines

## Touch-first

Mọi luồng chính phải hoàn thành được bằng cảm ứng. Primary action dùng target 64dp; action phụ ưu tiên 56dp và không nhỏ hơn 48dp.

## Context-first

Đặt hành động gần đối tượng đang thao tác. Cell action xuất hiện trong selected cell; divider action xuất hiện trên selected divider. Không chuyển primary contextual action vào menu ba chấm.

## Fit-to-window

Workspace luôn giữ tỷ lệ 16:10 và được contain trong vùng còn lại sau system insets, top content, bottom bar và screen padding. Resize cửa sổ chỉ thay geometry render, không reset canvas, selection hay history.

## Direct manipulation

Người dùng chọn trực tiếp cell hoặc divider trên canvas. Visual selection phải rõ, nhưng không thay đổi kích thước domain hoặc gây layout shift.

## Gesture alternative

Mọi gesture nâng cao phải có action chạm tương đương. Ví dụ thay đổi divider có nút giảm, tăng và đặt lại; drag không được là con đường duy nhất.

## Safe touch

Phần tử nhìn thấy và vùng chạm là hai khái niệm riêng. Divider 3dp dùng hit area 64dp. Khi các vùng chạm giao nhau, z-layer và pointer consumption phải ưu tiên contextual action đang hiển thị.

## Interaction Zones

- Giữ action cách cạnh ít nhất `SafeActionInset` khi không gian cho phép.
- Dùng `DeadZone` để giảm chạm nhầm giữa nội dung và gesture target.
- Action overlay phải nằm trên divider trong z-order.
- Không làm mất hit area divider bên ngoài vùng action.
- Với cell nhỏ, dùng một affordance lớn “Thao tác” và chuyển action đầy đủ vào bottom sheet.

## Template selection

Template Picker dùng dialog lớn căn giữa, không dính đáy cửa sổ. Màn DeX lớn dùng
đúng 3 cột, cửa sổ trung bình 2 và cửa sổ hẹp 1 cột. Card giữ gọn nhưng toàn bộ card
vẫn là touch target. Header luôn nhìn thấy; chỉ danh sách section cuộn. Safe-drawing
inset cộng margin ngoài bảo đảm hàng cuối, shape và shadow không bị taskbar/cạnh cắt.

Template hiển thị theo thứ tự Cơ bản, Chia trái / phải, Chia trên / dưới; không dùng tab
hoặc nested scroll. Hai nhóm đầu mở mặc định để chọn nhanh, nhóm Trên/Dưới thu gọn.
Header section là một touch target 56dp; card chỉ gồm preview 16:10 và tên.
