# DWT-VDM-010 — FINAL ACCEPTANCE REVIEW

**DWT-VDM-010 = COMPLETE / PASS. READY FOR COMMIT.**

Acceptance review ngày 2026-10-05: **13/13 required criteria có evidence đủ trong scope đã phê duyệt**. Closeout chỉ sửa tài liệu; không source/test executable edit, không test/build/device invocation mới, không stage/commit/push/tag. Dừng chờ authorization commit theo chỉ thị hiện tại.

Mục tiêu: qualify same-process Embedded lifecycle trước Activity/host recreation và Surface loss/replacement trên Android/Samsung DeX, giữ route ownership, value-only Application gate, exact token/generation/operation fences, một cleanup, fail-closed và fresh Start tường minh.

## Evidence catalog và giới hạn

| ID | Lớp | Artifact / ý nghĩa |
|---|---|---|
| A1 | LOCAL / FAKE | [Current host lifecycle XML](A-host-lifecycle-current.xml), 6/6; copy nguyên byte XML từ pre-Start checkpoint, không rerun. Test join/exact ownership/fail-closed/wrong generation-op, hai fixes, stale Surface identity + generation/viewport. |
| A2 | LOCAL / FAKE | [Full local sau policy](../device-20261002/full-local-summary-after-policy.json): 141 classes/1.165 tests, 0 failure/error/skip. Log thực tế có BUILD SUCCESSFUL và SHA-256 khớp; [lint XML](../device-20261002/lint-results-after-policy.xml): 0 Fatal/Error, 34 Warning. |
| A3 | LOCAL / FAKE | [Affected regression sau policy](../device-20261002/fresh-start-focused-green-summary.json): 86/86. [Receipt-fix regression trước đó](../device-20261002/focused-green-summary.json): 71/71. Các kết quả không cộng thành số unique tests. |
| A4 | LOCAL / FAKE | Current [failure replay](A-start-failure-current.xml) 1/1 + [UI action evidence](A-ui-action-current.xml) 5/5; với A1 thành 12/12 ở final diagnostic artifact. Không chứng minh root cause historical. |
| B1 | ANDROID CONTROLLED | [Recreation/cleanup/fencing output](../android-controlled/20261002T104818835Z/recreateJoinsCleanupOldCallbackCannotPopNewHostAndFreshStartIsExplicit.txt), [trace](../android-controlled/20261002T104818835Z/dwt-vdm-010/recreation-clean.txt). Actual Android Activity/Surface; session/results controlled. |
| B2 | ANDROID CONTROLLED | [INCOMPLETE output](../android-controlled/20261002T104818835Z/syntheticIncompleteSurvivesRecreationNavigationAndReadinessRefresh.txt), [trace](../android-controlled/20261002T104818835Z/dwt-vdm-010/blocked-INCOMPLETE.txt): exact controlled cause CONTROLLED_010. |
| B3 | ANDROID CONTROLLED | [UNCERTAIN output](../android-controlled/20261002T104818835Z/syntheticUncertainSurvivesRecreationNavigationAndReadinessRefresh.txt), [trace](../android-controlled/20261002T104818835Z/dwt-vdm-010/blocked-UNCERTAIN.txt). |
| B4 | ANDROID CONTROLLED | [Surface/viewport output](../android-controlled/20261002T104818835Z/actualSurfaceLossAndViewportChangesDoNotResurrectRun.txt), [trace](../android-controlled/20261002T104818835Z/dwt-vdm-010/surface-loss.txt): native destroy/create; viewport injected, session controlled. B1–B4 4/4 PASS, no skip. |
| B5 | ANDROID CONTROLLED CAPTURE | [Raw/pre-Start plumbing output](../android-controlled/20261005T051839029Z/preservesRawFailureAndExactOwnedValuesBeforeProductProjection.txt), 1/1 PASS. Synthetic host/node/readiness/failure; không remote Start. Parent-query limitation của unsealed synthetic node được ghi explicit. |
| C1 | REAL S23/DeX | [Calculator output](../android-real/20261002T111600412Z/realCalculatorRecreationFreshStartAndSurfaceLoss.txt), [trace](../android-real/20261002T111600412Z/dwt-vdm-010-real/calculator.txt): bounded single-app PASS sau policy fix. |
| C2 | REAL S23/DeX — NEW independent | [Calculator+Waze output](../android-real/20261005T052015915Z/realCalculatorWazeRecreationFreshStartAndSurfaceLoss.txt), [raw namespace](../android-real/20261005T052015915Z/dwt-vdm-010-real/vdm010-independent-a2ca3a7f-6079-453c-8bcd-759ced5f4336), [exact summary](../prestart-diagnostic-20261005/summary.json), [SID log join](../prestart-diagnostic-20261005/remote-log-correlation.json). Đúng một invocation, OK (1 test); lifecycle gồm initial và fresh explicit Start sau exact clean. |
| S1 | SOURCE REVIEW | [Product route](../../../app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/product/EmbeddedWorkspaceProductScreen.kt), [gate](../../../app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/product/EmbeddedProductRunGate.kt), [Application](../../../app/src/main/java/com/trancong/dexworkspacetouch/DexWorkspaceTouchApplication.kt). Source review bổ trợ, không thay runtime evidence. |
| S2 | SOURCE / GIT AUDIT | [Scope/Git audit](scope-audit.json), [verification audit](verification-review.json), [hash manifest](evidence-manifest.json): current diff/branch/base, protected paths, original excluded hashes/inventory, historical artifacts. Không phải runtime A/B/C. |

