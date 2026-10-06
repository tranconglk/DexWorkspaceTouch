# DWT-VDM-011 — Final partial/blocked closeout

**DWT-VDM-011 = COMPLETE / PARTIAL / BLOCKED**

Ngày quyết định: 2026-10-06. Đây là disposition cuối theo quyết định hiện hành của người dùng: kết thúc công việc 011 ở kết quả partial, không tiếp tục thực thi để lấy FULL PASS. COMPLETE mô tả việc đóng phạm vi công việc; PARTIAL mô tả mức acceptance; BLOCKED mô tả dual journey và historical ownership chưa giải quyết. COMPLETE không có nghĩa là FULL PASS, CLEAN hoặc remote release.

**One-app real production journey đã được qualified. Two-app real production journey chưa được qualified.** Fail-closed đã ngăn tiếp tục không an toàn. Không source/test/build/device changes trong closeout; không commit/push/tag.

## Quyền ưu tiên của closeout và hồ sơ lịch sử

Tài liệu này ghi nhận quyết định disposition mới, thay thế hướng tiếp tục/provision testbed cho 011 trong D2. Không sửa hoặc hạ thấp các kết luận kỹ thuật cũ:

- [Checkpoint STOP 011](../checkpoint.md) là hồ sơ đúng tại thời điểm blocked, giữ nguyên.
- [D1-C](../d1-forensics/checkpoint.md) là historical accepted record, giữ nguyên.
- [D2 design review](../d2-design-review.md) là historical accepted record, giữ nguyên; không thực hiện đề xuất thiết bị thứ hai/testbed mới.
- DWT-VDM-009 và DWT-VDM-010 vẫn CLOSED; không mở lại hoặc quảng bá evidence cũ thành execution mới.

Không điều tra forensic thêm, không provision thiết bị khác, không tạo testbed mới, không Embedded Start/retry, không restart/kill/reboot để recovery, không reconcile/late cleanup/recovery owner/journal/AIDL changes trong 011.

## Acceptance matrix

PASS là acceptance đã được người dùng chấp nhận dựa trên evidence 011 hiện có. Không chạy lại tests/build/device để xác nhận hoặc làm đẹp closeout. PARTIAL giữ phần đã quan sát và chỉ rõ phần còn thiếu; NOT VERIFIED không suy từ absence, inventory, PID hay diagnostic thiếu dữ liệu. OUT OF SCOPE không phải acceptance PASS.

