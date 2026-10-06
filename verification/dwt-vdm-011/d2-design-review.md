# DWT-VDM-011-D2 — Fresh testbed & diagnostic retention design review

Ngày: 2026-10-06. Phân loại: DESIGN / PLANNING ONLY.
Source HEAD: `db69bed601eceea12259249c89f0f5276793e425`.

## 1. Quyết định đề xuất

**Khuyến nghị A: một thiết bị Samsung/DeX vật lý khác, ưu tiên S23 Ultra/Android 16 tương đương môi trường đã chấp nhận. Trạng thái 011 hiện tại: REMAIN BLOCKED.** Chỉ đề xuất CONTINUE trên testbed mới sau khi các prerequisite bên dưới được đáp ứng và có scope cho lần thực thi tiếp theo. D2 không cho phép thực thi.

FULL 011 proof vẫn khả thi về mặt thiết kế mà không đổi kiến trúc ownership: tạo ownership mới trên thiết bị độc lập, chạy nguyên journey thật và dùng đường clean hiện có nếu run thành công. Chưa có bằng chứng thiết bị thứ hai, license hợp lệ hoặc dual journey thành công; tính khả thi này không phải PASS hay bảo đảm Start sẽ thành công.

Không cần làm C trước A. Nếu cần chẩn đoán raw failure bằng C, dùng một testbed độc lập đủ điều kiện cho C. Không lập kế hoạch “C blocked → đổi package/process → A trên cùng điện thoại”; unresolved ownership của C cũng phải được giữ riêng và chặn việc coi thiết bị đó là testbed sạch.

Nếu không có thiết bị/license đáp ứng, giữ BLOCKED; E là phương án quyết định phạm vi sau này, không tự đóng milestone trong D2.

## 2. Trạng thái và bằng chứng lịch sử được giữ nguyên

- DWT-VDM-011: **IN PROGRESS / BLOCKED_BY_UNRESOLVED_OWNERSHIP**; D1 outcome: **D1-C**.
- SID: `db621ed2-c769-408e-b901-7679ee03bd4f`; workspace `DWT-VDM-011 CAL+WAZE 20261005-R01`, ID `fc3b92c6-900a-4111-89ef-910ab90d400e`.
- `cell_a`, product generation 3, product Start operation 5; cleanup operation và private IPC/lease operation **NOT VERIFIED**. Hai namespace operation không được đồng nhất.
- START ROOT CAUSE: **NOT VERIFIED**.
- AUTHORITATIVE RELEASE: **NOT VERIFIED**.
- DEVICE RETEST ELIGIBILITY của thiết bị/run lịch sử: **NOT ELIGIBLE**.
- Giữ Calculator PASS đã được chấp nhận; Calculator + Waze ACTIVE/interaction/exact-owned clean/reopen vẫn **NOT VERIFIED**.

SID lịch sử vĩnh viễn chỉ dùng làm evidence, trừ khi tìm được một record exact-owned authoritative cleanup **đã tồn tại**. Không tạo record mới bằng retry, reconcile, reset gate, process mới, restart/PID, inventory hoặc UNKNOWN_SESSION. Không sửa checkpoint/manifest cũ; [D1 checkpoint](d1-forensics/checkpoint.md) và [011 checkpoint](checkpoint.md) vẫn giữ nguyên kết luận.

FULL proof trên thiết bị mới chỉ chứng minh journey trong môi trường mới đã ghi nhận. Nó không xác định nguyên nhân, release tài nguyên hoặc mở khóa run cũ; cũng không chứng minh dual journey trên chiếc S23 cũ hay toàn bộ ma trận OEM.

## 3. Phân loại A–E theo final-011 eligibility

`FULL 011 PROOF` dưới đây nghĩa là phương án có thể tạo bằng chứng đủ nếu prerequisite và acceptance đều đạt; không có phương án nào đã tạo proof mới trong D2.

