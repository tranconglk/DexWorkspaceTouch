# Architecture Decisions

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
