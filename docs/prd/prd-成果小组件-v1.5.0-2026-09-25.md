# PRD：新增「我的成果」桌面小组件（学年切换 + 实时更新 + 滑动查看）

> 2026-10-09：刘总确认采用 [组件内直接选择学年](../plans/2026-10-09-achievement-widget-year-picker.md)，
> 替代下文的左右箭头及共享学年偏好交互；记录只读、列表滚动与主动推送要求继续生效。

| 项 | 内容 |
| --- | --- |
| 文档版本 | **v1.1**（v1.0 草稿 → 已按 `docs/design/widget-achievement-design-v1.5.0-2026-09-25.md` 订正） |
| 日期 | 2026-09-25 |
| v1.1 修订 | ① `MAX_ROWS 30 → WIDGET_MAX_ROWS 20`（§3.3 / P0-4 / P1-1 / §8，理由：API 32+ 全部 item 一次性进 Binder 事务）；② 预览由"双保险"升级为**三档** L1/L2/L3，含 L2 不得放 ListView、`previewSizeMode = Responsive`、限流节流（§3.4 / P0-7）；③ §6 B-7 由"沿用 Glance 1.1.0"改为"升级官方三件套"，并新增 **P0-9**；④ §9 Q4 换成"依赖升级的回归风险"，预览取舍标记为已决策 |
| 作者 | Alice（PM） |
| 项目 | `zongce-android`（暨存 · Android，package `com.zongce.app`） |
| 建议发版版本 | **v1.5.0**（`versionCode` 建议 `1000014`，继 v1.4.0 / `1000013` 之后） |
| 编程语言 / 技术栈 | Kotlin · Jetpack Compose · Material 3 · Room · Navigation Compose · Glance / AppWidget |
| 文档状态 | 待架构师 / 用户确认（重点看 §9 待确认问题） |

---

## 0. 原始需求复述（用户原话）

> "帮我开发一个我的成果小组件，支持在组件选择学年，小组件实时更新成果内容，支持滑动查看更多。你先写方案，选择组件预览的时候要看到实际的样子。"

拆成四条可验收的诉求：

1. **新增一个「我的成果」桌面小组件**（App 内这个页面叫「成果」，即 `ui/AchievementScreen.kt`）。
2. **能在组件上选学年**，不用进 App。
3. **组件内容实时更新**——App 里存了/删了/改了记录，桌面上的数字和列表要跟着变。
4. **组件里的成果列表能滑动查看更多**。
5. **在系统的「添加组件」选择器里，看到的是这个组件真实的样子**（不是应用图标）。

---

## 1. 产品定义

### 1.1 Product Goal（一句话）

> **让暨大学生在桌面上就能看到某一学年攒下的全部成果，切学年、滑列表都不用进 App。**

### 1.2 背景：为什么是"重新做"，不是"把 v1.4.0 删掉的加回来"

这一条必须先说清楚，否则团队会误判成本与方向。

| 时间 | 事实 | 出处 |
| --- | --- | --- |
| v1.3.4 | 成果组件从 Glance 迁到传统 RemoteViews，做**可滑动列表**；同时发现"Glance 不支持滚动列表"（当时的判断，见下方 ⚠️） | `VERSION_HISTORY.md` v1.3.4 |
| v1.3.5 | **回退**为"Provider 一次性填充固定三行摘要（`MAX_SUMMARY_ROWS = 3`）"，理由：规避不同桌面（尤其 ColorOS）对集合视图的兼容差异，**兼容性优先于滚动** | `VERSION_HISTORY.md` v1.3.5 |
| v1.4.0 | 「成果概览」组件**整体移除**（P0-6：7 项删除 + 8 项修改）。移除理由不是"没人用"，而是：**3×2 格子只塞得下 3 条固定摘要、滑不动，感知弱而维护成本高**（要单独维护一套 RemoteViews XML + 写死色值 + 手动推送刷新） | `docs/prd/prd-删除与组件精简-v1.4.0-2026-09-23.md`；`docs/adr/0002-widget-reads-summary.md`（已 Deprecated） |

**本次与 v1.4.0 的关系，三句话：**

1. **不是回退，是重做。** v1.4.0 砍掉的是"3×2 固定三行摘要"那个形态；本次要的是"**更大的格子 + 真正可滑的列表**"，形态与旧组件不同，旧代码（`widget/AchievementListWidget.kt`、`widget_achievement_list.xml`、`AchievementListService.kt`）**一份都不恢复**。
2. **ADR-0002 不复活。** 它的 Deprecated 状态保持不动；本次若确认"组件只读 Room + 主动推送刷新"这条路线，请**新建 ADR-0003** 记录，让 v1.4.0 的决策链完整可读。
3. **历史踩过的三个坑必须前置规避**：① ColorOS 对集合视图的兼容差异（v1.3.5 回退的直接原因）；② 学年状态源分裂（v1.3.6 修的"App 内选学年、组件不刷新"，根因是 App 内是内存状态、组件读 SP，两边各一份）；③ 写路径漏挂刷新导致桌面停在旧值（v1.4.0 之前的隐性约束）。这三条的规避方案分别写在 §5 P0-2 / P0-4 / P0-6。

⚠️ **一处需要纠正的历史认知**：v1.3.4 记录"Glance 不支持滚动列表"，但 `androidx.glance:glance-appwidget` 1.x 实际提供 `androidx.glance.appwidget.lazy.LazyColumn`（本项目当前 1.1.0，本次升至 1.2.0，见 §6 B-7）。不过**无论走 Glance 还是传统 RemoteViews，底层都是 RemoteViews 集合视图（ListView + RemoteViewsService）**，v1.3.5 记录的 ColorOS 兼容问题**照样可能复现**。所以"能不能滑"不能靠文档判断，必须先真机验证（见 §5 P0-0、§9 Q2）。

### 1.3 User Stories

