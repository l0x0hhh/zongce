// 第二个桌面小组件：成果概览（RemoteViews 版）。
//
// 与 JicunWidget（拍照/相册入口）并列存在，两者职责刻意分开：
//   JicunWidget           —— 写入口。只发 Intent，不碰数据库（ADR-0001），绝对不动。
//   AchievementListWidget —— 只读展示。会读 Room，但绝不写入、不参与照片生命周期（ADR-0002）。
//
// 旧版是 Glance 实现：主体是一张静态卡片，切学年要弹透明壳 YearPickerActivity。
// 本次按用户诉求改为传统 RemoteViews：
//   1. 头部放 ‹ 学年 › 箭头，点击发广播就地切学年 —— 不出桌面、不弹 Activity；
//   2. 主体是 ListView（RemoteViewsService 喂数），手指可垂直滑动，组件拉大自动多显示几条；
//   3. 学年落盘改用 commit()（见 WidgetYearStore），修掉"选完学年组件不刷新"的异步写盘 bug。
//
// updatePeriodMillis 仍为 0，不做轮询 —— 桌面上的内容必须和 App 里看到的一致，
// 靠 AppViewModel.refreshWidget() → pushUpdate() 推送，不靠定时拉。
package com.zongce.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import com.zongce.app.MainActivity
import com.zongce.app.R
import com.zongce.app.WidgetActions
import com.zongce.app.core.AcademicYear
import com.zongce.app.data.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class AchievementListWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_PREV_YEAR = "com.zongce.app.action.WIDGET_YEAR_PREV"
        const val ACTION_NEXT_YEAR = "com.zongce.app.action.WIDGET_YEAR_NEXT"

        // 头部箭头的两种状态色：可点方向深墨、到头方向置灰（仍可点，但原地不动）。
        private val ArrowEnabled = 0xFF18202B.toInt()
        private val ArrowDisabled = 0xFFC7D0DA.toInt()

        /**
         * 数据（保存/删除记录）变化后由 AppViewModel 调用，把桌面上所有实例推成最新。
         * 组件没有实例时（用户还没添加到桌面）直接返回，不做无谓的查库。
         */
        fun pushUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, AchievementListWidget::class.java)
            )
            if (ids.isEmpty()) return
            for (id in ids) updateOne(context, manager, id)
            // 列表数据可能也变了（新记录/删记录），通知所有实例重新走 onDataSetChanged。
            manager.notifyAppWidgetViewDataChanged(ids, R.id.achievement_list)
        }

        /**
         * 渲染单个组件实例：头部（学年 + 箭头状态）+ 列表挂接。
         * 在主线程调用时查库用 runBlocking —— 数据量小（<10ms），见 WidgetYearStore 注释。
         */
        private fun updateOne(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val years = loadYears(context)
            val year = WidgetYearStore.current(context, years)
            val index = years.indexOf(year).coerceAtLeast(0)

            val views = RemoteViews(context.packageName, R.layout.widget_achievement_list).apply {
                setTextViewText(R.id.widget_year, year)
                // 到头的方向置灰提示"没有更早/更晚的学年"；可点方向保持深色。
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

                // RemoteViews 列表不刷新的经典坑：喂给 setRemoteAdapter 的 Intent
                // 如果和上次相等，系统会复用旧 Adapter，onDataSetChanged 根本不跑。
                // 每次造一个唯一 Uri 强制重建。3 参重载 API 31 才有，低版本走旧签名。
                val listIntent = Intent(context, AchievementListService::class.java).apply {
                    data = Uri.fromParts("content", "achievement_${System.nanoTime()}", null)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRemoteAdapter(appWidgetId, R.id.achievement_list, listIntent)
                } else {
                    @Suppress("DEPRECATION")
                    setRemoteAdapter(R.id.achievement_list, listIntent)
                }
                setEmptyView(R.id.achievement_list, R.id.achievement_empty)

                // 条目点击模板：fill-in Intent 不带 action，最终生效的就是这里的
                // OPEN_ACHIEVEMENT —— MainActivity 靠它落到成果 tab。
                val openAchievement = PendingIntent.getActivity(
                    context,
                    appWidgetId,
                    Intent(context, MainActivity::class.java)
                        .setAction(WidgetActions.OPEN_ACHIEVEMENT)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                setPendingIntentTemplate(R.id.achievement_list, openAchievement)
            }
            manager.updateAppWidget(appWidgetId, views)
        }

        /** 全量记录推出的学年列表（降序、必含当前学年）。查库失败按"只有当前学年"处理。 */
        private fun loadYears(context: Context): List<String> {
            val items = runCatching {
                runBlocking { AppDatabase.get(context).awardDao().allWithPhotos().first() }
            }.getOrDefault(emptyList())
            return AcademicYear.yearsOf(items.map { it.record.awardDate })
        }

        /** 箭头点击 → 广播回自身。data 唯一化，避免不同 action/实例的 PendingIntent 互相顶掉。 */
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
        for (id in appWidgetIds) updateOne(context, appWidgetManager, id)
    }

    /**
     * 处理箭头广播：shift 写盘（commit 同步）后立刻重渲染所有实例。
     * 广播在主线程到达，WidgetYearStore.shift 内部查库很小，runBlocking 足够。
     */
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val direction = when (intent.action) {
            ACTION_PREV_YEAR -> -1
            ACTION_NEXT_YEAR -> 1
            else -> return
        }
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(
            ComponentName(context, AchievementListWidget::class.java)
        )
        if (ids.isEmpty()) return

        runBlocking { WidgetYearStore.shift(context, direction) }

        // 头部（学年文字 + 箭头灰态）和列表（换了一组条目）都要变，两步各管各的。
        for (id in ids) updateOne(context, manager, id)
        manager.notifyAppWidgetViewDataChanged(ids, R.id.achievement_list)
    }
}
