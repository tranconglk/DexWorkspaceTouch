# DWT-VDM-009 — Task 3C: Bounded connect / runner event-loop boundary

**Ngày:** 2026-10-01 (Asia/Saigon). **Loại:** architecture / source inspection only, dừng để review.

**Project:** `D:\AndroidStudioProjects\DexWorkspaceTouch`. **Branch:** `release/1.0-beta`. **HEAD/baseline đã đối chiếu:** `2c77e6af40a879604897e328f5f6a46441e9e69e`.

**Quyết định duy nhất:** `FEASIBLE_WITH_NARROW_CONNECTION_BOUNDARY_CHANGE`.

Đây là kết luận khả thi của kiến trúc đề xuất, không phải runtime PASS. Task 3B vẫn `BLOCKED_BY_UNBOUNDED_CONNECT_BOUNDARY` trên source hiện tại; chưa được triển khai lại. Tasks 1–2 giữ COMPLETE / targeted PASS theo evidence trước; Task 3A COMPLETE, Task 3S APPROVED. Tasks 4–9 NOT AUTHORIZED. Lượt 3C chỉ tạo tài liệu này; không sửa production/tests, Job ownership, AIDL, plan hoặc trạng thái của task khác; không build/device, stage/commit/push.

Phương án tối thiểu là **A + B + C + E**, kèm loại mọi external IPC khỏi các monitor mà connect/Stop/Close cần dùng. Chỉ đưa callback ra ngoài lock hoặc chỉ chuyển `getUid()` sang worker hiện có đều không đủ. Ba điều kiện quyết định được lập luận bằng thứ tự thao tác và phân tách lane ở mục 8; implementation, test và heap/lifetime thực tế đều **NOT VERIFIED**.

## 1. Source và giới hạn evidence

Đã đọc trực tiếp các file dưới đây, dùng lại phần source không đổi đã đọc ở checkpoint 3B và bổ sung các boundary/tests liên quan. Line được ghi theo working tree hiện tại, gồm thay đổi có sẵn của Tasks 1–2.

| Source | Điểm đã kiểm tra |
| --- | --- |
| [EmbeddedWorkspaceRunner.kt](../../../app/src/main/java/com/trancong/dexworkspacetouch/workspace/execution/embedded/runtime/EmbeddedWorkspaceRunner.kt) | Scope/event actor (24, 52–55), reply await (59–74), snapshot/timeout (186–261), connect/READY (264–304), cleanup (364–488). |
| [EmbeddedWorkspaceSessionPort.kt](../../../app/src/main/java/com/trancong/dexworkspacetouch/workspace/execution/embedded/runtime/EmbeddedWorkspaceSessionPort.kt) | `create`, `connect`, `start`, `stop`, `close`, observer; chưa có non-blocking hoặc notification-detach contract. |
| [AndroidEmbeddedWorkspaceSessionFactory.kt](../../../app/src/main/java/com/trancong/dexworkspacetouch/workspace/execution/embedded/runtime/AndroidEmbeddedWorkspaceSessionFactory.kt) | Factory tạo generic session; adapter gọi thẳng `connect/stop/close`, không đổi execution lane. |
| [EmbeddedAppSession.kt](../../../app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedapp/EmbeddedAppSession.kt) | Listener (36–53), connect (57–65), UID (42), single-thread worker (26), start/stop/close (67–194), callback capture. |
| [EmbeddedAppServiceConnectionManager.kt](../../../app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedapp/EmbeddedAppServiceConnectionManager.kt) | Lease/bookkeeper/finalization, ba listener paths, monitor, generation, binding, reconcile, removal. |
| [EmbeddedAppSessionState.kt](../../../app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedapp/EmbeddedAppSessionState.kt) | `EmbeddedSessionSnapshot`, lifecycle, `SessionConnectionCoordinator`, ID và cleanup evidence. Hai coordinator nằm trong file này. |
| [EmbeddedAppModels.kt](../../../app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedapp/EmbeddedAppModels.kt) | Target/geometry là values; `SessionStopCoordinator` giữ một logical stop và điều kiện release lease. |
| [IEmbeddedAppService.aidl](../../../app/src/main/aidl/com/trancong/dexworkspacetouch/feature/embeddedapp/remote/IEmbeddedAppService.aidl), [EmbeddedAppUserService.kt](../../../app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedapp/remote/EmbeddedAppUserService.kt) | `int getUid() = 0` là synchronous; implementation chỉ trả `Process.myUid()`. Không có timeout/cancel protocol. |
| [EmbeddedWorkspaceProductScreen.kt](../../../app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/product/EmbeddedWorkspaceProductScreen.kt) | Production construction (114–136): application run scope, Android factory, proof timeout policy, **`dispatcher = Dispatchers.Main.immediate`**. |
| [EmbeddedWorkspaceRunnerController.kt](../../../app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/EmbeddedWorkspaceRunnerController.kt), [EmbeddedWorkspaceRendererCoordinator.kt](../../../app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/EmbeddedWorkspaceRendererCoordinator.kt) | Adapter Start/Close tới `runner.start()/stop()`, không có thread hop cứu event actor. |
| [DexWorkspaceTouchApplication.kt](../../../app/src/main/java/com/trancong/dexworkspacetouch/DexWorkspaceTouchApplication.kt), [EmbeddedWorkspaceProductController.kt](../../../app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/product/EmbeddedWorkspaceProductController.kt) | Run scope là child của process Job; disposal chờ `requestExit()` rồi mới cancel run scope. |
| [EmbeddedWorkspaceRuntimeModels.kt](../../../app/src/main/java/com/trancong/dexworkspacetouch/workspace/execution/embedded/runtime/EmbeddedWorkspaceRuntimeModels.kt) | READY 10s, ACTIVE 30s, cleanup 20s/owned item; result/receipt authority của 006. |

