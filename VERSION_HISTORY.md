# 版本记录卡

> 每次开始新的开发会话前，先阅读本文件。完成代码修改后，在提交前同步更新本文件。

## 当前版本

| 项目 | 内容 |
| --- | --- |
| 版本号 | `1.3.6` |
| 版本状态 | 待打 tag `v1.3.6` 走 CI 发布。**签名口令仍未取回**：keystore 在仓库根（`jicun-release.keystore`），口令只在 GitHub Secrets，因此本机**无法构建签名包**，发布必须走 tag + CI |
| 最近更新 | 2026-09-23 |
| Android 应用版本 | `versionName 1.3.6`（本地默认）；正式包由 tag 与流水线注入 `versionCode 1000000+run_number`；本机无口令构建不了可安装的验证包（历史做法是手动传 `-PreleaseVersionCode` 且须高于线上，例如 `1000009` > 线上 v1.3.2 的 `1000008`） |
| Git 分支 | `main` |
| 远端 | `https://github.com/l0x0hhh/zongce.git` |
| 工作区状态 | 发布前 5 个文件待提交（成果组件学年同步修复 4 个 + 版本号），已核对与 `origin/main`（`f71db8e`）同步、无并发会话推进 HEAD |

## 本版本更新（v1.3.6）

### 成果小组件：修「在 App 内选完学年，回桌面组件不刷新」

- **根因不是刷新时机 / 生命周期，是状态源分裂**。
  - `AchievementScreen` 的 `selectedYear` 是 `remember { mutableStateOf(...) }` 的 Compose 本地状态，点 chip 只改内存；
  - 组件读的是 `WidgetYearStore`（SharedPreferences）；
  - 全仓 grep 证实 `WidgetYearStore` **只有组件自己读写**（渲染时 `current()` 读、桌面箭头 `shift()` 写），**App 内代码零写入点**。
  - 于是「在桌面点箭头」一直正常，而「在 App 内选学年」这条路径从来没接上 —— 渲染层做得再正确，读到的仍是旧学年。
- **为什么 v1.3.3 / v1.3.4 / v1.3.5 三轮修复全部无效**（可复用的判断规则）：v1.3.3 修 `apply()→commit()`（写盘时序）、v1.3.4 修 `setRemoteAdapter` Intent 唯一化（RemoteViews 缓存）、v1.3.5 整包返工渲染方案 —— **三次都在渲染层打转，断点在状态层**。一个 bug 跨多版、换了实现依旧存在时，几乎可以断定它不在被反复改动的那一层，应先查「数据来源 / 状态归属 / 谁读谁写」。
- **修复四处（缺一不可）**：
  1. `widget/WidgetYearStore.kt` 新增 `set(context, year)`：与 `shift()` 一样用 **`commit()` 同步落盘** —— 调用方写完立刻要推送组件重渲染，必须当场读到刚写的值，`apply()` 的异步写盘做不到。
  2. `ui/AchievementScreen.kt` chip `onClick` 调 `vm.syncWidgetYear(year)`：写盘 + 主动推送所有成果组件重渲染。放在点击回调里、不在重组路径上，一次点击只触发一次。
  3. `ui/AppViewModel.kt` 新增 `initialWidgetYear()`：成果页初值以组件存储为准（打开就选中桌面组件正在显示的学年），且**自己查一次库**算学年列表再回落 —— 不能复用 UI 的 `items`，Room `StateFlow` 首帧是空列表、`yearsOf()` 又永远包含当前学年，会把存储里的学年误判成「已不存在」而错误回落。`selectedYear` 改 `String?`（null = 初值未读回），`LaunchedEffect(Unit)` 仅在为 null 时应用初值（防用户先点 chip 被覆盖），`LaunchedEffect(years)` 的回落改为 null 时直接 return。
  4. **串行化写入队列**（QA 二轮独立验证挖出的并发缺口）：`syncWidgetYear` 原为「每次点击起一个 `Dispatchers.IO` 协程」，而 `Dispatchers.IO` 是多线程池 —— **连点两个 chip 时两次「写盘 + 推送」执行顺序无保证**，坏交错下最终落盘的是先点的学年，表现为「明明选的是最后点的，组件却停在之前那个」（低概率、高混淆）。改为**单消费者 `Channel(UNLIMITED)` + 一个常驻 IO 协程**：严格按入队顺序（= 点击顺序）执行，最后一次点击最后生效；容量 UNLIMITED 防连点溢出丢事件；挂在 `viewModelScope` 下随 ViewModel 销毁自动取消。未用 `Dispatchers.IO.limitedParallelism(1)`（项目 coroutines 缓存最高 1.7.3，该 API 当时仍是实验性，会留下多余 `@OptIn` 警告）。
