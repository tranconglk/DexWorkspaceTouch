# DWT-VDM-009 — Task 7: Normal product UI + developer proof access

Ngày: 2026-10-02 (Asia/Saigon). Nhánh: `release/1.0-beta`.

**Task 7 hoàn tất: targeted GREEN và affected regression PASS.** Chỉ thực hiện Task 7 theo yêu cầu EXECUTE TASK 7 ONLY. Không có blocker trong phạm vi này. Tasks 8–9 không được thực hiện.

## Thay đổi và giới hạn

- `app/src/main/java/com/trancong/dexworkspacetouch/ui/screens/HomeScreen.kt`: chuyển năm proof control khỏi header Home rộng/hẹp vào sheet **Nhà phát triển (Thử nghiệm)**. Model menu chỉ có một Boolean bất biến, được giữ bằng `remember` tại Home; không có persistent setting.
- `app/src/main/java/com/trancong/dexworkspacetouch/navigation/TouchNavigation.kt`: giữ nguyên năm route string; dùng enum route chung cho đăng ký destination/test. Thêm seam `enterEmbeddedProofRoute` đọc Application `EmbeddedProductRunGate.canEnterEmbedded()` tại dispatch và destination, cùng UI từ chối có Back/Xem trạng thái Embedded.
- Mới `app/src/test/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/product/EmbeddedProofAccessTest.kt`: 65 case, parameterized theo năm route.
- Báo cáo này và các artifact `task-7-*` là evidence mới. Không sửa proof screen, gate, controller/runtime/session/renderer, Task 5 policy hoặc Task 6 schema/formatter.

Scope lock: chỉ Home/navigation, test proof access và evidence Task 7. Authorization hiện hành thay trạng thái NOT AUTHORIZED lịch sử của riêng Task 7 trong plan. Triển khai tại working tree hiện có theo yêu cầu giữ các task trước; không tạo checkout mới, không commit, không chạy script kết thúc toàn bộ plan hoặc tiếp tục Task 8.

