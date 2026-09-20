# 综测 · 综合测评材料管理

## 证书在手，材料归档；需要填报时，一键导出。

综测是一款 local-first 的 Android 综合素质测评材料管理工具。它围绕学生日常获得的证书和获奖材料，提供拍摄、记录、分类、校验和导出能力，把零散的证明照片整理成可提交的材料包。

> 综测是独立项目，不隶属于任何学校或教育机构。导出材料提交前，请始终通过学校官方系统和通知核对要求。

## 安装

当前项目处于早期开发阶段，暂未发布正式 APK 或应用商店版本。可以从源码构建 Debug APK：

```powershell
git clone https://github.com/l0x0hhh/zongce.git
cd zongce
.\gradlew.bat assembleDebug
```

生成文件：`app/build/outputs/apk/debug/app-debug.apk`

应用最低支持 Android 8.0（API 26）。也可以用 Android Studio 打开仓库根目录，等待 Gradle 同步后运行 `app` 模块。

## 使用流程

1. 打开应用，拍摄证书或从系统选择器导入证明照片。
2. 选择所属“五育”，填写获奖名称和获奖时间。
3. 根据证书补充获奖级别、等级或名次等信息。
4. 在记录列表中查看、编辑或删除材料。
5. 进入导出页面，选择目标学年并查看导出前检查结果。
6. 通过检查后生成 ZIP 材料包，并在提交前核对内容。

拍摄日期不等于获奖日期。请以证书或官方证明上的获奖时间为准。

## 功能

- 按“五育”分类管理获奖记录。
- 使用相机拍摄证书，或从系统文件/图片选择器导入材料。
- 保存获奖名称、获奖时间、获奖级别、获奖等级或名次。
- 根据获奖时间自动判断所属学年。
- 对学年边界日期和超出目标学年的记录给出提醒或阻断。
- 在导出前检查必填字段和证明照片是否完整。
- 按五育目录导出 ZIP 材料包。
- 按 `获奖时间_获奖名称_等级.jpg` 规则生成可读文件名。
- 处理图片方向、JPEG 转换和缩略图展示。
- 支持查看、编辑和删除已有记录。

## 数据与隐私

- 获奖记录使用 Room 保存在设备本地。
- 证明照片保存在应用私有存储中，不需要后端服务。
- 导出文件写入应用缓存目录，并通过 Android 文件分享能力交给用户处理。
- 应用不会把材料自动上传到服务器。

请不要公开未经脱敏的证书照片、导出 ZIP、日志或截图，其中可能包含姓名、学号、证件信息和其他个人数据。

## 开发

### 环境要求

- Android Studio
- JDK 17
- Android SDK Platform 35
- 可下载 Gradle 依赖的网络环境

### 构建与测试

```powershell
.\gradlew.bat test
.\gradlew.bat assembleDebug
```

测试主要覆盖学年归属、文件名生成和导出前检查等纯 Kotlin 规则。相机、文件选择器、图片导入、ZIP 分享和不同 Android 版本兼容性仍应在真机或模拟器上验证。

## 架构

Room 是本地数据源。Compose 页面通过 `AppViewModel` 读取和修改记录，照片由 `PhotoStore` 管理，导出前由 `ExportCheck` 校验，最后由 `ZipExporter` 生成 ZIP 文件。

项目使用 Kotlin、Jetpack Compose、Material 3、AndroidX、Room 和 KSP。详细模块定位见 [CODE_STRUCTURE.md](CODE_STRUCTURE.md)。

## 项目状态

当前版本：`1.1.0-dev`

项目已完成基础记录、照片管理、学年校验和 ZIP 导出流程，但仍处于开发阶段。当前尚未完成正式发布签名、自动化 CI/CD 和完整真机验收。版本和会话更新记录见 [VERSION_HISTORY.md](VERSION_HISTORY.md)。

## 帮助与贡献

欢迎通过 GitHub Issues 提交可复现的 Bug 和明确的功能建议。提交问题时请移除所有个人信息、证书原图、认证信息和本地路径。

## License

当前仓库尚未声明开源许可证。如需公开分发或接受外部贡献，请先补充合适的 License 文件。

## 免责声明

综测不是任何学校官方应用，也不代表学校的测评规则。学校系统的字段、时间窗口和材料要求可能发生变化，请以学校官方通知为准。

---

English version: [README.en.md](README.en.md) · Code structure: [CODE_STRUCTURE.md](CODE_STRUCTURE.md) · Version history: [VERSION_HISTORY.md](VERSION_HISTORY.md)
