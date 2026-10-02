# DWT-VDM-009 — Task 5: CLEANUP_BLOCKED product UX

Ngày: 2026-10-02 (Asia/Saigon). Nhánh: `release/1.0-beta`.

**Kết quả: Task 5 hoàn tất, targeted GREEN và affected regression PASS.** Chỉ Task 5 được thực hiện theo yêu cầu EXECUTE TASK 5 ONLY. Không có blocker trong phạm vi Task 5. Dừng sau Task 5; Tasks 6–9 chưa được cấp phép.

## File thay đổi

Production:

- `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/product/EmbeddedProductRunGate.kt`: thêm DTO/projection thuần giá trị `EmbeddedCleanupBlockedUi`; không đổi admission, operation ordering, release authority hoặc callback fences của gate.
- `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/product/EmbeddedWorkspaceProductScreen.kt`: nhánh blocked riêng, trước loader/readiness/execution; composable dùng chung cho status.
- `app/src/main/java/com/trancong/dexworkspacetouch/ui/screens/HomeScreen.kt`: nhận `ProductRunStatus` giá trị và hiển thị blocked status cùng nút xem trạng thái.
- `app/src/main/java/com/trancong/dexworkspacetouch/navigation/TouchNavigation.kt`: collect status Application; mở route chỉ để xem blocked status với workspace ID có provenance.

Test mới: `app/src/test/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/product/EmbeddedCleanupBlockedUxTest.kt` — 29 test.

Báo cáo và các log/XML/audit `task-5-*` là evidence mới. Không sửa tài liệu/evidence Task trước.

## RED

Lệnh đầu tiên và targeted GREEN:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests '*.EmbeddedCleanupBlockedUxTest' --console=plain
```

- Sandbox ban đầu không resolve Android Gradle plugin 9.2.1. Chạy cùng lệnh với quyền truy cập cache/SDK hiện có; không đổi dependency.
- Fixture ban đầu sai thứ tự constructor `RecoveryRequired`, đã sửa bằng named arguments trước behavioral RED. Lỗi compile không tính là RED.
- Để test API mới biên dịch, seam projection ban đầu chiếu đúng thông báo/action policy cũ, chưa nối UI mới. **28 test chạy, 14 FAIL, 14 PASS, errors/skipped 0**. FAIL ở copy nguyên nhân/lifetime, timeout provenance, safe navigation/Home và copy bất biến; các hàng rào an toàn Task 3B/4 vốn đúng tiếp tục PASS. Không cố tình làm hỏng gate để biến các invariant đã PASS thành RED.
- Tự review phát hiện copy `SurfaceLost` cũ có thể nói “phiên đang kết thúc” trong terminal blocked. Test mới `retainedSurfaceLostIssueDoesNotDescribeOldRunAsStillEnding`: **1/1 FAIL** trước sửa, sau đó GREEN. Chỉ sửa copy trong projection; không sửa renderer/runtime.

Evidence: [RED XML](task-5-red.xml), [RED log](task-5-red.log), [SurfaceLost RED XML](task-5-surface-copy-red.xml), [SurfaceLost RED log](task-5-surface-copy-red.log).

## GREEN và regression

Targeted cuối cùng: **29/29 PASS**, failures/errors/skipped 0 theo XML đã đọc. `compileDebugKotlin` và unit-test compilation thành công trong Gradle command. Evidence: [GREEN XML](task-5-green.xml), [GREEN log](task-5-green.log).

Affected regression:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests '*.EmbeddedProductRunGateTest' --tests '*.EmbeddedProductIssueTest' --tests '*.EmbeddedWorkspaceProductRoutingTest' --tests '*.EmbeddedShizukuRefreshTest' --tests '*.EmbeddedProductLifecycleTest' --tests '*.EmbeddedWorkspaceProductControllerTest' --tests '*.EmbeddedWorkspaceRunnerTest.every true terminal clears runtime graph and ends root job while cache remains usable' --console=plain
```

**BUILD SUCCESSFUL, exit 0; test task thực sự chạy.** Sáu class được yêu cầu và một filter Task 3G terminal/cache PASS trong command này. [Regression log](task-5-regression.log) lưu nguyên command/kết quả. Không báo số test regression từ suy luận: XML regression đã bị lượt targeted tiếp theo thay thế.

Regression chạy trước sửa riêng copy `SurfaceLost`; gate/controller/runtime/lifecycle/readiness và các giả định của regression không đổi sau đó. Targeted 29 test bao phủ projection cuối cùng; không lặp các regression đã PASS chỉ để lấy lại XML.

