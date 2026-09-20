// 编排记录保存、照片引用生命周期和导出状态反馈。
package com.zongce.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zongce.app.core.FileNameRule
import com.zongce.app.core.AcademicYear
import com.zongce.app.data.AppDatabase
import com.zongce.app.data.AwardPhoto
import com.zongce.app.data.AwardRecord
import com.zongce.app.data.PhotoStore
import com.zongce.app.data.RecordWithPhotos
import com.zongce.app.export.ExportCheck
import com.zongce.app.export.ExportResult
import com.zongce.app.export.ZipExporter
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

    fun resetExport() {
        _exportState.value = ExportState.Idle
    }

    /** 按用户选定的评价学年体检并导出。 */
    fun checkBeforeExport(
        list: List<RecordWithPhotos>,
        targetYear: String = AcademicYear.targetLabel()
    ) {
        val exportItems = list.filter {
            it.record.awardDate.isBlank() ||
                AcademicYear.check(it.record.awardDate).status == AcademicYear.Status.OUT_OF_RANGE ||
                AcademicYear.belongsTo(it.record.awardDate, targetYear)
        }
        val issues = ExportCheck.run(exportItems, targetYear, photoStore::exists)
        _exportState.value =
            if (ExportCheck.blocks(issues)) ExportState.Blocked(issues) else ExportState.Idle
        if (!ExportCheck.blocks(issues)) startExport(exportItems, targetYear)
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
