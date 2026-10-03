// 记录域 ViewModel：记录流、照片导入、多选态、删除（含导出后删除询问闸门）与学年偏好。
// 由原 AppViewModel 按功能域拆出（见 docs/design/architecture-refactor-step1-vm-split.md）：
// 删除相关的一切（RecordDeletion、YearDeletePromptGate、deleting、deleteMessages）都内聚在本类，
// 不跨 VM 拆散 —— 闸门状态分裂正是 v1.4.0 之前出竞态的重灾区。
package com.zongce.app.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zongce.app.core.AcademicYear
import com.zongce.app.data.AchievementYearStore
import com.zongce.app.data.AppDatabase
import com.zongce.app.data.AwardPhoto
import com.zongce.app.data.AwardRecord
import com.zongce.app.data.PhotoStore
import com.zongce.app.data.RecordDeletion
import com.zongce.app.data.RecordWithPhotos
import com.zongce.app.data.YearDeletePromptGate
import com.zongce.app.widget.WidgetRefresh
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

class RecordViewModel(app: Application) : AndroidViewModel(app) {

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

    fun setPendingUris(uris: List<Uri>) {
        _pendingUris.value = uris
    }

    fun clearPending() {
        _pendingUris.value = emptyList()
    }

    /**
     * 取某张照片的原图文件，给 UI 显示缩略图用。
     * UI 一律走这里 —— Composable 不要再自己构造 PhotoStore（那会绕过 ViewModel
     * 并拿 Activity Context 去建数据层对象）。
     */
    fun photoFile(fileName: String): File = photoStore.photoFile(fileName)

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

            WidgetRefresh.refresh(getApplication())

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
     * 与 update 域里的 silentCheckRunning 同理，全部在主线程读写（调用方都是 UI 点击）。
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
                WidgetRefresh.refresh(getApplication())
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
                WidgetRefresh.refresh(getApplication())
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
                WidgetRefresh.refresh(getApplication())
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
                WidgetRefresh.refresh(getApplication())
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

    /** 点「分享材料包」时调用（由 MainActivity 从 ExportScreen 的 onShareYear 回调接进来）。 */
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

    /**
     * 清掉导出后删除的 pending 状态与闸门（由 MainActivity 在「导出其他学年 / 重新开始」时
     * 与 ExportViewModel.resetExport() 组合调用）。
     * 拆自原 AppViewModel.resetExport() 的删除域部分：ExportViewModel 不该知道删除域的存在。
     */
    fun resetYearDeletePrompt() {
        _yearDeletePrompt.value = null
        yearDeleteGate.reset()
    }

    private companion object {
        /** 删除失败的日志单独一个 tag：它与更新检查是两条完全无关的链路。 */
        const val DELETION_TAG = "RecordDeletion"

        /** 删除失败的用户可见文案。文件全部幸存，所以文案必须说清"数据未改动"。 */
        const val DELETE_FAILED_MESSAGE = "删除失败，数据未改动"
    }
}
