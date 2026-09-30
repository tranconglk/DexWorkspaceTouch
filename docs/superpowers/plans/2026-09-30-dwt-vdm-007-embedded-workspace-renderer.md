# DWT-VDM-007 Embedded Workspace Renderer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Chạy Workspace đã lưu trong renderer embedded thử nghiệm, ánh xạ bounds chuẩn hóa sang pane pixel theo viewport DeX, giữ Surface và touch đúng source ID khi resize.

**Architecture:** Mapper layout/touch Kotlin thuần quyết định hình học host. Host Compose giữ generation, SurfaceView và config geometry đã resolve một lần; controller/runner hiện có vẫn là nơi duy nhất tạo và dọn session. Route thử nghiệm nhận `workspaceId` từ Workspace được chọn trong library.

**Tech Stack:** Kotlin, Jetpack Compose, Android SurfaceView, JUnit4, kotlinx.coroutines test, Gradle Android plugin.

**Spec:** `docs/superpowers/specs/2026-09-30-dwt-vdm-007-embedded-workspace-renderer-design.md`

## Global Constraints

- Giữ `EmbeddedWorkspaceRunner`, preflight sequencing, timeout, cleanup và DWT-VDM-006 proof route; không đổi Classic, persistence/schema, license/security, production URLs hoặc launch mặc định.
- `NormalizedBounds` là cạnh `left,top,right,bottom` Float trong `[0,1]`; map cạnh qua `floor(e.toDouble()*N+0.5)` rồi clamp `[0,N]`; zero pixel, overlap dương và viewport không hợp lệ trả lỗi typed.
- Host buffer cố định theo guest `EmbeddedAppGeometry`; pane host dùng `FILL`, X/Y scale độc lập; touch clamp `[0,W-1]×[0,H-1]`.
- Một map geometry bất biến per generation cung cấp fixed buffer, touch và runner target. Generation token + plan snapshot bảo vệ layout/callback cũ.
- Không build/chạy thiết bị cho đến bước verification của lần triển khai. Giữ nguyên mọi thay đổi chưa commit nêu trong yêu cầu DWT-VDM-007; không stage hay commit chúng. Không push.

## Review Focus

- Cùng `sourceCellId` xuất hiện ở generation mới: callback Surface cũ không được xóa slot mới; Task 3 kiểm thử.
- Viewport tạm `0×0` trong ACTIVE: giữ pane và không gọi cleanup nếu Surface không mất; Task 3 kiểm thử.
- Viewport dương quá nhỏ: typed `ZeroPixelPane` và Stop đúng một lần; Task 3 kiểm thử.
- Geometry policy có trạng thái/thay đổi kết quả: một lần resolve, buffer/touch/runner cùng snapshot; Task 3 kiểm thử.
- Workspace đã chọn bị xóa trước khi route load: không chọn row khác, không Start; Task 5 kiểm thử.

---

## File map

- `workspace/execution/embedded/layout/EmbeddedWorkspaceLayoutModels.kt`: viewport, pixel rect, mapped/rejected result và typed causes; `Mapped` giữ full `EmbeddedWorkspacePlan` snapshot.
- `workspace/execution/embedded/layout/EmbeddedWorkspaceLayoutMapper.kt`: validation, overlap và edge mapping thuần.
- `workspace/execution/embedded/layout/EmbeddedPaneTouchMapper.kt`: host-local → guest coordinate thuần bằng kích thước Int, không import Android/Compose.
- `feature/embeddedworkspace/EmbeddedWorkspaceGeometrySnapshot.kt`: resolve policy một lần, map bất biến và lookup `EmbeddedGeometryPolicy` cho runner.
- `feature/embeddedworkspace/EmbeddedWorkspaceRendererCoordinator.kt`: generation token, plan/layout correlation, readiness, callback identity, transient viewport và Stop gate. Không tạo session.
- `feature/embeddedworkspace/EmbeddedWorkspaceRenderer.kt`: Compose đo viewport, đặt pane, SurfaceView callback và touch.
- `feature/embeddedworkspace/EmbeddedWorkspaceLayoutScreen.kt`: tải Workspace ID, request/planner thật, tạo coordinator/controller/runner và diagnostics.
- `navigation/TouchNavigation.kt`, `ui/screens/HomeScreen.kt`: route thử nghiệm mới từ Workspace đang chọn; giữ route runner proof cũ.
- Test cùng package dưới `app/src/test/java/com/trancong/dexworkspacetouch/` cho mapper, touch, geometry snapshot và coordinator/route selection.

### Task 1: Pure layout model và mapper

