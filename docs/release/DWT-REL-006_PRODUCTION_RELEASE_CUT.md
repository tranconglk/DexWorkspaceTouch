# DWT-REL-006 — Production release cut

DWT-REL-006 qualification = PASS / FINAL ARTIFACT READY. Publication = HOLD; Tag = NOT AUTHORIZED.

## Nguồn và artifact

- Accepted baseline: `1daa35af70e9d611f033cdc31c66a9be4f94ecdd` trên `release/1.0-beta`.
- RELEASE_SOURCE_SHA / BUILD_COMMIT: `db9c4bbb884fa63bc4a1e782e6066cdc0b64de85`.
- Source commit: `DWT-REL-006: prepare 1.0.0-beta.11`.
- Source diff duy nhất: `app/build.gradle.kts`, versionName `1.0.0-beta.10` → `1.0.0-beta.11`, versionCode `11` → `12`.
- APK: `DexWorkspaceTouch-1.0.0-beta.11-12.apk`, 28,047,737 bytes; build UTC `2026-10-08T15:46:29Z`.
- FINAL_APK_SHA256: `cf076ae04f0d0ffe09b157ff6b54e02baba9c55f4e049006e5032b3539a59958`.
- Package `com.trancong.dexworkspacetouch`; release/beta; debuggable=false; dirty=false.
- Official signer SHA256: `19ac0ea99125361b3c2083aaa44c8745ebad9c55fa967642c2b9e5a1086a45e7`; apksigner PASS.
- Actual packaged manifest/DEX BuildConfig được kiểm tra bằng aapt2/dexdump: package, version, BUILD_COMMIT, dirty, debuggable và channel PASS.
- Production license configuration/trusted registry giữ nguyên so với APK production trước nâng cấp (SHA256 `95627b8ee33e8e66ab245ebbad7fff9d58d48e617b2cacc49b009a72d0cc4fa0`).
- License endpoint: `https://dexworkspacetouch-license-production.dex-backend.workers.dev`; kid duy nhất `license-signing-v1`, RS256; public SPKI SHA256 `ae8c0a5376ceef82fa3f0123e2788c21cc218bb19fb866aed83813745d392e74`.
- APK không nằm trong Git. Không rebuild sau thay đổi tài liệu. RELEASE_BRANCH_TIP là commit provenance phía sau source; APK vẫn thuộc RELEASE_SOURCE_SHA nêu trên.

## Verification đã hoàn thành

| Kiểm tra | Kết quả |
| --- | --- |
| Release-relevant JVM | PASS: 234 tests / 29 suites; 0 failure/error/skipped |
| Full JVM | PASS: 1,384 tests / 168 suites; 0 failure/error/skipped |
| assembleRelease | PASS |
| lintVitalRelease | PASS |
| Production guard | PASS |
| Package/provenance/config verification | PASS |
| Official signing verification | PASS |

JVM dùng task có sẵn `:app:testDebugUnitTest`. `:app:testReleaseUnitTest` không tồn tại trong project; không sửa variant/toolchain để tạo task. Relevant filters gồm Shizuku platform, WorkspaceRepair, Dock coordinator, preferences, post-Classic runtime và license. Full suite chạy không filter. Pipeline production `scripts/build-production-release.ps1 -SkipTests` tránh lặp lại JVM đã PASS, build từ HEAD source với tracked tree/index sạch.

Evidence cục bộ trong `release-output/` của release-cut worktree: `targeted-debug-jvm.log`, `targeted-jvm-summary.json`, `targeted-unit-results/`, `full-jvm.log`, `full-jvm-summary.json`, `production-build.log`, `release-manifest.json`, `artifact-verification.json`. Test XML full ở `app/build/test-results/testDebugUnitTest/`.

## Migration, preference và Shizuku

`WorkspaceRepairModePreferencesTest` PASS cả targeted/full:

- legacy AUTOMATIC → Auto ON, migration đúng một lần;
- legacy SUGGEST/OFF/absent/corrupt → Auto OFF;
- new Boolean preference có precedence, kể cả corrupt new value fail-closed;
- preference sống qua recreation/refresh mà không đổi shortcut configuration.

