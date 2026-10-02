# DWT-VDM-009 — Scope Amendment: Route-scoped Runtime / Fail-closed Hung Start (Task 3F)

**Ngày amendment:** 2026-10-01; **đóng tài liệu:** 2026-10-02 (Asia/Saigon). **Trạng thái cuối: DWT-VDM-009 = COMPLETE / PASS / CLOSED.** Tasks 1–2 COMPLETE / targeted PASS; Task 3D COMPLETE / PASS; Task 3G, Task 3B và Tasks 4–9 COMPLETE / PASS / ACCEPTED. Task 3F APPROVED. Task 3/3A/3E là các checkpoint kiến trúc lịch sử, không phải công việc đang chờ.

Final verification: [Task 8 — local gate PASS](../../../verification/dwt-vdm-009/local-gate.md) và [Task 9 — S23/DeX production-signed device proof PASS](../../../verification/dwt-vdm-009/s23-hardening-proof.md). [Final review](../../../verification/dwt-vdm-009/final-review.md) ghi acceptance và giới hạn còn lại. Không tạo Task 10 hoặc mở milestone mới.

## Quyết định và hiệu lực

Theo [009R-D đã được duyệt](2026-10-01-dwt-vdm-009r-d-process-death-recovery-decision.md), nhánh recovery đã đóng về mặt kiến trúc với `DWT-VDM-009R = BLOCKED_BY_PLATFORM_BOUNDARY`. 009 chỉ hardening trong cùng DWT process; không mở thêm milestone/spec recovery.

Amendment này thay thế scope, contract process restart và acceptance recovery cũ trong [spec 009](2026-10-01-dwt-vdm-009-embedded-product-hardening-design.md) và [plan 009 đã cập nhật](../plans/2026-10-01-dwt-vdm-009-embedded-product-hardening.md). Chi tiết hardening không xung đột trong spec cũ vẫn là tài liệu tham chiếu; yêu cầu Task 0/Branch A/B, journal và block Classic sau mọi restart bất định chỉ còn là lịch sử. 009R-D giữ thẩm quyền về boundary recovery.

Task 3S bỏ yêu cầu Application sở hữu execution/cleanup handle độc lập UI và thay bằng runtime thuộc route, Application chỉ giữ dữ liệu giá trị. Không tạo `EmbeddedProductRunOwner`. [Báo cáo Task 3A đã được chấp nhận](2026-10-01-dwt-vdm-009-task3-ui-independent-cleanup-boundary.md) được giữ nguyên: kết luận BLOCKED áp dụng cho guarantee Task 3 cũ. Authorization và acceptance Task 3G/3B cùng Tasks 4–9 đã được cấp trong các lượt thực hiện sau amendment. Closeout này chỉ sửa tài liệu; không sửa production/tests, không build/test/device, reset/stash/clean/stage/commit/push.

Task 3F bổ sung ranh giới fail-closed cho hung Start. [Task 3E COMPLETE / APPROVED](2026-10-01-dwt-vdm-009-task3e-start-terminal-boundary.md) giữ nguyên làm bằng chứng vì sao authoritative cleanup của Start IPC đã pending/hung cần một runtime milestone riêng. DWT-VDM-009 **không redesign `RemoteSessionRuntime` / `RemoteSessionRegistry` / UserService protocol chỉ để bảo đảm Stop dọn Start đó có thẩm quyền**. Amendment hiện hành thay mọi yêu cầu tương lai xung đột về authoritative hung-Start cleanup bằng contract bên dưới; các candidate remote redesign trong báo cáo 3E chỉ là lịch sử phân tích, không trở thành scope hoặc prerequisite implementation của 009.

## IN SCOPE

- Typed product errors và action policy theo allocation/cleanup evidence.
- Shizuku UX / refresh trước Start, không cấp phát hoặc tự Start.
- Diagnostics typed, có provenance; copy bằng thao tác người dùng.
- Proof-route cleanup ở UI thường; giữ route và guard khi có product ownership.
- Application-scoped gate/serialization và status **thuần giá trị trong cùng process**; runtime/execution/UI vẫn thuộc route/Activity tạo run.
- Activity/route recreation: host cũ fail closed và hội tụ cleanup của chính nó một lần; host mới chỉ quan sát gate/status, không dựng lại controller hoặc gắn Surface mới vào run cũ.
- Double Start / Start+Back / double Back hội tụ về một Start và một cleanup operation.
- `CLEANUP_BLOCKED` UX trong cùng process, kể cả khi rời route hoặc Activity recreation.
- Stale callback / generation protection; callback cũ không đổi hoặc nhả run mới.
- Classic safety khi ownership của run trong process hiện tại chưa được giải quyết.

