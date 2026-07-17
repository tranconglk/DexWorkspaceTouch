# Architecture

## Nguyên tắc phân lớp

### Domain

- Model immutable.
- Thuần Kotlin.
- Không Android Context.
- Không Compose.
- Không Modifier, Dp hoặc pixel.
- Bounds chuẩn hóa từ 0f đến 1f.

### Presentation

- Jetpack Compose.
- Render state và phát callback.
- Không truy cập Room hoặc PackageManager trực tiếp.
- Không chứa launch logic.
- Mỗi cell là một Composable độc lập.

### Infrastructure

- Navigation.
- PackageManager.
- Persistence.
- Samsung DeX display integration.
- Launch engine.

## Ranh giới chính

Workspace Designer không biết cách mở ứng dụng.

Launch engine không biết giao diện Designer.

## Cấu trúc dự kiến

```text
com.trancong.dexworkspacetouch/
├── navigation/
├── workspace/
│   ├── library/
│   ├── designer/
│   ├── apppicker/
│   └── launcher/
├── platform/
│   ├── dex/
│   └── applaunch/
├── data/
└── ui/
    ├── design/
    ├── components/
    └── theme/
```

Không tạo package `util`, `helpers`, `common` hoặc `misc` chung chung.

## Application Model

- `InstalledApp` mô tả metadata của ứng dụng hiện có trên thiết bị: identity,
  label và khả năng launch. Model này thuần Kotlin và không chứa icon/Android type.
- `AppIdentity` gồm package name và launcher activity nullable. Package name là
  định danh tối thiểu; activity phân biệt nhiều entry point trong cùng package.
- `AssignedApp` là identity được gán vào workspace. Trong giai đoạn migration
  E3-001, label vẫn được giữ tạm để UI hiện tại không mất khả năng render;
  E3-003 sẽ resolve presentation metadata từ catalog và loại bỏ label này.
- `InstalledAppDataSource` và `InstalledAppCatalog` là contract đồng bộ, thuần
  Kotlin. `DefaultInstalledAppCatalog` lọc launcher app, loại identity trùng và
  sắp xếp deterministic.
- `PackageManagerAdapter` và `AndroidInstalledAppDataSource` thuộc
  Infrastructure. Chỉ Infrastructure biết Android `PackageManager`; UI nhận
  state từ `AppPickerViewModel`.
- `PackageManagerAppIconLoader` tải icon theo `AppIdentity` trên IO dispatcher.
  Kết quả được giữ trong LRU cache RAM giới hạn 96 entry; domain không chứa
  `Drawable`, `Bitmap` hoặc Compose image type.
- UI label/icon phải được resolve từ catalog; domain không tự truy cập catalog
  hoặc `PackageManager`.

## Launch Bounds Pipeline

```text
Foreground Activity
→ ActivityDisplayWorkAreaProvider
→ DisplayWorkAreaSnapshot
→ DisplayWorkArea

NormalizedBounds + DisplayWorkArea
→ LaunchBoundsCalculator
→ PixelBounds
→ Android Rect mapper
→ Rect
```

- `DisplayWorkArea` là snapshot lấy từ foreground launch host ngay trước launch sequence.
  Model giữ kích thước display và cả bốn inset left/top/right/bottom; không biết
  `DisplayMetrics` hoặc `WindowInsets`.
- `LaunchBoundsCalculator` thuần Kotlin. Calculator dùng usable area sau inset,
  `roundToInt`, margin pixel hướng vào trong và clamp hoàn toàn trong usable rectangle.
- Margin launch là platform config, không phải UI spacing. Android adapter đổi 8dp mặc định
  sang px theo density của host/display; calculator chỉ nhận pixel.
- Không hard-code chiều cao taskbar, model thiết bị hoặc vị trí taskbar.
- Chỉ Android Rect mapper ở infrastructure được biết `android.graphics.Rect`.
- Provider mô tả usable area của toàn external display, không phải host Activity window.
  Trên API 30+, maximum WindowMetrics cung cấp display bounds còn current WindowMetrics chỉ
  dùng để phân biệt diagnostics windowed/maximized. Trên API 28–29, host display metrics
  được kết hợp với root WindowInsets đã sẵn sàng.
- Trên API 28–29, Activity-scoped `LegacyDisplayWorkAreaReferenceStore` tự ghi direct trusted
  snapshot và có thể phục hồi cùng work area khi freeform host không trả display-space inset.
  Reference key gồm display ID, real dimensions và density; không giữ Activity, không singleton,
  không lưu disk. Direct luôn thắng fallback, và API 30+ không dùng store.
- Nếu process khởi động lần đầu ở windowed mà chưa có direct trusted snapshot, provider trả
  unavailable. Presentation cần hướng dẫn người dùng phóng to ứng dụng một lần để bootstrap;
  infrastructure không bịa work area hoặc tự ép fullscreen.
- Insets dùng system bars và display cutout, gộp theo từng cạnh; IME không thuộc launch work
  area. Không có insets hoặc Activity/display không hợp lệ thì provider trả unavailable.
- Display ID và density đến từ foreground Activity/display. Display ID chỉ thuộc platform
  snapshot, không đi vào `WorkspaceLaunchRequest`.

## Single-target Android Launch

```text
ForegroundLaunchHost
→ ActivitySingleAppLaunchPlatform
→ fresh DisplayWorkAreaSnapshot
→ PackageManagerAdapter exact component verification
→ AndroidSingleAppLauncher
→ LaunchBoundsCalculator
→ PixelBounds.toAndroidRect
→ ActivityOptions.setLaunchBounds
→ foreground Activity.startActivity
```

