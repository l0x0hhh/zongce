# PRD：删除能力（多选 + 导出后一键清学年）与「成果概览」组件精简

| 项 | 内容 |
| --- | --- |
| 文档版本 | v1.0（草稿，待评审） |
| 日期 | 2026-09-23 |
| 作者 | Alice（PM） |
| 项目 | `zongce_android`（暨存 · Android，package `com.zongce.app`） |
| 建议发版版本 | **v1.4.0**（versionCode `1000013`，继 v1.3.6 / `1000012` 之后） |
| 编程语言 / 技术栈 | Kotlin · Jetpack Compose · Material 3 · Room · Navigation Compose · Glance / AppWidget |
| 文档状态 | 待架构师 / 开发确认后进入实现 |

---

## 0. 原始需求复述（用户原话归纳）

1. 导出成功后，先弹出系统分享面板发出材料包；**等用户从分享面板回到暨存之后**，才弹出「是否删除这一学年」的确认。
2. 删除 = **记录连同其照片文件一起删**，不是只删数据库行。
3. 成果页支持**多选记录删除**，入口是顶部一个「选择」按钮（不做长按、不做侧滑）。
4. 导出后要删除的对象 = **该学年的全部记录**（不是本次导出 ZIP 里筛选后的子集）。
5. **砍掉桌面「成果概览」小组件**；保留桌面「快速录入」（拍照 / 相册）组件。

---

## 1. 产品定义

### 1.1 Product Goals（3 个，正交）

| # | 目标 | 衡量口径 |
| --- | --- | --- |
| G1 | **清空成本可控**：一学年材料提交完，用户能在 3 次点击内把这一学年的数据从 App 里清干净，不用一条一条删 | 从「导出完成页」到「学年清空」≤ 3 次点击（点分享 → 回 App → 确认删除） |
| G2 | **删除可信**：删除必须同时收回磁盘空间（照片原图），且绝不误删仍被其他学年记录引用的照片 | 删除完成后 `filesDir/photos/` 中该学年专属照片文件数下降 ≥ 95%（被跨学年引用的除外，必须保留）；**0 起**因删除导致其他学年记录缩略图缺失的事故 |
| G3 | **产品收敛**：移除维护成本高、感知弱的「成果概览」组件，让 App 与桌面的关系只剩一个入口（快速录入） | 「成果概览」相关代码 / 资源 / Manifest 声明 100% 移除，构建无残留资源引用 warning |

### 1.2 User Stories

| # | 故事 |
| --- | --- |
| US-1 | 作为**刚提交完综测材料的学生**，我想导出成功后顺手把这一学年清掉，这样下学期打开 App 是干净的，不用再翻一堆旧证书 |
| US-2 | 作为**存错了一批证书的学生**，我想在成果页用「选择」勾选几条一起删，这样不用逐条进详情页删除连带照片 |
| US-3 | 作为**担心删错的学生**，我希望删之前明确告诉我「多少条记录、多少张照片会一起删，无法恢复」，这样我敢点确认 |
| US-4 | 作为**手机存储紧张的学生**，我期望删除真的把照片文件也删了，而不是库里查不到、磁盘还占着几百 MB |
| US-5 | 作为**只用桌面拍照入口的用户**，我希望删掉「成果概览」组件后，桌面的拍照 / 相册入口不受影响，App 也不会因此变卡或崩 |

---

## 2. 现状核查（代码事实，PRD 的事实基线）

> 以下四条均以 2026-09-23 的 `main` 代码为准，写在 PRD 里是给架构师的设计约束，实现时若发现已漂移请回来订正本节。

### 2.1 删除链路（当前只有「单条」）

| 层 | 位置 | 事实 |
| --- | --- | --- |
| UI 触发 | `ui/ListScreen.kt:152-195` | `RecordRow` 的 `onDelete = { pendingDelete = item }` → `AlertDialog`（标题「删除这条记录？」，正文「「X」和它的 N 张照片会一起删除，无法恢复」）→ 确认按钮调用 `vm.deleteRecord(target)` |
| ViewModel | `ui/AppViewModel.kt:166-175` `deleteRecord(item: RecordWithPhotos)` | IO 协程内：对每张照片 `if (dao.photoReferenceCount(fileName) <= 1) photoStore.delete(fileName)` → `dao.deletePhotosOf(record.id)` → `dao.deleteRecord(item.record)` → `refreshWidget()` |
| DAO | `data/AwardDao.kt:36-49` | `@Delete deleteRecord(record)`、`deletePhotosOf(recordId)`、`deletePhotos(ids)`、`photoReferenceCount(fileName)`（以上为 v1.4.0 改动**前**的代码事实基线；改动后 `deleteRecord(record)` 与 `deletePhotosOf(recordId)` 因改走 `RecordDeletion` 已无调用方**被删除**，`deletePhotos(ids)` **保留**） |
| 文件层 | `data/PhotoStore.kt:40-42` | `delete(fileName)` 即 `File(dir, fileName).delete()`，私有目录 `filesDir/photos/` |

