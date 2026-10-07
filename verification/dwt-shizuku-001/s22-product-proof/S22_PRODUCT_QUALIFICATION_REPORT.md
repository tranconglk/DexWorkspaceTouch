# DWT-SHIZUKU-001 — S22 product qualification

Ngày 2026-10-07 (Asia/Saigon). **S22: PASS. Milestone tổng: PARTIAL**, còn proof sản phẩm thật S23; S23 được hoãn theo chỉ dẫn hiện tại.

## Artifact và LicenseGate

- Package thật: `com.trancong.dexworkspacetouch`; S22 SM-S908E, SDK 36, One UI `80000`.
- APK qualification SHA-256: `708e4df09c1aea4e232e5655b981ef457b96d3b5d85fb36397fee9e1ccd14b68`.
- Dùng API, registry `license-signing-v1` / RS256 và signer DWT hợp lệ đã có. Chi tiết build/install: [artifact report](../artifact-license-unblock/ARTIFACT_LICENSE_UNBLOCK_REPORT.md).
- Người dùng xác nhận trình gõ làm sai key trước đó; sau khi nhập đúng, kích hoạt thành công qua LicenseGate bình thường.
- Quan sát Workspace Library, sau đó force-stop riêng DWT và mở MainActivity bình thường: vẫn vào Workspace Library. [Bằng chứng startup](license-startup-proof.json).
- Verification token được xác nhận qua đường activation và bootstrap có verifier bắt buộc trong source không đổi. Không đọc/xuất token hoặc license plaintext; không bypass, inject state hoặc sửa issuer/backend.

## Luồng sản phẩm thật

Fixture được nhập bằng chức năng **Tệp → Nhập workspace** của sản phẩm, từ [file .dwt](S22-Chrome-Browser-Tips-25-25-50.dwt). Ba ứng dụng: Chrome 25%, Samsung Browser 25%, Tips 50%. Không ghi trực tiếp Room hoặc SharedPreferences. Mode được chọn bằng radio thật trên Car Mode; workspace mở bằng **Mở** trong Workspace Library trên DeX. Manual dùng click input thật vào hàng Repair của Floating Dock, không gọi test hook hoặc callback giả.

Display logic `14`, usable area `1920 × 1024`. Bố cục cuối đúng tại zero pixels:

| Ô | Classic sai sau khi mở | Sau Repair |
|---|---|---|
| Chrome 25% | `[728,6,1192,1014]` | `[8,8,472,1016]` |
| Browser 25% | `[1456,16,1920,1024]` | `[488,8,952,1016]` |
| Tips 50% | `[0,0,944,1008]` | `[968,8,1912,1016]` |

Trước mỗi lượt, chỉ force-stop ba package fixture đã dùng trong qualification, không clear data. Cấp overlay và quyền Shizuku bằng cơ chế Android/Shizuku bình thường trong phần setup; setup nằm ngoài khoảng đo. Không dùng `.sw001` để chạy bất kỳ proof sản phẩm nào.

## Kết quả

| Scenario | Task IDs: Chrome / Browser / Tips | Bằng chứng | Kết quả |
|---|---|---|---|
| AUTOMATIC | `11362 / 11363 / 11364` | Classic có cả ba ô sai đồng thời → automatic repair, không click Repair → dock **3 repaired, 0 correct** | PASS |
| SUGGEST trước click | `11365 / 11366 / 11367` | Dock **Repair available**; bounds sai không đổi sau khi Classic ổn định, thêm khoảng hold 7 giây | PASS: zero mutation trước click |
| SUGGEST, click Repair thật | `11365 / 11366 / 11367` | Dock **3 repaired, 0 correct**, ba bounds đúng, giữ IDs | PASS |
| OFF trước click | `11368 / 11369 / 11370` | Mode OFF; dock giữ Repair, không assessment suggestion/auto; bounds sai giữ nguyên, thêm hold 7 giây | PASS |
| OFF, Manual Repair thật | `11368 / 11369 / 11370` | Dock **3 repaired, 0 correct**, ba bounds đúng, giữ IDs | PASS |

