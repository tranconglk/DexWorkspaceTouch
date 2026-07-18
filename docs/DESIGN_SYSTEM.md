# DexWorkspaceTouch Design System

Design system này là nguồn chuẩn cho kích thước, khoảng cách và lớp tương tác của UI touch-first. Token nằm trong package `com.trancong.dexworkspacetouch.ui.design`.

## Spacing Scale

| Token | Giá trị | Mục đích |
| --- | ---: | --- |
| `Spacing.XXS` | 2dp | Border hoặc khoảng cách cực nhỏ |
| `Spacing.XS` | 4dp | Khoảng cách nội bộ nhỏ |
| `Spacing.S` | 8dp | Khoảng cách giữa action liên quan |
| `Spacing.M` | 16dp | Padding nội dung tiêu chuẩn |
| `Spacing.L` | 24dp | Screen padding |
| `Spacing.XL` | 32dp | Khoảng cách section lớn |
| `Spacing.XXL` | 48dp | Khoảng cách tách vùng |

## Touch Targets

| Token | Giá trị |
| --- | ---: |
| `TouchTargets.PrimaryButton` | 64dp |
| `TouchTargets.SecondaryButton` | 56dp |
| `TouchTargets.DividerHitArea` | 64dp |
| `TouchTargets.MinimumInteractive` | 48dp |

Primary action ưu tiên 64dp. Secondary và action theo ngữ cảnh dùng 56dp. Không tạo target nhỏ hơn 48dp.

## Interaction Zones

| Token | Giá trị | Ý nghĩa |
| --- | ---: | --- |
| `InteractionZones.SafeActionInset` | 16dp | Khoảng an toàn từ action đến cạnh |
| `InteractionZones.DeadZone` | 12dp | Vùng đệm không kích hoạt nhầm gesture |
| `InteractionZones.DividerHitArea` | 64dp | Vùng nhận cảm ứng của divider |
| `InteractionZones.ActionPadding` | 8dp | Khoảng cách giữa các action |

Hit area có thể lớn hơn phần tử nhìn thấy. Ví dụ divider hiển thị 3dp nhưng nhận cảm ứng trên vùng 64dp.

## Z Layers

| Token | Giá trị |
| --- | ---: |
| `ZLayers.Workspace` | 0 |
| `ZLayers.Cell` | 1 |
| `ZLayers.Divider` | 2 |
| `ZLayers.Selection` | 3 |
| `ZLayers.ActionOverlay` | 4 |
| `ZLayers.BottomSheet` | 10 |

Không dùng z-index literal. Action của selected cell phải nằm trên divider để nhận pointer trước trong vùng giao nhau.

## Aspect Ratio

Workspace dùng `Dimensions.WorkspaceAspectRatio = 16 / 10`. Canvas phải dùng contain sizing theo cả chiều rộng và chiều cao khả dụng; không suy kích thước chỉ từ chiều rộng.

Cell ratio hợp lệ dùng `Dimensions.MinCellRatio = 0.2` và `Dimensions.MaxCellRatio = 0.8` ở lớp UI. Domain tiếp tục sở hữu validation riêng và không phụ thuộc design token.

## Divider Snap

Gesture kéo divider dùng `Dimensions.DividerSnapThreshold = 0.03` và các điểm snap chuẩn hóa:

- 25%
- 33⅓%
- 50%
- 66⅔%
- 75%

Snap chỉ áp dụng cho gesture drag. Các nút giảm/tăng 5% và reset 50% không đi qua Snap Engine.

### Divider feedback

Visual feedback khi kéo divider dùng các token
`Dimensions.DividerDragHandleLength`, `DividerDragHandleThickness`,
`DividerSnapGuideLength` và `DividerSnapGuideThickness`. Fade ngắn dùng
`DesignerAnimation.FastDurationMillis`; hit area vẫn giữ nguyên 64dp.

## Workspace Library Grid

