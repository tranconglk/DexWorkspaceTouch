# DWT-VDM-011 — Embedded Production Journey Qualification

> Execution: superpowers:executing-plans, native single-agent; không commit/push/tag. Scope và technical autonomy do user phê duyệt ngày 2026-10-05; approval mới chỉ cần khi vượt STOP boundary.

**Goal:** Chứng minh UI thật → Room → Library theo exact workspace ID → Embedded readiness → explicit Start → tương tác → exact-owned clean → explicit reopen.

**Architecture:** Giữ MainActivity/LicenseGate/TouchNavigation, repository Room thật và product route hiện tại. Chỉ sửa presentation/navigation khi có lỗi tái hiện; RED hành vi trước patch, targeted GREEN và affected regression sau patch.

**Tech stack:** Kotlin, Compose Navigation, Room, StateFlow, Android/Samsung DeX, Shizuku.

**Spec:** Yêu cầu hiện hành của user: DWT-VDM-011 — MILESTONE SCOPE APPROVED; canonical milestone Embedded Production Journey Qualification. 009/010 CLOSED.

## Scope lock / Global constraints

- Base: `db69bed601eceea12259249c89f0f5276793e425`; branch `qualification/dwt-vdm-011`; worktree `D:/AndroidStudioProjects/DexWorkspaceTouch-VDM-011`.
- Checkout gốc chỉ đọc: 2 modified probe files, 148 untracked files; không thay đổi index/HEAD/nội dung.
- Package thật `com.trancong.dexworkspacetouch`; production-config/same-signer in-place install; version `1.0.0-beta.8`, code `9`.
- Chỉ tạo workspace qualification mới qua UI; không clear data/uninstall/reset settings/delete workspace khác/đụng license/admin.
- Không process kill/restart trong khi owned run; clean navigation/reopen không phải process-death recovery.
- STOP: ownership/result authority, protocol/AIDL, renderer/runtime semantics, cleanup/reconcile architecture, schema, license/security, signing, version hoặc publication cần đổi; hoặc real ownership unresolved.
- No commit, push, tag, update-manifest/release/public-object mutation. APK qualification không phải public release.

## Files và interfaces

- Tạo tài liệu này: matrix, runbook, artifact/identity prerequisites.
- Tạo `docs/user/EMBEDDED_WORKSPACE_GUIDE.md`: hướng dẫn người dùng.
- Tạo `verification/dwt-vdm-011/checkpoint.md` và evidence dưới cùng thư mục: audit/status/provenance.
- Chỉ nếu tái hiện lỗi: patch nhỏ ở MainActivity/TouchNavigation/HomeScreen/EmbeddedWorkspaceProductScreen hoặc presentation liên quan; test sở hữu hành vi tương ứng. Không có production patch dự kiến trước evidence.
- Existing interfaces: `WorkspaceLibraryViewModel.saveWorkspace(canvas, requestedName, onPersisted)` → repository insert/update; callback sau write thành công. Library dùng `workspace.id`; router `openEmbedded(workspaceId)`; loader `loadEligible(workspaceId, geometryPolicy)` đọc `repository.getById`.
- Runtime gate/status chỉ value; admission/cleanup vẫn qua controller/gate hiện tại.

## Review focus

- Workspace tên giống nhau: unique qualification name + actual card callback ID; diagnostic PRODUCT_RUN_WORKSPACE ở hai lượt phải cùng exact ID. Export product chủ ý bỏ workspace ID; không suy ID qua tên/thứ tự.
- Save fail: không coi selection optimistic là persistence PASS; phải export/readback sau successful UI save.
- Entry/refresh/reopen: không permission dialog/auto-Start; gen/op/owned evidence không tăng trước tap Start.
- Invalid/unavailable: typed bounded message + actions hợp policy; không fallback tự động/false clean.
- Exit: empty pane, detach hoặc timeout không đủ; cần exact owned SID/display/cell outcome và IDLE/CLEAN_CONFIRMED.

## Task ledger — checkpoint STOP 2026-10-06

- [x] 011-01: clean worktree/base, scope lock, deterministic interaction/runbook trước device mutation.
- [x] 011-02: audit MainActivity/navigation/Room/Library/product readiness và rejection; không sửa production code.
- [ ] 011-03: single exact persisted ID qua hai lượt PASS; cả hai assignment/bounds đọc lại đúng qua UI backup. Dual chưa có later-run exact-ID proof vì STOP.
- [ ] 011-04: N/A. Không có patch presentation/navigation; runtime failure không được sửa ngoài STOP boundary. Không có RED/GREEN mới.
- [ ] 011-05: Calculator ACTIVE, 15, hai lần exact-owned clean và explicit reopen PASS. Calculator+Waze START_FAILED/CLEANUP_BLOCKED/INCOMPLETE; phần còn lại NOT VERIFIED.
- [x] 011-06a: user guide tạo và đối chiếu policy/source.
- [x] 011-06b: checkpoint STOP/evidence, preservation audit; không phải milestone closeout PASS, không commit.

