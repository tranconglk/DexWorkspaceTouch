# DexWorkspaceTouch — Hướng dẫn chuẩn bị, quay và dựng video

Tài liệu này dành cho người sản xuất video. Không hiển thị các lệnh kỹ thuật trong video dành cho người dùng cuối.

## 1. Phương án sản xuất đề xuất

- **Thiết bị chính:** Samsung S23 Ultra.
- **Màn hình:** Samsung DeX external 1920×1200.
- **Cách quay ưu tiên:** quay trực tiếp physical DeX display bằng `adb screenrecord --display-id`.
- **Âm thanh:** thu voice-over sau khi đã dựng hình thô.
- **Thành phẩm:** H.264 MP4, 1920×1080, giữ toàn bộ khung DeX 16:10 bằng pillarbox; không crop UI trên/dưới.
- **Thời lượng:** khoảng 6 phút 40 giây.

S23 được chọn vì công cụ `screenrecord` trên thiết bị hiện hỗ trợ chọn physical display. Note9 vẫn dùng được để kiểm tra compatibility, nhưng bản `screenrecord` trên máy không có tùy chọn display ID nên không phù hợp làm nguồn hình chính qua ADB.

## 2. Trạng thái công cụ đã kiểm tra

| Công cụ | Trạng thái | Ghi chú |
|---|---|---|
| ADB | Khả dụng | Đã nhận S23 Ultra và Note9 qua Wi-Fi tại thời điểm audit |
| S23 `screenrecord` | Khả dụng có điều kiện | Có `--display-id`; cần quay thử ngắn trước buổi chính |
| Note9 `screenrecord` | Hạn chế | Không có `--display-id`; mặc định có thể chỉ ghi màn hình chính |
| FFmpeg | Khả dụng | Bản cài hiện có hỗ trợ H.264 và subtitle |
| OBS/capture card | Chưa xác nhận | Chỉ dùng sau khi kiểm tra phần cứng/input thực tế |
| Windows screen recording | Có thể dùng có điều kiện | Chỉ phù hợp nếu toàn bộ DeX xuất hiện trong một cửa sổ trên PC |
| DeX built-in recorder | Chưa xác nhận | Không dựa vào phương án này cho kế hoạch chính |

Không quay DOC-002. Việc quay thử và xác nhận chất lượng thuộc bước sản xuất tiếp theo.

## 3. Chuẩn bị thiết bị

### S23 Ultra và DeX

1. Sạc điện thoại hoặc cắm nguồn ổn định.
2. Kết nối DeX với màn hình quay chính.
3. Chọn độ phân giải DeX 1920×1200 nếu màn hình hỗ trợ.
4. Tắt tự động xoay và các hiệu ứng có thể làm thay đổi bố cục giữa shot.
5. Đặt cỡ chữ, scale và theme cố định cho cả buổi quay.
6. Mở DexWorkspaceTouch trên màn hình DeX.
7. Kiểm tra Floating Dock xuất hiện trên đúng display.

### Dọn nội dung riêng tư

- Bật chế độ Không làm phiền.
- Xóa notification đang hiện và tắt preview notification nhạy cảm.
- Đóng email, tin nhắn, lịch, ảnh và tài liệu cá nhân.
- Đăng xuất hoặc dùng profile/demo account nếu ứng dụng quay có dữ liệu riêng.
- Trong Maps, tránh quay địa chỉ nhà, lịch sử tìm kiếm và vị trí chính xác. Đưa bản đồ tới một khu vực demo an toàn trước khi quay.
- Trong trình duyệt, đóng tab cá nhân, lịch sử gợi ý và thanh bookmark nhạy cảm.
- Kiểm tra tên Wi-Fi, tên thiết bị, avatar và đồng hồ trước mỗi shot.

## 4. Chuẩn bị dữ liệu demo

Không xóa dữ liệu thật nếu không cần. Tạo ba Workspace mới với tên ngắn, dễ đọc:

### Demo A — `Xe 50-50`

- Bố cục: hai cột bằng nhau.
- Trái: Google Maps.
- Phải: YouTube Music.
- Mục đích: hero Workspace cho launch, auto-collapse và daily flow.

### Demo B — `Ba ứng dụng`

