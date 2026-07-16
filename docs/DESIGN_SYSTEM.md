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

## Design Token Rules

1. UI mới phải dùng token thay cho literal `Dp`, elevation, shape, z-index và aspect ratio lặp lại.
2. Chọn token theo ý nghĩa, không chỉ theo giá trị trùng nhau. Ví dụ screen padding dùng `Spacing.L`, không dùng một token 24dp không liên quan.
3. Theme là nguồn cho typography và Material color scheme. `DesignerColors` chỉ đặt tên semantic cho màu dùng trong Designer.
4. Domain, state và ViewModel không được import package `ui.design`.
5. Token animation chỉ định thời lượng; việc thêm animation cần một task hành vi riêng.
6. Thay token không được làm thay đổi pixel, layout, state hoặc interaction hiện có nếu task chỉ là refactor design system.
