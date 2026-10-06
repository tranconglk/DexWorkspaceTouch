# DWT-VDM-011 — checkpoint STOP

**Trạng thái: STOP / BLOCKED — milestone chưa hoàn tất.** Một explicit Start của workspace Calculator + Waze gặp `START_FAILED`, gate `CLEANUP_BLOCKED`, cleanup `INCOMPLETE`. Chưa có authoritative clean cho owned session đó. Dừng qualification theo yêu cầu hiện hành: “Also STOP if a real run reaches unresolved ownership without authoritative cleanup evidence.” Không retry để lấy PASS.

Checkpoint ngày 2026-10-06, Asia/Saigon. Run bắt đầu tối 2026-10-05; blocked diagnostic UTC `2026-10-05T17:00:11.940Z`, tương đương `2026-10-06T00:00:11.940+07:00`. DWT-VDM-009/010 vẫn CLOSED.

## Thay đổi và scope

- Worktree riêng `D:/AndroidStudioProjects/DexWorkspaceTouch-VDM-011`, branch `qualification/dwt-vdm-011`, base/HEAD `db69bed601eceea12259249c89f0f5276793e425`.
- Thêm [plan/runbook/matrix](../../docs/superpowers/plans/2026-10-05-dwt-vdm-011-production-journey.md), [user guide](../../docs/user/EMBEDDED_WORKSPACE_GUIDE.md), diagnostic/audit/provenance/screenshots trong thư mục này; `smoke-output/.gitignore` giữ APK, backup và scratch ngoài evidence được chọn.
- Không đổi tracked source/build/test; không có production patch. RED/GREEN mới: **N/A**, không có hành vi mới cần test. Runtime failure không được biến thành một patch ngoài scope.
- Không đổi ownership/result authority, AIDL, runtime/renderer/cleanup semantics, Room schema, license/security/signing/version/dependencies/update manifest. Không commit/push/tag/publication.
- [Scope audit](scope-audit.json): checkout gốc HEAD/branch/index/status và hash cả 150 file giữ nguyên; đúng 2 modified probe files + 148 untracked. Qualification không staged file nào.

## Artifact và đường chạy thật

[Artifact identity](artifact-identity.json) ghi package thật `com.trancong.dexworkspacetouch`, version `1.0.0-beta.8` / code `9`, Samsung SM-S918B Android 16, DeX logical display 194. Đây là qualification artifact, không phải release cut/public artifact.

| Thuộc tính | Giá trị |
|---|---|
| APK SHA-256 | `6370cd122f8c5750e230e56c15af5f5ea7a0d252fa019371648c54ab1715852b` |
| Installed APK readback SHA-256 | cùng SHA-256 candidate trên |
| Signer SHA-256 | `19ac0ea99125361b3c2083aaa44c8745ebad9c55fa967642c2b9e5a1086a45e7` |
| Pre-install APK SHA-256 | `246b7df53d14935fbd92925ba7521f7f6216c4ca3b46d16175bbfa25fd970af6` |
| Install | apksigner xác nhận same signer trước `install -r`; `Success`; package/version/hash readback đúng |
| Build | unchanged `scripts/build-device-smoke.ps1`; log ghi `BUILD SUCCESSFUL` và `DEVICE SMOKE BUILD: PASS` |

[Build status excerpt](artifact-build-status.txt) chỉ chứa trạng thái/hash/version, không chứa signing secrets. Tool wrapper session 25932 chưa trả completion/exit code: **wrapper exit NOT VERIFIED**, không chạy lại build. Identity/install và hành trình device có bằng chứng độc lập.

Real MainActivity → LicenseGate hiện hành → TouchNavigation → Library callback dùng `workspace.id` → product route/loader `repository.getById` → Room repository thật. Save UI callback trở về Library sau persistence thành công. Không harness Activity, fixture repository, record injection hoặc license injection. PID MainActivity 4324 trước Start và ở evidence cuối; không process-death proof.

## Hành trình Calculator

Workspace `DWT-VDM-011 CAL 20261005-R01`, ID `60a45efc-6a4f-4d0d-9a52-42fd7a26e29e`, cell `cell`. Tạo qua UI “Toàn màn hình”, app picker Samsung Calculator, Save, chọn lại từ Library; target đọc lại trong final backup: `com.sec.android.app.popupcalculator/com.sec.android.app.popupcalculator.Calculator`, bounds normalized `[0,0,1,1]`.

