# ADR-0003: 「我的成果」小组件——只读 Room、主动推送刷新、学年单一事实源

> 2026-10-09 修订：组件使用实例独立学年、组件内直接选择并立即请求刷新；进入 App 时携带学年。
> 本文的箭头与共享学年偏好条款由 [最新交互方案](../plans/2026-10-09-achievement-widget-year-picker.md) 替代。

## Status

> **Accepted（生效中）**，对应 PRD：`docs/prd/prd-成果小组件-v1.5.0-2026-09-25.md`（v1.1）；实现方案：`docs/design/widget-achievement-design-v1.5.0-2026-09-25.md`。
>
> **⚠️ 状态标注（依赖 T01 真机 spike 结论）**：本 ADR 的列表形态取决于 T01 的实测结果——「能否滚动」由 **launcher（AppWidgetHost）是否放行垂直手势**决定，App 侧无法强制。
> - **T01 通过** → 维持 D6 的 `LazyColumn` 方案（`USE_LAZY_COLUMN = true`，默认）。
> - **T01 不通过** → 降级为**静态 `Column` + 按 `LocalSize.current.height` 计算行数 + 底部固定一行「共 X 条 · 查看全部」**；需求「能看更多」本身不降级，改由「查看全部」这一跳进 App 兜住。
> - **降级结论以文末「T01 真机 spike 结论回填表」的『结论』列为准**，不在正文另写附注（避免正文与表格各自演化）；分支选择（`USE_LAZY_COLUMN` 保持 `true` / 置 `false`）也在该表中勾选。
> - 该分支**不影响** D1–D5、D7 的任何决策。
>
> **编号说明**：本 ADR 的 **D1–D7 自成一套**，与 team-lead 决策清单里的 D1–D6 **不是同一套编号**，交叉引用时以本文件的 D 编号为准。刷新挂点 **W1–W6** 与架构设计文档统一（W6 = App 冷启兜底，该文档把它单列在 W1–W5 之外，本 ADR 一并纳入编号）。

## Context

「暨存」的核心定位是「方便填系统时不用乱找照片」——证明材料（获奖记录）按五育分类存放、按学年导出。学年是学校综测的唯一时间口径，所以用户来这个 App 的问句永远是「我这一学年攒下了什么」。

桌面侧的历史沿革决定了本次必须重新决策，而不是把旧东西搬回来：

| 版本 | 事实 | 出处 |
| --- | --- | --- |
| v1.3.4 | 成果组件从 Glance 迁到传统 RemoteViews，做了可滑动列表（`AchievementListService` + `RemoteViewsService`） | `VERSION_HISTORY.md` v1.3.4 |
| v1.3.5 | **回退**成「Provider 一次性填充固定三行摘要（`MAX_SUMMARY_ROWS = 3`）」，理由：规避不同桌面（尤其 ColorOS）对集合视图的兼容差异，兼容性优先于滚动 | `VERSION_HISTORY.md` v1.3.5 |
| v1.3.6 | 修「App 内选完学年、回桌面组件不刷新」。根因不是渲染层，是**状态源分裂**：App 内 `selectedYear` 是内存状态，组件读 SharedPreferences，两套各写各的 | `VERSION_HISTORY.md` v1.3.6 |
| v1.4.0 | 「成果概览」组件**整体移除**（3×2 固定三行摘要形态）。`docs/adr/0002-widget-reads-summary.md` 标 **Deprecated**；学年偏好降级为纯 UI 偏好 `data/AchievementYearStore.kt`（SP 名 `jicun_achievement_widget` / 键 `selected_year` 一字未改） | `docs/prd/prd-删除与组件精简-v1.4.0-2026-09-23.md` |

v1.5.0 用户重新提出要「我的成果」小组件，并要求**组件内切学年、实时更新、滑动查看更多、选择器里看到真实样子**。这带来五个必须重新定的架构问题：

