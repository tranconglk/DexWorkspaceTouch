# DWT-VDM-008 — Tích hợp chế độ chạy Workspace và điều kiện mở Embedded

**Ngày:** 2026-09-30

**Trạng thái:** Đề xuất kiến trúc để duyệt; chưa triển khai

**Nhánh / baseline:** `release/1.0-beta` / `5fc5492b3dad9e6077ae80e617172d824b9dec46`

## Mục tiêu và ranh giới

Một Workspace đã lưu là nguồn dữ liệu duy nhất cho cả hai cách chạy. “Mở” tiếp tục dùng đúng luồng Classic hiện tại; người dùng phải chọn hành động riêng để thử Embedded. Tích hợp ở lớp sản phẩm, không thay `AndroidWorkspaceLauncher`, `ActivityOptions`, `EmbeddedWorkspaceRunner`, renderer, session, VirtualDevice, dữ liệu Room hoặc schema. Shizuku chỉ là điều kiện cho Embedded. 006/007 đã PASS và các proof route vẫn có giá trị để cô lập hồi quy.

Các điểm hiện có làm nền cho quyết định: `workspace/execution/WorkspaceRunMode.kt` đã định nghĩa `CLASSIC`/`EMBEDDED`; `WorkspaceRunRequest.kt` hiện mang ID, tên, canvas và mode; `WorkspaceRunPlanner` dùng chung `WorkspaceLaunchRequestFactory`. `WorkspaceLibraryCard` đang có hàng `[Mở] [Sửa]`, chỉ hiện thêm các thao tác quản lý khi card được chọn. `HomeScreen` truyền `onLaunchWorkspace(workspace)` đến “Mở”; `TouchNavigation` gọi `WorkspaceLaunchViewModel.launchWorkspace(...)`. Route `embedded-workspace-layout/{workspaceId}` hiện dùng `EmbeddedWorkspaceLayoutLoader`, repository, planner, mapper, renderer và runner thật nhưng mang UI chẩn đoán. Route `embedded-workspace-runner` dùng proof plan cố định. Các quan sát này là hợp đồng tích hợp, không phải yêu cầu sửa các lớp đó trong 008.

## Quyết định sản phẩm

| Phương án | Ưu điểm | Chi phí/rủi ro | Quyết định |
| --- | --- | --- | --- |
| “Mở” giữ Classic, hành động phụ “Mở Embedded (Thử nghiệm)” | Rõ ý định, không đổi thói quen; phù hợp hàng action hiện tại | Thêm một control khi card được chọn | **Chọn** |
| “Mở” luôn hiện hộp chọn mode | Dễ thấy hai mode | Thêm bước cho mọi lần chạy Classic | Không chọn |
| Đổi hàng chính thành menu “Mở Classic/Mở Embedded” | Tiết kiệm ngang | Đổi nghĩa và khả năng nhận ra của “Mở” | Không chọn |
| Toggle mode toàn cục | Ít control trên card | Trạng thái ẩn, dễ mở nhầm | Không chọn |

Ở card **đã chọn**, thêm một `OutlinedButton` đủ kích thước chạm, chiếm hàng riêng dưới `[Mở] [Sửa]`, ghi **“Mở Embedded (Thử nghiệm)”**; giữ nguyên hàng chính và hành vi của “Mở” khi gate cho phép. Cả card ghim và card thường dùng cùng component nên nhận cùng hành động. Không hiện hành động chạy trong chế độ chọn nhiều. Nếu Embedded đang STARTING/ACTIVE/STOPPING hoặc cleanup còn bất định, gate product tạm chặn cả action chạy Classic lẫn Embedded để tránh execution xung đột, không sửa Classic launcher; khi không đủ điều kiện tĩnh hoặc Shizuku chưa sẵn sàng nhưng chưa cấp phát, vẫn cho phép chọn hành động để trình bày lý do và cách xử lý. Nhãn thử nghiệm được giữ trong card, màn chạy và thông báo.

Mode **chỉ tồn tại trong yêu cầu của lần chạy**. Không có “mode đang chọn” toàn cục hoặc trường trong Workspace. So sánh persistence: (A) runtime-only giữ dữ liệu và hành vi cũ, là lựa chọn 008; (B) nhớ mode cuối của từng Workspace qua preference ngoài Workspace có thể hữu ích về sau nhưng dễ biến mode cũ thành lựa chọn ngầm, nên hoãn; (C) cột default mode trong Workspace đòi Room migration, thay semantics của “Mở” và tạo dữ liệu cần đồng bộ/khôi phục, không có lý do sản phẩm đủ mạnh cho 008. Nếu sau này dùng B, “Mở” vẫn Classic trừ khi có quyết định sản phẩm riêng công khai.

