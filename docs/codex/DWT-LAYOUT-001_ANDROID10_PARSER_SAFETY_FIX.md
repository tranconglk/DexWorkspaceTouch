# DWT-LAYOUT-001 — Android 10 parser safety fix

Ngày: 2026-10-08. Worktree: `.worktrees/dwt-layout-001-minimal-gutter`.
Base: `1d7cf5eb7b6ed2741d92eb7f22882e82e1d86b68`.
Trạng thái: **READY FOR PARSER FIX REVIEW**.
Publication: **HOLD**. Commit: **NOT YET**. Không commit/push/tag.

## Phạm vi và thiết kế

Chỉ sửa production `WorkspaceTaskCorrelation.kt` và `ExistingWorkspaceRepair.kt`.
Parser dùng các context nhỏ cho stack, preamble/bounds, task và activity; hai
đường correlation đọc cùng structural evidence qua hàm internal `observe`.
Không đổi public API, `sameIdentity`, launchIdentifier, admission, policy,
revalidation, stable readback, geometry, license/signing, version hoặc release branch.

1. Display boundary dùng dòng trimmed; malformed boundary hủy display kế thừa và child contexts.
2. Preamble khóa ID/indent; bounds malformed/xung đột và malformed task boundary không thể phục hồi bằng dòng đến sau.
3. Mỗi ActivityRecord có context riêng; visibility mâu thuẫn hoặc ngoài context không được chấp nhận.
4. Legacy freeform yêu cầu StackId khớp stack hiện tại; missing/malformed relationship không mượn mode.
5. Matching ActivityRecord phải duy nhất; token trùng, owner/user mâu thuẫn làm observation không hợp lệ.

Bounds lặp lại giống hệt chỉ được chấp nhận trong cùng preamble ID/indent chưa
bị invalid. Activity không liên quan với token khác vẫn được giữ trong identity
khi owner/user đúng và matching activity duy nhất.

## Ledger kiểm chứng

- [x] Thêm `Android10ParserSafetyTest.kt`: 37 test, gồm bảy review probe.
- [x] Behavioral RED: 37 test, 23 failure, 0 error; cả bảy review probe phát resize vào fake shell trên parser cũ.
- [x] GREEN: 37 safety + 11 compatibility = 48/48 PASS.
- [x] Affected regression: 245/245 PASS, gồm Shizuku/Repair, mutation/admission, modern correlation và geometry.
- [x] Full regression: 1.368/1.368 PASS, 167 suites, 0 failure/error/skipped.
- [x] 11 file gutter đã chấp nhận: hash giữ nguyên.
- [x] `assembleDebug` và `assembleDebugAndroidTest`: PASS.
- [x] Shizuku/Repair, gồm policy/session: **137/137 PASS**.
- [x] Geometry/bounds và gutter consumer: **101/101 PASS**, nằm trong affected total.
- [x] `assembleRelease`, signer và `lintVitalRelease`: PASS.
- [x] S22 Automatic 25/25/50: 3/3 PASS; đã thực hiện trước.
- [x] S23 Classic-correct 50/50: LAYOUT_CORRECT, zero Repair.
- [x] Note 9 Automatic/Manual ba cột: 3/3 PASS mỗi lượt.
- [x] Restore SUGGEST / Dock OFF trên cả ba máy; Slot 8 S23 trả về Workspace 8.
- [x] `git diff --check` PASS; source/frozen hashes đối chiếu; release branch giữ nguyên.

## Bằng chứng

Artifacts mới ở `smoke-output/dwt-layout-001/parser-safety-fix/`.
`baseline.json` giữ hash frozen files và release HEAD/status trước sửa.
`*.before.kt` là source trước safety fix để tạo corrective diff độc lập với
gutter/parser compatibility đã tồn tại. RED XML/log và GREEN XML được giữ riêng;
`affected-xml/` và `full-xml/` giữ kết quả từng suite.

Comparison trên dump baseline trước smoke có 0 external visible freeform
candidate ở cả ba máy; không dùng kết quả đó để khẳng định qualification fixture
Android 16. Modern correlation regression và bounded smoke S22/S23 dưới đây
là evidence thực tế.

## Behavioral RED → GREEN

Thêm test trước khi sửa production. RED: 37 tests, 23 failures, 0 errors.
Cả bảy probe phát resize vào fake shell trên source cũ. Sau sửa, từng probe
assert không selectable/UNRESOLVED và **zero fake-shell resize**.

| Probe (`reviewProbe…`) | Trước | Sau |
| --- | --- | --- |
| IndentedDisplayCannotInheritExternalDisplay | UNSAFE / RED | UNRESOLVED, zero resize |
| MalformedTaskBoundaryCannotReusePreamble | UNSAFE / RED | UNRESOLVED, zero resize |
| ConflictingBoundsCannotUseLastValue | UNSAFE / RED | UNRESOLVED, zero resize |
| MalformedOtherActivityCannotUpgradeVisibility | UNSAFE / RED | UNRESOLVED, zero resize |
| ConflictingVisibilityIsUnresolved | UNSAFE / RED | UNRESOLVED, zero resize |
| StackIdMismatchCannotBorrowFreeformMode | UNSAFE / RED | UNRESOLVED, zero resize |
| DuplicateActivityIdentityIsUnresolved | UNSAFE / RED | UNRESOLVED, zero resize |

37 safety tests còn kiểm tra malformed/missing boundaries, stale/cross-display/
cross-stack bounds, missing/conflicting visibility, malformed stack relationship,
duplicate tokens/owners/users/components và positive modern/legacy contexts.
Exact component/user/display, launchIdentifier và ambiguity giữ fail-closed.
11 compatibility tests trong `Android10TaskCorrelationTest.kt` giữ nguyên hash.

