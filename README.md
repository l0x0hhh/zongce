# 综测材料管理 Android App

一个面向学生综合素质测评材料整理的 Android 应用。它帮助用户在证书或获奖材料产生时及时记录信息、保存证明照片，并在需要填报时按学校材料要求导出结构化 ZIP 文件。

[English README](README.en.md)

## 功能

- 按“五育”分类管理个人获奖记录。
- 使用相机拍摄证书，或从系统文件/图片选择器导入证明材料。
- 保存获奖名称、获奖时间、获奖级别、获奖等级或名次等信息。
- 使用 Room 在设备本地保存记录；照片保存在应用私有存储中。
- 根据获奖时间自动判断所属学年，并对学年边界日期给出提醒。
- 导出前检查必填信息、学年归属和证明照片是否完整。
- 将材料按五育分类写入 ZIP，并按 `获奖时间_获奖名称_等级.jpg` 规则生成文件名。
- 支持查看、编辑和删除已有记录。

## 技术栈

- Kotlin
- Jetpack Compose + Material 3
- AndroidX Navigation、Lifecycle、ViewModel
- Room + KSP
- Gradle Kotlin DSL

项目当前配置为：`minSdk 26`、`targetSdk 35`、Java/Kotlin JVM target 17，应用版本 `1.0`。

## 项目结构

```text
app/src/main/java/com/zongce/app/
├── core/       学年判断、文件名规则、图片处理
├── data/       Room 数据库、获奖记录和照片存储
├── export/     导出前检查和 ZIP 材料包生成
└── ui/         Compose 页面和 ViewModel

app/src/test/   学年、文件名和导出检查的单元测试
```

## 开始使用

### 环境要求

- Android Studio，能够使用 Android Gradle Plugin 8.5.2。
- JDK 17。
- Android SDK 35。
- 可用的 Gradle 网络依赖下载环境。

### 构建 Debug APK

Windows PowerShell：

```powershell
.\gradlew.bat assembleDebug
```

生成的 APK 位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

也可以直接使用 Android Studio 打开项目根目录并运行 `app` 模块。

### 运行测试

```powershell
.\gradlew.bat test
```

测试主要覆盖纯 Kotlin 规则逻辑，包括学年归属、文件名生成和导出前检查。完整的真机相机、文件选择器、图片导入和 ZIP 分享流程仍应在 Android 设备或模拟器上进行验证。

## 导出规则

导出目标学年由当前日期确定。应用会阻止缺少获奖名称、获奖时间或证明照片的记录导出，并提醒用户确认学年边界日期。导出的 ZIP 内按五育生成目录，照片文件名使用获奖时间、获奖名称和等级组合生成；同一记录的多张照片会追加序号。

## 数据与隐私

当前项目采用本地存储，不依赖后端服务。获奖记录和证明照片默认保存在设备本地；导出文件会写入应用缓存目录，并通过 Android 文件分享能力交给用户处理。请用户自行备份重要材料，并在提交学校系统前核对导出内容。

## 当前状态

项目已完成 Git 初始化并推送至 GitHub，当前版本为早期可运行原型。仓库暂未包含发布签名配置、正式 APK 或自动化 CI/CD 流程。

## License

当前仓库尚未声明开源许可证。如需公开分发，请先补充合适的 License 文件。
