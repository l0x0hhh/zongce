// 组件箭头动作：在桌面直接切换组件学年，并刷新当前成果快照。
package com.zongce.app.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.zongce.app.data.WidgetYearStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.job
import java.util.concurrent.ConcurrentHashMap

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
        // 连点时只保留最后一次重绘请求；状态已立即写入，停手后再提交一次 RemoteViews。
        scheduleRefresh(app, glanceId)
    }

    companion object {
        val DELTA_KEY = ActionParameters.Key<Int>("widget_year_delta")
        private const val REFRESH_DEBOUNCE_MS = 180L
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val pendingRefreshes = ConcurrentHashMap<String, Job>()

        private fun scheduleRefresh(context: Context, glanceId: GlanceId) {
            val key = glanceId.toString()
            pendingRefreshes.remove(key)?.cancel()
            pendingRefreshes[key] = scope.launch {
                delay(REFRESH_DEBOUNCE_MS)
                try {
                    WidgetRefresh.refresh(context, glanceId)
                } finally {
                    // 仅移除当前任务，避免被已取消的旧任务删掉新任务。
                    pendingRefreshes.remove(key, currentCoroutineContext().job)
                }
            }
        }
    }
}
