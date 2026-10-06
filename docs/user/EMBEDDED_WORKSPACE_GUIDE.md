# Sử dụng Embedded Workspace (Thử nghiệm)

Classic mở các ứng dụng thành cửa sổ riêng trên DeX. Trong Library, nút **Mở** dùng Classic. **Mở Embedded (Thử nghiệm)** hiển thị ứng dụng trong các vùng của màn hình Workspace của DWT. Bạn chọn chế độ mở; DWT không tự chuyển sang Classic khi Embedded không sẵn sàng.

Embedded vẫn đang thử nghiệm. Hiện hỗ trợ 1–2 ứng dụng/hoạt động khác nhau trong các ô không chồng lấn. Khả năng chạy và tương tác còn phụ thuộc thiết bị, phiên bản Android/DeX và ứng dụng khách. Kiểm tra khả năng thành công không bảo đảm mọi ứng dụng, bàn phím hay bố cục đều được hỗ trợ.

## Tạo và mở Workspace

1. Tạo Workspace mới trong Library, chọn bố cục, rồi gán ứng dụng vào từng ô qua trình chọn ứng dụng.
2. Lưu với tên dễ nhận biết. Chờ lưu thành công và trở lại Library; nếu lưu báo lỗi, xử lý lỗi trước khi tiếp tục.
3. Chọn đúng Workspace. Nút **Mở Embedded (Thử nghiệm)** xuất hiện trên thẻ đã chọn.
4. Mở Embedded để xem kiểm tra khả năng. Việc vào màn hình, quay lại màn hình hoặc bấm **Kiểm tra lại** chỉ kiểm tra readiness; không tự chạy ứng dụng và không tự xin quyền.
5. Khi readiness đáp ứng điều kiện và nút được bật, bấm **Bắt đầu Embedded** để bắt đầu. Mỗi lần chạy mới cần thao tác này.

## Điều kiện trước khi bắt đầu

- Thiết bị phải đáp ứng khả năng Embedded mà DWT kiểm tra.
- Shizuku phải đang chạy/kết nối và DWT phải có quyền Shizuku. Nếu màn hình cho phép, bạn chủ động bấm **Cấp quyền**; quyền chỉ được yêu cầu sau thao tác của bạn.
- Workspace phải có 1–2 target hợp lệ, không trùng ứng dụng/hoạt động và bố cục không chồng lấn; ứng dụng được gán phải còn khả dụng.
- Vùng hiển thị/kích thước phải sẵn sàng và không còn lượt Embedded chưa xác nhận cleanup sạch.

Nếu Shizuku chưa chạy, dùng thao tác mở Shizuku khi màn hình cung cấp, hoàn tất ở Shizuku rồi trở lại DWT và bấm **Kiểm tra lại**. Nếu bố cục hoặc ứng dụng không phù hợp, quay về Library để sửa Workspace. Chỉ chọn **Mở Classic** khi nút đó đang được màn hình cho phép; không có chuyển chế độ ngầm.

## Tương tác, thoát và mở lại

Chờ màn hình báo Embedded đang chạy rồi thao tác trong đúng ô của ứng dụng. Với hai ứng dụng, kiểm tra kết quả hiển thị trong ô vừa chạm.

Bấm **Quay lại** hoặc Back để yêu cầu kết thúc lượt Embedded. DWT chờ kết quả cleanup của lượt đó. Khi đã dừng và cleanup được xác nhận, có thể chọn lại Workspace từ Library. Mở lại không tự chạy; kiểm tra readiness rồi bấm **Bắt đầu Embedded** cho lượt mới.

Không tắt cưỡng bức/khởi động lại tiến trình để thay cho bước kết thúc khi Embedded đang chạy. Khôi phục quyền sở hữu sau process death chưa thuộc phạm vi hỗ trợ được kiểm chứng của hành trình này.

## Khi thấy CLEANUP_BLOCKED

Thông báo **chưa xác nhận cleanup sạch** có nghĩa DWT chưa có đủ bằng chứng rằng mọi tài nguyên của lượt Embedded đã được dọn. Lượt xử lý local có thể đã kết thúc nhưng quyền sở hữu remote vẫn chưa được xác nhận sạch. Vùng app biến mất hoặc readiness trở lại không chứng minh cleanup thành công.

DWT giữ khóa mở Embedded và Classic để tránh tạo thêm lượt khi trạng thái còn chưa rõ. **Kiểm tra lại**, mở lại màn hình, thay đổi quyền Shizuku hoặc rời màn hình không phải thao tác xác nhận cleanup và không mở khóa này.

Dùng **Quay lại** hoặc **Xem trạng thái Embedded** khi có để xem trạng thái. Nếu cần hỗ trợ, chủ động bấm **Sao chép chẩn đoán** và cung cấp nội dung đó qua kênh hỗ trợ bạn chọn. DWT không tự sao chép/gửi dữ liệu. Không thử chạy lại liên tục, clear data, gỡ cài đặt hay đổi license để bỏ qua trạng thái này. Không giả định restart ứng dụng đã dọn sạch lượt remote.