B1–B4 dùng [APK sau receipt fix](../device-20261002/artifact-identity-controlled-tested.json), trước policy fix thứ hai. Có thể tái sử dụng vì:
- B2/B3 nằm trong blocked branch; policy fix chỉ chạy khi IDLE/available + CLEAN_CONFIRMED/SurfaceLost, nên không đổi các giả định blocked/stale/navigation.
- B1 kiểm gate/cleanup/renderer/route allocation và handler explicit Start của controlled host; các thành phần đó không đổi bởi policy patch. Không dùng B1 làm proof product UI Start button mới.
- B4 kiểm native callback/renderer/runner terminal, stop/close/no auto-restart; policy thêm permitted action không tự dựng runtime. Policy branch mới có A1/A3 và C1/C2 product UI proof.

B synthetic blocked **không chứng minh** real remote cleanup failure, hung Binder hoặc authoritative remote ownership convergence. C dùng actual product Composable/remote factory trong isolated package với repository fixture chỉ đọc; không qualify toàn bộ production MainActivity/navigation/license/Room. Không chuyển authority giữa evidence classes.

## Final acceptance matrix

| # | Required criterion | Evidence class và chứng cứ trực tiếp | Kết quả / giới hạn |
|---:|---|---|---|
| 1 | Forced same-process recreation | B1; C1/C2 | PASS. C2 host identity 49277327 → 226738325, PID12800 không đổi; B1 cũng direct asserts. |
| 2 | New host không replacement execution/Surface cho old non-IDLE | A1 + B1, source S1; C2 corroboration | PASS. B1 assert host STOPPING, allocations=0, route=null/no Surface, explicit Start bị chặn. C2 có host-create STOPPING và no Surface sau clean; không claim C allocation counter chưa instrumented. |
| 3 | Một cleanup; exact owned cells/SIDs; không duplicate | A1/B1/B4 + C1/C2 | PASS. Back+dispose join một close ở A/B; C2 cleanup op2 giữ nguyên. C2 receipts+partialReceipt biểu diễn đúng hai cells/SIDs một lần mỗi cell; exact Clean outcomes. |
| 4 | Old callback không pop/unlock new host | A1 + B1/B2/B3; C2 corroboration | PASS. A/B direct stale result/session injections không đổi newer state hoặc navigation. C2 natural recreation callbacks old/new=0; không gọi đó là injected stale C. |
| 5 | Authoritative exact clean → IDLE/CLEAN_CONFIRMED | A1/B1/B4 + C1/C2 | PASS. Đúng accepted runner result/outcomes và operation fences; không dựa vào detach/count/timeout. |
| 6 | Recreation/refresh không auto-start/generation mới | A1/B1/B4 + C1/C2; S1 | PASS. A/B counters; C refresh sau clean giữ generation, no Surface. Factory chỉ đi từ Start handler. |
| 7 | Fresh token/generation/Start op chỉ sau explicit Start | A1/B1 + C1/C2 | PASS. C2 token111161902/gen1/Start1 → sau clean, explicit Start → token90185765/gen2/Start3. Current readiness vẫn bắt buộc. |
| 8 | INCOMPLETE/UNCERTAIN giữ CLEANUP_BLOCKED qua navigation/refresh | A1 + B2/B3/B5 | PASS cho controlled lifecycle/UI/gate/policy. Không claim real remote cleanup failure hoặc convergence. |
| 9 | Surface identity/generation fences; loss terminal; stale không phá newer run | A1 + B1/B4 + C1/C2 | PASS. A1 direct current-gen/wrong-Surface và old-generation cases; B stale generation + native destroy/create; C actual loss exact clean. Viewport B injected; không vendor/native lifetime guarantee. |
| 10 | Real Calculator+Waze ACTIVE/recreate/cleanup/fresh Start/Surface loss | C2 | PASS. Both apps ACTIVE ở gen1 và gen2; toàn bộ bounded scenario OK, no skip/retry. |
| 11 | Result authority chỉ từ exact accepted result/outcomes | A1/B2/B3 + C2; S1/S2 | PASS. Missing owned/incomplete/wrong op không unlock; exact C results được đối chiếu SID/cell. Historical close/death/inventory không được dùng làm release. |
| 12 | Architecture preservation | A1/A2/A3 + SOURCE/GIT S1/S2 | PASS. Route-owned runtime, Application value-only gate/status; authority/semantic contract không đổi, không AIDL/process-death/reconcile redesign. |
| 13 | Scope, 009, original excluded files | SOURCE/GIT S2 | PASS. Protected paths không diff; original 2 modified hashes và 148 observed untracked path inventory khớp. Original chỉ read-only; không claim đã có baseline byte hash cho mọi untracked file. 009 giữ COMPLETE/PASS/CLOSED. |