1. 组件要读数据，但 ADR-0001 定的是「小组件只发 Intent、不碰数据库」——**边界要不要扩、扩到哪**；
2. 数据变了桌面要跟着变，但 `updatePeriodMillis` 最快 30 分钟一次，**做不了实时**；
3. 学年现在有**两个写入源**（App 内成果页 chip、桌面箭头），必须保证不分裂、不并发错序；
4. 用户要在系统选择器里看到「实际的样子」，而静态预览资源在**渲染时 App 未必跑过**；
5. 「滑动」这条 v1.3.5 已经因为 ColorOS 回退过一次，**不能凭文档拍板**。

## Decision

新增**独立的第二个组件**「我的成果」（`AchievementWidget` / `AchievementWidgetReceiver`），与「快速录入」（`JicunWidget`）并列，各自注册 receiver 与 `*_widget_info.xml`。以下 D1–D7 为本次的架构决策。

### D1. 组件只读 Room；唯一的写是学年偏好（SP）

- 组件**只读** `award_records` / `award_photos`（经 `AwardDao.allWithPhotos().first()`），**绝不写记录、绝不碰照片文件**（`PhotoStore`）。
- 组件唯一的写操作是学年偏好 `AchievementYearStore.set()`（SharedPreferences）。
- **组件内不做删除 / 编辑 / 拍照 / 选图**：删除不可逆，桌面没有可靠的二次确认能力；录入仍然只有 `MainActivity` 一套入口。
- `JicunWidget`（快速录入）**代码、布局、行为一字不改**，继续遵守 ADR-0001「只发 Intent、不碰数据库」。
- **ADR-0001 对本组件不适用的部分**由本 ADR 显式覆盖：ADR-0001 的前提是「组件只有入口、没有数据要展示」，本组件有数据要展示，故必须读库；但**「录入流程只有一套、统一由 `MainActivity` 承接」这条继续有效**。

### D2. 刷新只走主动推送，`updatePeriodMillis = 0`，不轮询

- `updatePeriodMillis` 保持 **0**。平台轮询**最快 30 分钟一次**且每次唤醒进程——既做不到"实时"，开销又远大于一次推送。
- 全部写路径必须挂刷新，**漏挂 = 桌面停在旧值且不报错**（ADR-0002 称之为"隐性约束"，本次必须消灭它）：

| # | 写路径 | 位置 | 挂在何处 |
| --- | --- | --- | --- |
| W1 | 保存 / 编辑记录（含编辑时移除照片） | `RecordViewModel.saveRecord()` | DB 与文件处理完之后、`onSaved` 回调之前 |
| W2 | 单条删除 | `RecordViewModel.deleteRecord()` | `reportDeletion()` 之后、`finishDeletion()` **之前** |
| W3 | 多选删除 | `RecordViewModel.deleteSelected()` | `exitSelectionMode()` 之后、`finishDeletion()` **之前** |
| W4 | 整学年删除（导出后确认） | `RecordViewModel.deleteYear()` | `reportDeletion()` 之后、`finally` 之前 |
| W5 | **App 内切学年**（v1.3.6 遗留链路补齐） | `RecordViewModel.init{}` 的**单消费者协程** | `AchievementYearStore.set()` 那一行改为 `AchievementYearWriter.writeAndRefresh()`；**严禁裸 `launch(Dispatchers.IO)`** |
| W6 | App 冷启兜底 | `MainActivity.onCreate` | `lifecycleScope.launch(Dispatchers.IO) { WidgetRefresh.refresh(applicationContext) }` —— **它存在的唯一理由**：Glance 的 `MyPackageReplacedReceiver` 收到 `ACTION_MY_PACKAGE_REPLACED` 后**只做 `cleanReceivers()`、不刷新任何组件**（源码实证；`GlanceAppWidgetReceiver` 也未 override 该事件），因此 **App 升级后组件不会重查库**，这个空窗只能靠冷启兜底补上。**不要因为"升级不是写路径"就删掉 W6** |

