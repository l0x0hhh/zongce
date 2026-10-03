// 导出域 ViewModel：导出状态机（体检 → 确认 → 打包 → 完成/失败）。
// 由原 AppViewModel 按功能域拆出（见 docs/design/architecture-refactor-step1-vm-split.md）。
// 只依赖导出层的纯逻辑（ExportCheck / ZipExporter），不摸 DAO、不碰删除域。
package com.zongce.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zongce.app.data.PhotoStore
import com.zongce.app.data.RecordWithPhotos
import com.zongce.app.export.ExportCheck
import com.zongce.app.export.ExportResult
import com.zongce.app.export.ZipExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

class ExportViewModel(app: Application) : AndroidViewModel(app) {

    /** 体检时逐张照片做 File.isFile（磁盘 stat）的端口；复用同一 PhotoStore，别在回调里新建。 */
    private val photoStore = PhotoStore(app)

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

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

    /**
     * 回到导出页初始态。Confirm 态的导出范围本来就在状态里，清状态即清范围，
     * 不存在需要额外清掉的裸字段。
     *
     * 注意：原 AppViewModel.resetExport() 还会清「删除这一学年」的 pending 状态与闸门，
     * 那一半现在由 RecordViewModel.resetYearDeletePrompt() 承担，并在 MainActivity 组合调用
     * （见 docs/design/architecture-refactor-step1-vm-split.md 的 D2）—— 导出域不该知道删除域。
     */
    fun resetExport() {
        _exportState.value = ExportState.Idle
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
}