## Ranh giới điều phối và luồng dữ liệu

Giữ `WorkspaceRunMode` ở `workspace/execution`, phía trên cả hai engine; không đặt trong `feature/embeddedapp` hay `workspace/execution/embedded/runtime`. Yêu cầu ở biên UI là `(workspaceId, mode)`; `workspaceId` là khóa duy nhất được chuyển qua navigation. `WorkspaceRunRequest` hiện có tên/canvas là **snapshot sau khi repository tải**, không phải đối tượng serialize qua route và không yêu cầu tạo một loại Workspace mới.

`WorkspaceRunCoordinator` ở lớp sản phẩm/navigation (hoặc vai trò tương đương trong graph/ViewModel) xử lý intent, điều kiện vào và trạng thái điều hướng:

```text
Workspace Library
  ├─ Mở → CLASSIC → WorkspaceLaunchViewModel.launchWorkspace(...) → Classic pipeline hiện có
  └─ Mở Embedded (Thử nghiệm) → EMBEDDED → route sản phẩm embedded-workspace/{workspaceId}
       → repository.getById(id) → shared launch request/planner
       → eligibility → renderer 007 → pre-start readiness → Bắt đầu → runner 006
```

Nhánh Classic giữ **đúng lệnh gọi hiện tại** của `HomeScreen`/`TouchNavigation` sau khi product gate cho phép; coordinator không ép Classic đi qua model, preflight hoặc error handling của Embedded. Nhánh Embedded không tạo renderer, planner hay runner thứ hai; dùng host/renderer 007 với presentation sản phẩm và ID đã chọn. `WorkspaceRunPlanner` hiện có có thể tái sử dụng cho bước tạo plan từ Workspace đã tải; không định nghĩa một planner khác. Điều phối sản phẩm tuyệt đối không sở hữu `EmbeddedAppSession`, timeout, Surface/slot callback, VirtualDevice, rollback, cleanup, geometry guest hoặc logic `ActivityOptions`/bounds. 006 tiếp tục là nguồn duy nhất điều phối session.

Route sản phẩm mới là **`embedded-workspace/{workspaceId}`**, khác `embedded-workspace-layout/{workspaceId}` thử nghiệm. Chỉ truyền ID đã URI-encode; không truyền Workspace, plan, Surface, session ID hay VirtualDevice ID. Ở destination, tải `repository.getById(workspaceId)` trước khi tạo plan và tải lại theo ID cho từng lần chạy mới. Nếu Workspace bị sửa giữa chọn và tải, dùng bản hiện có vừa tải và kiểm tra lại toàn bộ; nếu bị xóa, trả `MissingWorkspace(id)`, không chọn Workspace khác, không Start. Không chuyển plan cũ qua back stack. Không tự khởi động runner khi destination vừa mở: sau eligibility và readiness, phải có thao tác **“Bắt đầu Embedded”** trên màn hình sản phẩm. Điều này tránh cấp phát phiên khi chạm nhầm action, đồng thời xác nhận lần cuối rằng đây là chế độ thử nghiệm.

## Điều kiện tĩnh và sẵn sàng lúc chạy

Một biên thuần, có kết quả typed, ví dụ `EmbeddedWorkspaceEligibility.evaluate(resolvedWorkspace, launchReadiness, plan)`, được dùng **trước khi dựng renderer**. Nó không gọi Shizuku, không tạo Surface/session và không dựa vào kích thước viewport. Loader/repository xử lý `MissingWorkspace` trước; `WorkspaceLaunchRequestFactory` hiện có là nguồn kiểm tra canvas và resolve app chung, không lặp quy tắc resolve. Chuyển các kết quả `LaunchReadiness` sang lý do sản phẩm cụ thể. Với plan đã resolve, kiểm tra:

