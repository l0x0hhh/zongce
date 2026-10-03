# Step 1 · AppViewModel 拆分清单（Record / Export / Update）

> 对应方案：`docs/design/architecture-refactor-step1-vm-split.md` 之前确定的「轻量分层演进」方案 A。
> 本文档只规划 **Step 1（拆 VM，纯重构）**：不引入新依赖、不改任何业务逻辑、不改变任何用户可见行为。
> 状态：**✅ 已实施（2026-09-24）**。`assembleDebug` + `testDebugUnitTest` 全绿；与计划的唯一偏差见 §5 注。

## 1. 目标与原则

把当前约 670 行的 `AppViewModel`（一拖五：记录流 / 照片导入 / 多选删除 / 导出状态机 / 更新）拆成三个按功能域归属的 ViewModel：

| ViewModel | 职责域 |
| --- | --- |
| `RecordViewModel` | 记录流、照片、多选态、删除（含删除询问闸门）、学年偏好 |
| `ExportViewModel` | 导出状态机（体检 → 确认 → 打包 → 完成/失败） |
| `UpdateViewModel` | 更新检查、下载、节流、手动/静默并发保护 |

三条硬原则：

1. **行为不变**：任何函数语义、状态流转、文案、时序都不许改，只搬位置。
2. **单域归属**：一个状态/函数只属于一个 VM，不跨 VM 共享可变状态。
3. **薄屏不变**：Screen 仍只做「collect 状态 + 转发点击」，跨 VM 的接线一律放在 `MainActivity`（组合根）。

## 2. 现状清单：AppViewModel 公开 API 全量与调用点

| 成员 | 类型 / 签名 | 调用点 | 去向 |
| --- | --- | --- | --- |
| `items` | `StateFlow<List<RecordWithPhotos>>` | MainActivity(→CaptureScreen)、AchievementScreen、ListScreen、ExportScreen(经 MainActivity 传参) | **Record** |
| `pendingUris` | `StateFlow<List<Uri>>` | EntryScreen | **Record** |
| `exportState` | `StateFlow<ExportState>` | ExportScreen | **Export** |
| `updateState` | `StateFlow<UpdateUiState>` | MainActivity → UpdateDialog | **Update** |
| `selectionMode` | `StateFlow<Boolean>` | AchievementScreen | **Record** |
| `selectedIds` | `StateFlow<Set<Long>>` | AchievementScreen | **Record** |
| `deleting` | `StateFlow<Boolean>` | AchievementScreen、MainActivity → YearDeleteDialog | **Record** |
| `deleteMessages` | `SharedFlow<String>` | MainActivity（Toast） | **Record** |
| `yearDeletePrompt` | `StateFlow<YearDeletePrompt?>` | MainActivity → YearDeleteDialog | **Record** |
| `previewFileName(record)` | `fun (AwardRecord) -> String` | **无调用点（死代码）** | 建议删除（见 D4） |
| `photoFile(fileName)` | `fun (String) -> File` | EntryScreen、ListScreen | **Record** |
| `setPendingUris(uris)` | `fun (List<Uri>)` | CaptureScreen | **Record** |
| `clearPending()` | `fun ()` | MainActivity（onAddRecord）、EntryScreen | **Record** |
| `loadRecord(id)` | `suspend (Long) -> RecordWithPhotos?` | EntryScreen | **Record** |
| `saveRecord(record, newUris, removedIds, onSaved)` | `fun (AwardRecord, List<Uri>, List<Long>, (List<ImportFailure>) -> Unit)` | EntryScreen | **Record** |
| `enterSelectionMode()` / `exitSelectionMode()` / `clearSelection()` | `fun ()` | AchievementScreen | **Record** |
| `toggleSelected(id)` | `fun (Long)` | AchievementScreen | **Record** |
| `selectAll(ids)` | `fun (List<Long>)` | AchievementScreen | **Record** |
| `deleteRecord(item)` | `fun (RecordWithPhotos)` | ListScreen | **Record** |
| `deleteSelected(ids)` | `fun (List<Long>)` | AchievementScreen | **Record** |
| `deleteYear(year)` | `fun (String)` | MainActivity → YearDeleteDialog（confirm） | **Record** |
| `saveAchievementYear(year)` | `fun (String)` | AchievementScreen | **Record** |
| `initialAchievementYear()` | `suspend () -> String` | AchievementScreen | **Record** |
| `markShared(year)` | `fun (String)` | ExportScreen（点分享时） | **Record**（闸门归属，见 D1） |
| `onReturnedFromShare()` | `fun ()` | ExportScreen（launcher 回调）、MainActivity（ON_RESUME 兜底） | **Record** |
| `confirmYearDelete()` | `fun ()` | MainActivity → YearDeleteDialog | **Record** |
| `dismissYearDelete()` | `fun ()` | MainActivity → YearDeleteDialog | **Record** |
| `resetExport()` | `fun ()` | ExportScreen（四处：Blocked/Confirm 返回、Done 导出其他学年、Error 重新开始） | **Export**，但需拆动作（见 D2） |
| `checkBeforeExport(list, targetYear)` | `fun (List<RecordWithPhotos>, String)` | ExportScreen | **Export** |
| `confirmExport()` | `fun ()` | ExportScreen | **Export** |
| `checkForUpdate()` | `fun ()` | MainActivity → UpdateDialog（onRetry） | **Update** |
| `autoCheckForUpdate()` | `fun ()` | MainActivity（LaunchedEffect(Unit)） | **Update** |
| `downloadUpdate(info)` | `fun (UpdateInfo)` | MainActivity → UpdateDialog | **Update** |
| `resetUpdate()` | `fun ()` | MainActivity → UpdateDialog | **Update** |