## Review hai production fixes

| Fix | Behavioral RED | Final green/regression | Smallest patch / architecture |
|---|---|---|---|
| A. SurfaceLoss receipt uniqueness | [Local RED](../device-20261002/surface-receipt-red.xml): expected [calc], actual [calc,calc], 1 failure/0 error. [Android controlled RED](../device-20261002/android-surface-behavior-red.txt): exact clean bị gate từ chối, CLEANUP_BLOCKED thay IDLE. | A1 có cùng behavioral case PASS trên final source; A2 full gate; B4 PASS; C2 gen1/gen2 exact two-cell union receipts+partial không duplicate và clean được nhận. | [Runner](../../../app/src/main/java/com/trancong/dexworkspacetouch/workspace/execution/embedded/runtime/EmbeddedWorkspaceRunner.kt): một dòng filter receipts để cell đã đại diện bằng partialReceipt không xuất hiện lần nữa. Giữ nguyên SID/outcomes/owned set/result authority/cleanup semantics. |
| B. Explicit fresh Start sau authoritative-clean SurfaceLost | [Policy RED](../device-20261002/surface-fresh-start-policy-red.xml): expected true, actual false, 1 failure/0 error. [C RED](../android-real/20261002T110727079Z/realCalculatorRecreationFreshStartAndSurfaceLoss.txt) + trace: host mới exact clean IDLE nhưng action Start không khả dụng. | A1 same case PASS; A3 86/86 và A2 full gate sau fix; C1/C2 actual product fresh Start PASS. A1 uncertain không có Start/Classic, Shizuku unavailable vẫn chặn Start. | [Recovery mapper](../../../app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedworkspace/product/EmbeddedProductRecovery.kt): chỉ thêm START_EMBEDDED khi nhánh resolved đã bảo đảm IDLE/gate available và SurfaceLost + CLEAN_CONFIRMED. Giữ prior issue; UI còn current readiness; không auto-start, không nới incomplete/uncertain/other issues. |

