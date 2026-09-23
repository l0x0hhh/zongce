// 编排记录保存、照片引用生命周期和导出状态反馈。
package com.zongce.app.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zongce.app.core.AcademicYear
import com.zongce.app.core.FileNameRule
import com.zongce.app.data.AchievementYearStore
import com.zongce.app.data.AppDatabase
import com.zongce.app.data.AwardPhoto
import com.zongce.app.data.AwardRecord
import com.zongce.app.data.PhotoStore
import com.zongce.app.data.RecordDeletion
import com.zongce.app.data.RecordWithPhotos
import com.zongce.app.data.YearDeletePromptGate
import com.zongce.app.export.ExportCheck
import com.zongce.app.export.ExportResult
import com.zongce.app.export.ZipExporter
import com.zongce.app.update.UpdateCheckResult
import com.zongce.app.update.UpdateChecker
import com.zongce.app.update.UpdateInfo
import com.zongce.app.update.UpdateThrottle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

sealed class ExportState {
    object Idle : ExportState()
    data class Blocked(val issues: List<ExportCheck.Issue>) : ExportState()

    /**
     * 没有阻断项、但有提醒项：先让用户过目，确认后才打包。
     * [items] 就是本次要打包的记录范围，随状态一起流转 —— 不再另存裸字段，
     * 从根本上消除"处于 Confirm 态却拿不到范围 → 点继续导出没反应"的可能。
     */
    data class Confirm(
        val items: List<RecordWithPhotos>,
        val targetYear: String,
        val issues: List<ExportCheck.Issue>
    ) : ExportState()
    data class Exporting(val done: Int, val total: Int) : ExportState()

    /**
     * [targetYear] 随结果一起流转：导出后「要不要删掉这一学年」的询问要用到它，
     * 不能让 UI 自己记住学年（导出页本地的 `targetYear` 会被 `LaunchedEffect(availableYears)`
     * 的回落改掉，于是弹窗问的学年和实际导出的学年不是同一个）。
     */
    data class Done(val result: ExportResult, val targetYear: String) : ExportState()
    data class Error(val message: String) : ExportState()
}

data class ImportFailure(val uri: Uri, val message: String)

/**
 * 导出后「删除这一学年？」的询问内容。
 * 数字在弹出那一刻实时算出来 —— 用户可能在导出生效后又删了几条，展示旧数字会误导。
 */