内部成员归属（不对外，随 VM 搬移）：

| 内部成员 | 去向 |
| --- | --- |
| `dao`（AppDatabase.get）、`photoStore`、`deletion`(RecordDeletion) | **Record** |
| `achievementYearRequests`(Channel) + init 消费者 | **Record** |
| `yearDeleteGate`(YearDeletePromptGate) + `launchYearPrompt` | **Record** |
| `reportDeletion` / `reportDeletionFailure` / `finishDeletion` / `DELETE_FAILED_MESSAGE` / `DELETION_TAG` | **Record** |
| `startExport` | **Export** |
| `silentCheckRunning` / `manualCheckEpoch` / `runUpdateCheck` / `isUpdateBusy` / `today()` / `TAG` | **Update** |

## 3. 目标结构

### 3.1 RecordViewModel（`ui/RecordViewModel.kt`，extends AndroidViewModel）

```kotlin
class RecordViewModel(app: Application) : AndroidViewModel(app)
```

- 状态：`items`、`pendingUris`、`selectionMode`、`selectedIds`、`deleting`、`deleteMessages`、`yearDeletePrompt`
- 函数：`photoFile`、`setPendingUris`、`clearPending`、`loadRecord`、`saveRecord`、`enterSelectionMode`、`exitSelectionMode`、`clearSelection`、`toggleSelected`、`selectAll`、`deleteRecord`、`deleteSelected`、`deleteYear`、`saveAchievementYear`、`initialAchievementYear`、`markShared`、`onReturnedFromShare`、`confirmYearDelete`、`dismissYearDelete`、**新增** `resetYearDeletePrompt()`
- 依赖：`AppDatabase`(dao)、`PhotoStore`、`RecordDeletion`、`YearDeletePromptGate`、`AchievementYearStore`、`AcademicYear`
- 文件内顶层类型：`YearDeletePrompt`、`ImportFailure`

### 3.2 ExportViewModel（`ui/ExportViewModel.kt`，extends AndroidViewModel）

```kotlin
class ExportViewModel(app: Application) : AndroidViewModel(app)
```

- 状态：`exportState`
- 函数：`checkBeforeExport`、`confirmExport`、`resetExport`（仅清导出状态）
- 依赖：`ExportCheck`、`ZipExporter`
- 文件内顶层类型：`ExportState`