**Files:**
- Create: `app/src/main/java/com/trancong/dexworkspacetouch/workspace/execution/embedded/layout/EmbeddedWorkspaceLayoutModels.kt`
- Create: `app/src/main/java/com/trancong/dexworkspacetouch/workspace/execution/embedded/layout/EmbeddedWorkspaceLayoutMapper.kt`
- Test: `app/src/test/java/com/trancong/dexworkspacetouch/workspace/execution/embedded/layout/EmbeddedWorkspaceLayoutMapperTest.kt`

**Interfaces:** `EmbeddedWorkspaceLayoutMapper.map(plan: EmbeddedWorkspacePlan, viewport: EmbeddedWorkspaceViewport): EmbeddedWorkspaceLayoutResult`; `Mapped(planSnapshot: EmbeddedWorkspacePlan, viewport, panes)`; `MappedPane(sourceCellId, order, packageName, componentName, normalizedBounds, pixelBounds)`; typed causes trong spec.

- [ ] Viết failing JUnit tests cho 50/50, 25/25/50, 2×2, `1101×701` với `.5→551`, 5/8 với `1100→688` và `1400→875`; assert `0→0`, `1→N`, cạnh chung bằng nhau, ID/order/metadata giữ nguyên khi đổi viewport hoặc thứ tự đầu vào.
- [ ] Thêm tests viewport 0/âm, zero pixel pane, overlap dương và touching edge; assert cause typed và không có `Mapped`. Kiểm out-of-range/NaN và extent bằng 0 tại constructor `NormalizedBounds`, duplicate source ID tại constructor plan. Không dùng reflection/unsafe mutation để chế tạo bounds không thể tới mapper; giữ `InvalidNormalizedBounds` typed nếu có đường validation phòng vệ có ý nghĩa, không làm yếu constructor.
- [ ] Chạy `./gradlew :app:testDebugUnitTest --tests "*.EmbeddedWorkspaceLayoutMapperTest"`; xác nhận FAIL vì API chưa có.
- [ ] Tạo models và mapper đúng chữ ký; kiểm tra cận trước khi map, dùng `Double` cho phép nhân rồi `floor(...+0.5)`, kiểm overlap trong normalized geometry; không ép pane lên tối thiểu 1 px và không phụ thuộc Compose/Android.
- [ ] Chạy lại đúng test class tới PASS; xem scoped diff của ba file.

### Task 2: Pure pane touch mapper

**Files:**
- Create: `app/src/main/java/com/trancong/dexworkspacetouch/workspace/execution/embedded/layout/EmbeddedPaneTouchMapper.kt`
- Test: `app/src/test/java/com/trancong/dexworkspacetouch/workspace/execution/embedded/layout/EmbeddedPaneTouchMapperTest.kt`

**Interfaces:** `EmbeddedPaneTouchMapper.map(x: Float, y: Float, hostWidth: Int, hostHeight: Int, guestWidth: Int, guestHeight: Int): PaneTouchMappingResult` với `Mapped(x: Float,y: Float)` hoặc typed `Rejected`; không nhận viewport offset, `NormalizedBounds`, `MotionEvent` hay `EmbeddedAppGeometry`.

- [ ] Viết failing tests: `(0,0)→(0,0)`, `(300,150)` của host `600×300` sang guest `900×675` → `(450,337.5)`, `(600,300)→(899,674)`, tọa độ hơi ngoài biên, pane rất rộng/cao, host zero và NaN/Infinity; cùng local point cho hai viewport offset khác nhau cho cùng kết quả.
- [ ] Chạy `./gradlew :app:testDebugUnitTest --tests "*.EmbeddedPaneTouchMapperTest"`; xác nhận FAIL vì API chưa có.
- [ ] Tạo mapper theo `clamp(local.toDouble()*guestExtent/hostExtent, 0, guestExtent-1)`, chỉ trả Float hữu hạn; giữ `virtualAction`, `pressureFor` và event time của proof cũ nguyên trạng.
- [ ] Chạy lại test class tới PASS.

### Task 3: Geometry snapshot và host coordinator

**Files:**
- Create: `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/EmbeddedWorkspaceGeometrySnapshot.kt`
- Create: `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/EmbeddedWorkspaceRendererCoordinator.kt`
- Test: `app/src/test/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/EmbeddedWorkspaceRendererCoordinatorTest.kt`

**Interfaces:** `EmbeddedWorkspaceGeometrySnapshot.resolve(plan, policy): GeometrySnapshotResult`; success có `geometryBySourceId: Map<String,EmbeddedAppGeometry>` bất biến và `asPolicy(): EmbeddedGeometryPolicy` lookup cùng map. `EmbeddedWorkspaceRendererCoordinator(plan, mapper, geometrySnapshot, controller)` sở hữu `generationToken: Any`; `onViewportChanged(viewport): suspend ...`, `onSurfaceAvailable(generationToken,sourceCellId,viewToken,surfaceToken,executionSurface)`, `onSurfaceDestroyed(...): suspend ...`, `start(): suspend ...`, `sendTouch(...): suspend ...`, `close(): suspend ...`; `state` typed gồm layout, readiness và renderer failure. Không dùng Android View trong coordinator.

