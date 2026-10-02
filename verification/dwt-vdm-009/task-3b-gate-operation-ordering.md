# DWT-VDM-009 — Task 3B: Gate generation/operation ordering

**Kết quả: COMPLETE / targeted và affected regression PASS.** Branch `release/1.0-beta`. Authorization lấy từ yêu cầu RESUME / EXECUTE TASK 3B ONLY: Task 3G đã PASS / ACCEPTED, thay trạng thái chặn cũ trong tài liệu cho riêng Task 3B. Dừng sau Task 3B; Tasks 4–9 chưa được cấp phép.

## File thay đổi

Production trong `app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/product/`:

- `EmbeddedProductRunGate.kt`: identity, invocation category, proof trước invocation, DTO bất biến, admission và callback fences.
- `EmbeddedWorkspaceProductController.kt`: không giữ mutex qua Start suspension; một Start, một cleanup; terminal gỡ execution/readiness closure và waiter.
- `EmbeddedWorkspaceProductScreen.kt`: observer gửi exact Start operation của controller thuộc route đó. Thay đổi Task 3B tại screen chỉ là đoạn observer này; các thay đổi Task 1/2 có trước được giữ.

Tests trong `app/src/test/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/product/`:

- `EmbeddedProductRunGateTest.kt`: 16 test, audit graph giá trị và helper fixture cho policy/routing/Shizuku tests hiện có.
- `EmbeddedWorkspaceProductControllerTest.kt`: 24 test, gồm integration với runner thật sau Task 3G.

Không cần sửa `DexWorkspaceTouchApplication.kt`: DI gate và route scope hiện có đủ dùng. Báo cáo này là file verification mới; không sửa tài liệu/evidence cũ.

## RED đã quan sát

- Baseline: **17 tests, 6 FAIL**. Hai late Started tests; Start+Back không ghi Stop intent khi Start pending; double Start phải chờ; null Start nhả gate; double Back/Dispose gọi cleanup nhiều lần.
- `terminalNotificationResolvesPendingStartAndDropsRouteWaiter`: **FAIL trong 35 tests**, waiter Start chưa hoàn tất sau terminal notification.
- `executionCancellationAfterInvocationRemainsUncertain` và `cleanupCancellationDoesNotStrandStopping`: **2/2 FAIL**, gate mắc ở STARTING/STOPPING khi execution ném cancellation.
- `statusCollectorStartingNewRunCannotBeOverwrittenByPreviousPhasePublication` và assertion typed issue trong `startedAndCleanStopFollowProductPhases`: **2/2 FAIL**. Publish pha cũ ghi đè pha run mới khi collector reentrant; clean result rỗng còn mang issue uncertainty.
- Affected regression `refreshAtIdleCannotReplaceUncertainTaskOneEvidenceWithAbsent`: **1 FAIL trong 36 tests** khi thử tự cleanup từ null Start. Đã bỏ hành vi đó: missing Start result giữ uncertainty trước terminal, Back/Dispose vẫn tạo cleanup ban đầu. File test Task 2 giữ nguyên.
- Test contract mới có RED tại test compilation do chưa có generation/operation/status API, gồm `recordMissingStartResult`. Đây là RED của API, **không gọi compile failure là behavioral proof**. Các behavioral RED ở trên được quan sát riêng.

## GREEN và lệnh kiểm chứng

Targeted command:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests '*.EmbeddedProductRunGateTest' --tests '*.EmbeddedWorkspaceProductControllerTest' --console=plain
```

**BUILD SUCCESSFUL; 40/40 PASS**, XML: gate 16, controller 24; failures/errors/skipped đều 0.

Affected regression và Task 3G regression nhỏ:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests '*.EmbeddedProductIssueTest' --tests '*.EmbeddedWorkspaceProductRoutingTest' --tests '*.EmbeddedShizukuRefreshTest' --tests '*.EmbeddedWorkspaceHungStartIntegrationTest' --tests '*.EmbeddedWorkspaceRunnerTest.every true terminal clears runtime graph and ends root job while cache remains usable' --console=plain
```

**BUILD SUCCESSFUL; 36/36 PASS**, XML:

| Lớp/filter | PASS |
| --- | ---: |
| EmbeddedProductIssueTest | 17 |
| EmbeddedWorkspaceProductRoutingTest | 3 |
| EmbeddedShizukuRefreshTest | 14 |
| EmbeddedWorkspaceHungStartIntegrationTest | 1 |
| EmbeddedWorkspaceRunnerTest — terminal/cache method nêu trên | 1 |

Failures/errors/skipped đều 0. Lượt sandbox đầu không tới tests vì không resolve Android Gradle plugin; cùng command chạy với quyền cache/SDK hiện có đã tạo RED/GREEN thực tế. Không thay dependency. XML được đọc theo suite name vì Gradle rút ngắn một số tên file dài trên Windows.

## Generation, operations và invocation boundary

