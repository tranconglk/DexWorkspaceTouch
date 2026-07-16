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