- 验证：`:app:testDebugUnitTest --rerun` **60 个用例全绿**（`failures=0 errors=0`），但本修复属**跨进程状态同步，本地单测覆盖不到，必须真机验证**。真机清单：① 首屏默认选中 = 组件当前学年；② App 内点 chip 回桌面组件**立刻**变；③ 桌面箭头切学年后重开 App 读回一致；④ 快速连点两 chip 终态 = 最后点的；⑤ 删光所选学年记录后两边回落一致；⑥ 保存/删除记录后组件刷新且学年不跳；⑦ 回归导出与启动自动检查更新。
- 诊断与修复报告：`成果组件学年同步-诊断与修复.md`（仓库根 `Jicun/`，与方案文档平铺）。

## v1.3.5 更新（历史）

### 一、启动时静默自动检查更新

- 背景：更新检查的主体（双源检查、更新弹窗、下载安装、权限、FileProvider、`UpdateCheckerTest`）早已就位，**唯一缺口是启动时没有自动触发** —— 此前必须用户自己点录入页右上角的「检查更新」。本版补上这一环。
- **新增 `update/UpdateThrottle.kt`**：节流纯函数 `shouldAutoCheck(lastCheckedDate, today)` + SharedPreferences 读写（`update_prefs` / `last_auto_update_check_date`，存 `yyyy-MM-dd`）。**只记「成功完成」的日期，失败不记** —— 否则今天断网失败一次，用户一整天都不会再自动检查；只记成功才能让失败在下次启动自然重试。抽成不依赖 Android 的纯函数是为了能被 JVM 单测直接覆盖。
- **`ui/AppViewModel.kt` 新增 `autoCheckForUpdate()`**，三条语义：
  1. **静默**：不置 `Checking`（否则启动瞬间会弹出一个转圈的「正在检查更新」弹窗）；无新版、检查失败、断网、更新源未配置一律保持 `Idle`、只打一条 `Log.i`；仅确实有新版本时才置 `Available`，复用已有 `UpdateDialog` 弹出。
  2. **每天最多一次**：今天已自动查过就直接跳过、不发网络请求。理由是 GitHub 未认证接口限流 60 次/小时/IP，App 面向全校学生，高峰冷启动容易被整段打满。
  3. **并发保护**：正在检查/下载时直接跳过，不打断用户操作。
  - 同时抽出 `private suspend fun runUpdateCheck()` 让手动与自动两条路径共用，避免逻辑各自漂移。
- **`MainActivity.kt`** 在 `App()` 内、`Scaffold` 之前加 `LaunchedEffect(Unit) { vm.autoCheckForUpdate() }`。**手动入口「检查更新」行为一字未改**：照常显示 `Checking`、照常提示「已经是最新版本」、失败照常报错，且**不受每天一次限制**；手动成功后也刷新日期，避免用户刚手动查完、紧接着重开 App 又自动查一遍。
- **竞态修复（QA 独立验证挖出的真缺陷）**：静默检查还在飞的时候用户手动点「检查更新」——手动守卫只看状态（此刻是 `Idle`）所以放行、置 `Checking`、发起第二条请求，两条协程并发写同一状态。后果是用户点了「稍后」把弹窗关掉之后，**晚到的静默结果又把弹窗弹回来**，现象像"关不掉"；或静默的 `Available` 覆盖掉手动检查正在显示的结果，用户看到自相矛盾的两次结论。
  - 修法：新增 `manualCheckEpoch`，**手动检查开始时自增**；静默检查开始时记下快照，完成时若 epoch 已变则**整个静默结果作废**（连「今天已查」的日期也不记）。另加一道 `is Idle` 双保险。
  - 为什么作废时也不记日期（看起来反直觉）：被作废必然意味着用户手动查过 —— 手动**成功**时手动路径自己已经记过日期（不会白查）；手动**失败**时才不记，而那正是「下次启动应当重查」的情形（那个已发现新版的静默结果被丢弃了，必须补一次）。所以这个取舍零回归。
  - **关键认知：只判断「当前是否空闲」挡不住这个缺陷** —— 用户点「稍后」关掉弹窗后状态正好回到 `Idle`，晚到的静默结果照样判定通过、弹窗复活。**必须靠序号（epoch）**，`is Idle` 只是防御性补充。
