# DWT-VDM-010 — Điều tra device blocker, 2026-10-05

**STOP: testbed hiện tại chưa có điều kiện retest an toàn cho owned run đã bị khóa. 010 chưa PASS, chưa ready for commit.** Exact Start root cause vẫn **NOT VERIFIED**; không nâng inference khoảng 3 giây thành kết luận.

Worktree `D:/AndroidStudioProjects/DexWorkspaceTouch-VDM-010`, branch `qualification/dwt-vdm-010`, HEAD/base `a43d97bd52386f9ac1a76e2adf03ddcdb21c1c77`. 009 giữ COMPLETE / PASS / CLOSED. [Checkpoint 02/10](../device-20261002/checkpoint.md) và evidence cũ giữ nguyên.

## Những gì đã xác lập

Owned attempt cần điều tra: main PID **5253**, token identity **260968613**, generation **1**, Start operation **1**, cleanup operation **null**, source cell **calc**, session **`0bf0ecec-9e2c-4196-8edb-a076b688edb1`**. Receipt terminal FAILED/display -1; outcome START_FAILED/INCOMPLETE; product CLEANUP_BLOCKED. Waze chưa Start.

[Native log cũ](../device-20261002/calculator-waze-remote-start.txt) liên kết đúng session qua hash input name `DWT-VT-30d571a3dc825a74f33ea009`: remote PID 5463/thread 5487, association 123, VDM 114, display 171, input 631. Native touchscreen registration lúc 18:18:54.232; input close bắt đầu lúc 18:18:57.235. Native Surface được ghi valid khi create, chưa có sample tại thời điểm launch guard. Không có dòng “target launch begin”.

[Đối chiếu source + SHA-256](source-correlation.json):

- [RemoteSessionRuntime](../../../app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedapp/remote/RemoteSessionRuntime.kt) gọi input-stability wait rồi launchTarget; catch rollback và trả exception trong Start Bundle. Khoảng thời gian trên phù hợp một input wait, nhưng không chứng minh wait đã timeout, không có finger, section thay đổi, lỗi đọc/parse dumpsys hay một nguyên nhân cụ thể khác.
- [EmbeddedAppVdm](../../../app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedapp/remote/EmbeddedAppVdm.kt) kiểm `receivedSurface.isValid` **trước** dòng launch log. Vì vậy thiếu launch log cũng chưa loại trừ Surface invalid ở guard này. Hai nhánh này là ví dụ ambiguity, không phải danh sách nguyên nhân đầy đủ.
- [Start IPC](../../../app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedapp/EmbeddedStartIpcTask.kt) giữ exception tối đa 512 ký tự; [session](../../../app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedapp/EmbeddedAppSession.kt) giữ message trong FAILED snapshot. [Runner](../../../app/src/main/java/com/trancong/dexworkspacetouch/workspace/execution/embedded/runtime/EmbeddedWorkspaceRunner.kt) giữ message trong StartFailed.failure.
- [Product value mapping](../../../app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/product/EmbeddedProductRunGate.kt) chỉ đưa issue, receipts, evidence và failureCode qua Application gate. Message gốc không được giữ trong DTO; [C harness trace](../../../app/src/debug/java/com/trancong/dexworkspacetouch/qualification/EmbeddedRealProductHarnessActivity.kt) chỉ ghi DTO này. Không tìm thấy hai message marker trên trong artifact của invocation thất bại.
- FAILED là terminal trong [lifecycle coordinator](../../../app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedapp/EmbeddedAppSessionState.kt). Session close republish FAILED; runner chuyển callback cleanup FAILED thành Incomplete. Điều này giải thích kết quả gate bị khóa, **không chứng minh remote cleanup thực sự thất bại**. Không thay terminal semantics để chờ receipt muộn.
- [Registry](../../../app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedapp/remote/RemoteSessionRegistry.kt) không lưu last Start error; [getSessionState](../../../app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedapp/remote/EmbeddedAppUserService.kt) chỉ trả phase/liveResources cho session còn trong registry. Getter không phục hồi exception cũ và không thay thế authority của original run.