- Canvas không rỗng, cấu trúc và ô hợp lệ, mọi ô được gán app; package/component thực tế có thể resolve và launch.
- `sourceCellId`/thứ tự/normalized bounds hợp lệ, layout không có vùng chồng dương; cạnh kề được phép. Kiểm tra hình học chuẩn hóa trước viewport; mapper 007 tiếp tục kiểm tra pixel rect thực tế khi đo.
- Không có hai item cùng cặp `(packageName, componentName)` **sau resolve**. Trả `DuplicateTarget`, giữ preflight 006 như chốt kiểm tra cuối, không nới runner.
- Chính sách sản phẩm **1–2 app**: 0 không hợp lệ; 1 và 2 được phép thử; `count > 2` trả `UnsupportedEmbeddedItemCount(count)`. Không chia/sao chép Workspace. Classic vẫn dùng giới hạn hiện tại của Classic. Giới hạn này chỉ ở eligibility sản phẩm; mapper/runner generic không nhận cap. Cho phép arbitrary N vì code có vòng lặp là tuyên bố vượt chứng cứ S23 với hai target, nên không chọn.
- Loại item/cấu trúc chưa hỗ trợ và geometry policy không thể resolve trả lý do riêng trước khi tạo session. `EmbeddedWorkspacePreflight` của runner vẫn chạy lại với Surface/slot thật ngay trước cấp phát.

`RuntimeReadiness` là bước **tách biệt và không cấp phát tài nguyên chạy Embedded**. Nó có thể đọc API/capability nền tảng, Shizuku đã cài/đang chạy/binder hiện hữu, quyền Shizuku, viewport đã đo, layout mapper chấp nhận, geometry snapshot đã đóng băng, Surface host hợp lệ và `controller.canStart`; kết quả là trạng thái/lý do typed, không phải một boolean. Kiểm tra lại ngay khi người dùng bấm “Bắt đầu Embedded” và khi chọn “Kiểm tra lại”. Trước Start, nó **không** tạo `EmbeddedAppSession`, lấy UserService lease, bind/khởi động UserService chỉ để thăm dò, hay cấp phát VirtualDevice/input/task. SurfaceView do host 007 chuẩn bị để đo và hiển thị không phải session allocation. Sau Start, kết nối UserService và mọi cấp phát/rollback thuộc đúng runner/session hiện có; kết nối thất bại lúc này là `RuntimeStartFailed`, không được báo như lỗi pre-start readiness. Vì vậy một từ chối trước Start phải có zero embedded execution allocation.

Không khẳng định mọi Android/DeX đều được hỗ trợ. Với minSdk 28, máy không có capability cần thiết báo `UnsupportedPlatform/Capability`; không hard-code danh sách firmware Samsung. S23 Android 16 là thiết bị đã được chứng minh; thiết bị khác vẫn mang nhãn thử nghiệm và chỉ được Start khi capability/preflight thực tế đạt. Trạng thái Shizuku trở lại có thể được nhận qua callback hiện có hoặc “Kiểm tra lại”, không yêu cầu khởi động lại app nếu API hiện có cho phép.

Shizuku không được khởi tạo/bắt buộc ở app startup, Workspace Library hay nhánh Classic. Chỉ sau hành động Embedded mới đọc binder/quyền; nếu quyền thiếu, hiển thị hướng dẫn và một thao tác yêu cầu quyền rõ ràng, không gọi bind/permission ngầm trong lúc mở thư viện. Connection manager hiện có có thể xin quyền khi `connect`, nên tích hợp sản phẩm phải chặn trước bước ấy và chỉ cho Start sau tương tác Embedded tường minh và khi quyền đã được cấp. Không dùng `connect()` như readiness probe. Nếu Shizuku chưa cài, đã dừng, wireless debugging tắt hoặc quyền thiếu, màn sản phẩm nêu đúng trạng thái và cho **“Kiểm tra lại”**, **“Mở Classic”**, **“Hủy”** khi an toàn. Nếu UserService không kết nối được **sau Start**, hiện `RuntimeStartFailed` cùng cleanup outcome. Không hướng dẫn bắt buộc bật debugging để dùng Classic.

**Không có fallback im lặng.** Khi Embedded được yêu cầu nhưng không khả dụng, không gọi Classic. “Mở Classic” là hành động riêng của người dùng và chỉ dispatch nhánh Classic sau khi biết không có phiên Embedded còn hoạt động hoặc việc cleanup đã được xác nhận hoàn tất.

## Navigation, quyền sở hữu và kết thúc

Chỉ **một run Embedded sản phẩm** được phép ở một thời điểm. `EmbeddedProductRunGate` là quyền sở hữu sản phẩm ở **app/nav-graph scope, cao hơn mọi route instance**; nó tồn tại qua Compose recomposition, route recreation, screen navigation và việc UI tạm biến mất. Gate có trạng thái sản phẩm `IDLE`, `STARTING`, `ACTIVE`, `STOPPING`, `CLEANUP_BLOCKED`. Nó ngăn mở run Embedded khác và **mọi điểm vào Classic của Workspace Library/product fallback** khi run cũ chưa sạch. Gate chỉ được nhả nếu xác nhận **chưa có allocation** hoặc runner/session trả **authoritative terminal-clean result**; route disposal tự nó không nhả gate. Runner 006 vẫn là nguồn sự thật cho session lifecycle; gate không giữ session, không thay runner state machine và chỉ ngăn execution xung đột.