| ID | Tiêu chí | Trạng thái | Evidence / phạm vi kết luận |
|---|---|---|---|
| P01 | Real MainActivity / LicenseGate / navigation / repository / Room journey | **PASS** | [Checkpoint journey](../checkpoint.md): real application route, không harness/fixture/license injection. |
| P02 | Tạo workspace và Save qua real UI | **PASS** | [Checkpoint](../checkpoint.md), [Library preservation](../library-preservation.json), [dual saved Library](../screenshots/dual-saved-library.png). Hai qualification workspace được tạo/lưu; assignments được giữ trong backup UI. |
| P03 | Calculator chọn lại và reopen đúng exact workspace identity | **PASS** | Hai ACTIVE diagnostics dùng workspace ID `60a45efc-6a4f-4d0d-9a52-42fd7a26e29e`: [run 1](../single-active-diagnostic.txt), [run 2](../single-fresh-active-diagnostic.txt). |
| P04 | Library → Embedded entry đúng workspace | **PASS** | [Checkpoint](../checkpoint.md), [Calculator reopen](../screenshots/single-reopen-before-start.png), [dual entry](../screenshots/dual-entry.png). |
| P05 | Readiness / entry / refresh giữ explicit Start; không auto-Start hoặc tự xin quyền | **PASS** | [Calculator reopen trước Start](../screenshots/single-reopen-before-start.png), [refresh](../screenshots/single-reopen-refreshed.png), [dual Ready](../screenshots/dual-refreshed.png). Phạm vi các lần đã quan sát. |
| P06 | Calculator ACTIVE | **PASS** | [ACTIVE 1](../single-active-diagnostic.txt), [ACTIVE 2](../single-fresh-active-diagnostic.txt), [ảnh ACTIVE](../screenshots/single-after-start.png). |
| P07 | Interaction xác định ở đúng Calculator pane, kết quả 15 | **PASS** | C → 7 → + → 8 → =; [ảnh kết quả 15](../screenshots/single-result-15.png). Không dùng “touch accepted” thay cho kết quả nhìn thấy. |
| P08 | Lượt Calculator đầu exact-owned cleanup → IDLE / CLEAN_CONFIRMED | **PASS** | [Clean 1](../single-clean-diagnostic.txt), SID/gen/start/cleanup operation khớp bảng bên dưới. |
| P09 | Explicit run thứ hai sau authoritative clean | **PASS** | [Reopen không auto-Start](../screenshots/single-reopen-before-start.png), [fresh ACTIVE](../single-fresh-active-diagnostic.txt). Không dùng process restart để chứng minh persistence. |
| P10 | Lượt Calculator thứ hai exact-owned cleanup | **PASS** | [Clean 2](../single-fresh-clean-diagnostic.txt), owner mới và clean đúng SID của lượt đó. |
| P11 | User guidance | **PASS** | [Embedded Workspace Guide](../../../docs/user/EMBEDDED_WORKSPACE_GUIDE.md): Classic/Embedded, experimental/support limits, readiness, explicit Start, CLEANUP_BLOCKED và hành động an toàn. |
| P12 | Bảo toàn dữ liệu workspace người dùng hiện có | **PASS** | [Preservation audit](../library-preservation.json): 11/11 workspace content/name/schema/canvas giữ nguyên; chỉ thêm hai qualification workspace. Không clear data/uninstall/reset/delete workspace/license mutation. Giới hạn export được giữ ở N08. |
| P13 | Accepted architecture / value-only gate / fences / fail-closed invariants | **PASS** | Source không đổi; [reused regression](../reused-regression.json) là accepted evidence cũ, không execution mới. [Real blocked diagnostic](../dual-blocked-diagnostic.txt) và [Library cuối](../screenshots/final-blocked-library.png) thể hiện khóa được giữ. |
| R01 | Complete Embedded production journey cho cả one-app và two-app | **PARTIAL** | One-app đạt journey; dual mới đạt create/save/entry/readiness và một explicit Start dẫn đến blocked. Không FULL production qualification. |
| R02 | Toàn bộ unsupported/unavailable/invalid-layout product messaging/actions trên real 011 environment | **PARTIAL** | Accepted A/B evidence được reuse và real CLEANUP_BLOCKED được quan sát; các real C011 trạng thái còn lại chưa được chạy lại, không mở thêm execution. |
| N01 | Calculator + Waze cùng ACTIVE | **NOT VERIFIED** | [Dual blocked diagnostic](../dual-blocked-diagnostic.txt): START_FAILED / INCOMPLETE / CLEANUP_BLOCKED; không chứng minh Waze được launch. |
| N02 | Deterministic interaction ở cả hai pane | **NOT VERIFIED** | Không thực hiện dual interactions vì không đạt ACTIVE. Không lấy single Calculator result làm dual proof. |
| N03 | Dual exact-owned authoritative clean | **NOT VERIFIED** | SID blocked không có accepted exact-owned clean; không coi local terminal/Library/pane biến mất là clean. |
| N04 | Dual explicit reopen sau clean | **NOT VERIFIED** | Chưa clean nên không có reopen journey hợp lệ; không có later-run same-ID dual proof. |
| N05 | START ROOT CAUSE của SID blocked | **NOT VERIFIED** | [D1-C](../d1-forensics/checkpoint.md): raw cause/stage không đủ durable evidence để xác định. Closeout không điều tra thêm. |
| N06 | AUTHORITATIVE RELEASE của SID blocked | **NOT VERIFIED** | [D1 correlation](../d1-forensics/exact-run-correlation.json) và checkpoint không có record release đủ authority. Không suy từ inventory, restart/PID hoặc UNKNOWN_SESSION. |
| N07 | Raw remote allocation IDs / rollback result-exception của historical failure | **NOT VERIFIED** | Giữ nguyên giới hạn D1; không giả định unknown là chưa allocate hoặc rollback thành công/thất bại. |
| N08 | Exact-ID/pin/settings preservation của toàn bộ workspace cũ ngoài UI backup format; build wrapper completion | **NOT VERIFIED** | Backup không chứa old workspace IDs/pin/settings; P12 chỉ acceptance preservation trong phạm vi evidence đó. Log build có success nhưng wrapper exit/completion không được xác nhận; không build lại trong closeout. |
| O01 | Thêm device/testbed, Start/retry/dual retest hoặc forensic investigation để tiếp tục 011 | **OUT OF SCOPE — prohibited** | Quyết định final closeout cấm tiếp tục 011 execution; không dùng run mới để đổi kết quả thành synthetic FULL PASS. |
| O02 | Late cleanup, recovery owner, process-death recovery, persistent ownership/recovery journal, global/cross-process reconcile | **OUT OF SCOPE — Charter STOP** | Đổi ownership/result/recovery/cleanup architecture; không implementation. |
| O03 | Protocol/AIDL, cancellation/tombstone, renderer/runtime semantic redesign | **OUT OF SCOPE — Charter STOP** | Giữ accepted remote/runtime contract. |
| O04 | License activation/admin reset/rebind/entitlement mutation; security/signing/backend/Room schema/dependency/toolchain/version changes | **OUT OF SCOPE** | Không cần cho documentation closeout; scope và Charter boundaries giữ nguyên. |
| O05 | Stress/soak, full OEM/viewport/Surface matrix, >2 apps, IME architecture và release/publication | **OUT OF SCOPE** | Qualification artifact không phải release cut; không publish/update manifest/commit/push/tag. |
| O06 | Implementation milestone Start-failure observability | **OUT OF SCOPE** | Chỉ đề xuất hướng milestone mới bên dưới; không triển khai trong 011. |