Mỗi khoảng trace có **0 thay đổi bounds của task ngoài fixture** và **0 START của app Shizuku**. Không có shell resize hoặc thao tác đổi vị trí cửa sổ trong các proof cuối cùng. Automatic đi qua `ExistingWorkspaceRepair` theo wiring/policy source đã đóng băng; policy chỉ cho phép automatic sau `REPAIR_AVAILABLE`.

### Phương pháp và giới hạn bằng chứng

ADB chỉ quan sát task snapshots và event `ActivityTaskManager START`, cộng screenshot UI thật. Verifier kiểm tra cả ba ô sai đồng thời, cùng task ID xuyên Repair, bounds cuối đúng, no-click không đổi bounds và không resize task khác. Không xuất raw dumpsys intent, key/token hoặc request body.

Số assessment/automatic invocation nội bộ không được instrument trực tiếp trên APK ký chính thức không debuggable. Kết luận OFF không assessment dựa trên early return `WorkspaceRepairSession.classicLaunchCompleted`, byte source không đổi và regression đã PASS `offDoesNotAssessAndManualRepairStillUsesExistingEngine` (assert zero commands), kết hợp OFF UI và no-mutation trên thiết bị. Không thay artifact/source chỉ để thêm instrumentation. Các số zero resize nêu trên là quan sát bounds/event trong trace, được đối chiếu với policy/engine đã qualification.

## Các lượt chuẩn bị và quan sát khác

Lưu nguyên bằng chứng các lượt trước, không tính thay cho proof cuối:

- Dock Classic có lượt Chrome đúng sẵn: **2 repaired, 1 correct**. Đây là skip đúng của engine; không tính thành “3 repaired”.
- Một lượt mở lại khi task fixture cũ còn tồn tại tạo ambiguity; không ép resize task mơ hồ.
- `automatic-home-entry` có Tips tạm chuyển sang Samsung account activity; trả **2/3, Tips UNRESOLVED**, không báo full success. Lượt `automatic-home-stable` sau đó không có chuyển activity này và đạt **3/3 repaired**. Không thay cơ chế fail-closed hoặc thêm retry.
- One UI có thể báo bounds yêu cầu lúc task vừa tạo, rồi chuyển sang bounds Classic sai; verifier dùng sample cả ba cửa sổ đã xuất hiện và sai, không coi geometry trong giai đoạn tạo cửa sổ là Repair.

Không có defect Workspace Control được xác lập từ các quan sát này; **không cần implementation bổ sung**.

## Source freeze và phạm vi còn lại

- [Verifier](verify-device-proof.mjs): PASS. [Summary máy đọc được](s22-qualification-summary.json).
- 36/36 source/test files khớp SHA-256 snapshot đã chấp nhận; **zero source changes** trong continuation.
- Regression 188 tests / 19 suites đã PASS được tái sử dụng, không chạy lại vì byte không đổi.
- `assembleRelease` của qualification artifact đã PASS bằng production config/signing material hiện có; production guard giữ nguyên. Không đổi version/signing/release config và không publish release.
- Sau proof, mode được trả về SUGGEST bằng UI: [screenshot](final-mode-restored-suggest.png). Fixture và dữ liệu app được giữ lại.
- **S23 product proof: NOT VERIFIED / deferred**. Không cài/gỡ/clear data trên S23 trong bước này.
- Không commit/push/tag. Chỉ khi S23 real-product proof cũng PASS mới đủ kết luận milestone **PASS — product integration viable**.

Screenshot chính: [Automatic](automatic-home-stable.png), [Suggest trước click](suggest-before-repair.png), [Suggest sau Repair](suggest-after-repair.png), [OFF trước click](off-before-repair.png), [OFF sau Repair](off-after-repair.png). Trace tương ứng được giữ cùng thư mục.