- **单一入口 `widget/WidgetRefresh.kt`**：全仓只有它允许出现 `GlanceAppWidgetManager` / `updateAll`，其它任何文件 grep 到即视为缺陷。它内部**先查 `getGlanceIds()`，为空直接 return**（没添加组件就不做无谓查库），并**全程 `runCatching` 不抛异常**——刷新是**旁路**，W1–W6 的调用点全在业务主干上（尤其删除路径），刷新一旦抛出就会污染删除结果提示，制造"删除失败"的假象。刷新失败最坏只是桌面暂时旧，必须降级而非传播。
- 组件自身生命周期（**添加组件 / 开机或启动器重启 / 桌面进程重启**）回调 `onUpdate` → `provideGlance` **当场重新查库**，这是"冷启动也对"的根本保障。
- ⚠️ **`ACTION_MY_PACKAGE_REPLACED`（App 升级）不在上面这个列表里**：Glance 的 `MyPackageReplacedReceiver` 收到该广播后**只调 `GlanceAppWidgetManager.cleanReceivers()`，不做任何业务刷新**（源码实证），`GlanceAppWidgetReceiver` 也没有 override 该事件。**所以 App 升级后桌面组件不会重查库**——这个空窗是 W6 存在的唯一理由，别把 W6 当冗余删掉。

### D3. 学年单一事实源 = `AchievementYearStore`；写侧统一经 `AchievementYearWriter`

- 组件、成果页、`AcademicYear` **三处读写一律经 `AchievementYearStore`**（SP 名 `jicun_achievement_widget` / 键 `selected_year` / 同步 `commit()`）。**任何地方不得新建第二份学年存储，不得直接 `getSharedPreferences` 操作该文件。**
- 学年候选一律 `AcademicYear.yearsOf(全部记录的 awardDate)`（恒含当前目标学年 `LABEL`），读取一律 `AchievementYearStore.current(context, years)`——存着的学年若因删记录而不在候选里，它自动回落到 `LABEL`，组件永远不会停在空学年。
- **写侧统一经 `data/AchievementYearWriter.kt`**（单消费者 Channel + `CompletableDeferred`，**进程级串行**），供 ViewModel 队列（W5）与桌面箭头（D4）共用。为什么必须引入它，即使 VM 里已经有一个队列：
  1. VM 的队列只能串行化**它自己**的点击；
  2. 桌面箭头是**另一个独立写入源**：每次点击各起一个 `goAsync` 协程跑在 `Dispatchers.Default` **多线程池**上，**连点 3 次的三次写盘没有顺序保证** → 最终落盘的未必是最后点的学年（v1.3.6 修过的那类并发坑，只是触发源从 chip 换成了箭头）；
  3. 桌面箭头只传 **delta（-1 / +1）**，学年列表在 writer / Loader 侧**重新读库**决定——组件里不缓存学年列表，避免用一份过期列表算出错误的目标学年。
- **不用 `PreferencesGlanceStateDefinition`**：① Glance state 是**每个 `appWidgetId` 一份 DataStore**（`createUniqueRemoteUiName(appWidgetId)`），会分裂成"桌面一份、成果页一份"，正是 v1.3.6 那个 bug；② **`providePreview` 读不到 per-instance 状态**——看它的签名就知道，参数里**没有 `GlanceId`**：
  ```kotlin
  open suspend fun providePreview(context: Context, widgetCategory: Int)
  //                               ↑ 只有 Context 与 category，没有 GlanceId → 拿不到任一实例的 Glance state
  ```
  用它就做不了 D5 的 L3 真实预览。只有**全局 SP** 同时满足"单一事实源"和"预览里取得到真实数据"。

### D4. 桌面 `‹ 学年 ›` 用 `actionRunCallback<SwitchYearAction>`，不是 `actionSendBroadcast`

- `ActionCallback.onAction` 是 suspend，跑在 `Dispatchers.Default` 后台线程池，可直接 await Room suspend DAO；受广播 `PendingResult` 生命周期约束（**工程惯例约 10 秒，非源码承诺**——源码只能证明 `PendingResult.finish()` 之后进程可能被杀），本次只是"读写 SP + 一次轻量查询"，远在预算内。
- ⚠️ **`ActionCallback` 执行完 Glance 不会自动刷新组件**（源码实证：`ActionCallbackBroadcastReceiver.onReceive` 里没有 update 调用）。所以 `SwitchYearAction` 必须自己调 `AchievementYearWriter.writeAndRefresh()`，不能指望系统补一次。