Hai RED là **assertion failures về behavior**, không compilation errors. Pre-implementation diagnostic API compile failure và synthetic unsealed-node fault về sau không được nâng thành production RED. Không thêm production patch trong review này.

## Exact C2 owned results

Run ID `vdm010-independent-a2ca3a7f-6079-453c-8bcd-759ced5f4336`, DeX display182. App PID12800 giữ nguyên; host ID1/identity49277327 → ID2/identity226738325.

| Gen / Start / gate Cleanup | Cell | SID owned | ACTIVE display |
|---|---|---|---:|
| 1 / 1 / 2 | calc | cb30ef64-ec68-4191-80dd-f7aed2a2fcdd | 183 |
| 1 / 1 / 2 | waze | 21470d47-c904-48d7-8252-95603a601ddb | 184 |
| 2 / 3 / null | calc | 4d30ec9f-8bb1-47f6-a73a-93f16ed3fd7b | 185 |
| 2 / 3 / null | waze | b44cacd9-87f1-4925-890f-a14d4aaf87f5 | 186 |

Mỗi terminal SurfaceLost sau ACTIVE gồm waze receipt + calc partialReceipt, hai Clean outcomes và allOwnedSessionsClean=true; gate exact clean IDLE. Receipt phase là snapshot representation, **không dùng phase/handle close để suy sạch**. Generation1 recreation result được nhận bởi cleanup op2; generation2 implicit Surface terminal trước exit intent thuộc exact Start3, cleanup op vẫn null.

Remote log join chỉ theo bốn SID mới: mỗi SID hai input-stability samples finger=true và sample sau bằng previous; wait-return/launch Surface valid=true, bốn raw Start completions success. Remote PIDs13118/13502; Surface object identities chỉ có nghĩa trong process tương ứng. No raw Start failure mới.

Pre-Start actual UI snapshot: fresh IDLE/gen0/null token/start/cleanup; eligibility/readiness Ready, host RESUMED/focused. Một matching text node “Kiểm tra lại” enabled/visible; TextView non-clickable có parent enabled/clickable; ACTION_CLICK=true. Screenshot/dumps/roots/nodes được giữ. Selector package+text không đổi; click false fail-fast. Success này **không tìm ra nguyên nhân refresh failure trước**.

## Files và verification

Production main files changed chỉ:
- EmbeddedWorkspaceRunner.kt — unique receipt representation.
- EmbeddedProductRecovery.kt — bounded explicit fresh Start policy.

Debug manifest thêm hai non-exported qualification Activities. Debug/qualification sources: EmbeddedLifecycleHarnessActivity.kt, EmbeddedRealProductHarnessActivity.kt, QualificationDiagnostics.kt, QualificationUiActionEvidence.kt.

Unit qualification: EmbeddedHostLifecycleQualificationTest.kt, EmbeddedStartFailureEvidenceQualificationTest.kt, QualificationUiActionEvidenceTest.kt. Instrumentation: EmbeddedHostLifecycleDeviceTest.kt, EmbeddedRealProductLifecycleDeviceTest.kt, EmbeddedDiagnosticCaptureDeviceTest.kt, QualificationPreStartCapture.kt.

Harness scripts: Prepare-Diagnostics.py, isolated-app-id.init.gradle, Run-Qualification.ps1, inspect-sources.init.gradle; README và diagnostic-overlay-manifest.json. Mirror debug-only có 267 sources/5 patched copies/19 insertions; stripping phục hồi nguyên byte current main sources. Init đổi applicationId chỉ cho qualification build, không signing/version/dependencies; package guard ngăn target production. Java/Kotlin source sets đều dùng mirror; release tasks bị chặn khi bật diagnostics.