| # | 故事 |
| --- | --- |
| US-1 | 作为**暨大学生**，我想在桌面上加一个「我的成果」小组件，这样不用打开 App 就能看到这学年攒了多少条成果 |
| US-2 | 作为**暨大学生**，我想在组件上直接切换学年（‹ 2026-2027 ›），这样回看上一学年的成果不用进 App 再点一遍 |
| US-3 | 作为**暨大学生**，我期望我刚拍下证书存进 App，回到桌面时组件上的条数和列表**立刻就是新的**，而不是昨天的样子 |
| US-4 | 作为**暨大学生**，我想组件里的成果列表能上下滑动，这样一学年十几条也能在桌面上翻完，不用被"只显示三条"卡住 |
| US-5 | 作为**暨大学生**，我希望在系统「添加组件」的列表里**看到这个组件长什么样**，这样我才知道它值不值得加到桌面 |
| US-6 | 作为**暨大学生**，我想点一下组件上的某一条成果就能进 App 看它，这样从桌面到材料是一步到位 |
| US-7 | 作为**已提交完综测的暨大学生**，我想删完整个学年的记录后组件不会变成一块空白或崩掉，而是自动回到还有内容的学年 |
| US-8 | 作为**暨大学生**，我希望只加「我的成果」组件时桌面的「快速录入」（拍照 / 相册）组件不受影响，两个组件互不打扰 |

---

## 2. 现状核查（代码事实，PRD 的事实基线）

> 以 2026-09-25 的 `main` 代码为准。实现时若发现已漂移，请回来订正本节。

| 层 | 位置 | 事实 |
| --- | --- | --- |
| 桌面组件 | `widget/JicunWidget.kt` + `widget/JicunWidgetReceiver.kt` | 目前**只有**「快速录入」一个组件（Glance，拍照 / 相册两个入口），3×2 格（180×110dp）。色值写死：`CardColor #FFFFFF` / `InkColor #1B2430` / `PrimaryColor #2D5F9A` / `PrimaryContainerColor #DDEAFF`。遵守 ADR-0001：只发 Intent，不碰数据库 |
| Manifest | `AndroidManifest.xml:39-49` | 只注册了 `.widget.JicunWidgetReceiver` 一个 receiver |
| 数据层 | `data/AwardDao.kt` | `allWithPhotos(): Flow<List<RecordWithPhotos>>`（`ORDER BY awardDate ASC`）；删除唯一入口是 `RecordDeletion`（经 `deleteRecordsAndPhotos`） |
| 学年规则 | `core/AcademicYear.kt` | 9 月 1 日滚动；`LABEL`（当前目标学年）、`belongsTo(dateText,label)`、`inYear(items,year){dateOf}`、`yearsOf(dateTexts)`（**恒含 `LABEL`**，降序）。学年归属全仓只有这一处实现 |
| 学年偏好 | `data/AchievementYearStore.kt` | SharedPreferences 名 `jicun_achievement_widget`、键 `selected_year`、`commit()` 同步落盘；`current(context, years)`：存的学年不在 years 里就回落 `AcademicYear.LABEL`。**成果页与（本次的）组件共用这一份存储** |
| 成果页 | `ui/AchievementScreen.kt` | 学年 chip 行 → `YearSummary`（条成果数 / 覆盖几育）→ `RecordGroup`（五育色点 + 获奖名称 + 「五育 · 日期 · 等级」）→ 空态 `EmptyYear`。切学年写偏好走 `RecordViewModel.saveAchievementYear()`（单消费者 Channel 串行） |
| 路由 | `MainActivity.kt` | `ROUTE_ACHIEVEMENT = "achievement"`、`ROUTE_ENTRY = "entry/{recordId}"`；`WidgetActions.OPEN_ACHIEVEMENT` 已存在且 `MainActivity` 有路由（v1.4.0 按 PRD §7.3 保留，目前无发送方） |
| 预览 | `res/xml/jicun_widget_info.xml` + `res/layout/widget_preview_entry.xml` + `res/drawable-nodpi/widget_preview_entry_img.png` | 双保险：`previewLayout`（API 31+）+ `previewImage`（位图，由 `tmp/gen_widget_previews.py` 生成）。**ColorOS「原子组件」选择器不渲染 previewLayout，会回落到应用图标，位图必须兜底** |
| 构建 | `app/build.gradle.kts` | `minSdk 26 / targetSdk 35 / compileSdk 35`，Compose BOM 2024.09.02，Glance `1.1.0`，Room 2.6.1。**本次将升为** Glance `1.2.0` + Compose BOM `2025.02.00` + Kotlin `2.0.21`（KSP `2.0.21-1.0.25`），理由见 §6 B-7 |

**结论（写进口径）**：

- 新增组件是**与 `JicunWidget` 并列的第二个组件**，两者各注册一个 receiver、各有一份 `*_widget_info.xml`。`JicunWidget` 的代码 / 布局 / 行为**一字不改**。
- 组件读学年**必须走 `AchievementYearStore`**，绝不能再起第二份学年存储（v1.3.6 的根因就是状态源分裂）。
- 组件**只读 Room，不写 `award_records` / `award_photos`**；它唯一的写是学年偏好（`AchievementYearStore.set()`）。

---

## 3. 关键机制口径（三条主线，先定口径再写代码）

### 3.1 学年切换（组件内切换，不做配置页）