- [ ] Viết failing tests với fake policy đếm lần resolve và trả giá trị khác nếu gọi lại: snapshot một lần mỗi item, `asPolicy().geometryFor(item)` luôn trả giá trị đã đóng băng; thiếu ID trả typed rejection. Test hai plan cùng source ID nhưng metadata khác, `Mapped.planSnapshot` cũ bị từ chối; sửa backing list của plan đầu vào sau snapshot không đổi kết quả; stale generation/view/Surface callback không thay slot mới.
- [ ] Viết failing coroutine tests với fake surfaces + runner/controller theo pattern `EmbeddedWorkspaceRunnerControllerTest`: trước Start viewport 0 chặn; sau ACTIVE viewport tạm 0 giữ mapped pane/slot và không Stop; viewport dương tạo `ZeroPixelPane` công bố lỗi typed và Stop một lần; `surfaceChanged` cùng Surface không mất slot; Surface thực sự destroyed gọi `surfaceLost` đúng ID; resize hợp lệ không restart. Test `controller`/runner nhận target geometry đúng snapshot và chỉ runner/session factory tạo session.
- [ ] Chạy `./gradlew :app:testDebugUnitTest --tests "*.EmbeddedWorkspaceRendererCoordinatorTest"`; xác nhận FAIL vì API chưa có.
- [ ] Triển khai snapshot, generation/correlation checks và coordinator. Token kiểm bằng identity; full plan snapshot kiểm bằng value. Khi active với viewport tạm invalid giữ `Mapped` cuối, chặn touch; khi positive layout rejection gọi `controller.stop()` một lần, giữ slot/view cho đến cleanup. Callback cũ bị bỏ qua. Không thay `EmbeddedWorkspaceRunner` hoặc preflight.
- [ ] Chạy lại test class tới PASS; chạy `EmbeddedWorkspaceRunnerControllerTest` vì coordinator dùng contract controller.

### Task 4: Compose renderer và Surface lifecycle

**Files:**
- Create: `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/EmbeddedWorkspaceRenderer.kt`
- Test: `app/src/test/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/EmbeddedWorkspaceRendererCoordinatorTest.kt` (thêm event bridge cases); device proof ở Task 6 kiểm SurfaceView thật.

**Interfaces:** `@Composable EmbeddedWorkspaceRenderer(coordinator: EmbeddedWorkspaceRendererCoordinator, modifier: Modifier = Modifier)`; Surface event bridge chuyển `(generationToken, sourceCellId, viewToken, Surface identity)` vào coordinator. Geometry lấy từ snapshot của cùng generation; touch gọi `EmbeddedPaneTouchMapper` rồi controller qua coordinator.

- [ ] Đối chiếu bridge SurfaceView dự kiến với coordinator tests Task 3: mỗi callback mang đủ generation, source ID, view và Surface token; Compose remeasurement dùng lại view. Project chưa có Robolectric/Compose test dependency, nên kiểm UI thật ở device proof Task 6.
- [ ] Tạo Compose layout đo renderer-local viewport sau Scaffold padding; place pane theo `PixelRect` không spacing; `key(generationToken,sourceCellId)` và stable `AndroidView` factory. Một `SurfaceView`/pane, `holder.setFixedSize(guestWidth,guestHeight)` khi tạo, holder callbacks theo contract; `surfaceChanged` chỉ cập nhật host touch size. Giữ child composition theo last valid mapped layout khi viewport tạm zero trong ACTIVE. Touch dùng local `MotionEvent.x/y`, actual `view.width/height` và action/pressure/time hiện có.
- [ ] Chạy `./gradlew :app:compileDebugKotlin` và targeted coordinator tests tới PASS; kiểm scoped diff cho renderer/coordinator.

### Task 5: Route dùng Workspace ID tường minh và plan thật

**Files:**
- Create: `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/EmbeddedWorkspaceLayoutScreen.kt`
- Modify: `app/src/main/java/com/trancong/dexworkspacetouch/navigation/TouchNavigation.kt`
- Modify: `app/src/main/java/com/trancong/dexworkspacetouch/ui/screens/HomeScreen.kt`
- Test: `app/src/test/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/EmbeddedWorkspaceLayoutRouteTest.kt`

**Interfaces:** route `embedded-workspace-layout/{workspaceId}`; HomeScreen callback `onOpenEmbeddedWorkspaceLayout: (String) -> Unit`; screen nhận `workspaceId`, repository, request factory, activity và `onBack`. Route chỉ nhận ID từ `selectedWorkspaceId` khi không multi-select. Một pure/suspend loader helper trong screen module gọi `repository.getById(id)` rồi `WorkspaceLaunchRequestFactory.create(...)` và `EmbeddedWorkspacePlanner.plan(...)`, trả typed `MissingWorkspace`/`LaunchNotReady`/`Ready(plan)`.