- 验证：`:app:testDebugUnitTest --rerun` **60 个用例全绿**（`failures=0 errors=0`），含新增 `UpdateThrottleTest` 8 条与 `UpdateThrottleBoundaryTest` 9 条边界用例。QA 两轮独立验证结论：`silentCheckRunning` 三条路径（正常/异常/取消）均复位、无用户可见泄漏；`runCatching` 会连带吞掉 `CancellationException`，但当前无用户可见后果（归类代码债，刻意不改为避免手动路径停在 `Checking`）；跨 00:00 完成才多查一次（窗口 <23 秒）与长期离线每次启动重试均判可接受。
- **更新源线上实测（2026-09-23）**：Gitee 镜像 `latest.json` **1.2s**、GitHub Release API **7.1s**（贴近代码里 `CONNECT_TIMEOUT_MS = 8_000`，属危险边缘）。扫描线上 v1.3.4 APK 的三个 dex，确认镜像地址与 GitHub 地址**都已编译进包**，国内用户走镜像快链路。

### 二、成果概览小组件改用固定摘要填充

- **改为由 Provider 一次性填充固定三行摘要（`MAX_SUMMARY_ROWS = 3`），不再依赖 `RemoteViewsService`**，以规避不同桌面（尤其 ColorOS）对集合视图的兼容差异。这是对 v1.3.4「可滑动列表」路线的回退 —— 取舍是**兼容性优先于滚动**。
- 具体改动：**删除** `widget/AchievementListService.kt` 与 `res/layout/widget_achievement_item.xml`；`widget_achievement_list.xml` 重写为「头部品牌行 + 学年 `‹ ›` 箭头 + 三行固定摘要 + 空态」；`AndroidManifest.xml` 移除 `BIND_REMOTEVIEWS_SERVICE` 的 service 注册、receiver 的 `exported` 改为 `true`；`widget_preview_achievement.xml` 与 `res/xml/jicun_achievement_widget_info.xml` 同步更新。
- 学年切换仍走「箭头广播回 Provider 就地重渲染」，不出桌面；`WidgetYearStore` 的 `commit()` 同步写盘保持不变。
- 验证：编译通过（包含在本次 60 用例测试的编译链路内）；**真机上的渲染、拉伸与切学年行为待装包确认**。

## v1.3.4 更新（历史）

