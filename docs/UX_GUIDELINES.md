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
Pinned rows are centered when they do not fill all adaptive columns. Pinned cards retain the same
calculated width as regular cards, while the section header stays left-aligned. The regular workspace
grid remains left-aligned, including its final partial row.
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
### Merge cells

- Use the label “Gộp ô”; do not describe the action as a standalone delete.
- Always present valid targets by direction before changing the layout.
- Confirm when merging would discard the selected source cell's app assignment.
- Tapping a cell continues to activate the App Picker and never merges automatically.
## Workspace transfer

Export is available from the selected card and offers “Chia sẻ” before “Lưu vào tệp…”. Import is a
56dp Library-toolbar action. A snapshot/name/cell/app preview is required before insertion. Errors
use friendly format/version/read/write messages and never expose raw URI, JSON or exceptions.

### Open with DexWorkspaceTouch

- Opening a supported external file brings the app forward and shows the established preview on
  Library; it never imports automatically.
- If Designer or App Picker is active, keep the transfer pending until the user returns to Library.
  Never discard a working draft or force navigation.
- A newer external file replaces an inactive old transfer dialog through one explicit routing policy;
  active reads/imports are allowed to finish before another event is dispatched.
- Errors use only friendly messages: cannot read, invalid DexWorkspaceTouch data, newer version,
  file too large, or read permission no longer available. Never show raw URI or parser details.

### Samsung My Files share fallback

Directly tapping `.dwt` or `.dwtbundle` in Samsung My Files is not guaranteed; some versions open
an app-store search without sending the file. The officially supported fallback is:

```text
Samsung My Files -> nhấn giữ file -> Chia sẻ -> DexWorkspaceTouch
```

The app accepts one shared file per delivery, shows the existing preview, and never imports before
confirmation. A shared file waits while Designer is active so an unsaved draft is not discarded.
## Workspace Library cards

### Multi-select

- Long-press a card to enter multi-select; a normal tap then toggles that card.
- Selected cards use an explicit check marker, primary tint/border, and accessibility state rather than color alone.
- The adaptive toolbar reports the selected count and provides Pin, Unpin, Delete, and Close actions with 56 dp touch targets.
- Search and sort may hide selected workspaces without deselecting them. Back or Close exits the mode.
- Batch deletion always requires confirmation, names the number of workspaces, and remains all-or-nothing.
- While multi-select is active, card-level open/edit/manage actions and external transfer dispatch are suspended.
- Multi-select uses a fixed top toolbar so actions remain available while the Library scrolls. Its single horizontal row starts with Close, followed by the selected count, Pin, Unpin, Export, and Delete; narrow windows can scroll the labeled 56 dp actions horizontally.
- Export offers Share and Save to file without leaving multi-select. Selection remains after success, cancellation, or failure.
- Search-hidden selected workspaces remain included in the selected count and exported bundle.

- Dùng metadata ngắn “x ô • y ứng dụng” và relative updated time một dòng.
- Snapshot ưu tiên icon thật; cell nhỏ có thể ẩn label và giữ icon/fallback rõ ràng.
- Card ghim đầu tiên không trở thành featured/hero card và giữ cùng layout với card thường.
- Library toolbar uses one “Tệp” action sheet for importing one workspace, backing up Library and
  restoring Library. Restore always shows counts, sample names, conflicts and the no-overwrite warning.
- Backup warnings require an explicit continue/cancel choice; all actions keep a 56dp touch target.