- **交互形态（采纳 team-lead 倾向）**：组件标题行右侧 `‹ 2026-2027 ›`——左边一个左箭头、中间学年标签、右边一个右箭头。点箭头就地切换，**不跳 App**。
- **学年候选列表**：`AcademicYear.yearsOf(全部记录的 awardDate)`，即与成果页 chip 行同源。`yearsOf` 恒定包含当前目标学年 `AcademicYear.LABEL`，所以候选**至少 1 个**（一条记录都没有时，候选就是 `[2026-2027]`）。
- **排序与边界**：候选降序（最新学年在左）。已在首端 → 左箭头置灰（`#C7D0DA`）且点击无效；已在末端 → 右箭头置灰。只有 1 个学年 → 两端都置灰。
- **切换后立刻生效**：写 `AchievementYearStore.set(context, year)`（`commit` 同步落盘）→ 主动 `updateAll` 重渲染。
- **已删空学年的回落**：组件的学年读取一律走 `AchievementYearStore.current(context, years)`。存着的学年若因删记录而不在 `years` 里，它会自动回落到 `AcademicYear.LABEL`；`LABEL` 又恒在 `years` 里，所以**组件永远不会停在空学年上**。这与成果页的 `LaunchedEffect(years)` 回落是同一套语义，不另写一份。
- **与成果页同源**：App 内切学年 → 也写这同一个 store；桌面切学年 → 也写这同一个 store。两边永远一致（v1.3.6 的修复目标），**因此 App 内切学年也必须挂组件刷新**（见 3.2）。

### 3.2 实时更新（主动推送 + 生命周期刷新，不轮询）

**原则**：`updatePeriodMillis` 保持 **0**，绝不定时轮询（Android 最小间隔 30 分钟且每次唤醒进程，开销远大于一次推送，还带最长半小时的数据延迟——ADR-0002 已评估并拒绝）。

**必须挂刷新的写路径（全仓就这 4 条，漏一条桌面就会停在旧值且不报错）**：

| # | 写路径 | 位置 | 触发时机 |
| --- | --- | --- | --- |
| W1 | **保存 / 编辑记录**（含"编辑时移除某张照片"） | `RecordViewModel.saveRecord()` | `onSaved` 回调之前，DB 与文件都处理完 |
| W2 | **单条删除** | `RecordViewModel.deleteRecord()` | `finishDeletion()` 之前 |
| W3 | **多选删除** | `RecordViewModel.deleteSelected()` | `exitSelectionMode()` 之后、`finishDeletion()` 之前 |
| W4 | **整学年删除**（导出后确认） | `RecordViewModel.deleteYear()` | `reportDeletion()` 之后、`finally` 之前 |
| W5 | **App 内切学年**（成果页 chip） | `RecordViewModel` 学年写入队列的消费者（`init{}` 里的单消费者协程） | `AchievementYearStore.set()` 之后 |

> W5 是 v1.3.6 那条链路的**补齐**：v1.4.0 删掉组件后，队列里只剩"写 SP"这一步；组件回来后必须把"推送刷新"接回去，且要接在**队列消费者里**（串行），不能裸 `launch(Dispatchers.IO)`——那是 v1.3.6 修掉的并发坑（连点 chip 时最后点的学年不一定最后落盘）。

**组件自身生命周期刷新（兜底）**：

- 系统在 **添加组件、开机 / 启动器重启、App 升级（`ACTION_MY_PACKAGE_REPLACED`）、桌面进程重启** 时会回调 `onUpdate` → `provideGlance` **当场重新查库**，天然是最新数据。这是"冷启动也能对"的根本保障。
- （P1-3 兜底）App 回到前台时再刷一次，覆盖"推送因进程被杀没送到"等边缘情况。

**刷新入口收口**：新建 `widget/AchievementWidgetRefresher.kt`（名字由架构师定），对外一个 `suspend fun refresh(context)`。**内部先查 `GlanceAppWidgetManager(context).getGlanceIds(AchievementWidget::class.java)`，为空直接 return**——没添加组件时不做无谓的查库与 RemoteViews 传输。W1–W5 一律只调它一个函数，不允许各处直接 `updateAll`。

### 3.3 滑动查看更多

- **口径**：组件内成果列表**纵向滚动**，不是翻页（翻页需要左右箭头 + 页码，会和学年切换的箭头抢位置、抢用户理解）。
- **实现**：优先 `androidx.glance.appwidget.lazy.LazyColumn`（Glance 自带，与 App 内同栈 Compose，省掉一套 RemoteViews XML；本项目本次同步升到 **1.2.0**，见 §6 B-7）。
- **⚠️ 硬风险（必须先验证）**：Glance LazyColumn 底层仍是 RemoteViews 集合视图，而 **v1.3.5 就是因为在 ColorOS 上被集合视图的兼容差异坑了才回退的**。因此**先做 P0-0 真机 spike，滑得动才走 LazyColumn 路线**；滑不动或条目渲染异常，降级为「固定 N 条（建议 4 条）+ 底部『共 N 条 · 查看全部』」（v1.3.5 路线，兼容性最优）。**需求本身不降级，"能看更多"必须通过滑动或一次点击达成。**
- **列表项内容**：五育色点 + 获奖名称（单行，末尾省略）+「五育 · 日期 · 等级」（单行，末尾省略）——与成果页 `RecordRow` 一致，用户从桌面到 App 不换认知。`awardName` 为空显示「未填写获奖名称」。
- **排序**：`sortedByDescending { awardDate }`（与成果页 `ofYear` 一致：新的在上）。DAO 返回的 ASC 只是原始顺序，展示排序由组件负责。
- **条数上限**：**`WIDGET_MAX_ROWS = 20`**。不是"懒加载"，是硬上限：Glance 在 `SDK_INT > 31` 时走 `RemoteViews.setRemoteAdapter(viewId, RemoteCollectionItems)`，**全部 item 的 RemoteViews 会一次性打包进这次 update 的 Binder 事务**（`<= 31` 才走 `GlanceRemoteViewsService` Intent 路），平台 javadoc 明确警示"总内存要小"。20 条是 team-lead 据该实证从 PRD 初稿的 30 下调后的结论，**以 20 为准**。超出截断并在末尾给「还有 N 条」提示（P1-1）。
- **点某条跳到哪**：点整块组件 / 点列表任意一条 → 进 App **成果页**（复用现成的 `WidgetActions.OPEN_ACHIEVEMENT` + `MainActivity` 路由，**零新增路由**）。P1-2 再升级为"点某条直达该记录详情 `entry/{id}`"。

### 3.4 组件预览：在系统选择器里看到真实样子