| Phương án | Phân loại | Kết luận |
|---|---|---|
| A. Thiết bị Android/DeX vật lý khác, real package, same signer, current HEAD, data/ownership độc lập | **FULL 011 PROOF — có điều kiện** | Lựa chọn duy nhất hiện phù hợp để tiếp tục đủ journey mà không đổi accepted architecture. Ưu tiên S23 tương đương; thiết bị/model khác cần chấp nhận rõ môi trường proof trước khi gọi đó là proof cho scope S23. |
| B. Android user/profile khác trên thiết bị đang blocked | **NOT ACCEPTABLE tại HEAD hiện tại** | App data tách theo user chưa đủ chứng minh remote ownership độc lập; remote launch/association hiện hardcode user 0. Không đủ điều kiện làm fresh testbed trên thiết bị này. |
| C. Qualification application / source overlay | **DIAGNOSTIC ONLY** | Hữu ích cho raw Start/rollback của NEW runs trên testbed đủ điều kiện riêng. Không tự thay thế real package/MainActivity/navigation/Room/license/signing proof. |
| D. Late cleanup acknowledgement / recovery owner / persistent ownership journal / reconcile-global cleanup | **NOT ACCEPTABLE trong D2 và scope 011 hiện tại** | Đổi authority/recovery/runtime contract; Charter STOP. Chỉ là hướng kiến trúc cần review riêng. |
| E. Suspend/close 011 PARTIAL/BLOCKED | **NOT ACCEPTABLE như FULL proof** | Là lựa chọn quản lý milestone hợp lệ nếu thiếu testbed; không chứng minh các acceptance còn thiếu. Giữ nguyên Calculator PASS và dual NOT VERIFIED. |

### Đối chiếu từng tiêu chí còn thiếu

| Tiêu chí | A | B | C | D | E |
|---|---|---|---|---|---|
| Calculator + Waze cùng ACTIVE | Có thể chứng minh bằng real run mới | Không chấp nhận proof từ testbed này hiện tại | Có thể quan sát, chỉ diagnostic | Chưa có thiết kế được chấp nhận để thực thi | NOT VERIFIED |
| Interaction xác định ở cả hai pane | Có thể chứng minh với action/result đã khóa | Không đủ testbed eligibility | Có thể quan sát, chỉ diagnostic | Ngoài scope | NOT VERIFIED |
| Exact-owned authoritative clean | Dùng đúng owner/token/gen/operation của run mới | Không được dùng profile để vượt blocker cũ | Chỉ kết quả của owner thật mới có authority; observer không cấp quyền | Đòi đổi authority nếu dùng để sửa blocker | NOT VERIFIED |
| Explicit reopen sau clean | Real navigation, không auto-Start | Không chấp nhận trong trạng thái hiện tại | Harness/overlay không tự chứng minh production reopen | Ngoài scope | NOT VERIFIED |
| MainActivity/navigation/repository/Room thật | Bắt buộc trong cùng artifact và journey mới | Không được bảo đảm bởi việc đổi profile | Workflow qualification hiện tại không tương đương proof này | Không giải quyết tiêu chí tự động | Chỉ giữ evidence đã có |
| Real applicationId/signing/source provenance | Bắt buộc, production-config/same signer | Có thể có cùng signer nhưng chưa đủ để an toàn | Package/debug/overlay khác phải ghi rõ; không tự nâng lớp evidence | Không tự đáp ứng | Chỉ giữ provenance lịch sử |

Không ghép ACTIVE từ C, clean từ A hoặc clean của SID khác thành một proof. Mỗi chu kỳ Start/Back phải có receipt và clean đúng SID của chính chu kỳ đó; reopen phải xảy ra sau clean được owner thật chấp nhận. Ownership mới tạo sau explicit Start, không nhập identity/generation/token từ run lịch sử.

### Vì sao không chọn B

