# DWT-REL-008 — 1.0.0-beta.12 release candidate

Qualification: PASS — 2026-10-12. Tag/push/publish: HOLD, cần duyệt riêng.

## Phạm vi và provenance

- Branch: `release/1.0-beta`.
- Baseline: `38f4b2496ffec648f9e09c464777ca33fc27f4d6`.
- Release source: `f5724623774326ee066ec20abe4240fa72f428ec`.
- `versionName`: `1.0.0-beta.11` → `1.0.0-beta.12`.
- `versionCode`: `12` → `13`, kế tiếp release HEAD hiện tại.
- Source commit chỉ đổi hai giá trị version trong `app/build.gradle.kts`.
- Commit chứa tài liệu này là docs-only provenance commit sau release source; không rebuild APK cho commit tài liệu.
- Checkout gốc có thay đổi người dùng từ trước, được giữ nguyên. Thực hiện trên linked worktree release hiện hữu.

Không đổi feature semantics, PHY/close backend, Workspace picker, license/security, dependencies/toolchain, signing hay trusted keys. Sử dụng nguyên cơ chế signing và script production release hiện hữu.

## APK cuối

Build bằng `scripts/build-production-release.ps1 -SkipTests` sau khi commit version; full unit đã PASS trước đó. Manifest và BuildConfig trong DEX xác nhận source commit ở trên, `dirty=false`, `release`, channel `beta`, `DEBUG=false`.

- Artifact: `release-output/DexWorkspaceTouch-1.0.0-beta.12-13.apk`.
- SHA-256: `80ab66ccd31e3fad84251050b45354090d36adb6eff23d846f03407393f8c2f3`.
- Signer SHA-256: `19ac0ea99125361b3c2083aaa44c8745ebad9c55fa967642c2b9e5a1086a45e7`.
- Package: `com.trancong.dexworkspacetouch`; minSdk 28, targetSdk 37.
- Version: `1.0.0-beta.12` / `13`; non-debuggable.
- Packaged production backend/update configuration và trusted registry đối chiếu với APK beta.11 production hiện hữu: giữ nguyên.

APK candidate trước commit chỉ dùng cho qualification ban đầu và được lưu riêng trong evidence. Cả hai máy sau đó được cài đúng APK cuối và chạy lại smoke trên bytes bàn giao.

## Verification

| Gate | Kết quả |
| --- | --- |
| Targeted picker (`workspace.apppicker.*`) | PASS: 5 suites, 39 tests |
| Targeted Physical Button + close (`feature.physicalbutton.*`, `workspace.close.*`) | PASS: 15 suites, 193 tests |
| Full unit (`:app:testDebugUnitTest`) | PASS: 183 suites, 1.582 tests |
| `assembleRelease`, production script, lintVitalRelease | PASS |
| Actual APK signer/package/version + packaged DEX/config verification | PASS |
| `git diff --check` | PASS |

Các suite có 0 failure/error/skipped. Không sửa production behavior sau test; source commit và docs-only commit không làm mất hiệu lực các kết quả này.

## Device qualification

Cài bằng `adb install -r`, không uninstall hay clear data. APK cuối trên cả hai máy có SHA-256 khớp artifact, app UID/firstInstallTime/dataDir giữ nguyên, cấu hình enabled AccessibilityService giữ nguyên qua lần cài APK cuối.

| Máy | Upgrade-in-place | Short press Volume Down | Long press Volume Down | DWT/service invariant |
| --- | --- | --- | --- | --- |
| S23 / SM-S918B | PASS: beta.11/code 12 → beta.12/code 13 | PASS: Workspace 9, VietMap Live + YouTube, display 33 | PASS: `CLOSE_COMPLETE`, hai package không còn task | PASS: DWT task 6525/PID 16114 giữ nguyên; service bound, filter-key-events |
| Note 9 / SM-N960N / Android 10 | PASS: beta.11/code 9 → beta.12/code 13 | PASS: Workspace 7, Maps + ServiceFare, host DeX display 2 | PASS: `CLOSE_COMPLETE`, hai package không còn task | PASS: DWT task 2032/PID 28737 giữ nguyên; service bound, filter-key-events |

Nút thật AB Shutter3 được người dùng bấm; service nhận đúng workspace ID đã bind, không thực hiện bind lại. LicenseGate vào Library không yêu cầu activation và các lần short press của APK cuối ghi `licenseAllowed=true state=Allowed`.

Library/config preservation qua candidate upgrade: backup chỉ hash trên thiết bị, chuẩn hóa duy nhất `exportedAtEpochMillis`, không kéo raw personal backup vào repo. Hash trước/sau bằng nhau:

- S23: `4aabbad685e6e581e0127ae1647d66fb007fa9a9bc01caa98e1cb8dbc2fd66d9`.
- Note 9: `f87538af64585dc8af45968eae140af98feada8ed7d7aaa6d40b028d55416027`.

Picker: trên candidate release S23, tìm exact own package và nhãn DWT trả empty state, Chrome vẫn có thể chọn. Note 9 có clone qualification `com.trancong.dexworkspacetouch.swc004` với cùng nhãn DWT và debug launcher; query package-family hiển thị hai card tương ứng hai launcher clone, trong khi inventory có thêm launcher production. Kết hợp runtime inventory/card count với unit/source exact-own-package exclusion; không mở rộng filter sang clone hay đổi semantics. Package identity trực tiếp của từng card Note 9: NOT VERIFIED vì UI chỉ công bố nhãn.

## Quan sát trong qualification

- Cold force-stop do thao tác kiểm thử làm framework gỡ enabled AccessibilityService; đã khôi phục đúng component baseline và xác nhận cài lại APK bằng `install -r` giữ setting. Không dùng force-stop cho smoke APK cuối.
- Short press Note 9 ban đầu bị `BLOCKED` khi service mới khởi tạo chưa ghi nhận host Activity. Đưa DWT về nền rồi resume trên DeX ghi nhận host display 2; candidate và APK cuối sau đó launch/close PASS. Không thay code để vượt gate này.
- Unsaved workspace dùng cho picker đã được bỏ, không lưu thay đổi vào Library. Các cửa sổ fixture được đóng bằng long press; DWT và service còn hoạt động.

Evidence: `release-output/dwt-rel-008/` (Git-ignored), gồm test XML/summary/log, candidate/final artifact verification, installed-package/hash checks, library-preservation JSON, picker XML/inventory và gesture/task/accessibility traces. Manifest cuối: `release-output/release-manifest.json`.

Không tag, push, publish, tạo public update manifest hay deploy backend trong DWT-REL-008.