## Thay đổi lần continuation này

Thêm duy nhất [test diagnostic LOCAL](../../../app/src/test/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/product/EmbeddedStartFailureEvidenceQualificationTest.kt), cùng checkpoint/evidence và cập nhật pointer/STOP note testbed. Test dùng lifecycle coordinator, runner và product gate thật với **fake session**, inject hai message khác nhau, hai-cell plan nhưng chỉ calc được tạo:

1. `VDM input configuration did not settle to touchscreen=finger`;
2. `Received Surface became invalid`.

Test xác nhận hai raw failures khác nhau trong runner, nhưng toàn bộ giá trị product giữ lại giống nhau sau bỏ token identity riêng mỗi replay: exact partial calc receipt, START_FAILED/INCOMPLETE/CLEANUP_BLOCKED, một stop/close, không tạo Waze, không acquire run mới.

Đây là characterization/diagnostic replay, **không phải behavioral RED của một production defect**, không tái hiện native input timeout hoặc native Surface invalid. Compile lỗi import ban đầu đã sửa; compile failure không được gọi là RED.

Không thêm production patch, Android harness patch hoặc protocol change. Hai patch 010 đã có RED ở checkpoint trước giữ nguyên. Chưa có defect mới được chứng minh để đề xuất fix.

## Evidence separation / verification

| Loại | Kết quả và giới hạn |
| --- | --- |
| **A — LOCAL / FAKE mới** | [JUnit XML](local-start-cause-replay.xml): **1 test / 2 injected replays PASS**, 0 failures/errors/skips. Chỉ chứng minh ambiguity của archived value projection và fail-closed path. |
| A lịch sử | Full local 1.165 tests, focused 86 và qualification 6 PASS từ 02/10 vẫn giữ; không chạy lại full gate vì không đổi production/shared infrastructure. Không gọi lần chạy targeted này là full gate mới. |
| **B — ANDROID CONTROLLED** | 4/4 PASS trước đây, giới hạn artifact đã ghi trong checkpoint cũ. Không rerun, không có B evidence mới. Không nâng controlled blocked thành real remote cleanup failure. |
| **C — REAL S23/DeX** | Calculator PASS trước đây; Calculator+Waze vẫn FAIL/STOP tại calc. Không chạy Start, instrumentation, install hoặc cleanup/reconcile mới. |
| C context đọc-only mới | [Snapshot](current-device-readonly.json) ngày 05/10: Shizuku server PID 24063; process listing không có PID original host/remote. Logcat all-buffer read không có native/historical failure marker; hai dòng match chỉ là adbd ghi lệnh logcat. Đây không phải qualification run hoặc clean proof. |

Lệnh targeted được lưu trong [summary](investigation-summary.json), cùng test/source/log hashes. XML có đúng một testcase; compile/build/test hoàn tất thành công.

Uptime snapshot 656262.03 giây và boot ID hiện tại không có counterpart boot ID của lần thất bại. **Không suy reboot**, không suy release từ PID biến mất, handle close, empty inventory, timeout hoặc detach.

## Blocker và STOP boundary

Chưa có authoritative release evidence cho exact owned attempt trên. Raw Start exception không có trong archived trace; source/runtime registry không giữ một record để getter phục hồi message đó. Các process đang quan sát không cung cấp original gate/operation authority.

Gate hiện hành từ chối result khi đã CLEANUP_BLOCKED, kể cả callback muộn có operation hợp lệ. Vì vậy một read/getter sau này hoặc gate IDLE của process mới không thể được dùng để mở lại run cũ. Không bind UserService mới để tạo registry mới rồi đọc UNKNOWN_SESSION/empty counts; không gửi thêm cleanup IPC hoặc reset gate.

