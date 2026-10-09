// 组件刷新入口：数据更新递增实例版本，确保活跃 Glance 会话重新读取成果缓存。
package com.zongce.app.widget

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.appwidget.updateAll
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState

object WidgetRefresh {
    suspend fun refresh(context: Context) {
        val app = context.applicationContext
        runCatching {
            // 保存/删除完成后先更新缓存，学年选择才能绕开 Room 查询。
            AchievementWidgetCache.rebuild(app)
        }.onFailure { error ->
            Log.e("WidgetRefresh", "重建成果组件缓存失败，将继续尝试刷新", error)
        }
        runCatching {
            GlanceAppWidgetManager(app).getGlanceIds(AchievementWidget::class.java).forEach { id ->
                updateAppWidgetState(app, id) { preferences ->
                    preferences[AchievementWidgetState.contentRevision] =
                        (preferences[AchievementWidgetState.contentRevision] ?: 0L) + 1L
                }
            }
            Log.d("WidgetRefresh", "开始刷新成果组件")
            AchievementWidget().updateAll(app)
            Log.d("WidgetRefresh", "成果组件刷新请求已提交")
        }.onFailure { error ->
            Log.e("WidgetRefresh", "刷新成果组件失败", error)
        }
    }

    suspend fun refresh(context: Context, glanceId: GlanceId) {
        runCatching {
            Log.d("WidgetRefresh", "刷新成果组件实例: $glanceId")
            AchievementWidget().update(context.applicationContext, glanceId)
        }.onFailure { error ->
            Log.e("WidgetRefresh", "刷新成果组件实例失败", error)
        }
    }
}