用户原话"选择组件预览的时候要看到实际的样子"——**三档预览，逐档递进**（team-lead 已拍板走进取路线，故比初稿的"双保险"多一档 L3）：

| 档 | 手段 | 生效范围 | 用户看到什么 | 关键约束 |
| --- | --- | --- | --- | --- |
| **L1 保底** | `android:previewImage="@drawable/widget_preview_achievement_img"`（`drawable-nodpi` 位图，`tmp/gen_widget_previews.py` 新增 achievement 分支生成） | **全版本** | 静态版式（示例数据） | **ColorOS「原子组件」选择器的唯一可用路径**——该选择器不渲染 `previewLayout`，会回落到应用图标（本项目 `jicun_widget_info.xml` 注释已记录）。位图规格 `250×250dp` @3x = **750×750 px**（沿用现有脚本"dp@3x"口径） |
| **L2 增强** | `android:previewLayout="@layout/widget_preview_achievement"` | 支持该属性的 launcher（API 31+），优先级高于 previewImage | 静态版式（示例数据，按 launcher 尺寸缩放） | **必须单独写一份 XML，且里面绝不能放 `ListView`**——集合数据是运行时注入的，预览里会渲染成**一张空列表卡片**。用 **LinearLayout 假造若干行**（官方"Build accurate previews that include dynamic items"的要求）。**不许图省事直接复用真实布局** |
| **L3 真实** | `providePreview()` + `GlanceAppWidgetManager.setWidgetPreviews()` | **Android 15+（API 35）** | **真实学年 + 真实条数 + 真实前几条成果** | ① 只有 **Glance 1.2.0** 才有这套 API（1.1.0 全仓 grep 零命中），这是 §6 B-7 必须升 1.2.0 的唯一理由；② **必须 override `previewSizeMode = SizeMode.Responsive`**，默认 `SizeMode.Single` 只按 minWidth/minHeight 渲染会把列表裁掉（官方 troubleshooting 点名的坑）；③ 平台**限流约每小时 2 次** → 必须做 fingerprint 判重 + 最小间隔节流，否则更新会静默失败 |

- **"实际的样子"的边界（如实告知用户）**：L1 / L2 是静态资源，渲染发生在启动器进程、此时 App 未必跑过，**所以这两档不可能显示真实记录**；**L3 可以**（Android 15+ 能看到你真实的学年与条数）。三档共通的最低保证：**版式、配色、文案结构与真实组件 1:1**（标题行 + 学年切换 + 统计行 + 若干条成果），一眼能认出"加进去之后就是这样"。示例数据用真实感记录（如"全国大学生XX竞赛 · 智育 · 2026-05-12 · 第1等级"），不用 Lorem ipsum。
- **验收硬指标**：① 一台 ColorOS/OPPO 真机的「原子组件」选择器 → 看到版式（走 L1），**不是暨存的应用图标**；② 一台原生/类原生启动器（API 31+）→ 看到版式（L2）；③ 一台 Android 15+ 设备 → 选择器里出现**真实学年与真实条数**（L3），且连续两次进入选择器不因限流丢失预览。

---

## 4. UI 设计稿

### 4.1 尺寸建议：**4×4 格（250×250dp）**

| 项 | 值 | 理由 |
| --- | --- | --- |
| `targetCellWidth` / `targetCellHeight` | `4` / `4` | 4×4 = 250×250dp（`70×n − 30`） |
| `minWidth` / `minHeight` | `250dp` / `180dp` | 允许纵向缩到 3 行（180dp），但不许缩到 3 列宽——那样一行记录会被挤成两三个字，"滑动"也就没了意义 |
| `resizeMode` | `horizontal\|vertical` | 与现有组件一致 |
| `updatePeriodMillis` | `0` | 不轮询（§3.2） |
| `initialLayout` | `@layout/glance_default_loading_layout` | 与现有 Glance 组件一致，真实内容由 `provideGlance` 渲染 |

**为什么不能沿用快速录入的 3×2（180×110dp）**：3×2 只有 110dp 高，扣掉 padding 与标题行，**剩给列表的不到 50dp = 一条记录**，"滑动查看更多"在物理上不成立；v1.4.0 之所以把旧成果组件砍了，一半原因就在这里（只能塞 3 条固定摘要、滑不动）。4×4 是主流桌面组件（日历 / 待办 / 笔记）的可读下限：列表可视区约 146dp ≈ **3 行完整 + 第 4 行露头**，正好让"还能往下滑"这件事看得见。再往上（4×5）会明显挤占桌面其他组件，与"瞄一眼"的主场景不符。

### 4.2 线框图（4×4 / 250×250dp）

```
┌────────────────────────────────────────────────┐  ← 卡片 #FFFFFF 圆角 22dp
│ 12dp padding                                   │
│  ┌──────────────────────────────────────────┐  │
│  │ ◉ 暨存                    ‹  2026-2027  › │  │  ← 标题行 h=24
│  │  logo 18dp  14sp 加粗 #1B2430   11sp #5D6875│  │     箭头 15sp 加粗 #1B2430
│  └──────────────────────────────────────────┘  │     箭头禁用 #C7D0DA
│                    ↓ 6dp                       │
│  ┌──────────────────────────────────────────┐  │
│  │        12 条成果    │    覆盖 4 育        │  │  ← 统计行 h=44
│  └──────────────────────────────────────────┘  │     浅蓝底 #DDEAFF 圆角 12dp
│                    ↓ 6dp                       │     数字 20sp 加粗 / 标签 11sp
│  ────────────────────────────────────────────  │  ← 分隔线 1dp #E6EBF1
│  ┌──────────────────────────────────────────┐  │
│  │ ● 全国大学生XX竞赛                        │  │  ← 列表区 ≈146dp
│  │   智育 · 2026-05-12 · 第1等级              │  │     每行 h=44
│  ├──────────────────────────────────────────┤  │     色点 8dp 圆
│  │ ● 校级优秀志愿者                          │  │     名称 13sp 加粗 #1B2430
│  │   德育 · 2026-03-08 · 优胜奖               │  │     副行 11sp #5D6875
│  ├──────────────────────────────────────────┤  │     行间分隔线 1dp #E6EBF1
│  │ ● XX 奖学金                               │  │
│  │   智育 · 2025-11-20 · 第2等级              │  │
│  └──────────────────────────────────────────┘  │
│         ↕ 可上下滑动（3 行完整 + 第 4 行露头）   │
└────────────────────────────────────────────────┘
```