[Technical Autonomy Charter](../../../../DexWorkspaceTouch/docs/codex/DWT_TECHNICAL_AUTONOMY_CHARTER.md), “Mandatory STOP boundaries”, yêu cầu **“STOP before implementation if the required solution would change”**:

- **2 — “ownership authority or result authority”**: dùng new gate/PID hoặc readback ngoài original result flow làm release authority.
- **4 — “process-death recovery architecture”**: dựng lại authority/ownership của original host đã mất.
- **6 — “cross-process or global cleanup/reconcile behavior”**: bổ sung cleanup/reconcile hoặc global cleanup để tạo baseline mới.

Đây là các boundary của những phương án vượt blocker; không khẳng định đã triển khai chúng. Current user instruction còn trực tiếp cấm retry blocked run thiếu authoritative release, reconcile/retry và đổi authority. Trong scope hiện tại **chưa có quy trình executable an toàn để retest chính run này**.

## Quy trình bounded retest có điều kiện

**Các điều kiện dưới đây chưa đạt; không chạy runner hiện có ngay.**

1. Xác lập testbed đủ điều kiện bằng authoritative exact-owned result qua accepted owner/result flow. Final CLEANUP_BLOCKED của original run không có đường late-unlock; nếu authority cũ không còn, phải có quyết định testbed/architecture được phê duyệt trước khi chọn môi trường độc lập. PID mới, reboot, inventory rỗng hoặc handle close tự chúng không đáp ứng điều kiện này. Không tự reboot, kill, clear/uninstall hoặc tạo applicationId khác để vượt khóa.
2. Trước retest hợp lệ, bổ sung **diagnostic-only debug harness** giữ raw session snapshot / StartFailed.failure.message trước value mapping, kèm exact SID, cell, PID/host, token/generation/operation và timestamp. Cần đo Surface identity/validity và stage/input-window samples tại các điểm liên quan nếu raw exception vẫn chưa phân biệt được. Capture này **chưa triển khai/chưa chạy**; phải delegate existing Start và giữ route ownership, gate value-only, geometry, Binder protocol và result authority.
3. Chọn đúng hai-app method, **một invocation**, không loop/retry. Chỉ tiến sang recreation khi cả hai exact sessions ACTIVE. Fresh generation chỉ sau authoritative exact clean và explicit Start. Không rerun Calculator đơn hoặc B/full local đã hợp lệ nếu không đổi giả định.
4. Failure đầu tiên: lưu raw exception/stage samples và exact owned receipts, dừng progression. Chỉ terminal clean của đúng owned set qua đúng authority mới cho phép fresh Start; timeout hoặc instrumentation disposal không là release.
5. Nếu có defect tái hiện được theo accepted contract: tạo behavioral RED trước smallest coherent fix. Nếu fix cần đổi một semantic contract/authority/protocol, STOP architecture review.

Một testbed độc lập trong tương lai có evidence riêng; không được dùng kết quả mới để suy root cause hoặc clean của session cũ.

## Scope audit, risk và next

[Audit](scope-audit.json): base/branch đúng, không staged paths; original hai modified file hashes khớp checkpoint trước, **148 untracked quan sát** được giữ nguyên. Không đụng 009, signing/license/security/config/version/dependencies, AIDL hoặc renderer/runtime contract; không commit/push/tag.

Rủi ro còn lại: nguyên nhân Start thật chưa phân biệt; original authoritative cleanup/convergence chưa xác nhận; required hai-app lifecycle chưa qualified. Native/vendor Surface lifetime, hung Binder và process-death recovery vẫn ngoài scope.

**NOT VERIFIED:** exact remote exception/root cause; authoritative release của SID cũ; hai-app ACTIVE/recreation/fresh-run/Surface qualification.

**Next:** giữ testbed này STOP. Nếu tìm được original raw Start reply/snapshot thì tiếp tục phân tích read-only. Nếu không, cần quyết định testbed/authority cho môi trường đủ điều kiện trước diagnostic C retest; không đề xuất production fix từ timing inference. 010 chưa PASS và chưa ready for commit.