- Host được scope theo Activity do caller cung cấp; không Application cache, singleton hoặc
  ViewModel Activity reference.
- Intent là explicit launcher intent với `NEW_TASK | MULTIPLE_TASK`. Không extra, animation
  tùy chỉnh hoặc `setLaunchDisplayId` trong production path.
- Platform kiểm tra lại display ID ngay trước start. Nếu host/display thay đổi thì trả
  `DISPLAY_UNAVAILABLE`, không tự chọn display khác.
- Start chạy trên Main dispatcher. `CancellationException` luôn được truyền tiếp.
- `AndroidSingleAppLauncher` chỉ xử lý một target. Sequencing, delay, duplicate coordination
  và workspace result aggregation thuộc pha sau.

## Multi-target Android Launch

```text
WorkspaceLaunchRequest
→ AndroidWorkspaceLauncher
→ AndroidSingleAppLauncher
→ WorkspaceLaunchResult
```

- `AndroidWorkspaceLauncher` sorts by target `order`, launches sequentially, uses a suspending
  delay only between targets, and aggregates typed success/partial/failure results.
- Per-app failures continue the sequence. `DISPLAY_UNAVAILABLE` stops it and marks every pending
  target unavailable without invoking the platform again.
- The coordinator stays in the caller context and never changes dispatcher. Cancellation from a
  launch or delay is rethrown, so no synthetic aggregate result is returned.
- Duplicate identities remain separate targets and preserve their individual ordered results.

## Phần đóng băng

Chưa port trong giai đoạn Designer MVP:

- Launch engine.
- DeX display provider.
- Shortcut.
- Room.
- Backup/import/export.

## Launch Contract

Luồng chuẩn bị và thực thi launch được tách thành các bước:

```text
WorkspaceCanvas
→ WorkspaceLaunchRequestFactory
→ LaunchReadiness
→ WorkspaceLauncher
→ WorkspaceLaunchResult
```

- `WorkspaceLaunchRequestFactory` validate canvas, kiểm tra cell trống và resolve
  `AssignedApp` qua `InstalledAppCatalog`. Factory không gọi `PackageManager`.
- `AppLaunchTarget` chỉ giữ `AppIdentity`, thứ tự cell deterministic và bounds chuẩn hóa
  `0f..1f`. Label và icon không thuộc launch request.
- Việc chuyển normalized bounds sang pixel thuộc Android launch implementation sau này.
- UI không tạo `Intent`; domain không biết `Context`, Android `Rect`, display ID hoặc
  Samsung DeX API.
- Designer hiện cho phép cùng một ứng dụng ở nhiều cell. Request giữ nguyên mọi target;
  platform implementation sau này phải báo kết quả rõ ràng nếu ứng dụng không hỗ trợ
  nhiều instance.

## Workspace Library Launch Integration

```text
WorkspaceLibraryCard
→ WorkspaceLaunchViewModel
→ WorkspaceLaunchRequestFactory
→ AndroidWorkspaceLaunchRuntime (Activity-scoped)
→ AndroidWorkspaceLauncher
→ WorkspaceLaunchUiState
```

- Navigation root owns the production graph. The ViewModel owns only typed UI state, its coroutine,
  and the pure-RAM legacy reference store; it never owns Activity, Intent, Rect, or Android errors.
- The installed-app catalog is shared by readiness and App Picker from application context.
- Activity recreation creates a new runtime. Disposal cancels work using the old host deliberately;
  recomposition alone neither recreates nor restarts a sequence.
- Readiness and environment validation happen before the first target. A phone/default-display host
  is rejected rather than used as fallback.

## Workspace Persistence Foundation

```text
WorkspaceLibraryViewModel (Room Flow source of truth)
→ WorkspaceRepository
→ RoomWorkspaceRepository
→ WorkspaceDao
→ Room / workspaces
```

- M5-001B wires the Library runtime to Room. `WorkspaceLibraryViewModel` observes repository Flow;
  UI receives only `WorkspaceLibraryItem` and loading/friendly persistence-error state.
- Pure Kotlin `Workspace` is separate from `WorkspaceEntity`; Room types never cross the repository.
- A workspace is one database row. `WorkspaceCanvas` uses deterministic JSON in `canvasJson`, while
  identity, timestamps, schema version, and modified sequence remain normal columns.
- `WorkspaceCanvasSerializer` is reusable by later import/export work, but M5-001A adds no transfer
  feature. Malformed data and unsupported schemas fail with typed persistence exceptions.
- New/edit Designer canvases remain immutable working copies. Create does not insert until Save;
  Back/cancel does not update Room. Migration from the former RAM workflow is deferred to M5-001C.
- The application-scoped database graph uses application context and owns no UI/Activity reference.
- Repository observation maps each Room row independently into a `WorkspaceRepositorySnapshot`.
  Malformed JSON and unsupported future schemas become typed per-row issues; valid rows remain
  available and unreadable rows are neither deleted nor replaced with empty canvases.
- Schema version 1 is the only decodable version. Future-version rows may be deleted by ID, but are
  excluded from edit and launch so unknown data cannot be overwritten.
- Insert/update/delete are single-row Room statements. Updates require exactly one affected row;
  serialization completes before the DAO call, and Library state changes after the committed Room
  emission. Failed writes retain the previous persisted row.
- JSON decoding rejects unknown or duplicate fields, trailing input, non-finite numbers, payloads
  over 1,000,000 characters, and nesting deeper than 64 containers.
