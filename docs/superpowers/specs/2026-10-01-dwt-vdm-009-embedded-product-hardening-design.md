# DWT-VDM-009 — Thiết kế hardening và phục hồi Embedded Workspace

> **Trạng thái hiện hành (2026-10-01): Tasks 1–2 COMPLETE / targeted PASS; Task 3 BLOCKED_BY_RUNTIME_OWNERSHIP_MODEL; Task 3A COMPLETE; Task 3B PLANNED / NOT AUTHORIZED; Tasks 4–9 NOT AUTHORIZED.** [Task 3S scope amendment 009](2026-10-01-dwt-vdm-009-in-process-scope-amendment.md) chỉ amend tài liệu để review: runtime/UI thuộc route, Application product state chỉ giá trị; không tạo `EmbeddedProductRunOwner`. [Quyết định 009R-D](2026-10-01-dwt-vdm-009r-d-process-death-recovery-decision.md) giữ process death là unsupported boundary.
>
> Nội dung và approval bên dưới được giữ làm lịch sử; chỉ chi tiết hardening không xung đột còn là tham chiếu cho [plan 009 đã cập nhật](../plans/2026-10-01-dwt-vdm-009-embedded-product-hardening.md). Các yêu cầu Application-owned execution/cleanup, cleanup độc lập UI vô điều kiện và owner tiếp tục old run qua recreation bên dưới đã bị thay thế bởi Task 3S; chỉ cho phép bounded retention trong cleanup của host cũ, rồi giải phóng scope/route graph tại terminal. Task 0/Branch A/B, journal, Retry/reconcile tương lai và acceptance process restart không có hiệu lực; process-death recovery vẫn OUT OF SCOPE. [Báo cáo Task 3A](2026-10-01-dwt-vdm-009-task3-ui-independent-cleanup-boundary.md) giữ nguyên. 006–008 giữ CLOSED / PASS.

**Ngày:** 2026-10-01

**Trạng thái:** Đặc tả kiến trúc để duyệt; chưa triển khai
**Nhánh / baseline:** `release/1.0-beta` / `2c77e6af40a879604897e328f5f6a46441e9e69e`

## 1. Mục tiêu, phạm vi và quyết định

009 làm cho lỗi trước Start, khi chạy và khi dọn tài nguyên có lời giải thích, hành động phục hồi và bằng chứng chẩn đoán phù hợp. Luồng sản phẩm vẫn là `Mở` → Classic hoặc `Mở Embedded (Thử nghiệm)` → kiểm tra không cấp phát → `Bắt đầu Embedded`. Không tự chuyển sang Classic. Chỉ cho Classic chạy sau khi biết chưa cấp phát hoặc run cũ đã sạch có thẩm quyền. Shizuku chỉ là điều kiện của Embedded.

Không mở rộng 1–2 app hiện có thành 3+ hay arbitrary N; không same-target concurrency, mixed mode, Embedded mặc định, nhớ mode, schema/Room migration, dynamic guest geometry, focus/IME/keyboard, clipboard/audio, notification, license, release/update, root, tự bật debugging, Shizuku bypass hoặc tự cài/khởi động Shizuku. Không viết engine/runner/renderer mới, không đổi Classic launcher, `ActivityOptions`, production URL hay khóa tin cậy. Không xóa proof route. 006–008 giữ nguyên baseline; chỉ trở lại hợp đồng cũ nếu có blocker hardening cụ thể.

Các lựa chọn được cân nhắc:

| Cách xử lý | Lợi ích | Rủi ro | Quyết định |
| --- | --- | --- | --- |
| Dùng gate 008 và result 006, thêm trình bày lỗi và chủ sở hữu run sống qua UI | Giữ một nguồn sự thật về session | Cần chứng cứ khi cleanup bất định; process death cần khảo sát trước khi chọn policy | **Chọn cho hardening ban đầu** |
| Nút “thử lại” gọi `close()` lần nữa | Ít API | Controller hiện đóng vĩnh viễn và runner trả terminal result cũ | Không chọn |
| Quét/xóa mọi VDM, display hoặc association | Có thể làm UI hết kẹt | Xóa tài nguyên không thuộc run | Cấm |

## 2. Hợp đồng thực tế tại baseline

