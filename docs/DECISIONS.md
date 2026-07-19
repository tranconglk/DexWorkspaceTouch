# Architecture Decisions

## ADR-036 — Beta release remains unminified and requires explicit signing intent

The first beta keeps R8 minification and resource shrinking disabled because the full Compose, Room, transfer, and DeX behavior has not yet been qualified under shrinking. Release remains non-debuggable. Signing secrets are read only from ignored local properties or environment variables. Unsigned release builds may be produced for QA, while `DWT_REQUIRE_RELEASE_SIGNING=true` makes a missing or partial signing configuration a hard failure.

## ADR-035 — Android automatic backup is disabled for the beta

The Room database, app files, shared preferences, and external app data are excluded from Android cloud backup and device-to-device transfer. Users already have the validated `.dwtbundle` manual backup path. Disabling automatic restore avoids silently moving database state across ROMs or versions without bundle validation. Cache exports and temporary transfer files are also excluded and remain cleanup-managed.

`.dwt` and `.dwtbundle` envelopes, MIME constants, and format version 1 are frozen for the beta.

## ADR-034 — Selected export reuses the Library bundle

Exporting selected workspaces uses the existing `.dwtbundle` envelope, MIME type, canonical serializer, validation limits, FileProvider sharing, SAF writing, and restore path. No selection or pin metadata is added to the file. Selection order, visible card order, current sort mode, and pinned sections do not influence canonical payload order. Workspaces hidden by search remain exported when their IDs are selected.

The export operation captures an immutable ID snapshot and rereads the repository. Missing or unreadable selected rows require explicit continuation when valid rows remain; if none remain, no file is created. Success and failure both preserve Library multi-selection. Restored rows continue to receive new IDs and default to unpinned.

## ADR-033 — Library multi-select uses ID state and atomic persistence operations

Workspace Library multi-select is held as an immutable `Set<String>` of persistent workspace IDs in the graph-scoped ViewModel. Visibility changes from search, sort, pin sections, or resize do not discard valid selections. Batch pin/unpin updates only rows whose value changes. Batch deletion is a single Room transaction that validates the affected row count and rolls back on mismatch. Selection is cleared only after successful deletion and is preserved on failure so the user can retry.

The card long-press gesture enters selection mode; subsequent taps toggle selection. Normal open/edit/pin actions are unavailable while the mode is active. This avoids ambiguous touch behavior and keeps database entities and the Room schema unchanged.

## ADR-019 — Workspace templates are non-persistent domain objects

- A template is immutable metadata plus a pure factory for `WorkspaceCanvas`.
- Factories compose existing `WorkspaceCanvasEditor` split operations rather than storing JSON or
  duplicating normalized-bounds algorithms.
- The catalog is explicitly constructed and immutable; no global singleton, reflection, Room row,
  download, or custom-template persistence is introduced.
- Template selection creates a Designer draft. Only the normal Save workflow writes a workspace to
  Room, keeping templates independent from user data and schema migrations.

## ADR-018 — Golden regression suite uses three layers

- JVM workflow tests are authoritative for deterministic application logic and use shared fixtures
  and fakes from test source only.
- File-backed Android instrumentation is authoritative for Room/SQLite close-reopen behavior.
- Physical S23 Ultra and Note 8 legacy-ROM runs are authoritative for DeX display, taskbar, windowing,
  process restart, and real application launch; emulator coverage cannot replace them.
- Compose screenshot infrastructure is not introduced without a stable cross-density baseline.
- The short manual checklist explicitly records gaps that cannot be claimed as automated end-to-end.

## ADR-017 — Workspace Library scaling policy

- The current scale target is 500 workspaces with one to five cells each.
- Optimization follows measured need: remove unnecessary decoding/work first, then allocation and
  recomposition, then consider an indexed projection/cache; paging is not added while 500 items remain
  usable on the legacy Note 8 DeX target.
- The version-1 query intentionally remains unchanged. Adding its multi-column sort index or a summary
  projection would require a database migration and is not justified by the current device baseline.
- Deterministic debug data and a separate debug database provide repeatable 100/250/500 measurements
  without seeding or mutating the production Library database.
- Device measurements are authoritative. JVM performance tests verify scale correctness but avoid
  brittle absolute-time thresholds.

## ADR-016 — Persistence isolates unreadable rows

Decision:

- Every Room row is decoded independently. One malformed row never hides other valid workspaces.
- Corrupted JSON and unsupported future schemas are typed persistence issues. Raw JSON, SQLite
  errors, and stack traces never cross the repository/UI boundary.
- Unreadable rows remain unchanged. The app does not delete them, replace them with empty canvases,
  or overwrite future-schema data it cannot understand.
- Future-schema workspaces are unavailable for edit and launch. Deletion by stable row ID remains a
  repository operation for a future explicit recovery UI.
- Room remains the source of truth; UI lists change only after a committed Flow emission.

Consequences:
Valid workspaces remain usable during partial corruption. Recovery/export tooling is outside this
task, and database version remains 1 without destructive migration or reset.

Default names use `max(N) + 1` over persisted `Workspace N` names plus numbers issued in the current
process. This is deterministic across restart while rows exist. Without a settings table or DataStore,
a highest-numbered name deleted before process death can be reused after restart; this limitation is
accepted to avoid persistence outside the workspace table.

## ADR-001 — Dùng Normalized Bounds

Quyết định:
Dùng tọa độ Float trong khoảng 0f..1f.

Lý do:  
Độc lập với độ phân giải và kích thước cửa sổ.

## ADR-002 — Cell là Composable độc lập

Quyết định:  
Dùng Box/Layout với cell composable thay vì vẽ toàn bộ bằng Canvas.

Lý do:  
Dễ hỗ trợ touch, semantics, animation và trạng thái riêng.

## ADR-003 — Chưa port Launch Engine

Quyết định:  
Hoàn thiện Visual Designer MVP trước khi tích hợp lõi DeX.

Lý do:  
Giảm regression và giữ UI độc lập với platform.

## ADR-004 — Project cũ là reference implementation

DexWorkspaceManager chỉ dùng để tham khảo phần đã được kiểm chứng.

Không phát triển tính năng UX mới ở project cũ.

## ADR-005 — Không quản lý session

Ứng dụng chỉ thiết kế, lưu và mở workspace.

Không theo dõi hoặc quản lý phiên làm việc sau khi launch.

## ADR-006 — Touch-first

Không thiết kế hành động chính phụ thuộc chuột, bàn phím, hover hoặc keyboard shortcut.

## ADR-007 — Không lưu metadata có thể tra cứu lại

Quyết định:
Workspace chỉ lưu identity ứng dụng cần thiết. Label, icon và launchable state
được resolve từ installed-app catalog khi render hoặc thực thi.

Lý do:

- Label có thể thay đổi theo locale.
- Icon có thể thay đổi sau khi ứng dụng update.
- Tránh hai nguồn metadata và dữ liệu trùng lặp.
- Giúp persistence và export sau này gọn hơn.

Migration:
E3-001 giữ `AssignedApp.label` như cầu nối presentation tạm thời để không phá
Designer/Snapshot demo. E3-003 sẽ chuyển UI sang catalog resolution rồi thu gọn
`AssignedApp` còn identity.

## ADR-008 — Launch contract độc lập Android

Quyết định:
Domain mô tả mục tiêu launch bằng `AppIdentity`, normalized bounds và thứ tự cell.
Readiness và kết quả launch là các contract typed, không chứa Android type hoặc exception.

Lý do:

- Có thể kiểm thử hoàn toàn bằng JVM.
- Giữ Launch Engine tách khỏi Workspace Designer và UI.
- Một request áp dụng được cho nhiều kích thước và độ phân giải cửa sổ.
- Android implementation có thể thay đổi mà không làm thay đổi canvas domain.

Hệ quả:
Chuyển bounds sang pixel, chọn display và tạo `Intent` thuộc infrastructure. Cùng một
identity được phép xuất hiện ở nhiều target vì Designer hiện không cấm; Android
implementation sau này có trách nhiệm báo hạn chế về nhiều instance, không được âm thầm
bỏ target trùng.

## ADR-009 — Launch bounds dùng work area có bốn inset

Quyết định:
Launch bounds được tính từ snapshot work area gồm kích thước display và bốn inset
left/top/right/bottom. Calculator thuần Kotlin chuyển `NormalizedBounds` thành pixel bằng
usable rectangle, rounding deterministic và margin platform hướng vào trong.

Lý do:

- DeX taskbar và system bars có thể thay đổi kích thước hoặc cạnh hiển thị.
- Windowed và maximized có thể báo vùng làm việc khác nhau.
- Tránh cửa sổ ứng dụng bị taskbar/system UI che.
- Không phụ thuộc model thiết bị hay magic taskbar height.

Hệ quả:
Foreground launch host phải cung cấp snapshot inset mới trước launch sequence. Calculator
trả typed failure nếu margin hoặc rounding làm vùng không còn kích thước dương. Việc đọc
Android `WindowInsets` và map `PixelBounds` sang `Rect` thuộc infrastructure, không thuộc
canvas hoặc launch contract.

## ADR-010 — Work area lấy từ foreground external-display host

Quyết định:
Android work-area provider nhận foreground Activity đang gắn với external display và đọc lại
display, WindowMetrics, WindowInsets cùng density mỗi lần tạo snapshot. Provider không tự tìm
hoặc chuyển sang display khác.

Lý do:

- Dialog, diagnostics và launch sau này dùng cùng display host.
- Tránh Application Context không đại diện đúng external-display resources/insets.
- Ownership Activity và lifecycle rõ ràng, không cần singleton cache.
- Insets/display có thể thay đổi giữa hai lần launch nên không cache snapshot.
- Maximum WindowMetrics tránh giới hạn workspace theo host window khi Activity đang windowed.

Hệ quả:
Activity chưa attached, đang kết thúc, display default/OFF/removed hoặc insets chưa sẵn sàng
đều trả unavailable thay vì đoán. API 28–29 vẫn cần device verification vì public API không có
maximum WindowMetrics. `DisplayWorkAreaSnapshot.displayId` chỉ thuộc platform adapter và không
được đưa vào domain launch request.

## ADR-012 — Launch tuần tự và partial success

Quyết định:

- Launch target tuần tự theo `AppLaunchTarget.order`; identity trùng không bị loại.
- Delay giữa target được cấu hình qua `LaunchSequencingPolicy`, mặc định 400 ms.
- Lỗi riêng của app tiếp tục sequence để cho phép partial success.
- `DISPLAY_UNAVAILABLE` dừng sequence; target hiện tại và mọi target còn lại đều
  nhận typed failure này.
- `CancellationException` luôn rethrow; không map sang `UNKNOWN` hoặc trả partial result.

Lý do:
Launch tuần tự giữ hành vi tương thích với implementation đã kiểm chứng và cho
phép báo cáo chính xác từng target. Display loss là failure cấp host nên không
còn cơ sở tin cậy để launch tiếp. Structured cancellation phải được bảo toàn.

Hệ quả:
400 ms chỉ là compatibility default, chưa phải giá trị tối ưu; cần device sweep sau
integration. Caller bị cancel sau khi một số target đã launch sẽ không nhận
`WorkspaceLaunchResult`; coroutine vẫn bị cancel đúng contract.

## ADR-011 — Legacy DeX work-area reference

Quyết định:
API 28–29 được phép tái sử dụng trusted full-display work area trong RAM khi freeform host trên
cùng external display không cung cấp display-space insets. Direct candidate luôn được ưu tiên;
reference chỉ là fallback.

Lý do:

- Samsung DeX cũ không luôn expose taskbar qua root insets trong freeform window.
- Work area của desktop không phụ thuộc kích thước host window.
- Tránh hard-code taskbar height, device model hoặc fallback rectangle.

Reference chỉ được auto-capture từ `ROOT_SYSTEM_INSETS`, `ROOT_STABLE_INSETS` hoặc
`DISPLAY_METRICS_DELTA` đã accepted. Key gồm display ID, real dimensions và density. Display
disconnect, ID/resolution đổi hoặc density đổi đáng kể làm reference bị xóa hoặc từ chối.

Giới hạn:

- Reference chỉ tồn tại trong RAM và không sống qua process death.
- Lần đầu process mở ở windowed có thể chưa có trusted candidate. Provider trả unavailable và UI
  hướng dẫn phóng to ứng dụng một lần; không tự ép fullscreen.
- API 30+ không capture hoặc resolve legacy reference.

## ADR-013 — Launch từ Workspace Library qua typed readiness/result

Quyết định:

