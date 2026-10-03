// 桌面组件刷新入口：所有会改变组件展示内容的写路径都从这里主动触发重绘。
package com.zongce.app.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll

object WidgetRefresh {
    suspend fun refresh(context: Context) {
        runCatching {
            AchievementWidget().updateAll(context.applicationContext)
        }.onFailure { error ->
            Log.w("WidgetRefresh", "刷新成果组件失败，保留系统下次重绘机会", error)
        }
    }
}