## Exact Calculator evidence và production provenance

| Lượt | Workspace ID | Product generation / Start / cleanup operation | Exact owned SID | Cell / display | Accepted terminal |
|---|---|---|---|---|---|
| Calculator 1 | `60a45efc-6a4f-4d0d-9a52-42fd7a26e29e` | 1 / 1 / 2 | `d8461d4c-6364-415b-958c-72586f8ffbdc` | cell / 195 | IDLE / STOPPED / CLEAN_CONFIRMED |
| Calculator explicit reopen | Cùng exact ID trên | 2 / 3 / 4 | `f8ec1d38-bf78-4db4-828d-1c84b6155260` | cell / 196 | IDLE / STOPPED / CLEAN_CONFIRMED |

Clean snapshots có `workspace_id=unknown` sau token release; giữ nguyên raw record, không điền hoặc sửa evidence. Correlation dựa trên accepted ACTIVE/clean pair, exact SID, cell/display và product generation/operation; không dùng clean của hai lượt này cho dual SID.

[Artifact identity](../artifact-identity.json), [build status excerpt](../artifact-build-status.txt) và historical checkpoint ghi real package `com.trancong.dexworkspacetouch`, production-config/same-signer workflow, source `db69bed601eceea12259249c89f0f5276793e425`, version `1.0.0-beta.8 / 9`, Samsung SM-S918B Android 16/DeX.

- APK / installed readback SHA-256: `6370cd122f8c5750e230e56c15af5f5ea7a0d252fa019371648c54ab1715852b`.
- Signer SHA-256: `19ac0ea99125361b3c2083aaa44c8745ebad9c55fa967642c2b9e5a1086a45e7`.
- Đây là provenance đã chấp nhận của qualification artifact lịch sử; không phải build/install/readback mới trong closeout.

## Historical blocked run — unresolved, evidence-only

Workspace `DWT-VDM-011 CAL+WAZE 20261005-R01`, ID `fc3b92c6-900a-4111-89ef-910ab90d400e`; SID **`db621ed2-c769-408e-b901-7679ee03bd4f`**; cell `cell_a`; product generation 3 / Start operation 5. Cleanup operation và private IPC/lease operation unknown; không đồng nhất product operation với IPC operation.

**START ROOT CAUSE = NOT VERIFIED. AUTHORITATIVE RELEASE = NOT VERIFIED.**