- `WorkspaceLaunchRequestFactory` luôn chạy trước Android infrastructure launch.
- UI chỉ gửi `WorkspaceLibraryItem` và render typed state; UI không tạo `Intent` hoặc `Rect`.
- Toàn ứng dụng chỉ có một launch sequence tại một thời điểm.
- Activity-scoped runtime không được lưu trong ViewModel. Activity dispose sẽ cancel sequence
  đang dùng host cũ thay vì giữ Activity đã destroy.
- API 28–29 không có direct/trusted work area hiển thị hướng dẫn phóng to một lần;
  không force fullscreen và không dùng model thiết bị.
- Raw Android exception và technical message không đi ra UI.

Hệ quả:
Launch state và legacy reference RAM sống qua resize/configuration change trong
`WorkspaceLaunchViewModel`, nhưng launcher luôn được tạo lại từ foreground Activity. UI có
trạng thái checking, launching, completed, readiness error, environment error và cancelled rõ ràng.

## ADR-014 — Workspace persistence dùng Room với JSON canvas

Quyết định:

- Database version 1 có một bảng `workspaces`.
- `WorkspaceCanvas` được serialize deterministic vào cột `canvasJson`; không tách cell/app
  thành nhiều bảng trong version đầu.
- Domain `Workspace` không có Room/Android annotation và tách biệt với `WorkspaceEntity`.
- `WorkspaceRepository` là boundary; UI không nhận Entity, DAO hoặc Room exception.
- Serializer custom JSON thuần Kotlin giữ field order, cell order, app identity, nullable activity
  và label; schema không hỗ trợ hoặc JSON lỗi trả typed persistence failure.

Lý do:
Cấu trúc một bảng đơn giản cho version đầu, giảm migration complexity và không coupling
UI/domain với Room. Serializer có thể được tái sử dụng cho import/export sau này mà không
đưa Android JSON API vào domain.

Trade-off:
Không tối ưu cho query theo từng cell hoặc package app. Chỉ tách bảng/index khi có nhu cầu
query thật và migration được thiết kế rõ ràng.

## ADR-015 — Workspace Library dùng Room làm source of truth

Quyết định:

- `WorkspaceLibraryViewModel` observe `WorkspaceRepository.observeAll()`; Room Flow là runtime
  source of truth và ViewModel không tự sort hay duy trì bản sao danh sách khác.
- Draft tạo mới và immutable edit working copy chỉ ghi repository khi Save. Back/cancel không
  update database.
- UI chỉ biết `WorkspaceLibraryItem`, loading và persistence error thân thiện; UI không biết
  Room, DAO, Entity hoặc raw exception.
- Production database/repository được tạo một lần trong application-scoped container nhỏ
  bằng application context, không destructive migration.
- ID dùng UUID; default name dùng issued-number policy không dựa vào count. Timestamp dùng
  clock abstraction; update/rename giữ created time và tăng updated time/modified sequence.
- Không migrate RAM data cũ trong M5-001B; M5-001C sẽ quyết định migration/seed policy.

Hệ quả:
Library phục hồi sau process restart, launch luôn nhận canvas đã persisted, và persistence
failure không crash UI. Save là asynchronous; Room emission xác nhận danh sách hiển thị.
## ADR-020 — Workspace giới hạn tối đa 5 cell theo khả năng sử dụng DeX đã xác minh

Quyết định:

- `WorkspaceLimits.MaxCells = 5` được enforce ở domain editor, template catalog,
  Designer UI và launch readiness.
- Split ngang/dọc bị khóa trước mutation khi canvas đã có 5 cell; failure không tạo history.
- Catalog không hỗ trợ template 6 ô. Mẫu 5 ô dùng ba ô hàng trên và hai ô hàng dưới.
- Quick Split bị loại bỏ để giảm độ phức tạp và tránh preset vượt khả năng sử dụng DeX.
- Row cũ trên 5 cell vẫn được giữ và deserialize; launch readiness trả typed failure,
  không gọi Android launcher và không coi row là corrupted.
- Catalog chỉ dùng topology/tỷ lệ canonical, không liệt kê mọi tỷ lệ divider. Signature
  dựa trên cell count và bounds có tolerance, không dựa tên hoặc ID.
- Mirror trái/phải và trên/dưới là khác nhau khi hướng sử dụng khác; topology có bounds
  tương đương chỉ xuất hiện một lần. Catalog được tinh gọn còn 16 mẫu có giá trị sử dụng
  cao trong ba category, ưu tiên chọn nhanh thay vì liệt kê mọi topology có thể tạo.