Samsung xác nhận Android Work Profile có thể hiển thị qua DeX; AOSP mô tả user/profile có app data riêng. Hai điều đó không chứng minh DWT Embedded runtime độc lập theo user, không xác nhận full secondary-user/Secure Folder trên đúng S23 hiện tại, và không xác nhận các prerequisite của profile đó. [Samsung DeX FAQ](https://docs.samsungknox.com/dev/knox-sdk/faq/samsung-dex/), [AOSP multi-user](https://source.android.com/docs/devices/admin/multi-user).

Căn cứ source trực tiếp tại HEAD:

- [ShellDisplayLaunch.kt](../../app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedapp/remote/ShellDisplayLaunch.kt), dòng 19, 30–38: yêu cầu shell UID 2000; `startActivityAsUser` được gọi với user cuối cùng là `0`.
- [AssociationShell.kt](../../app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedapp/remote/AssociationShell.kt), dòng 9, 13–14: associate/disassociate/list đều dùng user `0` và `com.android.shell`.

Vì vậy profile mới không thiết lập được đường target/association độc lập theo profile trong contract hiện có. Không kết luận mọi Work Profile không hỗ trợ DeX; kết luận là **B không đạt yêu cầu fresh-testbed an toàn cho DWT ở HEAD này**. Thêm user routing, đổi UID/bootstrap hoặc sửa namespace association cần scope/architecture review riêng, không là patch ngầm trong 011. Không tạo profile, chuyển user, khởi động Shizuku/UserService hoặc kiểm tra thực nghiệm trên thiết bị blocked trong D2.

## 4. Prerequisite chính xác cho A

Đây là điều kiện cho một task thực thi sau này, không phải lệnh thực thi D2.

1. **Thiết bị và ownership độc lập.** Xác định một thiết bị vật lý khác, quyền sử dụng nó, model/Android/DeX/display và user thực tế. Ưu tiên S23 Ultra Android 16 để giữ scope môi trường. Có app data riêng không restore/clone từ máy blocked, không nhập gate/SID/workspace/license token/Keystore cũ. Không có run unresolved trên testbed; nếu installation đã từng chạy thì cần provenance và exact-owned clean của các run liên quan, không suy từ absence/PID/restart. Fresh installation thật hoặc installation độc lập đã được clean đúng quyền đều có thể là baseline; không clear data để tạo baseline.
2. **License hợp lệ độc lập.** Có sẵn trạng thái license hợp lệ cho installation/device/package của chính testbed, và đủ điều kiện đi qua real LicenseGate. Hiện **NOT VERIFIED**. Nếu cần activation mới, admin reset/rebind hoặc entitlement mutation, ghi thành prerequisite LIC/admin riêng ngoài 011; chưa đáp ứng thì STOP. Không lấy license trên thiết bị cũ để “chuyển” sang máy mới.
3. **Artifact thật.** `com.trancong.dexworkspacetouch`; source HEAD `db69bed601eceea12259249c89f0f5276793e425`; workflow production-config/same signer đã chấp nhận; version `1.0.0-beta.8 / 9`. Không diagnostic overlay/debug harness trong lane FULL proof. Record APK SHA-256, signer SHA-256, installed package và APK readback theo workflow hiện có; signer baseline lịch sử `19ac0ea99125361b3c2083aaa44c8745ebad9c55fa967642c2b9e5a1086a45e7`. Artifact normal 011 đã ghi SHA-256 `6370cd122f8c5750e230e56c15af5f5ea7a0d252fa019371648c54ab1715852b`; đây là reference lịch sử, không phải readback mới của D2. Nếu dùng artifact khác phải chứng minh provenance đúng; nếu safe same-signer installation không thiết lập được thì STOP. Không bump version, thay signer/config/backend hay public release.
4. **Readiness thật.** Calculator và Waze đúng package/component, version được ghi nhận; hoàn tất onboarding cần thiết từ trước, trạng thái mục tiêu không phụ thuộc đăng nhập/network bất ổn. Existing permission/bootstrap/remote shell UID 2000 và Embedded readiness phải đạt bằng accepted path trên testbed mới. Không thay runtime/protocol để làm readiness PASS.
5. **Dữ liệu người dùng.** Inventory phần workspace cần bảo toàn trên testbed trước mọi mutation được phép trong task sau. Tạo workspace qualification tên mới qua UI, nhận ID mới từ Room, ghi đúng Calculator/Waze và cell assignment. Chọn lại theo exact ID qua Library. Không overwrite/delete workspace khác, reset settings, uninstall production app hoặc clear data. Thiết bị blocked cũ và toàn bộ workspace của nó không bị tác động.
6. **Interaction và evidence đã khóa.** Khóa cell/pane, action, expected visible result và cách capture trước explicit Start. Calculator có thể dùng `7 + 8 = 15`; Waze có thể mở menu/Settings đã có sau onboarding rồi Back với panel/title thay đổi nhìn thấy rõ. Chọn nhãn/action chính xác theo version Waze thực tế trước run; không coi gợi ý này là kiểm chứng UI hiện tại. Tránh route/map/network làm điều kiện PASS. Capture cả hai pane ACTIVE, tác động đúng pane, runner receipt, exact-owned clean/gate IDLE-CLEAN_CONFIRMED và explicit reopen không auto-Start/auto-permission.
7. **Scope cho lần thực thi tiếp theo và STOP rõ ràng.** Chấp nhận testbed/môi trường và evidence lane trước execution; D2 hiện chỉ review. Run mới dùng SID/owner/token/generation/operation mới do accepted flow tạo. Nếu run mới unresolved hoặc capture thiếu clean authority: STOP, giữ evidence, đánh dấu testbed đó không đủ điều kiện retry; không đổi package/process/reinstall để tiếp tục. Phải giữ record blocker lịch sử song song kể cả khi A sau này đủ FULL proof.

### License: vì sao cùng signer chưa đủ

[AndroidDeviceIdentityProvider.kt](../../app/src/main/java/com/trancong/dexworkspacetouch/license/infrastructure/identity/AndroidDeviceIdentityProvider.kt), dòng 23–32, dùng installation ID, Android Keystore/device key, ANDROID_ID và package name; [DeviceIdentityEncoding.kt](../../app/src/main/java/com/trancong/dexworkspacetouch/license/identity/DeviceIdentityEncoding.kt), dòng 21–25, đưa installationId/androidId/packageName vào fingerprint. [LicenseTokenVerifier.kt](../../app/src/main/java/com/trancong/dexworkspacetouch/license/activation/LicenseTokenVerifier.kt), dòng 123–126, từ chối installation/device hash hoặc package không khớp.

Android 8+ định phạm vi ANDROID_ID theo signing key, user và device; vì vậy same signer không bảo đảm cùng identity trên thiết bị/profile mới. [Android Settings.Secure.ANDROID_ID](https://developer.android.com/reference/android/provider/Settings.Secure#ANDROID_ID).

| Phương án | Prerequisite license/data |
|---|---|
| A | License độc lập đúng real package/installation/device; không giả định auto-activate. New activation/rebind/entitlement là prerequisite riêng, chưa được phép thực hiện. |
| B | Data/installation/key/ANDROID_ID của profile không được mặc định giống user 0. License hợp lệ và remote/user prerequisites đều chưa chứng minh; không copy token/data từ profile cũ. |
| C | Harness package không được coi đã có entitlement của real package. Nếu workflow C không đi qua production LicenseGate thì chỉ chứng minh runtime diagnostic; nếu cần license thì prerequisite riêng cho identity/package đó. Không bypass license trong real app. |
| D | Recovery design không cấp hoặc chuyển license authority; không sửa device-binding/license để cứu ownership. |
| E | Không mutation license/data; chỉ giữ evidence và trạng thái chấp nhận/NOT VERIFIED. |

## 5. Proposed diagnostic-retention scope cho NEW runs

**Đề xuất một task diagnostic riêng trong lane C, chỉ quan sát và copy giá trị; không implementation trong D2.** Không yêu cầu retention này như một architecture patch để A có thể thành công. Giữ Application gate value-only/sanitized; không thêm raw exception tùy ý vào `ProductExecutionValue`, đổi `START_FAILED` semantics hoặc mở late-clean path.

Nguồn tái sử dụng: [QualificationDiagnostics.kt](../../app/src/debug/java/com/trancong/dexworkspacetouch/qualification/QualificationDiagnostics.kt), dòng 15–158, và [Prepare-Diagnostics.py](../../experiments/dwt-vdm-010-host-harness/Prepare-Diagnostics.py). Observer hiện có chỉ dành cho debug harness package; có Surface/input/raw completion/runner result hooks nhưng retention còn memory/logcat. Historical guard hiện chỉ chứa SID 010 `0bf0ecec-9e2c-4196-8edb-a076b688edb1`, **không đủ để bảo vệ SID 011**. Task tương lai phải denylist tất cả historical evidence-only SIDs, bao gồm cả hai SID, và yêu cầu manifest testbed độc lập; không sửa guard trong D2.

### Envelope/correlation chung

Mỗi event có schema version, testbed/capture-run ID, evidence class, source/artifact/package/signer reference, exact SID và cell, event kind/sequence, UTC epoch timestamp và monotonic timestamp. Copy app/observer/remote PID, UID/user, process-instance identity và host ID/identity khi có sẵn; không so object identity giữa process hoặc dùng PID làm ownership authority.

Freeze context khi owner/SID thật được tạo: product token identity, generation, product Start operation, workspace ID và cell. Ghi cleanup operation lúc nó thực sự có sẵn, giữ private IPC/lease operation trong field/namespace riêng. Không gán metadata của gate hiện tại cho event cũ đến trễ; field chưa có ghi `NOT_CAPTURED`/`NOT_AVAILABLE` cùng lý do, không suy đoán. Token chỉ lưu identifier cần correlation, không lưu license token/key/secret.

### Capture trước khi thông tin bị mất

| Dữ liệu yêu cầu | Điểm capture đề xuất | Giới hạn/authority |
|---|---|---|
| Raw Start failure code/message/type | Remote catch trước chuyển exception thành string; local IPC catch trước generic `START_IPC_FAILED`; raw completion trước session mapping/fence; session/runner failure trước product projection | Phân biệt remote negative reply, transport exception và typed lifecycle failure. Giữ raw field tách khỏi sanitized message, ghi truncation/redaction rõ ràng; không coi raw text là result authority. |
| Start stage | Local preflight/connect/surface/IPC và remote validation/association/display/input/input-stability/launch tại entry và success/failure của từng stage | Đặt stage trước operation để exception thuộc đúng stage, gồm association. Không suy stage từ độ trễ hoặc code START_FAILED. |
| Surface identity/isValid | Copy ngay trên thread hiện có ở Start entry, input wait, trước target launch và failure; ghi timestamp/PID/context | Identity chỉ có nghĩa trong process; isValid là snapshot, không bảo đảm valid trong toàn stage. Không đưa Surface handle vào queue/file/gate. |
| Input-stability samples | Ngay tại mỗi sample trong vòng wait hiện có: sample index/time, display/input name, equality/count, deadline/remaining, section digest và bounded raw section hiện có | Không tăng poll/retry, không gọi thêm dumpsys, không đổi threshold/deadline; sample thiếu/truncated phải đánh dấu. |
| Remote allocation IDs | Copy association ID/MAC, display ID, input name/ID và các ID resource/target đã được expose locally tại allocation/release | Chỉ ID thực sự có sẵn, kèm stage/timestamp. Không thêm reflection/inventory để tự nhận task ID, không đưa ID thành quyền cleanup, không đồng nhất unknown với chưa allocate. |
| Remote rollback result/exception | Ghi rollback begin và result/exception quanh **chính lần** `runCatching { stop() }` hiện có trong remote catch; giữ original Start exception riêng | Không gọi stop lần hai, không thay return/rethrow/cleanup semantics. Observer ghi stop returned/exception không phải accepted exact-owned clean. |
| Runner receipts/partialReceipt | Copy result và receipt tại runner trước terminal detach/projection sang ProductExecutionValue | Giữ source cell/order/SID/phase/display và receipt completeness; không tự bổ sung omitted receipt. |
| Rollback outcomes | Copy từng existing outcome: cell/SID/disposition/allocation/release confirmation/failure, và tổng complete/incomplete trước projection | Ghi đúng result của runner/owner; diagnostic flag không làm gate CLEAN_CONFIRMED. |

Hiện [RemoteSessionRuntime.kt](../../app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedapp/remote/RemoteSessionRuntime.kt), dòng 19–47, bỏ outcome của `runCatching { stop() }`; [EmbeddedStartIpcTask.kt](../../app/src/main/java/com/trancong/dexworkspacetouch/feature/embeddedapp/EmbeddedStartIpcTask.kt), dòng 26–35, cap remote message và thay transport exception bằng generic code. [D1 source authority audit](d1-forensics/source-authority-audit.json) xác định runner/result projection và ownership fences liên quan. Đó là điểm mất thông tin cần quan sát trong run mới, không chứng minh raw cause của SID cũ.

### Lưu bền và thu hồi evidence

- Bounded append-only structured evidence log theo capture-run/process, một writer queue có giới hạn; copy primitives/strings/receipts, không giữ session/host/Binder/Surface để kéo dài ownership. Không phải persistent ownership/recovery journal: runtime/gate không đọc file để Start, cleanup, unlock hoặc restore owner.
- Trên app process, dùng vùng app-private của qualification installation mới; trên remote shell process, cần xác lập vùng ghi đúng quyền UID của nó và cơ chế export đã được phép cho testbed. Không giả định shell có quyền ghi app-private. Nếu không có đường lưu/thu thập phù hợp thì yêu cầu chưa đáp ứng, không tự thêm AIDL export/ack.
- Thiết kế collector off-device chạy trước NEW Start, thu tagged stream vào file và giữ phần app/remote riêng theo process sequence. Logcat chỉ là transport/corroboration, không là kho duy nhất; không clear buffer và không restart process để tạo evidence. Correlation bằng manifest/exact SID và context đã freeze, không cần protocol mới chỉ để ghép hai file.
- Quy định byte/event/sample budget trước run; pin failure/rollback/terminal metadata, ghi dropped-event count, sequence gaps, truncation/redaction và file I/O/export errors. Flush/export terminal records trong vòng đời observer hiện có; không block Binder/input loop chờ ghi và không hứa survive mọi process death nếu chưa chứng minh.
- Giữ schema completeness report và hashes của export cùng artifact/capture provenance. Thiếu raw failure/rollback là capture incomplete / NOT VERIFIED; không biến “không có log” thành success hoặc clean. Không xoay/xóa evidence đang unresolved để tái dùng testbed.
- Raw exception/input snippets được giữ bounded trong evidence dành cho review; loại key/token/secret/unrelated app data, ghi rõ redaction. Không xuất license state/user workspace database chỉ để thêm diagnostics.

Retention đầy đủ cần capture remote và app đồng thời. Observer trong qualification lane có thể ảnh hưởng timing, vì vậy chứng cứ đó giữ **DIAGNOSTIC ONLY**. Nếu sau này muốn đưa observer vào real-package artifact và dùng làm final proof, cần scope/provenance/semantic review riêng; D2 không tự nâng evidence class hay đề xuất bỏ normal production-package lane.

### Dữ liệu không có sẵn / protocol boundary

Có thể thiết kế capture **locally** ở hai process mà không thêm AIDL: exception/stage/locally exposed IDs/stop Result và runner receipts đã tồn tại hoặc có thể copy tại điểm xử lý hiện có. Không khẳng định mọi field đều đã expose trong production reply.

Nếu một ID/rollback acknowledgement chỉ lấy được bằng field/method IPC mới, authoritative remote receipt mới, lease handshake hoặc thay đổi AIDL/remote return semantics: đánh dấu **STOP-boundary design**, để riêng ngoài implementation 011. Chỉ ghi `NOT_CAPTURED` trong evidence hiện có, không suy ra/điền dữ liệu. Durable diagnostic observation không thay thế acknowledged result từ exact owner đang hợp lệ.

## 6. Charter STOP boundaries

[Technical Autonomy Charter](../../docs/codex/DWT_TECHNICAL_AUTONOMY_CHARTER.md), mục Mandatory STOP boundaries, yêu cầu “STOP before implementation” nếu đổi các contract liên quan. Các boundary áp dụng cụ thể:

| Hướng | Boundary |
|---|---|
| Late cleanup acknowledgement được gate/runner nhận sau terminal, đổi FAILED/fences để unlock | 2 ownership/result authority; 6 cleanup/reconcile; 15 runtime semantic contract |
| Recovery owner hoặc process mới nhận owner cũ | 2, 4 process-death recovery; có thể 6 |
| Persistent ownership/recovery journal được dùng để restore hoặc quyết định clean | 4, 5 persistent ownership/recovery journal; có thể 2/6 |
| Global/cross-process cleanup/reconcile, cancellation/tombstone | 6/7 và 2 |
| AIDL fields/methods/authoritative acknowledgements mới để lấy dữ liệu | 3 remote protocol/AIDL; có thể 2 |
| Sửa user routing/bootstrap/association để B trở thành independent runtime | 1 architecture / 15 accepted runtime semantic contract; scope review riêng |
| License activation/admin reset/rebind/entitlement mutation | Explicit 011 license boundary; license/security code/architecture đổi chạm 11. D2 không thực hiện prerequisite này. |
| Signing/config/version/backend/Room/dependency/release thay đổi | 8–14 theo loại thay đổi; không cần cho A/retention observational |
| Rewriting historical evidence, destructive Git, commit/push/tag | 18–20/Git rules và explicit D2 prohibition |

D2 chỉ nêu các hướng D để so sánh, không chọn hoặc triển khai kiến trúc mới. Không cần vượt ownership/protocol/recovery boundary để thử A khi testbed/license thật sự đủ điều kiện. Nếu A cũng blocked thì dừng theo accepted 011 STOP, không dùng retention làm đường recovery.

## 7. Disposition và giới hạn review

**011: REMAIN BLOCKED hôm nay.** Giữ thiết bị/SID lịch sử evidence-only và mọi NOT VERIFIED hiện tại. Đề xuất next decision là bố trí A với license độc lập và khóa run scope; chỉ khi prerequisites hoàn tất mới đề xuất CONTINUE trên ownership mới. Nếu A không thể bố trí mà không vi phạm license/data/architecture boundary, đề xuất người phụ trách milestone chọn E — CLOSE AS PARTIAL/BLOCKED hoặc tiếp tục suspend; không tuyên bố FULL PASS.

D2 hoàn tất phần design review, không thực hiện recovery hoặc product qualification. Chỉ thêm tài liệu này trong worktree 011; không source/test/build changes, không tests/build/install/device Start, không thiết bị/license/workspace mutation, không commit/push/tag. Đã đọc source/evidence liên quan và tài liệu chính thức về DeX/user/ANDROID_ID; availability, licensing, readiness, raw-retention implementation và dual journey trên testbed mới đều **NOT VERIFIED**.

Kiểm tra tài liệu/phạm vi bằng thao tác đọc: 7 mục quyết định, 13 liên kết local đều tồn tại; tracked diff của worktree 011 trống. Original worktree giữ nguyên HEAD/branch/index/status và SHA-256 của toàn bộ 150 file pre-existing so với baseline; không thêm sửa đổi tại đó. Đây không phải test ứng dụng hoặc device proof mới.
