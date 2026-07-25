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

## About and diagnostic copy

```text
BuildConfig + Android platform metadata + established format-version constants
→ AppDiagnosticInfo
→ About dialog
→ plain-text clipboard only after an explicit user action
```

`AppDiagnosticInfo` is a presentation model and contains only app/build versions, non-unique
manufacturer/model data, SDK level, and the current display ID/mode. Android `Build`, `Display`,
`BuildConfig`, and `ClipboardManager` stay at the platform/UI boundary. The formatter is pure Kotlin
and cannot access workspace content, installed-app lists, files, logs, identifiers, or Room paths.
An external display is described as “External display”; it is not asserted to be Samsung DeX.

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
- Library dùng cùng loader application-scoped với App Picker. Luồng tải là: visible Library card
  → visible `WorkspaceSnapshot` cell → LRU icon cache → render. Repository, Room Flow và Library
  ViewModel không preload hoặc chứa icon.
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

## Workspace Library multi-select

Multi-select is presentation state owned by the graph-scoped `WorkspaceLibraryViewModel`. A long press enters the mode and stores an immutable set of workspace IDs; search and sort only project the visible list and never rewrite that set. Room remains the source of truth. Batch pin/unpin and delete flow through repository APIs to DAO transactions. Delete validates every requested ID before committing, so a missing row cannot produce a partial deletion. External file intents wait until multi-select exits, preventing transfer UI from replacing an active batch operation.

Selected export follows `selectedWorkspaceIds → immutable ID snapshot → repository snapshot → existing WorkspaceLibraryBundleSerializer → FileProvider or SAF`. Hidden-but-selected items remain part of the ID snapshot. Missing, corrupted, or unsupported selected IDs produce the same explicit continue/cancel warning policy as full-Library backup. The transfer ViewModel serializes only valid current repository rows and never reads stale card models.

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

### Library scale baseline

- The supported planning target is 500 workspaces, each containing one to four cells. Room currently
  reads and sorts full rows, including `canvasJson`, and the repository decodes every row before its
  first snapshot emission. LazyVerticalGrid still composes only visible cards using stable String IDs;
  snapshots receive decoded canvases and do no JSON or icon loading in composition.
- On the Note 8 legacy DeX target, the measured 500-row debug baseline was: database open 42 ms,
  raw first emission 41 ms, decode 139 ms, repository emission 71 ms, render-ready 1,242 ms,
  persisted row lookup for edit 3 ms, and approximately 51.3 MB heap after a full automated scroll.
  The 500-row run completed without OOM or crash.
- On the S23 Ultra DeX target at 1920x1200 and 160 dpi (six adaptive columns), the measured 500-row
  debug baseline was: database open 32 ms, raw first emission 13 ms, decode 142 ms, repository
  emission 94 ms, render-ready 898 ms, persisted row lookup for edit 1 ms, and approximately 141.4 MB
  heap after the automated full scroll. The run completed without OOM, crash, or state reset. The
  higher post-scroll debug heap is monitored as a scale guardrail; it does not currently justify a
  production cache or paging layer, both of which would add retention and complexity.
- These measurements do not justify a version-2 index, projection, cache, or paging dependency.
  Full-row decoding is the largest persistence-side scaling cost but remains below the UI render and
  scroll cost at the current target. Any schema optimization requires a new measured regression and a
  separately approved migration.
- Benchmark data, its separate database, Activity, and logging exist only in the debug source set.
  No production database is seeded. Device measurements, particularly on legacy DeX, remain the final
  acceptance criterion rather than strict timing assertions in JVM tests.

## Golden regression architecture

- Golden regression uses three layers: deterministic JVM workflow tests, file-backed Room Android
  integration tests, and a physical DeX checklist. Focused unit tests remain in place for diagnosis.
- JVM fixtures contain only stable fake identities and immutable canvases; reusable fakes live only in
  test source. No JVM workflow calls PackageManager, real time, UUID, delay, or Android launch APIs.
- Room close/reopen behavior is verified with a disposable file database rather than relying only on
  in-memory tests. S23 Ultra and Note 8 remain mandatory for window/taskbar and actual launch behavior.

## Template layer

```text
WorkspaceTemplateCatalog
→ WorkspaceTemplate.factory()
→ WorkspaceCanvasEditor
→ immutable WorkspaceCanvas draft
→ Designer
→ Save
→ Room
```

- Templates are pure Kotlin domain definitions containing stable metadata and a deterministic canvas
  factory. They contain no Compose, Android, Room, JSON, bitmap, or installed-app dependency.
- The default catalog is immutable and constructed explicitly at the Home composition boundary; it is
  not a singleton and templates are not persisted.
- Home previews each temporary canvas through the existing `WorkspaceSnapshot`. Selecting a card
  creates only a Designer working draft; Room remains unchanged until the existing Save action.
- All layouts are derived through `WorkspaceCanvasEditor` splits so bounds validation and deterministic
  IDs stay shared with Designer domain behavior.
## Workspace limit and template selection

`WorkspaceLimits.MaxCells = 5` là contract Kotlin dùng chung cho editor, template
catalog, Designer UI và launch readiness. Dữ liệu cũ trên 5 cell vẫn được deserialize
và giữ nguyên; readiness từ chối launch thay vì coi row là corrupted.