- **成果概览小组件从 Glance 迁移到传统 RemoteViews：主体改为可滑动的成果列表**（本次改动的直接动机是用户三个诉求：① 组件内切学年不要再"跳进 App"；② 修复"选完学年组件不刷新"；③ 想把成果拉下来、显示不全就滑动。其中 ③ 是硬约束——**Glance 不支持滚动列表**，要把 ListView 映射进桌面小组件只有传统 RemoteViews 的集合视图一条路，故成果组件整体迁移；快速录入组件 `JicunWidget` 是纯入口无列表，留在 Glance 不动，仓库内自此两套小组件技术并存）。
  - **新增 `widget/AchievementListWidget.kt`**（`AppWidgetProvider`）：头部 `‹ 学年 ›` 箭头点击发广播回自身（`FLAG_IMMUTABLE`，targetSdk 35 强制），`WidgetYearStore.shift` 落盘后就地重渲染——全程不出桌面，`YearPickerActivity` 透明壳时代结束；到头方向的箭头置灰提示（仍可点、原地不动）。`setRemoteAdapter` 的 Intent 带 `System.nanoTime()` 唯一 Uri——**Intent 相等时系统复用旧 Adapter，`onDataSetChanged` 不跑、列表不刷新，这是 RemoteViews 经典坑**；三参重载（`appWidgetId` 版）API 31+ 才有，低版本走旧签名。列表挂 `setEmptyView` 空态、`setPendingIntentTemplate` + 条目 fill-in Intent 点开成果 tab。提供 `pushUpdate()` 供 `AppViewModel.refreshWidget()` 调用（切 `Dispatchers.IO`，内部查库）。
  - **新增 `widget/AchievementListService.kt`**（`RemoteViewsService`）：`onDataSetChanged` 查全量记录 → `AcademicYear.yearsOf` 算学年 → 按 `WidgetYearStore` 选中学年过滤、获奖日期倒序；条目为五育色点 + 名称 + "五育 · 日期" 副信息。**小组件由桌面（launcher）进程渲染，拿不到 Compose 主题，五育色值只能显式写死**（与 Theme.kt 浅色版一致）。`onDataSetChanged`/`getViewAt` 在主线程回调里 `runBlocking` 查库：个人记录几十条以内（<10ms），不值得异步桥。Manifest 注册 `BIND_REMOTEVIEWS_SERVICE` 签名权限——只有系统能绑定，第三方 App 拉不到用户数据。
  - **新增 `widget/WidgetYearStore.kt`**：从旧 Glance 版的 `internal object AchievementYearStore` 迁为顶层 public（Provider 与 Service 两个使用方）。**关键修复：写盘 `apply()`（异步）→ `commit()`（同步）**——旧版选完学年立刻刷新组件时，另一处可能还读到磁盘旧值，这正是"选完学年组件没变化"的根因；SharedPreferences 名与 key 原样保留，老用户已选学年不丢。存的学年因删记录而不存在时回落当前学年，避免永远停在空学年。
  - **删除 3 个文件**：`JicunAchievementWidget.kt`（Glance 版）、`JicunAchievementWidgetReceiver.kt`、`YearPickerActivity.kt`。Manifest 的 receiver 换成 `AchievementListWidget`。
  - 布局三件套：`widget_achievement_list.xml`（头部品牌行 + ListView + 空态）、`widget_achievement_item.xml`（色点 + 名称 + 副信息）、`drawable/widget_achievement_bg.xml`（白色圆角卡）。`jicun_achievement_widget_info.xml` 的 `initialLayout` 直接指向真实列表布局；`previewLayout`（API 31+ 启动器）与 `previewImage` 位图（ColorOS「原子组件」选择器只认位图，见 v1.3.3）同步改为列表版示意（由 `tmp/gen_widget_previews.py` 重新生成）。
  - 列表数据变化仍走 `updatePeriodMillis=0` + App 保存/删除记录后主动推送，不轮询。验证：`testDebugUnitTest --rerun` 43 用例全绿 + `assembleDebug` 通过；RemoteViews 组件无独立可单测逻辑，真机行为（滑动、切学年、拉伸）待装包确认。

## v1.3.3 更新（历史）

> v1.3.0–v1.3.2（设计系统重构、「成果」板块、小组件箭头切换与 previewLayout）由并行会话交付，详见 git log `e7ec6ba..3d82a23`。

- **成果概览小组件四项修复**（`554fde9`）：
  - 布局重排：头部置顶 + 纵向 `defaultWeight()` 弹性空隙均匀分布，组件拉大后内容不再堆在左上角。defaultWeight 只用于 Column 纵向（v1.2.1 的 Row 权重坑不复发）。
  - 学年成为视觉焦点：11sp 灰字 → 22sp 加粗主色，右侧加 "▾" 提示可点；条成果数字 26sp → 18sp 让位。
  - 交互改造：删除左右箭头切换（`SwitchYearAction` / `YearArrow` / `DIRECTION` / `shift` 全部移除），改为点击学年弹透明壳 `YearPickerActivity` 单选对话框——选完写回存储、`updateAll()` 刷新组件即关闭，不进 App；学年不足 2 个时提示「无需切换」。Manifest 注册 `exported=false` + `excludeFromRecents` + `Theme.Translucent.NoTitleBar`。
  - 预览修复根因：**ColorOS「原子组件」选择器不渲染 `previewLayout`，直接回落成应用图标**。补两张 540×330 `previewImage` 位图（`drawable-nodpi/`）兜底，previewLayout 同步新设计共存；位图由 `tmp/gen_widget_previews.py`（Pillow + msyh.ttc）生成——微软雅黑缺 U+25BE 字形，"▾" 需画多边形而非字符。