| Thành phần | Quan sát từ mã | Hệ quả cho 009 |
| --- | --- | --- |
| `EmbeddedProductRunGate` | Một owner `RunToken` so sánh bằng identity; pha `IDLE/STARTING/ACTIVE/STOPPING/CLEANUP_BLOCKED`; chỉ `PreflightRejected`, `StartFailed(allOwnedSessionsClean=true)` hoặc `Stopped` với toàn bộ outcome `Clean` nhả owner. `releaseWithoutAllocation()` tin lời caller. | Giữ gate app scope, nhưng chỉ gọi nhả không cấp phát khi chứng minh đường Start chưa đến runner; callback phải kèm đúng token và operation. |
| `EmbeddedWorkspaceProductController` | Mutex bao `start`, `requestExit`, `observeResult`; `requestExit` gọi `execution.close()`; `onHostDisposed()` chạy close rồi hủy run scope. | Đã chống phần lớn thao tác lặp trong một controller, chưa có retry/reconcile; scope/controller gắn `remember` của route và không thể tự phục hồi run sau recreation. |
| `EmbeddedWorkspaceRunnerController` | `startConsumed`, `closed`, `cleanupConverged`; `close()` đặt `closed=true`; terminal result được cache. | Gọi `close()` lại sau `CleanupIncomplete` không chứng minh lần dọn mới; không trình bày nút Retry trước khi có API thật. |
| `EmbeddedWorkspaceRunner` | Event queue hợp nhất `stop`/surface loss cùng cleanup đang chạy; sau terminal giữ `terminalResult`, `stop()` trả lại kết quả ấy. Có `Clean`, `Incomplete`, `RecoveryRequired`, `StartFailed.allOwnedSessionsClean`. | 006 cho kết quả cleanup có thẩm quyền của **lần chạy đó**, nhưng chưa có API retry cleanup sau terminal. Không biến `DuplicateCall` thành clean. |
| `EmbeddedAppSession` + connection manager | Session `stop/close` thử `stopSession(sessionId)`; khi thất bại có `reconcileAbsentSession(lease, service, generation)`. Điều kiện gồm lease còn sở hữu đúng session, cùng binder và generation còn sống, phản hồi `UNKNOWN_SESSION` khớp ID, và `serviceState.provesEmpty`; finalization chờ không còn in-flight/non-terminal. | Có reconcile **nội bộ cho một session còn lease trong process**, không có product API để reconcile một run đã mất controller/lease. Không bind chỉ để kiểm tra readiness. |
| `EmbeddedAppUserService` | Registry remote theo session ID; `getSessionState`, `getServiceState`, `stopSession`; `destroy()` cố dọn mọi entry. `UserServiceArgs.daemon(false)`, process suffix `embedded_app`. | Dữ liệu sở hữu nằm trong remote process; `getServiceState` chỉ là count, không chứng minh một run cụ thể sạch. |
| Runtime VDM/association | `createVirtualDevice(..., Binder(), ...)` trong UserService; cleanup dùng handle thiết bị, display và task ghi nhận; association tạo bằng `cmd companiondevice associate`, gỡ bằng MAC ghi trong `RemoteSessionRuntime`. | VDM gắn binder của UserService; association là tài nguyên hệ thống riêng. Không được suy diễn rằng process death đã gỡ association. Package name của task không đủ chứng minh quyền sở hữu. |
| Application/navigation/Home | Gate và parent scope ở `DexWorkspaceTouchApplication`; product route tự `remember` runner/controller/scope; Home đang hiện năm proof action ở cả bố cục rộng/hẹp; route proof vẫn khai báo trực tiếp. | Gate sống qua Activity recreation trong cùng process, nhưng host mới không có controller cũ. Process mới khởi tạo gate `IDLE`. Proof có thể chuyển khỏi mặt Home mà giữ route. |

Các điểm trên đọc từ `EmbeddedProductRunGate.kt`, `EmbeddedWorkspaceProductController.kt`, `EmbeddedWorkspaceRunnerController.kt`, `EmbeddedWorkspaceRunner.kt`, `EmbeddedWorkspaceRuntimeModels.kt`, `EmbeddedAppSession.kt`, `EmbeddedAppServiceConnectionManager.kt`, `EmbeddedAppUserService.kt`, `RemoteSessionRegistry.kt`, `RemoteSessionRuntime.kt`, `EmbeddedAppVdm.kt`, `AssociationShell.kt`, `DexWorkspaceTouchApplication.kt`, `TouchNavigation.kt`, `HomeScreen.kt` và `EmbeddedWorkspaceProductScreen.kt`. Các lỗ hổng hardening cụ thể: lỗi product hiện chủ yếu là chuỗi; probe không phân biệt chưa cài/đã dừng/đã từ chối quyền; nút permission không nghe kết quả để refresh; `remember` route không phục hồi controller; cleanup bất định chưa có hành động reconcile; Home còn nút proof thường trực.

## 3. Mô hình trạng thái lỗi sản phẩm