- [ ] Viết failing loader tests với fake repository/catalog: ID chọn được truyền đúng đến `getById`, row không còn thì `MissingWorkspace` và không plan/Start, `LaunchReadiness.Ready` cho plan giữ nguyên bounds thực tế của Workspace và source ID; không tìm theo tên/app/thứ tự row.
- [ ] Chạy `./gradlew :app:testDebugUnitTest --tests "*.EmbeddedWorkspaceLayoutRouteTest"`; xác nhận FAIL vì loader chưa có.
- [ ] Thêm hành động chẩn đoán bên cạnh nút proof runner cũ, chỉ bật khi selected Workspace ID hợp lệ; điều hướng với ID encode/decode qua Navigation argument. Screen load row, tạo request/plan thật, resolve geometry snapshot, tạo runner với snapshot lookup policy và controller cho generation; hiển thị typed lỗi/diagnostics gồm workspace ID, viewport, rect, Surface validity, phase và cleanup result. Giữ `onLaunchWorkspace` Classic nguyên trạng và không tạo row mới.
- [ ] Chạy targeted loader tests và `./gradlew :app:compileDebugKotlin` tới PASS; kiểm scoped diff của ba file giao diện/route.

### Task 6: Regression và S23 DeX proof

**Files:** không sửa production file nếu các bước trên đã PASS; chỉ lưu bằng chứng theo quy trình verification hiện có, không đụng dữ liệu unrelated.

**Interfaces:** thiết bị chỉ proof hai target khác nhau Waze/Calculator; Workspace đã lưu do người thử chọn tường minh qua library action.

**Device acceptance đã sửa:** Dùng một Workspace hai cột thật đã lưu; Waze ở trái, rộng hơn Calculator ở phải và hai pane khác kích thước đáng kể. Hình học chuẩn hóa thực tế trong persisted plan là nguồn có thẩm quyền, không yêu cầu tỷ lệ định trước. Chỉ chấp nhận khi request/plan bình thường PASS, hai pane có kích thước hữu dụng dương, cùng một cạnh pixel chính xác, gap = 0 và overlap dương = 0. Với Workspace `273f0b3b-1da0-4512-88e0-e71751221021` tại viewport `1888×792`, chấp nhận Waze `[0,0,1181,792)` và Calculator `[1181,0,1888,792)` nếu các kiểm tra đó PASS; không yêu cầu cạnh 1180. Sau resize, lấy layout mới từ **cùng persisted plan** thay vì tính từ 0.625 hard-code; xác nhận cùng `sourceCellId`, cùng renderer generation nếu Surface sống, hai pane vẫn khác kích thước đáng kể, cùng một cạnh pixel chính xác, gap = 0, overlap dương = 0, hình học chuẩn hóa nguồn/plan không đổi, guest geometry đóng băng và không cấp phát session mới. Pure mapper unit tests chịu trách nhiệm kiểm chứng số học ánh xạ chính xác.

- [ ] Trong thread triển khai này, sau Task 5 chạy focused EmbeddedWorkspace/EmbeddedApp runtime tests và local gate `.\gradlew.bat testDebugUnitTest assembleDebug lintDebug --console=plain`; xác nhận PASS hoặc báo rõ lỗi. `assembleDebug` chỉ là local gate, **không** dùng APK debug cho S23 proof.
- [ ] Dừng trước device proof. Thread proof riêng dùng workflow production-config/production-signed đã chứng minh qua script `build-device-smoke`; ghi smoke APK path, SHA-256 mới, installed `base.apk` hash và signer SHA-256 bắt buộc `19ac0ea99125361b3c2083aaa44c8745ebad9c55fa967642c2b9e5a1086a45e7`. Không đổi versionName/versionCode, không publish. Hai pane phải được tương tác **trước** khi resize một lần. Nhánh A: Surface giữ identity, ACTIVE/geometry guest giữ nguyên, rect và touch size đổi, Stop một lần, cleanup ngược, zero orphan. Nhánh B: Surface thực sự destroyed, đi `surfaceLost`, cleanup toàn run, zero orphan; không nhấn thêm Stop và không gọi stable-active-resize PASS. Không retry để lấy nhánh kia.
- [ ] Trong thread này chạy `git diff --check`, `git status --short`, `git diff --stat`, scan ranh giới kiến trúc, xem scoped diff và xác nhận các file uncommitted ban đầu còn nguyên. Báo rõ local PASS và device proof `NOT VERIFIED`; không commit/push.
