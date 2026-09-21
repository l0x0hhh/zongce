# 暨存 · 综测材料管理（Zongce）

> 证书还在手上的时候存进来，需要填报的时候一键导出。

暨存是一个 **local-first 的 Android 应用**，用来管理综合素质测评（综测）需要的证明材料：把零散的证书照片按五育归档，需要填报时按学年打包成可以直接上传的文件夹。

- 项目/包名：`zongce`（`com.zongce.app`），应用显示名：**暨存**
- 最低支持 Android 8.0（API 26），目标 SDK 35
- 无需注册账号，无后端服务

> **独立项目声明**：暨存是个人独立项目，与任何学校或教育机构均无隶属关系，也不代表任何学校的测评规则。提交材料前请始终以学校官方系统和通知为准。

## 它解决什么问题

综测填报的痛点集中在最后一刻：证书拍完就散在相册里，填报时要在几百张照片里翻找；学校系统还要求"上传前改好文件名""单文件不超过 4M""只能一个一个传"。暨存把这段手工劳动固定在"拿到证书的那一刻"完成：拍照 → 归到五育 → 填名称和时间，导出时自动整理成规范的文件夹和文件名。

## 功能

- **按五育归档**：德育 / 智育 / 体育 / 美育 / 劳育，一键筛选，支持"只看待补充"。
- **拍照或导入**：调用系统相机拍摄，或用系统图片选择器导入（不申请相机和存储权限）。
- **记录字段**：获奖名称、获奖时间、获奖级别、获奖等级或名次、本人角色、发证单位、备注，另附照片 1..N 张。
- **学年自动归属**：按获奖时间自动判断学年（每年 9 月 1 日滚动），学年首尾日单独提示确认。
- **导出前体检**：缺名称、缺时间、日期格式错误、未选五育、没有照片、照片文件丢失会**阻断**导出；级别/等级/角色缺失、边界日、其他学年记录只做**提醒**。
- **一键打包**：按五育目录生成 ZIP，附带可用记事本打开的 `填报核对.txt`。
- **文件名规范化**：`获奖时间_获奖名称_等级.jpg`，并保证排序即时间序。
- **图片处理**：EXIF 方向纠正、HEIC 转 JPG、导出时自动压到 3.5MB 以内。
- **原图永久保留**：照片存进应用私有目录后不会被清理，导出只做转换不改原件。
- **应用内检查更新**：镜像清单优先、GitHub Release 回退，下载后交给系统安装器。

## 导出的材料包长什么样

```
综测证明材料_2025-2026学年.zip
├── 填报核对.txt              # UTF-8 带 BOM，记事本双击不乱码
├── 德育/                     # 五个五育文件夹都会建出来（空的也建）
│   └── 20251123_全国大学生信息安全竞赛_第1等级.jpg
├── 智育/
├── 体育/
├── 美育/
└── 劳育/
```

- 照片放进**录入时选择的那个五育**文件夹；同一记录多张图按 `_2`、`_3` 递增。
- `填报核对.txt` 按五育分节，逐条列出名称、时间、级别、等级、角色、发证单位和照片相对路径，每条末尾留一个 `已填报：☐` 勾选位，方便对着系统一条条核对。
- 文件名规则：`YYYYMMDD_获奖名称[_等级].jpg`，含扩展名总长不超过 40 字符（学校系统里显示得全），名称最多 20 字符、等级最多 10 字符，非法字符 `\ / : * ? " < > |` 替换为 `-`，日期补零保证**按文件名排序等于按获奖时间排序**。
- 先写 `.part` 临时文件、成功后再改名，导出中断不会留下可误用的半成品。

## 数据与隐私

- 获奖记录用 Room（SQLite）保存在设备本地；照片原图存在应用私有目录 `filesDir/photos/`，按内容哈希命名，导出时才映射成规范文件名。
- 不需要账号、不需要登录，**没有把材料上传到服务器的功能**。
- 应用只申请两个权限：`INTERNET`（仅用于检查更新）和 `REQUEST_INSTALL_PACKAGES`（安装下载的更新包）。相机由系统相机应用完成，选图走系统图片选择器，因此**没有相机和存储权限**。
- 唯一的对外网络请求是更新检查：读取配置的镜像 `latest.json`，未配置或不可用时回退到 GitHub Release；只取版本号和下载地址，不发送任何材料内容。
- ⚠️ **注意系统云备份**：清单中 `android:allowBackup="true"`，Android 的自动备份可能把应用数据库和照片副本纳入用户自己的云备份（由系统设置决定）。应用本身不会上传，但不等于数据物理上只存在于本机。
- 请不要公开未脱敏的证书照片、导出的 ZIP、日志或截图，其中可能包含姓名、学号、证件信息。

## 安装

### 直接安装

