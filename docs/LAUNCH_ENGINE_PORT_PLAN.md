# Launch Engine Port Plan

Task: E3-005

## Source implementation

Implementation tham chiếu nằm trong project chỉ đọc `DexWorkspaceManager`.

Luồng production đã được dùng là:

```text
Home/LayoutEditor Compose route
→ Activity.currentExternalDisplayWorkArea()
→ WorkspaceLaunchCoordinator
→ LayoutBoundsCalculator
→ AndroidForegroundAppLauncher
→ Activity.startActivity(intent, ActivityOptions)
```

Các thành phần chính:

- `platform/applauncher/WorkspaceLaunchCoordinator.kt`: lọc/sắp assignment, tính bounds,
  launch tuần tự, delay và tổng hợp partial result.
- `platform/applauncher/AndroidForegroundAppLauncher.kt`: kiểm tra package/activity, tạo
  launcher intent, gắn bounds và gọi Activity đang ở foreground.
- `platform/applauncher/LayoutBoundsCalculator.kt` và `LaunchBounds.kt`: chuyển vùng
  chuẩn hóa sang pixel bounds.
- `platform/dex/DexWorkArea.kt`: kích thước, origin và bottom inset của vùng làm việc.
- `platform/dex/DisplayContextUtils.kt`: xác nhận Activity không chạy trên default display.
- `platform/dex/AndroidDexDisplayProvider.kt`: liệt kê external display và heuristic nhận
  diện DeX; không tham gia trực tiếp vào đường launch production hiện tại.
- `AndroidAppLauncher.launchOnTargetDisplayForDiagnostics()`: đường diagnostics dùng
  `setLaunchDisplayId`; không phải chiến lược launch chính đã được xác nhận.

`docs/TEST_MATRIX.md` của project tham chiếu ghi nhận mở từng app đúng vùng và launch toàn
workspace đã PASS trên Note8 Hades v3 và S23 Ultra. Cắm/rút DeX vẫn là NOT TESTED.

Không có test trực tiếp cho coordinator, Android launcher hoặc display provider. JVM test
liên quan launch duy nhất là `LayoutBoundsCalculatorTest`.

## Target architecture

Contract đã có trong `DexWorkspaceTouch`:

```text
WorkspaceCanvas
→ WorkspaceLaunchRequestFactory
→ LaunchReadiness
→ WorkspaceLauncher
→ WorkspaceLaunchResult
```

Đề xuất infrastructure:

```text
AndroidWorkspaceLauncher : WorkspaceLauncher
├── ForegroundLaunchHost
├── AndroidWorkAreaProvider
├── LaunchBoundsResolver
├── AndroidSingleAppLauncher
└── LaunchSequencingPolicy
```

- `WorkspaceLaunchRequestFactory` tiếp tục thuộc Kotlin domain/application boundary.
- Mọi `Activity`, `Intent`, `Rect`, `ActivityOptions`, `Display` và `PackageManager` chỉ
  xuất hiện trong infrastructure.
- `AndroidWorkspaceLauncher` nhận một host Activity có lifecycle rõ ràng, không truy cập
  `MainActivity` bằng singleton/static/Application cache.
- UI chỉ yêu cầu launch và render `WorkspaceLaunchResult`; UI không tạo Intent hoặc Rect.

## Files to port

Không có source file nào nên copy nguyên trạng 100% vì model và result contract đích đã
khác. Các phần logic cần port có kiểm soát:

| Source | Phần cần giữ | Dạng đích đề xuất |
|---|---|---|
| `LaunchBounds.kt` | invariant pixel bounds dương | model internal `PixelBounds` trong infrastructure |
| `LayoutBoundsCalculator.kt` | round, origin, margin, clamp | `LaunchBoundsResolver`, nhận `NormalizedBounds` |
| `AndroidForegroundAppLauncher.kt` | verify package/activity, flags, ActivityOptions, exception mapping | `AndroidSingleAppLauncher` |
| `WorkspaceLaunchCoordinator.kt` | thứ tự tuần tự, cancellation, delay, aggregate result | `AndroidWorkspaceLauncher` |
| `DexWorkArea.kt` | work-area dimensions/origin/inset | model internal `AndroidWorkArea` |
| `DisplayContextUtils.kt` | xác định host display | `ForegroundLaunchHost`/display adapter |