Mỗi acquire thành công tăng `generationSequence` trong process. `operationSequence` tăng cho Start và cleanup; cleanup chỉ được cấp ID một lần. `ProductRunOperation` gồm exact `RunToken`, `generation`, `operationId`, `kind` (`START` hoặc `CLEANUP`). Token giữ `workspaceId` bất biến và được so sánh bằng exact object identity trong process.

`markInvoked(start)` chạy ngay trước `execution.start()` trong coroutine `UNDISPATCHED`. Lock của controller chỉ bao quanh admission và phần đi vào invocation đến suspension đầu tiên; không giữ lock qua thời gian chờ Start. Back ghi `stopRequested` và cleanup ID trước khi gọi `execution.close()`.

`releaseWithoutAllocation` chỉ nhận proof của exact operation ở `PRE_RUNNER_REJECTED`; gate tự chứng minh operation chưa INVOKED, chưa có Stop intent và vẫn là owner hiện tại. Proof cũ, sai identity, chưa được xác nhận hoặc operation đã INVOKED đều không nhả gate. Host readiness đổi trước invocation dùng đường proof này, execution không được gọi.

Sau invocation, null/exception/cancellation không phải no-allocation hoặc terminal-detach acknowledgement. `recordMissingStartResult` giữ STARTING, `RESULT_OR_UNCERTAIN`, allocation UNKNOWN và cleanup UNCERTAIN; Classic/Embedded mới bị khóa. Không tự Start lại hoặc tự cleanup từ readiness refresh. Runtime còn thuộc route trước terminal để Back/Dispose có thể chạy cleanup ban đầu. Exact `PreflightRejected` đi qua DTO/result acceptance bình thường, có thể trả IDLE theo authoritative no-allocation result.

Khi có Stop intent, kết quả của Start operation bị từ chối; chỉ cleanup operation hiện tại được quyết định terminal. Sau terminal CLEANUP_BLOCKED, mọi completion muộn đều bị từ chối, kể cả một clean result cùng identity; không Retry/reconcile. DTO kiểm tra exact owned/cleanup source set, duplicate/missing/wrong outcome và `allOwnedSessionsClean`, đồng thời đối chiếu owned set đã biết.

Publish phase đọc status mới nhất sau khi phát status: collector có thể acquire run mới ngay khi nhận authoritative IDLE mà lần publish trước không ghi lại phase cũ lên run mới.

## PASS criteria — 22 trường hợp yêu cầu

| # | Trường hợp | Bằng chứng test | Kết quả |
| --- | --- | --- | --- |
| 1 | Late Started sau Stop intent | `lateStartedAfterStopIntentCannotBecomeActive`, pending Start+Back | PASS |
| 2 | Stale token | `staleTokenGenerationAndStartOperationAreRejectedWithoutChangingStatus` | PASS |
| 3 | Stale generation | Cùng test identity, controller stale callback test | PASS |
| 4 | Stale Start operation | Cùng test identity, `stopIntentHasOneOperationAndRejectsBothStartedAndOldStartTerminal` | PASS |
| 5 | Stale cleanup không nhả run mới | `staleCleanupCannotReleaseNewerRunEvenWithSameWorkspaceId`, reentrant publication test | PASS |
| 6 | Double Start chỉ một invocation | `doubleStartReturnsBusyWhileFirstInvocationIsPending` | PASS |
| 7 | Start+Back ghi intent khi Start pending | `startPlusBackRecordsStopIntentBeforePendingStartReturns` | PASS |
| 8 | Start+Back một cleanup | Cùng pending Start+Back test, integration runner thật | PASS |
| 9 | Double Back join | `doubleBackAndDisposeJoinOneBlockedCleanup`, exact cleanup identity test | PASS |
| 10 | Dispose join cleanup hiện có | `doubleBackSharesExactCleanupIdentityAndDisposeJoinsIt`; repeated Dispose cùng Job | PASS |
| 11 | Non-IDLE khóa Classic | `everyNonIdlePhaseBlocksClassicAndAnotherEmbeddedRun`, policy/routing regression | PASS |
| 12 | Non-IDLE khóa Embedded mới | Cùng admission test ở cả bốn pha non-IDLE | PASS |
| 13 | Pre-runner release chỉ theo proof | `preRunnerRejectionReleasesOnlyWithExactAuthoritativeNoInvocationProof`, renderer-change controller test | PASS |
| 14 | INVOKED + null giữ uncertainty | `invokedNullStartRemainsUncertainAndBlocksBothModes`, missing Start result gate test | PASS |
| 15 | INVOKED + exception giữ uncertainty | Invocation/exception test, cancellation test, missing/exceptional Start test | PASS |
| 16 | Missing callback/result giữ uncertainty | `missingStartCallbackAndMissingCleanupResultRemainBlockedAfterDispose` | PASS |
| 17 | Exact PreflightRejected qua normal result | Gate và controller `exactPreflight...` tests | PASS |
| 18 | Hung Start + bounded incomplete cleanup → blocked | `task3gTerminatedRunnerCacheFeedsBlockedProductAndLateStartStaysFenced` | PASS |
| 19 | Local detach/actor termination không đổi blocked evidence | Cùng integration: root completed, factory/scope cleared, status INCOMPLETE/CLEANUP_TIMEOUT giữ nguyên | PASS |
| 20 | Late Start sau blocked không activate/release | Gate blocked snapshot test và product/runner integration late ACTIVE/Started | PASS |
| 21 | Status dùng được sau bỏ controller/runtime | `applicationStatusSurvivesDroppedControllerAndExecutionReferences` | PASS |
| 22 | Application status graph thuần giá trị | `copiedStatusCannotBeMutatedThroughRuntimeListsAndHasOnlyImmutableValues`, recursive `assertValueGraph` | PASS |