**结论（写进口径）**：当前删除**已经是「记录 + 照片文件一起删」**，且带引用计数保护（同一张图被 ≥2 条记录引用时不删文件）。本次要做的是把这个能力「批量化」，而不是新建删除逻辑。

**发现的顺序风险（建议顺带修，见 P1-4）**：现有顺序是「先删文件 → 后删 DB 行」。若 DB 删除失败，文件已消失、记录还在 → 该记录永久缺图，且不可逆。**建议反转为「先删 DB（事务）→ commit 成功后再清理无引用文件」**：失败时最多残留一个孤儿文件（无害），而不是丢数据。

### 2.2 导出后分享

| 项 | 事实 |
| --- | --- |
| 位置 | `ui/ExportScreen.kt:265-290`，`ExportState.Done` 下的「分享材料包」Button |
| 方式 | `FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", result.file)` → `Intent(Intent.ACTION_SEND)`（`type = "application/zip"`，`EXTRA_STREAM`，`FLAG_GRANT_READ_URI_PERMISSION`）→ `context.startActivity(Intent.createChooser(intent, "发送材料包到…"))` |
| **有无回调** | **没有任何回调**。当前是「点了就走」，App 不知道用户是否真的分享、何时回来 |
| 同页其他出口 | 「导出其他学年」OutlinedButton → `vm.resetExport()`（Clear 到 Idle） |

**结论**：要满足「回到 App 之后才弹」，必须把这次 `startActivity` 改成 **`ActivityResultLauncher`**（`rememberLauncherForActivityResult(StartActivityForResult())`）。注意两点：

- 我们只需要「**回来了**」这个事件，**不关心结果是 `RESULT_OK` 还是 `RESULT_CANCELED`** —— 用户取消分享也算回来了，照样弹删除确认。
- chooser 包裹的 `ACTION_SEND` 在部分 ROM 上对 result 的回填不可靠，**因此实现上必须在 launcher 回调之外再挂一层生命周期兜底**（见 §4.2 时序图的保险带）。

### 2.3 成果页现有结构（`ui/AchievementScreen.kt`）

`LazyColumn`（contentPadding `start/end = Space.page`，`top = Space.lg`）依次是：

1. **标题块**（第 90-101 行）：大标题「成果」+ 副标题「共 N 条记录 · 跨 M 个学年」
   → **标题行右侧是空的，这是「选择」按钮最自然的落位。**
2. **学年 chip 行**（第 103-124 行）：`JicunChip`，`horizontalScroll` 横向滚动，点击时 `selectedYear = year` 且 `vm.syncWidgetYear(year)`
3. **`YearSummary`**（第 126 行 / 137-159 行）：两个指标 —— 条成果数、覆盖几育
4. **内容区**：空学年 → `EmptyYear`；否则 `RecordGroup`（白卡，行间 `HorizontalDivider`，每行 `RecordRow`：五育色圆点 + 获奖名称 + 「五育 · 日期 · 等级」+ `ChevronRight`，整行 `onClick = onOpen(id)`）

补充说明该页的既有行为（改动时必须兼容）：

- 初始学年来自 `vm.initialWidgetYear()`（`LaunchedEffect(Unit)`），走的是**组件共用的学年存储**（本次要拆，见 §6）。
- 有一条 `LaunchedEffect(years)` 回落：当前选中学年若因删数据而不存在了，回落到 `years.firstOrNull() ?: AcademicYear.LABEL`。**批量删光一学年后必须靠它，不能崩。**
- 该页**没有 topBar / Scaffold 顶栏**，由 `MainActivity` 的 `NavHost` 承载，底部才有 `JicunGlassNavigationBar`。所以「选择」按钮只能做进 LazyColumn 内容里，**不要用 Scaffold.topBar**（会与 NavHost 的 padding 打架）。

### 2.4 版本号现状

- `app/build.gradle.kts:17-18`：`versionCode` 取 `releaseVersionCode` 属性（默认兜底 2），`versionName` 取 `versionName` 属性（当前兜底 `1.3.6`）；CI Release 时注入 `1000012` / `1.3.6`。
- `VERSION_HISTORY.md` 约定：次版本号 = **新增完整功能或较大的用户流程**。
- 本次含「新用户流程（多选删除 + 导出后删除）」+「移除一个用户可见的桌面组件」→ **建议 `1.4.0`，versionCode `1000013`**。