## OUT OF SCOPE

- Process-death recovery.
- Cross-process ownership reconstruction.
- Durable recovery journal hoặc persistence giả lập ownership evidence.
- Pending/commit protocol.
- Recovery enrollment.
- Cross-process reconcile.
- Authoritative cleanup sau main-process restart.
- Global scan/cleanup tài nguyên platform; sửa AIDL hoặc runtime 006 để tiếp tục recovery.
- Remote pre-reserve cancellation/tombstone protocol; concurrent Start/Stop remote operation identities.
- Rewrite monitor model của `RemoteSessionRuntime`; authoritative cleanup của permanently hung remote Start.
- AIDL cancellation protocol; UserService kill/global cleanup.

## Product contract

Nếu DWT process chết trong một Embedded run:

- DWT không tuyên bố authoritative recovery sau restart.
- DWT không suy rằng cleanup trước đó đã thành công; gate mới `IDLE`, process/root biến mất hoặc count rỗng không phải clean evidence.
- DWT không persist ownership evidence giả để tái dựng gate cũ.
- DWT không quét hoặc dọn toàn cục tài nguyên platform.
- Sự kiện này vẫn là **UNSUPPORTED RECOVERY CASE** của Embedded **Thử nghiệm**, không phải PASS hoặc “đã sạch”.

Không dùng lại contract “Classic phải bị block sau mọi process restart bất định”. Với run còn token/evidence **trong cùng process**, giữ nguyên năm pha `IDLE`, `STARTING`, `ACTIVE`, `STOPPING`, `CLEANUP_BLOCKED`. Chỉ nhả gate theo no-invocation contract bên dưới hoặc kết quả no-allocation/clean có thẩm quyền cho toàn bộ owned set của đúng run. Route disposal, refresh và callback cũ không tự nhả gate; không thêm cleanup Retry/product reconcile. `RemoteDied` của UserService khi DWT còn sống vẫn được xử lý theo result 006 và evidence của run hiện tại.

### Fail-closed hung-Start boundary

Nếu Embedded Start đã vượt invocation boundary và không thu được authoritative cleanup trong bounded product cleanup window hiện có, local terminal result là **INCOMPLETE / UNCERTAIN** và gate trở thành/giữ **`CLEANUP_BLOCKED`**:

- Không tuyên bố clean, không gọi `releaseWithoutAllocation`, không cho Classic hoặc một Embedded run khác.
- Không Retry/reconcile/global-clean; không suy safety từ `UNKNOWN_SESSION`, empty inventory, Binder death, timeout hoặc local detach.
- Remote Start có thể vẫn pending và về lý thuyết cấp phát sau terminal local. Chính uncertainty đó buộc gate giữ `CLEANUP_BLOCKED`; point-in-time absence không chứng minh không còn allocation tương lai.

009 không yêu cầu permanently hung Start phải clean có thẩm quyền để đạt product boundary này. Local terminal và release graph không phải remote cleanup acknowledgement; không cần terminate hung Binder transaction. CLEANUP_BLOCKED chỉ giữ evidence giá trị trong cùng process, không mở process-death recovery.

Các giới hạn còn lại giữ nguyên: Classic mặc định và không phụ thuộc Shizuku; Embedded phải chọn tường minh, không silent fallback; cap 1–2 app; không rewrite hoặc mở rộng capability của engine 006/007; chỉ cho phép các thay đổi client-side lifetime/reference/admission được Task 3G cấp phép rõ ràng, không đổi ownership/result authority và không đổi renderer 007 semantics; không đổi Classic launcher/`ActivityOptions`, Room/schema, license/security, signing/release, production URL/trusted key hoặc dependency.

## Ownership hiện hành

Application product scope chỉ được giữ:

- `EmbeddedProductRunGate`, `RunToken` identity, generation tăng đơn điệu trong process và operation identity/sequence.
- Product phase do gate quyết định có thẩm quyền; typed issue/evidence/status bất biến và diagnostics bất biến, chỉ chứa giá trị.

Application product state không được giữ trực tiếp hoặc gián tiếp `EmbeddedWorkspaceProductController`, `EmbeddedWorkspaceRunner`, `EmbeddedWorkspaceRunnerController`, `EmbeddedAppSession` handle, renderer coordinator, Surface/View/Activity, cleanup lambda/function reference capture route/controller, hoặc Job/Deferred có closure capture runtime/UI. Không thêm RunOwner, wrapper hay recovery handle dài hạn để giữ graph đó.

Renderer, controller, execution adapter và Surface lifecycle thuộc route/Activity đã tạo run. Khi host dispose trong `STARTING/ACTIVE/STOPPING`, controller cũ ghi exit/stop intent một lần và các caller join cùng cleanup operation. Gate vẫn là nguồn quyết định serialization; callback phải khớp token, generation và operation. Host mới không được tạo controller thứ hai cho token cũ, attach Surface hoặc tiếp tục execution cũ.

## Bounded UI retention và terminal result

Quy tắc cũ “Application không bao giờ được transitively giữ UI ngay cả trong cleanup” không còn là guarantee hiện hành. Product state dài hạn của Application phải độc lập UI. Ngoại lệ duy nhất là cleanup coroutine đã tồn tại của host cũ có thể tạm giữ controller/Surface graph trong **một operation cleanup có terminal hữu hạn**; đây không phải cleanup độc lập UI.

Ngoại lệ không cho phép lưu Job/Deferred/cleanup closure trong một Application RunOwner mới, đưa renderer/Surface vào product state, tái sử dụng operation bởi host mới, giữ route sống giả tạo hoặc biến nó thành recovery handle vô hạn. Sau terminal, cleanup scope cũ phải kết thúc, bỏ waiter/callback tới route và giải phóng graph route; gate chỉ nhận bản sao dữ liệu giá trị.

| Terminal result của đúng run | Gate và lifetime |
| --- | --- |
| Authoritative clean / no-allocation result | Gate trở lại `IDLE`; cleanup scope cũ kết thúc; có thể tạo run mới. |
| Incomplete / uncertain | Gate giữ `CLEANUP_BLOCKED` cùng typed cause/evidence/status; cleanup scope cũ vẫn kết thúc và có thể bỏ execution/UI graph. Không cần live execution handle, không Retry hay dựng lại controller. |

**Evidence lịch sử trước Task 3G/3B, đối chiếu source trong Task 3S:** Các mô tả mutex/reference bên dưới phản ánh checkpoint lúc đó; không phải kết luận về implementation đã accepted. Evidence hiện hành là báo cáo Task 3G/3B và architecture audit Task 8.

- [Product screen](../../../app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/product/EmbeddedWorkspaceProductScreen.kt) truyền [timeout policy](../../../app/src/main/java/com/trancong/dexworkspacetouch/workspace/execution/embedded/runtime/EmbeddedWorkspaceRuntimeModels.kt) `PROOF_EMBEDDED_WORKSPACE_TIMEOUTS`: READY 10 giây, ACTIVE 30 giây, cleanup 20 giây **mỗi owned item**.
- [Runner](../../../app/src/main/java/com/trancong/dexworkspacetouch/workspace/execution/embedded/runtime/EmbeddedWorkspaceRunner.kt) `beginCleanup()` chốt owned order đảo ngược; `cleanupNext()` lập timer rồi request `stop()/close()`. Terminal snapshot hoặc `handleCleanupTimeout()` tiến sang item kế tiếp. Timeout tạo `Incomplete(CLEANUP_TIMEOUT)` hoặc `RecoveryRequired`, không tạo clean evidence. Với cap 1–2 app, ngân sách chờ cleanup tối đa 20/40 giây sau `beginCleanup()`, cộng độ trễ dispatch/scheduling; đây không phải deadline 40 giây từ host disposal.
- `finishCleanup()` lưu terminal result, hoàn tất Start reply và terminal waiters, hủy cleanup timer và xóa waiter list. [Product controller](../../../app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/product/EmbeddedWorkspaceProductController.kt) `onHostDisposed()` gọi `requestExit()` rồi `appRunScope.cancel()` trong `finally`. Tại checkpoint Task 3S, `requestExit()` có thể chờ mutex của Start; READY/ACTIVE timers cũng chặn phần chờ startup, không được bỏ qua khi chứng minh lifetime.

