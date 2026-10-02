# DWT-VDM-009 Task 3A — UI-independent cleanup ownership boundary

**Ngày:** 2026-10-01 (Asia/Saigon). **Loại công việc:** chỉ phân tích kiến trúc và đọc code.

**Project:** `D:\AndroidStudioProjects\DexWorkspaceTouch`. **Branch:** `release/1.0-beta`.
**Baseline/HEAD đã kiểm tra:** `2c77e6af40a879604897e328f5f6a46441e9e69e`.

**Quyết định:** `BLOCKED_BY_RUNTIME_OWNERSHIP_MODEL` cho guarantee Application-owned cleanup độc lập UI của Task 3 hiện tại.

Task 1–2 giữ trạng thái COMPLETE / targeted PASS đã có; không chạy lại các test đó. HARD STOP của Task 3 được giữ nguyên. Task 4–9 NOT AUTHORIZED. Task 3A chỉ tạo tài liệu này; không tạo `EmbeddedProductRunOwner`, không sửa production, tests, plan hoặc Task 4; không build/device-test, stage, commit hoặc push.

## 1. Kết luận và mức chứng minh

Có một điểm cắt **dữ liệu** hẹp trong 006: `executionSurface` chỉ phục vụ preflight và launch; các hàm cleanup đã đọc không dùng Surface. Có thể thay phần metadata của `OwnedItem.prepared` bằng metadata không chứa Surface, giữ nguyên session ownership, thứ tự rollback và terminal result. Đây chưa phải bằng chứng rằng toàn bộ runner/session/control graph độc lập UI.

Chỉ xóa Surface khỏi `preparedItems`/`OwnedItem` vẫn bỏ sót launch Runnable của session, continuation của caller đang chờ result, coroutine UI trong scope con của Application, và listener registry dùng chung có thể giữ callback của proof route. Handoff sau ACTIVE cũng cần chứng minh các reference này đã hết; ACTIVE không phải acknowledgement giải phóng mọi launch capture. Handoff chỉ sau launch thành công không giải quyết host disposal trong STARTING, startup failure hoặc cleanup timeout.

Vì vậy checkpoint này **không** chứng minh được một implementation hoàn chỉnh chỉ bằng thay đổi holder/lifetime nhỏ ở runner. Boundary cần thêm thiết kế về launch/control, quyền sở hữu waiter và callback/lease registry. Không phê duyệt một runner rewrite hay thực hiện thiết kế đó trong Task 3A. Quyết định BLOCKED là kết luận trong phạm vi đã cho phép, không phải khẳng định mọi kiến trúc tương lai đều bất khả thi.

## 2. Code thực tế đã đối chiếu

Các đường dẫn dưới đây tính từ repository root. Những file đã đọc ở lượt HARD STOP được dùng lại nếu không thay đổi; các dependency/capture bổ sung được đọc trong Task 3A.

