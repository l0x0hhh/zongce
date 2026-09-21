// 成果概览小组件的接收器：只负责把 Glance 的组件本体挂到系统的 AppWidget 广播上。
package com.zongce.app.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class JicunAchievementWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = JicunAchievementWidget()
}
