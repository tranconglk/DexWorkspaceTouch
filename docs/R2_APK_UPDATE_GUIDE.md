# Hướng dẫn cập nhật APK production lên Cloudflare R2

Tài liệu này mô tả quy trình phát hành một APK DexWorkspaceTouch mới qua hệ thống update production hiện tại.

## Nguyên tắc an toàn

- Mỗi bản phát hành phải có `versionCode` mới và tăng dần.
- Không ghi đè APK đã phát hành tại cùng một URL versioned trên R2.
- APK phải giữ package `com.trancong.dexworkspacetouch` và production signing certificate hiện tại.
- Upload APK trước, kiểm tra thành công, rồi mới publish `update-manifest.json`.
- Không dùng `-SkipTests` cho bản production.
- Không commit APK, output build, keystore hoặc credential.

Ví dụ dưới đây phát hành `1.0.0-beta.8`, `versionCode = 9`. Với bản sau, thay toàn bộ tên phiên bản, version code, tên APK và URL tương ứng.

## 1. Mở project và kiểm tra Git

```powershell
cd D:\AndroidStudioProjects\DexWorkspaceTouch
git status
git diff --stat
```

Nếu có thay đổi hoặc file chưa tracked, phải xác định rõ trước khi build. Không tự xóa file không liên quan.

## 2. Tăng phiên bản

Mở `app\build.gradle.kts` và đặt:

```kotlin
versionCode = 9
versionName = "1.0.0-beta.8"
```

Kiểm tra lại:

```powershell
Select-String -Path .\app\build.gradle.kts -Pattern "versionCode|versionName"
git diff -- .\app\build.gradle.kts
```

## 3. Build APK production và tạo update manifest

PowerShell có thể chặn script bằng Execution Policy. Không cần đổi policy toàn hệ thống; chạy script qua process con với `Bypass` chỉ cho lệnh này:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\scripts\build-production-release.ps1" -AcknowledgeDirtyWorktree -CreateUpdateManifest -UpdateManifestUrl "https://dexworkspacetouch-updates.dex-backend.workers.dev/update-manifest.json" -ApkUrl "https://dexworkspacetouch-updates.dex-backend.workers.dev/releases/1.0.0-beta.8/DexWorkspaceTouch-1.0.0-beta.8-9.apk" -ReleaseNotes "Dong bo ban phat hanh beta.8 cho khach hang dau tien."
```

Chỉ dùng `-AcknowledgeDirtyWorktree` sau khi đã xem và hiểu toàn bộ thay đổi từ `git status`/`git diff`. Nếu worktree sạch, có thể bỏ tham số này.

Chỉ tiếp tục khi script báo thành công và không có lỗi test, chữ ký hoặc license configuration.

## 4. Kiểm tra output

```powershell
Get-ChildItem .\release-output
Get-Content .\release-output\update-manifest.json
```

Phải có tối thiểu:

```text
DexWorkspaceTouch-1.0.0-beta.8-9.apk
update-manifest.json
release-manifest.json
SHA256SUMS.txt
```

Manifest phải chứa đúng `versionName`, `versionCode` và URL APK của bản vừa build.

## 5. So sánh APK với manifest

```powershell
$apk = ".\release-output\DexWorkspaceTouch-1.0.0-beta.8-9.apk"
$manifest = Get-Content ".\release-output\update-manifest.json" -Raw | ConvertFrom-Json
$apkHash = (Get-FileHash $apk -Algorithm SHA256).Hash.ToLowerInvariant()
$apkSize = (Get-Item $apk).Length

$apkHash
$manifest.apkSha256
$apkSize
$manifest.apkSize

if ($apkHash -ne $manifest.apkSha256) { throw "APK hash mismatch" }
if ($apkSize -ne $manifest.apkSize) { throw "APK size mismatch" }
```

Không publish nếu hash hoặc kích thước không khớp.

## 6. Upload APK lên R2 và publish manifest

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\scripts\publish-production-release.ps1" -BucketName "dexworkspacetouch-releases" -WorkerBaseUrl "https://dexworkspacetouch-updates.dex-backend.workers.dev" -ApkPath ".\release-output\DexWorkspaceTouch-1.0.0-beta.8-9.apk" -ManifestPath ".\release-output\update-manifest.json"
```

Script sẽ:

1. Xác minh package, production signer, SHA-256 và kích thước.
2. Upload APK vào object key versioned.
3. Kiểm tra APK từ delivery Worker bằng HEAD, tải lại hash và Range request.
4. Backup manifest production hiện tại nếu có.
5. Upload `update-manifest.json` cuối cùng.
6. Đọc lại và xác minh manifest production.

Kết quả mong đợi:

```text
PUBLISH COMPLETE: APK first, manifest last
```

Nếu Wrangler yêu cầu đăng nhập Cloudflare:

```powershell
.\update-delivery\node_modules\.bin\wrangler.cmd login
```

Sau khi đăng nhập thành công, chạy lại lệnh publish. Không đưa Cloudflare token hoặc credential vào source control.

## 7. Kiểm tra production từ máy operator

```powershell
Invoke-RestMethod "https://dexworkspacetouch-updates.dex-backend.workers.dev/update-manifest.json"
```

Kết quả phải có:

```text
versionName : 1.0.0-beta.8
versionCode : 9
```

Kiểm tra APK versioned:

```powershell
Invoke-WebRequest -Method Head "https://dexworkspacetouch-updates.dex-backend.workers.dev/releases/1.0.0-beta.8/DexWorkspaceTouch-1.0.0-beta.8-9.apk"
```

Mong đợi HTTP 200 và content type `application/vnd.android.package-archive`.

## 8. Kiểm tra cập nhật trên thiết bị đã có license

1. Mở DexWorkspaceTouch phiên bản cũ.
2. Chọn **Cập nhật ứng dụng** hoặc **Kiểm tra cập nhật**.
3. Xác nhận ứng dụng phát hiện phiên bản mới.
4. Tải APK qua trình duyệt.
5. Cài đè bản hiện tại; không uninstall trước.
6. Xác nhận license, workspace, device identity và Floating Dock vẫn hoạt động.

Uninstall có thể xóa workspace, dữ liệu cục bộ, device identity và signed license token.

## Xử lý lỗi thường gặp

### `PSSecurityException` hoặc `running scripts is disabled`

Dùng đúng lệnh `powershell.exe -NoProfile -ExecutionPolicy Bypass -File ...` ở trên. Không cần thay đổi Execution Policy vĩnh viễn.

### APK path đã tồn tại với bytes khác

Dừng phát hành. Tăng `versionCode`/`versionName`, tạo URL versioned mới, build lại APK và manifest. Không ghi đè object versioned cũ.

### Manifest không khớp APK

Không upload thủ công. Build lại bằng `build-production-release.ps1 -CreateUpdateManifest`, sau đó kiểm tra lại hash và kích thước.

### Upload APK thành công nhưng manifest thất bại

APK versioned vẫn chưa được client phát hiện nếu production manifest chưa đổi. Khắc phục lỗi publish rồi chạy lại script; không tạo một APK khác tại cùng URL.