### D5. 预览三档并存（L1 位图 / L2 静态 XML / L3 真实数据）

| 档 | 手段 | 生效范围 | 用户看到 |
| --- | --- | --- | --- |
| **L1 保底** | `android:previewImage="@drawable/widget_preview_achievement_img"`（`drawable-nodpi`，`tmp/gen_widget_previews.py` 生成，750×750 px = 250×250dp@3x） | **全版本** | 静态版式（示例数据） |
| **L2 增强** | `android:previewLayout="@layout/widget_preview_achievement"` | API 31+（优先级高于 previewImage） | 静态版式（按 launcher 尺寸缩放） |
| **L3 真实** | `providePreview()` + `GlanceAppWidgetManager.setWidgetPreviews()` | **Android 15+（API 35）** | **真实学年 + 真实条数 + 真实前几条成果** |

- **L1 不可省**：本项目已验证的事实——**ColorOS「原子组件」选择器不渲染 `previewLayout`，会回落到应用图标**（`res/xml/jicun_widget_info.xml` 注释已记录）。L1 是该场景**唯一可用路径**。
- **L2 必须单独一份 XML，且里面绝不能放 `ListView`**：集合数据是运行时注入的，预览里会渲染成**一张空列表卡片**。必须用 **LinearLayout 假造若干行**（官方"Build accurate previews that include dynamic items"的要求）。
- **L3 的两个硬约束**：① **必须 override `previewSizeMode = SizeMode.Responsive`**——默认 `SizeMode.Single` 只按 `minWidth/minHeight` 渲染，**会把列表裁掉**（官方 troubleshooting 点名的坑）；② 平台**限流约每小时 2 次**，必须做 fingerprint 判重 + 最小间隔节流（约 31min），只在内容真变时才消耗配额。
- **诚实边界**：L1/L2 是静态资源，渲染发生在 launcher 进程、**此时 App 未必跑过**，所以这两档**不可能显示真实记录**；**L3 可以**。三档共通的最低保证是版式 / 配色 / 文案结构与真实组件 1:1。

### D6. 列表走 `LazyColumn`，`WIDGET_MAX_ROWS = 20`，且列表内**禁止放照片缩略图**

- 用 `androidx.glance.appwidget.lazy.LazyColumn`（**不是实验性 API**，常规重载无 `@ExperimentalGlanceApi`，无需 `@OptIn`），与 App 内同栈 Compose，省掉一整套手写 RemoteViews XML。
- **`WIDGET_MAX_ROWS = 20` 是硬上限，不是懒加载**：Glance 按 SDK 分叉——`SDK_INT > 31` 走 `RemoteViews.setRemoteAdapter(viewId, RemoteCollectionItems)`，**全部 item 的 RemoteViews 一次性打包进这次 update 的 Binder 事务**（`<= 31` 才走 `GlanceRemoteViewsService` Intent 路）。平台 javadoc 明确警示"总内存要小（不要 contain large or numerous Bitmaps）"。
- 因此**列表内绝不放照片缩略图**：位图是最容易把 Binder 事务打爆的东西，而组件的价值是"看见清单"，看图应进 App。
- 排序 `sortedByDescending { awardDate }`（与成果页 `ofYear` 一致）；过滤一律 `AcademicYear.inYear(...)`，**学年归属全仓只有一处实现**。

### D7. 升级官方三件套换取 L3；三个版本必须同时升

