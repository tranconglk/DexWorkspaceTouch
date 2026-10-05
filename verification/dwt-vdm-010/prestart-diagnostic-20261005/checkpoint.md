# DWT-VDM-010 — pre-Start diagnostic retest checkpoint, 2026-10-05

**Checkpoint C: Calculator + Waze lifecycle PASS trong NEW independent testbed. DWT-VDM-010 chưa closeout; chưa commit/push/tag.** Đã dùng đúng một C instrumentation invocation theo authorization mới. Không chạy retry loop. Không có production fix mới.

## Ba lịch sử độc lập

- Historical SID `0bf0ecec-9e2c-4196-8edb-a076b688edb1`: **AUTHORITATIVE RELEASE = NOT VERIFIED**, raw Start root cause NOT VERIFIED. Evidence-only; không gửi cleanup/reconcile/readback IPC, không unlock/retry/reinterpret.
- [Independent invocation trước](../independent-diagnostic-20261005/checkpoint.md), `vdm010-independent-d7804af0-fdef-4ca1-9e2c-5b1b815166ea`: pre-Start refresh FAIL; gen0/token/start/cleanup null, zero new ownership. Không có failure-time snapshot, nên exact cause vẫn NOT VERIFIED.
- Invocation mới `vdm010-independent-a2ca3a7f-6079-453c-8bcd-759ced5f4336`: tạo ownership riêng; PASS bên dưới chỉ thuộc run mới. Không chuyển cleanup authority hoặc root-cause kết luận về hai lịch sử trên.

## Thay đổi và source/test files

Chỉ bổ sung qualification/debug diagnostics để nhìn thấy UI trước khi session/Start tồn tại:

- [QualificationDiagnostics.kt](../../../app/src/debug/java/com/trancong/dexworkspacetouch/qualification/QualificationDiagnostics.kt): giữ bản sao scalar readiness/eligibility từ UI thực tế, clear theo host identity; không giữ Activity/Surface/runtime handle, không sửa Application status/gate.
- [Prepare-Diagnostics.py](../../../experiments/dwt-vdm-010-host-harness/Prepare-Diagnostics.py): thêm read-only SideEffect vào bản mirror ProductScreen; build bình thường không đổi source production. [Manifest](diagnostic-overlay-manifest.json): 267 files, 5 bản copy có 19 observer insertions; strip phục hồi nguyên byte tất cả originals.
- [QualificationPreStartCapture.kt](../../../app/src/androidTest/java/com/trancong/dexworkspacetouch/qualification/QualificationPreStartCapture.kt): timestamp/PID/host/display/lifecycle/focus/route/gate, actual UI values, windows/roots, nodes/text/description/flags/bounds/class/parent, exact click result; own-host PNG và window/activity dumps. PNG là Canvas decor, không native Surface buffer capture.
- [EmbeddedRealProductLifecycleDeviceTest.kt](../../../app/src/androidTest/java/com/trancong/dexworkspacetouch/qualification/EmbeddedRealProductLifecycleDeviceTest.kt): live fresh gate guard trước product action; snapshot trước refresh và dispatch; giữ selector package+text và climb clickable parent. ACTION_CLICK false thì STOP, không dispatch lặp.
- [QualificationUiActionEvidence.kt](../../../app/src/debug/java/com/trancong/dexworkspacetouch/qualification/QualificationUiActionEvidence.kt) / [local tests](../../../app/src/test/java/com/trancong/dexworkspacetouch/qualification/QualificationUiActionEvidenceTest.kt): phân biệt no-text-match, no-click-target, disabled target, chưa dispatch, true/false result. Không suy ra readiness success từ timeout hoặc click true.
- [Controlled capture test](../../../app/src/androidTest/java/com/trancong/dexworkspacetouch/qualification/EmbeddedDiagnosticCaptureDeviceTest.kt): raw failure plumbing trước value mapping + scalar readiness/node serialization.

Không sửa production UI/readiness để làm harness PASS. Ba tracked diffs vẫn chỉ là manifest debug và hai RED-backed fixes đã có từ checkpoint trước (Runner receipt uniqueness / recovery policy sau exact clean SurfaceLost). Không thêm production behavioral defect claim.

## A — LOCAL / FAKE

[Prechecks](prechecks.json), ba XML tại thư mục này: **12/12 PASS**, 0 failures/errors/skips (5 diagnostic classification + 6 host lifecycle + 1 raw-failure replay). Build isolated debug/androidTest: PASS.

Source byte-roundtrip và source hashes: PASS. APK package/test target/version/signer guards: PASS; signer `74ec37ad…192e42f`, version 9 / 1.0.0-beta.8. Hash app và test APK ở B/C khớp.

Initial diagnostic-test compilation thiếu API là pre-implementation RED cho plumbing, **không production behavioral RED**. Không full gate hoặc unrelated 009 rerun; các edits mới chỉ là qualification observer/test/docs.

## B — ANDROID CONTROLLED

[Final B output](../android-controlled/20261005T051839029Z/preservesRawFailureAndExactOwnedValuesBeforeProductProjection.txt): **1/1 PASS**. Raw code/message, exact token/gen/op/SID/cell/partialReceipt/Incomplete outcome vẫn được capture trước projection. Thêm value-readiness và synthetic-node flags/text/description/bounds/class.

Synthetic node chưa sealed nên getParent ném exception. Hai B invocation FAIL cùng lỗi unsealed (ở lần thứ hai patch chưa áp dụng vì quoting shell); sửa observer để lưu explicit parent query error thay vì làm mất toàn bộ node. Assertion kiểm tra chính limitation này. Đây là harness fault, không production UI defect. Một wrapper attempt cũng dừng trước instrumentation do PowerShell xử lý stderr tiến độ APK pull; log và artifacts được giữ. Chỉ rerun B plumbing liên quan sau sửa, không real Start trong B.