- **架构评审 P1 修复**（`84dd7c7`，评审结论见 `docs/` 与 2026-09-22 会话记录）：
  - `allowBackup=false`：证书照片属个人隐私材料，不再参与系统云备份（推翻 v1.2.1 时「local-first 唯一兜底」的旧决策）。
  - `AwardDao.insertRecord` 弃用 `REPLACE`：REPLACE 是先删后插，主键撞上会经 `ON DELETE CASCADE` 静默清空该记录全部照片；回归默认 ABORT。
  - Room 导出 schema 基线到 `app/schemas/`（`exportSchema=true` + ksp arg），为将来写迁移做准备（此前 version=1 无基线，首改字段即启动崩溃）。
  - `checkBeforeExport` 移入协程、磁盘 stat 切 IO 线程（此前主线程逐张 stat 卡帧，是全仓唯一真实主线程磁盘 IO）。
  - `ExportState.Confirm` 直接携带导出范围，删除裸字段 `pendingExport`；Entry/List/Export 三屏不再自建 `PhotoStore`（统一走 `vm.photoFile`）；导出学年列表与范围计数与打包同源（`AcademicYear.yearsOf` / `ExportCheck.targetItems`）。
  - `file_paths.xml` 移除无引用的 photos files-path，收窄 FileProvider 共享面。

## v1.2.1 更新（历史）

- **修掉桌面小组件只显示「拍照」、看不到「相册」**：`JicunWidget.EntryTile` 内部把调用方传进来的 `defaultWeight()`（= `layout_weight = 1`）又叠了一个 `fillMaxSize()`，而后者会把宽度设成 `MATCH_PARENT`；同一个子项上"权重"和"宽度撑满"打架，LinearLayout 会让第一个子项独占整行、第二个被挤成 0 宽 —— 桌面上就只剩「拍照」一个。改为 `fillMaxHeight()`（宽度交给 weight 分配，这里只管高度）。**这是 Glance/RemoteViews 下的经典陷阱：权重必须与"高度撑满"配对，不能与"宽度撑满"配对。**（两个入口的接线本身没问题，`MainActivity` → `CaptureScreen` 的 `CAPTURE` / `PICK_PHOTOS` 分派一直是通的。）