到 [Releases](https://github.com/l0x0hhh/zongce/releases) 下载最新 `jicun-<版本>.apk`。首次安装需要在系统里允许"安装未知来源应用"。

### 国内网络

GitHub 访问不稳定时，可以自建镜像：把 `latest.json` 放在镜像根目录，构建时通过 Gradle 属性 `updateManifestUrl` 写入 APK，应用会优先读镜像、失败自动回退 GitHub Release。

```json
{
  "version": "1.2.0",
  "title": "暨存 1.2.0",
  "notes": "优化照片录入和导出流程",
  "apkUrl": "https://download.example.com/jicun/jicun-1.2.0.apk"
}
```

### 自行构建

用 Android Studio 打开仓库根目录，等待 Gradle 同步后运行 `app` 模块即可。命令行：

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon     # 产物：app/build/outputs/apk/debug/app-debug.apk
.\gradlew.bat :app:testDebugUnitTest --no-daemon # JVM 单元测试
```

## 使用流程

1. **快速存证**：首页点拍照或从相册选择，进入录入页。
2. **填基本信息**：选五育（必填，决定导出进哪个文件夹）、填获奖名称（必填，会进文件名）和获奖时间（必填）。级别 / 等级 / 角色 / 发证单位可以后补，会在导出前提醒。
3. **在记录页管理**：按五育筛选、查看、编辑、删除。
4. **导出**：选目标学年 → 跑导出前体检 → 通过后生成 ZIP → 通过系统分享发送到微信 / 网盘 / 电脑。
5. **对着 `填报核对.txt` 逐条填系统**，勾掉已经填过的。

> 拍摄日期 ≠ 获奖日期。请以证书或官方证明上的获奖时间为准，学年首尾日期的记录导出前会单独提醒确认。

## 项目结构

```
app/src/main/java/com/zongce/app/
├── MainActivity.kt        # 单 Activity、Compose 导航、更新弹窗入口
├── WidgetActions.kt       # 桌面组件入口协议（仅动作定义，见下方"已知未完成"）
├── core/                  # 纯 Kotlin 规则：学年归属、文件名、图片处理
├── data/                  # Room 实体/DAO/数据库 + 私有目录照片仓库
├── export/                # 导出前体检 + ZIP 打包
├── ui/                    # Compose 页面、液态玻璃底栏、主题、ViewModel
└── update/                # 更新检查与 APK 下载
app/src/test/              # 学年、文件名、导出体检的 JVM 单元测试
docs/adr/                  # 架构决策记录
```

调用链：`MainActivity` → `AppViewModel`（记录流、照片导入、导出状态）→ `AwardDao` / `PhotoStore` / `ExportCheck` / `ZipExporter`。技术栈：Kotlin + Jetpack Compose（BOM 2024.09.02）+ Material 3 + Navigation Compose + Room 2.6.1（KSP）+ ExifInterface，JDK 17。

更细的模块定位和"改哪里"见 [CODE_STRUCTURE.md](CODE_STRUCTURE.md)，开发与提交规范见 [AGENTS.md](AGENTS.md)。

## 开发与发布

- **CI**（`.github/workflows/ci.yml`）：推 `main` 或提 PR 时跑 `./gradlew test` + `:app:assembleDebug`，push 时上传 debug APK 产物（保留 14 天）。
- **发布**（`.github/workflows/release.yml`）：推 `v*.*.*` 标签或手动触发，解出签名密钥 → 跑测试 → 构建签名 Release APK → 重命名为 `jicun-<版本>.apk` → 创建 GitHub Release（`versionCode = 1000000 + 构建号`）。
- **签名 Secrets**：`ANDROID_KEYSTORE_BASE64`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD`。密钥只放 GitHub Actions Secrets，不提交仓库（`*.keystore` 已在 `.gitignore` 中）。
- 未配置签名参数时本地仍可构建 release，只是产物未签名。

## 已知未完成

- **桌面小组件尚未实现**：`WidgetActions` + `MainActivity` 只完成了入口协议与路由（收到 `WIDGET_CAPTURE` / `WIDGET_PICK_PHOTOS` 就跳录入页），真正的 AppWidget 还需要按 ADR-0001 注册 receiver 并接入 Glance。
- **真机验证未做**：相机、图片选择器、图片导入、ZIP 分享和各 Android 版本兼容性仍待在真机或模拟器上验证。单元测试只覆盖纯规则（学年归属、文件名生成、导出体检）。
- `isMinifyEnabled = false`，release 未开 R8 混淆压缩。

## 当前版本

`1.1.0-dev`（应用 `versionName 1.1.0` / `versionCode 2`）。版本与开发会话记录见 [VERSION_HISTORY.md](VERSION_HISTORY.md)。

## 反馈与贡献

欢迎通过 GitHub Issues 提交可复现的 Bug 和明确的功能建议。**提交前请移除所有个人信息**：证书原图、导出的 ZIP、日志、本地路径等。

## License

[MIT](LICENSE)。可以自由使用、修改、分发（含商用），只需保留原始版权声明与许可声明。

## 免责声明

暨存不是任何学校的官方应用，不代表任何学校的测评规则。学校系统的字段、时间窗口和材料格式要求都可能变化，提交前请以学校官方通知为准。

---

English: [README.en.md](README.en.md) · 代码结构：[CODE_STRUCTURE.md](CODE_STRUCTURE.md) · 版本记录：[VERSION_HISTORY.md](VERSION_HISTORY.md) · 开发规范：[AGENTS.md](AGENTS.md)
