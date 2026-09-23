// 成果页当前展示学年的持久化偏好。
//
// 由原 widget 包下那个组件学年存储下沉而来：它原本是「成果概览」桌面组件的学年存储，
// 组件在 v1.4.0 被整体移除（见 docs/design/system_design.md T01），而"成果页记住上次选的
// 学年"这个体验必须留下 —— 于是把它降格成纯 UI 偏好，挪进 data 包，不再与组件耦合。
//
// 两处刻意保持不变，因为它们直接决定老用户升级后学年在不在：
//   · SharedPreferences 名 jicun_achievement_widget（名字里带 widget 是历史遗留，改了就读不到旧值）
//   · 键名 selected_year
// 写盘继续用 commit() 同步落盘：写入后紧接着就要被读（刷新成果页 / 下次进页面回读初值），
// apply() 的异步落盘做不到"写完立刻能读到新值"，这正是 v1.3.6 修过的"选完学年不生效"的根因。
package com.zongce.app.data

import android.content.Context
import com.zongce.app.core.AcademicYear

object AchievementYearStore {

    private const val PREFS = "jicun_achievement_widget"
    private const val KEY = "selected_year"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * 当前选中的学年。存的学年可能已经因为删记录而不存在了 ——
     * 这种情况回落到当前目标学年，否则成果页会永远停在一个空学年上。
     */
    fun current(context: Context, years: List<String>): String {
        val saved = prefs(context).getString(KEY, null)
        return if (saved != null && saved in years) saved else AcademicYear.LABEL
    }

    /** 写入学年。调用方（AppViewModel 的单消费者队列）保证串行，这里只负责同步落盘。 */
    fun set(context: Context, year: String) {
        prefs(context).edit().putString(KEY, year).commit()
    }
}
