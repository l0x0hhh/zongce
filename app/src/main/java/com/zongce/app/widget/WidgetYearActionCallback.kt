// 组件箭头动作：在桌面直接切换组件学年，并刷新当前成果快照。
package com.zongce.app.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.zongce.app.core.AcademicYear
import com.zongce.app.data.AppDatabase
import com.zongce.app.data.WidgetYearStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class WidgetYearActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val delta = parameters[DELTA_KEY] ?: return
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val app = context.applicationContext
                val items = AppDatabase.get(app).awardDao().allWithPhotos().first()
                val years = AcademicYear.yearsOf(items.map { it.record.awardDate })
                WidgetYearStore.move(app, years, delta)
                // 只更新被点击的实例，避免 updateAll() 让多个组件实例排队重绘。
                WidgetRefresh.refresh(app, glanceId)
            }
        }
    }

    companion object {
        val DELTA_KEY = ActionParameters.Key<Int>("widget_year_delta")
        private val mutex = Mutex()
    }
}