`LayoutBoundsCalculatorTest` có thể port gần như nguyên bộ dữ liệu test, thay `LayoutZone`
bằng `NormalizedBounds` và `LaunchBounds` bằng model pixel đích.

## Files not to port

- `AndroidAppLauncher.kt`: đường launch application-context và diagnostics dùng
  `setLaunchDisplayId`; chỉ tham khảo exception/feature checks.
- `AppLauncher.kt`, `ForegroundAppLauncher.kt`, `AppLaunchResult.kt`: contract mới đã thay
  thế các interface/result cũ.
- `feature/layouteditor/WorkspaceLaunchResult.kt`: chứa label/reason String dành cho UI cũ.
- `LayoutTemplates`, `LayoutZone`, `Workspace`, `WorkspaceAppAssignment`: canvas mới đã cung
  cấp target và normalized bounds trực tiếp.
- `HomeScreen`, `LayoutEditorScreen`, `SavedLayoutsScreen` và các ViewModel cũ: launch được
  điều phối trực tiếp trong Compose và phụ thuộc UI state cũ.
- `AppContainer.kt`: kéo theo Application service locator, Room, repository, monitor và các
  subsystem không thuộc port tối thiểu.
- `WorkspaceRepository`, Room entity/DAO/mapper và transfer/preferences code: Library mới
  đang dùng RAM và request factory không cần persistence.
- `AndroidDexDisplayProvider` không nên copy nguyên trạng: heuristic theo display name và
  quy tắc “chỉ một external display thì xem là DeX” chưa đủ mạnh cho launch production.
- `MainActivity.kt` không port: chính sách DeX-only và package monitor của app cũ không phải
  launch engine. Chỉ cô lập khả năng cung cấp foreground Activity ở pha integration.

## API mapping

| Source | Target contract |
|---|---|
| `Workspace` + assignments | `WorkspaceLaunchRequest` |
| assignment package/activity | `AppLaunchTarget.identity` |
| `LayoutZone` | `AppLaunchTarget.bounds` |
| `launchOrder` + zone-id tie-break | `AppLaunchTarget.order` |
| `AppLaunchResult.Success` | `AppLaunchTargetResult` |
| source partial aggregate | `WorkspaceLaunchResult.PartialSuccess` |
| all targets succeed | `WorkspaceLaunchResult.Success` |
| all targets fail | `WorkspaceLaunchResult.Failure` |

`AppLaunchTarget` không mang label. Logging có thể dùng stable identity/order; UI không phụ
thuộc raw Android exception.

## Bounds conversion

Port công thức hiện tại sang input `NormalizedBounds`:

```text
workRight  = originX + workWidth
workBottom = originY + workHeight

left   = round(originX + bounds.left   * workWidth)  + marginPx
top    = round(originY + bounds.top    * workHeight) + marginPx
right  = round(originX + bounds.right  * workWidth)  - marginPx
bottom = round(originY + bounds.bottom * workHeight) - marginPx
```

Sau đó clamp từng cạnh vào work area và từ chối kết quả có `right <= left` hoặc
`bottom <= top`. Dùng `roundToInt` để giữ contract đã test. Margin tham chiếu là 8dp,
chuyển sang px theo density của display/host tại thời điểm launch; cần token/policy rõ ràng
thay vì hard-code trong coordinator.

Normalized bounds vẫn nằm trong request. Chỉ infrastructure chuyển sang `android.graphics.Rect`.

### Work area sau taskbar/system bars

Code tham chiếu gọi `Display.getRealMetrics()` và tạo `DexWorkArea` với `bottomInset = 0`.
Do đó implementation hiện tại dùng toàn bộ real display và **chưa chứng minh** đã loại taskbar
hoặc system bars. `DexWorkArea` có khả năng trừ bottom inset nhưng call site không truyền inset.

E3-006A cần:

1. Lấy real display bounds từ display của foreground Activity.
2. Đọc `WindowInsets` của chính host window cho `systemBars()`/display cutout; có fallback
   tương thích API 28–29.
3. Chuyển inset thành origin và usable width/height, không chỉ giả định taskbar luôn ở đáy.
4. Chụp một snapshot work area ngay trước mỗi workspace launch và xác nhận display vẫn còn.
5. Instrument-test/kiểm thử tay ở taskbar auto-hide, taskbar hiện, các cạnh navigation và
   cửa sổ host maximized/non-maximized trên cả hai thiết bị.

