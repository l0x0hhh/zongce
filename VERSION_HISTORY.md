# 版本记录卡

> 每次开始新的开发会话前，先阅读本文件。完成代码修改后，在提交前同步更新本文件。

## 当前版本

| 项目 | 内容 |
| --- | --- |
| 版本号 | `1.1.0-dev` |
| 版本状态 | 开发中 |
| 最近更新 | 2026-09-21 |
| 最近文档提交 | `70b296a` |
| Android 应用版本 | `versionName 1.1.0` / `versionCode 2` |
| Git 分支 | `main` |
| 远端 | `https://github.com/l0x0hhh/zongce.git` |
| 工作区状态 | 干净，与 `origin/main` 同步（本批已提交并推送：`70b296a`） |

## 本版本更新

- 新增 MIT 许可证（`LICENSE`，Copyright (c) 2026 l0x0hhh），README 的 License 章节同步改为 MIT；落地页底栏的「许可证」入口指向该文件。
- 重写 `README.md` 与 `README.en.md`：按代码事实校正表述，补齐导出包结构、文件名约束、权限清单、更新流程、CI/Release 流水线、项目结构和已知未完成项。
  - 校正项：应用显示名为「暨存」，仓库/包名为 `zongce`（`com.zongce.app`）；学年校验只在日期格式错误时阻塞，合法的跨学年记录在导出时过滤并提示；隐私表述改为如实说明系统云备份（`allowBackup="true"`）可能带走数据库与照片副本。
  - 新增章节：导出的材料包长什么样、已知未完成（桌面组件未实现、无真机验证、未开 R8）。
- 增加 GitHub Actions CI、标签发布和签名 Release 工作流。
- 增加国内镜像更新清单优先、GitHub Release 回退的更新源逻辑。
- 明确「仓库完整路径必须全 ASCII」的硬性要求（`AGENTS.md` 与中英文 README 同步说明）：路径含中文时 Gradle worker 进程无法启动（`ClassNotFoundException: GradleWorkerMain`），`assembleDebug` 可能照过但 `testDebugUnitTest` 必挂；修法是搬移或改名仓库，而不是改 `GRADLE_USER_HOME`。仓库已迁至纯 ASCII 路径 `E:\AIstudy\project\Jicun`。
- `.gitignore` 增加 `.gradle-user/`（工作区自带的预热 Gradle 缓存）、`worker-info.log` 与 `hs_err_pid*.log`（Gradle/JVM 崩溃诊断，机器相关，不入库）。
- 修正 `ExportCheckTest.missingPhotoFileBlocksExport` 的时间依赖：原本依赖 `ExportCheck.run` 的默认目标学年（随系统日期滚动），导致该用例长期为红；改为显式传入与获奖日期一致的 `targetYear`。
- 把导出体检的「提醒项」接进用户流程：无阻断项但存在提醒项时先显示确认页（边界日「请对照证书确认」、选填字段「待补充」、跨学年过滤条数），点「继续导出」才打包。此前这三条提醒会被整体丢弃 —— 无阻断项时状态直接置 `Idle` 并开始导出，而 `ExportState.Done` 不携带 issues，界面永远收不到。
- 学年判定收敛为 `ExportCheck.targetItems()` / `excludedCount()` 一份实现，界面与导出共用。这同时修掉「另有 N 条记录属于其他评价学年」因调用方先过滤一遍而永远算成 0、从不显示的问题。
- `ExportCheck.run()`、`ZipExporter.export()`、`AppViewModel.checkBeforeExport()` 的 `targetYear` 改为必填参数，根除「默认值随系统日期静默滑动」这一类缺陷；`AGENTS.md` 补入对应规范（档位参数不得有默认值、学年过滤只保留一份实现、测试必须显式钉住学年）。
- 导出流程中锁定学年下拉（仅 `Idle` / `Blocked` 状态可改），避免确认之后改档位导致显示范围与实际打包范围不一致。
- `ExportCheckTest` 新增 2 个用例（总数 12）：选填字段只提醒不阻塞、跨学年记录只提示且过滤计数正确。
- 修掉 release 流水线的**签名路径缺陷**：`app/build.gradle.kts` 读 `signingStoreFile` 用的是模块级 `file()`，**相对路径按 `app/` 模块目录解析**，而 `.github/workflows/release.yml` 是把解出来的 keystore 放在**仓库根**的 → CI 必然报 `.../app/release.keystore not found`。改用 `rootProject.file()`：相对路径=仓库根、绝对路径原样使用，两种写法都对，本地与 CI 语义一致。已做修复前/修复后的对照复现（修复前同一命令失败、修复后通过）。