- Template dialog dùng đúng 3 cột trên content width lớn, 2 cột ở mức trung bình và
  1 cột ở cửa sổ hẹp. Kích thước được clamp sau safe inset/margin để không bị cắt.
- Trên Samsung DeX windowed, host dưới large breakpoint dùng platform dialog width để
  tránh custom dialog đo theo desktop rồi bị clip bởi host; maximized vẫn dùng custom width.
- Cơ bản và Chia trái/phải mở mặc định; Chia trên/dưới thu gọn. Section header là touch
  target toàn hàng. Card chỉ hiển thị preview 16:10 và tên, không hiển thị mô tả phụ.

Lý do:
Giới hạn năm cửa sổ phản ánh phạm vi đã xác minh trên thiết bị, đồng thời đặt guard
cuối trước Android infrastructure mà không thay đổi Room schema hay launch sequencing.

## ADR-021 — Duplicate workspace tạo row độc lập

Quyết định:

- Duplicate tạo `Workspace` domain mới và gọi `WorkspaceRepository.insert`; không copy Entity.
- ID lấy từ `WorkspaceIdGenerator`; created/updated time và modified sequence đều là giá trị mới.
- Canvas, bounds, topology, app assignment và schemaVersion được giữ nguyên.
- Tên bản sao được chọn deterministic, trim và so sánh không phân biệt hoa thường:
  `(Bản sao)`, `(Bản sao 2)`, rồi số nhỏ nhất chưa dùng.
- Write mutex và active-mutation guard ngăn double tap hoặc write cạnh tranh.
- Thành công chọn bản sao để phản hồi rõ nhưng không tự mở Designer hoặc launch.
- Không thay đổi Room schema, serializer hay Launch Engine.

## ADR-022 — Search và sort là presentation projection

- Room/DAO tiếp tục là source of truth và không đổi query/schema.
- ViewModel tạo một projection search rồi sort từ source list đã observe; source không mutate.
- Search chỉ theo workspace name, trim và không phân biệt hoa thường.
- Năm sort mode có tie-break deterministic bằng metadata, name và ID.
- `searchQuery` và `sortMode` là preference trong phiên ViewModel, chưa persistence/DataStore.
- Selection và stable key dùng ID nên filter/sort không thay đổi identity.

## ADR-023 — Pin is persisted metadata with independent library sections

- Pin is a persisted `Workspace` property and a Room v2 column, not transient UI state.
- Migration v1→v2 uses `NOT NULL DEFAULT 0`; existing data is never destructively rebuilt.
- A dedicated DAO update changes only `isPinned`; pin/unpin does not change content metadata.
- Library search runs before partitioning. Pinned and regular sections are sorted independently.
- Duplicating a pinned workspace creates an unpinned copy; rename and Designer save preserve pin.
- Pin actions remain touch-first and never use an overflow menu.
- Pin is an always-visible 56dp corner action on every card and is removed from “Quản lý”.
- Outline/filled bookmark state replaces the redundant text badge; toggle requires no confirmation.
- Workspace ID remains the stable lazy key while a card moves between pinned and regular sections.
- Partially filled pinned rows are centered for visual balance. This is presentation-only: pinned
  card width follows the regular adaptive grid, while sorting, persistence, IDs, and traversal order
  remain unchanged. Regular workspace rows remain left-aligned.

## ADR-024 — Cell activation opens App Picker directly

- One cell tap selects the cell, clears divider selection, and emits one navigation action.
- A ViewModel-scoped pending guard ignores rapid taps until App Picker closes; selection alone
  never triggers navigation, so recomposition and configuration changes cannot replay it.
- Divider and contextual-action hit targets remain above cell content and consume their gestures.
- Choose/change-app buttons are removed from the structural overlay.
- App assignment changes only the working draft and history; persistence still occurs on Save.

## ADR-025 — Designer context toolbar replaces distributed action overlays

- A fixed adaptive toolbar above the canvas maps current selection to context actions.
- Cell selection exposes split/clear; divider selection exposes ±5%, reset 50%, ratio, and clear.
- Cell and divider action overlays are removed, leaving content unobstructed and reducing overlap.
- Wide layout uses one row; narrow layout prioritizes history, then context, then status rows.
- Toolbar commands delegate to existing ViewModel/state-holder contracts and do not change domain,
  drag/snap, history, Room, or persistence behavior.