- Bố cục: ba cột.
- Gợi ý: Chrome và hai ứng dụng launchable, không chứa dữ liệu riêng tư.
- Chỉ chốt app 2 và app 3 sau khi xác nhận chúng đã cài và mở ổn định trên S23.
- Mục đích: minh họa visual card ba vùng.

### Demo C — `Toàn màn hình`

- Bố cục: một ô toàn vùng.
- Gợi ý: Chrome hoặc YouTube.
- Mục đích: minh họa visual card đơn giản.

Không dùng ID cơ sở dữ liệu trong script, overlay hoặc tên clip.

## 5. Chuẩn bị Car Mode

1. Gán `Xe 50-50` vào Slot 1.
2. Gán `Ba ứng dụng` vào Slot 2.
3. Gán `Toàn màn hình` vào Slot 3.
4. Chuẩn bị ít nhất ba binding khác để grid 2×3 và 2×4 trông rõ.
5. Dành Slot 8 để quay thao tác **Clear** và trạng thái dấu cộng.
6. Trước SHOT-08, đặt **Visible shortcuts** về 6.
7. Sau SHOT-08, chọn 8 để quay grid 2×4.
8. Kiểm tra icon mọi ứng dụng đã tải trước khi bắt đầu SHOT-12.

Giảm visible count không xóa binding. Tuy vậy, ghi lại cấu hình Slot ban đầu để có thể khôi phục sau buổi quay.

## 6. Chuẩn bị Floating Dock và Desktop shortcut

### Floating Dock

1. Cấp quyền **Display over other apps** bằng UI Android.
2. Chọn **Hide** nếu Dock đang hiện.
3. Chọn **Show** để reset Dock về left-center trước SHOT-11.
4. Kiểm tra tap, expand, Collapse, drag/snap và auto-collapse một lần trước khi ghi.

### Car Dock shortcut

1. Trong Car Mode, chọn **Desktop shortcut → Car Dock → Add**.
2. Xác nhận hộp thoại của Samsung launcher nếu có.
3. Đặt icon Car Dock ở một vị trí dễ thấy trên DeX Desktop.
4. Dọn các icon hoặc file cá nhân xung quanh.
5. Kiểm tra một lần: Hide Dock, về Desktop, chạm Car Dock và xác nhận chỉ có một nút CAR xuất hiện.

Launcher có thể không cho pin lại một shortcut đã tồn tại. Nếu vậy, giữ clip Add và clip sử dụng icon thành hai shot độc lập.

## 7. Kiểm tra ứng dụng demo

Trước buổi quay chính:

- mở Google Maps và YouTube Music theo cách thông thường;
- đóng onboarding, cập nhật, yêu cầu đăng nhập hoặc popup không cần thiết;
- xác nhận cả hai hỗ trợ cửa sổ DeX;
- chạy Workspace `Xe 50-50` hai lần;
- kiểm tra không có dialog lỗi và hai app phủ đúng vùng làm việc;
- chuẩn bị trạng thái nội dung an toàn, ổn định cho khung hình cuối.

Nếu app được chọn cho Demo B không ổn định, thay app trong Workspace demo; không sửa production binding hay code.

## 8. Phương pháp quay ADB trên S23

### Xác định thiết bị và physical display

```powershell
adb devices -l
adb -s <DEVICE_SERIAL> shell dumpsys SurfaceFlinger --display-id
```

Không dùng logical DeX display ID cho `screenrecord`. Chọn physical display tương ứng với màn hình HDMI/DeX từ kết quả trên và xác nhận bằng một clip thử.

### Quay clip thử

```powershell
adb -s <DEVICE_SERIAL> shell screenrecord `
  --display-id <PHYSICAL_DISPLAY_ID> `
  --size 1920x1200 `
  --bit-rate 12000000 `
  --time-limit 15 `
  /sdcard/dwt_test.mp4

