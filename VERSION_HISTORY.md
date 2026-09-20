# 版本记录卡

> 每次开始新的开发会话前，先阅读本文件。完成代码修改后，在提交前同步更新本文件。

## 当前版本

| 项目 | 内容 |
| --- | --- |
| 版本号 | `1.1.0-dev` |
| 版本状态 | 开发中 |
| 最近更新 | 2026-09-20 |
| 最近文档提交 | `9e58c24` |
| Android 应用版本 | `versionName 1.0` / `versionCode 1` |
| Git 分支 | `main` |
| 远端 | `https://github.com/l0x0hhh/zongce.git` |

## 本版本更新

- 初始化 Android 项目 Git 仓库并推送到 GitHub。
- 增加中英文项目说明：`README.md`、`README.en.md`。
- 增加代码结构说明：`CODE_STRUCTURE.md`。
- 增加本版本记录卡，统一记录后续会话的开发上下文。
- 已忽略 `.agents/`、构建产物、IDE 配置和本地配置文件。

## 当前功能基线

- 按五育分类管理获奖记录。
- 拍摄或导入证书照片，并在设备本地保存。
- 使用 Room 保存获奖记录和照片关联信息。
- 校验获奖时间所属学年、必填字段和证明材料完整性。
- 按五育目录导出 ZIP 材料包，并生成规范化文件名。
- 支持记录查看、编辑和删除。

## 验证状态

- [x] Git 仓库初始化完成。
- [x] `main` 分支已推送到 GitHub。
- [x] README、英文 README 和代码结构文档已完成。
- [x] 文档执行 `git diff --check` 通过。
- [ ] Android Debug APK 构建验证。
- [ ] JVM 单元测试验证。
- [ ] 真机或模拟器功能验证。

## 当前未提交改动

以下文件在记录本版本时已经存在未提交改动。后续会话必须先确认这些改动属于哪个功能，再决定是否提交：

- `app/src/main/java/com/zongce/app/MainActivity.kt`
- `app/src/main/java/com/zongce/app/export/ExportCheck.kt`
- `app/src/main/java/com/zongce/app/ui/AppViewModel.kt`
- `app/src/main/java/com/zongce/app/ui/CaptureScreen.kt`
- `app/src/main/java/com/zongce/app/ui/EntryScreen.kt`
- `app/src/main/java/com/zongce/app/ui/ListScreen.kt`
- `app/src/main/java/com/zongce/app/ui/UpdateDialog.kt`
- `app/src/main/java/com/zongce/app/update/`

## 后续会话规则

1. 开始工作前读取本文件，并执行 `git status --short --branch`。
2. 先确认当前版本号和未提交改动，再分析用户的新需求。
3. 完成一个功能阶段后更新“本版本更新”和“验证状态”。
4. 提交代码前更新最近更新日期、版本号和提交说明。
5. 不把不属于当前功能的已有改动混入提交。
6. 推送后补充远端提交号，并保留未验证事项。

## 版本号约定

- `主版本号`：重大架构或产品方向变化。
- `次版本号`：新增完整功能或较大的用户流程。
- `修订号`：Bug 修复、文档更新和小范围优化。
- `-dev`：正在开发，尚未完成完整验证。
- `-verified`：已完成项目约定的构建、测试和必要的设备验证。
