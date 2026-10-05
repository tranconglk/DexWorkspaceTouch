# Final acceptance update — 2026-10-05

**DWT-VDM-010 = COMPLETE / PASS. READY FOR COMMIT.**

[Final acceptance matrix/evidence](../../../verification/dwt-vdm-010/final-acceptance-20261005/final-report.md) xác nhận 13/13 required criteria và RED→green/regression của hai fixes. NEW independent Calculator+Waze C PASS; không additional device invocation khi closeout. Zero staged paths; chưa commit/push/tag. Dừng chờ commit authorization.

Historical SID `0bf0ecec-9e2c-4196-8edb-a076b688edb1` release NOT VERIFIED; previous pre-Start refresh cause NOT VERIFIED; 009 COMPLETE/PASS/CLOSED. Không chuyển authority giữa các lịch sử.

Phần bên dưới là execution ledger/checkpoint lịch sử 02/10, giữ nguyên nội dung. Trạng thái current được xác định bởi acceptance update này.

---

# DWT-VDM-010 — Embedded DeX Host Lifecycle Qualification

Scope milestone 2026-10-02 đã được phê duyệt; tự chủ theo AGENTS.md và Technical Autonomy Charter. 009 giữ COMPLETE / PASS / CLOSED. Không stage/commit/push/tag.

Worktree `D:/AndroidStudioProjects/DexWorkspaceTouch-VDM-010`; branch `qualification/dwt-vdm-010`; base/HEAD `a43d97bd52386f9ac1a76e2adf03ddcdb21c1c77`.

## Scope lock

Qualification trước: A local/fake, B Activity/Surface Android controlled, C product route với session thật trên S23/DeX. Runtime thuộc route; Application chỉ gate/status giá trị. Chỉ sửa production sau behavioral RED theo accepted contract.

Không đổi AIDL/authority/ownership/renderer semantics, recovery journal/process death/global cleanup, signing/license/security, Room, production configuration, dependency/toolchain hoặc version. Không tác động file loại trừ ở worktree gốc.

## Execution ledger

- [x] Worktree sạch đúng base, branch riêng.
- [x] Local qualification: exact-owned/result fences, cleanup join, fail-closed, stale Surface/viewport.
- [x] Controlled host + instrumentation; actual SurfaceView và forced recreation trên S23 DeX.
- [x] Isolated package/init script; guard package/version/target/signer; không thay production app.
- [x] B bốn case PASS, không skip.
- [x] Receipt trùng SurfaceLoss: behavioral RED A/B, rồi smallest runner fix.
- [x] Fresh Start bị chặn sau clean SurfaceLost: behavioral RED C/A, rồi policy fix riêng SurfaceLost + CLEAN_CONFIRMED khi gate IDLE/available; UI vẫn kiểm readiness.
- [x] Focused regression sau policy: 86 PASS; full local gate cuối 1.165 PASS, lint không lỗi.
- [x] C Calculator PASS: recreation cùng PID, exact owned cleanup, explicit fresh Start, Surface loss clean.
- [ ] C Calculator+Waze lifecycle: FAIL/STOP trước ACTIVE tại cell calc; Waze chưa Start.
- [x] Scope audit/self-review/checkpoint; chưa closeout.

## Rulings / evidence

[Checkpoint](../../../verification/dwt-vdm-010/device-20261002/checkpoint.md).

A/B/C giữ riêng. Synthetic blocked không chứng minh real remote failure/hung Binder/convergence. B dùng harness route wiring; C dùng product Composable/remote factory hiện có với repository fixture chỉ đọc, không Room writes hoặc license activation.

Hai production fixes giữ accepted ownership/result authority/renderer contract. Policy fix giữ issue lịch sử, chỉ cho explicit fresh Start sau authoritative clean SurfaceLost và current host readiness; không nới blocked.

Harness incomplete ban đầu chỉ nhận Stopped; Surface race trả StartFailed biến injection thành uncertain. Đã sửa fixture và assert đúng INCOMPLETE/CONTROLLED_010; chỉ bốn case GREEN mới dùng làm evidence cause.

Harness C lưu trace khi failure và chờ disposal terminal. Surface terminal trước exit intent được fence bằng exact Start operation, không giả định có Product cleanup operation mới. Recreation exit vẫn có cleanup operation 2.

Full gate đầu 1.164 PASS thuộc trạng thái sau receipt fix. Sau policy fix đã chạy targeted trước, C, rồi full gate cuối 1.165 PASS; không lặp gate khi chỉ sửa docs.

## STOP hiện tại

Shizuku đã khả dụng; không còn blocker Binder. C Calculator PASS. C Calculator+Waze trả START_FAILED/INCOMPLETE, gate CLEANUP_BLOCKED cho session calc `0bf0ecec-9e2c-4196-8edb-a076b688edb1`. Không thêm Start, retry, cleanup toàn cục hoặc reconcile.

Raw log gắn input name theo hash đúng session: association 123, VDM 114, display 171, input 631; có handle-close logs nhưng không thay authoritative clean receipt. Exact remote exception/root cause và authoritative convergence NOT VERIFIED. Timing/source gợi ý input-stability wait trước target launch; đây là inference.

Tiếp theo: review Start failure và phương án testbed an toàn cho đúng owned session trước bounded two-app retest. Nếu cần đổi accepted contract/protocol/geometry policy thì STOP architecture review. Required two-app scenario chưa qualified nên 010 chưa PASS, chưa ready closeout hoặc commit.
