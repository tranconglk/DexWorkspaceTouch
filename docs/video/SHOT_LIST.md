# DexWorkspaceTouch — Shot list

## Quy ước

- Thiết bị chính: **Samsung S23 Ultra**.
- Display chính: **DeX external 1920×1200**.
- `Must-have`: bắt buộc có trong video.
- `Nice-to-have`: có thể rút ngắn nếu video vượt thời lượng.
- `Optional`: chỉ quay khi trạng thái thiết bị thuận lợi.
- Không quay display ID, ADB, log hoặc màn hình debug.

## Danh sách shot

### SHOT-01 — Intro montage

- **Priority:** Nice-to-have
- **Scene:** 01
- **Device:** S23 Ultra
- **Display:** DeX external
- **App/màn hình:** Library, Workspace 50/50, Floating Dock
- **Starting state:** Có sẵn ba clip sạch từ các shot sau
- **Exact action:** Cắt ba đoạn 2–3 giây; không cần quay riêng nếu đủ footage
- **Expected result:** Người xem thấy nhanh ba chức năng chính
- **Duration:** 10 giây footage, dùng khoảng 7 giây
- **Retake notes:** Tránh notification, con trỏ đứng yên giữa các cut
- **Output:** `01_intro_montage.mp4`

### SHOT-02 — Tổng quan Library

- **Priority:** Must-have
- **Scene:** 02
- **Device:** S23 Ultra
- **Display:** DeX external
- **App/màn hình:** DeX Workspace Manager
- **Starting state:** Library có 4–6 Workspace demo, ít nhất một Workspace đã ghim
- **Exact action:** Cuộn nhẹ; chạm một thẻ; mở sheet **Sắp xếp** rồi đóng
- **Expected result:** Thấy Đã ghim, Workspace, tìm kiếm, sort và thao tác thẻ
- **Duration:** 25 giây
- **Retake notes:** Không để tên Workspace cá nhân; không cuộn quá nhanh
- **Output:** `02_library_overview.mp4`

### SHOT-03 — Chọn template

- **Priority:** Must-have
- **Scene:** 03
- **Device:** S23 Ultra
- **Display:** DeX external
- **App/màn hình:** Library → Chọn mẫu bố cục
- **Starting state:** Library; không có dialog đang mở
- **Exact action:** Chọn **Tạo bố cục mới**, dừng 2 giây, chọn **2 Cột**
- **Expected result:** Trình thiết kế mở với hai ô bằng nhau
- **Duration:** 12 giây
- **Retake notes:** Đảm bảo nhóm Cơ bản và mẫu 2 Cột nằm trong khung hình
- **Output:** `03a_choose_template.mp4`

### SHOT-04 — Gán ứng dụng và lưu

- **Priority:** Must-have
- **Scene:** 03
- **Device:** S23 Ultra
- **Display:** DeX external
- **App/màn hình:** Thiết kế workspace, App Picker
- **Starting state:** Canvas hai cột trống
- **Exact action:** Gán Maps vào trái; gán YouTube Music vào phải; chọn **Lưu**; đặt tên `Xe 50-50`
- **Expected result:** Workspace mới được lưu và xuất hiện trong Library
- **Duration:** 38 giây
- **Retake notes:** Có thể cắt ngắn thời gian gõ; không để kết quả tìm kiếm chứa dữ liệu nhạy cảm
- **Output:** `03b_assign_apps_save.mp4`

### SHOT-05 — Điều chỉnh divider

- **Priority:** Nice-to-have
- **Scene:** 03
- **Device:** S23 Ultra
- **Display:** DeX external
- **App/màn hình:** Thiết kế workspace
- **Starting state:** Canvas hai cột, cả hai ô đã có app
- **Exact action:** Kéo divider lệch nhẹ; thả; chọn divider và bấm **50%**
- **Expected result:** Thấy tỷ lệ thay đổi, snap feedback và trở về 50%
- **Duration:** 12 giây
- **Retake notes:** Thao tác chậm, một đường kéo liên tục; không che feedback bằng con trỏ
- **Output:** `03c_adjust_divider.mp4`

### SHOT-06 — Launch Workspace 50/50

- **Priority:** Must-have
- **Scene:** 04
- **Device:** S23 Ultra
- **Display:** DeX external
- **App/màn hình:** Library → Maps + YouTube Music
- **Starting state:** Workspace `Xe 50-50` đã lưu; hai app đã đóng hoặc ở background
- **Exact action:** Chọn **Mở**; chờ hoàn tất; giữ bố cục
- **Expected result:** Maps ở trái, YouTube Music ở phải; phủ vùng làm việc DeX
- **Duration:** 25 giây
- **Retake notes:** Maps phải dùng vị trí demo an toàn; cắt dead time nhưng không che quá trình mở
- **Output:** `04_launch_50_50.mp4`

