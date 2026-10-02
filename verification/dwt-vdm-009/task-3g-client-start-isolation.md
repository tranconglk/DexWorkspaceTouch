# DWT-VDM-009 — Task 3G: Client Start Isolation + Terminal Runtime Detachment

Ngày: 2026-10-01 (Asia/Saigon). Branch: `release/1.0-beta`.

**Kết quả: implementation và targeted/affected regression PASS, chờ review/acceptance Task 3G.** Authorization lấy từ prompt EXECUTE TASK 3G ONLY, thay trạng thái PLANNED / NOT AUTHORIZED trước đó cho riêng lượt này. Task 3F APPROVED; Task 3B vẫn BLOCKED cho đến khi Task 3G được review và accepted; Tasks 4–9 NOT AUTHORIZED. Không tiếp tục Task 3B.

## File thay đổi trong Task 3G

Production:

- [EmbeddedStartIpcTask.kt](../../app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedapp/EmbeddedStartIpcTask.kt): standalone Start transport và value completion.
- [EmbeddedAppSession.kt](../../app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedapp/EmbeddedAppSession.kt): Start lane riêng, detachable completion, operation identity, Close không suy clean từ `startedRemotely == false`.
- [EmbeddedAppSessionState.kt](../../app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedapp/EmbeddedAppSessionState.kt): thêm bounded Start lane.
- [EmbeddedAppServiceConnectionManager.kt](../../app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedapp/EmbeddedAppServiceConnectionManager.kt): bookkeeping theo operation, giữ pending Start evidence khi Stop hoàn tất.
- [EmbeddedWorkspaceRunner.kt](../../app/src/main/java/com/trancong/dexworkspacetouch/workspace/execution/embedded/runtime/EmbeddedWorkspaceRunner.kt): coordinated admission, terminal materialization/detachment/shutdown, cached APIs.

Tests:

- [EmbeddedAppStartIsolationTest.kt](../../app/src/test/java/com/trancong/dexworkspacetouch/feature/embeddedapp/EmbeddedAppStartIsolationTest.kt) — mới.
- [EmbeddedWorkspaceHungStartIntegrationTest.kt](../../app/src/test/java/com/trancong/dexworkspacetouch/workspace/execution/embedded/runtime/EmbeddedWorkspaceHungStartIntegrationTest.kt) — mới.
- [EmbeddedAppServiceConnectionManagerTest.kt](../../app/src/test/java/com/trancong/dexworkspacetouch/feature/embeddedapp/EmbeddedAppServiceConnectionManagerTest.kt) — thêm overlap/identity cases.
- [EmbeddedWorkspaceRunnerTest.kt](../../app/src/test/java/com/trancong/dexworkspacetouch/workspace/execution/embedded/runtime/EmbeddedWorkspaceRunnerTest.kt) — thêm terminal graph/jobs/cache/admission cases.

Task 3D working-tree changes được giữ làm baseline, không reset/replace. Adapter/port và các Task-3D session/integration tests không chỉnh sửa. Không đổi scope amendment, plan, báo cáo 3E, remote/AIDL, renderer 007, product gate/controller, license/security, database, dependency hoặc release/signing.

## RED và GREEN

RED trước production:

- `completingStopCannotErasePendingStartOrAuthorizeAbsence`: FAIL, service removal count thực tế 1 thay vì 0 khi Start còn pending.
- `every true terminal clears runtime graph and ends root job while cache remains usable`: FAIL, runner root chưa terminate.
- `queued Stop SurfaceLost duplicate Start and Touch all resolve across terminal admission`: FAIL, runner root chưa terminate.
- Nhóm Start/identity/integration mới: RED tại compile test vì chưa có `startLane`, `startCompletionExecutor`, `StartCompletion` và exact `operation` completion API. Không gọi compile failure này là behavioral proof. Behavioral RED của manager/runner ở trên đã được quan sát riêng.

Lần chạy sandbox đầu không tới tests vì không resolve Android Gradle plugin; chạy lại đúng command với quyền cache/SDK đã có tạo RED thực tế. Không đổi dependency.