---

## 3. 需求池

> P0 = Must have（本期必须交付）｜P1 = Should have（同期做掉，否则留下债）｜P2 = Nice to have（本期不做，明确拒绝并记档）

### P0

| ID | 需求 | 验收口径（可测） |
| --- | --- | --- |
| **P0-1** | 成果页新增「选择」按钮（标题行右侧），点击进入多选模式：标题行变为「已选 N 条 / 取消」，`RecordRow` 前出现勾选框，整行点击改为「勾选/取消」而非打开详情；底部固定操作条提供「删除（N）」与「全选」 | ①未进选择模式时，外观与交互**完全等同现状**；②选择模式下点行不进详情页；③全选=当前学年可见列表全选；④返回手势/系统返回键优先退出选择态而非退出页面 |
| **P0-2** | 多选删除走统一确认弹窗：文案含**记录数**与**照片数**，明示不可恢复；确认后按 §5 口径批量执行 | 弹窗文案形如「删除 8 条记录和它们的 21 张照片？删除后无法恢复」，数字与实际被删记录数、照片数一致 |
| **P0-3** | 删除 = 记录行 + `award_photos` 行 + 照片**原图文件**（`PhotoStore.delete`），跨学年被引用的照片文件必须保留 | 删除后在 `filesDir/photos/` 实测：仅属于被删记录的文件消失；被其它记录仍引用的文件存在且对应记录仍可正常显示缩略图 |
| **P0-4** | 导出完成页点「分享材料包」后，**用户回到 App 时**弹出「是否删除这一学年」确认 | 时序见 §4.2；不设「只在分享页停留多久以内才弹」的限制：只要 Activity 回到前台即弹一次 |
| **P0-5** | 「删除这一学年」的作用域 = **该学年全部记录**（由成果页/主页学年口径筛选），与本次 ZIP 打包范围同源 | 导出成功 → 确认删除 → 该学年记录数为 0，成果页该学年 chip 消失并正确回落 |
| **P0-6** | 移除桌面「成果概览」小组件及其全部代码 / 资源 / Manifest 声明 | ① 桌面组件选择器里不再出现「暨存 · 我的成果」；② 保留「暨存 · 快速录入」（拍照 / 相册）且点击正常进录入页；③ `assembleRelease` 通过、无 Unused resource 新增错误 |

### P1

| ID | 需求 | 验收口径 |
| --- | --- | --- |
| **P1-1** | 删除完成给明确反馈：结果 Toast/SnackBar，形如「已删除 8 条记录 · 21 张照片」；部分照片文件删除失败时仍提示成功，但日志记一条 `Log.w` | 删除后必有一次可见反馈；Toast 不阻塞操作 |
| **P1-2** | 调整删除执行顺序为「**先删 DB（Room 事务）→ 成功后清理无引用文件**」 | 人为让 DB 删除失败时，照片文件必须还在（Unit 测试或手工插桩验证） |
| **P1-3** | 批量删除在 IO 线程执行；照片数 > 30 时确认按钮走 loading 态，ViewModel 层幂等，防连点重复触发 | 快速连点 3 次确认 → 只执行 1 次删除（ViewModel 层幂等标志位） |
| **P1-4** | 组件移除后，成果页「记住上次选的学年」仍需保留（把 `WidgetYearStore` 下沉/改名为纯 UI 偏好，不再与组件耦合） | 杀 App 重开 → 成果页停留在上次选的学年（而不是每次都跳回当前学年） |
| **P1-5** | `docs/adr/0002-widget-reads-summary.md` 标注 **Deprecated**（正文顶部加失效说明 + 指向本 PRD），不删历史；`0001-widget-entry-routing.md` 保留 | ADR 目录结构与历史链路可读 |
| **P1-6** | 更新 `VERSION_HISTORY.md` 与 `CODE_STRUCTURE.md`（组件清单、`AppViewModel` 职责描述里的「成果组件推送」需同步删） | 两份文档与代码事实一致 |

### P2（本期明确不做）