## Hung Start và Task 3G integration

Product integration dùng runner thật, session port không trả ACTIVE/terminal cleanup. Back đi vào STOPPING ngay, đúng một Close; sau cleanup timeout 20 giây, runner trả cached `CleanupIncomplete`, actor/root hoàn tất, `runnerScope`/factory và notification consumer được bỏ. Gate giữ token, cleanup ID, `CLEANUP_BLOCKED`, INCOMPLETE và failure code `CLEANUP_TIMEOUT`. Controller bỏ execution/readiness closure; pending Start waiter được giải quyết/cancel.

Sau đó `runner.stop()` vẫn trả cùng cached terminal object, không cần actor/session sống. Inject late ACTIVE và late product Started không đổi cached result hoặc product status; Classic/Embedded mới tiếp tục bị khóa.

Regression Task 3G dùng manager/session/production adapter thật với fake Binder hung Start/Stop; bao phủ Stop, SurfaceLost và ACTIVE timeout, terminal detach, cached Stop, root termination và completion muộn. Terminal/cache regression riêng bao phủ cả PreflightRejected, Stop từ IDLE, clean, incomplete, recovery và rollback. Không sửa implementation/tests Task 3G.

## Exact Application value-state fields và reference-graph audit

Application chỉ có gate đã tồn tại, không thêm RunOwner hoặc execution DI dài hạn. Payload `ProductRunStatus` có đúng các trường:

```text
token: RunToken?                         // workspaceId: String
generation: Long
startOperationId: Long?
cleanupOperationId: Long?
invocationCategory: ProductInvocationCategory
phase: ProductRunPhase
stopRequested: Boolean
resultKind: ProductResultKind?
issue: EmbeddedProductIssue?
allocationEvidence: AllocationEvidence
cleanupEvidence: CleanupEvidence
items: List<ProductItemStatus>
cleanupOutcomes: List<ProductCleanupStatus>
```

`ProductItemStatus`: `sourceCellId`, `sessionId`, `packageName`, `componentName` (String), `order`, `displayId` (Int), `phase` (enum). `ProductCleanupStatus`: `sourceCellId` (String), `evidence` (enum), `failureCode` (String?). Các list được copy và bọc immutable; issue dùng typed value mapper. Không giữ raw runtime result, failure object, Throwable hoặc raw exception message trong status.

Audit source và test graph: gate instance fields chỉ gồm lock, hai MutableStateFlow/read-only StateFlow, hai Long sequence và Boolean Classic dispatch; không có controller, runner, session, renderer, Surface/View/Activity, adapter, Binder, supplier/callback, Job/Deferred hoặc Flow producer capture route. `ProductExecutionValue` có private constructor và chỉ được tạo bằng projection copy; result gốc không được lưu vào gate.

Start/cleanup Deferred và disposal Job nằm ở controller thuộc route. Sau terminal, execution/readiness closure được đặt null; pending Start bị cancel khi cleanup/terminal notification hội tụ, các waiter hoàn tất và work references được bỏ. Dispose chờ cleanup rồi cancel route scope. Application status vẫn đọc được khi controller/runtime đã bỏ; không cần giữ handle để hiển thị CLEANUP_BLOCKED.

Scoped diff được đối chiếu với working-tree baseline, không chỉ HEAD. SHA256 của **144 file changed/untracked có trước ngoài năm file code/test được sửa** giữ nguyên; không có tracked edit bất ngờ. Task 3D/3G, remote/AIDL/renderer, Application, license/security, database, dependency, release và các experiment/material được bảo toàn.

`git diff --check`: **PASS**. Kiểm tra whitespace riêng báo cáo mới: **PASS**.

## Giới hạn và điểm dừng

Không có blocker trong Task 3B theo targeted/affected checks và audit trên. Real-device/heap/native-resource proof và full Task 8 gate: **NOT VERIFIED**, không chạy theo scope. Authoritative cleanup của permanently hung remote Start không được tuyên bố; local detach/actor termination không trở thành clean evidence.

Không reset, stash, clean, stage, commit hoặc push. Không persistence/process-death recovery, Retry/reconcile/global cleanup, protocol redesign, Application RunOwner hoặc renderer 007 changes. **STOP AFTER TASK 3B; không bắt đầu Task 4.**