Không suy diễn rằng chỉ một route trên back stack đã đủ: proof 007 hiện gọi `close()` bất đồng bộ từ `DisposableEffect`, nên pop ngay có thể đi trước cleanup. Tích hợp phải dùng cơ chế stop/close có thể đợi và quan sát kết quả **trước khi pop hoặc cho chạy tiếp**. Gate ở scope cao hơn route giữ trạng thái khi route bị hủy bất ngờ; host tiếp tục cleanup bằng scope có thời gian sống thích hợp. Đây là điều kiện tích hợp cụ thể, không phải lý do thay runner. Không mở Classic ngay sau lỗi nếu outcome cleanup chưa chắc chắn.

| Tình huống | Hành vi sản phẩm |
| --- | --- |
| Back khi IDLE/chưa Start | Rời route bình thường sau khi đóng host; không có session cần thu hồi. |
| Back khi ACTIVE | Khóa Back lặp, gọi đúng một đường `coordinator.close()`/runner cleanup hiện có, đợi kết quả sạch rồi pop. |
| Back khi STARTING/STOPPING | Ghi ý định rời route, hội tụ qua lifecycle runner hiện có, không tạo lệnh stop cạnh tranh hoặc bỏ phiên giữa chừng. |
| Surface mất / runner lỗi / remote chết | Dừng qua controller/runner 006/007, hiện lỗi typed và cleanup outcome; không tự Start lại. |
| Route dispose / Activity destroy / đóng cửa sổ | Gọi close của host 007 và giữ scope cleanup sống tới terminal result; disposal không được hủy coroutine cleanup sớm. Nếu hệ điều hành giết process thì không thể cam kết đồng bộ; cần ghi nhận giới hạn này, không dùng force-stop hoặc global cleanup. |
| Chuyển Workspace hoặc mở Embedded lại | Chờ run cũ cleanup sạch rồi tạo generation/controller mới, tải lại theo ID; callback cũ bị generation token loại. |
| Mở Classic ngay sau Embedded | Chỉ khi run Embedded terminal sạch hoặc được xác nhận chưa từng cấp phát; không trong `CLEANUP_BLOCKED`/outcome bất định. |

Kết quả runner `CleanupIncomplete`, remote death khi cleanup chưa xác minh, hoặc outcome bất định đều ánh xạ thành trạng thái sản phẩm **`CLEANUP_BLOCKED`**; UI không cần phản chiếu tên phase nội bộ. Ở trạng thái này: Open Embedded bị khóa, Open Classic từ luồng lỗi bị khóa, Back/pop làm mất quyền sở hữu bị cấm; chỉ Retry/reconcile theo đường được hỗ trợ mới được phép. Giữ màn trạng thái hoặc trạng thái product-run ở scope cao hơn route đủ lâu để người dùng thấy lý do; không pop rồi bỏ khóa, không coi mất UI là cleanup thành công. Chỉ quay gate về `IDLE` sau kết quả dọn sạch có thẩm quyền. Nếu không thể kiểm chứng, tiếp tục chặn execution xung đột và báo chẩn đoán; không tự dọn session ngoài phạm vi runner.

## Lỗi sản phẩm và chẩn đoán

Định nghĩa lỗi sản phẩm typed, ánh xạ từ repository, `LaunchReadiness`, eligibility, mapper, preflight và runner, giữ cause/code gốc cho diagnostics:

| Nhóm | Mã/lý do tối thiểu | Thông điệp và hành động |
| --- | --- | --- |
| Dữ liệu | `MissingWorkspace`, `LaunchNotReady`, `UnsupportedLayout`, `UnsupportedEmbeddedItemCount(count)`, `DuplicateTarget` | Nêu Workspace/ô/app cần sửa hoặc chọn Classic; không Start. |
| Thiết bị | `UnsupportedPlatform/Capability`, `GeometryUnavailable`, `ShizukuUnavailable`, `ShizukuPermissionMissing` | Giải thích điều kiện còn thiếu; Retry/thiết lập quyền khi người dùng chọn. |
| Host | `RendererNotReady` (Surface/viewport chưa sẵn) | Chờ hoặc Kiểm tra lại; không phân loại nhầm thành dữ liệu hỏng. |
| Runtime | `RuntimeStartFailed`, `RuntimeCleanupIncomplete`, `RemoteDied` | Hiện trạng thái run/cleanup; chỉ cho Classic sau cleanup sạch. |