## Trạng thái và copy chính xác

Title: **“Chưa xác nhận dọn Embedded”**.

Thông báo chung:

> Phiên Embedded trước chưa thể xác nhận đã dọn sạch. DWT đã kết thúc giao diện chạy cũ; Embedded mới và Classic đang bị chặn để tránh xung đột.

| Dữ liệu có provenance | Copy nguyên nhân |
| --- | --- |
| `RuntimeCleanupIncomplete` / CleanupIncomplete | Kết quả dọn phiên trước chưa đầy đủ; chưa thể xác nhận đã dọn sạch. |
| `RemoteDied` / RecoveryRequired có remote-death evidence | Kết nối phiên trước đã mất; kết quả dọn từ phía dịch vụ chưa được xác nhận. |
| RecoveryRequired chưa có remote-death evidence | Phiên trước cần được kiểm tra thêm; chưa thể xác nhận kết quả dọn. |
| `CleanupOutcomeUncertain` | Kết quả dọn phiên trước chưa rõ; chưa thể xác nhận đã dọn sạch. |
| `CLEANUP_TIMEOUT` trong outcome INCOMPLETE/UNCERTAIN | Quá thời gian chờ xác nhận dọn phiên (CLEANUP_TIMEOUT); kết quả vẫn chưa được xác nhận. |
| Thiếu typed cause và failure code | Nguyên nhân dọn phiên chưa rõ; chưa thể xác nhận đã dọn sạch. |
| Thiếu typed cause, có unknown code, ví dụ `OPAQUE_CODE` | Nguyên nhân dọn phiên chưa rõ (mã OPAQUE_CODE); chưa thể xác nhận đã dọn sạch. |
| Typed `SurfaceLost` còn trong terminal evidence | Vùng hiển thị phiên trước đã mất; kết quả dọn chưa rõ. |

Typed issue khác dùng `issue.message()` rồi “Kết quả dọn phiên trước chưa rõ.”; không lấy unknown code thay cho typed cause. Timeout chỉ dùng failureCode của copied unresolved outcome, không suy từ display string hoặc một outcome CLEAN_CONFIRMED. Projection giữ đầy đủ typed issue/items/outcomes/identities; không chế session/display/task IDs.

Phạm vi trạng thái:

> Trạng thái này chỉ áp dụng trong lần chạy DWT hiện tại khi ứng dụng còn giữ bằng chứng chưa được giải quyết.

Hướng dẫn hỗ trợ:

> Không có thao tác thử dọn lại hoặc mở khóa tại đây. Nếu cần hỗ trợ, hãy mô tả tình huống xảy ra trước thông báo này.

Không khẳng định dọn xong, đã sửa, hết tài nguyên, an toàn chạy lại hoặc recovery qua process death.

## Actions, Home và navigation

- Action model chỉ có `BACK` và `VIEW_STATUS`; set bất biến. Khi thiếu token/workspace provenance, chỉ có BACK.
- Embedded blocked route: duy nhất nút **“Quay lại”** và system Back. Callback chỉ gọi navigation hiện có, không gọi controller/requestExit/release.
- Home: hiển thị cùng title/message/reason/scope/support, nút **“Xem trạng thái Embedded”**. Callback kiểm tra status hiện tại vẫn CLEANUP_BLOCKED và workspace ID khớp token rồi chỉ `navigate()` tới route product. Không đi qua Start admission, không gọi repository/runtime để tái dựng run.
- Home tiếp tục vô hiệu hóa launch Classic/Embedded bằng phase gate; dispatch guard hiện có giữ nguyên. Rời rồi trở lại route không nhả gate hoặc thay evidence. Workspace đã bị xóa vẫn xem được blocked status vì nhánh blocked đứng trước loader.
- Blocked status surface không có Start, Classic, Retry/thử dọn lại, reconcile, force-stop, global scan, reset service, readiness refresh hay “Đã sửa xong”/manual unlock. Không có clipboard/support bundle/diagnostics formatter Task 6.

## PASS criteria và hành vi kiểm thử

