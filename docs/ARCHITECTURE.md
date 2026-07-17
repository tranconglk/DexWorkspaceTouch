# Architecture

## Nguyên tắc phân lớp

### Domain

- Model immutable.
- Thuần Kotlin.
- Không Android Context.
- Không Compose.
- Không Modifier, Dp hoặc pixel.
- Bounds chuẩn hóa từ 0f đến 1f.

### Presentation

- Jetpack Compose.
- Render state và phát callback.
- Không truy cập Room hoặc PackageManager trực tiếp.
- Không chứa launch logic.
- Mỗi cell là một Composable độc lập.

### Infrastructure

- Navigation.
- PackageManager.
- Persistence.
- Samsung DeX display integration.
- Launch engine.

## Ranh giới chính

Workspace Designer không biết cách mở ứng dụng.

Launch engine không biết giao diện Designer.

## Cấu trúc dự kiến

```text
com.trancong.dexworkspacetouch/
├── navigation/
├── workspace/
│   ├── library/
│   ├── designer/
│   ├── apppicker/
│   └── launcher/
├── platform/
│   ├── dex/
│   └── applaunch/
├── data/
└── ui/
    ├── design/
    ├── components/
    └── theme/
```

Không tạo package `util`, `helpers`, `common` hoặc `misc` chung chung.

## Application Model

- `InstalledApp` mô tả metadata của ứng dụng hiện có trên thiết bị: identity,
  label và khả năng launch. Model này thuần Kotlin và không chứa icon/Android type.
- `AppIdentity` gồm package name và launcher activity nullable. Package name là
  định danh tối thiểu; activity phân biệt nhiều entry point trong cùng package.
- `AssignedApp` là identity được gán vào workspace. Trong giai đoạn migration
  E3-001, label vẫn được giữ tạm để UI hiện tại không mất khả năng render;
  E3-003 sẽ resolve presentation metadata từ catalog và loại bỏ label này.
- `InstalledAppDataSource` và `InstalledAppCatalog` là contract đồng bộ, thuần
  Kotlin. `DefaultInstalledAppCatalog` lọc launcher app, loại identity trùng và
  sắp xếp deterministic.
- `PackageManagerAdapter` và `AndroidInstalledAppDataSource` thuộc
  Infrastructure. Chỉ Infrastructure biết Android `PackageManager`; UI nhận
  state từ `AppPickerViewModel`.
- UI label/icon phải được resolve từ catalog; domain không tự truy cập catalog
  hoặc `PackageManager`.

## Phần đóng băng

Chưa port trong giai đoạn Designer MVP:

- Launch engine.
- DeX display provider.
- Shortcut.
- Room.
- Backup/import/export.