- **实现桌面小组件本体**（此前只有入口协议，见「已知未完成」）：新增 `widget/JicunWidget.kt`（Glance 界面）+ `widget/JicunWidgetReceiver.kt` + `res/xml/jicun_widget_info.xml`，在 `AndroidManifest.xml` 注册 `exported="false"` 的 receiver。组件是一张 3×2 的白色卡片：标题行（logo + 「暨存」）点开正常启动 App，「拍照」直接进系统相机、「相册」直接进图片选择器。点击只发带 action 的显式 Intent（`WIDGET_CAPTURE` / `WIDGET_PICK_PHOTOS`），拍照与相册逻辑仍由 `MainActivity` + `CaptureScreen` 处理，组件不碰数据库、不保存照片——与 ADR-0001 的约定一致。
  - 两处必须注意的实现细节：**入口 Intent 必须带 `FLAG_ACTIVITY_NEW_TASK`**（否则从桌面点开会另起一个 App 实例，而不是走 `singleTop` 的 `onNewIntent`）；**两个 action 必须是不同的字符串**（`PendingIntent` 的相等性只看 action/组件/requestCode，不看 extras，action 相同会互相覆盖，两个按钮就都变成同一个入口）。
  - 组件里**不要直接引用 `drawable/logo.png`**（1254×1254 / 1.1MB）：桌面渲染由 launcher 进程解码，会白吃几 MB 内存。新增 `drawable-xxhdpi/widget_logo.png`（72px / 7.5KB）专供组件标题行。两枚按钮图标是自带填色的矢量图（`ic_widget_camera.xml` 白、`ic_widget_gallery.xml` 主色）。
  - **release 包里 `res/xml/jicun_widget_info.xml` 会被改名**：`optimizeReleaseResources` 把资源路径缩短成 `res/4R.xml`（debug 包仍是原名）。manifest 的 meta-data 用的是资源 id `0x7f0f0001`，不受影响；但去 release 包里找这个文件名会扑空——用 `aapt2 dump resources | grep jicun_widget_info` 按 id 反查。
  - 新增依赖 `androidx.glance:glance-appwidget:1.1.0`。**体积代价实测约 +1.5MB**：未混淆的 release 包 11.41MB → 12.87MB（+1.46MB / +13%，对比线上 `jicun-1.1.0.apk`）；debug 包同向 +1.7MB 左右（约 19.1MB → 20.78MB，旧值为按同一压缩率推算）。两种构建的结论一致，代价可接受，故沿用 Glance 而不是手写 RemoteViews。
  - ⚠️ **别拿 CI artifact 的 `size_in_bytes` 当 APK 体积**：artifact 是 zip，本地实测整包压缩率约 15%（20.78MB 的 APK → 17.63MB 的 zip，与 CI 的 17.65MB 吻合）。本次就踩了：用「本地 APK 20.78MB」对比「旧 artifact 16.19MB」，得出「debug 涨了 5.6MB」的错误结论，差点据此放弃 Glance。**比体积必须同口径**（APK 对 APK，或 zip 对 zip）。
  - 验证方式：`:app:testDebugUnitTest --rerun` **24 个用例全绿**（6+3+3+12）；`assembleDebug` / `assembleRelease` 均 `BUILD SUCCESSFUL`；用 `aapt2 dump xmltree` 反查 APK，确认 `JicunWidgetReceiver`（`exported=false` + `APPWIDGET_UPDATE` + `android.appwidget.provider` meta-data）与 `res/xml/jicun_widget_info.xml`（180×110dp、3×2 格、`updatePeriodMillis=0`）都正确进包。推送后 **CI #10 / #11 均 success**（`Run unit tests` + `Assemble debug APK` + 上传产物）。**仍未做的是真机/模拟器上把组件真正拖到桌面看渲染效果**。

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
- 更新检查改为**双源取较新版本**：同时问镜像与 GitHub，取版本号更高的那个。此前是"镜像可达即权威"，镜像一旦忘了同步，用户会被**永久卡在旧版本**、再也收不到更新；现在镜像退化成"可选加速"，两边只要有一边通就能检查（两边都失败才报错，并把两边原因都带上）。同时：`apkUrl` 强制 **HTTPS**（targetSdk 35 禁明文，否则下载会被系统拦掉、报错还很难懂）；`openConnection` 的报错文案改用调用方标签（此前读**镜像**失败也报「GitHub 返回 HTTP xxx」，会把排查带偏）。新增 `UpdateCheckerTest` 12 个用例覆盖版本比较（含 `1.10 > 1.9` 这种字符串比较会判反的情况）、双源择优与 HTTPS 校验；本地 `:app:testDebugUnitTest` 共 **24 个用例全绿**。
- **国内镜像自动化（Gitee）已写进 `release.yml`**，三步：① 解析镜像分支、算出清单地址写进 `$GITHUB_ENV`（**任何问题只警告不失败**，镜像不该拦住正式发布）；② 把 APK 作为 Gitee Release 附件上传（先按 tag 复用已有 release、同名附件先删 → 可重复执行；multipart 字段名固定为 `file`）；③ 用 `git push` 更新镜像仓库的 `latest.json`，并**回读校验**确认匿名可读且版本号正确。构建时用 `-PupdateManifestUrl` 把清单地址编译进 APK（未配镜像时为空串 → 走 GitHub 回退）。
  - **镜像三步刻意排在 `Create GitHub Release` 之后**：镜像失败只表现为"GitHub 成功、镜像没同步"，看得见，且不会丢正式包。
  - 需要 1 个 Secret（`GITEE_TOKEN`）+ 2 个 Variables（`GITEE_OWNER` / `GITEE_REPO`），**缺任一项整段自动跳过**，下一次发版行为与 v1.1.0 完全一致。
  - `latest.json` 用 `git push` 更新而不是 contents API：语义明确，没有"新建还是更新、要不要 sha"的歧义，镜像仓库只有一个 JSON，克隆成本可忽略。
  - **已做的静态校验**：YAML 解析通过、9 个 `run` 脚本逐个过 `bash -n`、5 个 jq 表达式用样本数据实测（含空仓库 / release 不存在 / 同名附件三个边界）。**未在真实 Gitee 上跑过**——需要镜像仓库先有分支 + 令牌到位。
  - 为什么清单必须放 raw 固定路径：该 URL 会被**编译进 APK**，而 release 附件 URL 带 tag、每版都变，所以清单不能放附件。

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
- [x] **正式 Release 已发布（2026-09-21）**：`暨存 v1.1.0` —— https://github.com/l0x0hhh/zongce/releases/tag/v1.1.0 ，资产 `jicun-1.1.0.apk`（11.41 MB）。Release 流水线 `#2`（提交 `0be3016`）10 个步骤全绿。**这是本仓库第一个 Release**：落地页所有下载入口、以及 App 内「检查更新」的 GitHub Release 回退路径，此前都指向一个空列表，现在有真实目标了。线上包已下载回来复验：`apksigner verify` 报 `Verifies`，证书与本机 keystore 一致，包信息 `com.zongce.app` / versionCode 1000002 / versionName 1.1.0。
- [x] **正式 Release 已发布（2026-09-21）**：`暨存 v1.2.0` —— https://github.com/l0x0hhh/zongce/releases/tag/v1.2.0 ，资产 `jicun-1.2.0.apk`（12.88 MB）。Release 流水线 `#3`（提交 `dd78f45`）10 个步骤全绿。**versionCode `1000003` > 线上 v1.1.0 的 `1000002`，可直接覆盖升级**（本地构建的 `versionCode` 默认是 2，装不上去，别再拿本地包做真机测试）。线上包已下载回来复验：`apksigner verify` 报 `Verifies`，证书 SHA-256 `7a1eeb88…` 与本机 keystore 一致；`aapt2 dump badging` 得 `com.zongce.app` / versionCode 1000003 / versionName 1.2.0 / label 暨存；小组件 receiver 与组件规格（180×110dp、3×2 格、`updatePeriodMillis=0`）都在包里。相比 v1.1.0 的 11.41 MB，体积 +1.47 MB（即 Glance 的代价）。
  - ⚠️ **该 tag 指向 `dd78f45`，正好是 `2513123`（Gitee 镜像自动化）的父提交** —— 两者只差 5 分钟（打 tag 15:24，那次提交 15:29）。所以**这个已发布 APK 的 `UPDATE_MANIFEST_URL` 是空串：应用内更新仍然只走 GitHub Release 回退**，`release.yml` 里的三步 Gitee 同步也不在它里面（Release #3 的步骤表只有 10 步可证）。这不是缺陷（v1.1.0 同样如此），但**镜像要真正生效，得等 Gitee 仓库有分支且 `GITEE_TOKEN` / `GITEE_OWNER` / `GITEE_REPO` 配好之后重新发一版**（重指 tag 重跑，或出 v1.2.1）。配好之前重发没有意义——清单地址仍是空串，产物一模一样。
