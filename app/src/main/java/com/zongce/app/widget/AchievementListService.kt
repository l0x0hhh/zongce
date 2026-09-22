// 成果概览小组件（RemoteViews 版）的列表数据源。
//
// RemoteViews 列表（ListView）的数据由 RemoteViewsService 提供：launcher 进程
// 绑定本服务拿到工厂，工厂在 onDataSetChanged 里查库、在 getViewAt 里产出条目。
// 组件本体在 AchievementListWidget 里，这里只负责"喂列表"。
package com.zongce.app.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.zongce.app.R
import com.zongce.app.core.AcademicYear
import com.zongce.app.data.AppDatabase
import com.zongce.app.data.RecordWithPhotos
import com.zongce.app.data.Wuyu
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class AchievementListService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        ListFactory(applicationContext)

    /**
     * 列表条目数据源。
     *
     * onDataSetChanged / getViewAt 都在主线程回调里执行，查库用 runBlocking：
     * 数据量小（个人证书记录通常几十条以内，<10ms），不值得为此引入异步桥。
     * 每次数据变化系统都会重新调 onDataSetChanged，不依赖任何缓存。
     */
    private class ListFactory(private val context: Context) : RemoteViewsFactory {

        private var items: List<RecordWithPhotos> = emptyList()

        override fun onCreate() {
            // 无事可做：真正的取数在 onDataSetChanged，它保证每次刷新都会重跑。
        }

        override fun onDestroy() {
            items = emptyList()
        }

        override fun onDataSetChanged() {
            val all = runCatching {
                runBlocking { AppDatabase.get(context).awardDao().allWithPhotos().first() }
            }.getOrDefault(emptyList())

            // 与成果页同一套口径：学年列表按实际数据算（必含当前学年），
            // 再按选中学年过滤、按获奖时间倒序。
            val years = AcademicYear.yearsOf(all.map { it.record.awardDate })
            val year = WidgetYearStore.current(context, years)
            items = all
                .filter { AcademicYear.belongsTo(it.record.awardDate, year) }
                .sortedByDescending { it.record.awardDate }
        }

        override fun getCount(): Int = items.size

        override fun getViewAt(position: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_achievement_item)
            val item = items.getOrNull(position) ?: return views

            val record = item.record
            views.setTextViewText(
                R.id.widget_item_name,
                record.awardName.ifBlank { "未填写获奖名称" }
            )
            views.setTextViewText(
                R.id.widget_item_meta,
                "${record.wuyu} · ${record.awardDate.ifBlank { "未填写时间" }}"
            )
            views.setTextColor(R.id.widget_item_dot, dotColor(record.wuyu))
            // fill-in Intent 只负责"这个条目被点了"，去哪里由组件本体上的
            // PendingIntent 模板（打开成果页）决定。
            views.setOnClickFillInIntent(R.id.widget_item_root, Intent())

            return views
        }

        override fun getLoadingView(): RemoteViews? = null

        override fun getViewTypeCount(): Int = 1

        override fun getItemId(position: Int): Long = position.toLong()

        override fun hasStableIds(): Boolean = false
    }
}

// 小组件由桌面进程渲染，拿不到 Compose 主题（Theme.kt 的 WuyuColors），
// 五育色值只能显式写死。这组值与 Theme.kt 浅色版保持一致。
private fun dotColor(wuyu: String): Int = when (wuyu) {
    Wuyu.DE -> 0xFF3B7DD8.toInt()   // 德育 蓝
    Wuyu.ZHI -> 0xFF7A5AF8.toInt()  // 智育 紫
    Wuyu.TI -> 0xFF2FA36B.toInt()   // 体育 绿
    Wuyu.MEI -> 0xFFE0603C.toInt()  // 美育 橙
    Wuyu.LAO -> 0xFFC9912A.toInt()  // 劳育 金
    else -> 0xFF8E8E93.toInt()      // 兜底 系统灰
}
