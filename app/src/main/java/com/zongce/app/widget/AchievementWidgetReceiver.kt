// 第二个桌面小组件的接收器：把「我的成果」组件挂到系统的 AppWidget 广播上。
// 与 JicunWidgetReceiver 各管一个组件，互不影响。
package com.zongce.app.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class AchievementWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AchievementWidget()
}