## Bounded device smoke

Thực hiện S22 trước, S23 tiếp theo, Note 9 sau khi người dùng chuyển DeX.
Các trace sau là lượt hợp lệ, có screenshot Dock và readback khoảng 20 giây.
Không dùng shell resize để tạo PASS; sai lệch Manual tạo bằng kéo viền UI.

| Máy / scenario | Kết quả / task IDs | Stable tối thiểu | Audit ngoài fixture |
| --- | --- | --- | --- |
| S22 Android 16, display 24, Automatic 25/25/50 | **3/3 PASS**; 11461/11462/11463; `3 repaired, 0 correct`; identity giữ nguyên | 15.258 s | 1.020 comparisons, 0 changes |
| S23 Android 16, display 6, Classic-correct 50/50, Automatic policy | **LAYOUT_CORRECT**; 5654/5655; `Layout already correct`; zero Repair, zero bounds changes, identity giữ nguyên | 19.601 s | 715 comparisons, 0 changes |
| Note 9 hadesROM Android 10, display 2, Automatic ba cột | **3/3 PASS**; Chrome 3355 từ 50/50 về cột trái; Calculator 3358, TikTok 3359; `1 repaired, 2 correct`; identity giữ nguyên | 17.160 s | 200 comparisons, 0 changes |
| Note 9, SUGGEST + Manual ba cột | **3/3 PASS**; Calculator 3358 `[963,8,1278,1020]` → `[642,8,1278,1020]`; `1 repaired, 2 correct`; giữ ba task/identity từ Automatic | 19.730 s | 230 comparisons, 0 changes |

Bounds cuối Note 9: `[8,8,638,1020]`, `[642,8,1278,1020]`,
`[1282,8,1912,1020]`; hai gutter 4 px. TikTok thực tế:
`com.ss.android.ugc.trill/com.ss.android.ugc.aweme.splash.SplashActivity`.
S22: `[8,8,478,1016]`, `[482,8,958,1016]`, `[962,8,1912,1016]`.
S23: `[8,8,958,1136]`, `[962,8,1912,1136]`.

Tổng audit bốn lượt hợp lệ: **2.165 comparisons, 0 unrelated bounds/display/mode
changes**. Đây là readback audit trên task có cùng object identity giữa các mẫu,
không phải capture mọi Binder command. S23 zero Repair được đối chiếu bằng
LAYOUT_CORRECT, code path không gọi Auto Repair ở trạng thái đó, và trace giữ bounds.

Artifacts: `s22-auto-dock-*`, `s23-clean-final-*`, `note9-auto-*`,
`note9-manual-*`. Cả ba máy khôi phục **SUGGEST / Dock OFF**; Slot 8 S23 trả về
`Workspace 8`. XML policy và screenshot `*-restored.png` giữ bằng chứng khôi phục.

Lượt chuẩn bị có task trùng, luồng Library/Car Mode chỉ chạy Classic, hoặc
Shizuku chưa chạy được giữ riêng và không tính PASS. S23 trước khi dọn fixture
từng trả partial/UNRESOLVED; Note 9 có hai task test phát sinh khi mở lại dưới
SUGGEST, đã đóng đúng stack 93/94 và giữ task gốc 3358/3359 cho Manual.
Không sửa production để vượt qua các trạng thái chuẩn bị này.

## Corrective diff, source hashes và release audit

[Corrective diff](../../smoke-output/dwt-layout-001/parser-safety-fix/corrective.patch)
so với source ngay trước safety fix, chỉ gồm hai production files và test safety
mới; không gộp lại gutter đã chấp nhận. Production files nằm tại
`app/src/main/java/com/trancong/dexworkspacetouch/platform/launch/shizuku/`.
Test safety nằm tại package tương ứng dưới `app/src/test/java/`.
[Manifest](../../smoke-output/dwt-layout-001/parser-safety-fix/review-manifest.json)
giữ exact paths/hashes, XML totals, RED/GREEN probes và release audit.

| Source | SHA-256 |
| --- | --- |
| WorkspaceTaskCorrelation.kt | `2304bcc062b8d941237f4d5c3c212d7d90e11343e054990f9917be3531e1a1e0` |
| ExistingWorkspaceRepair.kt | `998ca54a925b93001e392842db064a4cb430d14c4ce23fff8f6ba6e229edf6cb` |
| Android10ParserSafetyTest.kt | `0f42a5fe06bee07a1573eb659d965828ddf495c863ea41b7f038ec5e07af0360` |

APK `DWT-parser-safety-fix.apk`, SHA-256
`51c6f996f7063e14f5b159c4da00c853959c4393e4c93eadf6b92457750a4a56`.
Package `com.trancong.dexworkspacetouch`, version `1.0.0-beta.9`, code `10`.
Signer SHA-256 giữ nguyên:
`19ac0ea99125361b3c2083aaa44c8745ebad9c55fa967642c2b9e5a1086a45e7`.
Cùng APK cài bằng `adb install -r` trên cả ba máy; không clear data/license.

Release worktree `dwt-rel-004-final`: HEAD vẫn
`1d7cf5eb7b6ed2741d92eb7f22882e82e1d86b68`, status clean, bằng baseline.
Branch task giữ nguyên HEAD. 11 file gutter và compatibility test giữ hash;
chưa stage/commit/push/tag.

**READY FOR PARSER FIX REVIEW — Publication HOLD — Commit NOT YET.**