Entry và explicit “Kiểm tra lại” không tạo pane/auto-Start hoặc tự xin quyền; Ready bật explicit Start. Tap Start một lần → ACTIVE. Tương tác đã định trước C → 7 → + → 8 → = trong đúng pane `[16,298][1904,1128]`, kết quả nhìn thấy **15**: [ảnh kết quả](screenshots/single-result-15.png), [ACTIVE trước tương tác](screenshots/single-after-start.png).

| Lượt | Generation/start/cleanup op | Exact owned SID | Cell/display | Terminal |
|---|---|---|---|---|
| đầu | 1 / 1 / 2 | `d8461d4c-6364-415b-958c-72586f8ffbdc` | cell / 195 | IDLE, STOPPED, CLEAN_CONFIRMED |
| explicit reopen | 2 / 3 / 4 | `f8ec1d38-bf78-4db4-828d-1c84b6155260` | cell / 196 | IDLE, STOPPED, CLEAN_CONFIRMED |

Exact-owned join: [active 1](single-active-diagnostic.txt) → [clean 1](single-clean-diagnostic.txt); [fresh active](single-fresh-active-diagnostic.txt) → [fresh clean](single-fresh-clean-diagnostic.txt). Mỗi Back một lần, cleanup outcome cùng cell, SID/display/gen/start op khớp; cleanup op 2/4. Clean snapshot `workspace_id=unknown` vì token đã release, giữ nguyên và không tự điền ID. Hai ACTIVE snapshot cùng exact workspace ID, lượt mới khác generation/start op/SID/display.

Sau clean, chọn lại Library → [reopen trước Start](screenshots/single-reopen-before-start.png) → [refresh trước Start](screenshots/single-reopen-refreshed.png) không tự chạy; chỉ explicit Start mới tạo [fresh ACTIVE](screenshots/single-fresh-active.png). Không kill/restart process để chứng minh persistence.

## Hành trình Calculator + Waze và STOP

Workspace `DWT-VDM-011 CAL+WAZE 20261005-R01`, ID từ product diagnostic `fc3b92c6-900a-4111-89ef-910ab90d400e`. Tạo “2 Cột”, Calculator trái `cell_a` bounds `[0,0,0.5,1]`; Waze phải `cell_b` bounds `[0.5,0,1,1]`, target `com.waze/com.waze.FreeMapAppActivity`. [Assignment UI](screenshots/dual-assigned.png), [Library sau Save](screenshots/dual-saved-library.png) và final UI backup xác nhận targets/bounds.

[Entry](screenshots/dual-entry.png) và [refresh Ready](screenshots/dual-refreshed.png) chưa chạy app, không tự xin quyền. Một explicit Start → [CLEANUP_BLOCKED](screenshots/dual-after-start.png). [Diagnostic nguyên văn](dual-blocked-diagnostic.txt):

- generation `3`, start operation `5`, cleanup operation `unknown`;
- `START_FAILED`, `RuntimeStartFailed`, source `cell_a`, blocked cause `UNKNOWN`;
- owned SID `db621ed2-c769-408e-b901-7679ee03bd4f`, source cell `cell_a`, item phase `FAILED`, display `unknown`;
- allocation `POSSIBLE_OR_OWNED`, cleanup `INCOMPLETE`, exact cell cleanup outcome `INCOMPLETE/START_FAILED`;
- remote VDM/task/resource IDs `unknown`; chỉ có một owned item/result, không suy Waze đã được launch.

Calculator trái C→5+6=11 và Waze phải mở local menu/search panel là interactions đã định trước nhưng **chưa thực hiện** do Start không đạt ACTIVE. Both ACTIVE/touch/Back clean/reopen dual: **NOT VERIFIED**. Không suy tài nguyên đã được release từ việc pane biến mất/local terminal/Library xuất hiện.

Sau STOP chỉ explicit copy diagnostic → safe Back → paste đúng nội dung đã copy vào Library search để thu text, clear search/ẩn bàn phím, backup read-only để audit preservation và đóng xác nhận backup. Không retry/new Start, cleanup retry, restart/kill, global reconcile hoặc recovery. [Library cuối](screenshots/final-blocked-library.png) giữ banner unresolved, Embedded mới và Classic bị chặn; safe status action còn sẵn. License/Shizuku không bị đổi để vượt gate.

## User data và bằng chứng