Nếu DeX không báo taskbar qua public WindowInsets trên một ROM, cần ghi nhận giới hạn và
thiết lập policy đã đo/được người dùng cấu hình; không dùng Samsung private API trong pha này.

## Display selection

Chiến lược production tham chiếu không hard-code/chọn display ID:

- `MainActivity` tự kết thúc nếu chạy trên `Display.DEFAULT_DISPLAY`.
- Launcher nhận Activity đang foreground trên external display.
- `Activity.startActivity()` được gọi từ Activity đó mà không gọi `setLaunchDisplayId`.

Đây là chiến lược ưu tiên để port vì đã được ghi nhận PASS trên Note8 và S23 Ultra. Adapter
đích phải xác nhận ngay trước launch rằng:

- host Activity còn active;
- `Activity.display?.displayId` không null và không phải default display;
- display vẫn `STATE_ON` và có kích thước hợp lệ.

Khi có nhiều external display, display đích là display chứa host Activity, không phải display
đầu tiên từ `DisplayManager`. `setLaunchDisplayId` chỉ xem xét như diagnostics/fallback riêng
sau khi có test thiết bị; không đưa vào luồng chính E3-006A–E.

## Launch sequencing

Coordinator cũ:

- sort theo `launchOrder`, tie-break bằng zone ID;
- launch tuần tự;
- kiểm tra coroutine cancellation trước từng target;
- gọi `delay()` giữa các target, không delay sau target cuối;
- mặc định 400ms, cho phép 0–5000ms.

Contract mới đã có order duy nhất, nên target được sort tăng dần theo `order`; không cần
tie-break. Delay không nằm trong `WorkspaceLaunchRequest`. Pha E3-006C nên đưa delay vào
`LaunchSequencingPolicy`, mặc định ban đầu 400ms để giữ hành vi đã xác nhận.

Source không ghi lý do kỹ thuật cho 400ms. Giả thuyết cần kiểm chứng là tạo khoảng thời gian
cho DeX Window Manager dựng/định vị task trước khi launch app tiếp theo. Không xem giả thuyết
này là fact cho đến khi test 0/100/250/400ms trên Note8 và S23 Ultra.

Cancellation phải được truyền tiếp, không map thành `UNKNOWN`. Result khi cancellation cần
được quyết định trước integration vì contract E3-004 chưa có trạng thái Cancelled.

## Failure mapping

| Android/source condition | Target reason | Xử lý |
|---|---|---|
| package không tồn tại/`NameNotFoundException` khi verify package | `APP_NOT_FOUND` | target fail, tiếp tục target kế |
| component/activity không tồn tại hoặc `ActivityNotFoundException` | `ACTIVITY_NOT_FOUND` | target fail, tiếp tục |
| `SecurityException` | `SECURITY_RESTRICTION` | không đưa exception ra UI |
| host/display mất trước hoặc trong sequence | `DISPLAY_UNAVAILABLE` | target hiện tại và các target chưa chạy fail cùng reason; dừng sequence |
| `IllegalArgumentException`, bounds/feature không hỗ trợ, platform từ chối | `LAUNCH_REJECTED` | lưu technical message an toàn nếu có |
| exception không phân loại | `UNKNOWN` | logging nội bộ, không phụ thuộc raw message ở UI |

Mỗi target tạo `AppLaunchTargetResult` hoặc `AppLaunchFailure`:

- không failure: `Success`;
- có cả launched và failed: `PartialSuccess`;
- không target nào launched: `Failure`.

Sau lỗi app/activity/security riêng lẻ, sequence tiếp tục để hỗ trợ partial success. Sau khi
display mất, tiếp tục gọi `startActivity` không có ích và có thể sai display, nên dừng có chủ ý.

## Duplicate app policy

Designer và request mới cho phép cùng identity ở nhiều target. Code cũ cũng không deduplicate
assignment; mỗi target được launch với:

- `Intent.ACTION_MAIN` + `CATEGORY_LAUNCHER`;
- explicit `ComponentName`;
- `FLAG_ACTIVITY_NEW_TASK`;
- `FLAG_ACTIVITY_MULTIPLE_TASK`.

Port phải thử từng duplicate target theo order và giữ result riêng, không bỏ target. Hai flag
tăng khả năng tạo task riêng nhưng không bảo đảm mọi ứng dụng tạo nhiều instance; task affinity,
launch mode hoặc chính ứng dụng có thể tái sử dụng task/window. Kết quả `startActivity` thành
công chỉ xác nhận request được chấp nhận, không chứng minh có hai instance độc lập.

