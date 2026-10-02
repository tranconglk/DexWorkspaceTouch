# DWT-VDM-009 — Task 8: Regression + Full Local Gate

Ngày phiên: **2026-10-02 (Asia/Saigon)**. **Verdict: PASS.**

Chỉ thực hiện Task 8 theo yêu cầu `EXECUTE TASK 8 ONLY`. Authorization hiện hành xác nhận Tasks 1–2, 3D/3G/3B và 4–7 đã hoàn tất/accepted; các nhãn NOT AUTHORIZED cũ trong plan/evidence được giữ làm lịch sử. Task 9 không được thực hiện.

## Baseline và phạm vi

- Workspace: `D:\AndroidStudioProjects\DexWorkspaceTouch`.
- Branch: `release/1.0-beta`; HEAD: `2c77e6af40a879604897e328f5f6a46441e9e69e`.
- Baseline: **22 tracked modified, 187 untracked files** theo `git status --short --untracked-files=all`. Không có staged change.
- Tracked diff tổng: **22 files, 3192 insertions, 567 deletions**. Có 20 app production/test files và hai experiment files có trước.
- [Baseline đầy đủ](task-8-baseline.json) lưu status, scoped inventory/numstat, SHA256 của **818 tracked/untracked files**, index và HEAD. Timestamp chứng cứ giữ nguyên theo execution clock; ngày phiên ở đầu báo cáo theo client.
- [Scope audit cuối](task-8-scope-audit.json): **818/818 baseline files giữ nguyên**, index/branch/HEAD giữ nguyên, unexpected mutation = **0**. Chỉ thêm record và evidence Task 8; logs và APK/build intermediates thông thường nằm trong các đường dẫn ignored phù hợp.
- Không reset/stash/clean/normalize/stage/commit/push. Không ghi đè evidence lịch sử. Không sửa production hoặc tests trong Task 8.

Đã đọc scope amendment, plan và sáu báo cáo Task 3G/3B/4/5/6/7 được yêu cầu. Phạm vi hiện hành là hardening trong cùng process, runtime thuộc route, Application giữ value state và unresolved ownership fail closed.

### Inventory code/test 009 có trước Task 8

Các đường dẫn bên dưới tính từ `app/src/main/java/com/trancong/dexworkspacetouch/`; test tương ứng tính từ `app/src/test/java/com/trancong/dexworkspacetouch/`. Inventory gồm **18 production + 17 test/helper files** đã modified/untracked; mọi file giữ nguyên trong Task 8. Tên/path đầy đủ có trong baseline JSON.

| Task trước | Production trong working tree | Test/helper liên quan có thay đổi |
| --- | --- | --- |
| 1 | `feature/embeddedworkspace/product/EmbeddedProductIssue.kt`, `EmbeddedProductRecovery.kt`, controller/screen chung | `EmbeddedProductIssueTest.kt`, controller test chung |
| 2 | `feature/embeddedworkspace/product/AndroidEmbeddedCapabilityProbe.kt`, `EmbeddedWorkspaceReadiness.kt`, screen chung | `EmbeddedWorkspaceReadinessTest.kt`, `EmbeddedShizukuRefreshTest.kt` |
| 3D | `feature/embeddedapp/EmbeddedAppServiceConnectionManager.kt`, `EmbeddedAppSession.kt`, `EmbeddedAppSessionState.kt`; `workspace/execution/embedded/runtime/AndroidEmbeddedWorkspaceSessionFactory.kt`, `EmbeddedWorkspaceSessionPort.kt`, `EmbeddedWorkspaceRunner.kt` | `feature/embeddedapp/ConnectionBoundaryFixture.kt`, manager/session boundary tests; runtime adapter/runner/connection-boundary integration tests |
| 3G | `feature/embeddedapp/EmbeddedStartIpcTask.kt` cùng manager/session/state và runner ở trên | `EmbeddedAppStartIsolationTest.kt`, `EmbeddedWorkspaceHungStartIntegrationTest.kt`, manager/runner tests |
| 3B | `feature/embeddedworkspace/product/EmbeddedProductRunGate.kt`, `EmbeddedWorkspaceProductController.kt`, `EmbeddedWorkspaceProductScreen.kt` | `EmbeddedProductRunGateTest.kt`, `EmbeddedWorkspaceProductControllerTest.kt` |
| 4 | gate/controller/screen chung | `EmbeddedProductLifecycleTest.kt` |
| 5 | gate/screen chung; `navigation/TouchNavigation.kt`, `ui/screens/HomeScreen.kt` | `EmbeddedCleanupBlockedUxTest.kt` |
| 6 | `feature/embeddedworkspace/product/EmbeddedProductDiagnostics.kt`, `EmbeddedProductDiagnosticFormatter.kt`, gate/screen chung | `EmbeddedProductDiagnosticFormatterTest.kt` |
| 7 | `navigation/TouchNavigation.kt`, `ui/screens/HomeScreen.kt` | `EmbeddedProofAccessTest.kt` |