adb -s <DEVICE_SERIAL> pull /sdcard/dwt_test.mp4 .\recordings\dwt_test.mp4
```

Xem clip thử và kiểm tra:

- đúng external display;
- đủ 1920×1200;
- không đen hình;
- không méo màu;
- chuyển động con trỏ và app launch mượt;
- Floating Dock được ghi lại.

Nếu encoder không nhận 1920×1200, thử kích thước mà encoder hỗ trợ nhưng giữ đúng tỷ lệ. Không tự crop UI quan trọng.

### Quay từng shot

```powershell
adb -s <DEVICE_SERIAL> shell screenrecord `
  --display-id <PHYSICAL_DISPLAY_ID> `
  --size 1920x1200 `
  --bit-rate 12000000 `
  --time-limit 90 `
  /sdcard/08b_card_launch_auto_collapse.mp4

adb -s <DEVICE_SERIAL> pull `
  /sdcard/08b_card_launch_auto_collapse.mp4 `
  .\recordings\08b_card_launch_auto_collapse.mp4
```

Dừng sớm bằng `Ctrl+C` nếu shot hoàn tất. Quay từng shot riêng giúp retake mà không ảnh hưởng các phần khác.

### Hạn chế cần nhớ

- ADB Wi-Fi có thể mất kết nối; ưu tiên USB hoặc mạng ổn định nếu setup cho phép.
- `screenrecord` không thu voice-over từ microphone theo kế hoạch này.
- Note9 hiện không hỗ trợ chọn display trong `screenrecord`; không dùng Note9 làm nguồn chính trừ khi đã có capture card hoặc phương pháp khác được xác nhận.
- Không đưa terminal hoặc lệnh ADB vào video người dùng cuối.

## 9. Phương án quay thay thế

### Capture card + OBS

Đây là phương án tốt nếu capture card nhận trực tiếp HDMI DeX. Trước khi chọn:

1. xác nhận OBS thấy input 1920×1200 hoặc 1920×1080;
2. kiểm tra HDCP và độ trễ;
3. kiểm tra Floating Dock có trong tín hiệu;
4. quay thử app launch và con trỏ;
5. khóa canvas/output để mọi shot cùng kích thước.

### Windows screen recording

Chỉ dùng khi DeX chạy trong một cửa sổ có thể capture đầy đủ trên Windows. Không dùng nếu setup hiện tại xuất thẳng HDMI và Windows không nhận hình DeX.

### DeX built-in capture

Chỉ dùng sau khi xác nhận nó quay được toàn external desktop, Floating Dock và các cửa sổ app. Trạng thái này chưa được kiểm chứng trong audit hiện tại.

## 10. Clip naming và quản lý take

Dùng tên trong `SHOT_LIST.md`. Với retake, thêm hậu tố:

```text
08b_card_launch_auto_collapse_take01.mp4
08b_card_launch_auto_collapse_take02.mp4
08b_card_launch_auto_collapse_pick.mp4
```

Không ghi đè take cũ trước khi kiểm tra take mới. Sau mỗi 3–4 shot, sao chép clip về PC và xem nhanh đầu/cuối file.

## 11. Kế hoạch âm thanh

Ưu tiên thu voice-over sau:

- dễ quay lại riêng phần màn hình;
- không phải vừa nói vừa thao tác;
- dễ giữ nhịp câu và căn subtitle;
- loại bỏ tiếng quạt, click và thông báo từ hiện trường.

Quy trình:

1. dựng rough cut theo `VIDEO_SCRIPT_VI.md`;
2. đọc `VOICEOVER_VI.md` với tốc độ tự nhiên;
3. để khoảng nghỉ ngắn giữa câu;
4. căn hình theo voice-over;
5. retime `SUBTITLES_VI.srt` theo bản dựng thực tế.

Không cần nhạc nền. Nếu dùng, giữ âm lượng thấp và không che lời nói.

## 12. Kế hoạch dựng

1. Tạo title card đơn giản, 3–4 giây.
2. Đặt clip theo thứ tự Scene 01–12.
3. Cắt dead time, click nhầm và popup ngoài kịch bản.
4. Có thể tăng tốc nhẹ đoạn app launch dài, nhưng phải để lại thời gian nhận biết kết quả.
5. Chỉ zoom/crop vừa đủ để chỉ vị trí nút; quay về toàn khung trước khi chuyển scene.
6. Dùng cut thẳng hoặc fade ngắn; tránh transition nổi bật.
7. Thêm text overlay tối thiểu theo master script.
8. Mix voice-over.
9. Retime và kiểm tra subtitle.
10. Thêm end card, sau đó xuất bản nháp và xem lại toàn bộ.

