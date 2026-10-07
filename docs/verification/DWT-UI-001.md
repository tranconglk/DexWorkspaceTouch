# DWT-UI-001 — Remove Embedded from product UI

Baseline: `release/1.0-beta`, `b26b5228b6d203982c0c25361e104b086a9842d0`.
Branch: `ui/dwt-ui-001-hide-embedded`. No commit, push, tag or release publication.

## Inventory before implementation

| Class | Source / entry | Decision |
| --- | --- | --- |
| A | `WorkspaceLibraryCard`: Mở Embedded (Experimental/Frozen), used by both pinned and regular Home lists | Remove button and product callback |
| A | `HomeScreen`: Nhà phát triển (Experimental/Frozen), wide and narrow toolbars | Remove both buttons and the proof bottom sheet |
| A | Home proof menu: Embedded Waze (Experimental/Frozen) | Remove product control/callback |
| A | Home proof menu: Embedded Calculator (Experimental/Frozen) | Remove product control/callback |
| A | Home proof menu: Embedded Dual App (Experimental/Frozen) | Remove product control/callback |
| A | Home proof menu: Embedded Workspace Runner (Experimental/Frozen) | Remove product control/callback |
| A | Home proof menu: Embedded Workspace Layout (Experimental/Frozen), selected workspace | Remove product control/callback |
| A | Home CLEANUP_BLOCKED banner: Xem trạng thái Embedded | Remove product navigation callback; keep the complete safety banner |
| B | `TouchNavigation`: embedded-waze, embedded-calculator, embedded-dual-app, embedded-workspace-runner, embedded-workspace-layout/{workspaceId}, embedded-workspace/{workspaceId} | Retain destinations, screens and internal status links, unreachable from ordinary product controls |
| B | EmbeddedWorkspaceRunner, sessions, VDM/Surface, controllers, diagnostics/evidence, debug qualification hosts | Retain implementation and applicable historical tests |
| B | `HomeEmbeddedProofControl` / `HomeEmbeddedProofMenu` value models | Keep internal-only for existing historical admission tests; no product state, rendering or callbacks use them |
| C | `EmbeddedProductRunGate`, ProductRunGate admission, Classic/Shizuku exclusion, ownership/result authority | No changes |
| C | CLEANUP_BLOCKED projection/banner, blocked launch actions and fail-closed cleanup | Keep projection/banner and gate wiring; the existing optional status callback is omitted on Home |

Inspected Home, Workspace Library, navigation, main manifest/resources, Car screen/settings and other source references outside Embedded runtime. No additional product Embedded settings, bottom navigation item, external Embedded deep link or independent developer menu was found. Embedded destinations' own controls are internal once these incoming product edges are removed. Debug-only qualification activities are not product entries and remain intact.

## Scope lock and verification plan

- Change only Home/Library UI and their navigation callback wiring; retain historical proof-model/admission/evidence tests and add current product UI tests.
- Add real Android UI regression coverage for Library, Home/menus, ordinary navigation and the safety banner. Observe failure on the baseline before removing controls.
- Run affected Classic, Embedded admission/runtime and Shizuku Repair/settings regressions; build debug app and instrumentation APKs.
- Perform one-device UI smoke using an isolated debug application ID, retaining the installed product and its data. No Embedded execution qualification.
- Keep runtime, gate, Shizuku transport, Repair settings/default, Room schema, licensing, version and signing/release configuration unchanged.

## Results

**PASS — Embedded removed from product UI. STOP để review; chưa commit/push/tag.**

### Tệp source/test thay đổi

- `app/src/main/java/com/trancong/dexworkspacetouch/workspace/library/ui/WorkspaceLibraryCard.kt`: bỏ nút Embedded và hai tham số callback/enabled riêng của nó.
- `app/src/main/java/com/trancong/dexworkspacetouch/ui/screens/HomeScreen.kt`: bỏ cả hai nút Nhà phát triển, bottom sheet proof, state/callback mở Embedded và callback xem trạng thái; giữ đầy đủ banner an toàn và điều kiện chặn Classic.
- `app/src/main/java/com/trancong/dexworkspacetouch/navigation/TouchNavigation.kt`: bỏ dây nối từ Home vào Embedded; giữ nguyên sáu destination, route admission và đường Classic.
- `app/src/androidTest/java/com/trancong/dexworkspacetouch/ui/ProductUiWithoutEmbeddedTest.kt`: thêm test UI thực cho thẻ ghim/thường được chọn, Home, Tệp/Giới thiệu/Car, navigation bình thường, banner CLEANUP_BLOCKED và smoke Classic/Repair tùy chọn.
- Báo cáo này và `verification/dwt-ui-001/`: kiểm kê, script application ID kiểm thử riêng, log và bằng chứng thiết bị. Không sửa/xóa test an toàn hiện có.

### Kiểm chứng