Cơ chế tại checkpoint này giới hạn **chờ terminal result** của cleanup; không chứng minh synchronous Binder IPC đã kết thúc hay mọi UI reference được gỡ. Task 3A đã chỉ ra runner scope riêng, prepared/owned references và session worker có thể còn sau timeout; `finishCleanup()` không phải memory-detach acknowledgement. Task 3E giải thích blocker authoritative hung-Start cleanup; Task 3F giữ remote ownership uncertain theo fail-closed boundary, không yêu cầu chờ remote convergence vô hạn. Lifetime prerequisite đã được Task 3G chứng minh trong phạm vi client/source/local tests; Task 3B sau đó COMPLETE / PASS / ACCEPTED. Không suy heap/GC hoặc native/vendor Surface lifetime PASS từ amendment hay local audit.

### Local lifetime khi remote ownership uncertain

Local UI lifetime vẫn phải kết thúc dù remote Start còn hung. Implementation Task 3G đã isolate Start IPC để task treo chỉ giữ transport/value objects bắt buộc cho IPC, không giữ trực tiếp hoặc gián tiếp `EmbeddedAppSession`, runner, controller, renderer, Activity/View/Compose state hoặc route callbacks. Raw Surface/transport cần cho IPC không được kéo theo graph route; không dùng local detach làm clean evidence và không yêu cầu transaction Binder treo tự kết thúc.

Sau khi materialize bản sao terminal result/evidence đầy đủ:

1. Detach notification consumers và complete route waiters bằng terminal value.
2. Clear runner prepared/owned Surface và session holders sau evidence copy; không thay host ownership bằng việc release Surface để giả cleanup.
3. Đóng runner admission có phối hợp, giải quyết pending replies/payloads, kết thúc actor/timers; giữ cached terminal result thuần giá trị cho fast path.
4. Giải phóng route/controller graph; Application gate/status chỉ giữ bản sao value-only `CLEANUP_BLOCKED` evidence. Late Start completion bị drop, không đổi cached result/gate hoặc revive run.

Contract này đã được thực hiện trong [Task 3G — Client Start Isolation + Terminal Runtime Detachment](../../../verification/dwt-vdm-009/task-3g-client-start-isolation.md), **COMPLETE / PASS / ACCEPTED**: immutable/detachable Start IPC task không capture session/route; terminal copy → detach → clear Surface/session holders; coordinated runner admission shutdown/actor termination; cached terminal result fast path; drop late Start completion; unresolved remote ownership vẫn `CLEANUP_BLOCKED`. Không thêm remote cleanup protocol. Evidence client/local không phải authoritative remote cleanup hoặc heap/GC proof.

## Recreation contract

Recreation nghĩa là **host cũ fail closed, hội tụ cleanup của chính nó; host mới chỉ quan sát product gate/status và không resurrect run cũ**. Không còn contract “Application owner tiếp tục execution cũ”.

| Gate khi host mới xuất hiện | Hành vi host mới |
| --- | --- |
| `STARTING/ACTIVE/STOPPING` của host cũ | Hiện trạng thái previous-run/cleanup trong process; quan sát value-only state và chờ terminal transition. Không tạo replacement controller, attach Surface, cấp token mới hoặc dispatch Classic/Embedded xung đột. |
| `CLEANUP_BLOCKED` | Hiện typed cause/evidence/support UI từ dữ liệu Application; không cleanup Retry, không tái tạo execution/controller. |
| `IDLE` | Cho route/run mới theo gate bình thường; trong cùng process, token cũ chỉ về IDLE theo evidence có thẩm quyền. |

## Invocation contract cho `releaseWithoutAllocation`

Task 3B đã thay nullable “null nghĩa là không allocation” bằng hai loại trạng thái tường minh, gắn đúng token/generation/start operation:

