// 以临时文件完成 ZIP 后再提交，避免导出失败留下可误用的半成品。
package com.zongce.app.export

import android.content.Context
import com.zongce.app.core.FileNameRule
import com.zongce.app.core.ImageTools
import com.zongce.app.data.PhotoStore
import com.zongce.app.data.RecordWithPhotos
import com.zongce.app.data.WUYU_LIST
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

    /** 每张照片在包内的相对路径（txt 里引用、命名去重共用） */
    private class Placed(val wuyu: String, val entry: String)

    /** [targetYear] 必填：导出档位不能有默认值，否则会随"今天"静默滑动。 */
    fun export(
        context: Context,
        items: List<RecordWithPhotos>,
        targetYear: String,
        onProgress: (done: Int, total: Int) -> Unit
    ): ExportResult {
        val photoStore = PhotoStore(context)

        // 1) 先规划每条记录每张照片的路径（含同名去重）
        val plan = LinkedHashMap<Long, List<Placed>>()
        val used = mutableSetOf<String>()
        for (item in items) {
            val r = item.record
            val base = FileNameRule.build(r.awardDate, r.awardName, r.grade)
            val placed = item.photos.mapIndexed { index, photo ->
                val baseName = FileNameRule.withSuffix(base, index + 1)
                var name = "$baseName.jpg"
                var entry = "${r.wuyu}/$name"
                var seq = 1
                while (!used.add(entry)) {
                    seq++
                    name = "${baseName}_$seq.jpg"
                    entry = "${r.wuyu}/$name"
                }
                Placed(r.wuyu, entry)
            }
            plan[r.id] = placed
        }

        val totalPhotos = plan.values.sumOf { it.size }

        // 2) 出包
        val outDir = File(context.cacheDir, "exports").apply { mkdirs() }
        outDir.listFiles()?.forEach { it.delete() } // 清掉上一版
        val zipFile = File(outDir, "综测证明材料_${targetYear}学年.zip")
        val tempFile = File(outDir, "${zipFile.name}.part")
        tempFile.delete()

        var done = 0
        try {
            ZipOutputStream(FileOutputStream(tempFile)).use { zos ->
                // 清单 txt（UTF-8 带 BOM，任何记事本双击打开都不乱码）
                val txt = buildChecklist(items, plan, targetYear)
                zos.putNextEntry(ZipEntry("填报核对.txt"))
                zos.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                zos.write(txt.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                // 五育文件夹（空的也建出来，解压后结构完整）
                WUYU_LIST.forEach { wuyu ->
                    zos.putNextEntry(ZipEntry("$wuyu/"))
                    zos.closeEntry()
                }

                // 照片：压缩到 3.5MB 内 + 统一转 JPG + HEIC 转换
                for (item in items) {
                    val placedList = plan[item.record.id] ?: continue
                    item.photos.forEachIndexed { index, photo ->
                        val src = photoStore.photoFile(photo.fileName)
                        val bytes = ImageTools.toJpeg(src)
                        zos.putNextEntry(ZipEntry(placedList[index].entry))
                        zos.write(bytes)
                        zos.closeEntry()
                        done++
                        onProgress(done, totalPhotos)
                    }
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

        val perWuyu = items.groupBy { it.record.wuyu }.mapValues { it.value.size }
        return ExportResult(
            file = zipFile,
            wuyuCount = perWuyu.size,
            recordCount = items.size,
            photoCount = totalPhotos,
            sizeBytes = zipFile.length(),
            perWuyu = perWuyu
        )
    }

    /** 填报核对.txt：每育一节、每条一组，"照片："行给相对路径，末尾 ☐ 勾选位 */
    private fun buildChecklist(
        items: List<RecordWithPhotos>,
        plan: LinkedHashMap<Long, List<Placed>>,
        targetYear: String
    ): String {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date())
        val sb = StringBuilder()
        sb.appendLine("══════════════════════════════════════════")
        sb.appendLine("  综测填报核对清单 · $targetYear 学年")
        sb.appendLine("  生成时间：$time")
        sb.appendLine("  共 ${items.size} 条记录")
        sb.appendLine("══════════════════════════════════════════")
        sb.appendLine()

        WUYU_LIST.forEach { wuyu ->
            val group = items.filter { it.record.wuyu == wuyu }
            if (group.isEmpty()) return@forEach
            sb.appendLine("【$wuyu】${group.size} 条")
            group.forEachIndexed { i, item ->
                val r = item.record
                sb.appendLine(" ${i + 1}. ${r.awardName.ifBlank { "（缺获奖名称）" }}")
                sb.appendLine("    时间：${r.awardDate.ifBlank { "待补充" }}")
                sb.appendLine("    级别：${r.level.ifBlank { "待补充" }}    等级：${r.grade.ifBlank { "待补充" }}")
                sb.appendLine("    角色：${r.role.ifBlank { "待补充" }}    发证单位：${r.issuer.ifBlank { "—" }}")
                plan[r.id]?.forEach { p ->
                    sb.appendLine("    照片：${p.entry}")
                }
                sb.appendLine("    已填报：☐")
                sb.appendLine()
            }
        }

        sb.appendLine("──────────────────────────────────────────")
        sb.appendLine("照片在同名文件夹里（${WUYU_LIST.joinToString(" ")}），按文件名找对应图。")
        sb.appendLine("学校系统：可上传多个文件，每次只能上传一个；仅图片格式，单文件最大 4M。")
        sb.appendLine()
        sb.appendLine("等级口径速查（常用通用映射，最终以学院综测通知为准）：")
        sb.appendLine("  第1等级：第1名 / 一等奖；第2等级：第2、3名 / 二等奖、三等奖；第3等级：第4-8名 / 优胜奖。")
        return sb.toString()
    }
}