### SHOT-07 — Tổng quan Car Mode

- **Priority:** Must-have
- **Scene:** 05
- **Device:** S23 Ultra
- **Display:** DeX external
- **App/màn hình:** Car Mode
- **Starting state:** DexWorkspaceTouch ở Library
- **Exact action:** Chọn **Open Car mode**; cuộn từ dashboard xuống Workspace shortcuts
- **Expected result:** Thấy Workspace dashboard, Floating Dock, Desktop shortcut và cấu hình Slot
- **Duration:** 25 giây
- **Retake notes:** Không quay UI semantic Dock cũ; chỉ quay Workspace-first UI
- **Output:** `05_car_mode_overview.mp4`

### SHOT-08 — Đổi 6 thành 8 Slot

- **Priority:** Must-have
- **Scene:** 06
- **Device:** S23 Ultra
- **Display:** DeX external
- **App/màn hình:** Car Mode
- **Starting state:** Visible shortcuts = 6; Slot 7–8 đã có binding demo để cho thấy binding được giữ
- **Exact action:** Chọn **8**; cuộn lên dashboard và xuống lại danh sách Slot
- **Expected result:** Slot 7–8 xuất hiện ngay, không restart
- **Duration:** 18 giây
- **Retake notes:** Sau toàn bộ buổi quay, đưa count về trạng thái mong muốn cho các shot sau
- **Output:** `06a_visible_slots_6_to_8.mp4`

### SHOT-09 — Gán và Clear Slot

- **Priority:** Must-have
- **Scene:** 06
- **Device:** S23 Ultra
- **Display:** DeX external
- **App/màn hình:** Workspace shortcuts
- **Starting state:** Slot 1 có thể thay đổi; Slot 8 dùng làm slot minh họa Clear
- **Exact action:** Mở Slot 1, chọn `Xe 50-50`; mở Slot 8, chọn **Clear**
- **Expected result:** Slot 1 đổi binding; Slot 8 thành Not configured
- **Duration:** 22 giây
- **Retake notes:** Ghi lại binding ban đầu để khôi phục sau buổi quay
- **Output:** `06b_bind_and_clear_slot.mp4`

### SHOT-10 — Permission flow

- **Priority:** Optional
- **Scene:** 07
- **Device:** S23 Ultra
- **Display:** DeX external và Android Settings do hệ thống mở
- **App/màn hình:** Car Mode → Display over other apps
- **Starting state:** Overlay permission tắt; Floating Dock chưa hiển thị
- **Exact action:** Chọn **Allow**; bật quyền; quay lại ứng dụng
- **Expected result:** Trạng thái permission tự làm mới
- **Duration:** 20 giây
- **Retake notes:** Chỉ quay nếu có thể bật/tắt quyền mà không làm gián đoạn setup; không dùng ADB trong footage
- **Output:** `07a_overlay_permission.mp4`

### SHOT-11 — Show và expand Dock

- **Priority:** Must-have
- **Scene:** 07–08
- **Device:** S23 Ultra
- **Display:** DeX external
- **App/màn hình:** Car Mode + Floating Dock
- **Starting state:** Permission đã cấp; Dock Hidden; visible count = 8
- **Exact action:** Chọn **Show**; chạm **CAR**
- **Expected result:** Nút CAR xuất hiện bên trái rồi mở grid 2×4
- **Duration:** 15 giây
- **Retake notes:** Dock phải bắt đầu ở left-center; không để cửa sổ khác che card
- **Output:** `07b_show_expand_dock.mp4`

### SHOT-12 — Visual cards

- **Priority:** Must-have
- **Scene:** 08
- **Device:** S23 Ultra
- **Display:** DeX external
- **App/màn hình:** Floating Dock expanded
- **Starting state:** 8 Slot; có card full, 50/50, ba cột và một Slot chưa cấu hình
- **Exact action:** Giữ grid; đưa con trỏ lần lượt qua ba kiểu card và dấu cộng
- **Expected result:** Không có tên Workspace; thấy sơ đồ và icon trong từng cell
- **Duration:** 18 giây
- **Retake notes:** Không click trong lúc giới thiệu; kiểm tra mọi icon đã tải xong
- **Output:** `08a_visual_cards.mp4`

### SHOT-13 — Tap card và auto-collapse