Workspace Library dùng `GridCells.Adaptive` với
`Dimensions.WorkspaceCardMinWidth = 280dp`. Card giữ chiều cao tối thiểu
`Dimensions.WorkspaceCardMinHeight = 336dp` và khoảng cách lưới dùng
`Spacing.WorkspaceGrid = 16dp`. Số cột do chiều rộng khả dụng theo dp quyết định;
không giới hạn cứng theo thiết bị và không thu nhỏ touch target dưới 56dp.

Selected card từ `Dimensions.WorkspaceCardWideActionsWidth = 420dp` trở lên
hiển thị trực tiếp hành động Đổi tên/Xóa. Card nhỏ hơn dùng nút Quản lý 56dp
để mở bottom sheet; không dùng menu ba chấm hoặc icon-only action.

## App Picker Grid

App Picker dùng `GridCells.Adaptive` với
`Dimensions.AppPickerItemMinWidth = 280dp` và item cao tối thiểu
`Dimensions.AppPickerItemMinHeight = 168dp`. Icon dùng
`Dimensions.AppPickerIconSize = 64dp`, khoảng cách grid dùng
`Spacing.AppPickerGrid = 16dp`, và filter action cao tối thiểu
`TouchTargets.AppFilterMinHeight = 56dp`.

Số cột do chiều rộng cửa sổ theo dp quyết định; không ép số cột hoặc giảm touch
target để đạt mật độ cao hơn.

Workspace Library pin dùng `Dimensions.WorkspacePinIconSize = 24dp` trong vùng chạm
`TouchTargets.SecondaryButton = 56dp`. Nút góc dùng `ZLayers.ActionOverlay`; chuyển
outline/filled dùng `DesignerAnimation.FastDurationMillis`.

Designer status uses `Dimensions.DesignerStatusMinHeight = 32dp`; the toolbar switches to
an inline summary at `Dimensions.WorkspaceDesignerToolbarWideWidth = 720dp`. Selected-cell
border and container colors transition with `DesignerAnimation.FastDurationMillis` without
changing normalized bounds or layout size.

## Design Token Rules

1. UI mới phải dùng token thay cho literal `Dp`, elevation, shape, z-index và aspect ratio lặp lại.
2. Chọn token theo ý nghĩa, không chỉ theo giá trị trùng nhau. Ví dụ screen padding dùng `Spacing.L`, không dùng một token 24dp không liên quan.
3. Theme là nguồn cho typography và Material color scheme. `DesignerColors` chỉ đặt tên semantic cho màu dùng trong Designer.
4. Domain, state và ViewModel không được import package `ui.design`.
5. Token animation chỉ định thời lượng; việc thêm animation cần một task hành vi riêng.
6. Thay token không được làm thay đổi pixel, layout, state hoặc interaction hiện có nếu task chỉ là refactor design system.

## Template Picker Dialog

Template Picker dùng centered dialog rộng 88% vùng safe, cao tối đa 84% và rộng tối
đa 1440dp. Margin ngoài `Spacing.L` được trừ trước khi helper tính kích thước nên
surface, shape và shadow luôn nằm trong constraints an toàn.
Host windowed dưới large breakpoint dùng platform dialog width; host lớn/maximized
dùng custom width để tận dụng vùng DeX mà không đo vượt host.

Grid policy chỉ có ba mức: content width từ 1000dp dùng 3 cột, từ 620dp dùng 2 cột,
còn lại dùng 1 cột. Không có nhánh 4 cột. Preview dùng `WorkspaceAspectRatio = 16:10`
và tự tính chiều cao từ chiều rộng card, không có fixed-height token. Khoảng cách dùng
`Spacing.TemplateGrid = 16dp`; dialog header tối thiểu 72dp đứng yên trong khi một outer
list duy nhất cuộn các section. Section header tối thiểu 56dp và title card dành tối thiểu
48dp để giữ chiều cao hàng ổn định.

## Workspace Library Toolbar

Toolbar chuyển Row/Column tại 720dp. Search cao tối thiểu 56dp; sort button rộng tối thiểu
180dp ở layout rộng và full-width ở layout hẹp. Khoảng cách dùng `Spacing.M`/`Spacing.S`;
mọi clear/sort option dùng `TouchTargets.SecondaryButton = 56dp`.
