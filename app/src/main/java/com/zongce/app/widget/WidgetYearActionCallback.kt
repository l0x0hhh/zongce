// 组件学年选择：用明确学年替代增减量，写入实例状态后立即在回调内请求刷新。
package com.zongce.app.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState

class WidgetYearActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val app = context.applicationContext
        val requestedYear = parameters[YEAR_KEY]
        val pickerVisible = parameters[PICKER_VISIBLE_KEY]
        if (requestedYear == null && pickerVisible == null) return

        // 展开/收起只写 UI 状态；选学年用缓存校验，避免正常点击重复查询 Room。
        val validYear = requestedYear?.let { year ->
            val cached = AchievementWidgetCache.read(app) ?: AchievementWidgetCache.rebuild(app)
            year.takeIf { it in cached.years }
        }
        updateAppWidgetState(app, glanceId) { preferences ->
            if (validYear != null) {
                preferences[AchievementWidgetState.selectedYear] = validYear
                preferences[AchievementWidgetState.yearPickerVisible] = false
            } else if (pickerVisible != null) {
                preferences[AchievementWidgetState.yearPickerVisible] = pickerVisible
            }
        }
        // 不启动脱离回调生命周期的延迟任务，状态落盘后直接提交本实例的更新请求。
        WidgetRefresh.refresh(app, glanceId)
    }

    companion object {
        val YEAR_KEY = ActionParameters.Key<String>("widget_selected_year")
        val PICKER_VISIBLE_KEY = ActionParameters.Key<Boolean>("widget_year_picker_visible")
    }
}
