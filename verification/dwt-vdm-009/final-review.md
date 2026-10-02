# DWT-VDM-009 — Final documentation closeout

Ngày: **2026-10-02 (Asia/Saigon)**. Branch: `release/1.0-beta`.

**Milestone verdict: DWT-VDM-009 = COMPLETE / PASS / CLOSED.** Acceptance theo yêu cầu FINAL DOCUMENTATION CLOSEOUT ONLY; Task 8 và Task 9 là final verification đã được chấp nhận. Closeout chỉ cập nhật scope amendment, plan và record này; không sửa production/tests, không chạy lại build/tests/lint/device.

## Trạng thái accepted

| Task | Trạng thái cuối | Evidence / phạm vi |
| --- | --- | --- |
| 1–2 | COMPLETE / targeted PASS | Kết quả targeted giữ trong [plan](../../docs/superpowers/plans/2026-10-01-dwt-vdm-009-embedded-product-hardening.md) |
| 3D | COMPLETE / PASS | Connection-readiness boundary; không suy rộng sang hung Start |
| 3G | COMPLETE / PASS / ACCEPTED | [Client Start isolation / terminal detachment](task-3g-client-start-isolation.md) |
| 3B | COMPLETE / PASS / ACCEPTED | [Gate generation / operation ordering](task-3b-gate-operation-ordering.md) |
| 4 | COMPLETE / PASS / ACCEPTED | [Route lifecycle](task-4-route-lifecycle.md) |
| 5 | COMPLETE / PASS / ACCEPTED | [CLEANUP_BLOCKED UX](task-5-cleanup-blocked-ux.md) |
| 6 | COMPLETE / PASS / ACCEPTED | [Typed diagnostics](task-6-typed-diagnostics.md) |
| 7 | COMPLETE / PASS / ACCEPTED | [Proof access](task-7-proof-access.md) |
| 8 | COMPLETE / PASS / ACCEPTED | [Final local gate](local-gate.md) |
| 9 | COMPLETE / PASS / ACCEPTED | [Final S23/DeX production-signed proof](s23-hardening-proof.md) |

Các báo cáo task giữ nguyên trạng thái từng lượt khi được viết; acceptance cuối ở bảng trên thay các nhãn pending/pre-authorization lịch sử.

| Checkpoint kiến trúc lịch sử | Trạng thái giữ nguyên |
| --- | --- |
| Task 3 | BLOCKED_BY_RUNTIME_OWNERSHIP_MODEL — guarantee Application-owned cleanup cũ không còn được yêu cầu |
| Task 3A | COMPLETE — [architecture report accepted](../../docs/superpowers/specs/2026-10-01-dwt-vdm-009-task3-ui-independent-cleanup-boundary.md) |
| Task 3E | COMPLETE / APPROVED; [BLOCKED_REQUIRES_RUNTIME_PROTOCOL_REDESIGN](../../docs/superpowers/specs/2026-10-01-dwt-vdm-009-task3e-start-terminal-boundary.md) cho authoritative permanently-hung remote Start cleanup |
| Task 3F | APPROVED — [scope amendment](../../docs/superpowers/specs/2026-10-01-dwt-vdm-009-in-process-scope-amendment.md) |

Task 3E không kết luận authoritative cleanup đó khả thi trong scope 009. Scope accepted dùng local terminal INCOMPLETE / UNCERTAIN và gate CLEANUP_BLOCKED khi remote ownership chưa có clean evidence; local detach không phải remote cleanup acknowledgement.

## Task 8 — evidence cuối ở local gate

[Local gate](local-gate.md): focused **477 tests PASS**; full local gate **1159 tests PASS**; `assembleDebug` **PASS**; `lintDebug` **PASS**, **0 errors, 46 warnings**. Architecture audit, forbidden-scope audit và security sanity đều **PASS**. Giữ nguyên logs/summaries/audit liên kết trong báo cáo; không cộng focused/full thành số test duy nhất và không chạy lại.

## Task 9 — evidence cuối trên S23/DeX

[Device proof](s23-hardening-proof.md) ghi trực tiếp:

- Production-config / production-signed workflow: **PASS**; same-signer in-place install: **PASS**.
- Artifact và installed readback SHA-256 cùng `246b7df53d14935fbd92925ba7521f7f6216c4ca3b46d16175bbfa25fd970af6`: **PASS**.
- Expected/artifact/installed signer SHA-256 cùng `19ac0ea99125361b3c2083aaa44c8745ebad9c55fa967642c2b9e5a1086a45e7`: **PASS**.
- Samsung S23 Ultra / DeX thật được quan sát; Shizuku unavailable → Ready không restart DWT: **PASS**.
- Một explicit Embedded Start → STARTING → ACTIVE: **PASS**.
- Normal Back cleanup → STOPPED / CLEAN_CONFIRMED / IDLE: **PASS**; hai owned item có clean outcome, một cleanup operation.
- Explicit clipboard diagnostics copy: **PASS**; provenance/unknown giữ đúng evidence. Home và **Nhà phát triển (Thử nghiệm)** presentation: **PASS**.

## Scope giữ nguyên và giới hạn

Không remote protocol redesign, process-death recovery, cleanup Retry/reconcile hoặc RunOwner. Không đổi release/signing/license/security, Room/schema, dependencies, versionName/versionCode, production URLs/trusted keys hoặc renderer 007 semantics. Closeout không chỉnh runtime 006, production/tests hoặc bất kỳ báo cáo/evidence lịch sử nào, kể cả Task 8/9.

**NOT VERIFIED / OUT OF SCOPE:**

- Forced Activity recreation device proof.
- Deliberate CLEANUP_BLOCKED failure injection trên device.
- Native/vendor Surface lifetime.
- Heap/GC proof.
- Authoritative cleanup của permanently hung remote Start.
- Process-death recovery.

Các giới hạn này ngoài scope acceptance đã đóng, không mở lại DWT-VDM-009. Process restart/IDLE, timeout, Binder death, empty inventory hoặc local detach không chứng minh remote resource đã sạch. **Không tạo Task 10; không bắt đầu milestone hoặc implementation cycle mới.**

DWT-VDM-009 = COMPLETE / PASS / CLOSED