- 初始化 Android 项目 Git 仓库并推送到 GitHub。
- 增加中英文项目说明：`README.md`、`README.en.md`。
- 增加代码结构说明：`CODE_STRUCTURE.md`。
- 增加本版本记录卡，统一记录后续会话的开发上下文。
- 已忽略 `.agents/`、构建产物、IDE 配置和本地配置文件。

## 当前功能基线

- 按五育分类管理获奖记录（德育 / 智育 / 体育 / 美育 / 劳育）。
- 拍摄或导入证书照片，并在设备本地保存；原图永久保留，按内容哈希命名。
- 使用 Room 保存获奖记录和照片关联信息。
- 校验获奖时间所属学年、必填字段和证明材料完整性（阻断项 + 提醒项）。
- 导出前提醒确认页：存在提醒项（边界日、待补充字段、跨学年过滤）时先过目确认，再生成材料包。
- 按五育目录导出 ZIP 材料包，生成规范化文件名与 `填报核对.txt` 清单。
- 应用内检查更新：镜像 `latest.json` 优先，GitHub Release 回退。
- 支持记录查看、编辑和删除。

## 验证状态

- [x] Git 仓库初始化完成。
- [x] `main` 分支已推送到 GitHub。
- [x] README、英文 README 和代码结构文档已完成。
- [x] MIT 许可证与中英文 README 已同步到远端。
- [x] 文档执行 `git diff --check` 通过。
- [x] 仓库路径已迁至纯 ASCII（`E:\AIstudy\project\Jicun`），Gradle worker 启动失败（`ClassNotFoundException: GradleWorkerMain`）问题消除。
- [x] **JVM 单元测试验证（本批改动）：12 个用例全部通过**（`AcademicYearTest` 6 + `FileNameRuleTest` 3 + `ExportCheckTest` 3），0 失败 0 错误 0 跳过。命令：
  ```
  export GRADLE_USER_HOME='E:\AIstudy\project\Jicun\.gradle-user'
  ./gradlew :app:testDebugUnitTest --rerun --console=plain --no-daemon \
      -Dorg.gradle.jvmargs="-Xmx1536m -Dfile.encoding=UTF-8" --max-workers=1 \
      -Pkotlin.compiler.execution.strategy=in-process
  ```
  结果：`BUILD SUCCESSFUL in 2m 27s`，`26 actionable tasks: 7 executed, 19 up-to-date`（`7 executed` 说明 `compileDebugKotlin`、`compileDebugUnitTestKotlin`、`testDebugUnitTest` 确为真实执行，非 UP-TO-DATE 假绿）；证据在 `app/build/test-results/testDebugUnitTest/TEST-*.xml`，时间戳 `2026-09-21T04:17:39Z`。
- [x] Android Debug APK 构建验证（CI）：GitHub Actions `CI #5` 对提交 `70b296a` 的 `verify` 作业全部通过，含 `Assemble debug APK` 与 `Upload debug APK`，产物 `jicun-debug-70b296a…`（16.2 MB）。这是本仓库 **CI 首次转绿** —— 此前 `CI #1`～`#4` 全部失败，且失败步骤恒为 `Run unit tests`，APK 步骤因此每次都被跳过（根因即上面那条时间依赖的红测试，修好后连带消失）。
- [x] **正式签名链路验证（本机实签）**：用重新生成的 keystore 执行 `./gradlew :app:assembleRelease -PsigningStoreFile=release.keystore …`（刻意用**相对路径**，与 CI 的 `gradle.properties` 写法一致）→ `BUILD SUCCESSFUL`，产出 **`app-release.apk`（不是 `-unsigned`）**，`apksigner verify` 报 **`Verifies`**（v2 方案、1 个签名者、RSA 2048）。同一命令在修复签名路径前会报 `app/release.keystore not found`。
- [ ] 真机或模拟器功能验证。