## RED → GREEN

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests '*.EmbeddedProofAccessTest' --console=plain
```

Lượt sandbox dừng ở resolve Android Gradle plugin 9.2.1; cùng command chạy với cache/SDK hiện có đã tới tests. Lỗi visibility `public` test constructor dùng enum `internal` đã sửa bằng `internal class`; lỗi môi trường/biên dịch không được tính là behavioral RED.

Test được viết trước, rồi bổ sung scaffold biên dịch tối thiểu phản ánh presentation/admission cũ: menu vẫn trả năm control ở Home và route seam gọi content không kiểm tra gate. **Behavioral RED: 65 tests, 55 FAIL, 10 PASS, errors/skipped 0**; toàn bộ 55 failure là `AssertionError`. [RED XML](task-7-red.xml), [RED log](task-7-red.log).

Sau triển khai: **65/65 PASS**, failures/errors/skipped 0; Kotlin production và tests biên dịch thành công. [GREEN XML](task-7-green.xml), [GREEN log](task-7-green.log).

Test kiểm tra model presentation được Home dùng và admission function production mà cả callback điều hướng lẫn destination dùng. Đây là behavioral unit test với source audit wiring; không tuyên bố đã chạy Compose semantics hoặc NavController trên thiết bị. Scaffold RED không phải device baseline. Có warning Kotlin về type inference của parameterized fixture; không có compilation error.

## Home và developer entry

Home vẫn là **Workspace Library**. Các card giữ **Mở** qua `productRouting.openClassic` và **Mở Embedded (Thử nghiệm)** qua `productRouting.openEmbedded`; Classic vẫn là đường mặc định. Pinned/regular card callbacks, tạo/tìm/sắp xếp/chỉnh sửa/quản lý/import/export workspace, Car Mode, Cập nhật và Giới thiệu giữ nguyên. Không đưa tính năng sản phẩm vào mục developer.

Trong cả layout rộng/hẹp, người dùng chọn **Nhà phát triển (Thử nghiệm)** để mở một `ModalBottomSheet`. Chỉ khi sheet mở mới tạo năm nút proof:

1. `Embedded Waze`
2. `Embedded Calculator`
3. `Embedded Dual App`
4. `Embedded Workspace Runner (Experimental)`
5. `Embedded Workspace Layout (Experimental)` — cần workspace đã chọn.

Mở/đóng sheet chỉ đổi Boolean cục bộ và dựng menu; không gọi proof callback, không tạo Embedded graph/token, không bind/connect hoặc xin quyền Shizuku, không Start. Sheet vẫn xem được khi product gate đang giữ ownership; các proof button disabled theo UI hiện hành và callback vẫn có guard độc lập với enabled state.

Task 5 blocked status ở Home và đường **Xem trạng thái Embedded** giữ nguyên. Task 6 **Sao chép chẩn đoán** giữ nguyên ở product screen, gồm nhánh blocked trước allocation. Direct proof bị từ chối hiển thị Back và Xem trạng thái Embedded nếu có workspace provenance; không có Start/Classic/Retry/cleanup/unlock workaround.

## Route classification và source audit

Không phân loại theo tên: đã đọc các constructor, effects và renderer của mỗi screen. **Tất cả năm route thuộc loại A: execution-capable/potentially conflicting. Không có route loại B.**

Guard chung tại `TouchNavigation.kt:131` (`enterEmbeddedProofRoute`), đọc `gate.canEnterEmbedded()` trước gọi content. `openProof` tại dòng 186 kiểm tra khi dispatch; `EmbeddedProofDestination` tại dòng 147 quan sát status để cập nhật UI và kiểm tra gate mới nhất ở dòng 156 trước gọi child screen. Gate authority giữ nguyên, gồm kiểm tra token/IDLE và Classic dispatch đang diễn ra.

| Exact route được bảo toàn | Loại | Guard destination trong TouchNavigation.kt | Allocation sau guard |
| --- | --- | --- | --- |
| `embedded-waze` | A | dòng 506 | `EmbeddedWazeScreen` → session constructor dòng 42, `session.start` effect dòng 45, `SurfaceView` dòng 66 |
| `embedded-calculator` | A | dòng 512 | `EmbeddedCalculatorScreen` → session constructor dòng 42, effect dòng 45, `SurfaceView` dòng 66 |
| `embedded-dual-app` | A | dòng 518 | `EmbeddedDualAppScreen` → hai pane, session constructor/effect dòng 32–33, `SurfaceView` dòng 44 |
| `embedded-workspace-runner` | A | dòng 524 | `EmbeddedWorkspaceRunnerScreen` → runner dòng 57, controller dòng 66, pane `SurfaceView` dòng 163 |
| `embedded-workspace-layout/{workspaceId}` | A | dòng 555 | `EmbeddedWorkspaceLayoutScreen` → loader/geometry trước run content; runner dòng 92, renderer coordinator dòng 99, controller dòng 102 và renderer/Surface phía dưới |

Destination registration dùng chính `EmbeddedProofRoute.pattern`; layout vẫn có `navArgument("workspaceId")` String và dispatch vẫn `Uri.encode` ID. Không xóa/đổi route hoặc screen. Gate IDLE tiếp tục gọi content proof hiện có; các Start/Connect bên trong proof giữ nguyên hành vi trước đây. Task 7 chỉ thay admission/presentation, không đăng ký proof như một product run hoặc đổi runtime ownership.

[Source scan sau GREEN](task-7-route-source-audit.txt) cho thấy mỗi screen chỉ có định nghĩa và một điểm gọi production tại navigation; từng điểm gọi nằm bên trong `EmbeddedProofDestination`. Năm callback Home đều gọi `openProof`; không còn alternate unguarded route callback hoặc đăng ký route cũ bên cạnh registration guarded. Đã đối chiếu [navigation diff so với working-tree baseline](task-7-navigation.diff) và [Home diff](task-7-home.diff), cùng scoped `git diff --`.

## Phases, counters, stale UI và direct entry

Với **mỗi route**, các phase **STARTING, ACTIVE, STOPPING, CLEANUP_BLOCKED** đều trả false trước content/factory callback. IDLE trả true và gọi content một lần. Classic dispatch đang chạy cũng từ chối proof.

Counter độc lập đặt trong callback allocation ngay sau production admission seam:

```text
route dispatch         = 0 (click bị chặn)
controller construction = 0
execution construction  = 0
renderer creation       = 0
Surface creation        = 0
session construction    = 0
session/start allocation = 0
token acquisition       = 0
Shizuku bind/connect    = 0
```

Đây là injected unit-test counters ở boundary; không phải đo constructor/native/Binder thật trên Android. Việc content callback không được gọi kết hợp source audit chứng minh các điểm tạo runtime phía dưới guard không được vào khi admission bị từ chối. Trong case navigation đã dispatch khi IDLE nhưng gate đổi trước destination, **dispatch = 1** là thao tác trước đó; toàn bộ counter allocation tại destination vẫn **0**.

- Stale developer menu render khi IDLE, sau đó gate chuyển sang từng non-IDLE phase trước click: **dispatch bị từ chối, counter 0** cho cả năm route.
- Gate đổi sau navigation dispatch nhưng trước destination: destination **kiểm tra lại và từ chối**, không cấp phát.
- Gọi admission của destination trực tiếp, không đi qua Home/menu; gọi lại như restored destination trong cùng process: **không bypass**, mỗi lần bị chặn trước callback.
- CLEANUP_BLOCKED chỉ cần Application value status, không cần giữ/query old controller/runtime: direct attempts không làm thay đổi chính object status, generation, operation hoặc evidence.
- Menu vẫn viewable; blocked UI `BACK/VIEW_STATUS` và formatter/copy seam Task 6 vẫn dùng được. Copy chỉ ghi khi user tap; Classic và Embedded vẫn bị chặn sau proof attempts/copy.
- Mở developer section trong cả năm phase chỉ tạo value menu: không gọi dispatch/allocation seam và giữ nguyên gate status.

## Affected regression

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests '*.EmbeddedCleanupBlockedUxTest' --tests '*.EmbeddedProductDiagnosticFormatterTest' --tests '*.EmbeddedProductRunGateTest' --tests '*.EmbeddedProductIssueTest' --tests '*.EmbeddedWorkspaceProductRoutingTest' --tests '*.EmbeddedShizukuRefreshTest' --tests '*.EmbeddedProductLifecycleTest' --tests '*.EmbeddedWorkspaceProductControllerTest' --console=plain
```