### 3.3 UpdateViewModel（`ui/UpdateViewModel.kt`，extends AndroidViewModel）

```kotlin
class UpdateViewModel(app: Application) : AndroidViewModel(app)
```

- 状态：`updateState`
- 函数：`checkForUpdate`、`autoCheckForUpdate`、`downloadUpdate`、`resetUpdate`
- 依赖：`UpdateChecker`、`UpdateThrottle`；`UpdateUiState` 定义**留在** `UpdateDialog.kt`（D3）

### 3.4 类型归属

| 类型 | 现状 | 去向 |
| --- | --- | --- |
| `ExportState` | `ui/AppViewModel.kt` 顶层 | 随 **ExportViewModel.kt** 搬移 |
| `YearDeletePrompt` | `ui/AppViewModel.kt` 顶层 | 随 **RecordViewModel.kt** 搬移 |
| `ImportFailure` | `ui/AppViewModel.kt` 顶层 | 随 **RecordViewModel.kt** 搬移 |
| `UpdateUiState` | `ui/UpdateDialog.kt` | **不动**（UI 状态跟随弹窗，UpdateViewModel 引用即可） |

## 4. 关键设计决定

### D1：删除询问闸门（gate + prompt + deleteYear）整体归 RecordViewModel

`YearDeletePromptGate`、`yearDeletePrompt`、`confirmYearDelete`、`dismissYearDelete`、`deleteYear` 是一个内聚的「删除确认」生命周期，必须同处一个 VM——拆散会让闸门状态分裂（这正是 v1.4.0 之前出竞态的重灾区）。代价：ExportScreen 在「点分享」时通过回调触发闸门，见 §5。

### D2：`resetExport()` 拆成两个动作，由 MainActivity 组合

现状 `resetExport()` 同时清三样：导出状态、`_yearDeletePrompt`、`yearDeleteGate.reset()`。拆分后：

- `ExportViewModel.resetExport()` 只清 `exportState`
- `RecordViewModel.resetYearDeletePrompt()`（新增，内容 = 现状的 `_yearDeletePrompt.value = null; yearDeleteGate.reset()`）
- ExportScreen 的四处 `vm.resetExport()` 全部替换为回调 `onResetExport = { exportVm.resetExport(); recordVm.resetYearDeletePrompt() }`，行为与现状完全一致。

### D3：`UpdateUiState` 留在 UpdateDialog.kt

该类型只被弹窗渲染使用，随弹窗文件存放，UpdateViewModel 引用它；避免一次无收益的搬移。

### D4：`previewFileName` 是死代码

全项目 grep 无任何调用点。**建议直接删除**（0 行为影响）；若想保留，随 RecordViewModel 搬移。实施前需用户确认。

## 5. 文件改动清单

| 文件 | 动作 | 内容 |
| --- | --- | --- |
| `ui/AppViewModel.kt` | **删除** | 拆为下面三个文件 |
| `ui/RecordViewModel.kt` | **新建** | §3.1 全量（含 `YearDeletePrompt`、`ImportFailure`） |
| `ui/ExportViewModel.kt` | **新建** | §3.2 全量（含 `ExportState`） |
| `ui/UpdateViewModel.kt` | **新建** | §3.3 全量 |
| `MainActivity.kt` | 修改 | 组合根接线（见 §6） |
| `ui/CaptureScreen.kt` | 修改 | 签名 `vm: AppViewModel` → `vm: RecordViewModel` |
| `ui/EntryScreen.kt` | 修改 | 同上 |
| `ui/ListScreen.kt` | 修改 | 同上 |
| `ui/AchievementScreen.kt` | 修改 | 同上 |
| `ui/ExportScreen.kt` | 修改 | 签名改为 `(vm: ExportViewModel, items: List<RecordWithPhotos>, onShareYear: (String) -> Unit, onShareReturned: () -> Unit, onResetExport: () -> Unit)`；`vm.markShared(y)` → `onShareYear(y)`；launcher 回调 `vm.onReturnedFromShare()` → `onShareReturned()`；四处 `vm.resetExport()` → `onResetExport()` |
| `UpdateDialog.kt` / `YearDeleteDialog.kt` | **不动** | 类型与签名均无变化 |

