// 桌面小组件与主页面之间的轻量入口协议，不承载照片或业务数据。
package com.zongce.app

object WidgetActions {
    const val CAPTURE = "com.zongce.app.action.WIDGET_CAPTURE"
    const val PICK_PHOTOS = "com.zongce.app.action.WIDGET_PICK_PHOTOS"

    fun isEntryAction(action: String?): Boolean =
        action == CAPTURE || action == PICK_PHOTOS
}