## 13. Xử lý nguồn 1920×1200

Nguồn S23 DeX là tỷ lệ 16:10. Khuyến nghị giữ nguyên toàn bộ UI và xuất 1920×1080 bằng cách scale còn 1728×1080, sau đó thêm hai dải nền 96 pixel ở trái và phải. Cách này không cắt taskbar, title bar hoặc Floating Dock.

Nếu nền tảng phát hỗ trợ 1920×1200 tốt và không cần chuẩn 16:9, có thể giữ nguyên độ phân giải nguồn. Không crop 120 pixel theo chiều dọc chỉ để đạt 16:9.

## 14. FFmpeg outline

FFmpeg đã có trên máy. Các lệnh sau là outline, cần thay tên file theo bản dựng thật. Không chạy trong DOC-002.

### Chuẩn hóa một clip 1920×1200 sang 1920×1080 pillarbox

```powershell
ffmpeg -i input.mp4 `
  -vf "scale=1728:1080:flags=lanczos,pad=1920:1080:96:0:black" `
  -c:v libx264 -crf 18 -preset medium -an output_1080p.mp4
```

### Nối các clip đã chuẩn hóa

Tạo `concat.txt` chứa các dòng theo đúng thứ tự:

```text
file '01_intro_montage.mp4'
file '02_library_overview.mp4'
file '03a_choose_template.mp4'
```

Sau đó:

```powershell
ffmpeg -f concat -safe 0 -i concat.txt -c copy picture_track.mp4
```

### Ghép voice-over và subtitle mềm

```powershell
ffmpeg -i picture_track.mp4 -i voiceover.wav -i SUBTITLES_VI.srt `
  -map 0:v -map 1:a -map 2:0 `
  -c:v libx264 -crf 18 -preset medium `
  -c:a aac -b:a 192k -c:s mov_text `
  -metadata:s:s:0 language=vie tutorial_vi.mp4
```

Nếu cần burn subtitle, dùng filter `subtitles` sau khi đã kiểm tra font tiếng Việt và retime chính xác.

## 15. Quality-control checklist

### Hình ảnh

- [ ] Không có notification hoặc dữ liệu riêng tư.
- [ ] Mọi shot chính dùng cùng device/theme/scale.
- [ ] Không lẫn giao diện Note9 vào chuỗi S23.
- [ ] Không crop title bar, taskbar, Floating Dock hoặc nút đang hướng dẫn.
- [ ] Workspace 50/50 và visual card tương ứng dễ nhận ra.
- [ ] Dock Show, expand, auto-collapse, drag/snap và desktop shortcut đều có bằng chứng hình ảnh.

### Nội dung

- [ ] Chỉ có UI Workspace-first hiện tại.
- [ ] Không có Navigation/Music/Service Dock cũ.
- [ ] Nói đúng 3–8 Slot, mặc định 6.
- [ ] Visual card không có tên Workspace.
- [ ] Giải thích đúng Collapse khác Hide.
- [ ] Không hiện package, log, display ID hoặc lệnh kỹ thuật.

### Âm thanh và subtitle

- [ ] Giọng đọc tự nhiên, không quá nhanh.
- [ ] Nhạc nền không che lời.
- [ ] Subtitle tối đa khoảng hai dòng.
- [ ] Đã xóa caption ghi chú timing khỏi bản phát hành.
- [ ] Subtitle đã retime theo picture lock.

## 16. Ready for DOC-003 when

- [ ] S23 Ultra đã kết nối ổn định.
- [ ] DeX external display đang hoạt động.
- [ ] Clip thử `screenrecord --display-id` đã ghi đúng màn hình.
- [ ] Ba Workspace demo đã chuẩn bị và kiểm tra launch.
- [ ] Slot binding và visible count đã chuẩn bị.
- [ ] Overlay permission đã ở trạng thái phù hợp với shot.
- [ ] Shortcut Car Dock đã pin.
- [ ] Desktop, app demo và notification đã dọn riêng tư.
- [ ] Phương án âm thanh đã chốt là voice-over thu sau.
- [ ] Có đủ dung lượng trên điện thoại và PC.
- [ ] Shot list đã được người quay đọc và đánh dấu thứ tự thực hiện.

DOC-002 không thực hiện quay hoặc dựng video.