- **Priority:** Must-have
- **Scene:** 08
- **Device:** S23 Ultra
- **Display:** DeX external
- **App/màn hình:** Floating Dock → Workspace 50/50
- **Starting state:** Dock expanded; card `Xe 50-50` đã biết vị trí
- **Exact action:** Chạm card; chờ hai app mở
- **Expected result:** Dock tự chuyển về nút CAR; hai app đúng 50/50; Dock vẫn tồn tại
- **Duration:** 25 giây
- **Retake notes:** Không chạm nhầm Slot; giữ 4 giây sau khi layout ổn định
- **Output:** `08b_card_launch_auto_collapse.mp4`

### SHOT-14 — Drag và snap

- **Priority:** Must-have
- **Scene:** 09
- **Device:** S23 Ultra
- **Display:** DeX external
- **App/màn hình:** Floating Dock collapsed trên nền app demo an toàn
- **Starting state:** Dock ở cạnh trái
- **Exact action:** Kéo sang phải và thả; kéo lại trái và thả
- **Expected result:** Dock snap sát hai cạnh và không ra ngoài vùng hiển thị
- **Duration:** 20 giây
- **Retake notes:** Mỗi lần kéo dùng một gesture liền mạch; giữ 2 giây sau mỗi snap
- **Output:** `09_drag_snap.mp4`

### SHOT-15 — Collapse và Hide

- **Priority:** Nice-to-have
- **Scene:** 09
- **Device:** S23 Ultra
- **Display:** DeX external
- **App/màn hình:** Floating Dock + Car Mode
- **Starting state:** Dock expanded
- **Exact action:** Chọn **Collapse**; sau đó trong Car Mode chọn **Hide**
- **Expected result:** Collapse còn nút CAR; Hide loại Dock khỏi màn hình
- **Duration:** 15 giây
- **Retake notes:** Dừng rõ ở trạng thái collapsed trước khi Hide
- **Output:** `09b_collapse_vs_hide.mp4`

### SHOT-16 — Pin shortcut Car Dock

- **Priority:** Must-have
- **Scene:** 10
- **Device:** S23 Ultra
- **Display:** DeX external
- **App/màn hình:** Car Mode + Samsung launcher
- **Starting state:** Launcher hỗ trợ pin; shortcut chưa có hoặc có thể xác nhận lại an toàn
- **Exact action:** Chọn **Add**; xác nhận hộp launcher nếu xuất hiện
- **Expected result:** Shortcut Car Dock được yêu cầu ghim
- **Duration:** 12 giây
- **Retake notes:** Nếu shortcut đã tồn tại và launcher không cho pin lại, quay riêng phần Add rồi dùng icon đã có
- **Output:** `10a_pin_car_dock.mp4`

### SHOT-17 — Show từ DeX Desktop

- **Priority:** Must-have
- **Scene:** 10
- **Device:** S23 Ultra
- **Display:** DeX external
- **App/màn hình:** DeX Desktop
- **Starting state:** Shortcut Car Dock đã ghim; Dock Hidden; Car Mode không foreground
- **Exact action:** Chạm icon **Car Dock** một lần
- **Expected result:** Nút CAR xuất hiện bên trái; không mở màn hình Car Mode
- **Duration:** 15 giây
- **Retake notes:** Desktop sạch; không có tên file, widget hoặc notification cá nhân
- **Output:** `10b_desktop_shortcut_show.mp4`

### SHOT-18 — Daily flow liền mạch

- **Priority:** Must-have
- **Scene:** 11
- **Device:** S23 Ultra
- **Display:** DeX external
- **App/màn hình:** DeX Desktop → Floating Dock → Workspace
- **Starting state:** Dock Hidden; shortcut đã pin; Workspace demo đã cấu hình
- **Exact action:** Car Dock → CAR → card 50/50 → chờ Workspace ổn định
- **Expected result:** Bố cục sẵn sàng và Dock collapsed trong một chuỗi liên tục
- **Duration:** 28 giây
- **Retake notes:** Đây là hero shot; ưu tiên quay lại nếu có lag, popup hoặc click thừa
- **Output:** `11_daily_flow.mp4`

## Shot compatibility tùy chọn

Note9 chỉ dùng cho một cut ngắn cuối video nếu muốn nói ứng dụng đã được kiểm thử trên thế hệ DeX cũ. Không trộn Note9 với S23 trong cùng một thao tác, vì chrome cửa sổ và tỷ lệ màn hình khác nhau. Clip này không bắt buộc và không thay thế bất kỳ Must-have shot nào.