| ID | 需求 | 不做的理由 |
| --- | --- | --- |
| P2-1 | 删除撤销 / 回收站 / 30 天内找回 | 与「删除 = 不可恢复」这条产品承诺冲突。真要做需引入软删除字段 + 定时清理任务 + Room 迁移，**本期改动面过大** |
| P2-2 | 长按进入多选 / 侧滑删除 | 用户明确要求「顶部选择按钮」，多做一套手势会导致同一页面两套交互，与 P0-1 冲突 |
| P2-3 | 删除前展示照片缩略图清单 | 确认弹窗给出「N 条记录 · M 张照片」已足够决策；做缩略图墙会显著抬高弹窗复杂度，收益不明。**待补充**：若用户要求「先看一眼再删」可升级为 P1 |
| P2-4 | 「导出后总是删除」的开关 / 不再询问 | 破坏性操作必须每次确认 |

---

## 4. 交互与界面设计

### 4.1 成果页多选（`AchievementScreen.kt` 改造）

```
┌──────────────────────────────────────────┐
│ 成果                          [ 选择 ]    │  ← 标题行右侧新增 TextButton「选择」
│ 共 23 条记录 · 跨 2 个学年                │
├──────────────────────────────────────────┤
│ [2025-2026] [2024-2025]  →                │  ← 学年 chip（现状不动）
├──────────────────────────────────────────┤
│     23 条成果   │   5 育                  │  ← YearSummary（现状不动）
├──────────────────────────────────────────┤
│ ○ 全国大学生XX竞赛 · 国家级        ›       │  ← 选择模式：左侧 Checkbox
│ ○ 校级优秀志愿者 · 校级            ›       │     圆点左移或替换为勾选框
│ ● XX 奖学金 · 省部级               ›       │     选中行底色轻微高亮
└──────────────────────────────────────────┘
│  全选            [ 已选 3 条 · 删除 ]      │  ← 底部操作条（Scaffold.bottomBar 
                                             或 LazyColumn 末尾 sticky item）
```

规则：

- 未进入选择模式时，页面与现状**逐像素一致**；不加任何新控件到内容的行里。
- 选择态存 `AchievementScreen` 内部 `remember`（推荐 `mutableStateMapOf<Long, Unit>` 记已选 id），**跨返回 / 旋转保留**：建议提到 ViewModel 或 `rememberSaveable`，待架构师定（见 §8-Q4）。
- 切换学年 → 清空选择集。
- 删除执行成功后：退出选择态、清空选择集、(P1-1) 弹反馈、`YearSummary` 与 chip 列表随 Flow 自动重算（现有 `LaunchedEffect(years)` 会处理学年消失的回落）。
- 「删除」按钮在已选 0 条时 disabled。

### 4.2 导出后删除 —— 完整时序

```
用户点「导出这一学年」
        │
        ▼
[ExportCheck] 体检 → Blocked? → 报错停
        │ 无阻断
        ▼
[ZipExporter] 打包 → ExportState.Exporting(进度) → ExportState.Done(result)
        │
        ▼
完成页展示结果（recordCount / photoCount / sizeBytes / perWuyu）
        │
        ├─ 用户点「导出其他学年」→ vm.resetExport() → Idle（不弹删除，现状不变）
        │
        └─ 用户点「分享材料包」
                │
                ▼
        ActivityResultLauncher.launch(Intent.createChooser(ACTION_SEND, "发送材料包到…"))
        ViewModel 记录「待删除学年 = 本次 targetYear」（pendingDeleteYear）
                │
                ▼
        系统分享面板在前台（可能是微信/QQ/邮件/网盘…）
        用户可能：真发了 / 取消了 / 按 Home 走了 / 直接划掉 App
                │
                ▼  ★ 回到暨存（Activity RESUME，或 launcher 回调，二者取先到）
        若 pendingDeleteYear 非空 且 未弹过 → 弹确认：
        「2025-2026 学年已导出，是否删除这一学年的 23 条记录和它们的 47 张照片？
          删除后无法恢复。」
                │                       │
            [删除]                   [保留]
                │                       │
                ▼                       ▼
        执行学年删除（DB事务→文件）  清空 pending，不删
                │
                ▼
        (P1-1) 反馈「已删除 23 条记录 · 47 张照片」
                │
                ▼
        清空 pendingDeleteYear（同一学年本次导出生命周期内不再重复弹）
```

时序硬约束：

1. **必须等到用户真的离开过分享面板并回来**。不要在 `startActivity` 之后就弹 —— 那会盖在分享面板下面或直接打断分享。
2. **不区分 `RESULT_OK` / `RESULT_CANCELED`** —— 「回来了」就弹。取消分享也说明导出这一动作已经结束。
3. **保险带**：部分 ROM 上 chooser 的 ActivityResult 不回填。因此除了 launcher 回调，再挂一层「App 回到前台」的判定（`Lifecycle.Event.ON_RESUME` + 一个「分享已发起」的标志位）。两条路径共用同一个 `consumePendingDeletePrompt()`，**幂等**，一次导出生命期内只弹一次。
4. **进程被杀（用户在分享页划掉 App）**：`pendingDeleteYear` 只存内存，**不持久化** → 下次冷启动不弹、不删。宁可不删，也不错删。
5. 弹窗问的**是学年不是 Zip 文件名**：删除范围重新按学年算（见 §5），而不是记住导出那一刻的列表快照 —— 因为用户回来期间可能又加了记录。