GREEN targeted: **11/11 PASS** (5 Start isolation, 1 hung-Start integration bao phủ ba trigger, 2 manager overlap/identity, 3 runner terminal/admission). Sau sửa nullable warning trong test, affected regression sau đây PASS; không có production edit tiếp theo.

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests '*.EmbeddedAppServiceConnectionManagerTest' --tests '*.EmbeddedAppSessionBoundaryTest' --tests '*.EmbeddedAppStartIsolationTest' --tests '*.AndroidEmbeddedWorkspaceSessionAdapterTest' --tests '*.EmbeddedWorkspaceRunnerTest' --tests '*.EmbeddedWorkspaceConnectionBoundaryIntegrationTest' --tests '*.EmbeddedWorkspaceHungStartIntegrationTest' --tests '*.EmbeddedAppLifecycleCoordinatorTest' --tests '*.EmbeddedAppSessionStateTest' --tests '*.EmbeddedAppSessionPolicyTest' --tests '*.SessionStopCoordinatorTest' --tests '*.SessionConnectionCoordinatorTest' --tests '*.EmbeddedWorkspacePreflightTest' --console=plain
```

**BUILD SUCCESSFUL; 114 tests, 0 failures, 0 errors, 0 skipped** theo XML results:

| Lớp test | PASS |
| --- | ---: |
| EmbeddedAppServiceConnectionManagerTest | 27 |
| EmbeddedAppSessionBoundaryTest | 11 |
| EmbeddedAppStartIsolationTest | 5 |
| AndroidEmbeddedWorkspaceSessionAdapterTest | 11 |
| EmbeddedWorkspaceRunnerTest | 19 |
| EmbeddedWorkspaceConnectionBoundaryIntegrationTest | 6 |
| EmbeddedWorkspaceHungStartIntegrationTest | 1 |
| EmbeddedAppLifecycleCoordinatorTest | 10 |
| EmbeddedAppSessionStateTest | 4 |
| EmbeddedAppSessionPolicyTest | 2 |
| SessionStopCoordinatorTest | 1 |
| SessionConnectionCoordinatorTest | 6 |
| EmbeddedWorkspacePreflightTest | 11 |

Task 3D regression: 11 session-boundary và 6 connection-boundary integration cases PASS, cùng manager/adapter regression; UID/connect vẫn isolate, không stale READY sau detach. 006 client regression giữ sequential startup, reverse rollback, receipts/partial receipt/primary failure, one logical cleanup, READY 10s / ACTIVE 30s / cleanup 20s mỗi item, REMOTE_DIED distinction và host Surface ownership. Không chạy product Task-3B implementation tests, Task 8 full gate hoặc device.

## Start transport và false-clean prevention

`EmbeddedConnectionLanes.start`: `ThreadPoolExecutor` cố định **2 worker, SynchronousQueue (0 queue), AbortPolicy**, không caller-runs/retry. Tách khỏi UID verifier, runner/Main, Stop/Close worker và manager finalization. Hung IPC có thể chiếm slot vô hạn; saturation trả `START_CAPACITY_EXHAUSTED` ngay, không gọi Binder và bỏ operation chưa invoke.

`javap -p -c` trên compiled `StartIpcTask.class` xác nhận đúng 10 instance fields:

`service`, `sessionId`, `packageName`, `componentName`, `width`, `height`, `densityDpi`, `surface`, `operation`, `destination`.

Chỉ giữ exact Binder/proxy, copied strings/scalars, required raw Surface và detachable mailbox. Không có synthetic outer/session field, lease/manager, execution wrapper, runner/controller/renderer, Activity/View/Compose/callback hoặc Throwable field. Bytecode gọi Binder trước khi truy cập mailbox `offer`; không load consumer qua Binder call. Completion copy Bundle thành Boolean/Int/Long/bounded String; exception thành fixed error value. Production completion executor là shared bounded notification lane, không closure capture route; consumer là điểm liên kết tới session và được detach ở terminal.

Manager giữ `operationId -> sessionId` và non-terminal session IDs thuần giá trị. Mỗi operation có sequence riêng; finish chỉ xóa exact operation. Stop B success/failure không xóa Start A pending hoặc clear non-terminal authority. Duplicate/wrong-session completion bị từ chối; legacy không-ID completion chỉ được chấp nhận khi duy nhất một operation của session đang pending. Reconcile từ chối trước query nếu còn in-flight ở admission; nếu inventory thay đổi sau snapshot thì revision/in-flight guards từ chối acceptance. Release lease không đủ để remove service. Nếu Start completion đã detach, manager có thể giữ pending evidence vô hạn, kể cả Binder về sau trả lời.

Session record may-allocate trước Start admission. Close dùng `startSubmitted` cùng accepted cleanup evidence, không dùng `!startedRemotely` làm proof. Close-alone với Start đã submit nhưng chưa reply được test là CLEANUP_INCOMPLETE, không STOPPED/removal. Stop/Close không queue sau client Start; remote Stop vẫn có thể hung và runner timeout không đổi thành clean.

## Runner terminal và admission evidence

Bao phủ **PreflightRejected, Stop từ IDLE, ordinary Stopped, CleanupIncomplete, RecoveryRequired, Start failure + rollback**. ACTIVE/Started không terminate runtime.

Thứ tự: materialize receipt/outcome/partial evidence → publish cache + close admission dưới cùng local lock → detach observers → complete Start/waiters → drain/drop queued commands với replies → clear holders/dependencies → cancel timers/root. Không chạy Binder/handle detach hoặc complete replies dưới admission lock.

Deterministic tests kiểm tra prepared/owned maps, Surface/session holder paths, startup/cleanup IDs/order/outcome lists, startReply/waiters, preflight/factory/scope và timer fields đã clear; channel closed và không payload Start/Surface còn queued. Hook detach xác nhận copied terminal result tồn tại trước holder clear. Saved root Job `isCompleted`, không children, nên actor và timers không còn orchestration; runner không parent vào route scope để shortcut cleanup.

Stop/SurfaceLost/duplicate Start/Touch queued trước terminal đều có reply; caller từ bốn thread trong lúc detach vẫn đang chạy nhận cached result/rejection và không cần actor. Không command nào reopen runtime. Sau terminal: Stop và SurfaceLost trả **cùng cached result object**; Start trả DuplicateCall; known-source touch trả NOT_ACTIVE, unknown source trả UNKNOWN_SOURCE bằng value-only source IDs.

## Hung-Start integration và PASS criteria

Real manager/session/production adapter/runner với fake Binder: Start và remote Stop đều bị latch giữ. Ba trigger **Stop, SurfaceLost, ACTIVE timeout** tiến tới local terminal sau cleanup 20s, trong khi Binder vẫn blocked. Outcomes giữ INCOMPLETE/uncertain, holders/callback consumer detach và root terminate. Release latch sau đó không ACTIVE/READY, không next-item start, không observer delivery, không cache mutation hoặc service removal. Queued late success value cũng bị drop sau detach. Không có product/gate release callback trong Start task/completion.

| Prerequisite trước Task 3B | Kết quả |
| --- | --- |
| Hung Start không giữ session/route/runtime callback qua task/mailbox sau detach | PASS — fields/bytecode + detached consumer assertions |
| Local terminal Incomplete/Uncertain detach route graph | PASS — adapter/session notification detach và runner holder/dependency clear |
| Runner terminate, chỉ cached value result/metadata còn cần giữ | PASS — root/children/timer/channel/field assertions và cached APIs |
| Late Start không mutate cache/revive run/retroactively authorize release | PASS — detached completion, late value/snapshot/Binder integration; product gate không sửa |

`git diff --check`: PASS; scoped baseline và untracked code/test whitespace: PASS. Scoped diff đã review với bản working-tree trước Task 3G, không chỉ HEAD. SHA256 của 29 existing files ngoài allowed edits giữ nguyên; remote/AIDL/renderer diff rỗng. Không stage/commit/push, reset/stash/clean.

## NOT VERIFIED và điểm dừng

Remote/native/vendor Surface/resource lifetime, permanently hung remote Start authoritative cleanup, real-device Binder scheduling/heap, device proof và full Task 8 gate: **NOT VERIFIED**. Các giới hạn này không được suy thành clean; unresolved ownership vẫn phải CLEANUP_BLOCKED theo product boundary đã duyệt. Task 3G không triển khai product gate/controller Task 3B hay remote protocol.

Không có blocker trong client-side Task 3G theo checks đã chạy. Dừng để review/acceptance Task 3G; Task 3B vẫn BLOCKED, Tasks 4–9 NOT AUTHORIZED.