| 依赖 | 从 | 到 | 为什么 |
| --- | --- | --- | --- |
| `androidx.glance:glance-appwidget` | 1.1.0 | **1.2.0** | **L3 的 `providePreview` / `previewSizeMode` / `setWidgetPreviews()` 只有 1.2.0 有**；1.1.0 全仓 grep 零命中（源码实证） |
| `androidx.compose:compose-bom` | 2024.09.02 | **2025.02.00** | Glance 1.2.0 要求 compose runtime **1.7.8**，旧 BOM 只到 1.7.2 |
| Kotlin / KSP | 2.0.20 / 2.0.20-1.0.25 | **2.0.21 / 2.0.21-1.0.25** | Glance 1.2.0 拉 `kotlin-stdlib` 2.0.21，插件不对齐会被 Gradle 抬高 |

- **三个必须同时升，不能只升 Glance**：只升 Glance 会让 compose runtime 被**单独**抬到 1.7.8 而 `ui` / `material3` 留在 1.7.2，**Compose 家族版本错配会出运行时崩溃**。
- **绝不升到 `1.3.0-alpha`**：其 release note 明确"Updated Compose compileSdk to API 37. This means a minimum AGP version of 9.2.0 is required"，而本项目 **AGP 8.5.2 / compileSdk 35**——会直接把构建打挂。
- **不新增任何第三方库**：`glance-appwidget-preview` / `glance-material3` / `glance-appwidget-testing` 本就是 `glance-appwidget` 的传递 compile 依赖，无需显式声明。本次是**对齐官方依赖版本**，不是引入新库。
- **兜底（已写进 PRD P0-9）**：升级单独一个 commit 便于回滚；验收 = `testDebugUnitTest` 现有 97 用例全绿 + `assembleDebug` + `adb install` 真机过五个 tab（debug 包走 debug 签名，**验证不受阻**）。回归过不去的降级路径：先试 BOM 降到 `2024.12.01`（runtime 1.7.6）；仍失败则 **Glance 退回 1.1.0 + 预览只做 L1/L2**（放弃 generated preview）——这是**唯一允许降级的功能面**，须第一时间同步 team-lead 与 PM。

## Consequences

### Positive

- **学年只有一个事实源**：组件、成果页、预览、删除范围读的是同一份 `AchievementYearStore` + 同一份 `AcademicYear`，不会出现"桌面和 App 对不上"。v1.3.6 的状态分裂 bug 从结构上被排除。
- **刷新漏挂可被机械检查**：单一入口 `WidgetRefresh`，验收时"全仓 grep 除它之外不允许出现 `updateAll`" 就是一条可执行的检查项，不再依赖"以后记得调"。
- **刷新不会污染业务**：`runCatching` + 判空早退，让桌面的展示问题永远不会变成"删除失败"的假提示。
- **预览在 ColorOS 上不会退化成应用图标**：L1 位图兜住了本项目唯一实测过的失败路径；L3 在 Android 15+ 上兑现了用户"看到实际的样子"的原话。
- **组件边界仍然清晰**：只读 + 学年偏好，不承担删除与照片生命周期；录入仍然只有一套入口。
- **Binder 风险被显式量化**：`WIDGET_MAX_ROWS = 20` + 列表内无位图，把"列表一多就把组件打挂"变成一个可测的上限。

### Negative

- **新增一条隐性约束的变种**：以后**新增任何写路径都要记得挂 `WidgetRefresh.refresh()`**。缓解手段是单一入口 + grep 检查，但它仍然是一条要靠纪律维持的约定。
- **学年写入多了一层间接**：`AchievementYearWriter` 是"为了并发正确性"引入的，读代码的人要理解它为什么存在（已在 D3 写明，并需在代码注释里保留）。
- **`provideGlance` 的耗时进入首帧**：组件首次渲染要查库，几十到几百条记录量级 < 50ms，可接受；但它是"桌面渲染"路径上的一次 IO。
- **预览要维护三处，且靠人工保持一致**：Glance 组合（`providePreview` 与 `provideGlance` 共用装载逻辑）、`res/layout/widget_preview_achievement.xml`、`tmp/gen_widget_previews.py` 的位图分支。**这是刻意接受的债务**——三处各自服务不同的系统路径（API 35+ / API 31+ / 全版本含 ColorOS），无法合并；改配色或版式时必须三处同步，已在 PRD §4.3 与 P1-7 记为验收项。
- **依赖升级的 blast radius 不止组件**：Compose BOM 与 Kotlin 会同时影响 App 内**全部** Compose 页面（成果页多选删除、导出页是重点回归对象）。风险已由用户认领，但回归成本是真实的。
- **两个组件各有一套写死的色值**（RemoteViews 拿不到 App 主题），改配色要同步两处——ADR-0002 里已记录过这条，本次沿袭。
- **年检成本**：`WIDGET_MAX_ROWS`、预览节流间隔、`previewSizeMode` 都绑定在具体的 Glance / 平台行为上，Glance 大版本升级时需要重新核对。

