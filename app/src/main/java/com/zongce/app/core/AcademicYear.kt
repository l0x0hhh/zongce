package com.zongce.app.core

import java.time.LocalDate

/** 学年归属规则：每年 9 月 1 日自动滚动，合法日期只提示归属、不阻塞录入。 */
object AcademicYear {

    private const val START_MONTH = 9
    private const val DATE_FORMAT = "yyyy-MM-dd"

    enum class Status { OK, BOUNDARY, OUT_OF_RANGE }

    data class Check(
        val status: Status,
        val message: String,
        val academicYear: String = ""
    )

    /** 页面展示用的当前目标学年，随系统日期滚动。 */
    val LABEL: String
        get() = targetLabel()

    /** 保留页面现有调用，但窗口由当前目标学年动态推导。 */
    val START: String
        get() = windowFor(LABEL).first.toString()

    val END: String
        get() = windowFor(LABEL).second.toString()

    fun labelForDate(dateText: String): String = labelFor(LocalDate.parse(dateText))

    fun targetLabel(dateText: String = today()): String = labelForDate(dateText)

    fun belongsTo(dateText: String, label: String): Boolean =
        runCatching { labelForDate(dateText) == label }.getOrDefault(false)

    /**
     * 一组获奖日期里出现过的全部学年，降序。
     * 当前目标学年始终在列 —— 否则新学期刚开始、一条记录都没有时，
     * 学年选择器会空掉，用户看不到"当前学年"这个锚点。
     * 日期格式非法的记录直接跳过（它们本来就会被导出体检拦下）。
     */
    fun yearsOf(dateTexts: List<String>): List<String> =
        (dateTexts.mapNotNull { runCatching { labelForDate(it) }.getOrNull() } + LABEL)
            .distinct()
            .sortedDescending()

    fun check(dateText: String): Check {
        val date = runCatching { LocalDate.parse(dateText) }
            .getOrNull() ?: return Check(Status.OUT_OF_RANGE, "日期格式不正确，应为 $DATE_FORMAT")

        val label = labelFor(date)
        val (start, end) = windowFor(label)
        return if (date == start || date == end) {
            Check(
                Status.BOUNDARY,
                "边界日（${date}）：请确认证书上的获奖时间确实在这一天，已归入 $label 学年",
                label
            )
        } else {
            Check(Status.OK, "已归入 $label 学年", label)
        }
    }

    /** 只有日期格式错误才阻塞，跨学年日期不再被旧窗口拦截。 */
    fun blocked(dateText: String): Boolean = check(dateText).status == Status.OUT_OF_RANGE

    fun today(): String = LocalDate.now().toString()

    private fun labelFor(date: LocalDate): String {
        val startYear = if (date.monthValue >= START_MONTH) date.year else date.year - 1
        return "$startYear-${startYear + 1}"
    }

    private fun windowFor(label: String): Pair<LocalDate, LocalDate> {
        val startYear = label.substringBefore('-').toInt()
        return LocalDate.of(startYear, START_MONTH, 1) to
            LocalDate.of(startYear + 1, START_MONTH, 1).minusDays(1)
    }
}