Mandatory STOP: một explicit Start dual tạo generation 3/start operation 5, cell_a, owned SID db621ed2-c769-408e-b901-7679ee03bd4f; cleanup INCOMPLETE. Không retry, restart/kill, reconcile hay đổi ownership authority. Sau STOP chỉ copy diagnostic, safe Back, backup Library để audit preservation và ghi tài liệu. Chi tiết: verification/dwt-vdm-011/checkpoint.md.

## Workspace identities

Hai tên không tồn tại trong inventory 11 workspace trước tạo, đã tạo/lưu qua UI:
- `DWT-VDM-011 CAL 20261005-R01`: single cell, Samsung Calculator.
- `DWT-VDM-011 CAL+WAZE 20261005-R01`: hai pane không chồng lấn, Calculator trái, Waze phải.
- Actual workspace ID: single `60a45efc-6a4f-4d0d-9a52-42fd7a26e29e` (cell); dual `fc3b92c6-900a-4111-89ef-910ab90d400e` (cell_a/cell_b), export product không chứa workspace ID; ghi ID từ diagnostic PRODUCT_RUN_WORKSPACE của lượt explicit Start và đối chiếu lại ở reopen. Export UI chứng minh tên/cell/targets/bounds; không suy ID từ tên và không tự chèn record/gán UUID ngoài UI.
- Calculator target: `com.sec.android.app.popupcalculator/com.sec.android.app.popupcalculator.Calculator`.
- Waze target: `com.waze/com.waze.FreeMapAppActivity`; đã đọc lại đúng trong final UI export. Dual interactions dưới đây là kế hoạch, chưa thực hiện vì Start failed.

| Scenario/pane | Action sau ACTIVE | Expected visible result | Evidence |
|---|---|---|---|
| Single Calculator | Trong đúng pane: C → 7 → + → 8 → = | Kết quả `15` trong Calculator | Screenshot before/after + pane rectangle/cell ID + action sequence |
| Dual Calculator trái | Trong pane trái: C → 5 → + → 6 → = | Kết quả `11` bên trái; pane phải vẫn Waze | Screenshot before/after với pane bounds/cell IDs |
| Dual Waze phải | Tap nút mở menu/tìm kiếm có nhãn quan sát được trước; chọn hành động mở panel local, không nhập destination | Panel/menu tìm kiếm Waze hiện bên phải; Calculator trái còn `11` | Screenshot before/after + nhãn/bounds target đã quan sát |

Waze không phụ thuộc tìm route/network/GPS. Nếu local panel khác với dự kiến, ghi nhãn/action/visible expectation cụ thể trước tap; nếu không có tương tác deterministic, criterion NOT VERIFIED. Touch accepted/counter chỉ bổ trợ, không thay visible result. Crop/redact vị trí cá nhân trước lưu evidence cuối.

## Safe device runbook

