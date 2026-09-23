// 编排记录保存、照片引用生命周期和导出状态反馈。
package com.zongce.app.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zongce.app.core.AcademicYear
import com.zongce.app.core.FileNameRule
import com.zongce.app.data.AppDatabase
import com.zongce.app.data.AwardPhoto
import com.zongce.app.data.AwardRecord
import com.zongce.app.data.PhotoStore
import com.zongce.app.data.RecordWithPhotos
import com.zongce.app.export.ExportCheck
import com.zongce.app.export.ExportResult
import com.zongce.app.export.ZipExporter
import com.zongce.app.update.UpdateCheckResult
import com.zongce.app.update.UpdateChecker
import com.zongce.app.update.UpdateInfo
import com.zongce.app.update.UpdateThrottle
import com.zongce.app.widget.AchievementListWidget
import com.zongce.app.widget.WidgetYearStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
    data class Done(val result: ExportResult) : ExportState()
    data class Error(val message: String) : ExportState()
}

data class ImportFailure(val uri: Uri, val message: String)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.get(app).awardDao()
    private val photoStore = PhotoStore(app)

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

            if (removedIds.isNotEmpty()) {
                removedIds.forEach { pid ->
                    val name = currentPhotoName(pid)
                    if (!name.isNullOrEmpty() && dao.photoReferenceCount(name) <= 1) photoStore.delete(name)
                }
                dao.deletePhotos(removedIds)
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

            refreshWidget()
            withContext(Dispatchers.Main) { onSaved(failures) }
        }
    }

    fun deleteRecord(item: RecordWithPhotos) {
        viewModelScope.launch(Dispatchers.IO) {
            item.photos.forEach { photo ->
                if (dao.photoReferenceCount(photo.fileName) <= 1) photoStore.delete(photo.fileName)
            }
            dao.deletePhotosOf(item.record.id)
            dao.deleteRecord(item.record)
            refreshWidget()
        }
    }

    /**
     * 数据变了就把「成果概览」小组件推一次。
     * 它不做定时轮询（updatePeriodMillis = 0），桌面上的列表靠这里和 App 保持一致。
     * 只刷成果组件 —— 「快速录入」组件是纯入口、不显示数据，没有刷新的必要。
     * 失败只吞掉：小组件刷不出来，不该影响"把这条获奖记下来"这件正事。
     * pushUpdate 里会查一次库，所以放到 IO 线程执行。
     */
    private fun refreshWidget() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { AchievementListWidget.pushUpdate(getApplication()) }
        }
    }

    /**
     * 学年同步请求队列。
     *
     * 为什么需要它，而不是每次点击直接起一个 IO 协程：Dispatchers.IO 是多线程池，
     * 连续点两个学年 chip 时，两次「写盘 + 推送组件」的**执行顺序没有保证**。
     * 坏交错下后点的先执行、先点的后执行，最终落盘的反而是先点的那个 ——
     * 表现为「我明明选的是最后点的学年，组件却停在之前那个」，正是本次要修的
     * 同步失效表征（低概率，但混淆度极高，用户只会觉得"又没同步"）。
     *
     * 单消费者队列把这两步串行化，且严格按入队顺序（= 点击顺序）执行，
     * 保证最后一次点击的最后生效。UNLIMITED 容量避免连点时溢出丢事件。
     */
    private val widgetYearRequests = Channel<String>(Channel.UNLIMITED)

    init {
        // 队列的唯一消费者：挂在 viewModelScope 下，随 ViewModel 销毁而取消，
        // 不需要另外管理生命周期。receive() 在队列空时挂起，取消时抛
        // CancellationException 正常退出。
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                val year = widgetYearRequests.receive()
                WidgetYearStore.set(getApplication(), year)
                runCatching { AchievementListWidget.pushUpdate(getApplication()) }
            }
        }
    }

    /**
     * App 内成果页选中了学年：写入组件共用的学年存储，并立刻推送所有成果组件重渲染。
     * 此前 App 内的选中只活在 Compose 本地状态里，组件读的存储永远没人写 ——
     * 这就是"选完学年回桌面，组件还是旧学年"的根因。
     *
     * 只负责入队，实际写盘与推送由上面的消费者在 IO 线程串行完成。
     * 推送失败只吞掉，与 refreshWidget() 同理：组件刷不出来，不该影响 App 内的筛选本身。
     */
    fun syncWidgetYear(year: String) {
        widgetYearRequests.trySend(year)
    }

    /**
     * 成果页的初始学年：与组件渲染同口径 —— 查一次库算学年列表，再读存储做回落。
     * 不能拿 UI 已有的 items 来算 years（StateFlow 首帧是空列表，
     * yearsOf 又永远包含当前学年），否则存储里的学年会被误判成"已不存在"而错误回落。
     */
    suspend fun initialWidgetYear(): String = withContext(Dispatchers.IO) {
        val items = dao.allWithPhotos().first()
        val years = AcademicYear.yearsOf(items.map { it.record.awardDate })
        WidgetYearStore.current(getApplication(), years)
    }

    private suspend fun currentPhotoName(photoId: Long): String? {
        val all = items.value
        return all.asSequence()
            .flatMap { it.photos.asSequence() }
            .firstOrNull { it.id == photoId }?.fileName
    }

    // ---------- 导出（英雄时刻） ----------

    /**
     * 回到初始态。Confirm 态的导出范围本来就在状态里，清状态即清范围，
     * 不存在需要额外清掉的裸字段。
     */
    fun resetExport() {
        _exportState.value = ExportState.Idle
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
                _exportState.value = ExportState.Done(result)
            } catch (e: Exception) {
                _exportState.value = ExportState.Error(e.message ?: "导出失败")
            }
        }
    }

    private companion object {
        /** 项目目前没有引入日志库，用系统的 Log 打静默自动检查的失败信息即可。 */
        const val TAG = "UpdateChecker"
    }
}