| Nhóm yêu cầu | Bằng chứng | Kết quả |
| --- | --- | --- |
| Causes 1–4: incomplete/recovery/uncertain/timeout | Các test typed cause, copied timeout, unknown code và missing provenance | PASS |
| Actions 5–8, 20 | Action model chỉ navigation; gate từ chối Start/Classic/pre-runner unlock; source audit composable | PASS |
| Refresh 9–10 | `EmbeddedShizukuRefresh` thật: refresh/resume/Binder received/dead/permission đổi readiness, giữ nguyên UI/evidence/gate; permission request bị từ chối | PASS |
| Bỏ old graph 11 | Controller thật chạy Start rồi Dispose; close đúng một lần, scope completed, execution/hostReadiness/backCallback null; function oldRoute kết thúc rồi projection vẫn dùng được | PASS |
| Recreation/Home 12–13 | Tạo lại projection từ Application gate, evidence/generation/operation không đổi; graph toàn giá trị, safe VIEW_STATUS có workspace provenance | PASS |
| Navigation 14–15 | Action model cho BACK/VIEW_STATUS, value-host navigation simulation giữ gate; production callback audit chỉ pop/navigate; status quay lại bằng cùng value projection | PASS ở mức model/source |
| Allocation 16 | Projection lặp ba lần và actual gate admission từ chối factory/token; factory side effects đều 0; Task 4 lifecycle regression và production early-return audit | PASS ở mức unit/source |
| Stale callback 17–18 | Late Started/old incomplete, old token/generation/cleanup ID, exact cleanup đã terminal nhận clean lần nữa đều bị từ chối; UI/status không đổi | PASS |
| Missing provenance 19 | Unknown copy, token/operation null, items/outcomes rỗng; không tạo IDs hay nút view thiếu workspace provenance | PASS |
| Projection bất biến | Mutate input list không đổi UI evidence; list/action set từ chối mutation; recursive `assertValueGraph` | PASS |
| Local-end copy | `SurfaceLost` terminal không còn nói phiên đang kết thúc | PASS |

## Allocation và reference graph

Counters của blocked projection/recreated admission fixture:

```text
controller construction = 0
execution construction  = 0
Surface allocation      = 0
new token acquire       = 0
cleanup invocation      = 0
```

Các số này là unit-test counters ở factory/admission boundary, không phải đo Surface/native allocation trên thiết bị. Cleanup duy nhất của old route là thao tác trước terminal, không được gọi lại để render status. Regression Task 4/3B kiểm tra graph cũ kết thúc, gate blocked và late completion vẫn fenced; một test Task 3G kiểm tra runtime terminal/cache/root termination.

Source và compiled debug fields đã audit trong [javap log](task-5-value-graph-javap.log):

```text
Application.embeddedProductRunGate
  → lock + StateFlow<ProductRunStatus>/phase + scalar counters/flag
  → ProductRunStatus: token identity + scalars/enums + typed issue + copied immutable values
  → cleanupBlockedUi(): strings + typed cause + copied value evidence + immutable safe action set
```

Projection không được lưu vào gate, không đổi gate instance fields. Không có đường từ status/projection tới controller, runner, session, renderer, execution adapter, Binder, Surface/View/Activity, Job/Deferred, Throwable hoặc callback/navigation của route cũ. Callback Compose chỉ thuộc UI hiện tại; không nằm trong Application status/model. Blocked branch return trước loader, probe/refresh, route execution factory và renderer; không có Binder/Shizuku/session query để render blocked UI.

## Diff, bảo toàn và review

- Đã đọc scoped `git diff -- <four production files>` và đối chiếu từng file với snapshot working-tree trước Task 5.
- SHA256: **150 file changed/untracked có trước ngoài hai allowed files đã modified trước đó giữ nguyên; unexpected changes = 0**. Home/navigation trước đó không modified; chỉ thêm edits của Task 5. Không đụng Task 3G isolation, controller/runner/session lifetime, AIDL/protocol, renderer 007, Classic launcher, schema, license/security/release.
- `git diff --check`: **PASS**. Kiểm tra whitespace file mới riêng sau khi viết báo cáo.
- Self-review theo requesting-code-review; không có callable subagent tool. Review này không phải review độc lập. Finding quan trọng về copy SurfaceLost được sửa bằng RED → GREEN; không còn finding/blocker trong Task 5. Không mở rộng sang proof-route guards hoặc diagnostics.
- Không reset/stash/clean/stage/commit/push; không chạy full Task 8 gate hoặc device proof.

## NOT VERIFIED

Actual Compose semantics trên thiết bị, NavController/device Back/recreation, actual Surface/native/vendor allocation, heap/GC, Binder scheduling và authoritative cleanup của permanently hung remote Start: **NOT VERIFIED**. Navigation/recreation evidence của Task 5 là pure model/controller integration/source audit, không phải device proof. Không persistence hoặc process-death recovery; không tuyên bố khôi phục hay remote ownership sạch.

**STOP AFTER TASK 5. Không bắt đầu Task 6.**