- [x] **JVM 单元测试（v1.3.5 批次）：60 个用例全部通过**（`failures=0 errors=0`），含更新检查节流 17 条（`UpdateThrottleTest` 8 + `UpdateThrottleBoundaryTest` 9）。命令：`GRADLE_USER_HOME='E:\AIstudy\project\Jicun\zongce-android\.gradle-user' ./gradlew :app:testDebugUnitTest --rerun --console=plain --no-daemon -Dorg.gradle.jvmargs="-Xmx1536m -Dfile.encoding=UTF-8" --max-workers=1 -Pkotlin.compiler.execution.strategy=in-process`；结果看 `app/build/test-results/testDebugUnitTest/TEST-*.xml`。
- [x] **编译 / 资源 / Manifest 处理通过**：`assembleDebug` 与 `assembleRelease` 的 `compileDebugKotlin`、`processDebugMainManifest`、`processDebugResources` 均为 executed 且成功（非 UP-TO-DATE），确认小组件返工删除 `AchievementListService` 与 `widget_achievement_item.xml` 后无悬空引用。另做全仓 grep 复核：`AchievementListService` / `widget_achievement_item` / `REMOTEVIEWS_SERVICE` 仅剩两处说明性注释，无任何代码或资源引用。
- [x] **JVM 单元测试（v1.3.6 批次）：60 个用例全部通过**（`AcademicYearTest` 8 + `FileNameRuleTest` 3 + `ExportCheckTest` 3 + `ExportPlanTest` 17 + `UpdateCheckerTest` 12 + `UpdateThrottleBoundaryTest` 9 + `UpdateThrottleTest` 8，`failures=0 errors=0 skipped=0`），命令同上，结果 `BUILD SUCCESSFUL in 2m 29s`、`26 actionable tasks: 7 executed, 19 up-to-date`（`7 executed` 说明是真跑，非 UP-TO-DATE 假绿）。注：这 60 条覆盖的是纯逻辑，**跨进程同步不在覆盖范围内，需真机验证**。
- [ ] **本地整包打包未跑通（环境问题，非代码问题）**：`mergeDebugGlobalSynthetics` 与 `mergeExtDexRelease` 两次分别失败，错误同为 `Could not move temporary workspace … .gradle-user/caches/transforms-4/<hash>-<uuid>` —— Windows 文件系统层的原子重命名被卡住，`--max-workers=1` 也规避不掉（同一根因在 v1.3.4 会话中已出现过一次）。已清理缓存里残留的临时目录后重跑。**dex 合并与 release 变体的最终打包以 CI（Linux）为准。**
- [ ] 真机或模拟器功能验证（含成果小组件返工后的固定摘要渲染、拉伸、切学年行为）。