### Neutral

- **ADR-0001 关于"录入入口统一由 `MainActivity` 承接"继续有效**；本 ADR 只新增了一个只读组件并覆盖了"不碰数据库"这条对**本组件**的适用性，原组件（`JicunWidget`）的约束一字未改。
- **Manifest 里会有两个独立 receiver**，各自指向自己的 `*_widget_info.xml`；桌面组件选择器里出现两个条目，用户可分别添加。
- **`WidgetActions.OPEN_ACHIEVEMENT` 终于有了发送方**：v1.4.0 移除组件后它成了无发送方的常量，按当时 PRD §7.3 刻意保留，本次复用它做"点组件进成果页"，无需新增路由。
- **ADR-0002 保持 Deprecated，仅作备查**（见下方"与 ADR-0002 的关系"）。

## 与 ADR-0002 的关系（重要：本次不是复活 ADR-0002）

| 项 | ADR-0002（已 Deprecated） | 本 ADR（v1.5.0） |
| --- | --- | --- |
| 形态 | 3×2 格（180×110dp），**固定三行摘要**，`MAX_SUMMARY_ROWS = 3`，滑不动 | **4×4 格（250×250dp）**，真列表（`LazyColumn`），组件内 `‹ 学年 ›` 切换 |
| 学年存储 | `widget/WidgetYearStore.kt` | `data/AchievementYearStore.kt` + 写侧 `AchievementYearWriter`（进程级串行） |
| 刷新 | `AppViewModel.refreshWidget()`，v1.4.0 已移除 | `WidgetRefresh.refresh()` 单一入口 + W1–W6 全覆盖 + 冷启兜底 |
| 预览 | `previewLayout` + `previewImage` 两档 | **三档**，新增 L3 `providePreview`（API 35+ 真实数据） |
| 依赖 | Glance 1.1.0 | Glance **1.2.0**（+ BOM 2025.02.00 / Kotlin 2.0.21） |

- v1.4.0 移除的旧「成果概览」组件及其全部文件（`widget/AchievementListWidget.kt`、`widget/AchievementListService.kt`、`res/xml/jicun_achievement_widget_info.xml`、`res/layout/widget_achievement_list.xml`、`widget_preview_achievement.*`、`widget_achievement_bg.xml`、Manifest receiver、两条 string）**一份都不恢复**；v1.4.0 的移除决策见 `docs/prd/prd-删除与组件精简-v1.4.0-2026-09-23.md`，仍然成立（被砍的是"3×2 固定三行摘要"那个形态）。
- ⚠️ **注意（避免实现者误读上面这句清单）**：`res/xml/jicun_achievement_widget_info.xml` 与 `widget_preview_achievement.*` 这两个**路径名与 v1.4.0 删除的旧文件同名**，但本次是**按 PRD P0-1 / P0-7 新建全新内容**（4×4 规格、三档预览、新组件类），**不是恢复旧文件**。清单说的是"不把旧内容搬回来"，不是说"这两个路径不能建"。
- ADR-0002 正文保留备查，**不删除、不复活、不再指导开发**。

## Alternatives Considered

**让组件自己写库 / 在组件里做删除**：拒绝。删除不可逆，而桌面没有可靠的二次确认能力（弹不了对话框）。删除只能走 `RecordDeletion` 的唯一管线，那是 App 内的能力。

