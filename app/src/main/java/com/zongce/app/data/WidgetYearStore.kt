// 桌面“我的成果”组件独立保存学年，避免覆盖 App 成果页的学年偏好。
package com.zongce.app.data

import android.content.Context
import com.zongce.app.core.AcademicYear

object WidgetYearStore {
    private const val PREFS = "jicun_achievement_widget_selection"
    private const val KEY = "selected_year"
    private val lock = Any()

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun current(context: Context, years: List<String>): String = synchronized(lock) {
        val saved = prefs(context).getString(KEY, null)
        if (saved != null && saved in years) saved else AcademicYear.LABEL
    }

    fun set(context: Context, year: String) = synchronized(lock) {
        prefs(context).edit().putString(KEY, year).commit()
    }

    fun move(context: Context, years: List<String>, delta: Int): String = synchronized(lock) {
        val current = current(context, years)
        val index = years.indexOf(current).coerceAtLeast(0)
        val target = (index + delta).coerceIn(0, (years.size - 1).coerceAtLeast(0))
        years[target].also { set(context, it) }
    }
}
