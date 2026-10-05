# DWT-VDM-010 — S23/DeX checkpoint 2026-10-02

**STOP: Calculator+Waze chưa qualified; 010 chưa PASS/chưa đóng.** 009 giữ COMPLETE / PASS / CLOSED.

Worktree `D:/AndroidStudioProjects/DexWorkspaceTouch-VDM-010`, branch `qualification/dwt-vdm-010`, base/HEAD `a43d97bd52386f9ac1a76e2adf03ddcdb21c1c77`. Không stage/commit/push/tag.

## Thay đổi và lý do

Hai failure đã có behavioral RED trước patch:

1. SurfaceLoss StartFailed biểu diễn cùng cell trong receipts và partialReceipt, làm exact-owned check từ chối dù rollback clean. [Local RED](surface-receipt-red.xml), [Android controlled RED](android-surface-behavior-red.txt). Runner sửa một dòng, loại cell đã được partialReceipt đại diện, giống nhánh StartFailure hiện có.
2. Real Calculator recreate → exact clean/IDLE trên host mới nhưng nút Start vẫn disabled do SurfaceLost cũ. [C RED output/trace](../android-real/20261002T110727079Z), [local policy RED](surface-fresh-start-policy-red.xml). Mapper cho explicit fresh Start riêng SurfaceLost + CLEAN_CONFIRMED trong nhánh gate IDLE/available; issue giữ làm history, UI vẫn kiểm current readiness. Không áp dụng cho incomplete/uncertain hoặc các issue khác.

Files production: [runner](../../../app/src/main/java/com/trancong/dexworkspacetouch/workspace/execution/embedded/runtime/EmbeddedWorkspaceRunner.kt) và [recovery mapper](../../../app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/product/EmbeddedProductRecovery.kt).

Files qualification: [local tests](../../../app/src/test/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/product/EmbeddedHostLifecycleQualificationTest.kt), [controlled host](../../../app/src/debug/java/com/trancong/dexworkspacetouch/qualification/EmbeddedLifecycleHarnessActivity.kt), [B instrumentation](../../../app/src/androidTest/java/com/trancong/dexworkspacetouch/qualification/EmbeddedHostLifecycleDeviceTest.kt), [real product host](../../../app/src/debug/java/com/trancong/dexworkspacetouch/qualification/EmbeddedRealProductHarnessActivity.kt), [C instrumentation](../../../app/src/androidTest/java/com/trancong/dexworkspacetouch/qualification/EmbeddedRealProductLifecycleDeviceTest.kt), debug manifest, [init/runner](../../../experiments/dwt-vdm-010-host-harness/README.md), plan/docs/evidence 010.

Harness incomplete ban đầu chỉ nhận Stopped; Surface race trả StartFailed làm injection biến thành uncertain. Đã sửa fixture và siết đúng cause INCOMPLETE/CONTROLLED_010. Outputs cũ giữ lịch sử, không dùng để chứng minh cause incomplete. Harness C có assertion số Surface và exact cell/session receipts, lưu trace khi failure và chờ disposal terminal.

## A — LOCAL / FAKE

| Verification | Kết quả |
| --- | --- |
| [Local qualification XML sau policy](local-qualification-green-after-policy.xml) | 6/6 PASS, không failure/error/skip |
| [Focused regression sau policy](fresh-start-focused-green-summary.json) | 86 PASS |
| [Full unit cuối](full-local-summary-after-policy.json) | 141 classes, 1.165 tests PASS; không failure/error/skip |
| [Lint cuối](lint-results-after-policy.xml) | 0 Fatal/Error; 34 Warning |
| Isolated debug + androidTest build | PASS |
| Package/version/target/signer guards | PASS; production-package APK bị từ chối trước ADB/install |

Full gate 1.164 test trước đó thuộc receipt-fix snapshot; full gate cuối chạy sau policy fix vì shared production đã đổi. Không sửa verification 009 hoặc lặp gate sau docs. Logs build bị ignore; summary lưu counts/XML và SHA-256.