| File | Field / hàm đã kiểm tra |
| --- | --- |
| `app/src/main/java/com/trancong/dexworkspacetouch/DexWorkspaceTouchApplication.kt` | `processScope`, `embeddedProductRunGate`, `createEmbeddedProductRunScope()` |
| `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/product/EmbeddedWorkspaceProductController.kt` | `execution`, `hostReadiness`, `appRunScope`, `start()`, `requestExit()`, `onHostDisposed()` |
| `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/product/EmbeddedWorkspaceProductScreen.kt` | Tạo renderer/runner, execution adapter, readiness lambda, Start/exit Job, quan sát result |
| `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/product/EmbeddedProductRunGate.kt` | Token identity, chuyển phase/result, `releaseWithoutAllocation()` |
| `app/src/main/java/com/trancong/dexworkspacetouch/workspace/execution/embedded/runtime/EmbeddedWorkspaceRunner.kt` | Request/event, prepared/owned state, reply/waiter, observer, timeout Job, cleanup, receipt |
| `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/EmbeddedWorkspaceRunnerController.kt` | `hostSlots`, `runner`, guard trước Start, áp dụng result, stop/close |
| `app/src/main/java/com/trancong/dexworkspacetouch/workspace/execution/embedded/runtime/EmbeddedWorkspaceRuntimeModels.kt` | Surface reference trong request/slot/prepared; receipt/result thuần giá trị |
| `app/src/main/java/com/trancong/dexworkspacetouch/workspace/execution/embedded/runtime/EmbeddedWorkspacePreflight.kt` | Đọc Surface validity, preparation, rejection trước tạo session |
| `app/src/main/java/com/trancong/dexworkspacetouch/workspace/execution/embedded/runtime/EmbeddedWorkspaceSessionPort.kt` | Contract factory/handle; chưa có transfer/detach acknowledgement |
| `app/src/main/java/com/trancong/dexworkspacetouch/workspace/execution/embedded/runtime/AndroidEmbeddedWorkspaceSessionFactory.kt` | Production provider, chuỗi wrapper/delegate, observer được inject, `changed` rỗng |
| `app/src/main/java/com/trancong/dexworkspacetouch/workspace/execution/embedded/runtime/AndroidEmbeddedWorkspaceExecutionSurface.kt` | `surface`, `validityQuery` capture Surface |
| `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedapp/EmbeddedAppSession.kt` | Worker/Handler, callback/listener, manager/lease, launch/stop/close Runnable |
| `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedapp/EmbeddedAppSessionState.kt` | `EmbeddedAppLifecycleCoordinator`, snapshot, session/connection state |
| `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedapp/EmbeddedAppServiceConnectionManager.kt` | Application context, singleton/listener, lease lifecycle, connection/finalization callback |
| `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedapp/EmbeddedAppModels.kt` | Target/geometry thuần giá trị; ordering của `SessionStopCoordinator` |
| `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/EmbeddedWorkspaceRendererCoordinator.kt` | `surfaces`, `TrackedSurface`, controller và host event |
| `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/EmbeddedWorkspaceRenderer.kt` | `PaneSurfaceHost`, SurfaceView/context, holder/touch callback, launched closure, disposal |
| `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/EmbeddedWorkspacePaneEventBridge.kt` | coordinator, `viewToken`, generation token, surface/touch forwarding |
| `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/EmbeddedWorkspaceGeometrySnapshot.kt` | Policy lambda chỉ capture frozen plan và geometry giá trị |
| `app/src/main/java/com/trancong/dexworkspacetouch/workspace/execution/embedded/EmbeddedWorkspacePlan.kt` | Field plan/item thuần giá trị, không giữ host object |
| `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedcalculator/EmbeddedCalculatorScreen.kt` | Proof-session `changed` callback thực tế capture Compose state của route |
| `app/src/main/java/com/trancong/dexworkspacetouch/navigation/TouchNavigation.kt` | Proof route entry point hiện chưa acquire/check product gate |

`EmbeddedAppLifecycleCoordinator` được khai báo trong `EmbeddedAppSessionState.kt`, không có file riêng `EmbeddedAppLifecycleCoordinator.kt`. `SessionStopCoordinator` được khai báo trong `EmbeddedAppModels.kt`.

## 3. Graph tham chiếu hiện tại

### 3.1 Application scope đi tới UI qua Job và lambda

```text
DexWorkspaceTouchApplication
  → processScope.Job
  → SupervisorJob(processScope.Job) of child run scope
  → onHostDisposed launch coroutine
  → EmbeddedWorkspaceProductController
      → execution adapter → renderer
      → hostReadiness lambda → renderer
      → appRunScope → other child Jobs
  → renderer.surfaces[sourceCellId] → TrackedSurface
      → viewToken = PaneSurfaceHost.view = SurfaceView → Activity Context
      → surfaceToken = actual Surface
      → executionSurface → AndroidEmbeddedWorkspaceExecutionSurface.surface
      → executionSurface.validityQuery lambda → Surface
```

Bằng chứng: Application dòng 52–56; product controller dòng 32–34 và 122–124; product screen dòng 133–136; renderer coordinator dòng 98–99 và 165–168. `viewToken`/`surfaceToken` có kiểu `Any`, nhưng `EmbeddedWorkspacePaneEventBridge` nhận SurfaceView và Surface thực từ `PaneSurfaceHost`. Đây không phải ID thuần giá trị.

Cùng parent scope còn sở hữu coroutine Start (`product.start(); refresh.refresh()`) và exit (`product.requestExit(); onBack()`) trong product screen. Chúng capture state/callback của route. Scope chỉ bị hủy trong `finally` của `onHostDisposed()`, nên các đường này có thể tồn tại trong lúc chờ cleanup. Job không có field Surface vẫn chưa chứng minh độc lập UI.

### 3.2 Runner hoặc wrapper control vẫn đi tới Surface