**用 `updatePeriodMillis > 0` 定时轮询**：拒绝。平台最快 30 分钟一次，且每次唤醒进程；既做不到用户要的"实时"，开销又远大于一次推送，还会带来最长半小时的数据延迟。（ADR-0002 已同一理由拒绝过，本次结论不变。）

**组件自己维护一份学年（或每 `appWidgetId` 一份 `PreferencesGlanceStateDefinition`）**：拒绝。① 每实例一份 DataStore 会分裂成"桌面一份、成果页一份"，正是 v1.3.6 那个 bug；② `providePreview` 读不到 per-instance 状态，等于主动放弃 L3 真实预览。

**桌面箭头直接调 `AchievementYearStore.set()`（不经 `AchievementYearWriter`）**：拒绝。`ActionCallback` 跑在 `goAsync` / `Dispatchers.Default` 多线程池，连点 3 次箭头的写盘顺序没有保证 —— 最终落盘的未必是最后点的学年。必须由进程级串行写入器收敛。

**用 `actionSendBroadcast` + 自定义 Receiver 代替 `actionRunCallback`**：拒绝。`ActionCallback` 的 `onAction` 是 suspend，可以直接 await Room DAO，代码路径短且不需要额外注册 receiver；走自定义广播还得自己管 `goAsync` 与线程。

**只做 L1/L2 静态预览，不升 Glance**：拒绝（已由 team-lead 与用户拍板）。用户原话是"选择组件预览的时候要看到实际的样子"，而 L1/L2 在系统层面**不可能**显示真实记录（渲染发生在 launcher 进程、App 未必跑过）。只有 L3 能兑现这句话。

**升 Glance 1.3.0-alpha 拿更新的预览能力**：拒绝。它要求 **AGP 9.2.0 / compileSdk 37**，本项目是 AGP 8.5.2 / compileSdk 35，会直接把构建打挂。

**只升 Glance、不动 BOM / Kotlin**：拒绝。会让 compose runtime 被单独抬到 1.7.8 而 `ui` / `material3` 留在 1.7.2，家族版本错配会出运行时崩溃。三个版本必须同时升。

**在列表项里放照片缩略图**：拒绝。位图是最容易把 Binder 事务打爆的东西（平台 javadoc 点名警示），而组件的价值是"看见清单"；看图应进 App。

**沿用 3×2 格（与快速录入同尺寸）**：拒绝。110dp 高扣掉 padding 与标题行，剩给列表不到 50dp = 一条记录，"滑动查看更多"在物理上不成立——这正是旧组件在 v1.4.0 被砍的半个原因。

**用传统 RemoteViews + `RemoteViewsService` 手写列表（v1.3.4 路线）**：不作为首选。Glance 的 `LazyColumn` 与 App 内同栈 Compose，省一整套手写 XML；且两者**底层都是 RemoteViews 集合视图**，v1.3.5 记录的 ColorOS 兼容差异**照样可能复现**——所以真正的裁判是 T01 真机 spike，不是技术选型本身（见文首状态标注与 D6）。

---

## 附：T01 真机 spike 结论回填表

> **本节待 T01 执行后由实现者直接回填。降级结论以本表『结论』列为准，不在正文另写附注。**
>
> **判定口径（回填时照此填）**：
> - **可滑动？** = 列表区纵向拖动能滚动，且松手后不回弹到底。
> - **滑动是否误触发？** = 滑动过程中是否**启动了 App**，或**触发了桌面"拖动/调整组件"手势**。能滑但手势打架，**同样判不通过**——用户对桌面的手势预期是"拖动组件"，被打断比不能滑更糟。
> - **条目渲染正常？** = 无文字截断 / 串行 / 重叠 / 空白行。
> - **缩到最小高度（4×3 = 250×180dp）** = 标题行 + 统计行完整，列表区**至少 1 行完整可见**。
> - **预览走到哪一档** = 记录实际生效的最高档。**ColorOS 大概率走 L1（位图）**，其硬指标是"**看到组件版式，而不是暨存的应用图标**"；原生/类原生 API 31+ 走 L2；Android 15+ 走 L3（能看到真实学年与真实条数）。