Tách **pha run** của gate khỏi **vấn đề** và **bằng chứng cleanup**. Snapshot UI gồm `phase`, `runIdentity`, `issue`, `allocationEvidence`, `cleanupEvidence`, `permittedActions`; `issue` typed, không dùng chuỗi làm điều kiện. `allocationEvidence` là `NONE_CONFIRMED`, `POSSIBLE_OR_OWNED`, `UNKNOWN`; mặc định `UNKNOWN` khi Start đã qua biên runner mà chưa có kết quả. `cleanupEvidence` là `NOT_NEEDED`, `CLEAN_CONFIRMED`, `INCOMPLETE`, `UNCERTAIN`. `CLEANUP_BLOCKED` là trạng thái gate, không phải tên lỗi thay cho nguyên nhân.

| Pha | Mã lỗi tối thiểu | Hướng xử lý |
| --- | --- | --- |
| Trước Start, dữ liệu | `MissingWorkspace`, `LaunchNotReady`, `UnsupportedLayout`, `UnsupportedEmbeddedItemCount`, `DuplicateTarget` | Chỉ sửa/chọn lại Workspace hoặc chọn Classic; không Start. `sourceCellId` khi có. |
| Trước Start, môi trường | `UnsupportedPlatform`, `GeometryUnavailable`, `ShizukuUnavailable` (kèm `NOT_INSTALLED`/`SERVER_STOPPED_OR_DISCONNECTED` nếu phân biệt được), `ShizukuPermissionMissing` (kèm `NOT_REQUESTED`/`DENIED`), `RendererNotReady` | Tác vụ theo nguyên nhân; `Kiểm tra lại` cho trạng thái có thể đổi. Không tạo session. |
| Runtime | `RuntimeStartFailed`, `SurfaceLost`, `RemoteDied` | Luôn ghép với cleanup outcome. Không tự Start lại. Surface mất phải đợi host mới hợp lệ trước run mới. |
| Cleanup | `RuntimeCleanupIncomplete`, `CleanupOutcomeUncertain`, `CleanupBlocked` | Giữ owner; khóa mọi dispatch Workspace xung đột; chỉ kết quả clean có thẩm quyền hoặc reconcile được chứng minh mới nhả. |

Thứ tự phân loại: dữ liệu/eligibility trước; readiness trước Start; sau Start dùng result runner và cleanup. `StartFailed(allOwnedSessionsClean=true)` là lỗi start với cleanup sạch, không phải `CLEANUP_BLOCKED`. `RemoteDied` hoặc `SurfaceLost` cũng vậy nếu runner chứng minh mọi owned session sạch. Nếu không có chứng cứ clean, cả hai vào `CLEANUP_BLOCKED`. `PreflightRejected` chỉ được coi chưa cấp phát khi 006 bảo đảm preflight trước session factory; không tự suy từ UI. `DuplicateCall`, exception và mất callback không chứng minh sạch.

## 4. Shizuku và refresh không cấp phát

Màn Embedded chỉ kiểm tra Shizuku sau khi người dùng chọn Embedded. Trên Library và Classic không probe/quyền Shizuku. Trạng thái cụ thể:

| Tình huống | Câu ngắn cho người dùng | Hành động khả dụng |
| --- | --- | --- |
| Chưa cài (chỉ khi PackageManager/intent xác nhận) | “Cần cài và chạy Shizuku để thử Embedded. Classic vẫn dùng được.” | `Mở Classic`, `Hủy`; liên kết cài đặt chỉ nếu đích an toàn được kiểm tra. |
| Đã cài nhưng server dừng, hoặc binder không kết nối | “Shizuku chưa chạy hoặc chưa kết nối. Hãy mở Shizuku và khởi động theo hướng dẫn của ứng dụng.” | `Mở Shizuku` **nếu** resolve được launch intent, `Kiểm tra lại`, `Mở Classic`, `Hủy`. Nếu không xác định đã cài, dùng câu chung “chưa khả dụng”. |
| Server chạy, chưa cấp quyền | “Embedded cần quyền Shizuku. Classic không cần quyền này.” | `Cấp quyền Shizuku` chỉ sau tap, `Kiểm tra lại`, `Mở Classic`, `Hủy`. |
| Người dùng từ chối quyền | “Quyền Shizuku chưa được cấp; kiểm tra quyền trong Shizuku rồi thử lại.” | Xin lại chỉ nếu API cho phép; nếu không, hướng dẫn mở Shizuku, `Kiểm tra lại`, Classic/Hủy. Không lặp hộp xin quyền tự động. |
| Quyền/server đổi khi màn đang mở | Cập nhật trạng thái; `Ready` chỉ khi toàn bộ capability, geometry, renderer đạt. | Callback binder/quyền đang có ở Shizuku hoặc `ON_RESUME`, cộng tap `Kiểm tra lại`; không polling loop. |
| Shizuku dừng khi ACTIVE | “Kết nối Shizuku đã mất; đang xác minh dọn phiên.” | Chờ result 006; nếu sạch cho phép run mới/Classic bằng lựa chọn tường minh, nếu không vào blocked. |
| Shizuku chết khi cleanup | “Chưa xác nhận dọn Embedded. Tạm khóa mở Workspace khác.” | Chẩn đoán; chỉ Retry khi đường reconcile sở hữu đúng run đã được triển khai và có kết nối thích hợp. |