```text
proposed Application owner → runner / lambda calling runner.stop()
  → preparedItems[*].executionSurface → Surface
  → ownedItems[*].prepared.executionSurface → Surface
  → events → queued Event.Start.request.hostSlots[*].executionSurface → Surface
  → ownedItems[*].handle
      → AndroidEmbeddedWorkspaceSessionHandle.session
      → ProductionAndroidEmbeddedAppSession.delegate
      → EmbeddedAppSession.worker
      → queued/running startSession Runnable → captured Surface
```

Bằng chứng: runner dòng 23, 28, 31, 61, 116, 290 và 514–516; runtime models `EmbeddedWorkspaceHostSlot`/`EmbeddedWorkspacePreparedItem`; factory dòng 37–38, 58–59 và 75–76; session dòng 26 và 77–80. Phải kiểm tra cả capture của worker task ngoài các field khai báo trên session.

`runnerScope` được tạo bằng `scope.coroutineContext + SupervisorJob() + dispatcher`; Job mới không nhận parent. Nó không kế thừa quan hệ Job cha/con của scope đầu vào. Các coroutine event-loop/timeout vẫn capture runner; sống qua việc hủy Composable không chứng minh không giữ UI.

### 3.3 Đường tham chiếu ngược qua reply và observer

```text
runner.startReply / terminalWaiters[*]
  → incomplete CompletableDeferred
  → await completion handler → awaiting continuation
  → suspended runner/controller/renderer/route call chain
  → route objects / Surface-bearing request / callback captures

session.lifecycleChanged → observer created in runner.startNextItem()
  → runner (observer accesses the runner's events property)
  → prepared/owned state and pending replies above
```

Runner `start()`/`stop()`/`surfaceLost()` tạo reply rồi await; `handleStart()`/`handleStop()` giữ reply. Reply chưa hoàn tất vì vậy có thể giữ caller, không thể coi là terminal data thuần giá trị. Completion/cancellation có thể gỡ đăng ký await, nhưng disposal hiện tại khởi chạy cleanup thay vì hủy ngay mọi waiter của route. Control handle tương lai không được giữ reply chưa giải quyết có caller UI sau handoff/disposal.

Đường continuation này là suy luận về ownership từ chuỗi suspend và đăng ký await. Phần bytecode đọc được từ coroutines 1.10.2 trong cache cho thấy `awaitSuspend` đăng ký `ResumeAwaitOnCompletion`, giữ `CancellableContinuationImpl` có field `delegate` continuation. Tuy nhiên `javap` thoát với `AccessDeniedException`, nên kiểm tra bytecode đầy đủ là **NOT VERIFIED**, không phải bằng chứng runtime/heap. Các field Surface và worker capture đã đủ xác lập blocker độc lập với kiểm tra phụ này.

## 4. Graph theo sáu giai đoạn lifecycle

| Giai đoạn | Ownership/capture hiện tại | Nhu cầu sử dụng và điểm có thể cắt |
| --- | --- | --- |
| Trước Start | Route sở hữu PaneSurfaceHost/SurfaceView/Surface, bridge, renderer và controller `hostSlots`. Runner chưa có prepared/owned item; host-event Job trong scope con của Application đã capture bridge/host. | Renderer cần chúng cho readiness/render/touch. Chưa yêu cầu runtime allocation. Application không được nhận renderer/controller hoặc UI scope này. |
| Start/preparation | Request/`Event.Start` trong queue giữ mọi slot; preflight kiểm tra validity rồi tạo prepared item chứa Surface. `startReply` giữ caller đang chờ. | Request cần cho preflight. Surface của item chưa launch cần cho `onReady()` sau đó. Có thể bỏ request đã tiêu thụ, nhưng pending launch state vẫn chứa UI. |
| Session starting | Mỗi `OwnedItem` đã tạo giữ prepared item; các item tiếp theo giữ Surface chưa dùng. `onReady()` kiểm tra/chuyển Surface. Worker Runnable capture Surface đến khi launch call kết thúc. Observer/waiter/timer dẫn ngược tới runner/UI. | Lần đọc Surface trực tiếp cuối ở runner nằm trong `onReady()`; `handle.start()` trả về chỉ chứng minh đã enqueue công việc bất đồng bộ trong production. Không chứng minh worker đã bỏ Surface. Host loss/stop có thể xảy ra ở khoảng này. |
| ACTIVE | Runner hiện vẫn giữ prepared list và owned prepared item. Receipt chỉ cần metadata. Launch đã xong thường không cần Surface trong runtime, nhưng callback ACTIVE không phải fence giải phóng task/capture. Route tiếp tục render bằng Surface của nó. | Sau khi mọi launch capture thực sự được bỏ, runtime chỉ cần metadata/handle/snapshot. Vá holder ở ACTIVE còn phải cắt prepared list, UI waiter, callback chung và scope back edge. |
| STOPPING / ROLLING_BACK | `beginCleanup()` ngăn startup tiếp, tạo owned order đảo ngược, gọi handle `stop()`/`close()` và chờ snapshot/timeout. Map vẫn giữ Surface. Nếu startup IPC chưa xong, stop/close nằm sau nó trên cùng session worker. | Cleanup runner không đọc Surface. Surface chưa launch hết cần khi startup đã bị ngăn. Launch task đang chờ/chạy có thể vẫn cần Surface; timeout không chứng minh task đã bỏ nó. |
| Terminal clean / incomplete / recovery-required | `finishCleanup()` lưu terminal result thuần giá trị, hoàn tất reply/waiter và gỡ waiter/timer. Không xóa prepared/owned map hoặc đóng channel/runner scope. Session close có thể vẫn chạy sau runner timeout. | Phải giữ evidence và exact owned identity khi incomplete. Terminal bookkeeping không cần Surface, nhưng reference hiện vẫn còn. Terminal result không phải acknowledgement đã detach mọi memory/capture. |