B này không chứng minh real accessibility-window availability, remote cleanup failure, hung Binder hoặc authoritative convergence. B 4/4 lifecycle trước được giữ riêng; stale callback/generation, Back+dispose join, synthetic INCOMPLETE/UNCERTAIN blocked và controlled viewport/Surface replacement không nâng thành C.

## C — REAL S23 / DeX, NEW independent run

[Run/output](../android-real/20261005T052015915Z/realCalculatorWazeRecreationFreshStartAndSurfaceLoss.txt): **OK (1 test)**. [Plan](invocation-plan.json), [summary](summary.json), [raw namespace](../android-real/20261005T052015915Z/dwt-vdm-010-real/vdm010-independent-a2ca3a7f-6079-453c-8bcd-759ced5f4336).

Pre-Start:

- PID **12800**, DeX **182**, host 1 / identity **49277327**, RESUMED và window focused.
- Fresh gate **IDLE / gen0 / token/start/cleanup null**; eligibility Ready, readiness Ready, platform/Binder/permission true.
- Một text node “Kiểm tra lại” trên display182, enabled/visible=true, TextView clickable=false; parent View enabled/clickable/visible=true. Bounds [481,469,556,489], parent query errors rỗng.
- [Trước refresh](../android-real/20261005T052015915Z/dwt-vdm-010-real/vdm010-independent-a2ca3a7f-6079-453c-8bcd-759ced5f4336/prestart-01-before-refresh.json), [kết quả](../android-real/20261005T052015915Z/dwt-vdm-010-real/vdm010-independent-a2ca3a7f-6079-453c-8bcd-759ced5f4336/prestart-03-action-result.json): ACTION_CLICK=true; Start explicit cũng true. Screenshot đã xem và khớp UI/readiness. System dump ghi focused/resumed host; accessibility root/node inventories được giữ đầy đủ cho package testbed.
- Không thay selector để đạt kết quả này. Success mới không chứng minh nguyên nhân của failure trước.

Exact owned lifecycle:

| Run | Token identity | Gen / Start / Cleanup op | Cell | Exact SID | ACTIVE display |
|---|---:|---|---|---|---:|
| First | 111161902 | 1 / 1 / 2 | calc | cb30ef64-ec68-4191-80dd-f7aed2a2fcdd | 183 |
| First | 111161902 | 1 / 1 / 2 | waze | 21470d47-c904-48d7-8252-95603a601ddb | 184 |
| Explicit fresh | 90185765 | 2 / 3 / null | calc | 4d30ec9f-8bb1-47f6-a73a-93f16ed3fd7b | 185 |
| Explicit fresh | 90185765 | 2 / 3 / null | waze | b44cacd9-87f1-4925-890f-a14d4aaf87f5 | 186 |

Cố ý recreation: host identity **49277327 → 226738325**, host ID1→2, **PID12800 không đổi**. New host được tạo trong STOPPING, giữ gen1/Start1/cleanup2; không replacement execution/Surface cho old non-IDLE run. Exact owned result gồm waze receipt + calc partialReceipt, không duplicate; hai outcomes Clean, allOwnedSessionsClean=true. Gate về IDLE/CLEAN_CONFIRMED, cleanup op2 không đổi; navigation callbacks old/new bằng0.

Sau exact clean, host không Surface/autostart; refresh không tăng generation. Chỉ explicit Start mới tạo token khác/gen2/Start3 và cả hai SID mới ACTIVE. GONE trên Surface calc tạo loss; terminal của exact Start3, cleanup operation null (không invent thêm gate cleanup op), cùng owned set/hai Clean outcomes/allOwnedSessionsClean=true → IDLE/CLEAN_CONFIRMED. Dispose sau đó không resurrect.

Native diagnostics [SID correlation](remote-log-correlation.json) / [joined records](new-session-log-records.jsonl): 4 local Start completions success; mỗi SID có 2 input samples (finger=true, sample tiếp bằng previous), wait-return và launch guard Surface valid=true. Remote PIDs 13118 (gen1), 13502 (gen2); Surface identities chỉ so sánh trong process tương ứng. Không có raw remote Start failure mới. Native cleanup authority dựa trên exact accepted runner results/outcomes, không handle close/process counts/empty inventory/timeout/detach.

C này chứng minh product Composable/runner thật trong isolated package. Back/dispose join với forced delayed/stale injections, blocked navigation/refresh và viewport injection được chứng minh bằng A/B riêng; C không giả mạo các injections đó thành remote failure evidence.

## Architecture, risks và NOT VERIFIED

Ownership/result authority, value-only Application gate, exact fences, renderer/runtime semantics, geometry policy, AIDL/protocol, signing/version/config và 009 không đổi. [Scope audit](scope-audit.json): đúng branch/base, zero staged paths; original worktree hai modified hashes không đổi, 148 untracked quan sát được; không tác động các excluded files.

**NOT VERIFIED**: historical SID release/raw root cause; exact cause của previous pre-Start refresh failure; real permanently-hung Binder cleanup/convergence; process-death recovery; mọi DeX viewport/vendor Surface lifetime ngoài bounded scenario. Native observer logging có thể ảnh hưởng timing; không claim heap/GC/vendor lifetime guarantee.

## Next recommended action

Chốt acceptance matrix/final evidence review của 010 bằng evidence A/B/C hiện có, giữ limits và historical blocked run riêng. Dừng tại checkpoint C theo user authorization; không cần thêm device invocation để làm đẹp report. Milestone chưa được đánh dấu CLOSED, chưa ready for commit qua báo cáo này; commit/push/tag vẫn chưa được cấp phép.