Hai tracked experiment edits có trước là `experiments/shizuku-task-probe/.../MainActivity.java` và `ProbeUserService.java`. Các tài liệu 009R, `experiments/dwt-vdm-009r-i-platform-probe/`, AGENTS, smoke images và evidence lịch sử cũng có trước, được bảo toàn; chúng không được đưa vào production app hoặc chạy trong Task 8.

## Lệnh kiểm chứng và kết quả focused

Gộp Steps 1–4 vào một Gradle invocation để giảm lượt build. Các bộ lọc độc lập vẫn được báo cáo riêng theo XML; full gate chỉ chạy sau khi mọi nhóm focused PASS.

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests '*.feature.embeddedworkspace.product.*' `
  --tests '*.EmbeddedAppServiceConnectionManagerTest' `
  --tests '*.EmbeddedAppSessionBoundaryTest' `
  --tests '*.EmbeddedAppStartIsolationTest' `
  --tests '*.AndroidEmbeddedWorkspaceSessionAdapterTest' `
  --tests '*.EmbeddedWorkspaceRunnerTest' `
  --tests '*.EmbeddedWorkspaceConnectionBoundaryIntegrationTest' `
  --tests '*.EmbeddedWorkspaceHungStartIntegrationTest' `
  --tests '*.EmbeddedAppLifecycleCoordinatorTest' `
  --tests '*.EmbeddedAppSessionStateTest' `
  --tests '*.EmbeddedAppSessionPolicyTest' `
  --tests '*.SessionStopCoordinatorTest' `
  --tests '*.SessionConnectionCoordinatorTest' `
  --tests '*.EmbeddedWorkspacePreflightTest' `
  --tests '*.EmbeddedWorkspaceRendererCoordinatorTest' `
  --tests '*.EmbeddedWorkspaceRunnerControllerTest' `
  --tests '*.EmbeddedWorkspaceLayoutRouteTest' `
  --tests '*.EmbeddedWorkspaceLayoutMapperTest' `
  --tests '*.EmbeddedPaneTouchMapperTest' `
  --tests '*.WorkspaceLaunchViewModelTest' `
  --tests '*.AndroidWorkspaceLauncherTest' `
  --tests '*.WorkspaceLaunchRequestFactoryTest' `
  --tests '*.WorkspaceLaunchUiMapperTest' `
  --tests '*.WorkspaceLibraryViewModelTest' `
  --tests '*.WorkspaceLibraryInteractionPolicyTest' `
  --tests '*.WorkspaceLibraryProjectionTest' `
  --tests '*.WorkspaceLibraryMapperTest' --console=plain
