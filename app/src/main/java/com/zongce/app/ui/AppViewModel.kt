// 编排记录保存、照片引用生命周期和导出状态反馈。
package com.zongce.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class ExportState {
    object Idle : ExportState()
    data class Blocked(val issues: List<ExportCheck.Issue>) : ExportState()

    /** 没有阻断项、但有提醒项：先让用户过目，确认后才打包。 */
    data class Confirm(
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

    /** 最近一次导出预览字段名（导出前过目一眼——文件名是公开可读的） */
    fun previewFileName(record: AwardRecord): String =
        FileNameRule.build(record.awardDate, record.awardName, record.grade) + ".jpg"

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
        }
    }

    private suspend fun currentPhotoName(photoId: Long): String? {
        val all = items.value
        return all.asSequence()
            .flatMap { it.photos.asSequence() }
            .firstOrNull { it.id == photoId }?.fileName
    }

    // ---------- 导出（英雄时刻） ----------

    /** 提醒确认页上「继续导出」要用的负载：已确认的范围与学年。 */
    private var pendingExport: Pair<List<RecordWithPhotos>, String>? = null

    fun resetExport() {
        pendingExport = null
        _exportState.value = ExportState.Idle
    }

    fun checkForUpdate() {
        if (_updateState.value is UpdateUiState.Checking ||
            _updateState.value is UpdateUiState.Downloading
        ) return
        _updateState.value = UpdateUiState.Checking
        viewModelScope.launch {
            runCatching { UpdateChecker.check() }
                .onSuccess { result ->
                    _updateState.value = when (result) {
                        is UpdateCheckResult.UpToDate -> UpdateUiState.UpToDate(result.currentVersion)
                        is UpdateCheckResult.Available -> UpdateUiState.Available(result.info)
                    }
                }
                .onFailure { error ->
                    _updateState.value = UpdateUiState.Error(
                        error.message ?: "请检查网络后重试"
                    )
                }
        }
    }

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

    /** 按用户选定的评价学年体检并导出：阻断项先去修，提醒项先过目。 */
    fun checkBeforeExport(
        list: List<RecordWithPhotos>,
        targetYear: String
    ) {
        // 全量记录直接交给体检：学年过滤只在 ExportCheck 里做一次。
        // 调用方再过滤一遍的话，"另有 N 条属于其他学年"这条提醒会被算成 0，永远不显示。
        val issues = ExportCheck.run(list, targetYear, photoStore::exists)
        val exportItems = ExportCheck.targetItems(list, targetYear)

        when {
            ExportCheck.blocks(issues) -> {
                pendingExport = null
                _exportState.value = ExportState.Blocked(issues)
            }

            issues.isNotEmpty() -> {
                pendingExport = exportItems to targetYear
                _exportState.value = ExportState.Confirm(targetYear, issues)
            }

            else -> {
                pendingExport = null
                startExport(exportItems, targetYear)
            }
        }
    }

    /** 用户在提醒确认页确认无误后继续导出。 */
    fun confirmExport() {
        val pending = pendingExport ?: return
        pendingExport = null
        startExport(pending.first, pending.second)
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
}