Historical SID giữ vĩnh viễn evidence-only, trừ khi một record authoritative exact-owned cleanup **đã tồn tại** được phát hiện sau này trong scope được phép. Ngoại lệ này không cho phép điều tra thêm hoặc tạo record trong 011 closeout, không tự mở lại milestone hoặc cho phép Start.

Không retry/reconcile/reset gate, manufacture clean evidence hoặc dùng process mới làm owner của run cũ. Không suy release từ restart/PID/inventory/UNKNOWN_SESSION. Thiết bị/run lịch sử vẫn **NOT ELIGIBLE** cho retest; final disposition không mở khóa gate hoặc tuyên bố device clean.

**Một run độc lập trong tương lai không thể chứng minh root cause hoặc release của SID này.** Nó chỉ tạo evidence cho owner/SID của chính run mới. Fail-closed đã ngăn unsafe continuation; CLEANUP_BLOCKED là bằng chứng giữ khóa, không phải remote cleanup proof.

## Đề xuất milestone mới — Start-failure observability for future runs

Đề xuất một **milestone mới, chưa được thực thi/phê duyệt implementation trong 011**, chỉ cải thiện capture failure cho NEW runs. Không tiếp tục 011 dual qualification bằng một testbed khác và không đặt mục tiêu giải phóng, tái tạo hoặc giải thích SID historical từ run mới.

Scope đề xuất:

- Capture Start stage và raw cause trước mapping/projection làm mất thông tin; phân biệt negative remote reply, transport exception và typed runtime failure.
- Copy Surface identity/isValid và input-stability samples tại các điểm hiện có.
- Giữ allocation IDs thực sự có sẵn locally, không suy ID từ absence/inventory.
- Giữ result/exception của lần rollback hiện có, giữ Start exception riêng; không gọi cleanup lần hai.
- Copy runner receipts/partialReceipt và rollback/cleanup outcomes trước terminal detach và ProductExecutionValue projection.
- Correlate exact NEW SID/cell/timestamp/process-host/product token-generation-operation; lưu bền bounded evidence với thiếu/drop/truncation được ghi rõ.

Observation giữ riêng khỏi ownership/result authority: không raw exception tùy ý trong Application gate, không thay terminal/fences/fail-closed, không nhận late acknowledgement để unlock, không recovery owner/journal/reconcile. Diagnostic evidence không trở thành authoritative clean.

Nếu required data chỉ lấy được qua AIDL/protocol mới hoặc thay acknowledged result/owner contract, STOP và review scope/architecture riêng. [D2 retention design](../d2-design-review.md) là tài liệu tham chiếu lịch sử; các đề xuất continuation testbed của nó đã bị quyết định closeout hiện tại thay thế.

## Preservation và documentation verification

Chỉ thêm file mới dưới `verification/dwt-vdm-011/final-partial-closeout/`. Không sửa checkpoint, raw diagnostics, screenshots, audit, manifests, D1-C/D2, plan/runbook hoặc user guide hiện có.

- [Historical evidence baseline](historical-evidence-baseline.json) giữ SHA-256 của toàn bộ file 011 trước closeout và các tài liệu liên quan.
- [Preservation audit](preservation-audit.json) đối chiếu after với baseline, original worktree và Git scope.
- [Closeout manifest](closeout-manifest.json) giữ hashes của bundle closeout mới, không tự include manifest.
- Documentation checks chỉ kiểm tra consistency, local links, hashes và Git scope. Không chạy application tests/build/device commands; không quảng bá reused results thành execution mới.

## Commit checkpoint và STOP

**READY FOR PARTIAL-CLOSEOUT COMMIT** khi documentation/preservation audit đạt; chưa commit/stage/push/tag.

Candidate scope chỉ gồm tài liệu/evidence 011 đã được kiểm tra: plan/runbook 011, user guide, `smoke-output/.gitignore`, existing curated `verification/dwt-vdm-011/` và closeout bundle mới. Không broad staging; không include ignored APK, backup, scratch/private export hoặc thay đổi ở original worktree.

**DWT-VDM-011 = COMPLETE / PARTIAL / BLOCKED.**

Không có FULL PASS; không có authoritative release mới cho blocked SID. Dừng 011 tại đây.
