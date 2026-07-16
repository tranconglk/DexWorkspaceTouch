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

## Phần đóng băng

Chưa port trong giai đoạn Designer MVP:

- Launch engine.
- DeX display provider.
- Shortcut.
- Room.
- Backup/import/export.