| Kiểm tra | Kết quả / bằng chứng |
| --- | --- |
| RED trước bản vá | Hai test thất bại đúng vì nút Nhà phát triển và nút Xem trạng thái Embedded còn hiển thị: `ui-red.log` |
| `:app:assembleDebug` | PASS: `verification-build.log`; toàn bộ source Embedded vẫn được biên dịch |
| `:app:assembleDebugAndroidTest` | PASS: `ui-test-build.log`, trên source test cuối cùng |
| Hồi quy vùng liên quan | PASS: 38 suite, 467 test, 0 failure/error; `summary.json` và `verification-build.log` |
| UI/navigation + Classic/Repair smoke | PASS: `ui-smoke.log`, `OK (1 test)` |
| Banner an toàn trên APK cuối cùng | PASS: `ui-safety-green.log`, `OK (1 test)` |
| Scoped diff / whitespace | PASS: `git diff --check`; source sản phẩm chỉ đổi ba tệp UI/navigation, +2/-87 dòng |

Các nhóm unit test đã chạy: `feature.embeddedworkspace.product.*`, `workspace.execution.embedded.runtime.*`, `platform.launch.shizuku.*`, `*WorkspaceRepair*Test`, `workspace.library.*`. Bao gồm Classic admission/runtime isolation, ProductRunGate, cleanup/proof admission, Shizuku transport/mutation admission và Repair policy/session/default preferences. Không chạy lại những kết quả PASS này sau khi chỉ chỉnh helper instrumentation hoặc tài liệu.

### PASS criteria và smoke thiết bị

Thiết bị: **SM-S908E, Android SDK 36, DeX display 17**. APK debug cô lập: `com.trancong.dexworkspacetouch.ui001`. Test render composable/navigation sản phẩm thật bên dưới LicenseGate; không thay đổi trạng thái license hoặc APK/data của ứng dụng sản phẩm đang cài.

| Tiêu chí | Kết quả |
| --- | --- |
| Workspace Library không còn action Embedded, cả ghim/thường khi được chọn | PASS |
| Home không còn entry Embedded | PASS |
| Developer/product menus không expose Embedded | PASS; cả nút rộng/hẹp được bỏ, Tệp/Giới thiệu/Car không có entry |
| Navigation qua control sản phẩm không vào Embedded | PASS; gỡ tất cả callback đầu vào, giữ route nội bộ |
| Classic Open còn hiển thị và hoạt động | PASS; mở Calculator thật trên DeX, UI DWT báo gửi yêu cầu mở 1 ứng dụng |
| Dock Repair còn hiển thị và hoạt động | PASS; transport thật trả `admitted=true`, `REPAIRED` |
| OFF / SUGGEST / AUTOMATIC và mặc định SUGGEST | PASS; source/preferences không đổi, UI thấy đủ ba lựa chọn và SUGGEST được chọn |
| CLEANUP_BLOCKED vẫn fail-closed, không có nút vào Embedded | PASS; banner còn nguyên, Classic bị chặn, bằng chứng gate không đổi |
| ProductRunGate/admission và runtime Embedded | PASS; hồi quy xanh, source giữ nguyên và build được |

Repair giữ nguyên task **11421**, display **17**, user **0**, component Calculator và activity identity. Bounds trước `(16,16)-(1920,1024)`, sau **`(8,8)-(1912,1016)`**, đúng expected. Bằng chứng: `device/repair-report.txt`, `device/repair-result.txt`. Các snapshot UI cuối cùng nằm trong `device/`.

Điều kiện smoke Repair: có một target Calculator freeform hiển thị duy nhất. Các lần thử helper trước đó tạo bốn cửa sổ Calculator, đều được xác minh `launchedFromPackage=...ui001`; Repair từ chối đúng với `UNRESOLVED`. Chỉ bốn cửa sổ kiểm thử đó được đóng qua nút Close trước lần PASS (`cleanup-calculator-tasks.log`). Không nới matching, admission, ownership hay cleanup. Helper đọc node được refresh và thao tác chạm/cuộn theo bounds nhìn thấy; không thêm đường vào UI sản phẩm.

### Xác nhận phạm vi giữ nguyên

- Classic launch; Shizuku Workspace Control/UserService transport; floating dock Repair; OFF/SUGGEST/AUTOMATIC/default SUGGEST và normal Library flows: source/runtime không đổi.
- Embedded runner, sessions, VDM/Surface, product controller/runtime, diagnostics/evidence và các test lịch sử: còn nguyên.
- `EmbeddedProductRunGate`, ProductRunGate admission, CLEANUP_BLOCKED, Classic/Shizuku mutual exclusion, ownership/result authority và cleanup fail-closed: không đổi.
- Room/schema, licensing/security, dependency/toolchain, version, signing/production configuration: không đổi.
- `release/1.0-beta` và HEAD nhánh mới vẫn là `b26b5228b6d203982c0c25361e104b086a9842d0`. Worktree gốc vẫn ở `wip/pre-integration-release-1.0-beta-20261006`, HEAD `db69bed601eceea12259249c89f0f5276793e425`; các thay đổi sẵn có được giữ nguyên.
- Không commit, push, tag hoặc publish. Dừng để review tại `.worktrees/dwt-ui-001`.

Full project suite và chạy APK release đã ký: **NOT VERIFIED**, ngoài phạm vi task. Các log thử helper trung gian đã được thay thế về kết luận bởi hai log UI PASS cuối cùng nêu trên.