> **实施偏差注**：§5 原表漏了分享 launcher 的返回回调——`onReturnedFromShare()` 属于删除域（闸门在 RecordViewModel），ExportScreen 不能直接调 ExportViewModel。按 D1 的精神补了第 4 个回调参数 `onShareReturned`，由 MainActivity 接 `recordVm::onReturnedFromShare`。行为与拆分前完全一致（同一幂等消费函数，两条路径各弹一次且仅一次）。

## 6. MainActivity 接线对照（组合根）

```kotlin
val recordVm: RecordViewModel = viewModel()
val exportVm: ExportViewModel = viewModel()
val updateVm: UpdateViewModel = viewModel()

val items by recordVm.items.collectAsState()          // 原 vm.items
val updateState by updateVm.updateState.collectAsState()

LaunchedEffect(Unit) { updateVm.autoCheckForUpdate() }        // 原 vm.autoCheckForUpdate()
LaunchedEffect(Unit) { recordVm.deleteMessages.collect { ... } } // 原 vm.deleteMessages
DisposableEffect(...) { ... recordVm.onReturnedFromShare() ... } // 原 vm.onReturnedFromShare

CaptureScreen(vm = recordVm, ...)
AchievementScreen(items = items, vm = recordVm, ...)
EntryScreen(vm = recordVm, ...)
ListScreen(items = items, vm = recordVm, ...)
ExportScreen(
    vm = exportVm,
    items = items,
    onShareYear = recordVm::markShared,
    onShareReturned = recordVm::onReturnedFromShare,
    onResetExport = { exportVm.resetExport(); recordVm.resetYearDeletePrompt() }
)
UpdateDialog(state = updateState, onDownload = updateVm::downloadUpdate,
             onInstall = ..., onDismiss = updateVm::resetUpdate, onRetry = updateVm::checkForUpdate)
YearDeleteDialog(prompt = recordVm.yearDeletePrompt.collectAsState().value,   // 由 App() 收集后传入
                 deleting = recordVm.deleting.collectAsState().value,
                 onConfirm = recordVm::confirmYearDelete,
                 onDismiss = recordVm::dismissYearDelete)
```

## 7. 验证清单

1. 编译：`.\gradlew.bat :app:assembleDebug --no-daemon`
2. 单测：`.\gradlew.bat :app:testDebugUnitTest --no-daemon` —— 现有 97 个用例不涉及 VM，应全绿（用于排除误伤）
3. 真机回归（删除/导出链路是历史 bug 重灾区，拆分中不得退步）：
   - 录入/编辑/保存/移除照片 → 确认 `files/photos` 中孤儿文件清理行为不变
   - 成果页多选删除（含跨学年共用照片保护）
   - 导出全流程：体检 → 提醒确认 → 打包 → 分享
   - 分享后「删除这一学年」：真分享 / 取消 / 按 Home 回来三种路径各弹一次且仅一次；点「保留」后再分享不追问；失败可重试
   - 更新：手动检查 / 启动静默检查 / 下载 / 安装入口
   - 小组件两个入口（拍照 / 相册）仍落到录入页

## 8. 后续步骤预告（不在本步实施）

- **Step 2 · Repository**：新增 `AwardRepository` 收口 `AwardDao` + `PhotoStore`（记录+照片是一个聚合），`RecordDeletion` 注入端口改指向 Repository；Export/Update VM 不受影响。
- **Step 3 · 控制器 + 测试**：把「保存+照片生命周期」「导出状态机」抽成纯 Kotlin 控制器（沿用 `RecordDeletion` / `YearDeletePromptGate` 范式）并补 JVM 单测；顺带实现规则外置（`ZongceRules` + `assets/rules.json` + 默认值回退）。