### 4.3 删除确认弹窗文案（统一模板）

| 场景 | 标题 | 正文 | 主按钮 | 次按钮 |
| --- | --- | --- | --- | --- |
| 单条（列表页，现有） | 删除这条记录？ | 「X」和它的 N 张照片会一起删除，无法恢复 | 删除（红） | 取消 |
| 多选（成果页） | 删除 N 条记录？ | 已选 N 条记录和它们的 M 张照片会一起删除，无法恢复 | 删除（红） | 取消 |
| 导出后学年删除 | 删除这一学年的成果？ | 2025-2026 学年的 N 条记录和它们的 M 张照片会一起删除，无法恢复。**材料包已导出。** | 删除（红） | 保留（默认推荐） |

> 导出后弹窗的次按钮建议文案用「保留」而非「取消」，让用户明确知道默认是留下数据。

---

## 5. 数据删除口径（核心，实现必须严格照此）

### 5.1 删除单元

一次「删除」操作，对每条被删记录必须顺序完成三步：

1. **DB**：删 `award_photos` 中 `recordId = X` 的行 + 删 `award_records` 中 `id = X` 的行（同一 Room `@Transaction` 内）。
2. **文件**：对该记录的每张照片**重新查** `AwardDao.photoReferenceCount(fileName)`，**仅当计数为 0**（该照片的 DB 行已删除，已无任何引用）时才 `PhotoStore.delete(fileName)`。
3. **兜底**：`File.delete()` 返回 false（文件本来就不存在 / 被外部删过）**不算失败**，只记 `Log.w`，不影响整体成功判定。

> 顺序要求（对齐 P1-2）：先 1 后 2。所有 DB 行删成功、事务提交之后，再统一走 2 的文件清理。

### 5.2 引用计数的关键陷阱（务必实现正确）

判断「某照片文件是否还能删」必须以**删除动作执行完毕后**的数据库状态为准，**不能用删除前的快照**。

反例：照片 `ab12cdef.jpg` 同时被 2025-2026 的 A 记录和 2024-2025 的 B 记录引用。
- 删除 2025-2026 学年 → A 被删，B 仍在 → 计数 = 1 → **绝不删文件**。
- 先 Batch 删 Photo 行再统计，或者用旧快照一次性判断，都会误删 B 的照片。

正确做法：整个学年删除完成后，对「本次涉及到的所有 fileName 去重集合」逐个查一次 `photoReferenceCount`，= 0 才删。

### 5.3 学年归属口径

- **成果页展示 / 多选删除范围**：`AcademicYear.belongsTo(record.awardDate, year)`（与现状 `AchievementScreen.ofYear` 同源）。
- **导出范围**：`ExportCheck.targetItems(items, targetYear)` —— 它除了 `belongsTo` 还额外收进了「`awardDate` 为空 **或** 日期格式非法（`OUT_OF_RANGE`）」的记录。
- **两者是否等价？** 在「导出已成功」这个前提下**等价**：日期为空 / 非法的记录会被 `ExportCheck` 判为 `Level.BLOCK`，导出根本走不到 `Done`，也就不存在「导出后删除」这条路径。
- **因此删除一律用 `belongsTo`（严格口径）**，不采用 `targetItems`。理由：删除不可逆，"明确属于这一学年" 才算，不能顺带带走归属不明的记录。（若团队有异议，见 §8-Q2）

### 5.4 明确的「不删除」范围

| 对象 | 是否删除 | 说明 |
| --- | --- | --- |
| `award_records` 行 | ✅ | 主对象 |
| `award_photos` 行 | ✅ | Room 外键 / CASCADE + 显式清理，两条保险 |
| `filesDir/photos/<hash><ext>` 原图 | ✅（0 引用时） | 真正的磁盘回收点 |
| `cacheDir/exports/*.zip` 已导出的 ZIP | ❌ | ZIP 是提交给学校的产物，**绝对不能因为删了学年就把它删了**（用户可能还没发） |
| 系统备份中的照片副本 | ❌ 不涉及 | `allowBackup=false`（既有决策），系统备份里本就没有，无需处理 |
| 桌面组件已渲染的数据 | ❌ | 本次组件已移除；未移除前若有残留，随 `AppWidgetManager` 自然消亡，**不做主动清理** |