**空态（该学年 0 条记录）**——统计行保留，列表区换成空态，整块可点进 App：

```
┌────────────────────────────────────────────────┐
│  ◉ 暨存                    ‹  2026-2027  ›     │
│  ┌──────────────────────────────────────────┐  │
│  │         0 条成果    │    覆盖 0 育        │  │
│  └──────────────────────────────────────────┘  │
│                                                 │
│                      🏆                         │  ← 图标 28dp #C7D0DA
│             2026-2027 学年还没有记录              │  ← 13sp #1B2430
│            拍下证书，它就会出现在这里              │  ← 11sp #5D6875
│                                                 │
└────────────────────────────────────────────────┘
```

**最小高度（缩到 4×3 = 250×180dp）的降级**：标题行 + 统计行 + 分隔线完整保留，列表区压缩到 ≈76dp，**至少完整显示 1 行**，其余靠滑动。不允许出现"统计行被裁一半"或"列表区高度为 0"。

### 4.3 色值（小组件里必须写死）

RemoteViews 由桌面进程渲染，**拿不到 App 的 `MaterialTheme` / CompositionLocal**，所以全部写死（沿用 `JicunWidget.kt` 现有常量风格）：

| 用途 | 色值 | 来源 |
| --- | --- | --- |
| 卡片底 / 行底色 | `#FFFFFF` | 现有 `CardColor` |
| 主文字（获奖名称、数字、箭头） | `#1B2430` | 现有 `InkColor`（⚠️ 旧成果组件与预览 XML 里用的是 `#18202B`，是历史漂移，**本次统一到 `#1B2430`**，预览与新组件同步） |
| 主色（品牌、可点强调） | `#2D5F9A` | 现有 `PrimaryColor` |
| 统计行底（浅蓝） | `#DDEAFF` | 现有 `PrimaryContainerColor` |
| 次要文字（副信息、学年标签） | `#5D6875` | 旧成果组件沿用值 |
| 箭头禁用 / 空态图标 | `#C7D0DA` | 旧成果组件 `ArrowDisabled` |
| 分隔线 | `#E6EBF1` | **新增建议值**（取现有浅蓝底与白之间的浅灰蓝）；架构师若更愿意用主题同名色，请同步改预览 XML 与生成脚本，三处保持一致 |
| 五育色点 | 德育 `#3B7DD8` / 智育 `#7A5AF8` / 体育 `#2FA36B` / 美育 `#E0603C` / 劳育 `#C9912A`，未分类 `#8E8E93` | 与 `ui/Theme.kt` 的 `WuyuPalette.light` **逐字一致**（小组件不跟随深色模式，固定取浅色版） |

字号：标题 14sp 加粗 / 学年标签 11sp / 箭头 15sp 加粗 / 统计数字 20sp 加粗 / 统计标签 11sp / 记录名 13sp / 副信息 11sp。

---

## 5. 需求池

> P0 = Must have（本期必须交付）｜P1 = Should have（同期做掉，否则留债）｜P2 = Nice to have（本期明确不做，记档）
> 术语对照：学年 = `AcademicYear`；记录 = `award_records` / `AwardRecord`；成果 = `RecordWithPhotos`；五育 = `Wuyu`（德育 / 智育 / 体育 / 美育 / 劳育）。

### P0