## Note8 compatibility

- Thiết bị tham chiếu là Note8 + Hades ROM v3; min API của project cũ là 28.
- Đường foreground Activity + `setLaunchBounds`, không `setLaunchDisplayId`, đã được ghi nhận
  PASS cho mở từng app đúng vùng và toàn workspace.
- Giữ nhánh API cũ cho display lookup và PackageManager flags.
- Không dùng Samsung private/hidden API.
- Cần retest taskbar insets, display disconnect, duplicate target, delay thấp và các app có
  launchMode đặc biệt; test matrix cũ chưa bao phủ các trường hợp này.

## S23 Ultra compatibility

- Cùng đường foreground Activity đã được ghi nhận PASS.
- Dùng API hiện đại cho `Activity.display`, PackageManager flags và WindowInsets khi có.
- Không dùng `requestPinShortcut`: tài liệu cũ ghi dialog có thể xuất hiện trên màn hình điện thoại.
- Cần retest multi-display, taskbar auto-hide, resize host window và rút/cắm DeX; các mục reconnect
  trong test matrix cũ vẫn NOT TESTED.

## Test plan

### JVM tests có thể port

- Toàn bộ matrix `LayoutBoundsCalculatorTest`: full display, tỷ lệ, origin/margin, resolution
  khác, rounding, clamp và positive bounds.
- Bổ sung system inset ở cả bốn cạnh, bounds sát cạnh và margin làm cell quá nhỏ.
- Sequencing executor với fake single-app launcher và fake delay: order, một delay giữa hai
  target, không delay cuối, cancellation và stop-on-display-loss.
- Result aggregation: all success, partial, all failure và failure reason mapping từ sealed
  infrastructure result không chứa Android type.
- Duplicate identity: mọi target được gọi theo order và giữ result riêng.

### Android instrumentation/device tests

- `PackageManager` package/activity verification và component resolution.
- Intent flags/component/category thực tế.
- `ActivityOptions.setLaunchBounds(Rect)` tạo cửa sổ đúng vùng.
- Foreground Activity routing không dùng `setLaunchDisplayId` trên Note8/S23 Ultra.
- Display disconnect/reconnect và host Activity lifecycle giữa sequence.
- Work area với DeX taskbar/system bars/cutout ở các trạng thái.
- Security/platform rejection mapping.
- Duplicate app với các launch mode/task affinity khác nhau.
- Delay sweep và launch nhiều app liên tục.

Test dùng emulator/Robolectric không thay thế xác minh DeX thực trên hai thiết bị.

### Dependency tối thiểu

Không cần dependency mới cho production port tối thiểu:

- Android framework: `Activity`, `ActivityOptions`, `Intent`, `ComponentName`, `Rect`,
  `PackageManager`, `Display`/`DisplayManager`, `WindowInsets`.
- Kotlin coroutines đã có trong project cho `WorkspaceLauncher.suspend`, sequencing và `delay`.
- JUnit hiện có đủ cho JVM unit tests.

Instrumentation tests có thể dùng AndroidX test dependencies chỉ nếu project đã có sẵn; nếu
chưa có thì dependency test phải được phê duyệt ở task implementation tương ứng.

## Risks

- Real display metrics hiện chưa loại taskbar/system bars một cách được chứng minh.
- Public WindowInsets có thể khác nhau giữa Samsung ROM/DeX version.
- `startActivity` success không bảo đảm window cuối cùng giữ đúng bounds hoặc app tạo instance mới.
- Host Activity có thể bị resize/destroy hoặc chuyển display trong lúc sequence chạy.
- Package/activity có thể biến mất sau readiness check và trước launch.
- Delay 400ms là hành vi đã tồn tại nhưng lý do/giá trị tối ưu chưa có test định lượng.
- Contract hiện chưa mô tả cancellation; cần quyết định trước E3-006C.
- `LAUNCH_REJECTED` gộp nhiều lỗi platform/bounds; logging internal cần đủ để chẩn đoán nhưng
  không làm UI phụ thuộc exception.

## Port phases

### E3-006A — Bounds and display adapter

Trạng thái: **đã hoàn thành phần contract/calculator thuần Kotlin**.

- Đã tạo `PixelBounds`, `DisplayWorkArea`, `LaunchBoundsCalculator`, typed calculation
  failure, provider contract và JVM tests.