[Library preservation](library-preservation.json): backup qua UI trước/sau có 11 → 13 workspace; so từng record name/schema/canvas theo multiset xác nhận **11/11 giữ nguyên**, chỉ thêm hai workspace qualification trên. Không overwrite/delete workspace cũ, clear data, uninstall, reset settings, activate/rebind/reset license hoặc gọi admin API. Hai workspace qualification được giữ để truy vết; không xóa khi remote ownership unresolved.

Format backup chủ ý bỏ workspace IDs và pin/settings state. Vì vậy exact-ID preservation của mọi workspace cũ và toàn bộ settings **NOT VERIFIED bằng export**. Exact ID single được chứng minh qua product diagnostics hai lượt và route source; dual chỉ có ID của lượt failed, chưa có later-run same-ID proof. Giữ backup đầy đủ trong ignored scratch; checkpoint chỉ lưu audit/canvas của qualification, không lưu danh sách private Downloads.

Chẩn đoán chỉ được copy khi tap nút “Sao chép chẩn đoán”; không đọc clipboard history hay nội dung khác. UI collector đôi lúc null dump/accessibility bounds bị clip theo phone viewport, đã dùng screenshot DeX để xác định vị trí nút; không coi lỗi collector là lỗi runtime và không retry Start vì collector.

## Regression và acceptance

A = local/fake/source; B = controlled Android; C = real production-package journey. Không đổi source assumptions nên tái sử dụng accepted 009/010; không chạy lại full suite/lint hay device ritual. [Affected regression reused](reused-regression.json): 8 classes / 124 tests, zero failures/errors/skipped từ accepted 010 (không phải execution mới). Full accepted summary có 1165 tests / 141 classes; không quảng bá thành kết quả mới 011.

Accepted [009 S23 hardening](../dwt-vdm-009/s23-hardening-proof.md) hỗ trợ readiness/blocked/Classic policy trong phạm vi cũ. [010 final report](../dwt-vdm-010/final-acceptance-20261005/final-report.md) phân biệt controlled harness/fixture; không dùng harness đó thay cho MainActivity/Room C011. Real CLEANUP_BLOCKED lần này chứng minh product giữ khóa, không chứng minh remote cleanup.

| # | PASS criterion | Checkpoint |
|---:|---|---|
| 1 | UI save/persist và later same exact identity | single PASS; dual later identity NOT VERIFIED |
| 2 | Assignments Calculator / Calculator+Waze preserved | PASS, final real UI backup |
| 3 | Correct Library Embedded entry | PASS cho hai workspace đã chọn |
| 4 | Entry/refresh no auto-Start/automatic permission | PASS trong các lần quan sát |
| 5 | Current readiness gates explicit Start | PASS cho Ready quan sát; các C state khác chưa chạy lại |
| 6 | One-app ACTIVE | PASS |
| 7 | Two-app both ACTIVE | NOT VERIFIED; START_FAILED quan sát |
| 8 | Visible deterministic intended-pane interaction | single PASS (15); dual NOT VERIFIED |
| 9 | Exact-owned Back clean → IDLE/CLEAN_CONFIRMED | single PASS hai lượt; dual INCOMPLETE |
| 10 | Reopen after clean, new execution only explicit Start | single PASS; dual NOT VERIFIED |
| 11 | Bounded unsupported/unavailable/invalid messaging/actions | PARTIAL: A/B reused + C real blocked; other C states NOT VERIFIED |
| 12 | Classic/Embedded, experimental, readiness, Start, blocked guidance | PASS, guide đối chiếu source/policy |
| 13 | No weakening authority/value-only gate/fences/fail-closed | PASS trong unchanged source architecture + A reused |

## NOT VERIFIED và bước tiếp theo

Nguyên nhân exception/raw remote của Start failed; display/VDM/task/resource IDs đang unknown; remote release/authoritative clean của SID blocked; both ACTIVE/touch/clean/reopen dual; later exact-ID dual; các unavailable/unsupported/invalid C011 scenario chưa chạy; wrapper build exit code; old-workspace IDs và settings ngoài format export. Milestone **không PASS / không CLOSED**.

Checkpoint dừng tại unresolved real-device ownership. Bước tiếp theo cần task/scope riêng để chẩn đoán nguyên nhân và quyết định xử lý ownership; trước khi có authoritative clean không tiếp tục 011 device run. Nếu giải pháp cần đổi runtime/remote protocol/cleanup authority hoặc recovery architecture, đó là STOP boundary yêu cầu phạm vi được phê duyệt riêng. Không tự chọn restart/kill/clear/reconcile để mở khóa.