Readiness refresh chỉ đọc platform capability, `Shizuku.pingBinder`/permission, geometry và trạng thái host/controller hiện có. Không `acquire` lease, `connect`, `bindUserService`, tạo session/VDM hay xin quyền. Trước tap `Bắt đầu`, đọc lại cùng snapshot để tránh Ready cũ. Permission callback/`ON_RESUME` phải gắn với route/run hiện tại; callback đến muộn không tự Start. `Mở Shizuku` chỉ hiện khi có launch intent an toàn. Không hứa DWT tự khởi động server, bật wireless debugging hay cài Shizuku. Nếu không phân biệt được “chưa cài” với “server dừng” bằng public API, giữ `ShizukuUnavailable` chung, không đoán.

## 5. `CLEANUP_BLOCKED` và reconcile

`CLEANUP_BLOCKED` nghĩa là Start có thể đã cấp phát và chưa có bằng chứng sạch cho **đúng run**. Owner giữ ở Application scope; Embedded mới và dispatch Classic từ mọi điểm vào sản phẩm bị chặn. Route có thể đóng **chỉ nếu** còn một recovery surface ở scope cao hơn (Home/banner hoặc route khôi phục theo owner), owner và scope cleanup còn sống, và navigation không nhả gate. Ở hiện trạng 008 chưa có recovery surface/host app scope đó, nên UI blocked không được pop như một cách giải quyết. Activity recreation phải hiển thị lại đúng blocked state, không tạo runner mới rồi coi đó là run cũ.

Bằng chứng đủ để nhả owner:

1. **Trước cấp phát:** runner trả `PreflightRejected`, hoặc product xác nhận Start chưa gọi runner/session factory; token tương ứng vẫn là owner. Không dùng `releaseWithoutAllocation` sau exception của `execution.start()`.
2. **Sau cấp phát:** `StartFailed(allOwnedSessionsClean=true)` với rollback đầy đủ, hoặc `Stopped` mà outcome cho **mọi** session của run là `Clean`; so số outcome với receipt/owned session để không có kết quả rỗng giả. Runner 006 là nguồn kết quả.
3. **Reconcile tương lai:** với run còn sở hữu, xác minh từng session ID trên cùng live UserService generation, `stopSession` trả thành công hoặc `UNKNOWN_SESSION` khớp ID cộng trạng thái service chứng minh không còn live/in-flight session liên quan, sau đó xác minh mọi owned task/display/VDM/association theo chứng cứ ID đã ghi. Trả `CleanConfirmed`, `StillOwned`, hoặc `Unknown`; chỉ `CleanConfirmed` nhả gate. Không dùng count toàn service một mình để kết luận run cụ thể sạch.

Hôm nay `EmbeddedAppServiceConnectionManager.reconcileAbsentSession` chỉ phục vụ session còn lease và cùng service generation. `EmbeddedWorkspaceRunner.stop()` sau terminal và `EmbeddedWorkspaceRunnerController.close()` sau `closed` trả kết quả đã cache; **không có product reconcile/retry API được hỗ trợ**. **Hardening ban đầu của 009 không triển khai product reconcile và không hiện Retry cleanup** hoặc nút “Đã sửa xong” giả. Khi blocked, hành động chỉ gồm hiển thị trạng thái đúng, `Sao chép chẩn đoán`, hướng dẫn hỗ trợ không giả định đã dọn sạch, và điều hướng an toàn nếu recovery surface ở app scope còn giữ ownership hiển thị. `Kiểm tra lại` ở §4 chỉ dành cho Shizuku/readiness **trước Start**; nó không nhả `CLEANUP_BLOCKED`. Nếu Task 0 hoặc một quyết định kiến trúc về sau yêu cầu Retry, trước tiên phải có API hẹp tại owner 006/session, không tạo runner mới để dọn hộ: `reconcile(runIdentity, ownedSessionIds): ReconcileResult` với kết quả typed trên, thao tác đơn luồng, idempotent và chứng cứ per resource. Product chỉ gọi và tiêu thụ kết quả, không giữ hoặc xóa VDM/task/association. Chỉ khi API ấy tồn tại mới xét UI/test Retry. Không force-stop Waze/Calculator, không xóa display/association theo package hoặc quét toàn máy.