data class YearDeletePrompt(
    val year: String,
    val recordCount: Int,
    val photoCount: Int
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.get(app).awardDao()
    private val photoStore = PhotoStore(app)

    /**
     * 删除的唯一入口。单条 / 多选 / 整个学年三条路径都只经过它 ——
     * 「先删库、再按删除后的库态重查引用、只删 0 引用文件」这条顺序铁律由它独占实现，
     * 任何地方都不许再手写 `if (photoReferenceCount(...) <= 1) photoStore.delete(...)`。
     */
    private val deletion = RecordDeletion(
        deleteRows = { ids -> dao.deleteRecordsAndPhotos(ids) },
        referenceCount = { name -> dao.photoReferenceCount(name) },
        deleteFiles = { names -> photoStore.deleteAll(names) }
    )

    /** 全部记录（含照片），按获奖时间升序 */
    val items: StateFlow<List<RecordWithPhotos>> = dao.allWithPhotos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 拍照/选图后暂存的 uri，进录入页 */
    private val _pendingUris = MutableStateFlow<List<Uri>>(emptyList())
    val pendingUris: StateFlow<List<Uri>> = _pendingUris.asStateFlow()

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    private val _updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

    /**
     * 是否有一次"静默自动检查"正在后台进行。
     *
     * 手动检查会立刻把状态置成 Checking，靠状态就能判出来；而自动检查是静默的、
     * 不改状态，所以必须单独记一个标志位，用来挡住"自动检查还没跑完又被触发一次" ——
     * 例如旋转屏幕导致 Activity 重建、App 重新组合，LaunchedEffect(Unit) 会再触发一次。
     * 全部在主线程读写（调用方都在主线程），无需额外同步。
     */
    private var silentCheckRunning = false

    /**
     * 每次"手动检查"开始就自增。
     *
     * 静默检查开始时会记下它的值，完成时若发现已经变了，说明这段静默结果已被用户的手动
     * 操作取代（用户查过了、甚至已经把弹窗点掉了），必须作废——否则晚到的静默结果会把
     * 用户刚关掉的弹窗又弹回来（像是"关不掉"），或覆盖掉手动检查正在显示的结果。
     * 全靠主线程读写（调用方都在主线程），无需额外同步。
     */
    private var manualCheckEpoch = 0

    /** 最近一次导出预览字段名（导出前过目一眼——文件名是公开可读的） */
    fun previewFileName(record: AwardRecord): String =
        FileNameRule.build(record.awardDate, record.awardName, record.grade) + ".jpg"

    /**
     * 取某张照片的原图文件，给 UI 显示缩略图用。
     * UI 一律走这里 —— Composable 不要再自己构造 PhotoStore（那会绕过 ViewModel
     * 并拿 Activity Context 去建数据层对象）。
     */
    fun photoFile(fileName: String): File = photoStore.photoFile(fileName)

    fun setPendingUris(uris: List<Uri>) {
        _pendingUris.value = uris
    }

    fun clearPending() {
        _pendingUris.value = emptyList()
    }


    suspend fun loadRecord(id: Long): RecordWithPhotos? = dao.byId(id)

    /**
     * 保存一条记录。
     * @param newUris 本次新拍/新选的照片
     * @param removedIds 本次被删除的旧照片 id
     */
    fun saveRecord(
        record: AwardRecord,
        newUris: List<Uri>,
        removedIds: List<Long>,
        onSaved: (List<ImportFailure>) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val failures = mutableListOf<ImportFailure>()
            val id = if (record.id == 0L) {
                dao.insertRecord(record.copy(updatedAt = System.currentTimeMillis()))
            } else {
                dao.updateRecord(record.copy(updatedAt = System.currentTimeMillis()))
                record.id
            }

            // 移除旧照片也守同一条顺序，但两件事的先后不能搞混：
            //   · fileName 必须在 DELETE **之前**抓住 —— 它是"删行之前的事实"，
            //     放到删行之后就得从一条刚被删过的缓存里反查，取不取得到全看 Room 的
            //     失效通知有没有先到（竞态），取不到就是孤儿文件永久泄漏；
            //   · photoReferenceCount 才必须在 DELETE **之后**重查 ——
            //     它是"删行之后的状态"，用旧快照数会误删仍被引用的照片。
            // 至于"先删行、后删文件"：DB 一旦失败就变成"行还在、文件没了"的永久缺图，
            // 反过来最坏只是残留孤儿文件，无害。
            if (removedIds.isNotEmpty()) {
                val names = removedIds.mapNotNull { dao.photoFileName(it) }.toSet()
                dao.deletePhotos(removedIds)
                val orphan = names.filter { dao.photoReferenceCount(it) == 0 }
                photoStore.deleteAll(orphan)
            }

            if (newUris.isNotEmpty()) {
                val photos = newUris.mapNotNull { uri ->
                    try {
                        val (fileName, takenAt) = photoStore.import(getApplication(), uri)
                        AwardPhoto(recordId = id, fileName = fileName, takenAt = takenAt)
                    } catch (e: Exception) {
                        failures += ImportFailure(uri, e.message ?: "无法读取照片")
                        null
                    }
                }
                if (photos.isNotEmpty()) dao.insertPhotos(photos)
            }

            withContext(Dispatchers.Main) { onSaved(failures) }
        }
    }

    // ---------- 多选态（成果页，P0-1）----------
    //
    // 放在 ViewModel 而不是 rememberSaveable：RecordWithPhotos 不可序列化，
    // 进 SavedStateHandle 还要额外转换；而「删除进行中」要能跨重组存活并把按钮置成 loading，
    // 这个状态本来就得在 VM 里。

    private val _selectionMode = MutableStateFlow(false)
    val selectionMode: StateFlow<Boolean> = _selectionMode.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    /** 删除执行中：确认按钮转圈 + 禁用（不做全屏进度条）。 */
    private val _deleting = MutableStateFlow(false)
    val deleting: StateFlow<Boolean> = _deleting.asStateFlow()

    /**
     * 删除结果提示（Toast 文案）。
     * extraBufferCapacity=1 + replay=0：best-effort，UI 不在前台就丢，绝不堆积补播 ——
     * 用户回到 App 时看到三条"已删除"的历史 Toast 比没有反馈更糟。
     */
    private val _deleteMessages = MutableSharedFlow<String>(extraBufferCapacity = 1, replay = 0)
    val deleteMessages: SharedFlow<String> = _deleteMessages.asSharedFlow()

    /**
     * 删除幂等标志位（P1-3）。
     * 连点「删除」时只放行第一次：删除是破坏性操作，重复执行没有第二次的意义，
     * 而第二次执行时选择集可能已经被第一次的 Flow 刷新清空，反而会删错范围。
     * 与 silentCheckRunning 同理，全部在主线程读写（调用方都是 UI 点击）。
     */
    @Volatile
    private var deleteRunning = false

    /** 进入多选模式（标题行「选择」）。进入即清空上一轮选择集。 */
    fun enterSelectionMode() {
        _selectedIds.value = emptySet()
        _selectionMode.value = true
    }

    /** 退出多选模式（标题行「取消」/ 返回键 / 删除成功后）。 */
    fun exitSelectionMode() {
        _selectedIds.value = emptySet()
        _selectionMode.value = false
    }

    /** 切学年时调用：选择集的意义是"当前学年里的几条"，跨学年留着会让人误删。 */
    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun toggleSelected(id: Long) {
        val current = _selectedIds.value
        _selectedIds.value = if (id in current) current - id else current + id
    }

    /** 全选：scope 由 UI 传入（当前学年可见列表），VM 不猜范围。 */
    fun selectAll(ids: List<Long>) {
        _selectedIds.value = ids.toSet()
    }

    /**
     * 删除一条记录。走 RecordDeletion 的唯一管线，与多选、整学年删除同一套顺序语义。
     *
     * 旧实现是「先删文件、后删库」：DB 一旦失败，文件已经没了而记录还在 —— 永久缺图、不可逆。
     * 现在反过来，最坏结果只是残留几个孤儿文件（无害），绝不会丢照片。
     */
    fun deleteRecord(item: RecordWithPhotos) {
        if (deleteRunning) return
        deleteRunning = true
        _deleting.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 与另两条路径一致：执行这一刻重新查库。用 UI 传来的快照算照片数的话，
                // 用户在这条记录上刚加过照片而列表还没刷新时，Toast 会少报。
                val fresh = dao.byId(item.record.id) ?: item
                reportDeletion(deletion.delete(listOf(fresh)))
            } catch (e: Exception) {
                reportDeletionFailure(e)
            } finally {
                finishDeletion()
            }
        }
    }

    /**
     * 批量删除（成果页多选）。幂等：执行中再触发直接 return。
     *
     * @param ids 本次要删的记录 id（UI 传当前学年可见范围里被选中的那些）
     */
    fun deleteSelected(ids: List<Long>) {
        if (deleteRunning || ids.isEmpty()) return
        deleteRunning = true
        _deleting.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 作用域在执行这一刻重新查库：界面传来的列表快照可能已经过期，
                // 拿过期快照算照片数会让"将删除 M 张照片"和实际删掉的不一致。
                val all = dao.allWithPhotos().first()
                val idSet = ids.toSet()
                val records = all.filter { it.record.id in idSet }
                reportDeletion(deletion.delete(records))
                exitSelectionMode()
            } catch (e: Exception) {
                reportDeletionFailure(e)
            } finally {
                finishDeletion()
            }
        }
    }

    /**
     * 删除整个学年（导出后确认）。
     * 作用域**在执行那一刻**按 AcademicYear.inYear 实时算，不用导出时的快照 ——
     * 导出到确认之间用户可能又删了几条，按快照删会多删或提示错误的条数。
     */
    fun deleteYear(year: String) {
        if (deleteRunning) return
        deleteRunning = true
        _deleting.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val all = dao.allWithPhotos().first()
                val records = AcademicYear.inYear(all, year) { it.record.awardDate }
                // 弹窗弹出到用户点确认之间，这一学年可能已经被删空了（例如刚才那条
                // 多选删除正好覆盖了它）。此时一条"已删除 0 条记录"的 Toast 只会让人
                // 以为刚才删掉了什么 —— 什么都不做才是对的。
                if (records.isEmpty()) {
                    Log.w(DELETION_TAG, "学年 $year 已无记录可删，跳过本次删除")
                    return@launch
                }
                reportDeletion(deletion.delete(records))
            } catch (e: Exception) {
                reportDeletionFailure(e)
                // 失败时放开闸门让用户能再试一次。闸门自己会把三个状态全部清干净，
                // 所以下一次询问仍要"重新分享并回来"才触发 —— 不会追着用户弹。
                yearDeleteGate.allowRetry()
            } finally {
                // 学年删除成功后**不重置导出页**：完成页仍展示本次导出结果，
                // 用户还能再点一次分享或点「导出其他学年」。只清 pending 与弹窗。
                _yearDeletePrompt.value = null
                yearDeleteGate.dismiss()
                finishDeletion()
            }
        }
    }

    /** 删除成功的用户可见文案；个别文件删除失败不影响成功文案，只留一条日志。 */
    private fun reportDeletion(outcome: RecordDeletion.Outcome) {
        if (outcome.failedFiles.isNotEmpty()) {
            Log.w(DELETION_TAG, "照片文件删除失败（记录已删除，残留孤儿文件）：${outcome.failedFiles}")
        }
        // M 用「随记录一起删掉的 photo 行数」：它与被删记录一一对应，用户能自己验算；
        // 实际删掉的文件数（会因跨学年共用而更少）只进日志。
        _deleteMessages.tryEmit("已删除 ${outcome.recordCount} 条记录 · ${outcome.photoRowCount} 张照片")
    }

    private fun reportDeletionFailure(error: Exception) {
        Log.w(DELETION_TAG, "删除失败，数据未改动：${error.message}")
        _deleteMessages.tryEmit(DELETE_FAILED_MESSAGE)
    }

    private fun finishDeletion() {
        _deleting.value = false
        deleteRunning = false
    }

    /**
     * 学年偏好写入队列。
     *
     * 为什么需要它，而不是每次点击直接起一个 IO 协程：Dispatchers.IO 是多线程池，
     * 连续点两个学年 chip 时，两次「写盘」的执行顺序没有保证。坏交错下后点的先执行、
     * 先点的后执行，最终落盘的反而是先点的那个 ——
     * 表现为「我明明选的是最后点的学年，重开 App 却回到之前那个」（v1.3.6 修过的并发坑）。
     *
     * 单消费者队列把写入串行化，且严格按入队顺序（= 点击顺序）执行，
     * 保证最后一次点击的最后生效。UNLIMITED 容量避免连点时溢出丢事件。
     */
    private val achievementYearRequests = Channel<String>(Channel.UNLIMITED)

    init {
        // 队列的唯一消费者：挂在 viewModelScope 下，随 ViewModel 销毁而取消，
        // 不需要另外管理生命周期。receive() 在队列空时挂起，取消时抛
        // CancellationException 正常退出。
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                val year = achievementYearRequests.receive()
                AchievementYearStore.set(getApplication(), year)
            }
        }
    }

    /**
     * 成果页选中了学年：写进成果页自己的学年偏好（杀 App 重开仍停在同一个学年）。
     * 只负责入队，实际写盘由上面的消费者在 IO 线程串行完成。
     */
    fun saveAchievementYear(year: String) {
        achievementYearRequests.trySend(year)
    }

    /**
     * 成果页的初始学年：查一次库算学年列表，再读偏好做回落。
     * 不能拿 UI 已有的 items 来算 years（StateFlow 首帧是空列表，
     * yearsOf 又永远包含当前学年），否则存储里的学年会被误判成"已不存在"而错误回落。
     */
    suspend fun initialAchievementYear(): String = withContext(Dispatchers.IO) {
        val items = dao.allWithPhotos().first()
        val years = AcademicYear.yearsOf(items.map { it.record.awardDate })
        AchievementYearStore.current(getApplication(), years)
    }

    // ---------- 导出后删除的 pending 状态机（P0-4 / P0-5）----------

    private val _yearDeletePrompt = MutableStateFlow<YearDeletePrompt?>(null)
    val yearDeletePrompt: StateFlow<YearDeletePrompt?> = _yearDeletePrompt.asStateFlow()

    /**
     * 闸门本身是纯 Kotlin 的（见 [YearDeletePromptGate]）：它只管"该不该问"，
     * 三个状态**只活在内存里**，绝不进 SharedPreferences / SavedStateHandle。
     * 这是刻意的：删除是不可逆动作，"没得到用户明确确认就删"是绝对不能犯的错。
     * 代价是进程被杀（用户在分享面板里划掉 App）后冷启动不弹不删 ——
     * 少一次便利，换一条硬保证。
     */
    private val yearDeleteGate = YearDeletePromptGate { year -> launchYearPrompt(year) }

    /** 点「分享材料包」时调用：只记内存，是否真问由闸门判断。 */
    fun markShared(year: String) {
        yearDeleteGate.markShared(year)
    }

    /**
     * ActivityResultLauncher 回调 与 ON_RESUME 兜底**共用这一个函数**。
     *
     * 为什么两条路径都要：部分 ROM 的 chooser 不回填 ActivityResult，只用 launcher
     * 就永远不弹；只用生命周期又无法区分"分享前的一次普通 resume"。
     * 二者必须调同一个幂等函数，否则会各弹一次。
     */
    fun onReturnedFromShare() {
        // 删除进行中时不问：此刻没有可问的东西，问了也只会和正在跑的删除打架。
        if (deleteRunning) return
        yearDeleteGate.onReturnedFromShare()
    }

    /**
     * 闸门放行后：查库算这一学年的条数与照片数，再落到 [_yearDeletePrompt]。
     * 数字在弹出这一刻实时算 —— 用户在导出生效后可能又删了几条，展示旧数字会误导。
     */
    private fun launchYearPrompt(year: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val all = runCatching { dao.allWithPhotos().first() }.getOrNull() ?: return@launch
            val records = AcademicYear.inYear(all, year) { it.record.awardDate }
            if (records.isEmpty()) {
                // 该学年已经没有记录了（用户在分享前就删干净了）：没有可问的东西。
                yearDeleteGate.dismiss()
                return@launch
            }
            _yearDeletePrompt.value =
                YearDeletePrompt(year, records.size, records.sumOf { it.photos.size })
        }
    }

    fun confirmYearDelete() {
        val year = _yearDeletePrompt.value?.year ?: return
        // 弹窗保留在屏幕上：删除期间确认按钮转圈并禁用，删完由 deleteYear 统一收掉。
        deleteYear(year)
    }

    fun dismissYearDelete() {
        _yearDeletePrompt.value = null
        yearDeleteGate.dismiss()
    }

    // ---------- 导出（英雄时刻） ----------

    /**
     * 回到初始态。Confirm 态的导出范围本来就在状态里，清状态即清范围，
     * 不存在需要额外清掉的裸字段。
     *
     * 「导出其他学年」走这里，所以 pending 三字段也一并清 ——
     * 否则换了个学年还会拿旧学年去问"要不要删"。
     */
    fun resetExport() {
        _exportState.value = ExportState.Idle
        _yearDeletePrompt.value = null
        yearDeleteGate.reset()
    }

    /**
     * 手动"检查更新"：照常显示 Checking、照常提示"已经是最新版本"、失败照常报错。
     * 不受"每天最多自动检查一次"的限制 —— 用户主动点，就该立刻查。
     */
    fun checkForUpdate() {
        // 只挡"正在显示的手动检查/下载"。若此刻只是后台静默自动检查在跑，让用户这次
        // 手动操作照常进行 —— 不要因为一次静默检查把用户主动点的按钮吞掉。
        if (_updateState.value is UpdateUiState.Checking ||
            _updateState.value is UpdateUiState.Downloading
        ) return
        // 记一笔"用户发起了手动检查"：让在飞的静默检查知道自己的结果已作废。
        manualCheckEpoch++
        _updateState.value = UpdateUiState.Checking
        viewModelScope.launch {
            runUpdateCheck()
                .onSuccess { result ->
                    _updateState.value = when (result) {
                        is UpdateCheckResult.UpToDate -> UpdateUiState.UpToDate(result.currentVersion)
                        is UpdateCheckResult.Available -> UpdateUiState.Available(result.info)
                    }
                    // 手动查完也把"上次检查日期"刷新到今天：这样用户刚手动查完、
                    // 紧接着重开 App，就不会又自动查一遍。
                    UpdateThrottle.markChecked(getApplication(), today())
                }
                .onFailure { error ->
                    _updateState.value = UpdateUiState.Error(
                        error.message ?: "请检查网络后重试"
                    )
                }
        }
    }

    /**
     * 启动时的静默自动检查。三条语义：
     * 1. 静默：不置 Checking（否则启动瞬间会弹出一个转圈的"正在检查更新"弹窗）；
     *    无新版、检查失败、断网、更新源没配置 —— 一律保持 Idle，不弹任何东西，
     *    失败只留一条日志；只有确实有新版本才置 Available，复用现有 UpdateDialog 弹出。
     * 2. 节流：每天最多一次，今天已经自动查过就直接跳过、不发网络请求。
     * 3. 并发保护：若正在检查/下载，直接跳过，不要打断用户操作。
     */
    fun autoCheckForUpdate() {
        if (isUpdateBusy()) return

        val app = getApplication<Application>()
        val today = today()
        if (!UpdateThrottle.shouldAutoCheck(UpdateThrottle.lastCheckDate(app), today)) return

        val epochAtStart = manualCheckEpoch
        silentCheckRunning = true
        viewModelScope.launch {
            try {
                runUpdateCheck()
                    .onSuccess { result ->
                        // 静默结果若已被用户的手动操作取代（期间用户点过手动检查，甚至已把弹窗
                        // 点掉），整体作废 —— 连"今天已查"的日期也不记。
                        // 为什么作废时也不记：被取代意味着用户手动检查过 —— 手动成功时手动路径
                        // 自己已经记过日期，不会白查；手动失败时不记日期，下次启动才会重查，
                        // 而"补一次"正是我们想要的（那个已发现新版的静默结果已被丢弃）。
                        if (manualCheckEpoch != epochAtStart) return@onSuccess
                        // 检查确实成功完成了，记下今天的日期，避免同一天反复查。
                        UpdateThrottle.markChecked(app, today)
                        when (result) {
                            // 静默：已经是最新，什么都不做，保持 Idle。
                            is UpdateCheckResult.UpToDate -> Unit
                            is UpdateCheckResult.Available ->
                                // 双保险：epoch 未变时状态理论上必为 Idle（静默不改状态，下载中会被
                                // isUpdateBusy 拦掉），这里再确认一次，防止将来改动流程时误覆盖用户
                                // 正在看的弹窗。
                                if (_updateState.value is UpdateUiState.Idle) {
                                    _updateState.value = UpdateUiState.Available(result.info)
                                }
                        }
                    }
                    .onFailure { error ->
                        // 静默失败：自动检查不该打扰用户，只打一条日志。
                        Log.i(TAG, "自动检查更新失败，已忽略：${error.message}")
                    }
            } finally {
                silentCheckRunning = false
            }
        }
    }

    /**
     * 真正发起一次更新检查。手动与自动两条路径共用，避免两处逻辑各自漂移。
     * 只做网络请求、不碰 UI 状态 —— 状态的落法由各自的调用方决定。
     */
    private suspend fun runUpdateCheck(): Result<UpdateCheckResult> =
        runCatching { UpdateChecker.check() }

    /** 是否有检查/下载正在进行中（含后台静默自动检查），自动检查靠它做并发保护。 */
    private fun isUpdateBusy(): Boolean =
        silentCheckRunning ||
            _updateState.value is UpdateUiState.Checking ||
            _updateState.value is UpdateUiState.Downloading

    /** 今天的本地日期，ISO-8601（yyyy-MM-dd）。minSdk 26 可直接用 java.time，无需 desugaring。 */
    private fun today(): String = LocalDate.now().toString()

    fun downloadUpdate(info: UpdateInfo) {
        _updateState.value = UpdateUiState.Downloading(info, -1)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                UpdateChecker.download(getApplication(), info) { progress ->
                    _updateState.value = UpdateUiState.Downloading(info, progress)
                }
            }.onSuccess { file ->
                _updateState.value = UpdateUiState.Ready(info, file)
            }.onFailure { error ->
                _updateState.value = UpdateUiState.Error(
                    error.message ?: "下载失败，请稍后重试"
                )
            }
        }
    }

    fun resetUpdate() {
        _updateState.value = UpdateUiState.Idle
    }

    /**
     * 按用户选定的评价学年体检并导出：阻断项先去修，提醒项先过目。
     * 体检会对每张照片做一次 File.isFile（磁盘 stat），照片多时会卡住 UI 帧，
     * 所以整段放进协程，磁盘部分切到 IO，回到主线程再更新状态。
     */
    fun checkBeforeExport(
        list: List<RecordWithPhotos>,
        targetYear: String
    ) {
        viewModelScope.launch {
            // 全量记录直接交给体检：学年过滤只在 ExportCheck 里做一次。
            // 调用方再过滤一遍的话，"另有 N 条属于其他学年"这条提醒会被算成 0，永远不显示。
            val issues = withContext(Dispatchers.IO) {
                ExportCheck.run(list, targetYear, photoStore::exists)
            }
            val exportItems = ExportCheck.targetItems(list, targetYear)

            when {
                ExportCheck.blocks(issues) ->
                    _exportState.value = ExportState.Blocked(issues)

                issues.isNotEmpty() ->
                    _exportState.value = ExportState.Confirm(exportItems, targetYear, issues)

                else -> startExport(exportItems, targetYear)
            }
        }
    }

    /** 用户在提醒确认页确认无误后继续导出。范围随 Confirm 状态一起带出来，不会再为空。 */
    fun confirmExport() {
        val confirm = _exportState.value as? ExportState.Confirm ?: return
        startExport(confirm.items, confirm.targetYear)
    }

    private fun startExport(list: List<RecordWithPhotos>, targetYear: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = ZipExporter.export(getApplication(), list, targetYear) { done, total ->
                    _exportState.value = ExportState.Exporting(done, total)
                }
                _exportState.value = ExportState.Done(result, targetYear)
            } catch (e: Exception) {
                _exportState.value = ExportState.Error(e.message ?: "导出失败")
            }
        }
    }

    private companion object {
        /** 项目目前没有引入日志库，用系统的 Log 打静默自动检查的失败信息即可。 */
        const val TAG = "UpdateChecker"

        /** 删除失败的日志单独一个 tag：它与更新检查是两条完全无关的链路。 */
        const val DELETION_TAG = "RecordDeletion"

        /** 删除失败的用户可见文案。文件全部幸存，所以文案必须说清"数据未改动"。 */
        const val DELETE_FAILED_MESSAGE = "删除失败，数据未改动"
    }
}