| ID | 需求 | 验收标准（可测） |
| --- | --- | --- |
| **P0-0** | **技术前提验证（spike，半天）**：在目标真机（至少一台 ColorOS/OPPO + 一台原生或类原生启动器，API 31+）上验证 Glance `LazyColumn` 能否正常渲染并滑动 | 交付一段真机录屏：组件列表可上下滑动、滑动不误触发 App、条目文字不截断不串行。**滑不动或渲染异常 → 立即切 P0-4 的降级方案 B**（固定 4 条 +「共 N 条 · 查看全部」），并把结论写进 ADR-0003 |
| **P0-1** | 新增第二个独立组件「我的成果」：`widget/AchievementWidget.kt` + `AchievementWidgetReceiver.kt` + `res/xml/jicun_achievement_widget_info.xml` + Manifest receiver + 两条 string（`achievement_widget_label` = 「暨存 · 我的成果」、`achievement_widget_description`） | ① 系统组件选择器里同时出现「暨存 · 快速录入」和「暨存 · 我的成果」两个条目，可分别添加；② 「快速录入」的外观与行为**与现状逐像素一致**（ADR-0001 约束不变）；③ `assembleDebug` 通过 |
| **P0-2** | **组件内切学年**：标题行右侧 `‹ 学年 ›`，候选 = `AcademicYear.yearsOf(全部记录的 awardDate)`（恒含 `AcademicYear.LABEL`），点箭头就地切换并立刻重渲染 | ① 切到上一学年后，列表与「N 条成果 / 覆盖 M 育」同步变化，耗时可感（≤1s）；② 只有 1 个学年时两端箭头置灰（`#C7D0DA`）且点击无反应；③ **连点 3 次箭头，最终停在正确学年第 3 格，不跳格、不回退**（串行化，见 §3.2 W5 的并发坑）；④ 写入 `AchievementYearStore`（SP `jicun_achievement_widget` / 键 `selected_year`），杀 App 后**组件与成果页停在同一个学年** |
| **P0-3** | **学年候选同源 + 删空学年回落**：组件读学年一律 `AchievementYearStore.current(context, years)`；不新增第二份学年存储 | ① 删光某学年的记录后，该学年从候选消失；② 当前选中学年若被删空，自动回落（到 `LABEL`），组件**不空白、不崩溃、不停在空学年**；③ 组件与成果页永远显示同一学年 |
| **P0-4** | **成果列表可滑动查看更多**：`WIDGET_MAX_ROWS = 20` 条上限（理由见 §3.3：API 32+ 全部 item 一次性进 Binder 事务），按 `awardDate` 降序，每行 = 五育色点 + 获奖名称（单行省略）+「五育 · 日期 · 等级」（单行省略） | ① 可视 3 行 + 第 4 行露头，可上下滑动，滑动**不启动 App**；② 排序与成果页一致（新的在上）；③ `awardName` 为空显示「未填写获奖名称」；④ 40 条记录时列表显示 **20 条**（P1-1 补「还有 20 条」提示）；⑤ 若 P0-0 spike 判定不可用，走降级方案 B 并满足"能看更多" |
| **P0-5** | **空态**：该学年 0 条 → 「{year} 学年还没有记录 / 拍下证书就会出现在这里」；统计行保留并显示「0 条成果 · 覆盖 0 育」 | 空态下整块组件可点 → 进 App 成果页；不出现空白卡片 |
| **P0-6** | **实时更新**：W1 保存/编辑、W2 单条删除、W3 多选删除、W4 整学年删除、W5 App 内切学年 —— 五条路径全部挂刷新；`updatePeriodMillis = 0` | ① App 内保存/编辑/删除后 **≤3 秒**桌面条数与列表跟着变（不需要回到桌面再做任何操作）；② 成果页切学年 → 桌面组件学年同步变（v1.3.6 遗留链路补齐）；③ 用 `adb shell am kill com.zongce.app` 杀进程后从图标进 App，桌面显示仍是最新；④ `updatePeriodMillis` 为 0，无轮询 |
| **P0-7** | **组件预览三档**（详见 §3.4）：L1 `previewImage` 位图（全版本 / ColorOS 唯一可用）+ L2 `previewLayout` 静态 XML（API 31+，**不得含 ListView**）+ L3 `providePreview` + `setWidgetPreviews`（API 35+，真实数据，`previewSizeMode = Responsive`，带限流节流） | ① ColorOS「原子组件」选择器与原生/类原生启动器（API 31+）都**看到组件版式而不是应用图标**；② 预览的版式 / 配色 / 结构与真实组件 1:1（示例数据真实感文案）；③ Android 15+ 设备选择器里出现**真实学年与真实条数**；④ 连续两次进入选择器，预览不因平台限流（约 1 次/小时）丢失 |
| **P0-8** | **点击进入 App**：点整块组件或点列表任意一条 → 进成果页。复用现成 `WidgetActions.OPEN_ACHIEVEMENT` + `MainActivity` 路由，**不新增路由** | 点击后进 App 且停在成果页（不进拍照页）；冷启动 / 热启动都成立 |
| **P0-9** | **官方依赖对齐升级**（L3 真实预览的前提）：`glance-appwidget` `1.1.0 → 1.2.0`、Compose BOM `2024.09.02 → 2025.02.00`、Kotlin `2.0.20 → 2.0.21`（KSP `2.0.21-1.0.25`）。**不新增第三方库** | ① **升级单独一个 commit**，不与组件逻辑混在一起（便于回滚）；② `testDebugUnitTest` 现有 **97 个用例全绿** + `assembleDebug` 通过；③ 真机过一遍五个 tab（尤其成果页多选删除、导出页）；④ 升级回归过不去的兜底 = **砍 L3**、Glance 退回 1.1.0，其余需求不受影响 |

### P1

| ID | 需求 | 验收标准 |
| --- | --- | --- |
| **P1-1** | 列表超过 `WIDGET_MAX_ROWS` 时，末尾一行显示「还有 N 条」（可点，进成果页） | 40 条记录时列表 20 条 + 末尾提示「还有 20 条」 |
| **P1-2** | 点某条记录**直达该记录详情**（`entry/{id}`）：新增 `WidgetActions.OPEN_RECORD` + `extra recordId`，`MainActivity` 支持"widget action + recordId"直达 | 点第 3 条 → 进 App 并直接打开第 3 条的编辑页；返回键回到成果页 |
| **P1-3** | App 回到前台时兜底刷一次（`MainActivity` 的 `ON_RESUME` 或进程级生命周期） | 冷启动进 App → 桌面组件显示最新数据（覆盖"推送因进程被杀没送到"） |
| **P1-4** | 刷新入口收口到单一对象（建议 `widget/AchievementWidgetRefresher.kt`），内部先查 `GlanceAppWidgetManager.getGlanceIds()`，**为空直接 return** | 全仓 grep：除该对象外无任何 `GlanceAppWidget.updateAll` 调用；未添加组件时写路径不做查库 |
| **P1-5** | **查库失败降级**：`runCatching` 包住查库，失败时渲染空态（不是崩溃、不是白屏、不是卡在 loading 布局） | 人为让 DAO 抛异常 → 组件显示空态且仍可点进 App，日志一条 `Log.w` |
| **P1-6** | 最小尺寸降级：缩到 4×3（250×180dp）时，标题行 + 统计行完整，列表区至少 1 行完整可见 | 三种尺寸（4×4 / 4×3 / 拉伸到 5 列宽）截图，无裁切、无重叠 |
| **P1-7** | 文档同步：① **新建 ADR-0003**（组件只读 Room + 主动推送刷新 + 学年同源 + **依赖升至 Glance 1.2.0 / 三档预览的取舍**），**ADR-0002 保持 Deprecated 不复活**；② 更新 `VERSION_HISTORY.md`（新增 v1.5.0 记录卡，写明依赖升级与 ColorOS 预览/滑动的实测结论）、`CODE_STRUCTURE.md`（组件清单从 1 个变 2 个、构建依赖版本）、`README.md`（如列了组件）；③ `tmp/gen_widget_previews.py` 加回 achievement 分支并在注释里说明与 v1.4.0 删除的关系 | 三份文档与代码事实一致；脚本可重跑产出位图且不破坏现有 entry 位图 |
| **P1-8** | 组件最小刷新耗时的性能约束：`provideGlance` 内查库走 `Dispatchers.IO`，个人量级（几十~几百条）单次查询 < 50ms | 真机添加组件从"拖到桌面"到内容出现 ≤ 1s |