Một `UNKNOWN_SESSION` đơn lẻ không đủ nếu còn live resource khác, remote mới không chứng minh trạng thái remote cũ, hoặc kết nối Shizuku chết giữa hai phép kiểm. Khi đó `Unknown` và tiếp tục blocked. Callback cũ sau khi đã có run mới không được nhả owner mới. Không suy “cleanup succeeded” từ route biến mất, Surface mất, Shizuku restart, app restart hay màn diagnostics trống.

## 6. Vòng đời, process restart và dấu vết bền vững

Trong **cùng process**, Application gate tồn tại qua Activity recreation. `EmbeddedProductRunOwner` ở Application scope chỉ giữ `RunToken`/generation, operation identity, terminal result, cleanup Job, diagnostics/recovery state và phần runner/execution lifetime **thật sự độc lập UI**. Owner không được giữ `Activity`, `Context` của Activity, `View`, `SurfaceView`, `Surface`, renderer host hoặc Compose callback/state. `EmbeddedRendererHost` ở route/Activity scope sở hữu SurfaceViews, Surface callbacks và viewport. Nếu execution hiện có giữ một trong các đối tượng UI đó, phải tách hoặc đóng qua đường fail-closed; không nâng nguyên controller/renderer lên app scope. Khi Activity cũ mất lúc ACTIVE, surface loss của host cũ đi qua đường fail-closed hiện có → `STOPPING` → clean hoặc `CLEANUP_BLOCKED`. Activity mới chỉ quan sát app-scoped state, **không gắn Surface mới để giả vờ run cũ vẫn ACTIVE**. Route recreation không giành token mới hay tạo cleanup cạnh tranh; nếu route mất khi chưa Start, xác nhận chưa cấp phát rồi nhả. Nếu người dùng rời route trong lúc cleanup, Home phải có recovery surface và Classic vẫn bị gate chặn.