## ADR-026 — Cell removal is represented by rectangular merge

Status: Accepted.

Removing a cell must never leave a gap. A selected source cell can therefore only be merged into a
target that shares its full edge and whose union is one rectangle. The target ID is retained. Its app
assignment wins; when the target is empty, the source assignment moves to it. Losing a source app when
both cells are assigned requires confirmation. A successful merge is atomic and creates exactly one
history entry; invalid or cancelled merges create none.
## ADR-027 — Workspace transfer uses a versioned `.dwt` envelope

Share is the primary export path and SAF “Save As” is secondary. Import always creates a new local
ID and never overwrites in version 1. Local IDs, pin state, timestamps and modification sequence are
not exported. FileProvider exposes only `cache/exports`; SAF and temporary URI grants avoid broad
storage permission. ACTION_VIEW/Open With is deferred until cross-provider device validation.

## ADR-028 — Workspace card uses a bounded 64-entry icon cache

Status: Accepted.

Library snapshot và App Picker dùng chung loader thuộc Application graph, không giữ Activity.
Cache RAM là LRU tối đa 64 identity, dùng stable String package/activity và cache cả fallback.
Chỉ cell đang compose trong viewport yêu cầu icon; icon không được lưu vào Room hoặc domain.
Target UX là tối đa 100 workspace. Workspace ghim giữ cùng card layout, không có hero/featured item.
## ADR-030 — Library backup uses all-or-nothing `.dwtbundle`

Status: Accepted.

Bundle v1 contains at most 100 workspaces and 500 cells in a 2 MiB UTF-8 deterministic envelope.
It excludes database IDs, timestamps, sequences and pin state. Restore creates new IDs, resolves
names deterministically, resets pin to false and inserts the complete batch in one Room transaction.
Existing rows are never overwritten. Pin remains a device-local organization preference.

## ADR-031 — External transfer files are always content-validated

Status: Accepted.

- MIME, extension, and provider display name are hints and never choose the final parser.
- ACTION_VIEW input is byte-limited, minimally envelope-sniffed, then passed to the existing strict
  `.dwt` importer or `.dwtbundle` restorer.
- No Room write occurs until the existing preview is confirmed; bundle restore remains atomic.
- One accepted external event produces at most one preview. Consumed events do not replay after
  recomposition, resize, or normal Activity recreation within the same Activity-scoped owner.
- A later explicit `onNewIntent` delivery may reopen the same URI after the prior inbox event was
  consumed. This distinguishes a new user action from `onCreate` replay during configuration change;
  an event already reading or pending still rejects rapid duplicate delivery.
- An open Designer draft is never discarded or force-navigated. External transfer waits until the
  user returns to Library.
- The implementation reuses both established transfer flows and does not introduce a second parser,
  import policy, restore policy, or persistence path.
- `application/octet-stream` is registered as a measured compatibility fallback: Downloads on the
  Note 8 legacy-ROM device reported a received `.dwt` content URI with that MIME. The URI path did
  not contain a usable filename, so an extension path filter could not safely replace content validation.
- Legacy Samsung My Files was also observed opening a Play Store search for `dwtbundle` without
  delivering ACTION_VIEW. Narrow data-only `content`/`file` path patterns for `.dwt` and
  `.dwtbundle` provide extension resolution when that file manager omits MIME entirely.

## ADR-032 — ACTION_SEND is the fallback for file managers that do not support custom extensions

Status: Accepted.

- Samsung My Files direct-open is not reliable: on both tested devices it can open
  `market://search?q=dwtbundle` without delivering the file to DexWorkspaceTouch.
- DexWorkspaceTouch does not intercept `market://`. The supported fallback is My Files **Share**
  to DexWorkspaceTouch through ACTION_SEND.
- Only one content URI is accepted. EXTRA_STREAM is preferred; ClipData is the fallback; matching
  values are one event and differing/multiple values are rejected.
- Reported MIME is a chooser hint only. The bounded envelope detector and existing strict parser
  still decide whether the file is `.dwt`, `.dwtbundle`, invalid, oversized, or too new.
- ACTION_VIEW and ACTION_SEND share the same pending, preview, confirmation, duplicate-event, and
  Designer-draft preservation pipeline.