### P2（本期明确不做）

| ID | 需求 | 不做的理由 |
| --- | --- | --- |
| P2-1 | **组件配置页**（添加组件时先选一次学年，`android:widgetFeatures="reconfigurable"`） | 与"组件内随时切学年"重复；配置页在 ColorOS 上入口深、且必须声明 `reconfigurable` 才能再进一次。二选一，见 §9 Q1 |
| P2-2 | 组件内按**五育筛选** | 4×4 的高度已被学年切换 + 统计 + 列表占满，再加一行筛选会挤掉列表；App 内成果页也没做五育筛选，桌面先不越级 |
| P2-3 | 组件内显示**照片缩略图** | RemoteViews 跨进程传 bitmap 有大小限制，几十条记录成本高；组件的价值是"看见清单"，看图进 App |
| P2-4 | 组件内**删除 / 编辑**记录 | 破坏性操作绝不能放在没有确认能力的桌面上；删除只有 `RecordDeletion` 一个入口，那是 App 内的能力 |
| P2-5 | 组件**跟随系统深色模式**（`ColorProvider` day/night） | 现有「快速录入」组件是写死色值，两套组件混用会增加配色不同步的风险；如要做请两个组件一起做，单独开一期 |

---

## 6. 边界与不做的事

| # | 边界 | 说明 |
| --- | --- | --- |
| B-1 | **组件只读** | 只读 `award_records` / `award_photos`，绝不写记录、绝不碰照片文件（`PhotoStore`）。组件唯一的写是学年偏好 `AchievementYearStore.set()`。**ADR-0001「不碰数据库」对本组件不适用**，以新建的 ADR-0003 为准 |
| B-2 | **不在组件里删除 / 编辑** | 删除不可逆，桌面没有可靠的二次确认能力 |
| B-3 | **不在组件里拍照 / 选图** | 那是「快速录入」组件的职责，两个组件不互相抢 |
| B-4 | **学年列表为空的兜底** | `AcademicYear.yearsOf` 恒含 `LABEL`，候选至少 1 个；再加 `AchievementYearStore.current()` 的回落双保险，组件不会"没有学年可显示" |
| B-5 | **查库失败降级** | 渲染空态（可点进 App）+ 一条 `Log.w`，不崩、不白屏、不卡 loading |
| B-6 | **不动 `JicunWidget`** | 代码 / 布局 / 预览 / 行为一字不改；只新增，不修改 |
| B-7 | **不新增第三方依赖，但需升级官方三件套** | ① **不引入任何新库**（`docs/design/system_design.md` §6 约定）；`glance-appwidget-preview` / `glance-material3` / `glance-appwidget-testing` 本就是 `glance-appwidget` 的传递依赖，无需显式声明。② **但必须升级**：`glance-appwidget` **1.1.0 → 1.2.0**（L3 真实预览的 `providePreview` / `setWidgetPreviews` 只有 1.2.0 有，1.1.0 源码零命中），并**同步对齐** Compose BOM `2024.09.02 → 2025.02.00`（Glance 1.2.0 要求 compose runtime 1.7.8，旧 BOM 只到 1.7.2，只升 Glance 会造成 Compose 家族版本错配）与 Kotlin `2.0.20 → 2.0.21`（KSP 同步 `2.0.21-1.0.25`）。③ 升级属于"对齐官方依赖版本"，不是引入第三方库；回归范围见 §9 Q4 |
| B-8 | **不新增第二份学年存储** | 组件与成果页共用 `AchievementYearStore`（SP `jicun_achievement_widget` / 键 `selected_year`）。另起一份 = v1.3.6 状态源分裂 bug 原样复发 |
| B-9 | **不做定时轮询** | `updatePeriodMillis = 0`，永远 |
| B-10 | **不动 Room schema** | 本次不需要新字段，**不得触发迁移**（schema 保持 version 1） |
| B-11 | **不动删除管线** | `RecordDeletion` 的顺序铁律（先删库 → 后查引用 → 只删 0 引用文件）与本组件无关，只在其后挂刷新 |
| B-12 | **已添加的旧组件** | v1.4.0 前桌面上加过「成果概览」的老组件，系统层面 App 无法移除；升级后通常显示为无法加载，用户自行移除即可（Release Notes 里提示一句） |

---

## 7. 交互与跳转矩阵（实现照此表）

| 点击位置 | 行为 | 落地页 | 复用 |
| --- | --- | --- | --- |
| `‹` / `›` 箭头（可用态） | 切上/下一个学年 → 写 store → 重渲染 | 不跳页（留在桌面） | 新增（Glance `actionRunCallback` 或 `actionSendBroadcast`） |
| `‹` / `›` 箭头（禁用态） | 无反应 | 不跳页 | — |
| 统计行 | 进 App | 成果页 | 同下 |
| 列表任意一条 | 进 App | 成果页（P1-2 后升级为该记录详情 `entry/{id}`） | `WidgetActions.OPEN_ACHIEVEMENT` |
| 空态区域 / 组件空白处 | 进 App | 成果页 | `WidgetActions.OPEN_ACHIEVEMENT` |
| 列表区域纵向滑动 | 滚动列表 | 不跳页，不启动 App | — |

---

## 8. 数据与渲染口径（实现必须照此）

