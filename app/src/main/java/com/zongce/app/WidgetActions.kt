// 桌面小组件与主页面之间的轻量入口协议，不承载照片或业务数据。
package com.zongce.app

object WidgetActions {
    const val CAPTURE = "com.zongce.app.action.WIDGET_CAPTURE"
    const val PICK_PHOTOS = "com.zongce.app.action.WIDGET_PICK_PHOTOS"

    /** 点小组件主体：直接进成果页。 */
    const val OPEN_ACHIEVEMENT = "com.zongce.app.action.WIDGET_ACHIEVEMENT"

    /** 需要真正唤起相机/相册的入口（实际动作由 CaptureScreen 执行）。 */
    fun isEntryAction(action: String?): Boolean =
        action == CAPTURE || action == PICK_PHOTOS

    /** 全部来自小组件的 action —— MainActivity 用它决定拦截哪些 Intent。 */
    fun isWidgetAction(action: String?): Boolean =
        isEntryAction(action) || action == OPEN_ACHIEVEMENT
}