Các giai đoạn này chỉ nằm trong cùng process. Không thêm persistence, tái dựng sau process death hoặc recovery guarantee mới.

## 5. Lifetime của từng reference

| Reference | Ai sở hữu / vì sao cần | Lần dùng cuối hoặc điều kiện bỏ reference | Cleanup sau đó / đường giữ gián tiếp |
| --- | --- | --- | --- |
| Renderer slot `viewToken`, `surfaceToken`, execution surface | Route renderer đối chiếu callback, kiểm tra host và render/touch. | Khi bỏ host/slot, đồng thời detach/cancel host callback Job; chỉ unmount chưa xóa mọi coordinator reference bị giữ. | Cleanup 006 không cần View. Adapter/readiness/Job hiện giữ coordinator. |
| Runner controller `hostSlots` | Route controller tạo execution request và slot validity. | Khi host bị hủy/route dispose, sau khi ngừng forwarding. | `close()` không xóa mọi slot; không an toàn khi đưa controller lên Application. |
| Request/`Event.Start` slot | Launch payload cho preflight. | Sau khi preflight tiêu thụ request, nếu queue/continuation của launch caller không còn giữ nó. | Cleanup không đọc request. Queue còn Surface payload không phải handle độc lập Surface. |
| `preparedItems[*].executionSurface` | Launch/validity check cho item tiếp theo. | Với runner-local reference: sau `onReady()` handoff của từng item; bỏ toàn bộ pending launch reference khi `beginCleanup()` ngăn startup. | Cleanup chỉ cần identity/order. Không giữ nguyên prepared item trong owned map được Application giữ. |
| Surface trong `OwnedItem.prepared` | Launch item hiện tại; field còn lại tạo receipt. | Tách metadata khỏi launch reference; bỏ launch reference sau runner-local handoff hoặc khi startup bị hủy trước lần dùng đó. | Giữ metadata giá trị, handle, snapshot/receipt để không đổi partial receipt, rollback đảo ngược và `allOwnedSessionsClean`. |
| Surface trong session worker Runnable | Đối số bất đồng bộ của `service.startSession(...)`. | Sau lần dùng cuối trong remote invocation, với capture thực sự được bỏ trước acknowledgement có thể chuyển control. | Stop/close cần session ID/service/lease. Task launch đang chờ/chạy là đường giữ riêng; `handle.start()` trả về và cleanup timeout không xóa nó. |
| Route coroutine/waiter/callback | UI Start, quan sát result, navigation, Surface/touch forwarding. | Cancel/detach tại disposal; reply chưa giải quyết do runtime giữ phải mất continuation của UI caller. | Scope/Deferred hiện giữ route. Cleanup Job chỉ được dùng owner/control độc lập UI, không capture product/renderer/refresh/onBack. |
| `lifecycleChanged` observer | Chuyển snapshot session vào runner event loop. | Có thể giữ cho cleanup nếu toàn bộ graph độc lập UI. | Observer hiện capture runner; chỉ an toàn sau khi cắt mọi back edge của runner/session/dependency. |
| Shared manager listener entry | Chuyển connection event cho mọi session đã đăng ký. | `release(lease)` gỡ entry; session close đi tới đó bất đồng bộ. UI callback cần detach riêng sớm hơn nếu route đã chết. | Session khác có thể có `changed` capture Compose state hoặc observer dẫn tới proof runner chứa Surface. |
| Geometry policy | Kiểm tra frozen plan/geometry. | Production snapshot policy không có ràng buộc lifetime UI. | `asPolicy()` chỉ capture snapshot chứa plan/geometry giá trị; không thấy back reference tới renderer/Activity. Policy/provider inject tùy ý vẫn phải kiểm tra riêng. |