**BUILD SUCCESSFUL, exit 0; 157/157 PASS**, failures/errors/skipped 0. [Regression log](task-7-regression.log), [XML summary](task-7-regression-summary.json); từng XML đã lưu trước khi bị Gradle ghi đè.

| Class | PASS |
| --- | ---: |
| EmbeddedCleanupBlockedUxTest | 29 |
| EmbeddedProductDiagnosticFormatterTest | 32 |
| EmbeddedProductRunGateTest | 16 |
| EmbeddedProductIssueTest | 17 |
| EmbeddedWorkspaceProductRoutingTest | 3 |
| EmbeddedShizukuRefreshTest | 14 |
| EmbeddedProductLifecycleTest | 22 |
| EmbeddedWorkspaceProductControllerTest | 24 |

Không cần chạy thêm Task 3G terminal/cache class: route guard chỉ đọc Application gate; direct blocked tests dùng value-only status, và affected lifecycle/controller tests đã bao phủ terminal/drop-runtime. Không chạy full assemble/lint matrix, Task 8 local gate hoặc device proof.

## Value/reference graph và preservation

[Compiled javap audit](task-7-javap.txt): `HomeEmbeddedProofMenu` chỉ có một instance field `final boolean isOpen`; `EmbeddedProofRoute` chỉ có String pattern và enum constants. `TouchNavigationKt` có các static functions/UI callback implementations; không thêm owner hoặc retained proof handle. Test reflection kiểm tra menu chỉ có Boolean; controls là enum values/string labels.

```text
Home Compose remember → immutable menu(Boolean) → derived enum controls(String labels)
UI-scoped navigation callbacks → current NavController/Application gate
Application → existing ProductRunGate → existing value-only status
```

Không có đường mới từ Application state tới menu/navigation callback, controller, runner, session, renderer, Surface/View/Activity, Job/Deferred hoặc old-route execution callback. Không thêm field vào Application/gate/status; tất cả callback mới thuộc composition hiện tại, không được lưu vào gate. Không tạo persistent developer state hoặc execution handle.

SHA256 xác nhận **184 tệp changed/untracked có trước ngoài hai allowed files giữ nguyên; unexpected changes = 0**. Audit paths mới/modified ngoài Task 7 và baseline: **0**. [Scope audit](task-7-scope-audit.txt), [baseline hashes](task-7-baseline-hashes.json). Hai allowed files có snapshot trước Task 7 và diff riêng để bảo toàn Task 5/6 wiring.

`git diff --check`: **PASS, exit 0**; [evidence](task-7-diff-check.txt). Test mới có 0 dòng trailing whitespace; không stage để kiểm tra untracked file. Tự review source/diff theo checklist code review; không có callable reviewer/subagent độc lập. Không còn blocker hoặc finding cần sửa trong Task 7.

Không sửa Task 3G, runner 006, renderer 007 semantics, remote protocol/AIDL, Task 3B gate authority, Task 5 action policy, Task 6 diagnostics, Classic launcher, Room/schema, license/security/release, dependency hoặc production configuration. Không reset/stash/clean/stage/commit/push.

## NOT VERIFIED và điểm dừng

Actual Compose semantics/layout, NavController deep/direct/restored navigation trên thiết bị, Activity recreation thực, native Surface allocation, Binder/Shizuku scheduling, heap/GC và authoritative cleanup của permanently hung remote Start: **NOT VERIFIED**. Unit tests/source/compiled-field audit không được trình bày như device proof. Không claim process-death recovery hoặc cleanup proof runtime sạch có thẩm quyền.

**STOP AFTER TASK 7. Không bắt đầu Task 8 hoặc Task 9.**
