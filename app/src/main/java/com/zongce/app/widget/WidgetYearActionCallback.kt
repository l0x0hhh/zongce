// 组件箭头动作：在桌面直接切换组件学年，并刷新当前成果快照。
package com.zongce.app.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.zongce.app.data.WidgetYearStore

class WidgetYearActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val delta = parameters[DELTA_KEY] ?: return
        val app = context.applicationContext
        val cached = AchievementWidgetCache.read(app)
            ?: AchievementWidgetCache.rebuild(app)
        if (cached.years.isEmpty()) return
        var targetYear: String? = null
        updateAppWidgetState(app, glanceId) { preferences ->
            val current = preferences[AchievementWidgetState.selectedYear]
                ?.takeIf { it in cached.years }
                ?: WidgetYearStore.current(app, cached.years).takeIf { it in cached.years }
                ?: cached.years.first()
            val currentIndex = cached.years.indexOf(current)
            val targetIndex = (currentIndex + delta).coerceIn(0, cached.years.lastIndex)
            targetYear = cached.years[targetIndex]
            preferences[AchievementWidgetState.selectedYear] = targetYear!!
        }
        // 状态写入完成后只更新当前实例，不查询 Room、不刷新全部组件。
        WidgetRefresh.refresh(app, glanceId)
    }

    companion object {
        val DELTA_KEY = ActionParameters.Key<Int>("widget_year_delta")
    }
}
