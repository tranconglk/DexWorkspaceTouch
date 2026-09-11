# CHECKLIST BÁN DEXWORKSPACETOUCH

## 1. Nhận đơn

Gán mã đơn, ví dụ:

```text
ORDER-0001
```

Không dùng số điện thoại/email làm mã license.

---

## 2. Mở PowerShell

```powershell
cd D:\AndroidStudioProjects\DexWorkspaceTouch\license-backend
```

Nạp production API:

```powershell
$env:DWT_LICENSE_ADMIN_API_URL="https://dexworkspacetouch-license-production.dex-backend.workers.dev"
```

Nạp admin token từ file bảo mật:

```powershell
$env:DWT_LICENSE_ADMIN_TOKEN = `
  [System.IO.File]::ReadAllText("D:\SecureKeys\DexWorkspaceTouch\license-production-v1\<file-admin-token>").Trim()
```

KHÔNG in admin token ra màn hình.

---

## 3. Tạo license

```powershell
npm.cmd run fulfillment -- create --order ORDER-0001 --max-devices 1
```

Kiểm tra đã có:

```text
fulfillmentId
licenseId
customer-delivery.txt
```

---

## 4. Kiểm tra record

```powershell
npm.cmd run fulfillment -- show <fulfillmentId>
```

Đảm bảo persistent record KHÔNG chứa:

```text
License Key
Admin token
Private key
Device public key
Installation ID
```

---

## 5. Gửi cho khách

File cần gửi:

```text
fulfillment-output\<fulfillmentId>\customer-delivery.txt
```

Khách nhận:

```text
Link tải APK
License Key
Hướng dẫn cài
Hướng dẫn kích hoạt
Hướng dẫn cập nhật
```

KHÔNG gửi:

```text
admin token
backend secrets
private keys
fulfillment record nội bộ
```

---

## 6. Khách kích hoạt

Khách:

```text
Tải APK
→ cài
→ mở DexWorkspaceTouch
→ nhập License Key
→ kích hoạt
```

Không cần ADB.

Không cần gửi device ID cho bạn.

---

## 7. Đánh dấu đã giao

Sau khi khách xác nhận nhận được License Key:

```powershell
npm.cmd run fulfillment -- mark-delivered <fulfillmentId>
```

Kiểm tra:

```powershell
npm.cmd run fulfillment -- show <fulfillmentId>
```

---

## 8. Xóa bản License Key tạm

Sau khi chắc chắn khách đã nhận:

Xóa:

```text
fulfillment-output\<fulfillmentId>\customer-delivery.txt
```

GIỮ:

```text
fulfillment-records\<fulfillmentId>.json
```

---

# HỖ TRỢ SAU BÁN

## Khách đổi điện thoại

Tra record:

```powershell
npm.cmd run fulfillment -- show <fulfillmentId>
```

Reset device cũ:

```powershell
npm.cmd run fulfillment -- reset-device <fulfillmentId> <deviceId>
```

Khách dùng lại License Key trên máy mới.

---

## Khách mất License Key

KHÔNG thể lấy lại key cũ.

Dùng:

```powershell
npm.cmd run fulfillment -- replace-license <fulfillmentId>
```

Flow:

```text
revoke license cũ
→ tạo license mới
→ giao License Key mới
```

---

## Khách hoàn tiền / hủy

```powershell
npm.cmd run fulfillment -- revoke <fulfillmentId>
```

Kiểm tra lại:

```powershell
npm.cmd run fulfillment -- show <fulfillmentId>
```

---

# KẾT THÚC PHIÊN

Xóa admin token khỏi PowerShell:

```powershell
Remove-Item Env:DWT_LICENSE_ADMIN_TOKEN
```

Backup:

```text
fulfillment-records\
```

sang ổ lưu trữ mã hóa.

KHÔNG cần backup:

```text
fulfillment-output\
```

---

# 5 NGUYÊN TẮC KHÔNG ĐƯỢC QUÊN

1. Không gửi admin token cho khách.
2. Không lưu plaintext License Key lâu dài.
3. Không gỡ app khi cập nhật.
4. Không direct-rebind thiết bị — luôn dùng reset + activation mới.
5. Không xóa fulfillment record vì đây là liên kết giữa đơn hàng và licenseId.

---

## Quy trình ngắn nhất

```text
NHẬN ĐƠN
→ CREATE LICENSE
→ GỬI customer-delivery.txt
→ KHÁCH ACTIVATE
→ MARK DELIVERED
→ XÓA FILE CHỨA KEY
→ GIỮ + BACKUP FULFILLMENT RECORD
```