---

## 6. 边界与异常清单（实现逐条兜）

| # | 场景 | 期望行为 |
| --- | --- | --- |
| E-1 | 分享面板打开后，用户**按 Home 直接回桌面**，几秒后再从图标进 App | 回前台时弹删除确认（保险带路径生效） |
| E-2 | 用户在分享页**从最近任务划掉 App**（进程被杀） | `pendingDeleteYear` 不持久化 → 冷启动**不弹**、**不删**。ZIP 仍在 `cacheDir/exports/` 可再次导出 |
| E-3 | 分享过程中**旋转屏幕 / 切深色模式 / 分屏**（配置变更） | `pendingDeleteYear` 在 ViewModel 里 → 重建后仍弹一次且仅一次；多选的选择集同样要撑过旋转 |
| E-4 | 用户点了「分享材料包」但**系统没有任何应用能接收 zip** | 不崩（现有 `createChooser` 已有处理），仍然应当走到删除确认路径 |
| E-5 | 导出成功 → 分享 → 回来删学年期间，**用户又新增/删除了记录** | 弹窗显示的条数按**弹窗打开那一刻实时计算**；删除执行时也实时计算，不用导出时的快照 |
| E-6 | 该学年记录被删后**没有剩余学年** | `AcademicYear.yearsOf` 恒含当前目标学年（`LABEL`），chip 列表不会空；选中态靠现有 `LaunchedEffect(years)` 回落，**不得崩且不得停在空学年** |
| E-7 | 照片文件被用户用文件管理器手动删过 | `File.delete()` 返回 false → 忽略，整体仍判成功，只 `Log.w` |
| E-8 | 照片被另一学年记录引用（见 §5.2） | 保留文件，另一记录缩略图正常显示 |
| E-9 | 删除过程中 App 退后台 / 被系统回收 | 删除跑在 ViewModel + 主线程外的 IO 作用域；进程若在事务中被杀，Room 保证原子性；重启后未完成的清理退化为「残留孤儿文件」（无害），可接受 |
| E-10 | 快速连点删除确认 | P1-3：ViewModel 幂等标志位，一次删除进行中忽略后续触发 |
| E-11 | 多选模式下点某行 | 进入/取消勾选，**不跳转详情页**；返回键优先退出选择态 |
| E-12 | 导出后被删学年的 ZIP 仍在磁盘 | 不删（§5.4）；用户点「导出其他学年」→ `resetExport()` 再导出时，`ZipExporter` 会清掉 `outDir` 上一版（现状行为，保持） |
| E-13 | 桌上已添加的「成果概览」组件在升级后 | App 无法系统级移除；组件 Receiver 注销后系统通常显示为无法加载 / 自动失效。用户自行移除即可。**需在 Release Notes 里提示一句** |
| E-14 | 学年 chip 行在 `horizontalScroll` 里，学年很多 | 多选相关控件不放在 chip 行，只放标题行右侧，避免滚动容器内的布局问题 |

---

## 7. 组件与代码清理清单（P0-6 + P1-5 的执行清单）

### 7.1 删除

| 类型 | 路径 | 备注 |
| --- | --- | --- |
| Kotlin | `app/src/main/java/com/zongce/app/widget/AchievementListWidget.kt`（211 行） | 组件主体 |
| Kotlin | ~~`app/src/main/java/com/zongce/app/widget/WidgetYearStore.kt`（63 行）~~ | **不删文件本体**：按 P1-4 下沉改造为 `data/AchievementYearStore.kt`（见 §7.2），仅 `widget/` 下的旧路径消失 |
| Manifest | `AndroidManifest.xml` 中 `.widget.AchievementListWidget` 的 `<receiver>` 声明及其注释块（当前约第 52-66 行） | 含 `intent-filter` 与 `meta-data` |
| XML | `app/src/main/res/xml/jicun_achievement_widget_info.xml` | 组件登记信息 |
| Layout | `app/src/main/res/layout/widget_achievement_list.xml` | 组件 RemoteViews 布局 |
| Layout | `app/src/main/res/layout/widget_preview_achievement.xml` | 组件选择器预览 |
| Drawable | `app/src/main/res/drawable/widget_achievement_bg.xml` | 仅成果组件使用 |
| Drawable | `app/src/main/res/drawable-nodpi/widget_preview_achievement_img.png` | 仅成果组件预览使用 |
| String | `res/values/strings.xml`：`achievement_widget_label`、`achievement_widget_description` | 两条 |