Runtime/policy regression PASS: capability không phải user preference; NOT_RUNNING/PERMISSION_MISSING không đổi preference ON. `unavailableAtTriggerSkipsTransportAndLaterReadyDoesNotRepair` và `permissionLostDuringStabilizationSkipsAssessmentWithoutRetry` xác minh safe skip, không assessment/repair/retry sau readiness/resume muộn. `automaticCorrectLayoutOnlyReadsOnceAndDoesNotInvokeRepair` xác minh layout đúng chỉ đọc một lần, không repair. `offDoesNotAssessAndManualRepairStillUsesExistingEngine` giữ Manual với Auto OFF. Resume refresh chỉ đọc readiness và preferences; không phát repair mới.

Release APK thực tế: S22 legacy AUTOMATIC → ON; S23 và Note 9 legacy SUGGEST → OFF. Không thay đổi pairing, quyền hay trạng thái Shizuku để ép test.

REAL DEVICE SHIZUKU-UNAVAILABLE = NOT VERIFIED. Không có điều kiện unavailable sẵn có an toàn trong các máy đã kiểm định; giữ nguyên cấu hình người dùng. Evidence class: automated policy/transport JVM PASS + kiểm tra đường resume/readiness; không suy diễn real-device unavailable PASS.

Direct internal RPC counter trên APK production non-debuggable = NOT VERIFIED. Bằng chứng thiết bị gồm bounds/identity trace và assessment UI; automated transport/policy chứng minh zero unnecessary repair. Không instrument/sửa APK đã chốt.

## In-place qualification

Cùng một APK được cài bằng `adb install -r`; không uninstall, clear data/license hay reset Library/settings. Trên mỗi máy kiểm tra beta.11/12, debuggable=false, app UID/firstInstallTime/dataDir giữ nguyên, hash base.apk khớp FINAL_APK_SHA256. Cold force-stop/start qua LicenseGate vào Library không yêu cầu activation. Library backup chỉ hash trên thiết bị, chuẩn hóa duy nhất timestamp export; không chuyển raw personal backup về repo.

### S22 / Android 16 — PASS

- Library 2 workspace, 0 pin, 6 slot hiển thị / 2 assigned giữ nguyên. Canonical backup SHA256 trước/sau: `cd69205eedce3d60f2f1eef493b86b0e344f820a5ad288f2570b7bf5929d3029`.
- Legacy AUTOMATIC → Auto ON; Dock OFF; Embedded product UI absent; Shizuku READY.
- Chrome/Browser/Tips 25/25/50, Auto ON + Dock CLOSED: final 3/3 exact, task/activity identity giữ nguyên; task IDs 11557/11558/11559.
- Expected bounds: Chrome `[8,8,478,1016]`, Browser `[482,8,958,1016]`, Tips `[962,8,1912,1016]`.
- Chrome/Browser ban đầu Samsung-cascaded, mỗi task đổi bounds một lần; Tips đã đúng, zero bounds change. Stable cuối >16 giây. 146 so sánh unrelated task, zero unrelated bounds change; gutters 4 px, outer margin 8 px. Không quan sát Shizuku app/permission UI.
- Auto OFF giữ layout eligible sai suốt 20 giây, zero auto bounds change. Manual Dock Repair chuyển đúng 3/3, cùng task IDs 11560/11561/11562 và activity identities từ trace OFF; UI `3 repaired, 0 correct`; Auto vẫn OFF sau Manual.
- Khôi phục preferred Auto ON / Dock OFF, chỉ đóng cửa sổ fixture kiểm thử.
- Evidence: `S22-final-result.json`, `S22-auto-stable-analysis.json`, `S22-off-hold-analysis.json`, `S22-off-classic-analysis.json`, `S22-off-manual-analysis.json`, ảnh trạng thái cuối.

### S23 / Android 16 — PASS