Bỏ runtime reference không yêu cầu gọi `Surface.release()` hoặc hủy Surface đang render. Route vẫn giữ Surface để render khi ACTIVE. Tài nguyên platform của remote session vẫn chịu authority của cleanup result 006; mất client reference không chứng minh clean.

## 6. Ứng viên A — bỏ prepared Surface reference

**Kết luận cục bộ: có thể sửa lifetime của holder; chưa đủ cho toàn bộ Task 3.**

Cleanup code thực tế chứng minh phần giới hạn này:

- `beginCleanup()` dùng owned source ID và thứ tự insertion đảo ngược.
- `cleanupNext()` đọc latest snapshot và gọi `item.handle.stop()/close()`, không đọc `executionSurface`.
- `finishCurrentCleanup()` ghi outcome rồi tiến tiếp theo cùng cleanup order.
- `finishCleanup()`/`activeReceipts()`/`partialReceipt()`/`OwnedItem.toReceipt()` chỉ cần source ID, package/component, order, session ID và snapshot/receipt.
- `sendTouch()` dùng handle và session phase, không cần Surface.

Owned record không chứa Surface có thể giữ `EmbeddedWorkspacePlanItem` thuần giá trị hiện có, handle, latest snapshot và active receipt. Pending launch Surface phải là dữ liệu tạm riêng, được bỏ sau từng handoff hoặc khi ngừng startup. Chỉ xóa `preparedItems` còn giữ `OwnedItem.prepared`; chỉ thay `OwnedItem.prepared` còn giữ `preparedItems`. Phải cắt cả hai đường, giữ exact owned identity/count và thứ tự/index startup; không xóa/reindex item làm đổi sequential launch.

Ứng viên cục bộ không bắt buộc đổi public `EmbeddedWorkspacePreparedItem`: preflight vẫn trả launch payload chứa UI để tiêu thụ tạm thời, không dùng làm retained cleanup ownership. Giữ readiness/active timeout, sequential launch, rollback order, stop/close call, RemoteDied và terminal result authority như hiện tại.

Tuy nhiên production `handle.start()` gọi `EmbeddedAppSession.startSession()`, enqueue Runnable capture Surface rồi trả về. A chưa chứng minh detachable handle trong lúc task đó pending và chưa cắt manager/waiter/caller graph. Vì vậy chưa đủ điều kiện chốt toàn bộ là `FEASIBLE_WITH_NARROW_006_LIFETIME_CHANGE`.

## 7. Ứng viên B — detachable cleanup/control handle

**Kết luận toàn bộ: chưa chứng minh trong giới hạn thay đổi hẹp được cho phép.**

Sau acknowledgement detach đầy đủ, control handle có thể cung cấp stop/close request và lifecycle/terminal result thuần giá trị của exact run. Session ownership và terminal result authority vẫn ở 006. Wrapper quanh runner/session handle hiện tại, function reference `stop`, hoặc event channel chưa tạo được acknowledgement đó.

Graph phải chứng minh:

```text
Application product owner → control handle
  → surface-free runtime state + value-only event/reply state
  → session handles → delegates → session lifecycle / lease / transport
  → manager → every listener reachable through that manager
  → every queued/running task and callback reachable through those objects
```

Không được có đường ngược tới host, route hoặc launch payload. Channel-only wrapper không phải ngoại lệ đã chứng minh: channel hiện chứa `Event.Start(request, reply)` và receiving coroutine của runner; phải kiểm tra cả pending payload/continuation. Handle không có public Surface member vẫn chưa chứng minh độc lập UI.

Chỉ chuyển control sau ACTIVE chưa giải quyết disposal trong STARTING. Có thể cần cleanup trong lúc launch IPC còn giữ Surface. Chờ Started thành công không bao phủ failure/timeout; API hiện tại cũng không cho phép dùng STOPPING/terminal làm memory fence. Session port chưa có acknowledgement bỏ launch capture/chuyển control và chưa tách ownership launch work khỏi control work.