1. ADB inventory một lần; kết nối serial IP:port được user cung cấp. Không suy địa chỉ cũ. Chỉ kiểm lại khi trạng thái kết nối thay đổi.
2. Đọc package/version/pm path/PID/display và nhìn UI hiện tại. Không force-stop/relaunch nếu có owned run; nếu unresolved ownership STOP ngay.
3. Khi UI gate đã xác nhận idle/clean: inventory Library qua backup UI, ghi tên/canvas/hash (format export không mang workspace ID); ghi baseline hash exported file. Không thay đổi workspace cũ/settings/license.
4. Kiểm existing valid license qua UI; unexpected license blocker → STOP, không activate/rebind/reset.
5. Build bằng `scripts/build-device-smoke.ps1` unchanged, config/signing local ignored được dùng kín. Lưu source commit, dirty scope, APK SHA-256, version, signer. Không dùng build-production-release/publish/update-manifest.
6. Trước install: `pm path` → pull installed base APK vào ignored smoke-output → apksigner verify signer. So expected signer `19ac0ea99125361b3c2083aaa44c8745ebad9c55fa967642c2b9e5a1086a45e7` và candidate signer. Mismatch/không establish same-signer → STOP.
7. Chỉ khi không active owned run và same signer established: `adb -s SERIAL install -r APK`. Không uninstall, downgrade/clear, data reset. Pull installed APK sau install; hash bằng candidate, package/version readback đúng.
8. Mở real MainActivity bình thường trên DeX; kiểm Library preserved. Create mới UI → chọn template 1 cell/2 panes → app picker exact target → save unique name. Export/backup qua UI để lấy cell/targets/bounds; actual workspace ID lấy diagnostic value PRODUCT_RUN_WORKSPACE sau explicit Start; export artifact chỉ qualification.
9. Chọn thẻ qualification tên duy nhất; callback truyền actual card ID, đối chiếu exact token workspace ID khi ACTIVE và reopen; tap Mở Embedded (Thử nghiệm). Snapshot entry, explicit Kiểm tra lại; no allocation/permission/auto-Start. Ready mới enable Start. Không dùng debug harness, fixture repo hay inject license.
10. Tap Bắt đầu Embedded một lần. Chờ bounded terminal theo existing timeout; capture ACTIVE/owned diagnostics rồi interaction matrix. Khi unresolved run, không retry để có PASS đẹp hơn.
11. Tap Quay lại một lần; capture terminal diagnostics qua explicit Sao chép chẩn đoán tại product UI nếu có thể. Join token/workspace/gen/start/cleanup operations và exact owned cells/SIDs/displays với CLEAN_CONFIRMED outcomes. IDLE/CLEAN_CONFIRMED mới cho lượt tiếp theo. UI disappearance/dumpsys absence chỉ corroboration.
12. Reopen exact workspace ID từ Library, entry và refresh phải không auto-Start; explicit Start mới có generation mới. Interaction và Back clean lần này cũng capture; không kill process để prove persistence.
13. Lặp bounded scenario còn lại sau authoritative clean. Unsupported/invalid evidence từ A tests/source; unavailable UI chỉ mô phỏng an toàn khi zero ownership và không thay permissions/settings người dùng, nếu thiếu thì NOT VERIFIED. Không gây remote cleanup failure.
14. Nếu clean, kết thúc idle/clean tại Library. Nếu ownership unresolved, STOP và giữ Library blocked; không tạo clean bằng recovery. Giữ qualification workspaces để truy vết. Không cleanup workspace tự động; nếu cần sau evidence chỉ product path exact test ID. So original checkout status/hashes/index unchanged.

## Acceptance matrix / evidence classes

A = local/fake/source; B = controlled Android; C = real production-package MainActivity/navigation/Room journey. Accepted 009/010 A/B/runtime evidence tái sử dụng khi source/assumptions không đổi; không dùng harness evidence 010 thay C011.

| # | Criterion | Evidence | Checkpoint |
|---:|---|---|---|
| 1 | UI create/save → same exact persisted ID later | C UI save, real route/getById, matching ACTIVE IDs generation 1/2 | single PASS; dual later exact-ID NOT VERIFIED |
| 2 | Calculator/Calculator+Waze assignments preserved | C final UI backup cells/targets/bounds | PASS |
| 3 | Library selected valid ID → correct Embedded entry | source + C named route screenshots/diagnostics | PASS for both entries |
| 4 | Entry/refresh no auto-Start/permission | A reused + C entry/refresh screenshots | PASS in observed entries |
| 5 | Current readiness gates explicit Start | A reused + C Ready and enabled explicit Start | PASS for observed Ready; other C states not rerun |
| 6 | One-app Calculator ACTIVE | C ACTIVE diagnostic/screenshot | PASS |
| 7 | Both Calculator+Waze ACTIVE | C START_FAILED/CLEANUP_BLOCKED | NOT VERIFIED; failure observed |
| 8 | Deterministic interaction in intended pane | C single 7+8=15 screenshot | single PASS; dual NOT VERIFIED |
| 9 | Back exact-owned clean → IDLE/CLEAN_CONFIRMED | C single two exact SID/cell/display/gen/op joins | single PASS; dual INCOMPLETE, no clean claim |
| 10 | Reopen after clean no auto-Start; fresh explicit Start | C single same ID/new SID/gen/op | single PASS; dual NOT VERIFIED |
| 11 | Bounded unsupported/unavailable/invalid actions, no false clean | A/B 009/010 reused; C real blocked UI fail-closed | PARTIAL; other C states NOT VERIFIED |
| 12 | Guidance covers six required distinctions | user guide + policy/source review | PASS |
| 13 | Authority/value-only gate/fences/fail-closed preserved | tracked source/build/test diff empty + A reused | PASS within unchanged architecture |
## Verification policy

No new behavior → no invented RED, no duplicated full suite/lint/009–010 device ritual. Build once for new C011 candidate; real S23/DeX run executed through midnight 2026-10-05/06 and stopped at unresolved ownership. Build log has SUCCESS/PASS; wrapper exit code NOT VERIFIED, no duplicate build. On actual patch run owning test RED/GREEN plus affected regression; lint/build only justified. Progress/evidence lives in checkpoint; all unproved C criteria stay NOT VERIFIED.
