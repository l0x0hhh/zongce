# 代码结构说明

本文档用于快速定位 `zongce-android` 的代码模块。修改功能前，建议先根据下表找到对应层级，再检查相关测试和调用链。

每次开发会话开始前，请先阅读根目录的 `VERSION_HISTORY.md`，确认当前版本、已有更新、验证状态和未提交改动。

## 整体调用关系

```text
MainActivity
    ├── WidgetActions           桌面组件入口协议（动作常量 + 是否为入口动作的判断）
    └── App / Compose Navigation
          ├── CaptureScreen     拍摄、导入、最近记录与更新入口
          ├── EntryScreen       新增或编辑获奖记录
          ├── ListScreen        查看、筛选和管理记录
          └── ExportScreen      校验并导出材料包
    └── UpdateDialog            更新检查、下载进度和安装入口

JicunWidgetReceiver（系统广播）
    └── JicunWidget             Glance 组件界面：发 Intent 给 MainActivity，不碰数据库

AppViewModel
    ├── AwardDao               读写 Room 数据库
    ├── PhotoStore             管理本地证明照片
    ├── ExportCheck            导出前校验
    ├── ZipExporter            生成 ZIP 材料包
    └── UpdateChecker          读取更新清单并下载 APK
```

## 入口与导航

| 文件 | 职责 |
| --- | --- |
| `app/src/main/java/com/zongce/app/MainActivity.kt` | Android Activity、Compose 根入口、页面导航和组件入口路由 |
| `app/src/main/java/com/zongce/app/ZongceApp.kt` | Application 类 |
| `app/src/main/java/com/zongce/app/WidgetActions.kt` | 桌面组件与主页面之间的入口协议（动作常量 + `isEntryAction`）。组件只发 action 字符串，业务一律由主 Activity 承接 |

新增页面、调整页面路由，或接收新的外部入口动作时，优先检查 `MainActivity.kt`。

## 桌面小组件

| 文件 | 职责 |
| --- | --- |
| `app/src/main/java/com/zongce/app/widget/JicunWidget.kt` | 组件界面（Glance）：标题行 + 「拍照 / 相册」两个入口。只构造带 action 的显式 Intent，不加 NEW_TASK 会另起实例，故已加 |
| `app/src/main/java/com/zongce/app/widget/JicunWidgetReceiver.kt` | `GlanceAppWidgetReceiver`，把组件挂到系统的 `APPWIDGET_UPDATE` 广播上 |
| `app/src/main/res/xml/jicun_widget_info.xml` | 组件规格：3×2 格（约 180×110dp）、可伸缩、`updatePeriodMillis=0`（内容是静态入口，无需轮询） |

改组件外观、尺寸或入口按钮时只动 `JicunWidget.kt` 与 `jicun_widget_info.xml`；改点击后的行为一律回到 `MainActivity.kt` + `CaptureScreen.kt`——不要在组件里复制拍照/相册逻辑（见 ADR-0001）。

组件用的两枚图标是自带填色的矢量图（`ic_widget_camera.xml` 白色、`ic_widget_gallery.xml` 主色）；`drawable-xxhdpi/widget_logo.png` 是 logo 的 72px 缩小版，专门给组件标题行用——**别直接引用 `drawable/logo.png`**（1254×1254 / 1.1MB，桌面渲染时会按原图解码，白吃几 MB 内存）。

## 核心规则与图片处理

| 文件 | 职责 |
| --- | --- |
| `core/AcademicYear.kt` | 学年归属、边界日确认和日期格式校验（只有格式错误才阻塞录入） |
| `core/FileNameRule.kt` | 导出图片文件名生成、非法字符清理和长度限制 |
| `core/ImageTools.kt` | JPEG 转换、EXIF 旋转、导出压缩和缩略图生成 |

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
| `export/ExportCheck.kt` | 导出前体检：阻断项（缺字段、照片丢失）与提醒项（待补充字段、边界日、跨学年）。`targetItems()` / `excludedCount()` 是学年过滤的唯一实现，UI 与导出都必须调它们 |
| `export/ZipExporter.kt` | 按五育生成目录、生成清单和写出 ZIP 文件 |

导出流程的主要顺序是：运行 `ExportCheck` → 有阻断项则先修；只有提醒项则过确认页 → 用 `ExportCheck.targetItems()` 取本次范围 → `ZipExporter` → 生成并分享 ZIP。

`ExportCheck.run()`、`ZipExporter.export()`、`AppViewModel.checkBeforeExport()` 的 `targetYear` 都是必填参数：这类"档位"参数一旦带默认值就会随系统日期静默滑动，调用方（尤其是测试）会在不自知的情况下换一个学年。

## 更新层

| 文件 | 职责 |
| --- | --- |
| `update/UpdateChecker.kt` | 读取镜像清单或 GitHub Release、比较版本号、下载 APK 并提供安装用 Uri |

