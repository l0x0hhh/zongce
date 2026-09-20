# 代码结构说明

本文档用于快速定位 `zongce-android` 的代码模块。修改功能前，建议先根据下表找到对应层级，再检查相关测试和调用链。

每次开发会话开始前，请先阅读根目录的 `VERSION_HISTORY.md`，确认当前版本、已有更新、验证状态和未提交改动。

## 整体调用关系

```text
MainActivity
    └── App / Compose Navigation
          ├── CaptureScreen     拍摄或导入证书
          ├── EntryScreen       新增或编辑获奖记录
          ├── ListScreen        查看和管理记录
          └── ExportScreen      校验并导出材料包

AppViewModel
    ├── AwardDao               读写 Room 数据库
    ├── PhotoStore              管理本地证明照片
    ├── ExportCheck             导出前校验
    └── ZipExporter             生成 ZIP 材料包
```

## 入口与导航

| 文件 | 职责 |
| --- | --- |
| `app/src/main/java/com/zongce/app/MainActivity.kt` | Android Activity、Compose 根入口和页面导航 |
| `app/src/main/java/com/zongce/app/ZongceApp.kt` | Application 类 |

新增页面或调整页面路由时，优先检查 `MainActivity.kt`。

## 核心规则与图片处理

| 文件 | 职责 |
| --- | --- |
| `core/AcademicYear.kt` | 学年归属、边界日期和超出范围判断 |
| `core/FileNameRule.kt` | 导出图片文件名生成、清理和长度限制 |
| `core/ImageTools.kt` | JPEG 转换、EXIF 旋转和缩略图生成 |

修改学年窗口或文件名格式时，必须同步检查对应单元测试和导出逻辑。

## 数据层

| 文件 | 职责 |
| --- | --- |
| `data/AwardRecord.kt` | 获奖记录、照片实体、五育分类和字段选项 |
| `data/AwardDao.kt` | Room 查询、增删改和照片关联操作 |
| `data/AppDatabase.kt` | Room 数据库单例及数据库配置 |
| `data/PhotoStore.kt` | 应用私有目录中的照片导入、保存、读取和删除 |

修改实体字段时，需要同时检查数据库版本、DAO 查询、ViewModel 和新增/编辑页面。

## 导出层

| 文件 | 职责 |
| --- | --- |
| `export/ExportCheck.kt` | 导出前阻断项和提醒项校验 |
| `export/ZipExporter.kt` | 按五育生成目录、生成清单和写出 ZIP 文件 |

导出流程的主要顺序是：筛选目标学年记录 → 运行 `ExportCheck` → 通过后调用 `ZipExporter` → 生成并分享 ZIP。

## UI 与业务编排

| 文件 | 职责 |
| --- | --- |
| `ui/AppViewModel.kt` | 记录流、照片导入、保存/删除、导出状态和业务编排 |
| `ui/CaptureScreen.kt` | 相机拍摄和系统图片选择入口 |
| `ui/EntryScreen.kt` | 新增、编辑获奖记录和表单校验 |
| `ui/ListScreen.kt` | 记录列表、预览和删除入口 |
| `ui/ExportScreen.kt` | 目标学年选择、校验反馈和导出操作 |
| `ui/UiKit.kt` | 公共 Compose 控件和照片缩略图 |

页面显示问题优先查对应 Screen；跨页面的数据或状态问题优先查 `AppViewModel`。

## 测试位置

| 文件 | 覆盖内容 |
| --- | --- |
| `app/src/test/java/com/zongce/app/core/AcademicYearTest.kt` | 学年归属、边界和时间窗口 |
| `app/src/test/java/com/zongce/app/core/FileNameRuleTest.kt` | 文件名格式、清理和长度限制 |
| `app/src/test/java/com/zongce/app/export/ExportCheckTest.kt` | 导出前材料完整性检查 |

运行测试：

```powershell
.\gradlew.bat test
```

## 构建配置

| 文件 | 职责 |
| --- | --- |
| `settings.gradle.kts` | 项目名称、模块和依赖仓库配置 |
| `build.gradle.kts` | Android、Kotlin、Compose 和 KSP 插件版本 |
| `app/build.gradle.kts` | Android 模块配置、SDK、依赖和测试配置 |
| `gradle/wrapper/` | Gradle Wrapper |

## 常见修改定位

- 修改获奖记录字段：`AwardRecord.kt` → `AwardDao.kt` → `AppViewModel.kt` → `EntryScreen.kt` / `ListScreen.kt`。
- 修改拍照或导入照片：`CaptureScreen.kt` → `AppViewModel.kt` → `PhotoStore.kt`。
- 修改学年判断：`AcademicYear.kt` → `AcademicYearTest.kt` → `ExportCheck.kt`。
- 修改导出目录、文件名或 ZIP 内容：`FileNameRule.kt` / `ZipExporter.kt` → `ExportCheck.kt` → `ExportScreen.kt`。
- 修改页面路由：`MainActivity.kt`。
- 修改通用 UI 控件：`UiKit.kt`。