## B — ANDROID CONTROLLED HARNESS

[Preflight](preflight.json): S23 Ultra SM-S918B, Android 16/API 36, One UI 80500; actual DeX display 167. Surface/Activity thật, session/result controlled.

[B outputs/traces](../android-controlled/20261002T104818835Z), [summary/hash](controlled-summary.json): **4/4 PASS, không skip**.

| Case | PID / host identity | Evidence |
| --- | --- | --- |
| Back + recreate | 24260; 182194414 → 266222982 | Host mới STOPPING không execution/Surface/allocation; một close/cleanup; old callback không pop/unlock; clean IDLE không auto-start; explicit fresh generation 2 |
| INCOMPLETE synthetic | 24356; 182194414 → 67021029 → 47715551 | Đúng INCOMPLETE/CONTROLLED_010; blocked qua recreate/navigation/readiness refresh; không Start/Classic unlock |
| UNCERTAIN synthetic | 24512; 182194414 → 192278113 → 105203736 | Blocked giữ nguyên qua lifecycle/refresh/stale callback |
| Native Surface loss/replacement | 24595 | Destroy/create, viewport zero/positive inject khóa/hồi touch; một stop/close, không restart; exact clean IDLE |

Clean recreation: token 162281913/gen 1/start 1/cleanup 2; explicit fresh token 172225309/gen 2/start 3/cleanup 4. Token là object identity trong cùng process. Final owned calc có đúng Clean outcome. Surface terminal: receipts rỗng + partial calc duy nhất, rollback Clean(calc).

B APK identity [đã đọc lại](artifact-identity-controlled-tested.json) thuộc snapshot sau receipt fix. Policy fix sau đó chỉ thêm action ở clean SurfaceLost/IDLE; các B assertions về lifecycle/cleanup/blocked/stale fences không đổi giả định. Branch policy mới có A regression và C product UI proof; không nâng B thành proof current product UI.

Stale Surface identity/generation có A; stale generation trên native newer Surface có B. B viewport dùng injection, không toàn bộ DeX resize policy. Controlled blocked chỉ chứng minh lifecycle/UI/gate/action-policy/stale callback, không chứng minh remote failure, hung Binder hoặc remote convergence.

## C — REAL S23 / DeX RUNTIME

C dùng product Composable và real session factory hiện có trong package riêng; workspace repository fixture chỉ đọc. Không ghi Room hoặc thử license activation; không claim toàn bộ production MainActivity/navigation.

