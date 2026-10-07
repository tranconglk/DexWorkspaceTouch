# DWT-SHIZUKU-001 — provenance của commit

Kết quả được người dùng chấp nhận: **PASS — PRODUCT INTEGRATION VIABLE / READY FOR COMMIT**. Commit chỉ tích hợp Workspace Control đã qualification trên S22 và S23; không có thay đổi implementation sau qualification.

- Release baseline / HEAD trước commit: `b04288e69a0f8c2057e185b68a1df9ae8fcd0b80`.
- R&D reference: `4e8bca37d904ef6f537d10a52403262e5f4e742a`; không nhập launch prototype hoặc raw evidence R&D.
- Nhánh riêng: `product/dwt-shizuku-001`. Không tích hợp vào `release/1.0-beta`, không push/tag/publication.
- [Snapshot](device-continuation/frozen-source-snapshot.json): 36 tệp, gồm 22 main Kotlin, 1 AIDL, 12 unit test và 1 androidTest. Byte trong index phải khớp SHA-256 của snapshot.
- [Manifest commit](COMMIT_MANIFEST.json) liệt kê đường dẫn và SHA-256 của source/evidence được chọn. Hash áp dụng trên byte gốc, không chuẩn hóa EOL hoặc raw evidence.

## Qualification được giữ nguyên

- [S22 report](s22-product-proof/S22_PRODUCT_QUALIFICATION_REPORT.md), [S22 summary](s22-product-proof/s22-qualification-summary.json): real product, Automatic 3/3, Suggest không mutation trước click, OFF và Manual Repair PASS; cùng task IDs, không unrelated resize hoặc Shizuku app START.
- [S23 report](s23-product-proof/S23_PRODUCT_QUALIFICATION_REPORT.md), [S23 summary](s23-product-proof/s23-qualification-summary.json): update tại chỗ cùng signer, license/session và 13 workspace cũ bảo toàn; Calculator `5506` / Clock `5507`, requested = actual, 0 px deviation, `LAYOUT_CORRECT`, zero mutation/automatic attempt.
- Các report/summary cũ giữ nguyên checkpoint lịch sử PARTIAL hoặc “không commit”; quyết định cuối là S23 PASS và ủy quyền commit hiện tại.
- Regression được chấp nhận: 188 tests / 19 suites; debug và androidTest build PASS. Không chạy lại test/thiết bị khi staged source khớp snapshot.

## Artifact và licensing công khai

- Package: `com.trancong.dexworkspacetouch`; version `1.0.0-beta.8 (9)`.
- APK qualification SHA-256: `708e4df09c1aea4e232e5655b981ef457b96d3b5d85fb36397fee9e1ccd14b68`.
- Signer certificate SHA-256: `19ac0ea99125361b3c2083aaa44c8745ebad9c55fa967642c2b9e5a1086a45e7`.
- API hợp lệ hiện có: `https://dexworkspacetouch-license-production.dex-backend.workers.dev`.
- Trusted registry hiện có: `license-signing-v1`, RS256; SPKI SHA-256 `ae8c0a5376ceef82fa3f0123e2788c21cc218bb19fb866aed83813745d392e74`.
- [Qualification manifest](artifact-license-unblock/qualification-manifest.json), [packaged config check](artifact-license-unblock/packaged-config-check.json), [signer verification](artifact-license-unblock/qualification-signer.txt) ghi cấu hình công khai; không thay production licensing, trusted-key semantics, signer, version hoặc release config.
- Build qualification đã dùng config/material DWT hợp lệ sẵn có, giữ production guard. Đây là provenance của artifact đã được chấp nhận, không phải lệnh build/publish release mới.

## Dữ liệu giữ ngoài Git

Không stage APK/build outputs, `local.properties`, keystore/private key/password/token/plaintext license, database/sidecar hoặc backup Library `.dwtbundle`. Chẩn đoán activation/fulfillment chứa thông tin khách hàng, raw setup/failed-trial không cần thiết và historical raw R&D evidence cũng giữ ngoài commit.

Các snapshot Library/UI trước và sau update được giữ cục bộ để bảo vệ dữ liệu người dùng; kết quả so sánh nằm trong `preservation-proof.json` và S23 summary. Report lịch sử có thể tham chiếu các tệp cục bộ không được commit, gồm `ARTIFACT_LICENSE_UNBLOCK_REPORT.md`.

Verifier thiết bị được lưu nguyên trạng để truy xuất cách kiểm chứng. Chúng cần APK đã biết hash và, với S23, các export/UI snapshot cục bộ trước/sau update. Không chạy verifier từ một clone sạch khi chưa có các đầu vào đó; không đưa đầu vào riêng tư vào Git để làm verifier tự chứa.

## Ranh giới hành vi

Default **SUGGEST**. Manual chỉ sửa task hiện hữu, fail closed khi ambiguous, không launch missing, revalidate trước resize. Suggest chỉ một assessment hữu hạn. Automatic chỉ `REPAIR_AVAILABLE`, cùng `ExistingWorkspaceRepair.run()`, tối đa một attempt; `LAYOUT_CORRECT` không mutation. OFF không assessment/Automatic, Manual vẫn hoạt động. Không watcher/polling nền.

Production UserService chỉ nhận whitelist `dumpsys activity activities` và `am task resize`; không arbitrary shell hoặc launch prototype. Không thay ProductRunGate/Embedded authority, Room schema, licensing/security, dependency/toolchain/version/signing/release config.
