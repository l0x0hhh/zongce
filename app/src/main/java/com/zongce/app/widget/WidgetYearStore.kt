// 小组件当前展示学年的存储。
//
// 从旧 Glance 版 JicunAchievementWidget 里的 internal object AchievementYearStore
// 迁移为顶层 object：RemoteViews 版的 Provider（渲染头部）和 Service（过滤列表）
// 都要读写它，internal 私有可见性装不下两个使用方。
//
// 关键修正：写盘用 commit() 而不是 apply()。旧版 apply() 是异步落盘，选择器写完
// 立刻刷新组件时，另一处可能读到旧值 —— 这就是"选完学年组件不刷新"的根因。
// 现在箭头广播 → shift() 写盘 → 立即重渲染，全程同步，commit() 保证读到的是新值。
package com.zongce.app.widget

import android.content.Context
import com.zongce.app.core.AcademicYear
import com.zongce.app.data.AppDatabase
import kotlinx.coroutines.flow.first

object WidgetYearStore {

    // 与旧版一致的存储位置：老用户升级后已选的学年不丢。
    private const val PREFS = "jicun_achievement_widget"
    private const val KEY = "selected_year"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * 当前选中的学年。存的学年可能已经因为删记录而不存在了 ——
     * 这种情况回落到当前目标学年，否则小组件会永远停在一个空学年上。
     */
    fun current(context: Context, years: List<String>): String {
        val saved = prefs(context).getString(KEY, null)
        return if (saved != null && saved in years) saved else AcademicYear.LABEL
    }

    /**
     * 直接写入指定学年。App 内成果页的学年筛选与组件箭头共用这一份存储 ——
     * 此前只有 shift() 会写，App 内选完学年组件永远读不到，两边各显各的学年。
     * 与 shift() 同理用 commit() 同步落盘：调用方写完立刻推送组件重渲染，
     * 必须读到刚写入的值，apply() 的异步写盘做不到这一点。
     */
    fun set(context: Context, year: String) {
        prefs(context).edit().putString(KEY, year).commit()
    }

    /**
     * 沿学年列表移动一位（direction 为 -1 / +1）。
     * 每次重新查库算学年列表 —— 用户可能删过记录，列表以当前数据为准。
     * 越界时原地不动（coerceIn 把越界值夹回来，等于没动）。
     *
     * 注意：这里用 commit() 同步落盘。调用方（onReceive / pushUpdate 链路）
     * 写完立即重渲染组件，必须读到刚写入的值，apply() 的异步写盘做不到这一点。
     */
    suspend fun shift(context: Context, direction: Int) {
        val items = runCatching {
            AppDatabase.get(context).awardDao().allWithPhotos().first()
        }.getOrDefault(emptyList())
        val years = AcademicYear.yearsOf(items.map { it.record.awardDate })
        val index = years.indexOf(current(context, years))
        if (index < 0) return
        val next = (index + direction).coerceIn(0, years.lastIndex)
        prefs(context).edit().putString(KEY, years[next]).commit()
    }
}