- `DisplayWorkArea` hỗ trợ đầy đủ left/top/right/bottom inset và là snapshot tại thời điểm
  chuẩn bị launch.
- Calculator dùng usable rectangle sau inset, `roundToInt`, margin px hướng vào trong và
  clamp vào usable bounds.
- Margin mặc định được định danh là platform config 8dp; Android adapter sau này chịu trách
  nhiệm đổi sang px theo density của foreground host/display.
- Margin hoặc rounding làm mất kích thước trả `INSUFFICIENT_SPACE`; không tạo bounds đảo cạnh.
- Chưa tạo Android Rect mapper, lifecycle-safe foreground host hoặc WindowInsets provider.
- Vẫn cần xác minh cách DeX cung cấp system-bar/taskbar insets trên Note8 Hades v3 và S23
  Ultra, cả windowed/maximized và taskbar hiện/ẩn.
- Chưa gọi `startActivity`.

### E3-006B — Single app launcher

Trạng thái: **đã hoàn thành phần display work-area adapter và Rect mapper; single-app
launch vẫn chưa triển khai**.

- `ActivityDisplayWorkAreaProvider` nhận Activity trực tiếp, không singleton/Application
  Context, và đọc snapshot mới ở mỗi lần gọi.
- API 30+ dùng `WindowManager.maximumWindowMetrics` làm toàn external-display bounds;
  `currentWindowMetrics` chỉ dùng để ghi diagnostics windowed/maximized.
- API 28–29 dùng display gắn với Activity + real metrics, nhưng bắt buộc root window insets
  đã sẵn sàng; đây không phải fallback đoán taskbar.
- Insets là union theo cạnh của system bars và display cutout; không dùng IME, không cộng
  cutout hai lần.
- Display được lấy từ host Activity và xác nhận lại qua `DisplayManager`; không tự chọn
  display khác. Default/OFF/removed display trả unavailable.
- Density lấy từ `createDisplayContext(hostDisplay)`, không lấy từ Application Context.
- `PixelBounds.toAndroidRect()` là boundary duy nhất hiện tại import `android.graphics.Rect`.
- Provider ghi log diagnostics `DexLaunchWorkArea`: display ID, display/window bounds, bốn
  inset, usable size, density và host window mode.
- Cần device-test WindowMetrics/insets trên Note8 Hades v3 và S23 Ultra, maximized/windowed,
  taskbar visible/hidden/auto-hide, disconnect và đổi resolution.
- Chưa verify package/activity, chưa tạo Intent/ActivityOptions và chưa gọi `startActivity`.

#### E3-006B.1 — Stable taskbar inset resolution

Kết quả thiết bị dẫn tới hai nguyên nhân code-level:

- S23 Ultra windowed đúng nhưng fullscreen bị che vì API 30+ chỉ dùng
  `maximumWindowMetrics.windowInsets.getInsets(systemBars)`. Giá trị này phụ thuộc visibility;
  fullscreen có thể trả bottom 0 dù vùng taskbar ổn định vẫn cần được bảo vệ.
- Note8 windowed bị che nhưng fullscreen đúng vì full-display real metrics được kết hợp với
  `rootWindowInsets.systemWindowInset*` của host. Freeform host không chạm taskbar nên system
  inset có thể bằng 0; stable inset trước đây bị bỏ qua.

Policy mới dùng `WorkAreaInsetResolver` thuần Kotlin:

- API 30+: chỉ chọn candidate cùng maximum-metrics coordinate space, hợp nhất visible
  system bars, ignoring-visibility system bars và display cutout bằng max từng cạnh.
- API 28–29: hợp nhất root system, root stable và display cutout trên full-display bounds.
  Display metrics, visible frame và host bounds được giữ làm diagnostics nhưng không được
  dùng nếu thuộc host-window coordinate space.
- Candidate lệch coordinate space, inset âm/quá nửa display hoặc usable area không dương bị
  loại. Không còn candidate đáng tin thì provider trả null.
- Snapshot ghi toàn bộ candidate cùng `selectedInsetSource`. Provider không tự log mỗi lần
  đọc; debug harness log khi người dùng chụp thông số hoặc ngay trước debug launch.

Device validation cần ghi cho từng case: raw bounds, mọi candidate, selected source, final
usable bounds, final Rect và việc app có bị taskbar che hay không:

- S23 Ultra: windowed/fullscreen với taskbar visible, auto-hide và hiện lại.
- Note8 API 28/29: windowed/fullscreen với taskbar visible và auto-hide nếu có.

#### E3-006B.2 — Legacy display-metrics fallback

API 28–29 bổ sung `DISPLAY_METRICS_DELTA` từ chính external `Display`: reference bounds lấy từ
`getRealMetrics()`, usable width/height lấy từ `getMetrics()`. Vì API legacy không cung cấp origin
cho metrics, candidate chỉ suy ra right/bottom delta và chỉ chấp nhận khi real bounds bắt đầu tại
0,0. Không có taskbar constant hoặc nhánh theo model thiết bị.

Validation thuần Kotlin yêu cầu mọi kích thước dương, metrics không lớn hơn real metrics, mỗi delta
không quá nửa display và usable area còn dương. Khi host đang windowed, metrics gần bằng cả width
và height của host (tolerance tỷ lệ 2% display) bị phân loại là host-window-sized và không được dùng
làm desktop work area. Nếu root không có inset dương và metrics candidate bị từ chối, provider trả
unavailable thay vì đoán.

Resolver áp dụng ưu tiên theo từng cạnh: root system/stable/cutout dương được chọn trước;
`DISPLAY_METRICS_DELTA` chỉ là fallback cho cạnh root bằng 0. Điều này tránh lấy max mù giữa hai
nguồn cùng mô tả taskbar, đồng thời vẫn cho phép kết hợp cutout ở cạnh trên với metrics taskbar ở
cạnh dưới. Thứ tự input quyết định tie giữa các nguồn root nên kết quả deterministic. Nhánh API
30+ không tạo metrics-delta candidate và giữ nguyên policy maximum window metrics hiện có.

Debug harness hiển thị trực tiếp real metrics, display metrics, bốn delta, từng root candidate,
selected source, final work area và final PixelBounds. Matrix Note8 windowed/fullscreen vẫn cần được
chụp trên thiết bị Hades v3 để xác nhận `getMetrics()` ổn định theo desktop và bottom delta thực sự
trùng vùng taskbar luôn hiển thị.

### E3-006C — Single-target Android launcher

Trạng thái: **đã hoàn thành single-target Android launcher; multi-app sequencing vẫn chưa
triển khai**.

- `ForegroundLaunchHost` giữ Activity theo scope caller và trả null khi Activity
  finishing/destroyed, chưa attached hoặc nằm trên default display. Không có global cache.
- `ActivitySingleAppLaunchPlatform` lấy snapshot mới, xác nhận activity display ID trùng
  snapshot, verify exact package/activity/launcher resolution qua `PackageManagerAdapter`.
- Intent dùng `ACTION_MAIN`, `CATEGORY_LAUNCHER`, explicit component và chỉ hai flag
  `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK` như implementation tham chiếu.
- Bounds đi qua `LaunchBoundsCalculator`, display density margin và Rect mapper. Bounds
  insufficient trả `LAUNCH_REJECTED`, không fallback full screen.
- ActivityOptions dùng `makeBasic().setLaunchBounds(rect)`. Production path không dùng
  `setLaunchDisplayId`, vì Activity foreground trên external display là launch source đã
  được kiểm chứng ở project tham chiếu.
- `activity.startActivity(intent, options.toBundle())` chạy trong `Dispatchers.Main.immediate`.
  Cancellation được rethrow; Android exceptions được log nội bộ và map sang typed failure,
  không đưa raw exception vào domain.
- Chưa implement `AndroidWorkspaceLauncher`, sort/loop, delay, partial aggregation hoặc UI.

### E3-006C device checklist

Log cần thu: `displayId`, work area, final Rect, package/activity và typed result.

S23 Ultra:

- [ ] Host maximized.
- [ ] Host windowed.
- [ ] Taskbar visible.
- [ ] Taskbar auto-hide/hidden.
- [ ] Launch Google Maps, Chrome và VLC.
- [ ] Rút DeX ngay trước launch.

Note8 Hades v3:

- [ ] Xác minh API 28–29 real display metrics + root insets.
- [ ] Taskbar visible.
- [ ] Host maximized.
- [ ] Host windowed.
- [ ] Launch Google Maps, Chrome và VLC.
- [ ] Exact activity không tồn tại trả `ACTIVITY_NOT_FOUND`.

