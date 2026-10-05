# DWT-VDM-010 — Android/DeX testbed

**DWT-VDM-010 = COMPLETE / PASS. READY FOR COMMIT.** [Final acceptance review](../../verification/dwt-vdm-010/final-acceptance-20261005/final-report.md): 13/13 required criteria đủ evidence; hai fixes có behavioral RED và final regression; NEW independent Calculator+Waze lifecycle PASS. Chưa stage/commit/push/tag; dừng chờ commit authorization. Historical SID release và previous refresh cause vẫn NOT VERIFIED.

Historical SID `0bf0ecec-9e2c-4196-8edb-a076b688edb1` là evidence-only; authoritative release NOT VERIFIED. New testbed không nhả hoặc phục hồi authority cho SID này.

Diagnostic qualification build (không source production patch):

```powershell
python experiments/dwt-vdm-010-host-harness/Prepare-Diagnostics.py
.\gradlew.bat --offline -I experiments/dwt-vdm-010-host-harness/isolated-app-id.init.gradle '-Pdwt.vdm010.diagnostics=true' assembleDebug assembleDebugAndroidTest --console=plain
powershell -NoProfile -ExecutionPolicy Bypass -File experiments/dwt-vdm-010-host-harness/Run-Qualification.ps1 -VerifyArtifactsOnly
```

Generator copy main sources và insert read-only observers; strip markers phải phục hồi nguyên byte originals. Init dùng mirror cho cả Java/Kotlin source sets và từ chối release khi bật diagnostics. Run Capture trước Real để kiểm presence/capture; Capture không Binder/VDM Start.

Real luôn cần `-OnlyMethod` và `-IndependentRunId vdm010-independent-<UUID>`, chỉ một invocation được user phê duyệt. Mỗi output có namespace run ID. Các lệnh Real generic bên dưới là lịch sử; không chạy nếu thiếu new independent authorization/args/capture guards.

Package riêng `com.trancong.dexworkspacetouch.vdm010harness`; instrumentation target `.vdm010harness.test`. Hai Activity debug không exported và từ chối chạy dưới package production. Init script chỉ đổi applicationId cho build được chỉ định; namespace, signing configuration, version và dependencies giữ nguyên.

## Build và guard

Từ worktree 010:

```powershell
.\gradlew.bat --offline -I experiments/dwt-vdm-010-host-harness/isolated-app-id.init.gradle assembleDebug assembleDebugAndroidTest --console=plain
powershell -NoProfile -ExecutionPolicy Bypass -File experiments/dwt-vdm-010-host-harness/Run-Qualification.ps1 -VerifyArtifactsOnly
```

Không cài APK build thiếu init script. Runner kiểm tra allowlist package, app version 9/1.0.0-beta.8, target instrumentation, chữ ký hợp lệ và signer app/test khớp. Nếu package riêng đã có, đọc signer trước update; mismatch thì STOP. Không uninstall/clear data, không target hoặc thay app production.

## B — Android controlled

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File experiments/dwt-vdm-010-host-harness/Run-Qualification.ps1 -Serial DEVICE_SERIAL -DisplayId DEX_DISPLAY_ID
```

Bốn case không skip:

1. Back + recreate cùng PID: host mới STOPPING không runtime/Surface; Back/dispose dùng một close/cleanup; old callback không pop/unlock; exact clean IDLE không tự chạy; explicit Start mới và stale callback/generation bị fence.
2. Synthetic INCOMPLETE giữ CLEANUP_BLOCKED qua recreate/navigation/readiness refresh, đúng cause và CONTROLLED_010.
3. Synthetic UNCERTAIN giữ CLEANUP_BLOCKED, không Start/Classic unlock.
4. Actual SurfaceView GONE/VISIBLE tạo destroy/create; viewport zero/positive inject kiểm tra touch; một stop/close, không resurrect, exact clean về IDLE.

Session và terminal result ở B là controlled. Start/Back gọi explicit handler của debug host. Route wiring riêng, không chứng minh toàn bộ product Compose/navigation. Viewport inject không đại diện cho mọi DeX resize policy. Synthetic blocked chỉ chứng minh lifecycle/UI/gate/action-policy/stale-callback; không chứng minh real remote cleanup failure, hung Binder hay authoritative remote ownership convergence.

## C — Real S23/DeX runtime

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File experiments/dwt-vdm-010-host-harness/Run-Qualification.ps1 -Serial DEVICE_SERIAL -DisplayId DEX_DISPLAY_ID -CaseSet Real
```

C dùng public product Composable và remote session factory hiện có trong package riêng. Workspace fixture chỉ đọc: Calculator hoặc Calculator+Waze, tối đa hai app; không ghi Room hoặc đi qua license activation. Đây là qualification product route trên testbed, không phải toàn bộ production MainActivity/navigation.

Cần Shizuku server và permission cho package testbed. Instrumentation dùng action accessibility trên product UI: refresh, Start, forced recreate cùng PID, refresh không allocation, explicit fresh Start, Surface loss. Assertion gắn exact sourceCell/session receipts và CLEAN_CONFIRMED outcomes với generation/operation; timeout không phải clean evidence. Khi thất bại, lưu trace và chờ lifecycle disposal đạt IDLE hoặc CLEANUP_BLOCKED; unresolved không được coi sạch.

`-OnlyMethod` chỉ nhận tên thuộc case set; runner dừng tại failure đầu tiên. Không cố mở run mới sau kết quả cleanup không chắc chắn.

## Evidence và invocation

Serial/display được chọn từ preflight hiện tại; không hardcode giả định cho lần sau. Lần chạy 2026-10-02: S23 `192.168.1.106:5555`, DeX display `167`.

Mỗi method là một instrumentation invocation riêng; trong từng scenario recreation phải đổi host nhưng giữ PID. Chuẩn bị process riêng giữa case không phải proof process-death recovery; không reset gate CLEANUP_BLOCKED.

Mỗi run mới lưu vào `verification/dwt-vdm-010/android-controlled/<UTC-run>` hoặc `android-real/<UTC-run>`: output method, trace, artifact identity và run manifest. Run trước khi bổ sung manifest có identity APK đọc lại riêng trong device checkpoint. Không ghi đè RED/output trước. APK/log build bị ignore; XML/JSON/trace lưu evidence.

[Checkpoint lịch sử 02/10](../../verification/dwt-vdm-010/device-20261002/checkpoint.md): A/B PASS; C Calculator PASS, Calculator+Waze FAIL/STOP. Hai fix nhỏ có behavioral RED; không có signer/version/configuration change. Required two-app lifecycle NOT VERIFIED; không retry sau blocked.