更新源优先级：`BuildConfig.UPDATE_MANIFEST_URL`（构建时由 Gradle 属性 `updateManifestUrl` 写入）→ 未配置或读取失败时回退 GitHub Release。改动更新逻辑时，注意同时检查下载临时文件（`.part`）与 FileProvider 路径（`res/xml/file_paths.xml`）。

## UI 与业务编排

| 文件 | 职责 |
| --- | --- |
| `ui/AppViewModel.kt` | 记录流、照片导入、保存/删除、导出状态（Idle / Blocked / Confirm / Exporting / Done / Error）、更新状态和业务编排 |
| `ui/CaptureScreen.kt` | 相机拍摄、系统图片选择和最近记录展示 |
| `ui/EntryScreen.kt` | 新增、编辑获奖记录和表单校验 |
| `ui/ListScreen.kt` | 记录列表、五育筛选、预览和删除入口 |
| `ui/ExportScreen.kt` | 目标学年选择（仅在可重新体检的状态下开放）、校验反馈、提醒确认页、导出进度和分享 |
| `ui/UpdateDialog.kt` | 更新检查、下载进度和系统安装入口 |
| `ui/GlassNavigationBar.kt` | 液态玻璃底部导航栏（半透明、细描边、选中态） |
| `ui/Theme.kt` | 颜色、字体与圆角等视觉主题定义 |
| `ui/UiKit.kt` | 公共 Compose 控件和照片缩略图 |

页面显示问题优先查对应 Screen；跨页面的数据、状态或导出流程问题优先查 `AppViewModel`；整体视觉调整优先查 `Theme.kt` 与 `GlassNavigationBar.kt`。

## 测试位置

| 文件 | 覆盖内容 |
| --- | --- |
| `app/src/test/java/com/zongce/app/core/AcademicYearTest.kt` | 学年归属、边界和日期格式 |
| `app/src/test/java/com/zongce/app/core/FileNameRuleTest.kt` | 文件名格式、清理和长度限制 |
| `app/src/test/java/com/zongce/app/export/ExportCheckTest.kt` | 导出前体检：照片丢失阻塞、选填字段只提醒、跨学年只提示 |

运行测试：

```powershell
.\gradlew.bat test
```

相机、图片选择器、图片导入、ZIP 分享、更新安装和不同 Android 版本兼容性仍需要在真机或模拟器上验证。

## 构建配置与流水线

| 文件 | 职责 |
| --- | --- |
| `settings.gradle.kts` | 项目名称、模块和依赖仓库配置 |
| `build.gradle.kts` | Android、Kotlin、Compose 和 KSP 插件版本 |
| `app/build.gradle.kts` | Android 模块配置、SDK、签名、依赖和测试配置 |
| `gradle.properties` | Gradle 参数；本地签名与 `updateManifestUrl` 也通过 Gradle 属性注入 |
| `gradle/wrapper/` | Gradle Wrapper |
| `.github/workflows/ci.yml` | push `main` / PR：跑测试、构建 debug APK 并上传产物 |
| `.github/workflows/release.yml` | 标签 `v*.*.*` 或手动触发：注入签名、构建并发布 GitHub Release |

## 架构决策记录

| 文件 | 内容 |
| --- | --- |
| `docs/adr/0001-widget-entry-routing.md` | 桌面组件入口统一由主 Activity 承接，以及液态玻璃导航不引入大型动效框架 |

新增影响面较大的技术选择时，在本目录追加 ADR。

## 常见修改定位

- 修改获奖记录字段：`AwardRecord.kt` → `AwardDao.kt` → `AppViewModel.kt` → `EntryScreen.kt` / `ListScreen.kt`。
- 修改拍照或导入照片：`CaptureScreen.kt` → `AppViewModel.kt` → `PhotoStore.kt`。
- 修改学年判断：`AcademicYear.kt` → `AcademicYearTest.kt` → `ExportCheck.kt`。
- 修改导出目录、文件名或 ZIP 内容：`FileNameRule.kt` / `ZipExporter.kt` → `ExportCheck.kt` → `ExportScreen.kt`。
- 修改更新逻辑或安装流程：`UpdateChecker.kt` → `AppViewModel.kt` → `UpdateDialog.kt`。
- 修改桌面小组件：`widget/JicunWidget.kt` / `res/xml/jicun_widget_info.xml`（外观与尺寸）→ `MainActivity.kt` / `CaptureScreen.kt`（点击后的行为）→ `docs/adr/0001-widget-entry-routing.md`。
- 修改页面路由或外部入口：`MainActivity.kt`。
- 修改通用 UI 控件或主题：`UiKit.kt` / `Theme.kt` / `GlassNavigationBar.kt`。
