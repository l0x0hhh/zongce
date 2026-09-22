// 导出规划与文案：纯函数、零 Android 依赖，JVM 单测直接可跑。
// 学年过滤不在这里做 —— 过滤是调用方用 ExportCheck.targetItems 的事，
// 同一个谓词只许有一份实现，否则导出与体检迟早漂移。
package com.zongce.app.export

import com.zongce.app.core.FileNameRule
import com.zongce.app.data.RecordWithPhotos
import com.zongce.app.data.WUYU_LIST
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 一张照片在导出包里的落位。
 * 扁平化成一条记录，消灭旧实现里 `plan[recordId]` + `placedList[index]`
 * 的双重顺序配对 —— 那两套索引一旦错位，照片就会写进别人的文件名而 ZIP 照样成功。
 */
data class ExportEntry(
    val recordId: Long,
    val photoId: Long,
    val sourceFileName: String,   // filesDir/photos/ 下的哈希文件名
    val zipPath: String           // 如 "德育/20251123_xxx_第1等级.jpg"
)

/**
 * 只命名 + 去重，不做学年过滤（过滤是调用方用 ExportCheck.targetItems 的事）。
 *
 * 同名去重走 FileNameRule.withSuffix：裸拼接 "${baseName}_$seq" 会把含后缀长度
 * 撑到 42 字符，突破 FileNameRule 自定的 ≤40 硬约束（学校系统会截断/错认文件名）；
 * withSuffix 保证含序号 ≤36，加 .jpg 恒为 40。
 */
fun plan(items: List<RecordWithPhotos>): List<ExportEntry> {
    val entries = mutableListOf<ExportEntry>()
    val used = mutableSetOf<String>()
    for (item in items) {
        val r = item.record
        val base = FileNameRule.build(r.awardDate, r.awardName, r.grade)
        item.photos.forEachIndexed { index, photo ->
            val baseName = FileNameRule.withSuffix(base, index + 1)
            var name = "$baseName.jpg"
            var entry = "${r.wuyu}/$name"
            var seq = 1
            while (!used.add(entry)) {
                seq++
                name = "${FileNameRule.withSuffix(baseName, seq)}.jpg"
                entry = "${r.wuyu}/$name"
            }
            entries += ExportEntry(
                recordId = r.id,
                photoId = photo.id,
                sourceFileName = photo.fileName,
                zipPath = entry
            )
        }
    }
    return entries
}

/** 填报核对.txt：每育一节、每条一组，"照片："行给相对路径，末尾 ☐ 勾选位 */
fun checklist(
    items: List<RecordWithPhotos>,
    plan: List<ExportEntry>,
    targetYear: String
): String {
    val time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date())
    // 按记录建索引一次，避免在 per-record 循环里 filter（O(n²)），也别用 first 撞 id=0。
    val entriesByRecord = plan.groupBy { it.recordId }
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
            entriesByRecord[r.id]?.forEach { e ->
                sb.appendLine("    照片：${e.zipPath}")
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

/** 导出包文件名：综测证明材料_2025-2026学年.zip */
fun zipFileName(targetYear: String): String = "综测证明材料_${targetYear}学年.zip"

/**
 * 只出统计。recordCount = items.size；perWuyu 按【记录数】分组（含 0 照片记录！）；
 * photoCount = plan.size（实际落包的照片数）。
 */
fun summarize(
    items: List<RecordWithPhotos>,
    plan: List<ExportEntry>,
    zipFile: File
): ExportResult {
    val perWuyu = items.groupBy { it.record.wuyu }.mapValues { it.value.size }
    return ExportResult(
        file = zipFile,
        wuyuCount = perWuyu.size,
        recordCount = items.size,
        photoCount = plan.size,
        sizeBytes = zipFile.length(),
        perWuyu = perWuyu
    )
}