Generated debug AIDL đã tồn tại cũng được đọc: `getUid()` dùng `mRemote.transact(..., _reply, 0)` rồi `readException()/readInt()`. Đây là evidence bổ sung, không build lại hoặc sửa generated file; AIDL source là thẩm quyền. Theo [Android IBinder](https://developer.android.com/reference/android/os/IBinder), RPC synchronous chờ callee trả về. Không có platform contract đã xác lập ở đây cho interrupt làm kết thúc transaction.

Tài liệu nền: [Task 3S amendment](2026-10-01-dwt-vdm-009-in-process-scope-amendment.md), [Task 3A ownership report](2026-10-01-dwt-vdm-009-task3-ui-independent-cleanup-boundary.md), [plan 009 / Task 3B](../plans/2026-10-01-dwt-vdm-009-embedded-product-hardening.md), [006 session contract](2026-09-28-dwt-vdm-006-embedded-workspace-runner-design.md).

## 2. Blocking call graph hiện tại

```text
Product screen: runner dispatcher = Main.immediate
  → renderer.start() → RunnerController.start() → runner.start(request)
  → Event.Start → runner actor → startNextItem()
  → Android handle.connect() → EmbeddedAppSession.connect()
  → acquire lease; assign session.lease; lease.connect()
  → @Synchronized manager.connect()
  → remote-already-available: listener.onServiceReady(service), vẫn giữ monitor
  → session listener: set remote; lifecycle.serviceReady(); publish READY
  → synchronous service.uid / AIDL getUid()
  → connect phải trả về
  → runner mới schedulePhaseTimeout(READY)
```

Khi chưa có remote, `connect()` còn gọi Shizuku permission/bind API dưới manager monitor. Callback `onServiceConnected()` sau đó cũng gọi listener dưới cùng monitor, trên callback thread; nếu đó là Main, nó chặn dispatcher dùng chung với runner. Ngay cả khi callback chạy trên thread khác, monitor bị giữ vẫn có thể chặn runner khi Start/Stop cần manager.

`getUid()` ở remote là hàm ngắn không tạo tài nguyên; điều này không cung cấp deadline cho Binder dispatch, process scheduling hoặc callee đang không trả lời. Finding `BLOCKED_BY_UNBOUNDED_CONNECT_BOUNDARY` được giữ nguyên.

Đặt READY timer trước `handle.connect()` chỉ tạo timer/job hoặc xếp timeout event; actor vẫn đang ở call stack blocking nên không xử lý Stop, SurfaceLost hoặc timeout. Đổi riêng runner dispatcher sang IO cũng không giải quyết: actor tuần tự vẫn mắc trong cùng call stack.

## 3. Manager monitor và listener contract

### Ba đường gọi listener hiện có

| Đường | Lock/state hiện tại | Snapshot + dispatch đề xuất |
| --- | --- | --- |
| Remote đã có trong `connect()` | `@Synchronized`, kiểm tra lease, gọi `onServiceReady(remote)` trực tiếp. | Dưới lock xác nhận lease/registration còn sống, snapshot Binder identity + service generation + event sequence; nhả lock rồi enqueue notification. |
| `onServiceConnected()` | `synchronized(manager)`, kiểm tra `connection === this`, tăng generation, gán remote, snapshot listeners và gọi tất cả dưới lock. | Kiểm tra connection/bind epoch, commit remote/generation và snapshot registrations dưới lock; delivery bên ngoài lock theo sequence. Callback lỗi/hung của một registration không được giữ manager lock hoặc runner thread. |
| `onServiceDisconnected()` | Cùng lock, kiểm tra connection, tăng generation, xóa remote/connection, gọi listeners dưới lock. | Commit disconnect generation trước; snapshot registrations, nhả lock rồi enqueue disconnect. Ready/UID của generation cũ phải bị vô hiệu dù delivery đến muộn. |

Listener envelope cần có `sessionId`, registration identity/epoch, service generation, connection operation identity, event sequence và Binder identity cho event có service. Binder chỉ ở runtime boundary, không nằm trong Application product status. Không dùng callback không có generation như interface hiện tại để suy ra rằng service vẫn là service đang hoạt động.

Session ingress chỉ enqueue vào lane xử lý state, không gọi Binder hoặc callback UI trên notification thread. Manager không gọi listener dưới monitor, kể cả callback sticky/reentrant do đăng ký Shizuku listeners. Notification dispatch không chạy trên runner actor và không chờ listener hoàn tất. Delivery sử dụng registration/mailbox có thể detach, thay vì giữ trực tiếp controller/route trong queue.

**Snapshot không tự loại race.** Kiểm tra listener còn trong map rồi gọi ngoài lock vẫn có khoảng release/disconnect ở giữa. Phải kiểm tra fence tại nơi tiêu thụ event; `sessionId` một mình không đủ. Remote identity thay đổi chỉ được commit trên một control lane tuần tự; UID completion và session Stop/Close được xử lý trên lane state tuần tự tương ứng. Không thực hiện external work trong đoạn kiểm tra-and-apply.

### Các lock path phải xử lý cùng boundary

Không chỉ ba listener paths: `acquire()` đăng ký sticky Shizuku listeners dưới lock; `connect()/bind()` gọi Shizuku; `reconcileAbsentSession()` giữ lock qua `getSessionState()` và `getServiceState()`; `FinalRemovalCoordinator.maybeFinalizeServiceRemoval()` gọi `queryRemote/removeService` dưới monitor của nó; `removeService()` gọi `unbindUserService` dưới manager lock. Các call này phải trở thành **local state transition → external request ngoài lock → fenced value completion**. Nếu giữ bất kỳ external call nào dưới monitor mà cleanup cần dùng, kết luận khả thi không áp dụng.

Để giữ lease/generation/final removal semantics:

1. Membership, registration, in-flight/non-terminal sets, service/bind generation và inventory revision chỉ cập nhật bằng thao tác local ngắn dưới lock. Không callback, `Future.get`, join, Binder, Shizuku hoặc executor queue wait trong lock.
2. Reconcile lấy snapshot exact lease/session/service/generation/revision; gọi IPC ngoài lock; revalidate tất cả trước khi chấp nhận absence + aggregate-empty evidence. Failed/stale query không xóa non-terminal ownership.
3. Final removal chỉ đủ điều kiện khi leases/in-flight/non-terminal đều rỗng và exact remote aggregate có đủ bốn count bằng 0. Snapshot empty bị vô hiệu khi revision/generation đổi.
4. Sau revalidation, reserve một removal operation trong process dưới lock; admission lease/bind/start mới phải trả Busy/fail closed ngay khi service đang removal, không đợi monitor hoặc IPC. Nhả lock mới unbind exact connection/args. Matching completion mới xác nhận removal và mở admission cho cycle mới.
5. Unbind/query hung hoặc bất định không được đánh dấu service đã removed. Giữ reservation/evidence fail closed; không retry, global scan hoặc recovery. Đây chỉ là serialization trong process, không protocol pending/commit hay journal qua restart.
6. Release listener/local lease và acknowledgement của exact session phải tách khỏi việc chờ global finalization IPC. `STOPPED` vẫn chỉ đến từ scoped cleanup thành công và local lease release đã xác nhận theo contract 006; nếu thiếu acknowledgement, runner hết bound trả incomplete. Không dùng việc detach notifications làm clean evidence.

Thứ tự nhả local lease và service removal trở nên async, nên timing quan sát được thay đổi và phải được review/test. Điều kiện removal, số lần logical stop, reverse cleanup order và quyền terminal của 006 không được đổi.

## 4. UID: mục đích và alternative

`getUid()` kiểm tra service thực sự chạy với shell UID 2000; UI dùng kết quả cho `shellReady` và thông báo. [EmbeddedAppVdm.kt](../../../app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedapp/remote/EmbeddedAppVdm.kt) và [ShellDisplayLaunch.kt](../../../app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedapp/remote/ShellDisplayLaunch.kt) còn kiểm tra `Process.myUid() == 2000` trước thao tác đặc quyền. Không xóa hoặc thay bằng giả định rằng permission/bind thành công nghĩa là shell.

**Chi tiết hiện tại:** session gán remote và publish lifecycle `READY` *trước* `service.uid`. UID sai chỉ làm `shellReady=false`; typed READY không bị chặn. Kiến trúc đề xuất chỉ phát READY sau UID 2000 của exact generation được chấp nhận. Đây là thay đổi observable có chủ đích cần phê duyệt; không được mô tả là chỉ chuyển thread mà giữ mọi timing/behavior cũ.

| Alternative | Kết luận |
| --- | --- |
| `Shizuku.getUid()` | UID của Shizuku server, không phải attestation cho exact `IEmbeddedAppService` Binder/generation. [API source chính thức](https://github.com/RikkaApps/Shizuku-API/blob/master/api/src/main/java/rikka/shizuku/Shizuku.java) có cached `serverUid`, nhưng cold path gọi RPC. Không có bounded exact-service replacement đã chứng minh. |
| `Binder.getCallingUid()` / `Process.myUid()` tại client | Xác định caller/process hiện hành, không truy ra UID của peer qua một outgoing Binder proxy. Không thay kiểm tra UserService. |
| Permission, `isBinderAlive`, UserServiceArgs/component/package | Capability/liveness/construction metadata, không chứng minh UID 2000 của service đang trả lời. |
| `remoteUid` trong `getServiceState()` hoặc reply khác | Có field trong implementation, nhưng vẫn là IPC synchronous; Start reply đến sau allocation, quá muộn để thay readiness check. |
| Đổi AIDL sang callback/oneway handshake | Có thể thiết kế riêng nhưng cần đổi protocol/service; không cần thiết cho phương án tối thiểu và không đề xuất trong checkpoint này. |

Dependency của app là Shizuku API 13.1.5. Đã thử đọc `getUid()` bytecode từ AAR cache: snippet cho thấy cached field và `IShizukuService.getUid()`, nhưng `javap` báo `AccessDeniedException`; kiểm chứng binary đầy đủ **NOT VERIFIED**. Không dùng snippet này để tuyên bố có trusted primitive mới. API master chỉ là nguồn bổ sung về alternative, không coi nó là source pin của dependency.

Không tìm được primitive đã chứng minh thay thế exact UID check trong source/construction đã kiểm tra. Minimum async protocol giữ AIDL `getUid()` hiện có, thực hiện trên lane riêng và trả **value completion** có identity + uid/error code; exception được project thành code, không giữ Throwable trong status/mailbox.

## 5. Execution lanes và cancellation

| Lane | Công việc | Điều cấm để giữ cleanup độc lập |
| --- | --- | --- |
| Runner actor / Main.immediate | Event ordering, timers, owned set, terminal result; gọi port bằng request fire-and-return. | Không Binder/Shizuku, không chờ connect task, Future hoặc lock đang giữ IPC. |
| Session/manager control | Commit state, fence completion, lease bookkeeping, Stop/Close intent; callbacks chỉ enqueue. State/service transitions được serialize, UI publication trên Main. | Không chạy verification/transport IPC; không callback tùy ý trong monitor. |
| Connection transport | Shizuku registration/bind/permission external work sau khi snapshot local. | Không dùng single-thread session worker; không giữ monitor hay synchronous consumer tới route. |
| UID verification | `getUid()` trên exact candidate Binder, sau đó enqueue value completion. | Không dùng session worker, runner dispatcher hoặc shared connection/control lane mà cleanup phải đợi. |
| Session worker hiện có | Start/touch/scoped stop/close IPC của exact session, giữ ordering và một logical stop. | Không thêm UID/connect/bind task vào queue này. Cleanup không join connection transport hoặc verifier. |
| Manager finalization IO | Query/reconcile/unbind ngoài locks, publish fenced result. | Không giữ manager/finalization monitor trong lúc IPC; cleanup deadline không chờ lane này. |

Lane riêng nghĩa là executor resource riêng, không chỉ tên coroutine hoặc `withContext(IO)` dùng một queue chung rồi await. Thiết kế tối thiểu dùng admission không chờ: connection transport một worker, UID verification pool cố định tối đa hai operation đang chạy, finalization IO một worker; queue phải hữu hạn, không caller-runs/retry/spawn thêm thread khi saturated. Có thể chia sẻ UID verification giữa các lease của cùng exact service generation bằng mailbox registrations, không giữ danh sách callback trong IPC task. Khi hết capacity, connect trả request failure và structured failure event; không chặn actor. Con số pool là giới hạn đề xuất, cần kiểm chứng với proof clients; không suy cap product 1–2 là cap mọi session trong process.

Timeout của startup/cleanup vẫn thuộc runner, bao gồm thời gian chờ lane. Stop/Close hoặc READY timeout invalidates connection operation, không chờ executor termination. UID sai/failure không phát READY, đi qua typed failure/cleanup hiện có. Binder death giữ `REMOTE_DIED`/`RecoveryRequired`; không gộp nó thành ordinary clean.

Theo [Java Future.cancel](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html#cancel(boolean)), interrupt chỉ là attempt để dừng task. Thiết kế giả định transaction **có thể không bao giờ trả về**; không coi cancel, shutdown hoặc `withTimeout` là acknowledgement đã ngắt Binder. Bounded pool có thể mất slot vĩnh viễn; request mới fail closed. Đây là giảm liveness có chủ đích, không process-death recovery và không thread leak không giới hạn.

IPC task phải là task riêng không capture `EmbeddedAppSession.this`, listener, runner, observer, Surface, Context của route hoặc callback UI. Nó chỉ giữ Binder candidate, IDs và mailbox có thể detach. Khi Stop/timeout/terminal, mailbox bỏ consumer; task đang hung không giữ consumer trong local variable qua lời gọi IPC. Completion đến sau đó chỉ chứa value và bị drop. Notification queued cũng phải dùng mailbox/delivery identity có thể detach, không queue closure đã capture session.

Để giải phóng route callbacks khi worker khác còn pending, session cần explicit notification-detach hook, độc lập `close()/lease release`. Runner gọi hook sau khi chốt/copy terminal evidence; hook bỏ `changed/lifecycleChanged` consumer và registration consumer, không xóa ownership bookkeeping hoặc gọi cleanup mới. Không detach lifecycle trước terminal đến mức mất clean/death evidence. Task hung giữ callback trong stack hoặc queue không thể detach là điều kiện bác bỏ implementation tương lai.

## 6. Cleanup khi connection/UID không bao giờ trả về

`EmbeddedAppSession.worker` hiện là single-thread executor dùng chung Start/touch/Stop/Close. Chuyển UID vào nó sẽ làm `worker.execute(stop/close)` đứng sau UID hung; không đạt acceptance. Phương án giữ connection/UID ở ngoài worker và ngoài mọi monitor của cleanup.

```text
connect request → local epoch/lease + enqueue external request → return
UID lane: getUid() hangs forever; không giữ manager/session lock hoặc route consumer
Stop/SurfaceLost event → runner.beginCleanup() → lập cleanup timer
  → handle.stop()/close(): enqueue/local state intent, return; invalidate connect epoch
  → session worker/control path vẫn chạy, không join verifier
  → terminal snapshot có thẩm quyền, hoặc cleanup timer event tới hạn
  → finishCleanup(): copy result, complete Start reply + terminal waiters, clear waiters
  → detach notifications/cancel route waiters theo terminal boundary, drop late completion
```

Nếu scoped cleanup chưa chứng minh clean, timer vẫn tạo `Incomplete(CLEANUP_TIMEOUT)`; nếu snapshot mất remote certainty, dùng `RecoveryRequired`. Không tạo synthetic `Stopped` từ UID timeout/null, việc cancel verifier hoặc việc bỏ listener. Một terminal clean đến sớm chỉ hợp lệ khi đáp ứng contract exact-session cleanup/lease release 006.

Product policy có cleanup 20s/owned item, cap 1–2: tối đa 20/40s chờ terminal sau `beginCleanup()`, cộng độ trễ scheduling. Nếu không có Stop, READY 10s có thể mở rollback trước, sau đó cleanup dùng bound riêng. Không gọi đây là deadline cứng 40s từ host disposal hoặc deadline chấm dứt IPC; scheduler/Main phải được chạy bình thường. Connection work đề xuất không được là nguyên nhân chặn scheduler đó.

`finishCleanup()` hiện hoàn tất reply và clear terminal waiters, nhưng không detach mọi observer, clear prepared/owned Surface holders hay dừng actor. Notification-detach hook và bỏ launch-only holders sau khi terminal result đã copy là phần lifetime nhỏ cần thiết nếu muốn chứng minh bỏ route callbacks khi IPC pending; không thay receipt/owned-set authority. Toàn bộ graph release của 3A/3S không tự thành PASS từ thiết kế connect này. Hung Start IPC mang Surface, shared proof-route capture và parent Job lifecycle vẫn phải được đối chiếu trong scope implementation được duyệt; không giải quyết chúng bằng cancellation giả.

## 7. Timeout/event-loop và runnerScope Job

Port contract đề xuất:

- `create/connect/stop/close` trên đường actor chỉ làm local bounded work và enqueue không chờ; rejection enqueue cũng phải trả về.
- READY/FAILED/death được nhận qua snapshot/event, không return result Binder trên stack của connect. Fake có thể phát snapshot synchronous nếu observer chỉ enqueue và không IPC; production READY phải qua verified value completion.
- Stop/SurfaceLost được runner serialize. `cleanupActive` và terminal phase luôn chặn startup; snapshot sau terminal không thay terminal evidence. Session operation fence chặn READY sau Stop/Close, kể cả callback source cell còn đúng.
- Identity ở manager→session boundary phải kiểm tra exact registration/service/generation/operation; sourceCellId hiện tại của runner không thay được các fence đó. Mỗi owned handle vẫn có exact session ID, startup tuần tự và rollback đảo ngược như 006.

Current scope:

```kotlin
CoroutineScope(scope.coroutineContext + SupervisorJob() + dispatcher)
```

`SupervisorJob()` không truyền parent (`parent = null`) và thay phần tử Job của `scope.coroutineContext`. Dispatcher/context còn lại được kế thừa nhưng parentage không được kế thừa. Vì vậy cancel route/run scope **không cancel runner Job**. Đây là semantics của [source kotlinx.coroutines 1.10.2](https://raw.githubusercontent.com/Kotlin/kotlinx.coroutines/1.10.2/kotlinx-coroutines-core/common/src/Supervisor.kt), khớp dependency đang dùng.

Runner đã dùng expression này từ commit `2f355f4` tạo orchestration. Commit message và phần spec 006 đã đối chiếu không giải thích lý do lựa chọn parentage. Phân loại mục đích ban đầu: **NOT VERIFIED**, không gọi chắc chắn là intentional cleanup lifetime hoặc accidental detachment.

Product hiện tạo child run scope từ process Job, và disposal cancel nó trong `finally` *sau* `requestExit()` hoàn tất. Trên đường đó, cleanup có thể chạy trước cancel mà không cần runner detach khỏi run Job. Chưa có evidence product cần detached runner cho guarantee cleanup khác; mutex Start đang chặn Back vẫn là lỗi Task 3B, không được sửa ở 3C.

Nếu chỉ đổi thành `SupervisorJob(scope.coroutineContext[Job])`, cancellation trước terminal sẽ hủy actor/timers trong lúc replies đang await, có thể làm cleanup mất cơ hội đi tới timeout. Route coroutine chết ngay và run scope application-child được giữ tới terminal là hai lifetime khác nhau; không được đổi parent mà chưa khóa thứ tự này. Checkpoint không đổi Job ownership, không tuyên bố parentage fix đã được duyệt. Termination/detach sau terminal là concern riêng phải review, không dùng independent Job để biện hộ retention vô hạn.

## 8. Race matrix và chứng minh ba điều kiện quyết định

| Race | Fence/order cần thiết | Kết quả |
| --- | --- | --- |
| Ready snapshot → lease bị release trước delivery | Registration identity/epoch + detached consumer, kiểm tra tại session ingress. | Drop; không set remote/READY hoặc resurrect lease. |
| Ready(g) → disconnect(g+1) → UID(g) trả về | Commit disconnect generation trước dispatch; completion so exact Binder/generation/operation và phase CONNECTING. | Drop UID(g); giữ death/terminal, không startup. |
| UID Ready và Stop cùng lúc | Session state lane serialize; Stop invalidates operation trước khi chờ cleanup. Runner FIFO + cleanupActive guards startup. | Stop xử lý trước thì không launch; READY xử lý trước có thể launch theo ordering cũ, Stop sau vẫn một cleanup. |
| Close lúc bind chưa callback | Closed/operation epoch; late `onServiceConnected` phải khớp active connection và bind epoch. | Không attach old session; không tạo token/run mới. Late bind không chứng minh cleanup clean. |
| Service replacement dùng cùng Binder object | Generation vẫn khác, không chỉ so `===`. | UID/Ready cũ bị drop. |
| Old disconnected callback sau connection mới | `connection === callbackConnection` + bind epoch/generation. | Không xóa remote mới hoặc notify death cho generation mới. |
| Callback snapshot rồi listener removal | Snapshot có registration mailbox; removal/detach invalidates consumer trước terminal return. | Snapshot cũ không giữ route consumer dài hạn; no delivery sau invalidation. |
| Empty-state query rồi acquire/start mới | Inventory revision + service generation revalidate trước reserve removal; reservation từ chối admission mới ngay. | Không unbind service của lease mới; query stale không có authority. |
| Old removal completion sau generation khác | Exact removal identity/connection/generation; admission đóng khi removal chưa rõ. | Không clear remote mới, không giả removed/clean. |
| Verifier/transport pool saturated | Bounded admission, rejection không caller-runs hoặc chờ. | Actor vẫn xử lý Stop/timeout; failure có identity, không allocation vì retry ẩn. |
| READY timeout và UID success | Operation invalidated bởi failure/Stop; matching phase/epoch; runner phase wait token. | Chỉ một startup outcome được chấp nhận, late success không restart. |
| Terminal incomplete trong lúc UID còn hung | Value terminal copied trước detach; task không capture session; ingress/observer consumer được gỡ. | Waiters kết thúc, product giữ blocked value status; không giữ execution như recovery handle. |

**1 — Event loop luôn lấy lại quyền điều khiển sau connect:** đường inline kết thúc tại local transition + non-blocking enqueue hoặc rejection; external registration/bind/UID không ở đó, không nằm dưới lock của actor. Callback observer chỉ enqueue. Vì thế UID/transport không trả về cũng không giữ connect stack.

**2 — Cleanup vẫn runnable khi UID/connection hung:** các task hung không chiếm session worker/control/Main hoặc monitor cleanup; Stop/Close không join chúng. Cleanup timer + event actor vẫn tiến tới terminal theo logic 006, kể cả session không phát terminal. Finalization IPC cũng không giữ monitor hoặc prerequisite cho actor xử lý timeout. Consumer của hung task được detach, không đợi thread chết để giải phóng route waiter/callback.

**3 — Late completion không revive stopped generation:** exact registration, connection operation, service generation và Binder identity được kiểm tra trên state lane; Stop/Close/disconnect/timeout invalidates chúng trước terminal. Runner còn guard cleanup/phase của riêng run. Một UID value đến muộn không có quyền cấp lease, gọi start hoặc thay terminal result.

Ba lập luận là proof của contract đề xuất dưới giả thiết scheduling bình thường, không phải evidence code hiện tại đã thỏa contract. Bỏ lane isolation, một lock-held IPC, admission không chờ hoặc một fence/detach rule sẽ làm proof không còn hợp lệ và implementation phải STOP.

## 9. So sánh candidates

Các chữ viết tắt file trong bảng: **M** = manager, **S** = session, **V** = session state, **P** = session port, **A** = Android factory/adapter, **R** = runner, đều đã link tại mục 1.

| Candidate | Giải quyết | Chưa giải quyết / race | File cần tác động | Observable 006 và test coverage |
| --- | --- | --- | --- | --- |
| A. Callback snapshot ngoài monitor | Bỏ lock-held listener và cho membership mutation tiếp tục. | UID vẫn block caller nếu callback inline trên actor; bind/reconcile/finalization còn IPC dưới lock. Snapshot cần registration/generation fence. | M; S/V để consume tagged notifications. | Callback timing thay đổi; manager tests cần callback reentrant/hung + release/disconnect races. **A riêng không đủ**. |
| B. Non-blocking connect/readiness state machine | CONNECTING → verified READY/FAILED qua value event; Stop invalidates attempt. | Chưa bảo đảm resource lane độc lập, saturation hoặc monitor-free cleanup. READY/Stop cần serialize. | S, V, M. | READY chỉ sau UID hợp lệ; boundary/lifecycle tests phải đổi và thêm wrong UID/late completion. **B riêng không đủ**. |
| C. UID/connection lane riêng cleanup lane | UID hung không chặn single-thread session worker; không join/interrupt dependency. | Lock-held IPC vẫn chặn Stop; task capture session vẫn giữ route. Cần bounded admission, detachable mailbox. | S, M; V cho identity values. | Timing async, không đổi một logical stop. Tests phải giữ verifier chưa trả về trong khi cleanup progress. **C riêng không đủ**. |
| D. Trusted primitive thay `uid` | Có thể bỏ transaction nếu exact identity proof existed. | Không có bounded exact-service primitive đã chứng minh; cached server UID/liveness/permission không đủ. | Nếu có proof: S/M; hiện không đề xuất patch hoặc đổi AIDL/remote. | Không được đổi trust check; existing tests không chứng minh alternative. **Không chọn D**. |
| E. Narrow port: request return, snapshot completion | Runner có contract không IPC/wait trên stack; cleanup/terminal guard và notification detach rõ. | Contract vô ích nếu Android adapter/session vi phạm; READY timer sớm không cứu blocking implementation. | P, A, R, S. | Result classes/order/authority giữ nguyên; synchronous fake snapshot vẫn có thể enqueue; runner/adapter tests thêm hung-real-boundary coverage. **E riêng không đủ**. |

Chọn conjunction A+B+C+E với monitor contract toàn bộ mục 3. Không cần đổi AIDL `getUid`, remote registry, VDM/renderer logic, launch options, ownership algorithm hay chuyển execution lên Application. Đây là connection/control boundary và một terminal notification hook, không thiết kế lại engine 006/007. Tuy nhiên scope implementation tương lai **phải** cho phép manager finalization boundary cùng session/runner hooks; Task 3B product-only hiện tại không đủ.

## 10. Minimum files và verification cần cho implementation sau review

**Sáu production file tối thiểu:** `EmbeddedAppServiceConnectionManager.kt`, `EmbeddedAppSession.kt`, `EmbeddedAppSessionState.kt`, `EmbeddedWorkspaceSessionPort.kt`, `AndroidEmbeddedWorkspaceSessionFactory.kt`, `EmbeddedWorkspaceRunner.kt`. Identity/lane/mailbox helper có thể đặt trong các file này; không bắt buộc tạo subsystem mới. Product construction giữ dispatcher hiện tại; Application không nhận runtime handle. AIDL/getUid implementation, `EmbeddedAppModels.kt` stop semantics và renderer 007 chỉ đọc/giữ nguyên.

| Test hiện có | Phần dùng lại / cần thêm |
| --- | --- |
| [EmbeddedAppServiceConnectionManagerTest](../../../app/src/test/java/com/trancong/dexworkspacetouch/feature/embeddedapp/EmbeddedAppServiceConnectionManagerTest.kt) | Giữ lease-before-notify, multi-lease, in-flight/non-terminal + empty-state removal; thêm monitor-free reentrant/hung listener, snapshot/release/disconnect/replacement và removal/admission races. Tests hiện tại chưa kiểm tra lock/callback hoặc IPC hang. |
| [EmbeddedAppSessionBoundaryTest](../../../app/src/test/java/com/trancong/dexworkspacetouch/feature/embeddedapp/EmbeddedAppSessionBoundaryTest.kt) | Test hiện giả READY synchronous và gọi Start trong observer; cần chuyển expectation sang verified completion. Inject fake ingress/transport/verifier, assert UID wrong/failure/hung, Stop/Close tiến độc lập và late UID bị drop. |
| [SessionConnectionCoordinatorTest](../../../app/src/test/java/com/trancong/dexworkspacetouch/feature/embeddedapp/SessionConnectionCoordinatorTest.kt), [EmbeddedAppLifecycleCoordinatorTest](../../../app/src/test/java/com/trancong/dexworkspacetouch/feature/embeddedapp/EmbeddedAppLifecycleCoordinatorTest.kt) | Giữ monotonic/death/idempotence; thêm operation/generation/closed fences, READY chỉ sau UID acceptance. |
| [AndroidEmbeddedWorkspaceSessionAdapterTest](../../../app/src/test/java/com/trancong/dexworkspacetouch/workspace/execution/embedded/runtime/AndroidEmbeddedWorkspaceSessionAdapterTest.kt) | Giữ exact target/Surface/session identity, stop-close count và fake callback enqueue; thêm fire-and-return contract và notification-detach delegation. Fake synchronous READY hiện không chứng minh production async safety. |
| [EmbeddedWorkspaceRunnerTest](../../../app/src/test/java/com/trancong/dexworkspacetouch/workspace/execution/embedded/runtime/EmbeddedWorkspaceRunnerTest.kt) | Giữ sequential startup, reverse rollback, duplicate/Stop+SurfaceLost, READY/ACTIVE/cleanup deadlines và RecoveryRequired. Thêm connect admitted nhưng verifier không trả về: Stop/SurfaceLost vẫn xử lý, 20s/item terminal incomplete, no late startup, reply/waiter và callback detach sau terminal. |

Cần một integration fixture nối **real manager/session state + real adapter + runner**, chỉ fake transport/UID IPC và executor scheduling; unit runner với fake `connect()` trả ngay không chứng minh production boundary. Thread-hang fixture dùng latch ngoài control/cleanup lane, tháo latch trong test teardown; không cần transaction Binder thật hung vô hạn. Assert execution lane/lock, exact identities, bounded terminal và không queue cleanup sau verifier; không chỉ assert source text hoặc `Future.cancel`.

Task 3B gate/controller tests chỉ quay lại sau khi runtime boundary được implement và verify theo scope riêng đã duyệt. Không chạy chúng ở checkpoint tài liệu này. Existing test names/behavior là evidence đã đọc, không báo PASS mới.

## 11. Quyết định, giới hạn và verification checkpoint

**`FEASIBLE_WITH_NARROW_CONNECTION_BOUNDARY_CHANGE`** cho boundary connect/readiness được mô tả: inline request luôn return; cleanup không phụ thuộc lane/lock hung; exact completion fences chặn revival. Các điều kiện thiết kế bắt buộc ở mục 3, 5, 7 và 8 là một phần của kết luận, không phải optional follow-up.

Không tuyên bố hung Binder transaction chấm dứt, remote allocation đã sạch, full route/UI graph đã detach trong source hiện tại hoặc runnerScope parentage đã được giải quyết. Không thêm process-death recovery, persistent journal, Retry/reconcile product API, global cleanup, RunOwner hoặc permission bypass. Những giới hạn ownership đã ghi ở Task 3A/3S còn hiệu lực; checkpoint connect này không tự unblock toàn bộ Task 3B hoặc cấp phép Tasks 4–9.

Verification 3C chỉ kiểm tra local document links, whitespace và self-review consistency với source/authority boundaries. Không build, unit test hoặc device; implementation/runtime/heap acceptance **NOT VERIFIED**. Dừng để review tài liệu; chưa có implementation plan hoặc code được cấp phép từ quyết định này.