### 7.2 修改（不是删除）

| 位置 | 改动 |
| --- | --- |
| `ui/AppViewModel.kt` | 删 `import ...widget.AchievementListWidget`；删 `refreshWidget()` 及其在 `saveRecord` / `deleteRecord` 里的调用；删 `syncWidgetYear()`；`initialWidgetYear()` 按 P1-4 改造（或简化为直接返回 `AcademicYear.LABEL` 初值）。**⚠️ 保留 `widgetYearRequests` Channel 与 `init{}` 里的单消费者协程**，但**去掉其中的组件推送（`pushUpdate`）**，只保留「写学年偏好存储」这一步 —— 原因见下方「⚠️ 保留学年写入队列」。`WidgetYearStore` 的 import 改为下沉后的 `data.AchievementYearStore` |
| `widget/WidgetYearStore.kt` → `data/AchievementYearStore.kt` | **不是删除，是下沉改造**：从 `widget` 包移到 `data` 包并更名，去组件语义。SharedPreferences 名 `jicun_achievement_widget` 与 key `selected_year` **一字不变**（老用户已选学年不能丢），仍是 `commit()` 同步落盘（对应 P1-4） |
| `ui/AchievementScreen.kt` | 删 chip 点击里的 `vm.syncWidgetYear(year)` 调用；删 `LaunchedEffect(Unit) { vm.initialWidgetYear() }`（或换成 UI 偏好版）；新增多选态（P0-1） |
| `data/PhotoStore.kt` | 类注释里「导出时才映射成规范名」保留；如有提到组件的地方同步改。删除能力建议在此新增 `deleteAll(names: Collection<String>)`（内部去重 + 逐个 delete + 统计失败数），给学年批量删除使用 |
| `data/AwardDao.kt` | 新增批量删除：`@Transaction suspend fun deleteRecordsAndPhotos(recordIds: List<Long>)` —— 具体签名由架构师定，但**必须保证 DB 删除是原子的**。另：`@Delete deleteRecord(record)` 与 `deletePhotosOf(recordId)` 改走 `RecordDeletion` 后**零调用方，已删除**；`deletePhotos(ids)` **保留**（编辑页删照片仍在用），不要顺手删 |
| `ui/ExportScreen.kt` | 「分享材料包」改为经 `ActivityResultLauncher` 发起；发起时 `vm.markShared(year)`；回来时 `vm.consumePendingDeletePrompt()` 决定是否弹窗 |
| `docs/adr/0002-widget-reads-summary.md` | 顶部加 **Deprecated** 说明 + 指向本 PRD（P1-5） |
| `VERSION_HISTORY.md` | 新增 v1.4.0 记录卡；更新版本号 / 已知待处理 |
| `CODE_STRUCTURE.md` | 更新「桌面组件」清单与 `AppViewModel` 职责描述（含「数据变化时推送成果组件」这段要删） |

#### ⚠️ 保留学年写入队列（不要删 `widgetYearRequests` Channel）

`AppViewModel` 里的 `widgetYearRequests` Channel + `init{}` 中的单消费者协程**必须保留**，这不是组件的残留，而是 **v1.3.6 修掉的一个并发坑**：

- **修复前的写法**：每次点击学年 chip 就起一个 `Dispatchers.IO` 协程去做「写盘 + 推送」。
- **坑在哪**：`Dispatchers.IO` 是多线程池，用户快速连点两个学年 chip 时，两次「写盘 + 推送」的**执行顺序没有保证**。坏交错下最终落盘的是**先点的那个学年**，表现就是「我明明选的是最后点的，App 却停在之前那个」。
- **怎么修的**：改用单消费者 Channel 把学年写入**串行化**，按投递顺序逐个执行，才修好。
- **为什么这次不能删**：组件虽然砍掉了（所以队列里的组件推送 `pushUpdate` 要去掉），但**学年偏好仍在写**（下沉后的 `data/AchievementYearStore.kt`）。去掉队列、退回「每次点击起一个 IO 协程」，会让这个坑**原样复发**。

**正确改法**：保留 Channel 与单消费者协程，把队列里的「组件推送」这一步删掉，只留「写学年偏好存储」。串行化语义保持不变。

### 7.3 明确保留（不得误删）

