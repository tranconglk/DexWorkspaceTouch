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

## Workspace duplication

“Nhân bản” không cần confirmation vì không phá dữ liệu. Card rộng hiển thị action trực
tiếp; card hẹp đặt action trong sheet “Quản lý”. Mọi action cao tối thiểu 56dp và có label
đầy đủ. Trong lúc ghi, action bị disable để chống double tap. Thành công/thất bại được báo
bằng Snackbar; bản sao mới được chọn nhưng không tự mở Designer, scroll hay launch.

## Workspace Library search and sort

Library có toolbar touch-first: cửa sổ rộng đặt search và “Sắp xếp” cùng hàng, cửa sổ hẹp
xếp dọc. Search không tự focus, chỉ tìm theo tên và có action xóa 56dp. Sort dùng bottom
sheet với năm lựa chọn full-width 56dp, selected state rõ ràng. Source rỗng và query không
khớp có empty state riêng; filter không xóa selection theo ID.

## Workspace pin

Pinned workspaces appear first under “Đã ghim”, followed by the regular “Workspace” section.
Pin is an always-visible direct corner action, never an action inside “Quản lý”. Its 56dp touch
target shows a neutral outline bookmark when inactive and a primary filled bookmark when active.
The former text badge is removed; accessibility still announces the action and state. Toggling
needs no confirmation. Search applies to both sections and preserves IDs.

## Direct cell activation

A single tap on an empty or assigned Designer cell selects it and opens App Picker directly.
The structural overlay no longer repeats choose/change-app actions; it contains only horizontal
split, vertical split, and clear selection. Divider and overlay gestures keep higher hit-test
priority than cell content. At five cells, split actions are visibly disabled and announce the
maximum. A compact summary shows current cells, the five-cell limit, and assigned-app count.

## Designer context toolbar

The fixed toolbar above the canvas is the only home for structural cell and divider actions.
Cell content stays unobstructed: tapping it is reserved for selecting/changing its app. No cell
or selected-divider action overlay is rendered. Wide windows keep history, context actions, and
status in one row; narrow windows use separate history, context, and status rows while preserving
56dp targets. Selection itself never creates history.