Instrumentation cần xác nhận Intent flags/component/category, ActivityOptions bounds,
display routing và exception behavior thực tế. Android stub local JVM không được xem là bằng
chứng cho Intent/ActivityOptions.

#### E3-006C.2 — Note8 windowed launch diagnostics

Trạng thái hiện tại: **chưa xác nhận nguyên nhân và chưa đổi production display-routing policy**.
Thiết bị Note8 Hades v3 không có trong phiên build này nên chưa có bằng chứng để phân biệt app
off-screen, launch sang sai display, hoặc app đã launch nhưng nằm sau debug host.

Debug-only harness cung cấp A/B:

- `INHERITED`: production path hiện tại, chỉ gọi `setLaunchBounds(rect)`.
- `EXPLICIT`: thêm public API `setLaunchDisplayId(snapshot.displayId)`.
- Tùy chọn đóng debug Activity 750 ms sau success để kiểm tra z-order/focus. Delay này không có
  trong production launcher.

Trước launch, harness hiển thị host/snapshot display ID, inset source, real/display metrics, host
bounds, final work area, PixelBounds và kết quả kiểm tra Rect nằm trọn trong usable area. Sau launch
harness ghi routing mode, requested display ID, result và final Rect. Production launcher cũng thực
hiện bounds sanity; Rect vượt trái/phải/trên/dưới hoặc không dương trả `LAUNCH_REJECTED`, không gửi
Intent, và Android platform log snapshot/candidates để chẩn đoán.

Chỉ sau khi matrix Note8 chứng minh inherited thất bại, explicit thành công ở windowed và cả hai
không regression ở fullscreen mới được cân nhắc policy explicit cho toàn API 28–29 external display.
API 30+ giữ inherited. Policy tương lai phải dựa trên API/capability, không dùng `Build.MODEL`.

Matrix còn chờ thiết bị:

- [ ] Windowed + inherited.
- [ ] Windowed + explicit display ID.
- [ ] Windowed + inherited rồi đóng harness.
- [ ] Fullscreen + inherited.
- [ ] Fullscreen + explicit display ID.

#### E3-006B.3 — Legacy fullscreen work-area reference

Note8 hardware chạy Hades v3/Note9 ROM đã xác nhận lỗi windowed xảy ra trước `startActivity`:
legacy provider trả unavailable, nên đây chưa phải display-routing hoặc z-order failure.

Nhánh API 28–29 giờ ghi typed evaluation cho root system, root stable, display cutout,
display-metrics delta và fullscreen reference. Diagnostics tồn tại cả khi không tạo được snapshot,
bao gồm host/real/app-metrics display ID. `getRealMetrics()` và `getMetrics()` đều được gọi trực
tiếp trên cùng external `Display`; mismatch display ID trả unavailable.

Rule host-sized chỉ reject metrics delta khi đồng thời width và height gần host và host nhỏ đáng
kể so với real display. Một chiều gần host không đủ bằng chứng. Candidate vẫn phải nằm trong real
display, tạo usable area dương và không có inset vô lý.

Debug composition root giữ `LegacyDisplayWorkAreaReferenceStore` trong debug ViewModel. Store không
giữ Activity, không dùng disk/singleton, và chỉ nhận snapshot maximized có measured inset từ nguồn
trusted. Reference được key bằng display ID và real dimensions; mismatch hoặc disconnect clear
reference. Khi direct candidate unavailable trên API 28–29, cùng reference có thể tạo snapshot với
source `LEGACY_FULLSCREEN_REFERENCE`. API 30+ không dùng fallback này.

Workflow xác minh:

1. Mở harness fullscreen, chụp snapshot và bấm “Lưu vùng làm việc hiện tại làm tham chiếu”.
2. Chuyển host sang windowed, bấm “Chụp lại thông số”.
3. Xác nhận selected source direct hoặc `LEGACY_FULLSCREEN_REFERENCE`, PixelBounds nằm trong usable
   area, rồi launch app.
4. Resize nhiều lần và ngắt/kết nối DeX để xác nhận identity invalidation.

Kết quả vật lý full-screen → windowed sau thay đổi vẫn cần thao tác trên thiết bị; build/JVM test
không được xem là bằng chứng rằng taskbar bounds và launch thực tế đã PASS.

#### E3-006B.4 — Automatic legacy reference lifecycle