Existing verification được tái sử dụng sau kiểm source/diff/hashes/assertions:
- A2 full gate 1.165/141 classes; A3 86 affected tests; final diagnostic A1+A4 12/12.
- Lint 0 Fatal/Error, 34 existing warnings.
- Isolated debug + androidTest build PASS; package/version/test-target/signer guards PASS; B5/C2 app + test artifact identities khớp.
- B lifecycle4/4 (snapshot riêng), B capture1/1; C Calculator PASS, C2 two-app PASS.
- Review command xác thực RED/green XML, output/trace hashes, C23 capture-file hashes, full-gate log SHA, mirror roundtrip, protected paths, original inventory và scoped diff; **không rerun suites/device**.

## Historical records độc lập

1. [Historical blocked attempt](../device-20261002/checkpoint.md), SID `0bf0ecec-9e2c-4196-8edb-a076b688edb1`: **AUTHORITATIVE RELEASE = NOT VERIFIED**, exact raw Start root cause NOT VERIFIED. Không cleaned/retry/reconcile/unlock/readback-authority. Gate blocked evidence giữ nguyên.
2. [Previous independent pre-Start failure](../independent-diagnostic-20261005/checkpoint.md), run `vdm010-independent-d7804af0-fdef-4ca1-9e2c-5b1b815166ea`: gen0/null ops/token, zero remote Start/new ownership; **exact refresh cause NOT VERIFIED**.
3. [Successful independent run](../prestart-diagnostic-20261005/checkpoint.md): chỉ authority/evidence của ownership mới bên trên.

Historical reports/RED outputs/raw data giữ nguyên; hash audit ghi nhận trước/sau documentation closeout. Previous current pointer được lưu nguyên byte trong [archive](previous-current-pointer.md). Original 010 execution ledger được giữ phía dưới dated closeout update trong current plan. 009 không reopen/rewrite/rerun.

## NOT VERIFIED / OUT OF SCOPE / risks

Historical release/raw root cause và previous refresh failure exact cause vẫn NOT VERIFIED; không phải gap của qualification independent run đã được user chấp thuận. Không claim process-death recovery, recovery journal/cross-process/global reconcile, permanently-hung Start/Binder cleanup, cancellation/tombstones, heap/GC, native/vendor Surface lifetime, mọi DeX resize/viewport policy hoặc >2 apps/IME. Experimental label giữ nguyên.

Observer logging có thể ảnh hưởng timing; bounded C PASS không phải reliability/stress guarantee. Review do cùng agent thực hiện, không independent reviewer. Controlled blocked chỉ có local lifecycle/UI/gate/action-policy authority.

## Scope audit và Git / commit readiness

Worktree `D:/AndroidStudioProjects/DexWorkspaceTouch-VDM-010`, branch `qualification/dwt-vdm-010`, base/HEAD `a43d97bd52386f9ac1a76e2adf03ddcdb21c1c77`. Original HEAD/branch vẫn `c5cd7bacae6f48f049a29bd0bec3b479b7831828` / `release/1.0-beta`.

Tracked diffs chỉ debug manifest và hai main files. Untracked changed paths đều thuộc qualification/test/docs/evidence010; protected build/signing/license/security/schema/config/dependencies/version/009 không diff. Original hai modified hashes unchanged, untracked inventory148 khớp checkpoint; không modify/stash/clean/reset/stage/move/delete excluded files. Count149 trong prompt ban đầu không có matching baseline quan sát; không quy chênh lệch này thành thao tác của 010.

**READY FOR COMMIT**: đủ required evidence, hai production fixes RED→green/regression, architecture/scope preserved, docs closeout nhất quán, no unresolved required gap. Zero staged paths; chưa commit/push/tag. Logs/APK/generated source/build outputs vẫn ignored; không đưa signing materials hoặc binaries vào commit. Commit cần authorization mới; review dừng tại boundary này.
