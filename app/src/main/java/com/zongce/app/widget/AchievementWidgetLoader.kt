// 组件的数据装载：一次快照读，不做流式订阅。
//
// 为什么不做 Flow 订阅：provideGlance 跑在 WorkManager 的 CoroutineWorker 里，
// provideContent 之后 composition 只活约 45 秒（源码 KDoc），监听 Flow 没有意义。
// 数据变了由写路径主动调 WidgetRefresh.refresh() 触发下一次 provideGlance。
package com.zongce.app.widget

import android.content.Context
import androidx.compose.ui.graphics.toArgb
import com.zongce.app.R
import com.zongce.app.core.AcademicYear
import com.zongce.app.data.AppDatabase
import com.zongce.app.data.RecordWithPhotos
import com.zongce.app.data.WidgetYearStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** 组件列表里的一行。字段口径与成果页 RecordRow 逐条对齐，用户从桌面到 App 不换认知。 */
data class AchievementWidgetRow(
    /** = record.id，用作 LazyColumn 的 itemId（稳定 id，滚动位置才不会跳） */
    val id: Long,
    /** awardName，空则显示「未填写获奖名称」 */
    val name: String,
    /** 「五育 · 日期 · 等级」 */
    val subline: String,
    /** 五育色点的 ARGB，取自己 WidgetPalette（不依赖主题） */
    val wuyuColor: Int
)

/** 组件一次渲染所需的全部数据 —— provideGlance 与 providePreview 共用同一份装载结果。 */
data class AchievementWidgetSnapshot(
    /** AcademicYear.yearsOf(全部日期)，恒含 LABEL，降序 */
    val years: List<String>,
    /** WidgetYearStore.current()，已回落过的安全值 */
    val year: String,
    /** 该学年条数（未截断） */
    val recordCount: Int,
    /** 该学年覆盖几育 */
    val coveredWuyu: Int,
    /** 已排序 + 已截断 ≤ WIDGET_MAX_ROWS */
    val rows: List<AchievementWidgetRow>,
    /** 是否超出上限（决定底部 L2 文案） */
    val hasMore: Boolean
) {
    companion object {
        /**
         * 查库失败时的降级快照（PRD B-5）：只有当前目标学年、0 条记录。
         *
         * 用 getter 而不是 val 常量，是为了让 LABEL 每次都按"当时"的系统日期算
         * —— 组件进程可能活很久，写死成 val 会在跨过 9 月 1 日后还报上一学年的标签。
         */
        val FALLBACK: AchievementWidgetSnapshot
            get() = AchievementWidgetSnapshot(
                years = listOf(AcademicYear.LABEL),
                year = AcademicYear.LABEL,
                recordCount = 0,
                coveredWuyu = 0,
                rows = emptyList(),
                hasMore = false
            )
    }
}

object AchievementWidgetLoader {

    /**
     * 装载一次渲染所需的数据。内部 runCatching：查库失败一律回落 FALLBACK，
     * 绝不向上抛 —— 组件刷新失败最坏只是桌面暂时旧，不能污染调用方。
     */
    suspend fun load(context: Context): AchievementWidgetSnapshot =
        withContext(Dispatchers.IO) {
            runCatching {
                val app = context.applicationContext
                // 一次性快照读：first() 拿当前值就走，不留订阅。
                val items = AppDatabase.get(app).awardDao().allWithPhotos().first()

                val years = AcademicYear.yearsOf(items.map { it.record.awardDate })
                // 学年归属全仓只有 AcademicYear 一处实现，这里禁止手写日期比较。
                // 组件学年与 App 成果页偏好分开；删空当前学年时回落到 LABEL。
                val year = WidgetYearStore.current(app, years)

                // 排序必须在过滤之后：先筛出该学年，再按日期降序。
                val ofYear = AcademicYear.inYear(items, year) { it.record.awardDate }
                    .sortedByDescending { it.record.awardDate }

                val rows = ofYear
                    .take(AchievementWidget.WIDGET_MAX_ROWS)
                    .map { it.toRow(app) }

                AchievementWidgetSnapshot(
                    years = years,
                    year = year,
                    recordCount = ofYear.size,
                    coveredWuyu = ofYear
                        .map { it.record.wuyu }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .count(),
                    rows = rows,
                    hasMore = ofYear.size > rows.size
                )
            }.getOrElse { AchievementWidgetSnapshot.FALLBACK }
        }

    private fun RecordWithPhotos.toRow(context: Context): AchievementWidgetRow {
        val record = this.record
        return AchievementWidgetRow(
            id = record.id,
            name = record.awardName.ifBlank {
                context.getString(R.string.achievement_widget_unnamed)
            },
            subline = listOfNotNull(
                record.wuyu.ifBlank { null },
                record.awardDate.ifBlank {
                    context.getString(R.string.achievement_widget_undated)
                },
                record.grade.takeIf { it.isNotBlank() }
            ).joinToString(" · "),
            wuyuColor = WidgetPalette.wuyuColor(record.wuyu).toArgb()
        )
    }
}