API 28–29 provider tự auto-capture mỗi direct resolution có measured inset và selected source
`ROOT_SYSTEM_INSETS`, `ROOT_STABLE_INSETS` hoặc `DISPLAY_METRICS_DELTA`. Fallback-derived snapshot,
rejected candidate và host-window bounds không được ghi ngược lại store.

Reference key gồm display ID, real width/height và density; value giữ work area, original source,
capture mode (`AUTO_CAPTURED`/`MANUAL`) và sequence RAM cho diagnostics. Display mất/default/OFF,
ID hoặc resolution đổi, hay density lệch quá 2% sẽ clear/reject. Store nằm trong Activity-scoped
debug ViewModel cho harness và không giữ Activity; production composition root sau này phải sở hữu
store với lifecycle tương đương. API 30+ resolver không capture hoặc fallback.

Luồng legacy: direct → auto-capture/update → dùng direct. Nếu direct unavailable: resolve reference
cùng identity → `LEGACY_FULLSCREEN_REFERENCE`; không match thì unavailable. Process restart ở
windowed không có reference và cần phóng to một lần. Đây là limitation chủ ý, không có disk
persistence và không tự ép fullscreen.

Device validation trên Note 8 hardware chạy Hades v3/Note9 ROM, API legacy, Desktop display ID 2:

- Fullscreen: real `1920×1080`, display metrics `1920×1028`, root system/stable bottom `52`,
  selected `ROOT_SYSTEM_INSETS`, usable `1920×1028`; reference được auto-capture.
- Windowed host `598,131–1609,861`: root system/stable đều 0, `getMetrics()` co theo host thành
  `1011×730`, selected `LEGACY_FULLSCREEN_REFERENCE`, usable vẫn `1920×1028`.
- Inherited launch Chrome thành công trên display 2 với Rect `8,8–1912,1020`; taskbar không che
  bounds. Không dùng manual save và không cần explicit display routing cho case này.

### E3-006D — Multi-app sequencing and result mapping

Status: implemented, not connected to Workspace Library.

- `AndroidWorkspaceLauncher` sorts ascending by unique `order`, keeps duplicate identities, and
  delegates each target through the `SingleAppLauncher` seam implemented by
  `AndroidSingleAppLauncher`.
- `LaunchSequencingPolicy.delayBetweenTargetsMs` accepts `0..5000`; 400 ms is a compatibility
  default, not a claimed optimum. Run a later 0/100/250/400 ms device sweep.
- Suspending delay occurs only between targets. There is no initial, final, or post-stop delay.
- Per-app failures continue. `DISPLAY_UNAVAILABLE` stops immediately and marks every pending
  target with the same typed reason without another platform call.
- `ensureActive()` runs before each target and after each launch. Cancellation from launch or
  delay is rethrown and never produces a synthetic partial result.
- Aggregation maps all success to `Success`, mixed outcomes to `PartialSuccess`, and zero success
  to `Failure`, preserving deterministic target order in both result lists.
- JVM coverage includes 20 scenarios for ordering/delay, all continue reasons, partial/all-fail,
  display stop/propagation, duplicate identity, cancellation points, order, and policy bounds.

After E3-006E integration, run a multi-app matrix on Note8 Hades v3/Note9 ROM and S23 Ultra,
including duplicate targets, mid-sequence display disconnect, delay sweep, and partial failure.

### E3-006E — Library “Mở” integration

Status: implemented; physical multi-app matrix remains to be executed.

- `TouchNavigation` builds the production Activity-scoped graph from the current `MainActivity`.
- `WorkspaceLaunchViewModel` performs typed readiness, enforces one global sequence, retains UI
  state and the non-Activity legacy reference store across configuration changes.
- The Activity runtime is passed only to an invocation and is never stored in ViewModel state.
  Root disposal cancels an active old-host sequence deliberately.
- Home no longer uses the placeholder Snackbar. It shows checking/launching progress, supports
  explicit cancellation, and maps success/partial/failure without raw platform exceptions.
- API 28–29 with no direct/trusted work area shows the fullscreen-bootstrap guidance. API 30+
  receives the normal unavailable message instead.
- All workspace Open buttons are disabled while the single global sequence is active.

Still required on devices: the S23 Ultra and Note8 Hades v3 matrix listed in the task, including
2/3/4 apps, windowed/fullscreen, taskbar, removed app, display disconnect, process restart without
legacy reference, and final-bounds/work-area-source logging.
