// 成果概览小组件：用 Provider 一次性填充固定摘要，避免桌面对 RemoteViewsService 的兼容差异。
package com.zongce.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.zongce.app.MainActivity
import com.zongce.app.R
import com.zongce.app.WidgetActions
import com.zongce.app.core.AcademicYear
import com.zongce.app.data.AppDatabase
import com.zongce.app.data.RecordWithPhotos
import com.zongce.app.data.Wuyu
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AchievementListWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_PREV_YEAR = "com.zongce.app.action.WIDGET_YEAR_PREV"
        const val ACTION_NEXT_YEAR = "com.zongce.app.action.WIDGET_YEAR_NEXT"

        private const val MAX_SUMMARY_ROWS = 3
        private val ArrowEnabled = 0xFF18202B.toInt()
        private val ArrowDisabled = 0xFFC7D0DA.toInt()

        private data class RowIds(
            val root: Int,
            val dot: Int,
            val name: Int,
            val meta: Int
        )

        private val SummaryRows = listOf(
            RowIds(R.id.widget_row_1, R.id.widget_row_dot_1, R.id.widget_row_name_1, R.id.widget_row_meta_1),
            RowIds(R.id.widget_row_2, R.id.widget_row_dot_2, R.id.widget_row_name_2, R.id.widget_row_meta_2),
            RowIds(R.id.widget_row_3, R.id.widget_row_dot_3, R.id.widget_row_name_3, R.id.widget_row_meta_3)
        )

        /** 保存/删除记录后主动推送所有已添加的成果组件。调用方应在 IO 协程中执行。 */
        suspend fun pushUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, AchievementListWidget::class.java)
            )
            if (ids.isEmpty()) return
            for (id in ids) updateOne(context, manager, id)
        }

        /** 在 IO 线程读取一次 Room，然后把当前学年的摘要直接写进 RemoteViews。 */
        private suspend fun updateOne(
            context: Context,
            manager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val allItems = loadItems(context)
            val years = AcademicYear.yearsOf(allItems.map { it.record.awardDate })
            val year = WidgetYearStore.current(context, years)
            val yearItems = allItems
                .filter { AcademicYear.belongsTo(it.record.awardDate, year) }
                .sortedByDescending { it.record.awardDate }
            val summaryItems = yearItems.take(MAX_SUMMARY_ROWS)
            val openAchievement = openAchievementPendingIntent(context, appWidgetId)
            val index = years.indexOf(year).coerceAtLeast(0)

            val views = RemoteViews(context.packageName, R.layout.widget_achievement_list).apply {
                setTextViewText(R.id.widget_year, year)
                setTextViewText(R.id.widget_count, "共 ${yearItems.size} 条")
                setTextColor(
                    R.id.widget_prev_year,
                    if (index <= 0) ArrowDisabled else ArrowEnabled
                )
                setTextColor(
                    R.id.widget_next_year,
                    if (index >= years.lastIndex) ArrowDisabled else ArrowEnabled
                )
                setOnClickPendingIntent(
                    R.id.widget_prev_year,
                    yearShiftPendingIntent(context, appWidgetId, ACTION_PREV_YEAR)
                )
                setOnClickPendingIntent(
                    R.id.widget_next_year,
                    yearShiftPendingIntent(context, appWidgetId, ACTION_NEXT_YEAR)
                )
                setViewVisibility(
                    R.id.achievement_empty,
                    if (yearItems.isEmpty()) View.VISIBLE else View.GONE
                )

                SummaryRows.forEachIndexed { rowIndex, row ->
                    bindRow(this, row, summaryItems.getOrNull(rowIndex), openAchievement)
                }
            }
            manager.updateAppWidget(appWidgetId, views)
        }

        private fun bindRow(
            views: RemoteViews,
            row: RowIds,
            item: RecordWithPhotos?,
            openAchievement: PendingIntent
        ) {
            if (item == null) {
                views.setViewVisibility(row.root, View.GONE)
                return
            }

            val record = item.record
            views.setViewVisibility(row.root, View.VISIBLE)
            views.setTextViewText(row.name, record.awardName.ifBlank { "未填写获奖名称" })
            views.setTextViewText(
                row.meta,
                "${record.wuyu.ifBlank { "未分类" }} · ${record.awardDate.ifBlank { "未填写时间" }}"
            )
            views.setTextColor(row.dot, dotColor(record.wuyu))
            views.setOnClickPendingIntent(row.root, openAchievement)
        }

        private suspend fun loadItems(context: Context): List<RecordWithPhotos> = runCatching {
            AppDatabase.get(context).awardDao().allWithPhotos().first()
        }.getOrDefault(emptyList())

        private fun openAchievementPendingIntent(
            context: Context,
            appWidgetId: Int
        ): PendingIntent = PendingIntent.getActivity(
            context,
            appWidgetId,
            Intent(context, MainActivity::class.java)
                .setAction(WidgetActions.OPEN_ACHIEVEMENT)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        /** 箭头点击回到本 Provider；data 唯一化避免不同实例互相覆盖 PendingIntent。 */
        private fun yearShiftPendingIntent(
            context: Context,
            appWidgetId: Int,
            action: String
        ): PendingIntent {
            val intent = Intent(context, AchievementListWidget::class.java).apply {
                this.action = action
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.fromParts("content", "${action}_$appWidgetId", null)
            }
            return PendingIntent.getBroadcast(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                for (id in appWidgetIds) updateOne(context, appWidgetManager, id)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /** 切换学年后在后台重绘标题、总数和三条摘要。 */
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val direction = when (intent.action) {
            ACTION_PREV_YEAR -> -1
            ACTION_NEXT_YEAR -> 1
            else -> return
        }
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(
                    ComponentName(context, AchievementListWidget::class.java)
                )
                if (ids.isEmpty()) return@launch

                WidgetYearStore.shift(context, direction)
                for (id in ids) updateOne(context, manager, id)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

private fun dotColor(wuyu: String): Int = when (wuyu) {
    Wuyu.DE -> 0xFF3B7DD8.toInt()
    Wuyu.ZHI -> 0xFF7A5AF8.toInt()
    Wuyu.TI -> 0xFF2FA36B.toInt()
    Wuyu.MEI -> 0xFFE0603C.toInt()
    Wuyu.LAO -> 0xFFC9912A.toInt()
    else -> 0xFF8E8E93.toInt()
}
