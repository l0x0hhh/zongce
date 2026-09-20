package com.zongce.app.core

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 导出文件名规则：获奖时间_获奖名称_等级.jpg
 *
 * 学校系统原文："请在文件上传前修改好文件名"——文件名就是索引。
 * 约束：≤40 字（系统里显示得全）；剔除 \ / : * ? " < > | 与换行；
 * 按名排序 = 按获奖时间排序（YYYYMMDD 补零开头）；同记录多图加 _2 _3。
 */
object FileNameRule {

    private const val MAX_TOTAL = 40
    private const val EXTENSION_LENGTH = 4 // ".jpg"
    private const val MAX_BASE_LENGTH = MAX_TOTAL - EXTENSION_LENGTH
    private const val MAX_NAME = 20

    /** 生成主文件名（不含扩展名），如 20251123_全国大学生信息安全竞赛_国家级三等奖 */
    fun build(awardDate: String, awardName: String, grade: String): String {
        val date = try {
            LocalDate.parse(awardDate).format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        } catch (e: Exception) {
            awardDate.replace("-", "")
        }
        val name = sanitize(awardName.ifBlank { "未命名" }).take(MAX_NAME)
        val gradePart = sanitize(grade).take(10)
        var base = buildString {
            append(date)
            append('_')
            append(name)
            if (gradePart.isNotEmpty()) {
                append('_')
                append(gradePart)
            }
        }
        if (base.length > MAX_BASE_LENGTH) base = base.take(MAX_BASE_LENGTH)
        return base
    }

    /** 同一条记录的第 N 张：主名_2.jpg，并为序号保留扩展名长度。 */
    fun withSuffix(base: String, index: Int): String =
        if (index <= 1) {
            base.take(MAX_BASE_LENGTH)
        } else {
            val suffix = "_$index"
            base.take((MAX_BASE_LENGTH - suffix.length).coerceAtLeast(0)) + suffix
        }

    /** 清洗非法字符：\ / : * ? " < > | 换行 → '-'，压缩空白 */
    fun sanitize(raw: String): String = raw
        .replace(Regex("[\\\\/:*?\"<>|\\r\\n\\t]"), "-")
        .replace(Regex("\\s+"), "")
        .trim()
        .trim('-')
}