## 最近一次提交

`daaa14b` `feat: 成果小组件迁移到 RemoteViews，列表可滑动、箭头就地切学年、修刷新 bug (v1.3.4)` —— 已推送至 `origin/main`，对应线上 tag `v1.3.4`（CI 发布于 2026-09-22）。

> v1.3.5 的提交把**两批独立改动并入一次提交**（启动自动检查更新 + 成果小组件返工），提交信息里分别说明；版本记录卡本文件也在同一提交中更新。之所以合并而不是拆两个提交：两批改动各自都能独立编译，但同属一个版本、共用一个 tag，拆开只会让发版流程多一次推送与 CI 触发。

### 已知待处理

- 两处既有的编译告警（非本批引入，`compileDebugKotlin` 输出）：`ExportScreen.kt` 的 `LinearProgressIndicator(progress = …)` 已废弃（应改用接收 lambda 的重载）；`GlassNavigationBar.kt` 的 `Icons.Filled.List` 已废弃（应改用 `Icons.AutoMirrored.Filled.List`）。
- `ExportScreen` 顶部的「$targetYear · $selectedCount 条记录」仍用 `belongsTo` 计数，与 `ExportCheck.targetItems()` 的判定在「日期缺失或非法」时不一致。这类记录会被体检阻断，所以目前只影响显示，待统一。
- `gradle.properties` 的 `android.overridePathCheck=true` 是当年中文路径时期的产物，AGP 每次构建都会警告它「experimental」。路径已是纯 ASCII，这项可以删掉。
- 界面新增的提醒确认页还没上真机/模拟器过一遍（CI 只做到构建与单测）。
- **Release 已跑通（2026-09-21）**，两次失败的根因都已定位并修掉：第一次＝4 个签名 Secret 未配好（keystore 口令遗失，已重新生成）；第二次＝`signingStoreFile` 相对路径被按 `app/` 解析（已改用 `rootProject.file()`）。签名口令**不入库、不进记忆**，由刘总存在自己的密码管理器；丢了就再生成一把（发新版本时换 key 会让老安装升级失败，届时需先卸载——目前只有 1.1.0 一个版本，尚无此负担）。
- GitHub Release 的发行说明目前只有一行 `Full Changelog` 链接（`generate_release_notes` 在没有 PR 的仓库里生成不出内容）。想给 1.1.0 写正式说明的话，去 Release 页面点编辑补上即可。
- **release.yml 没传 `-PupdateManifestUrl`** → 正式包 `UPDATE_MANIFEST_URL` 为空 → App 内更新永远走 GitHub Release 回退，镜像清单形同虚设。要让国内用户走镜像需在 release.yml 里注入。

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