Template Picker là centered adaptive dialog, không phải bottom sheet. Dialog dùng
safe-drawing inset, explicit outer margin và helper thuần Kotlin để clamp kích thước
theo safe constraints. Header cố định; một `LazyColumn` duy nhất cuộn ba category,
mỗi section có header mở/thu gọn và grid hàng không-scroll 3/2/1 cột.

Samsung DeX windowed có thể đo custom dialog window theo desktop thay vì host. Vì vậy
host dưới large breakpoint dùng platform dialog width; host lớn/maximized dùng custom
width. Grid luôn quyết định cột từ constraints thực bên trong dialog.

Catalog mặc định được tinh gọn còn 16 topology canonical có giá trị sử dụng cao: 6 Cơ bản,
6 Trái/Phải và 4 Trên/Dưới. `canonicalTemplateSignature()` bỏ qua ID/order và so bounds theo tolerance;
catalog từ chối duplicate nhưng giữ mirror có hướng sử dụng khác. Quick Split đã bị
loại bỏ; Designer chỉ còn split ngang/dọc với một history entry cho mỗi thao tác.

## Workspace duplication

Duplicate là operation của `WorkspaceLibraryViewModel`, không thuộc DAO. Luồng dữ liệu:
workspace source được đọc lại từ repository → `WorkspaceDuplicateNamePolicy` chọn tên
duy nhất → tạo `Workspace` domain với ID/time/sequence mới → `WorkspaceRepository.insert`
→ Room Flow phát danh sách mới. Canvas immutable và app assignments được giữ nguyên;
Entity không được copy trực tiếp và schema Room không thay đổi.

## Workspace Library search and sort

Room Flow phát source list đã decode một lần → `WorkspaceLibraryViewModel` giữ source
immutable → projection lọc theo tên → comparator sort deterministic → Home render
`visibleWorkspaces`. Search/sort không query lại Room, không mutate source và không tham gia
write mutex. Selection và Lazy grid key tiếp tục dùng workspace ID, không dùng index.

## Persistent workspace pin

Room schema v2 adds `workspaces.isPinned INTEGER NOT NULL DEFAULT 0`. `MIGRATION_1_2`
preserves every v1 row and makes it unpinned by default. Pin writes use a dedicated DAO
field update, then the existing Room Flow refreshes the domain and presentation models.
Library projection filters once, partitions pinned and regular workspaces, and applies the
selected deterministic sort independently inside each section. Pin does not alter canvas,
identity, timestamps, or modified sequence; duplicate always starts unpinned.

## Designer cell activation

`WorkspaceCellView` tap → `LayoutDesignerScreen` → `WorkspaceDesignerViewModel.activateCell`
selects the cell and clears divider selection → one-shot navigation callback opens App Picker →
assignment updates the shared working draft. The ViewModel guard ignores additional activation
until App Picker closes, so recomposition and resize cannot replay navigation. Room is written
only by the existing explicit Designer Save workflow.

Selection state (`selectedCellId` / `selectedDividerId`) → pure
`DesignerContextToolbarState` mapping → `DesignerContextToolbar` → existing ViewModel callbacks.
The canvas owns hit testing and drag feedback only; structural commands no longer live inside
cell/divider overlays. Toolbar resize buttons still call the existing 5%/50% divider APIs and
therefore retain the current immutable history behavior.
### Rectangular cell merge

Cell removal is represented as a rectangular merge rather than deletion. The flow is:

```text
Selected cell
→ WorkspaceCanvasEditor.findMergeCandidates
→ directional target picker
→ optional assignment-loss confirmation
→ atomic mergeCells
→ one immutable history entry
```

Only cells sharing a complete edge can merge. The target ID survives, dividers are re-derived from
the resulting bounds, and no UI, Android, persistence, or launch type enters the domain operation.
## Workspace transfer boundary

```text
Workspace → versioned WorkspaceTransferSerializer → FileProvider/Sharesheet or SAF
          → bounded content validation → import preview → new Workspace ID → Room
```

The transfer envelope reuses the deterministic canvas serializer. Android `Uri`, streams and
`ContentResolver` remain at the platform boundary and never enter the transfer domain model.

### External transfer entry

```text
ACTION_VIEW / ACTION_SEND -> external intent parser -> Activity URI reader
                          -> bounded envelope detector -> Activity-scoped pending event
                          -> existing transfer state -> existing preview
                          -> confirmed import/restore
```

`MainActivity` handles both cold-start and `onNewIntent`. The URI is read once, its stream is
closed immediately, and only bounded bytes plus a typed detection result cross into state. MIME,
extension, and display name are diagnostics only; the envelope selects the existing `.dwt` or
`.dwtbundle` workflow. While Designer or App Picker is visible the event remains pending, so the
working draft is not replaced or navigated away from. It is dispatched when Library becomes
visible. An Activity-scoped identity guard ensures one accepted ACTION_VIEW event creates one
preview across recomposition and resize.

ACTION_SEND is adapted at the Android boundary: one `EXTRA_STREAM` URI is preferred, ClipData is
the fallback, identical values are deduplicated, and ambiguous/multiple values are rejected. The
pure parser sees only action and URI strings; Android `Intent`, `ClipData`, `Uri`, and
`ContentResolver` remain outside domain and transfer formats.
- Library backup pipeline: latest repository snapshot → deterministic `.dwtbundle` serializer →
  FileProvider/SAF → restore preview → batch name/ID policy → atomic Room transaction.
- Bundle restore validates the complete payload before writing and always creates new local rows.