| Invocation category | Release authority |
| --- | --- |
| `NOT_INVOKED / PRE-RUNNER_REJECTED` | Chứng minh chưa vượt runner invocation boundary và không có execution cũ còn owned; chỉ loại này được gọi `releaseWithoutAllocation`. |
| `INVOKED / RESULT_OR_UNCERTAIN` | Đánh dấu trước khi vượt invocation boundary. `null`, exception hoặc thiếu result không chứng minh no-allocation; giữ fail closed. Exact-run `PreflightRejected` hoặc authoritative clean result nhả gate qua normal result handling. |

Hung/pending Start sau invocation thuộc `INVOKED / RESULT_OR_UNCERTAIN`: terminal timeout/detach không được đổi category thành `NOT_INVOKED`, gọi `releaseWithoutAllocation` hoặc nhả `CLEANUP_BLOCKED` từ completion muộn. [Task 3B — gate operation ordering](../../../verification/dwt-vdm-009/task-3b-gate-operation-ordering.md) **COMPLETE / PASS / ACCEPTED**; Task 3F trước đó chỉ sửa tài liệu.

## Plan và verification

Plan 009 giữ kết quả Tasks 1–2 và Task 3D evidence. Checkpoint lịch sử: Task 3 `BLOCKED_BY_RUNTIME_OWNERSHIP_MODEL`, Task 3A COMPLETE, Task 3E COMPLETE / APPROVED với quyết định `BLOCKED_REQUIRES_RUNTIME_PROTOCOL_REDESIGN` cho authoritative permanently-hung remote Start cleanup; Task 3F APPROVED. Không sửa kết luận các báo cáo này hoặc coi remote redesign là prerequisite của 009.

Task 3G **COMPLETE / PASS / ACCEPTED** đã đáp ứng client-side prerequisite: (1) hung Start IPC không giữ route/runtime callbacks; (2) local terminal Incomplete/Uncertain detach được route graph; (3) runner kết thúc và chỉ giữ cached value result; (4) stale Start completion không đổi `CLEANUP_BLOCKED` hoặc revive run. Task 3B **COMPLETE / PASS / ACCEPTED** xử lý late Started sau stop intent, stale token/generation/cleanup callbacks, double Start một invocation, Start+Back một cleanup intent/operation, double Back join operation đó và typed status/evidence cho UI mới. Không tạo RunOwner, giữ runtime handle trong Application hoặc triển khai remote cleanup protocol.

[Task 4 — route lifecycle](../../../verification/dwt-vdm-009/task-4-route-lifecycle.md), [Task 5 — blocked UX](../../../verification/dwt-vdm-009/task-5-cleanup-blocked-ux.md), [Task 6 — typed diagnostics](../../../verification/dwt-vdm-009/task-6-typed-diagnostics.md) và [Task 7 — proof access](../../../verification/dwt-vdm-009/task-7-proof-access.md) đều **COMPLETE / PASS / ACCEPTED**. Host cũ cleanup một lần; host mới chỉ quan sát value state, không reattach/controller/token cho old non-IDLE run. Blocked UX và diagnostics không giữ runtime graph; proof routes còn và có guard. Task 8/9 là hai gate cuối, không phụ thuộc RunOwner.

Task 0/Branch A/B không là prerequisite hoặc acceptance. Task 0 cũ cùng evidence 009R-I/009R-A được giữ nguyên làm lịch sử; Task 0B không chạy. Các test gate/lifecycle/blocked đều giới hạn trong cùng process; không tạo `RecoveryCheckRequired` hoặc test journal/crash-recovery cho scope này.

**Task 8 — COMPLETE / PASS / ACCEPTED:** focused 477 tests và full local gate 1159 tests PASS; `assembleDebug` PASS; `lintDebug` PASS (0 errors, 46 warnings); architecture audit, forbidden-scope audit và security sanity PASS. **Task 9 — COMPLETE / PASS / ACCEPTED:** production-config/production-signed workflow, same-signer in-place install, artifact/readback hash và signer match; S23 Ultra/DeX Shizuku unavailable → Ready không restart DWT, explicit Start → STARTING → ACTIVE, Back → STOPPED / CLEAN_CONFIRMED / IDLE, explicit clipboard copy và Home/developer presentation PASS. Hai báo cáo final gate được giữ nguyên, không chạy lại trong closeout.