UI thường hiển thị một câu ngắn và hành động cụ thể, không in stack trace, session ID hay raw exception. Bảng chẩn đoán/diagnostic export hiện có có thể ghi `workspaceId`, run mode, phase, mã lỗi, `sourceCellId` liên quan và cleanup outcome để kỹ thuật đối chiếu; không ghi Surface/secret hay biến diagnostics thành UI mặc định. “Không thể mở” đơn độc không đủ để phân biệt thiếu quyền, layout, lỗi start và lỗi cleanup. Mọi từ chối trước Start phải có bằng chứng zero allocation.

## Proof route, kiểm chứng và giới hạn

Giữ các route Waze, Calculator, Dual App, `embedded-workspace-runner` của 006 và `embedded-workspace-layout/{workspaceId}` của 007 dưới quyền truy cập experimental/developer hiện có; không chuyển nút proof ở Home thành hành động sản phẩm và không xóa chúng trong 008. Màn sản phẩm dùng cùng loader/plan/renderer/controller/runner nhưng có gating, nhãn thử nghiệm, thông báo và lifecycle navigation riêng. Nếu hiện tại proof entries đang lộ trên Home, việc thu gọn chúng vào vùng developer là thay đổi trình bày thuộc bước tích hợp sau, không được làm mất khả năng truy cập chẩn đoán. 008 không khẳng định parity với Classic.

Lần triển khai sau cần unit/integration test cho routing Classic-only/Embedded-only, nút “Mở” mặc định, không fallback, explicit Classic sau cleanup; eligibility thiếu Workspace, canvas lỗi, app không resolve, overlap, duplicate target, 0/1/2/>2 item và geometry; pre-start readiness không gọi `connect`/acquire lease/tạo session/VDM, còn lỗi kết nối sau Start là `RuntimeStartFailed`; Shizuku thiếu/thiếu quyền chỉ chặn Embedded, phục hồi qua Retry; ID truyền chính xác, Workspace bị xóa trước load, Back ở IDLE/STARTING/ACTIVE/STOPPING, route dispose/recreation không nhả gate, cleanup incomplete ánh xạ `CLEANUP_BLOCKED` và chặn run kế tiếp; coordinator không sở hữu session/VDM; regression 006 runner, 007 renderer và Classic Workspace. Chỉ chạy bộ test ảnh hưởng khi triển khai, không lặp toàn bộ proof cũ.

Device proof 008 trên S23 DeX dùng **cùng một persisted Workspace hai app Waze + Calculator**: “Mở” chạy Classic; “Mở Embedded (Thử nghiệm)” tới route sản phẩm, người dùng bấm Bắt đầu; 007 renderer hiển thị hai pane và touch độc lập; Back/Stop cleanup không còn orphan; sau khi sạch, “Mở” lại chạy Classic. Kiểm tra Shizuku thiếu chỉ khi có thể làm mà không phá trạng thái thiết bị khác. Ghi ID Workspace và phase/cleanup outcome. Không chạy lại toàn ma trận 006/007. Chưa có device proof 008 trong tài liệu thiết kế này.

Hoãn sang milestone product-hardening: mode mặc định Embedded hoặc ghi nhớ, hỗ trợ >2 app, same-target concurrency, arbitrary-N proof, mixed Classic/Embedded trong cùng Workspace, mở rộng ma trận thiết bị, tự thiết lập Shizuku, dynamic guest geometry, focus/IME/keyboard, clipboard/audio và tương đương tính năng Classic. Không vượt hạn chế debugging/banking app. Không đổi schema, migration, license/security, production URL hoặc khóa tin cậy.

## Tự rà soát quyết định

“Mở” vẫn Classic; Embedded cần hai thao tác tường minh (chọn action, Bắt đầu); pre-start không cấp phát execution; Shizuku vẫn tùy chọn; không fallback ẩn; Workspace vẫn một row; mode không persist; ID là dữ liệu route duy nhất; cap 1–2 chỉ ở eligibility sản phẩm; coordinator không thành runner; gate nằm trên route và không nhả trước terminal-clean; Classic launcher, 006 runner và 007 renderer không bị sửa trong spec; proof route còn; Back chờ cleanup; cleanup bất định là `CLEANUP_BLOCKED`; phạm vi hỗ trợ thiết bị được trình bày là thử nghiệm. Tài liệu này chỉ là thiết kế, không phải xác nhận device PASS.