```

Lượt sandbox đầu không resolve được Android Gradle plugin `9.2.1`, exit 1, **chưa tới tests**: [log môi trường](task-8-focused.log). Cùng command chạy bằng cache/SDK hiện có với quyền phù hợp đạt **BUILD SUCCESSFUL, exit 0**: [log focused](task-8-focused-cache.log). Không đổi dependency/configuration.

XML đã được đọc trước khi full gate thay thế results; [summary focused](task-8-focused-summary.json) lưu exact suite name, actual counts, testcase names, XML timestamp và SHA256. Đối chiếu nguồn xác nhận wildcard product chạy đủ **11** lớp hiện có. Assertion metadata ban đầu nhầm tổng dự kiến 38 suites đã được sửa bằng inventory thực tế **37**; đây không phải test failure, không sửa test và không chạy lại focused tests.

| Nhóm | Suites | Tests | Failures | Errors | Skipped | Kết quả |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| 009 product | 11 | 236 | 0 | 0 | 0 | PASS |
| Runtime / Task 3D + 3G | 13 | 114 | 0 | 0 | 0 | PASS |
| Renderer 007 | 5 | 21 | 0 | 0 | 0 | PASS |
| Classic / Workspace | 8 | 106 | 0 | 0 | 0 | PASS |
| **Focused tổng** | **37** | **477** | **0** | **0** | **0** | **PASS** |

### Actual counts từng focused suite

Mỗi hàng dưới đều có **failures/errors/skipped = 0**; counts lấy từ thuộc tính XML `testsuite`, không suy từ số annotation hoặc báo cáo Task trước.

| Nhóm | Suite | Tests |
| --- | --- | ---: |
| 009 | EmbeddedProductIssueTest | 17 |
| 009 | EmbeddedShizukuRefreshTest | 14 |
| 009 | EmbeddedProductRunGateTest | 16 |
| 009 | EmbeddedWorkspaceProductControllerTest | 24 |
| 009 | EmbeddedProductLifecycleTest | 22 |
| 009 | EmbeddedCleanupBlockedUxTest | 29 |
| 009 | EmbeddedProductDiagnosticFormatterTest | 32 |
| 009 | EmbeddedProofAccessTest | 65 |
| 009 | EmbeddedWorkspaceProductRoutingTest | 3 |
| 009 | EmbeddedWorkspaceEligibilityTest | 5 |
| 009 | EmbeddedWorkspaceReadinessTest | 9 |
| Runtime | EmbeddedAppServiceConnectionManagerTest | 27 |
| Runtime | EmbeddedAppSessionBoundaryTest | 11 |
| Runtime | EmbeddedAppStartIsolationTest | 5 |
| Runtime | AndroidEmbeddedWorkspaceSessionAdapterTest | 11 |
| Runtime | EmbeddedWorkspaceRunnerTest | 19 |
| Runtime | EmbeddedWorkspaceConnectionBoundaryIntegrationTest | 6 |
| Runtime | EmbeddedWorkspaceHungStartIntegrationTest | 1 |
| Runtime | EmbeddedAppLifecycleCoordinatorTest | 10 |
| Runtime | EmbeddedAppSessionStateTest | 4 |
| Runtime | EmbeddedAppSessionPolicyTest | 2 |
| Runtime | SessionStopCoordinatorTest | 1 |
| Runtime | SessionConnectionCoordinatorTest | 6 |
| Runtime | EmbeddedWorkspacePreflightTest | 11 |
| 007 | EmbeddedWorkspaceRendererCoordinatorTest | 7 |
| 007 | EmbeddedWorkspaceRunnerControllerTest | 3 |
| 007 | EmbeddedWorkspaceLayoutRouteTest | 3 |
| 007 | EmbeddedWorkspaceLayoutMapperTest | 5 |
| 007 | EmbeddedPaneTouchMapperTest | 3 |
| Classic | WorkspaceLaunchViewModelTest | 15 |
| Classic | AndroidWorkspaceLauncherTest | 20 |
| Classic | WorkspaceLaunchRequestFactoryTest | 12 |
| Classic | WorkspaceLaunchUiMapperTest | 2 |
| Library | WorkspaceLibraryViewModelTest | 46 |
| Library | WorkspaceLibraryInteractionPolicyTest | 2 |
| Library | WorkspaceLibraryProjectionTest | 7 |
| Library | WorkspaceLibraryMapperTest | 2 |

Runtime regression bảo vệ bounded connect/readiness, exact UID 2000, identity/revision fencing, pending-Start false-clean prevention, sequential startup, reverse cleanup, REMOTE_DIED, host-owned Surface, terminal cache, actor/root/timer termination và late READY/ACTIVE rejection. Renderer suites bảo vệ frozen geometry, stale generation/Surface events, real destroy vs stale destroy, detach/touch mapping và một cleanup khi Stop/Dispose. Không thay renderer semantics.

Classic launcher/ActivityOptions/flags và launch ViewModel không có diff. Home `Mở` vẫn gọi `productRouting.openClassic` rồi launch ViewModel; Embedded là hành động thử nghiệm tường minh, không silent fallback hoặc Shizuku trên Classic. Library suites bảo vệ load/projection/selection/interaction. Task-5/7 navigation được kiểm chứng bằng policy/controller unit tests và source wiring; actual Compose/NavController vẫn NOT VERIFIED.

## Full local gate

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug lintDebug --console=plain
```

