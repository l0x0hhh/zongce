// 桌面组件刷新入口：所有会改变组件展示内容的写路径都从这里主动触发重绘。
package com.zongce.app.widget

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.appwidget.updateAll

object WidgetRefresh {
    suspend fun refresh(context: Context) {
        runCatching {
            Log.d("WidgetRefresh", "开始刷新成果组件")
            AchievementWidget().updateAll(context.applicationContext)
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