```
provideGlance(context, id)
  ├─ withContext(Dispatchers.IO) { runCatching { dao.allWithPhotos().first() } }
  │     └─ 失败 → 降级空态（B-5）
  ├─ years  = AcademicYear.yearsOf(items.map { it.record.awardDate })   // 恒含 LABEL，降序
  ├─ year   = AchievementYearStore.current(context, years)              // 同源 + 自动回落
  ├─ ofYear = AcademicYear.inYear(items, year) { it.record.awardDate }
  │             .sortedByDescending { it.record.awardDate }             // 与成果页一致
  ├─ 统计：条数 = ofYear.size ；覆盖几育 = ofYear.map { it.record.wuyu }.distinct().size
  └─ 列表：ofYear.take(WIDGET_MAX_ROWS = 20)
```

- **学年归属只有一处实现**：严禁在组件里手写日期比较或 `awardDate.substring`，一律 `AcademicYear`。
- **排序放在过滤之后**（与 `AchievementScreen.ofYear` 同一写法：过滤保序不重排，排哪一端由展示决定）。
- **五育色值**取 `Theme.kt` 的 `WuyuPalette.light`，写死在组件里（§4.3）。

---

## 9. 待确认问题（Open Questions）

| # | 问题 | PM 的默认建议 | 需谁定 |
| --- | --- | --- | --- |
| **Q1** | 「在组件选择学年」是**组件内随时切换**（`‹ 学年 ›`）还是**添加组件时的配置页选一次**（`reconfigurable`）？ | **组件内切换**。理由：① 随时可改，不用长按组件找「设置」；② 配置页在 ColorOS 上入口深，且必须声明 `reconfigurable` 才能二次进入；③ 旧组件就是组件内切换（v1.3.4 实现过），用户认知连贯。**若用户坚持"添加时选一次"，则 P2-1 升为 P0，且学年切换箭头可去掉** | 用户 / team-lead |
| **Q2** | 「滑动查看更多」是**列表纵向滚动**还是**翻页**？以及——**若真机验证 Glance `LazyColumn` 在 ColorOS 上滑不动，接受降级成「固定 4 条 + 底部『共 N 条 · 查看全部』」吗？** | **滚动优先**（与成果页一致，符合"拉下来看更多"的直觉，翻页会和学年箭头抢位置）。**但必须先跑 P0-0 spike**：v1.3.5 已经因为 ColorOS 集合视图兼容差异回退过一次，这次不能凭文档拍板。滑不动就走降级方案 B，**"能看更多"这条需求本身不降级** | 用户 / 架构师 |
| **Q3** | 组件尺寸定为 **4×4（250×250dp）**能否接受？它比现有快速录入组件大一圈，会在桌面上占明显位置 | **建议 4×4**。3×2 物理上放不下可滑列表（这正是 v1.4.0 砍掉旧组件的半个原因）；4×5 太占地方。最小可缩到 4×3。若用户嫌大，可讨论 4×3 起步 + 可拉伸到 4×4 | 用户 |
| **Q4** | **依赖升级的回归风险**：为做 L3 真实预览必须升 Glance `1.2.0`，连带 Compose BOM `2025.02.00` + Kotlin `2.0.21`（KSP `2.0.21-1.0.25`）。这三个版本会**同时影响 App 内全部 Compose 页面**（不只用组件的那一块） | **风险已由用户在选"本次就升 1.2.0 要真实数据预览"时认领**；把它当一次独立的回归项处理：① 升级单独一个 commit，不与组件逻辑混在一起；② 升级后必须跑 `testDebugUnitTest`（现有 97 用例）+ `assembleDebug`，并 `adb install` **真机过一遍五个 tab**（debug 包走 debug 签名，**验证不受阻**——特此订正初稿"本机无签名口令、构建不了可安装验证包"的说法，那条不成立）；③ 升级失败或回归过不去时的兜底：**砍掉 L3**（退回 L1+L2 静态预览），Glance 保持 1.1.0，其余需求不受影响 | 已认领（用户）；执行归 team-lead |

> **已决策，不再问（team-lead 拍板）**：初稿 Q4「预览显示不了真实记录，接受静态示例吗？」——走进取路线，预览升级为**三档**：L1 位图（全版本 / ColorOS 唯一可用）、L2 静态 XML（API 31+）、**L3 真实数据（API 35+，Glance 1.2.0）**。见 §3.4 与 P0-7。残留小风险：L3 受平台限流（约 1 次/小时）约束，极少数情况下预览会滞后一拍。
| **Q5** | 组件学年与成果页学年**必须同步**（共用 `AchievementYearStore`）吗？还是允许"组件独立选一个学年、不跟随 App"？ | **必须同步**。另起一份存储 = v1.3.6 那个"状态源分裂"bug 原样复发（App 内选的不生效、组件选的又同步不回 App）。如果确实要独立，唯一安全做法是新建第二个 SP 名（如 `jicun_achievement_widget_v2`，**不能复用 `jicun_achievement_widget`**，会污染老用户值）并在 ADR-0003 里写清两套学年的读写方 | 用户 / 架构师 |

> 附：版本号建议 **v1.5.0**（`versionCode` `1000014`）——按 `VERSION_HISTORY.md` 既有约定，次版本号 = 新增完整功能或较大用户流程；新增一个桌面组件属此列。**待 team-lead 确认。**

---

## 10. 不在本次范围内（显式划界）

- 「快速录入」组件（`JicunWidget`）的任何形态调整（尺寸、图标、样式、预览）。
- 删除能力 / `RecordDeletion` / 导出链路 / 更新检查（`UpdateChecker`、`UpdateThrottle`）——只在其后挂刷新，不改逻辑。
- Room schema 变更与迁移。
- 照片云同步 / 备份（`allowBackup=false` 的既有决策不变）。
- 应用内成果页的改版（本次只"共用"它的口径，不改它的 UI）。
- 组件跟随深色模式（P2-5）。
- 新增任何**第三方**依赖（`LazyColumn`、L1/L2 预览、L3 真实预览全部由官方 `glance-appwidget` 提供）。**但包含一次官方依赖的对齐升级**：Glance `1.1.0 → 1.2.0` + Compose BOM `2024.09.02 → 2025.02.00` + Kotlin `2.0.20 → 2.0.21`（KSP 同步），理由见 §6 B-7。