- Library 14 workspace, 1 pin; 8/8 shortcut assigned, tên/thứ tự/settings giữ nguyên. Canonical backup SHA256 trước/sau: `37f77bd44768a1aa90196026d9fc8d78e1ccb39be9c50da6b12b1721c47d7c57`.
- Legacy SUGGEST → Auto OFF; Dock OFF; Embedded product UI absent; Shizuku READY.
- Calculator/Clock 50/50, Auto ON + Dock CLOSED: exact `[8,8,958,1136]` / `[962,8,1912,1136]`; 4 px gutter và 8 px outer margin.
- Trace qualification 89 samples / 20.017 giây: task IDs 5718/5719, identity variants=1, zero bounds change, ổn định >19.5 giây; 176 unrelated comparisons, zero unrelated resize.
- Sau trace mới mở Dock để đọc assessment, không bấm Repair: UI `Layout already correct` (LAYOUT_CORRECT).
- Khôi phục Auto OFF / Dock OFF theo baseline; hash APK cuối vẫn khớp FINAL_APK_SHA256.
- Evidence: `S23-final-result.json`, `S23-assessment-fixture-analysis.json`, `S23-layout-correct-expanded.png`, migration/final settings screenshots, library hash JSON.
- Một lần mở nhầm workspace khác khi hộp thoại Classic còn tồn tại là lỗi điều khiển UI; cửa sổ vừa mở đã đóng. Thu lại fixture/assessment đúng sau đó. Trace đầu cũng exact/zero-change; không tính thao tác mở nhầm làm qualification.

### Note 9 / hadesROM / Android 10 — PASS

- Update-in-place beta.10/11 → beta.11/12; cold LicenseGate vào Library, license được giữ; installed bytes khớp FINAL_APK_SHA256; UID/firstInstallTime/dataDir không đổi.
- Library 9 workspace, 0 pin; 6/6 shortcut assigned và settings giữ nguyên. Canonical backup SHA256 trước/sau: `66bdf614412912b2a4e53549e608def470769533720055053ec2bd94abe5a21c`.
- Legacy SUGGEST → Auto OFF, Dock OFF; Embedded product UI absent; Shizuku READY.
- Một lượt fixture Workspace 9 Chrome/Calculator/TikTok, Auto ON + Dock CLOSED: final exact `[8,8,638,1020]` / `[642,8,1278,1020]` / `[1282,8,1912,1020]`; hai gutters 4 px, outer margin 8 px.
- 114 samples / 20.070 giây; task IDs 3425/3426/3427, task/activity identity variants=1, zero bounds change, stable >18.2 giây. 339 unrelated comparisons, zero unrelated resize.
- Mở Dock sau trace để đọc assessment: `Layout already correct`; không bấm Repair. Production Android 10 TaskRecord assessment hoạt động trên fixture exact.
- Fail-closed của parser/correlation được chứng minh bằng `Android10TaskCorrelationTest` (11 tests, 0 failure/error/skipped): task preamble/bounds riêng, live visibility, đúng display/user/mode, hidden target, missing/mismatched/changed activity identity, ambiguous/duplicate task đều từ chối khi không chắc chắn. Không cố tạo điều kiện nguy hiểm trên máy người dùng.
- Khôi phục Auto OFF / Dock OFF và chỉ đóng các cửa sổ smoke; hash APK cuối khớp FINAL_APK_SHA256.
- Evidence: `Note9-final-result.json`, `Note9-auto-dock-closed-analysis.json`, `Note9-dock-assessment.png`, migration/final settings screenshots, library hash JSON.

## Tích hợp và đối chiếu SHA

Cả ba qualification bắt buộc đã PASS. Target duy nhất là `release/1.0-beta`, tích hợp content-preserving fast-forward source + đúng một docs-only provenance commit `DWT-REL-006: record beta.11 production qualification`; normal non-force push chỉ branch này.

RELEASE_SOURCE_SHA là `db9c4bbb884fa63bc4a1e782e6066cdc0b64de85`, commit đã build APK. Commit chứa tài liệu này là provenance commit / RELEASE_BRANCH_TIP phía sau source; tìm SHA bằng `git log -1 --format=%H -- docs/release/DWT-REL-006_PRODUCTION_RELEASE_CUT.md`. SHA thực tế sau integration/push và local/origin ahead/behind được lưu trong `release-output/final-release-result.json` và báo cáo cuối. Không rebuild APK cho docs-only commit.

Release-cut worktree và integration worktree phải clean sau commit. Giữ worktree release-cut để bàn giao APK/evidence trong thư mục Git-ignored `release-output/`. Checkout gốc có thay đổi người dùng từ trước và được giữ nguyên; không reset/clean hoặc đưa các thay đổi này vào release.

Không đổi app id, signer, license/backend/trusted keys, dependencies/toolchain, Room, protocol/whitelist, preference semantics, Classic/Repair/parser/geometry hay Embedded routes/runtime trong REL-006. Không tag/GitHub Release, public upload, Play publish hoặc backend deployment.

Tag = NOT AUTHORIZED
Publication = HOLD

STOP.