Đề xuất sau này phải giải quyết cụ thể các khoảng này và chứng minh giữ ordering start/stop/close của single session worker. Tách executor launch/control hoặc thêm completion protocol vượt quá việc thay prepared holder; tài liệu này không cấp phép thực hiện.

## 8. Session callback, connection và shared-listener graph

Với production workspace factory:

```text
OwnedItem.handle → AndroidEmbeddedWorkspaceSessionHandle
  → ProductionAndroidEmbeddedAppSession → EmbeddedAppSession
      → lifecycleChanged = runner observer → runner
      → changed = {} (no route capture in this construction)
      → target / lifecycle / connectionCoordinator / stopCoordinator (values/state)
      → worker → start Runnable → Surface while launch is pending
      → manager and lease.owner → shared manager
```

Context được production manager giữ là `context.applicationContext`; product screen truyền `activity.applicationContext` vào factory. Không thấy field Activity Context trên construction path này. Factory nhận `Context` và provider, nên factory/provider inject tùy ý vẫn phải kiểm tra graph thực tế.

Application Context đúng không tự chứng minh graph product an toàn: trong dự án này `manager.app` dẫn tới `DexWorkspaceTouchApplication.processScope` và những child Job capture UI mô tả ở mục 3.1. Đây là một back edge bổ sung cần cắt ở lifetime/wiring của UI Job, không cần giữ Activity Context để tạo ra nó.

`changed` rỗng của workspace session và các lifecycle/connection coordinator tự chúng không chứa UI. Lifecycle observer có thể an toàn sau khi runner và dependency thực sự hết Surface/UI caller. Cycle giữa các runtime object thuần là hợp lệ; cycle đi tới Surface/UI không hợp lệ.

Shared manager tạo thêm đường cụ thể:

```text
product control → owned session → manager.listeners[otherSessionId]
  → other EmbeddedAppSession.listener → other EmbeddedAppSession
  → changed callback → proof-route Compose state
```

`EmbeddedCalculatorScreen.kt:42` tạo `EmbeddedAppSession(...){ state = it }`. `DisposableEffect` gọi `session.close()` khi dispose, nhưng `close()` bất đồng bộ; `changed` vẫn là callback được giữ dù `update()` ngừng gọi nó khi `closed`. Không gọi callback không xóa reference. `EmbeddedAppServiceConnectionManager.release()` gỡ listener sau đó khi lease được release. Launch task đang pending của proof session này cũng có thể capture Surface.

Manager là singleton multiplex listener, không phải dependency riêng từng run. `TouchNavigation.kt:441` trở đi đăng ký proof route không acquire product gate; cleanup proof cũ cũng có thể còn pending khi route mới mở. Đây là bằng chứng về reference path có thể tồn tại, không phải kết quả quan sát hai session đồng thời trên device. Handoff hoàn chỉnh không được giả định mọi listener khác độc lập UI hoặc coi guard Task 7 tương lai là invariant hiện tại.

Cô lập/detach graph này cần thiết kế lifetime callback/lease rõ ràng. Tạo manager thứ hai với bookkeeping riêng trên cùng shared service không phải shortcut hợp lệ: final removal hiện dựa vào lease và in-flight operation của manager. Không đề xuất đổi semantics này trong checkpoint.

## 9. Ứng viên C — host-loss event boundary

**Boundary event thuần giá trị là contract phù hợp, nhưng code hiện tại chưa triển khai/chứng minh.**

`PaneSurfaceHost.dispose()` hiện gỡ holder/touch listener rồi launch `bridge.onSurfaceDestroyed(surface)` trong scope con Application được truyền vào. Coroutine capture Surface và bridge; bridge giữ coordinator và View token thực. Product disposal còn launch `product.requestExit()` qua closure khác chứa UI. Đây chưa phải host-loss message thuần giá trị.

Boundary tương lai cần các điều kiện sau, không đưa renderer 007 lên Application hoặc rewrite renderer:

1. Host lifetime identity là giá trị như String/Long, đối chiếu với in-process generation và operation identity. Không truyền `Any` view/surface token hiện tại làm host identity.
2. Route host gửi đồng bộ loss/stop event thuần giá trị, idempotent, trước khi bỏ reference. Application không giữ `onHostDisposed` lambda capture product/controller/renderer.
3. Coroutine launch/Surface/touch/navigation và result waiter thuộc route phải detach khi dispose. Activity mới chỉ quan sát product values, không gắn Surface vào run cũ.
4. Cleanup không gọi lại dead host; chuyển terminal evidence thuần giá trị của exact run tới owner. Callback runtime → owner chỉ hợp lệ nếu owner/capture không có đường ngược tới UI.
5. Core phải xác định cách nhận host-loss trong preparation và session launch đang in-flight, trước khi có handle độc lập Surface. Giữ serialization launch/stop/close hiện tại; không suy outcome từ việc host biến mất.

Đây là yêu cầu cho boundary còn thiếu, chưa triển khai Task 4. Wrapper quanh renderer disposal Job hiện tại không đáp ứng.

## 10. Phát hiện về `releaseWithoutAllocation` và contract cần có

Đường hiện tại: controller gọi `execution.start()` ở dòng 79, nhận `null` rồi gọi `gate.releaseWithoutAllocation(acquired)` ở dòng 81. Gate kiểm tra token identity nhưng chưa có invocation/allocation-boundary flag.

| Câu hỏi | Trả lời theo code thực tế |
| --- | --- |
| Runner/session factory đã được gọi khi production adapter trả null chưa? | Adapter **đã** được gọi. Trong call chain production hiện tại, `renderer.start()` trả null tại guard, hoặc `EmbeddedWorkspaceRunnerController.start()` trả null tại guard (`closed`, `startConsumed`, slot invalid); đều nằm trước `runner.start(request)` của lần gọi này. Sau khi gọi runner `start()`, result type không nullable. Không kết luận nhánh null cụ thể này tạo session mới trong cùng attempt. |
| Chỉ null có chứng minh toàn bộ allocation absent không? | Không. `startConsumed` có thể phản ánh runner invocation trước; null không mô tả owned set đó. `EmbeddedProductExecution` là nullable interface, không có proof contract về no-invocation/no-allocation; adapter khác có thể gọi runner rồi trả null. Exception/missing outcome sau invocation là uncertain. |
| Result nào chứng minh không allocation cho current run? | Rejection đã chứng minh trước invocation, không có older owned execution; hoặc exact-run `PreflightRejected` do 006 tạo trước `sessionFactory.create()`. Result sau phải đi qua result acceptance có authority; không cho phép dùng shortcut pre-invocation sau khi đã vượt boundary. |
| Có thể đưa release hoàn toàn trước runner boundary không? | Có dưới contract tương lai: tách pre-invocation check không side effect ở route và ghi operation invocation boundary trước khi gọi execution. Shortcut chỉ hợp lệ khi chưa vượt boundary và đã chứng minh không có execution cũ còn owned. Readiness không tự cho phép release operation đã invoke. |
| Null/exception sau invocation nên xử lý thế nào? | Giữ `UNKNOWN`/possible ownership, fail closed, giữ gate token và stop intent tới exact-run no-allocation/clean result có authority. Không suy clean từ null, exception, route loss hoặc fresh IDLE. |

Contract cho implementation sau này:

- Generation + operation identity phân biệt start/cleanup result; token identity riêng chưa đủ cho same-token ordering.
- Gate giữ **single phase authority**. Stop intent/STOPPING từ chối late Started; old generation/cleanup không activate hoặc release token mới.
- Đúng một acquisition/invocation Start và một cleanup operation; Start+Back ghi stop intent trong lúc Start outcome pending.
- Đánh dấu invocation trước execution boundary; sau đó không dùng `releaseWithoutAllocation` chỉ vì nullable adapter không trả result.
- Exact-run preflight/cleanup evidence 006 giữ release authority. Không thêm persistence, reconcile, retry, pending/commit hoặc process-death contract.

Không patch phần nào của contract này trong Task 3A.

## 11. File tối thiểu và phân loại thay đổi

**Chưa có bộ file tối thiểu được chứng minh cho implementation Task 3 hoàn chỉnh** dưới guarantee hiện tại, vì A chưa chứng minh B/C. Bảng sau xác định file cụ thể cho từng phần đã kiểm tra, chưa phải implementation plan được duyệt.