| 对象 | 理由 |
| --- | --- |
| `widget/JicunWidget.kt`（Glance）、`widget/JicunWidgetReceiver.kt` | 「快速录入」= 拍照 / 相册入口，用户明确要求保留 |
| `WidgetActions.kt` | 快速录入组件的 action 路由（`MainActivity` 的 `widgetAction` 依赖它） |
| `res/xml/jicun_widget_info.xml`、`res/layout/widget_preview_entry.xml`、`res/drawable-nodpi/widget_preview_entry_img.png`、`res/drawable/ic_widget_camera.xml`、`ic_widget_gallery.xml` | 同属快速录入组件 |
| `res/drawable/widget_preview_bg.xml`、`widget_preview_tile_container.xml`、`widget_preview_tile_primary.xml`、`res/drawable-xxhdpi/widget_logo.png` | **待确认**：可能被两个组件的预览共用，删除成果组件前先 grep，见 §8-Q5 |
| `AppViewModel.widgetYearRequests` Channel + `init{}` 里的单消费者协程 | v1.3.6 修的并发坑：去掉会让「快速连点学年 chip → 落盘的是先点的学年」原样复发。只删队列里的组件推送，队列本身保留（见 §7.2 末尾） |
| `AwardDao.deletePhotos(ids)` | 编辑页删照片仍在用（`@Delete deleteRecord` / `deletePhotosOf` 已无调用方可以删，这个不行） |
| `UpdateChecker` / `UpdateThrottle` / FileProvider `file_paths.xml`（`exports`、`camera_tmp`） | 与本次无关 |
| `docs/adr/0001-widget-entry-routing.md` | 讲快速录入的入口路由，仍适用 |

---

## 8. 待确认问题（Open Questions）

| # | 问题 | PM 的默认建议 | 需谁定 |
| --- | --- | --- | --- |
| Q1 | 删除执行期间要不要给 loading / 阻断式进度？照片很多时（数百 MB）耗时可能上秒 | 默认给按钮 loading + 禁用重复点击（P1-3 已包含），不做全屏进度条 | 架构师 |
| Q2 | 学年删除口径用严格 `belongsTo` 还是与导出同源的 `targetItems`？ | **建议 `belongsTo`**（§5.3 已说明，导出成功场景下两者等价，但严格口径更安全） | 架构师 / 用户 |
| Q3 | 要不要给「撤销」？哪怕是 5 秒内的 SnackBar Undo | **不做**（P2-1）。产品口径已明确「删除后无法恢复」，做 Undo 会让弹窗文案与承诺自相矛盾 | 待用户确认 |
| Q4 | 组件移除后，成果页「记住上次选的学年」还留不留？ | **留**（P1-4：把 `WidgetYearStore` 改造为学生成果页自己的 UI 偏好；老键名 `jicun_achievement_widget` 保持兼容，老用户升级后不丢） | 架构师 |
| Q5 | `widget_logo` / `widget_preview_*` 几个资源是否被快速录入组件预览引用着？ | 删除前先 grep 确认；被引用的保留 | 开发执行时确认 |
| Q6 | 多选的选择态放 Composable `rememberSaveable` 还是提到 ViewModel？ | 倾向 ViewModel（`SavedStateHandle` 兜底），避免旋转丢选择；由架构师按现有状态管理风格定 | 架构师 |
| Q7 | 版本号定为 **1.4.0**（versionCode `1000013`）是否认可？若认为「移除桌面组件」属于破坏性变更，是否走 **2.0.0**？ | 建议 **1.4.0** —— 按 `VERSION_HISTORY.md` 既有约定（次版本 = 较大用户流程），砍一个鲜有人用的桌面组件不构成产品方向变化 | 用户 / team-lead |
| Q8 | 除「导出→分享」这条路径外，是否允许跳过分享直接删学年？ | 不建议。保持单一路径（导出 → 分享 → 回来 → 问删），避免用户误以为已发就删 | team-lead |
| Q9 | 是否需要补齐删除相关的 JVM 单测（尤其 §5.2 的引用计数陷阱）？ | **建议有**，至少覆盖「照片被跨学年引用时文件不删」这一条；现有 `UpdateThrottleTest` 的纯函数测试风格可作为模板 | 架构师 |

---

## 9. 不在本次范围内（显式划界）

- 照片云同步 / 备份 / 多设备：仍遵循 `allowBackup=false` 的既有决策。
- Room 数据库 schema 变更：本次删除不需要新字段，**不得触发迁移**（schema 仍为 version 1，保持不动）。
- 快速录入组件的任何形态调整（尺寸、图标、样式）。
- 应用内更新机制（`UpdateChecker` / `UpdateThrottle`，v1.3.5 / v1.3.6 成果，不动）。
- 仅清理这次组件移除产生的 `build/` 临时目录，不动 `.gitignore` / CI 配置。
