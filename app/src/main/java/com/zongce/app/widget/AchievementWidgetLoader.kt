// 组件的数据装载：一次快照同时提供学年、选择器状态和成果内容。
//
// 为什么不做 Flow 订阅：provideGlance 跑在 WorkManager 的 CoroutineWorker 里，
// provideContent 后会话时间有限，组件不持续订阅数据库。
// 数据变了由写路径更新缓存和实例版本，活跃会话重组时读取新快照。
package com.zongce.app.widget

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.compose.ui.graphics.toArgb
import com.zongce.app.R
import com.zongce.app.core.AcademicYear
import com.zongce.app.data.WidgetYearStore
import kotlinx.coroutines.Dispatchers
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
    val hasMore: Boolean,
    val yearPickerVisible: Boolean = false
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
    suspend fun load(context: Context, glanceId: GlanceId? = null): AchievementWidgetSnapshot =
        withContext(Dispatchers.IO) {
            runCatching {
                val app = context.applicationContext
                // 学年选择只读缓存；首次绑定或缓存丢失时才回源 Room。
                val cached = AchievementWidgetCache.read(app)
                    ?: AchievementWidgetCache.rebuild(app)
                val state = glanceId?.let {
                    getAppWidgetState(app, PreferencesGlanceStateDefinition, it)
                }
                buildSnapshot(app, cached, state)
            }.getOrElse { AchievementWidgetSnapshot.FALLBACK }
        }

    /** 重组只从已预热的缓存生成画面，不查询 Room；实例状态变化后不会重用旧学年快照。 */
    fun fromCache(
        context: Context,
        state: Preferences,
        fallback: AchievementWidgetSnapshot
    ): AchievementWidgetSnapshot =
        AchievementWidgetCache.read(context.applicationContext)?.let { buildSnapshot(context, it, state) }
            ?: fallback.copy(yearPickerVisible = state[AchievementWidgetState.yearPickerVisible] ?: false)

    private fun buildSnapshot(
        context: Context,
        cached: AchievementWidgetCacheSnapshot,
        state: Preferences?
    ): AchievementWidgetSnapshot {
        val years = cached.years
        val selected = state?.get(AchievementWidgetState.selectedYear) ?: WidgetYearStore.current(context, years)
        val year = selected.takeIf { it in years } ?: AcademicYear.LABEL
        val ofYear = AcademicYear.inYear(cached.records, year) { it.awardDate }
            .sortedByDescending { it.awardDate }
        val rows = ofYear.take(AchievementWidget.WIDGET_MAX_ROWS).map { it.toRow(context) }
        return AchievementWidgetSnapshot(
            years = years,
            year = year,
            recordCount = ofYear.size,
            coveredWuyu = ofYear.map { it.wuyu }.filter { it.isNotBlank() }.distinct().count(),
            rows = rows,
            hasMore = ofYear.size > rows.size,
            yearPickerVisible = state?.get(AchievementWidgetState.yearPickerVisible) ?: false
        )
    }

    private fun CachedAchievementRecord.toRow(context: Context): AchievementWidgetRow {
        return AchievementWidgetRow(
            id = id,
            name = awardName.ifBlank {
                context.getString(R.string.achievement_widget_unnamed)
            },
            subline = listOfNotNull(
                wuyu.ifBlank { null },
                awardDate.ifBlank {
                    context.getString(R.string.achievement_widget_undated)
                },
                grade.takeIf { it.isNotBlank() }
            ).joinToString(" · "),
            wuyuColor = WidgetPalette.wuyuColor(wuyu).toArgb()
        )
    }
}