| Phần cần xử lý | Production file tối thiểu đã xác định | Phân loại / giới hạn |
| --- | --- | --- |
| A: bỏ retained launch reference của runner, giữ owned metadata | `app/src/main/java/com/trancong/dexworkspacetouch/workspace/execution/embedded/runtime/EmbeddedWorkspaceRunner.kt` | Sửa holder/reference lifetime cục bộ. `OwnedItem` là private trong file này; không bắt buộc đổi public prepared/result model. Riêng thay đổi này chưa làm toàn graph an toàn. |
| Chứng minh lần dùng cuối của Surface trong asynchronous session launch và transferability | `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedapp/EmbeddedAppSession.kt`; `app/src/main/java/com/trancong/dexworkspacetouch/workspace/execution/embedded/runtime/AndroidEmbeddedWorkspaceSessionFactory.kt`; `app/src/main/java/com/trancong/dexworkspacetouch/workspace/execution/embedded/runtime/EmbeddedWorkspaceSessionPort.kt`; runner ở trên | Cần lifetime/control contract qua session/adapter boundary. Chưa chọn implementation an toàn cụ thể. Chỉ thay `OwnedItem` chưa đáp ứng. |
| Cắt shared-listener back edge hoặc chứng minh exclusivity hợp lệ | `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedapp/EmbeddedAppServiceConnectionManager.kt`; session ở trên | Phải giữ lease/connection/final-removal semantics và xử lý UI callback của client khác. Bọc manager không giải quyết. Không thực hiện/giả định thay đổi proof route hoặc Task 7. |
| Product handoff, route Job/waiter và token/ordering | `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/product/EmbeddedWorkspaceProductScreen.kt`; `EmbeddedWorkspaceProductController.kt` và `EmbeddedProductRunGate.kt` cùng thư mục; `app/src/main/java/com/trancong/dexworkspacetouch/DexWorkspaceTouchApplication.kt`; proposed new `EmbeddedProductRunOwner.kt` trong product directory | Task 3 tương lai cần amend allowed files nếu cần screen wiring. Không thực hiện trước khi chứng minh runtime/control boundary. Chưa tạo owner. |

Nếu A được cấp phép riêng sau này, affected test gồm `app/src/test/java/com/trancong/dexworkspacetouch/workspace/execution/embedded/runtime/EmbeddedWorkspaceRunnerTest.kt`, thêm regression reference lifetime và chứng minh startup/rollback/cleanup outcome không đổi. Task 3 hoàn chỉnh còn cần ba lớp test đã chỉ định và session-level closure/launch-boundary test. Checkpoint này không tạo/chạy test file.

Công việc chưa giải quyết nằm ở ownership/interface giữa launch transport, result waiter và shared callback; chưa chứng minh là single-holder lifetime fix trong 006. Không coi đây là patch nhỏ đã được cấp phép, mở rộng thành engine mới hoặc đổi ownership renderer 007. Chỉ thực hiện A không bắt buộc rewrite `EmbeddedWorkspaceRuntimeModels.kt`, lifecycle phase model, renderer/bridge hoặc AIDL.

## 12. Quyết định, đề nghị scope và giới hạn verification

**Quyết định Task 3A: `BLOCKED_BY_RUNTIME_OWNERSHIP_MODEL`.** Giữ Task 3 dừng. Checkpoint này chưa chứng minh execution/control handle, cleanup Job hoặc closure nào hợp lệ để Application giữ.

Đề nghị scope amendment được review riêng, bỏ guarantee vô điều kiện rằng Application cleanup/control sống qua route/Activity recreation mà toàn graph vẫn độc lập UI. Ghi rõ host-loss/startup lifetime case chưa giải quyết và hành vi gate/evidence fail-closed; không tuyên bố authoritative clean hoặc tái dựng ownership từ host đã dispose. Không patch riêng `releaseWithoutAllocation` khi boundary đang blocked. Nếu muốn giữ guarantee, cần một architecture task mới được cấp phép để chứng minh launch/control/callback boundary trước implementation; tài liệu này chưa mở công việc đó.

Đề nghị không hủy Tasks 1–2 hoặc kết quả 006–008 đã đóng. Không cấp phép Task 4–9 hay process-death recovery. Không chấp nhận đưa controller lên Application, giấu Surface trong wrapper, giữ route sống giả tạo, WeakReference, singleton renderer hoặc persistence workaround.

Verification ở đây là kiểm tra graph/lifetime theo source và validation tài liệu. Build, unit/device test, heap retention, coroutine cancellation timing và detachable control implementation hoàn chỉnh là **NOT VERIFIED**. Giới hạn bytecode command phụ được ghi tại mục 3.3. Whitespace validation được chạy riêng khi giao tài liệu; không suy runtime PASS từ tài liệu.