| 机型 / launcher | Android 版本 | ① 可滑动？ | ② 滑动是否误触发（启动 App / 拖动组件） | ③ 条目渲染正常？（无截断/串行/重叠） | ④ 缩到 4×3 至少 1 行完整可见？ | 预览生效档（L1 / L2 / L3） | `USE_LAZY_COLUMN` | 结论 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Android 模拟器 `sdk_gphone64_x86_64`（AOSP NexusLauncher / Launcher3）· 1080×2400 @420dpi · **API 34**，两次独立实测（手势为 `adb shell input swipe` 合成注入，两次均验证到滚动） | 14（API 34） | **通过**：组件区内纵向滑动，可见行由「第 1–5 行」变为「第 7–11 行」（滚动 6 行）；另一轮由「第 1–7 行」滚到「第 15–20 行」并可反向滚回顶部。松手后均停住不回弹，双向连续 | **通过（组件区内）**：同一手势后组件 bounds 仍为 `[309,472][1023,1372]`——未移动、未变形；仍在桌面（dock + 搜索栏均在）；暨存 App 未启动。⚠️ **边界提示**：起点落在**组件下边界之外**时上滑会触发 Launcher 自身的「上滑打开应用抽屉」（桌面常规手势，不是组件内冲突），需真人手测复核 | **通过**：行距均匀 158px（= 60dp），每行「第 N 行 · 滑动测试条目 X」+「智育 · 日期 · 一等奖」两行文字完整，无截断 / 串行 / 重叠 / 空白行 | **通过（已实测进入 resize，未压满 4×3）**：长按组件**顶部 padding**（非列表区）成功进入 resize 模式，拖底部手柄把组件从 1214px 压到约 922px（≈351dp）；该尺寸下标题行、统计行均完整，列表区见第 1–4 行**完整可见**，且最小尺寸下再次纵向滑动仍可滚（第 1–5 行 → 第 5–8 行）。以实测几何外推：chrome ≈60dp、行距 ≈60dp，180dp（4×3）时列表区 ≈120dp → **约 2 行完整可见 ≥ 1 行**。证据：`tmp/spike/51_after_resize.png`、`52_step.png`、`60_min_size.png`（后续几次压手柄的拖拽起点错位、落回列表区被当成滚动，未能压满 4×3——脚本坐标缺陷，非组件/launcher 缺陷） | 系统选择器渲染的是**实时组件版式**（`LauncherAppWidgetHostView`，非应用图标）；本机 API 34 < 35，**L3 generated preview 不适用、未验证** | `true` | **通过**（仅原生/类原生，且为合成手势；真人手测待补）。**另记一条需 T02/T03 复核的观察**：在冷启动 + 软件渲染（swiftshader）的模拟器上**首次绑定组件**时出现过一次「暨存 isn't responding」ANR，点 Wait 后恢复、组件渲染正常——真机首绑（`provideGlance` 首帧 + WorkManager 调度）耗时需在 vivo 上复核，若真机也复现则首帧降级（先出 `glance_default_loading_layout`）要提级处理 |
| **ColorOS / OPPO**（v1.3.5 的历史雷区） | — | 未验证 | 未验证 | 未验证 | 未验证 | 未验证 | `true`（沿用上一行） | **未验证 · 残留风险** |
| vivo V2301A / OriginOS（目标真机，装包前掉线） | 16（API 36） | 未验证 | 未验证 | 未验证 | 未验证 | 桌面实例为**版式渲染**（拍照 / 相册两个色块，非应用图标）；系统选择器未截图 | `true`（沿用上一行） | **未验证** |

**降级执行**：任一目标机型在 ①②③ 任一项判否 → 该表『结论』列写"降级"、`USE_LAZY_COLUMN` 置 `false`，启用**静态 `Column` + `LocalSize` 计算行数 + 底部「共 X 条 · 查看全部」**；需求"能看更多"本身不降级，由"查看全部"这一跳进 App 兜住。④ 不通过属于尺寸降级问题（PRD P1-6），不影响列表形态的选择。
