// 以临时文件完成 ZIP 后再提交，避免导出失败留下可误用的半成品。
// 命名、清单文案与统计都在 ExportPlan.kt（纯函数）；这里只装配 IO 与 Android 适配。
package com.zongce.app.export

import android.content.Context
import com.zongce.app.core.ImageTools
import com.zongce.app.data.PhotoStore
import com.zongce.app.data.RecordWithPhotos
import com.zongce.app.data.WUYU_LIST
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 导出结果清点数据（用于导出完成页） */
data class ExportResult(
    val file: File,
    val wuyuCount: Int,
    val recordCount: Int,
    val photoCount: Int,
    val sizeBytes: Long,
    val perWuyu: Map<String, Int>
)

/**
 * 导出上传包：
 *   综测证明材料_2025-2026学年.zip
 *   ├── 填报核对.txt
 *   ├── 德育\ ... 智育\ 体育\ 美育\ 劳育\
 *
 * 照片放进你录入时选的那一育；文件名 = 获奖时间_获奖名称_等级.jpg（文件夹已表达五育，不再重复）。
 */
object ZipExporter {

    /**
     * 只装配 IO：JDK 纯 java.util.zip，零 Android 依赖。
     *
     * 行为：mkdirs → 清掉 outDir 里的上一版 → 写 .part 临时文件
     * （UTF-8 BOM + 填报核对.txt → 五育目录恒建（空的也建）→ 逐条 photoBytes 写入）
     * → 原子 rename 到正式名；任何异常删掉 .part 后原样抛出。
     */
    internal fun exportTo(
        outDir: File,
        zipName: String,
        plan: List<ExportEntry>,
        checklistText: String,
        photoBytes: (sourceFileName: String) -> ByteArray,
        onProgress: (done: Int, total: Int) -> Unit
    ): File {
        outDir.mkdirs()
        outDir.listFiles()?.forEach { it.delete() } // 清掉上一版
        val zipFile = File(outDir, zipName)
        val tempFile = File(outDir, "${zipFile.name}.part")
        tempFile.delete()

        var done = 0
        try {
            ZipOutputStream(FileOutputStream(tempFile)).use { zos ->
                // 清单 txt（UTF-8 带 BOM，任何记事本双击打开都不乱码）
                zos.putNextEntry(ZipEntry("填报核对.txt"))
                zos.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                zos.write(checklistText.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                // 五育文件夹（空的也建出来，解压后结构完整）
                WUYU_LIST.forEach { wuyu ->
                    zos.putNextEntry(ZipEntry("$wuyu/"))
                    zos.closeEntry()
                }

                // 照片：按 plan 扁平落位，字节来源由调用方注入
                plan.forEach { entry ->
                    zos.putNextEntry(ZipEntry(entry.zipPath))
                    zos.write(photoBytes(entry.sourceFileName))
                    zos.closeEntry()
                    done++
                    onProgress(done, plan.size)
                }
            }
            if (zipFile.exists() && !zipFile.delete()) {
                throw IllegalStateException("无法替换旧导出文件")
            }
            if (!tempFile.renameTo(zipFile)) {
                throw IllegalStateException("无法完成导出文件写入")
            }
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
        return zipFile
    }

    /**
     * Android 适配器：公开签名不变，AppViewModel 调用方一行不改。
     * [targetYear] 必填：导出档位不能有默认值，否则会随"今天"静默滑动。
     */
    fun export(
        context: Context,
        items: List<RecordWithPhotos>,
        targetYear: String,
        onProgress: (done: Int, total: Int) -> Unit
    ): ExportResult {
        // 构造一次，别放进 lambda —— 每张照片 new 一个 PhotoStore 是纯浪费。
        val photoStore = PhotoStore(context)
        val entries = plan(items)
        val zipFile = exportTo(
            outDir = File(context.cacheDir, "exports"),
            zipName = zipFileName(targetYear),
            plan = entries,
            checklistText = checklist(items, entries, targetYear),
            photoBytes = { sourceFileName ->
                ImageTools.toJpeg(photoStore.photoFile(sourceFileName))
            },
            onProgress = onProgress
        )
        return summarize(items, entries, zipFile)
    }
}