## 最近一次提交

`70b296a` `feat: confirm export check warnings before packing` —— 11 个文件，`+250 / -49`，已推送至 `origin/main`。

| 文件 | 改动 |
| --- | --- |
| `.gitignore` | 增加 `.gradle-user/`、`worker-info.log`、`hs_err_pid*.log` |
| `AGENTS.md` | 补「仓库完整路径必须全 ASCII」；新增两条规范：档位参数不得用「随今天」的默认值、学年过滤只保留一份实现；测试必须显式钉住学年 |
| `README.md` / `README.en.md` | 命令行章节补 ASCII 路径要求与 `.gradle-user` 用法 |
| `export/ExportCheck.kt` | 抽出 `targetItems()` / `excludedCount()` 供界面与导出共用；`run()` 的 `targetYear` 改必填 |
| `export/ZipExporter.kt` | `export()` 的 `targetYear` 改必填；移除仅服务于该默认值的 `AcademicYear` 导入 |
| `ui/AppViewModel.kt` | 新增 `ExportState.Confirm` 与 `confirmExport()`；`checkBeforeExport()` 的 `targetYear` 改必填、改为把全量记录交给体检 |
| `ui/ExportScreen.kt` | 新增提醒确认分支（继续导出 / 返回修改）；学年下拉仅在 `Idle` / `Blocked` 开放 |
| `test/.../ExportCheckTest.kt` | 时间依赖修复 + 新增 2 个用例（选填字段只提醒、跨学年只提示） |
| `CODE_STRUCTURE.md` | 同步导出流程、状态枚举与测试覆盖说明 |

这一批是一个整体（同一批体检/导出流程的修复与增强），不要再拆成"测试文件一个提交、源码一个提交"——先提交的那一半无法编译。

### 已知待处理

- 两处既有的编译告警（非本批引入，`compileDebugKotlin` 输出）：`ExportScreen.kt` 的 `LinearProgressIndicator(progress = …)` 已废弃（应改用接收 lambda 的重载）；`GlassNavigationBar.kt` 的 `Icons.Filled.List` 已废弃（应改用 `Icons.AutoMirrored.Filled.List`）。
- `ExportScreen` 顶部的「$targetYear · $selectedCount 条记录」仍用 `belongsTo` 计数，与 `ExportCheck.targetItems()` 的判定在「日期缺失或非法」时不一致。这类记录会被体检阻断，所以目前只影响显示，待统一。
- `gradle.properties` 的 `android.overridePathCheck=true` 是当年中文路径时期的产物，AGP 每次构建都会警告它「experimental」。路径已是纯 ASCII，这项可以删掉。
- 界面新增的提醒确认页还没上真机/模拟器过一遍（CI 只做到构建与单测）。
- **Release 仍未发布成功**：tag `v1.1.0` 已存在，Release 流水线两次失败的原因都已定位并修掉（第一次＝4 个签名 Secret 未配好；第二次＝上面那条签名路径缺陷）。剩下只有在仓库 Secrets 里填好 `ANDROID_KEYSTORE_BASE64` / `ANDROID_KEYSTORE_PASSWORD` / `ANDROID_KEY_ALIAS` / `ANDROID_KEY_PASSWORD`（keystore 已于 2026-09-21 13:26 重新生成），然后重跑流水线。**注意：仓库至今没有任何 Release，落地页的下载入口和 App 内的更新检查都指向它。**

早先的记录卡曾把 `MainActivity.kt`、`ExportCheck.kt`、`AppViewModel.kt`、`CaptureScreen.kt`、`EntryScreen.kt`、`ListScreen.kt`、`UpdateDialog.kt` 和 `update/` 列为未提交改动，这些文件已在提交 `9e8686d` 中入库。

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