Acceptance đã đóng theo evidence client/source/unit/integration và kịch bản device nhỏ: typed errors/actions đúng; refresh zero allocation; gate/value-only blocked status qua host observation; một cleanup operation với local terminal hữu hạn; client detachment và cached value result; host mới/late Start không resurrect old run hoặc nhả `CLEANUP_BLOCKED`; thao tác lặp/stale callback an toàn; Classic/Embedded mới bị chặn khi current-process token unresolved; diagnostics có provenance và proof routes có guard. Không suy forced Activity recreation hoặc deliberately blocked cleanup trên device từ normal Back proof.

**Giới hạn còn lại — NOT VERIFIED / OUT OF SCOPE:** forced Activity recreation device proof; deliberate `CLEANUP_BLOCKED` failure injection trên device; native/vendor Surface lifetime; heap/GC proof; authoritative cleanup của permanently hung remote Start; process-death recovery. Các giới hạn này ngoài acceptance đã đóng, không mở lại DWT-VDM-009 và không tạo Task 10.

**Verification lịch sử Task 3S/3F — chỉ tài liệu:** links/whitespace và ownership/invocation/recreation/status consistency; bỏ Application-owned cleanup guarantee vô điều kiện và mandatory authoritative hung-Start cleanup; giữ `CLEANUP_BLOCKED` fail closed, không RunOwner, remote redesign/process-death recovery OUT OF SCOPE. Tại checkpoint 3F local detachment chưa được chứng minh; implementation/evidence sau đó thuộc Task 3G/3B. Task 3F đã APPROVED. Closeout cuối chỉ kiểm tra links, whitespace, scoped documentation diff và `git diff --check`; không sửa production/tests hoặc chạy lại build/test/device.

## Trạng thái milestone

| Hạng mục | Trạng thái |
| --- | --- |
| DWT-VDM-006 | CLOSED / PASS — giữ kết quả đã đóng |
| DWT-VDM-007 | CLOSED / PASS — giữ kết quả đã đóng |
| DWT-VDM-008 | CLOSED / PASS — giữ kết quả đã đóng |
| DWT-VDM-009 | COMPLETE / PASS / CLOSED |
| Task 1–2 | COMPLETE / targeted PASS — giữ evidence cũ, không chạy lại |
| Task 3 | BLOCKED_BY_RUNTIME_OWNERSHIP_MODEL — checkpoint lịch sử; implementation cũ không còn được yêu cầu |
| Task 3A | COMPLETE — checkpoint kiến trúc lịch sử; report accepted, giữ nguyên báo cáo |
| Task 3B | COMPLETE / PASS / ACCEPTED — gate generation/operation ordering |
| Task 3D | COMPLETE / PASS — connection-readiness boundary, giữ evidence; không mở rộng sang Start |
| Task 3E | COMPLETE / APPROVED — checkpoint lịch sử; `BLOCKED_REQUIRES_RUNTIME_PROTOCOL_REDESIGN` cho authoritative permanently-hung remote Start cleanup; giữ nguyên báo cáo |
| Task 3F | APPROVED — checkpoint amendment lịch sử |
| Task 3G | COMPLETE / PASS / ACCEPTED — Client Start Isolation + Terminal Runtime Detachment |
| Task 4 | COMPLETE / PASS / ACCEPTED — route lifecycle |
| Task 5 | COMPLETE / PASS / ACCEPTED — CLEANUP_BLOCKED UX |
| Task 6 | COMPLETE / PASS / ACCEPTED — typed diagnostics |
| Task 7 | COMPLETE / PASS / ACCEPTED — proof access |
| Task 8 | COMPLETE / PASS / ACCEPTED — final local gate |
| Task 9 | COMPLETE / PASS / ACCEPTED — final S23/DeX production-signed proof |
| DWT-VDM-009R | BLOCKED_BY_PLATFORM_BOUNDARY |
| DWT-VDM-009R-I | STOPPED — giữ evidence cũ |
| DWT-VDM-009R-A | STOPPED — giữ evidence cũ |
| Task 0B | CANCELLED / NOT NEEDED |