[Full gate log](task-8-full-gate.log): **BUILD SUCCESSFUL, exit 0**, 3m20s; 56 actionable tasks, 13 executed/43 up-to-date. Ba task cần kiểm chứng đều hiện trong log, không có failed task.

| Task | Kết quả | Bằng chứng |
| --- | --- | --- |
| `:app:testDebugUnitTest` | PASS | **140 suites, 1159 tests, 0 failures, 0 errors, 0 skipped**; test task thực sự executed |
| `:app:assembleDebug` | PASS | debug package/assemble hoàn tất |
| `:app:lintDebug` | PASS | **0 errors, 46 warnings**, không ERROR/FATAL |

[Full XML summary](task-8-full-summary.json) lưu **actual counts từng suite của toàn bộ 140 suites**, timestamp và XML SHA256. Không xuất raw system-out, exception payload hoặc license-test data vào evidence. Focused và full counts là hai lượt chạy; không cộng chúng thành số test duy nhất.

[Lint summary](task-8-lint-summary.json) ghi issue ID, severity, category và source location. Nhóm warnings:

| Issue ID | Count |
| --- | ---: |
| AndroidGradlePluginVersion | 2 |
| GradleDependency | 7 |
| NewerVersionAvailable | 3 |
| ModifierParameter | 3 |
| DiscouragedApi | 1 |
| HardwareIds | 1 |
| DrawAllocation | 1 |
| ObsoleteSdkInt | 3 |
| ViewConstructor | 1 |
| UseKtx | 10 |
| UseTomlInstead | 2 |
| ClickableViewAccessibility | 11 |
| SetTextI18n | 1 |
| **Tổng** | **46** |

Category totals: Correctness 16, Security 1, Performance 4, Usability 1, Productivity 12, Accessibility 11, Internationalization 1. Không sửa warnings hoặc nâng dependency để làm sạch lint; không khẳng định mọi warning đều có trước 009 khi chưa có lint baseline tương ứng.

## Diff / whitespace / evidence

```powershell
git diff --check
```

**PASS, exit 0**: [diff-check output](task-8-diff-check.txt), file rỗng đúng với không có lỗi. Untracked production/test Kotlin/Java được kiểm tra riêng bằng `Select-String -Pattern '[\t ]+$'`: **0 findings**. Tracked scoped diffs, relevant untracked source/tests/evidence đã đối chiếu với inventory và approved contracts.

SHA256 bảo toàn baseline và kiểm tra new-path scope xác nhận không có mutation ngoài evidence Task 8, không có binary/secret file mới trong diff hoặc verification output. Debug APK/intermediates là output build được yêu cầu trong ignored `app/build`; không cài lên thiết bị.

## Architecture audit — current working tree

Source audit và focused tests được đối chiếu với compiled fields bằng:

```powershell
& 'C:/Program Files/Android/Android Studio/jbr/bin/javap.exe' -p `
  -classpath 'app/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes' `
  com.trancong.dexworkspacetouch.feature.embeddedapp.StartIpcTask `
  com.trancong.dexworkspacetouch.feature.embeddedworkspace.product.EmbeddedProductRunGate `
  com.trancong.dexworkspacetouch.feature.embeddedworkspace.product.ProductRunStatus `
  com.trancong.dexworkspacetouch.feature.embeddedworkspace.product.EmbeddedProductDiagnostics `
  com.trancong.dexworkspacetouch.feature.embeddedworkspace.product.EmbeddedDiagnosticId `
  com.trancong.dexworkspacetouch.ui.screens.HomeEmbeddedProofMenu