**Process death khác Activity recreation.** Hiện gate chỉ ở RAM và sẽ `IDLE` ở process mới. Shizuku API mô tả UserService non-daemon bị dừng khi client bind process chết; cấu hình hiện tại là `.daemon(false)`. Android `VirtualDeviceImpl` gắn app binder token và `binderDied()` gọi `close()`, còn mã DWT tạo token đó trong UserService. Điều này hỗ trợ kết luận VDM/display gắn UserService sẽ đóng khi remote chết, nhưng không chứng minh mọi đường xử lý OEM hoàn tất đồng bộ khi DWT khởi động lại. `AssociationShell` lại tạo companion association bằng lệnh hệ thống; mã chỉ gỡ bằng MAC giữ trong remote object, không có `finally` bền vững trên process death. Guest task và association không được coi đã biến mất nếu chưa có proof thực nghiệm/đọc lại theo ID. Nguồn API: [Shizuku UserService non-daemon](https://github.com/RikkaApps/Shizuku-API/blob/master/api/src/main/java/rikka/shizuku/Shizuku.java), [Android VirtualDevice binder death](https://android.googlesource.com/platform/frameworks/base/+/HEAD/services/companion/java/com/android/server/companion/virtual/VirtualDeviceImpl.java).

**Process-death policy chưa được chọn. Task 0 là checkpoint khảo sát quyền sở hữu trước mọi thay đổi persistence/recovery.** Trên S23 DeX, thực hiện kill DWT client có kiểm soát khi ACTIVE và quan sát PID UserService, VDM, guest display, guest task, association bằng ID trước/sau restart; xác minh Shizuku non-daemon và binder death trên bản đang dùng. Khảo sát cleanup bất định bằng test/quan sát an toàn, không cố gây cleanup hỏng nguy hiểm. Ghi rõ dữ liệu nào là quan sát thực tế và dữ liệu nào còn chưa biết.

| Kết quả Task 0 | Chính sách 009 |
| --- | --- |
| **Branch A:** chứng minh UserService chết cùng DWT client; mọi VDM/display thuộc run biến mất; guest task không còn là execution Embedded đang hoạt động; association cũng được gỡ hoặc được chứng minh vô hại/sạch. | **Không** thêm persistent recovery marker trong 009. Ghi bằng chứng trên S23 và giới hạn phát biểu đúng cấu hình đã thử. Process mới có thể tạo gate `IDLE` theo hợp đồng đã chứng minh. |
| **Branch B:** bất kỳ tài nguyên có thể sống qua process death, **hoặc** không chứng minh được ownership/cleanup sau restart. | Persistent recovery evidence **bắt buộc trước khi tuyên bố process-restart safety**. Implementation plan phải **dừng ở hard decision checkpoint sau Task 0**; không tự triển khai marker theo giả định. Cần duyệt thiết kế journal/ownership riêng trước khi tiếp tục nhánh này. |

Nếu Branch B được duyệt, marker `PREPARED` trước cấp phát **chưa đủ**. Protocol tối thiểu là `PREPARED(runId, workspaceId)` → `RESOURCE_OWNERSHIP_RECORDED(exact positively-owned IDs)` → `TERMINAL_CLEAN_CONFIRMED`. Crash window trọng yếu: main process ghi `PREPARED`, UserService tạo association/VDM/display/task, rồi main chết **trước khi nhận và lưu các ID**. Khi đó journal chỉ biết “có thể đã cấp phát”, không có quyền xóa tài nguyên nào. Chỉ chấp nhận thiết kế nếu remote ghi bền vững exact identity **trước khi báo cấp phát thành công**, hoặc giao thức tạo tài nguyên bảo đảm truy hồi chính xác ownership từ stable DWT `runId`. Việc gửi ID về main “ngay sau khi tạo” không đóng crash window. Nếu không thể đạt một trong hai mà không sửa sâu 006/session runtime, báo **blocker** và dừng Branch B; không triển khai journal best-effort, không quét/xóa toàn cục.

Trong Branch B, record và recovery chỉ chứa/đọc ID đã xác thực thuộc đúng run; `TERMINAL_CLEAN_CONFIRMED` chỉ theo chứng cứ sạch có thẩm quyền. Khi restart gặp record chưa terminal, gate vẫn dùng **đúng năm pha 008**: `phase=CLEANUP_BLOCKED`, `issue=RecoveryCheckRequired`, `cleanupEvidence=UNCERTAIN`. `RecoveryCheckRequired` là issue, **không phải gate phase thứ sáu**. Classic và Embedded xung đột đều bị chặn cho tới chứng cứ sạch; nếu ownership không thể truy hồi, hiển thị blocked/hỗ trợ và báo blocker. Nếu association còn, chỉ xử lý đúng association đã ghi nhận qua đường có quyền, không xóa association khác. Không thêm Room/Workspace schema field cho journal. Bản 008 hiện không có recovery record; trạng thái RAM `IDLE` sau restart không tự chứng minh sạch.

## 7. Identity, thao tác lặp và callback cũ

Mỗi Start thành công qua gate có `RunToken` identity duy nhất; thêm `runGeneration`/operation sequence ở coordinator app scope cho mọi callback, cleanup Job và diagnostics. Tại điểm nhận kết quả, so owner bằng identity **và** generation/operation trước khi đổi pha hoặc nhả. `workspaceId` không phải identity của run. `RunToken` hiện đã ngăn token khác nhả owner nhưng `acceptResult` chưa loại mọi result sai thứ tự của **cùng** token (ví dụ `Started` đến sau `STOPPING`); 009 cần guard theo transition hợp lệ. Reconcile callback từ generation cũ chỉ được ghi diagnostics cũ, không đổi gate mới. Mọi đường clean phải chứng minh full owned-set và đúng run; `releaseWithoutAllocation` chỉ dùng khi chưa gọi runner.

| Tương tác | Hợp đồng bắt buộc |
| --- | --- |
| Double Start | Một lệnh giành token và gọi runner; lệnh sau trả `Busy`/same in-flight outcome, không cấp phát lần hai. Mutex/coordinator là chốt, không dựa button disabled. |
| Start + Back | Ghi exit intent, Start hoàn thành hoặc chuyển cleanup, rồi một close; không nhả gate khi kết quả Start còn bất định. |
| Double Back | Một cleanup Job; cả hai caller đợi cùng kết quả; pop tối đa một lần sau clean. |

Chỉ **nếu sau Task 0 hoặc một quyết định kiến trúc khác có owner-scoped reconcile API thật**, mới bổ sung hợp đồng Retry: Retry + Back cùng đợi một operation; hai Retry trả cùng kết quả/`Busy`; Activity recreation chỉ quan sát operation đang chạy; không hai `stopSession` đồng thời. Đây không phải acceptance bắt buộc của hardening ban đầu.

Controller 008 mutex và runner event queue là nền sẵn có, nhưng cần explicit run-scoped operation identity và owner ở Application scope, cao hơn route, để bao phủ recreation. Nếu process chết, generation cũ biến mất; policy Branch A/B ở §6 quyết định có cần recovery evidence bền vững trước khi tạo generation mới hay không.

## 8. UX chẩn đoán, proof và thông điệp hỗ trợ

UI thường chỉ có một câu mô tả nguyên nhân, trạng thái dọn và hành động hợp lệ. Developer/support snapshot typed, immutable theo lần cập nhật: `workspaceId`, product phase, run ID/generation rút gọn, readiness + Shizuku state, error code, `sourceCellId`, runner terminal result, per-cell cleanup outcome, owned session/VDM/display/task/association IDs **nếu đã xác thực**, timestamp UTC và provenance (`RUNNER_RESULT`, `SESSION_RECONCILE`, `UNKNOWN`). Không dump raw exception; dùng error code allowlist. Session IDs và resource IDs không có secret nhưng có thể liên hệ thiết bị, nên copy chỉ theo thao tác người dùng, không tự gửi.

`Sao chép chẩn đoán` tạo văn bản UTF-8 versioned, thứ tự trường ổn định, ví dụ:

```text
DWT Embedded diagnostics v1
timeUtc=2026-10-01T00:00:00Z
workspaceId=<id>
runId=<short-id>
phase=CLEANUP_BLOCKED
readiness=ShizukuUnavailable
error=CleanupOutcomeUncertain
sourceCellId=<id-or-none>
runnerResult=RecoveryRequired
cleanup=cell:<id>:RecoveryRequired
ownedVirtualDeviceIds=<known-ids-or-unknown>
ownedDisplayIds=<known-ids-or-unknown>
evidence=RUNNER_RESULT
```

Không sao chép tên/package app nếu không cần support, token license, khóa, raw Binder/Surface, MAC association ở UI thường, full stack trace, logcat hoặc dữ liệu Workspace canvas. Nếu association MAC cần để hỗ trợ khôi phục, chỉ hiển thị trong kênh developer có chủ ý và cân nhắc redaction khi copy mặc định; giữ ID chính xác trong record nội bộ. Giới hạn kích thước và số item bằng cap sản phẩm. Nếu không có snapshot, báo “Chưa có chẩn đoán Embedded” thay vì xuất dữ liệu rỗng. Copy là tùy chọn.

Home thường chỉ hiện Workspace Library với `Mở` (Classic) và `Mở Embedded (Thử nghiệm)` của Workspace đã chọn. Di chuyển năm proof action Waze, Calculator, Dual, `embedded-workspace-runner`, `embedded-workspace-layout/{workspaceId}` khỏi toolbar Home sang khu developer/experimental có chủ ý (ví dụ mục developer trong Giới thiệu/Chẩn đoán), không để lẫn với product action. Giữ route identifiers và direct navigation để test/proof vẫn hoạt động; không xóa màn proof. Khu developer chỉ thay khả năng khám phá, không được làm các proof route bypass gate của product nếu chúng dùng chung tài nguyên xung đột; cần guard tại entry khi có product owner.

Thông điệp capability: nếu platform/API/VDM hoặc Shizuku chưa đạt, ghi “Thiết bị hiện chưa đáp ứng điều kiện chạy Embedded thử nghiệm” và lý do cụ thể. Nếu check đạt trên máy khác, ghi “Thiết bị đáp ứng kiểm tra khả năng; Embedded vẫn đang thử nghiệm trên thiết bị này.” Chỉ ghi **“Đã kiểm chứng trên S23 Ultra, Android 16/API 36, DeX”** cho cấu hình đã proof; `SUPPORTED_BY_CAPABILITY_CHECK` không đồng nghĩa `DEVICE_VERIFIED`. Không kết luận toàn bộ Samsung/DeX được hỗ trợ hoặc gắn version blacklist không có bằng chứng.

## 9. Ma trận phục hồi cho người dùng

| Tình huống | Hành động chính | Classic? | Gate / bằng chứng |
| --- | --- | --- | --- |
| Shizuku chưa cài/dừng | Hướng dẫn Shizuku; `Mở Shizuku` nếu resolve được; `Kiểm tra lại` | Có, bằng tap riêng | Chưa allocation; `IDLE`. |
| Quyền thiếu/từ chối | Xin quyền bằng tap rõ ràng hoặc chỉnh trong Shizuku; kiểm tra lại | Có | Chưa allocation; `IDLE`. |
| Workspace thiếu/sai layout/count/target | Sửa/chọn Workspace | Có, bằng tap riêng | Không Start; `IDLE`. |
| Platform/geometry/renderer chưa sẵn | Giải thích; chờ host rồi kiểm tra lại nếu có thể | Có | Không Start; `IDLE`. |
| Start failed, rollback sạch | Xem lý do; muốn thử lại phải tạo run mới bằng `Bắt đầu` sau readiness | Có | Nhả khi `allOwnedSessionsClean` đáng tin. |
| Start failed, cleanup chưa chắc | Xem diagnostics; reconcile chỉ nếu có API thật | Không | `CLEANUP_BLOCKED`. |
| Remote died, cleanup sạch | Thông báo phiên đã kết thúc; người dùng chọn run mới | Có | Terminal clean. |
| Remote died, cleanup chưa chắc | Chẩn đoán/hỗ trợ, chờ chứng cứ | Không | `CLEANUP_BLOCKED`. |
| Surface loss | Đợi Surface mới; chỉ Start mới sau cleanup sạch | Chỉ khi sạch | Outcome quyết định, không đoán từ UI. |
| Back cleanup sạch | Rời route | Có sau pop | Terminal clean, gate `IDLE`. |
| Back cleanup incomplete | Ở màn blocked hoặc Home recovery surface | Không | Owner vẫn giữ. |
| Activity recreation ACTIVE/cleanup | UI mới quan sát owner, host cũ đi qua close/cleanup một lần | Chỉ khi sạch | Không tái dùng Surface cũ. |
| Process restart | Theo Branch A/B sau Task 0; Branch B cần checkpoint và journal đóng crash window trước khi tuyên bố an toàn | Chỉ khi xác minh không còn execution xung đột | `RecoveryCheckRequired` là issue dưới `CLEANUP_BLOCKED` nếu Branch B có record chưa terminal. |

`Hủy`/Back trước Start chỉ rời route; sau Start nghĩa là yêu cầu kết thúc và chờ kết quả. `Mở Classic` chỉ gọi Classic pipeline hiện tại sau khi gate cho phép; không sửa launch flags/`ActivityOptions` và không thêm điều kiện Shizuku cho Classic. Các điểm vào Classic khác có thể xung đột cũng phải tôn trọng gate trong khi ownership bất định; không dùng trạng thái enabled của một nút làm bảo vệ duy nhất.

## 10. Kiểm thử và điều kiện chấp nhận của bản triển khai sau

**Unit/integration tập trung cho hardening ban đầu:** mapping từng lỗi typed và action availability, không fallback generic; Shizuku stopped/missing/denied/granted sau khi mở màn; probe/refresh không lease/bind/session/VDM và không tự xin quyền; gate giữ owner qua route/Activity recreation, stale token/late callback không nhả run mới, result sai thứ tự không đưa `STOPPING` về `ACTIVE`; double Start/Back và Start+Back; cleanup clean/incomplete/remote death, không đụng unrelated resource; app owner không giữ Activity/View/Surface; diagnostics ổn định, không secret. **Không** yêu cầu Retry+Back, double Retry, recreation khi Retry hoặc product reconcile test trong acceptance ban đầu. Chỉ nếu có reconcile API thật mới thêm các test đó. Test marker/crash window chỉ áp dụng nếu Task 0 chọn Branch B và thiết kế journal được duyệt. Regression: 006 runner, 007 renderer, 008 integration và Classic Workspace. Chỉ chạy test phù hợp khi triển khai, không lặp matrix cũ vô lý.

**Device proof nhỏ trên S23 Ultra DeX:** Shizuku dừng → đúng thông điệp và zero allocation; mở Shizuku thủ công → callback/`Kiểm tra lại` thành Ready không restart DWT; Start → ACTIVE; một lỗi phục hồi an toàn nếu gây được; Back → cleanup sạch, zero orphan; xem/copy diagnostics. `CLEANUP_BLOCKED` có thể xác minh bằng unit/integration nếu fault injection trên máy nguy hiểm. Task 0 process-death ownership characterization ở §6 là **hard checkpoint trước khi chọn policy**, không suy PASS từ màn hình trống. Không chạy lại proof 006–008 toàn bộ.

**Chốt thiết kế 009:** lỗi có type và hành động đúng; Shizuku tùy chọn; mọi refresh trước Start zero allocation; blocked chỉ nhả với chứng cứ cùng run; hardening ban đầu không có cleanup Retry/product reconcile; app owner không giữ UI objects; Task 0 chọn Branch A hoặc dừng ở checkpoint Branch B trước persistence; nếu Branch B cần journal thì phải đóng remote-allocation crash window hoặc báo blocker; gate vẫn chỉ có năm pha; diagnostics không lộ secret; proof routes còn; copy thiết bị chỉ nói mức đã kiểm chứng. Tài liệu này không khẳng định các hành vi ấy đã được triển khai hoặc device PASS.