Shizuku ban đầu chưa chạy; Binder attempt trước remote Start là testbed blocker lịch sử. Server sau đó khả dụng (PID 27143) trước case C, không cần chạy starter. Không thay Shizuku config/install. [Starter 13.6 source](https://github.com/RikkaApps/Shizuku/blob/v13.6.0/manager/src/main/java/moe/shizuku/manager/starter/Starter.kt) chỉ dùng đối chiếu lệnh phiên bản đang cài.

[Real summary/hash](real-summary.json), [artifact identity](artifact-identity-after-policy.json).

**Calculator: PASS**, [output/trace](../android-real/20261002T111600412Z).

- PID 3275 giữ nguyên; host identity 49277327 → 77053696.
- Gen 1/start 1/cleanup 2: calc session `dac6e266-b541-4821-8c5e-65fe1f38eff2`, display 169; host mới quan sát STOPPING, exact same-session clean outcome → IDLE.
- Không Surface/auto-start sau clean; explicit product UI Start mới token/gen.
- Gen 2/start 3: calc session `fc38d167-78b7-4755-b4b3-af43c3bb5fa4`, display 170; native Surface loss → StartFailed/SurfaceLost, exact same-session CLEAN_CONFIRMED → IDLE.
- Implicit Surface terminal trước exit intent thuộc exact Start operation 3, cleanup operation vẫn null; không giả định một Product exit operation chưa xảy ra.
- Old/new navigation callbacks đều 0; assertion exact owned receipts/outcomes PASS.

**Calculator+Waze: FAIL / STOP**, [output/trace](../android-real/20261002T111849380Z).

PID 5253, token 260968613, gen 1/start 1: cell calc session `0bf0ecec-9e2c-4196-8edb-a076b688edb1` trả START_FAILED/INCOMPLETE. Product receipt phase FAILED/display -1, outcome failureCode START_FAILED; gate CLEANUP_BLOCKED. Waze chưa Start, không có hai-app ACTIVE/forced recreation/fresh-run/Surface proof. Test thất bại chờ ACTIVE; trace giữ terminal blocked lúc dispose.

[Remote log](calculator-waze-remote-start.txt) liên kết đúng session bằng input name `DWT-VT-30d571a3dc825a74f33ea009` theo hàm hash hiện có: association 123, VDM 114, display 171, input 631. Log ghi handle close, nhưng không có authoritative clean receipt cho phép nhả gate. Không suy sạch từ log đóng handle, timeout, detach, count, empty inventory hoặc process kết thúc.

Inference: khoảng 3 giây giữa touchscreen registration và rollback, không có target-launch log, khớp input-stability wait trước launch trong source hiện hành. **Exact remote exception/root cause NOT VERIFIED**; không patch guard/geometry/runtime dựa trên inference.

## Acceptance hiện tại

| Required criterion | Evidence / trạng thái |
| --- | --- |
| Recreation đổi host, giữ PID | B + C Calculator PASS |
| New host không allocate old non-IDLE run | B allocation/Surface assertions; C Calculator observer/fresh Start PASS |
| Back/dispose một cleanup, old callback không pop/unlock | A/B PASS; C Calculator có exact cleanup operation 2 |
| Exact clean IDLE, không auto-start, explicit fresh Start | A/B/C Calculator PASS |
| Incomplete/uncertain bị khóa qua navigation/refresh | A/B PASS; C two-app attempt thực tế giữ blocked lúc dispose |
| Stale Surface/generation không ảnh hưởng newer run | A/B PASS; không claim vendor lifetime |
| Runtime receipts gắn exact owned set | C Calculator PASS; failed two-app attempt chỉ owned calc |
| Required Calculator+Waze lifecycle | **NOT VERIFIED**, initial Start FAIL/STOP; không SKIP→PASS |

## Architecture, scope và risk

[Audit cuối](scope-audit-after-policy.json): hai main files trên; gate, AIDL, renderer/remote runtime, signing/license/security, config, dependency/toolchain, Room/version và 009 không có diff. Runtime tiếp tục route-owned; Application chỉ gate/status giá trị. Representation fix và fresh-start policy sửa behavior theo accepted contract, không đổi result authority/ownership hoặc renderer/runtime semantics.

Chỉ cài hai package isolated, không uninstall/clear hoặc thay production app. Production preflight version 9/1.0.0-beta.8 và signer/hash trong preflight; không có production install command. Original hai modified hashes khớp checkpoint trước; 148 untracked quan sát, không thao tác vào chúng; original HEAD/branch giữ nguyên. Review là self-review, chưa độc lập. Không stage.

Rủi ro còn lại: session startup failed trong layout hai cell có convergence chưa chứng minh; required two-app lifecycle và exact remote exception chưa có proof. Process kết thúc sau instrumentation không phải recovery/clean evidence. Heap/GC/native/vendor Surface lifetime, process-death recovery và hung Binder cleanup nằm ngoài scope.

## STOP / next

Đã dừng mọi device Start/retry/cleanup/reconcile sau blocked; chỉ thu log đọc và gate local. Chưa milestone PASS hoặc closeout, chưa ready commit.

Review Start failure và xác lập phương án testbed an toàn cho đúng owned session trước bounded two-app retest. Nếu phương án cần đổi accepted contract/protocol/geometry policy, STOP architecture review; không tự nới guard, suy remote clean hoặc cấp phát run mới.