```

**javap exit 0**: [compiled field audit](task-8-value-graph-javap.txt). Đây là source/compiled-field/test audit, không phải heap/GC proof.

| Mục | Findings / evidence hiện hành | Kết quả |
| --- | --- | --- |
| A. Application state | `DexWorkspaceTouchApplication.kt:53` giữ gate. Gate instance fields chỉ lock, StateFlows, hai sequences và Classic flag; status gồm token/workspace value, identities, enums, typed issue và copied immutable lists. Không có RunOwner/renamed runtime owner, controller/runner/session/renderer/Surface/View/Activity/adapter/Binder/navigation callback/Job/Deferred trong product state. Existing process scope chỉ parent cleanup coroutine hữu hạn của route theo ngoại lệ amendment; không có retained cleanup handle trong gate. | PASS |
| B. Hung Start | `EmbeddedStartIpcTask.kt` có đúng 10 fields: Binder/proxy, copied strings/scalars, required raw Surface, operation và detachable mailbox. Không outer/session/route callback field. `EmbeddedAppSessionState.kt:37` dùng Start lane 2 workers, zero queue, AbortPolicy; Stop/Close dùng worker khác. Manager bookkeeping `operationId -> sessionId` không xóa pending Start khi Stop hoàn tất, absence/revision checks từ chối pending operations. Timeout/detach không thành clean; unresolved ownership giữ fail closed. | PASS |
| C. Terminal lifetime | `EmbeddedWorkspaceRunner.kt:516`: materialize result trước publish cache/close admission, detach consumers, complete/drain replies, clear prepared/owned/dependency holders, cancel timers/root. Cached Stop/SurfaceLost vẫn dùng được và late events không resurrect. Controller bỏ execution/readiness/navigation callbacks; host disposal kết thúc scope. CLEANUP_BLOCKED UI/diagnostics đọc Application values, không cần old graph. | PASS |
| D. Gate | `EmbeddedProductRunGate.kt:12` có đúng IDLE/STARTING/ACTIVE/STOPPING/CLEANUP_BLOCKED. `matches` dùng exact token identity + generation + operation kind/ID; Stop intent fence Start result. Controller gọi `markInvoked` trước execution Start. `releaseWithoutAllocation` chỉ nhận exact authoritative PRE_RUNNER_REJECTED proof; null/exception/cancellation/timeout sau invocation không release. Blocked terminal từ chối cả late clean completion. | PASS |
| E. Recreation | Product screen khởi tạo route `execution = null`; factory chỉ được gọi bởi explicit user Start sau `createExecutionIfIdle`. Non-IDLE new host không controller/execution/Surface/token/cleanup retry. Blocked branch early-return trước loader/probe/runtime. IDLE transition không auto-restart. Lifecycle tests bao phủ old/new host, cleanup join, stale callbacks và terminal graph release. | PASS |
| F. Blocked UX | `EmbeddedCleanupBlockedAction` chỉ BACK/VIEW_STATUS; branch blocked chỉ navigation/status/copy diagnostics. Không Start/Classic/Retry/reconcile/manual unlock/service reset/global cleanup. Home view-status callback đọc lại current phase và workspace provenance trước navigate, không dispatch cleanup hoặc nhả gate. | PASS |
| G. Diagnostics | Pure immutable allowlisted snapshot; provenance giữ nguyên, thiếu ID thành unknown. Max 2 items + 2 outcomes, ID <=256 code units, output <=8192 chars, cleanup failure-code allowlist. Không raw runtime result/Binder/Surface/Throwable/stack/logcat/secret trong DTO. Clipboard chỉ được gọi tại `OutlinedButton.onClick`; formatting/observation không tự copy. | PASS |
| H. Proof access | Ordinary Home chỉ có deliberate developer entry; menu có đúng Boolean value và mở sheet không allocation. Cả năm exact routes còn tồn tại, mỗi child screen chỉ được gọi sau `EmbeddedProofDestination`; dispatch và destination cùng đọc gate mới nhất. Non-IDLE/direct/restored/stale admission bị chặn trước factory. Home vẫn là startDestination; proof không thành normal default. | PASS |

Các source symbols được kiểm tra bằng targeted `rg -n` và scoped `git diff --` trên product/gate/controller/screen, Home/navigation, Start task/session/manager/runner/adapter/port; `rg` references xác nhận năm proof screens chỉ có một production call site mỗi screen, nằm trong guarded navigation destination. Value-graph, lifecycle, late-completion và allocation-counter assertions nằm trong các suite đã chạy, không chỉ dựa vào báo cáo lịch sử.

## Forbidden-scope audit

**PASS — không phát hiện forbidden capability trong current 009 production diff.**

- Không process-death recovery, persistent ownership journal/marker, pending/commit, cross-process reconstruction/reconcile hoặc recovery enrollment.
- Không remote pre-reserve cancellation/tombstone, RemoteSessionRuntime monitor redesign, AIDL cancellation protocol, UserService kill/global cleanup mới. Remote implementation/AIDL không có diff. Existing final-removal/reconcile primitives đã có ở HEAD; 009 chỉ bổ sung client operation/revision guards và không expose product reconcile.
- Không product cleanup Retry/reconcile, >2-app support, mixed mode hoặc dynamic geometry expansion; eligibility vẫn 1–2 và renderer/layout/Classic engine giữ nguyên.
- Không Room/schema migration, release/license/signing/trusted-key/production URL change hoặc dependency upgrade; protected-path diff kiểm tra cho các nhóm này rỗng.
- 009R design/probe material và experiment edits có trước được giữ nguyên, không được triển khai/chạy như recovery của Task 8.

## Secret / security sanity

**PASS trong phạm vi audit.** Diagnostics DTO/formatter chỉ xuất allowlisted value fields; không có truy cập license/token store, raw exception/stack/logcat hoặc arbitrary runtime dump. `RunToken` là in-process workspace identity, không phải license token.

Production 009, reports/logs/text evidence 009 và toàn bộ Task-8 artifacts được kiểm tra local cho private-key PEM, Bearer payload và secret assignment; Task-8 artifacts còn kiểm tra JWT/license-key/token assignment. **0 candidate locations**. Summaries chỉ lưu class/test names, counts, hashes và lint metadata; không copy raw XML system-out/full exception data. Không có secret material hoặc personal/device data không cần thiết được phát hiện; không tạo security subsystem mới.

## Narrow fixes và final changed-file scope

**Production/test fixes: không có.** Chỉ xử lý environment bằng cùng Gradle command với cache/SDK có sẵn và sửa phép đếm metadata suite theo inventory thực tế; không weaken/skip test, không broaden capability.

Task 8 chỉ tạo:

- `verification/dwt-vdm-009/local-gate.md`.
- `task-8-baseline.json`, `task-8-scope-audit.json`.
- `task-8-focused.log`, `task-8-focused-cache.log`, `task-8-full-gate.log`.
- `task-8-focused-summary.json`, `task-8-full-summary.json`, `task-8-lint-summary.json`.
- `task-8-diff-check.txt`, `task-8-value-graph-javap.txt`.

Các file evidence ở cùng thư mục với báo cáo này. Audit không có reviewer/subagent độc lập; kết luận dựa trên commands, source/compiled-field audit và unit/integration results của lượt này.

## PASS criteria và giới hạn

| Tiêu chí Task 8 | Kết quả |
| --- | --- |
| Focused 009 regression | PASS |
| Client runtime / 3D / 3G regression | PASS |
| Renderer 007 regression | PASS |
| Classic / Workspace regression | PASS |
| Full Gradle local gate exit 0 | PASS |
| git diff --check exit 0 | PASS |
| Architecture scan A–H | PASS |
| Forbidden-scope scan | PASS |
| Không unresolved unexpected working-tree mutation | PASS |
| Secret/security sanity và ghi rõ limitations | PASS |

**NOT VERIFIED:** actual Compose/NavController behavior trên S23/DeX; real Shizuku/Binder scheduling; native Surface/vendor lifetime; heap/GC proof; authoritative cleanup của permanently hung remote Start; process-death recovery. Unit/local build evidence không chuyển các mục này thành PASS hoặc clean acknowledgement.

**Final verdict: PASS. STOP AFTER TASK 8.** Không connect S23, không install/device scenario, không production-signed smoke APK, không đổi signing/version và không tạo Task-9 result.
