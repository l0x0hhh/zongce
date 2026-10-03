// 桌面小组件专用色值。
//
// 为什么必须写死而不是读 MaterialTheme：RemoteViews 由**桌面进程**渲染，拿不到 App 的
// CompositionLocal / 深色模式设置，所以组件里的每一处颜色都得是编译期常量。
//
// 这份常量是§8.3 色值表在代码里的唯一落点：组件 UI、T04 的 preview XML、以及
// tmp/gen_widget_previews.py 三处必须逐字一致。改色值只改这里，另两处跟着改。
package com.zongce.app.widget

import androidx.compose.ui.graphics.Color

object WidgetPalette {

    /** 卡片底 / 行底 */
    val Card = Color(0xFFFFFFFF)

    /** 主文字（名称 / 数字 / 箭头）——统一口径，旧值 #18202B 是历史漂移 */
    val Ink = Color(0xFF1B2430)

    /** 主色 */
    val Primary = Color(0xFF2D5F9A)

    /** 统计行底 */
    val StatTile = Color(0xFFDDEAFF)

    /** 次要文字（学年标签 / 副信息） */
    val SecondaryText = Color(0xFF5D6875)

    /** 箭头禁用 / 空态图标 */
    val Disabled = Color(0xFFC7D0DA)

    /** 分隔线 */
    val Divider = Color(0xFFE6EBF1)

    // 五育色点：与 ui/Theme.kt 的 WuyuPalette.light 逐字一致，不跟随深色模式。
    // 桌面进程拿不到系统深色态，跟了反而会让同一条记录在两个进程里颜色不一样。
    private val WUYU_LIGHT = mapOf(
        "德育" to Color(0xFF3B7DD8),
        "智育" to Color(0xFF7A5AF8),
        "体育" to Color(0xFF2FA36B),
        "美育" to Color(0xFFE0603C),
        "劳育" to Color(0xFFC9912A)
    )

    private val WUYU_FALLBACK = Color(0xFF8E8E93)

    /** 取某一育的色点颜色；未知/空的五育回落成灰色，不抛异常。 */
    fun wuyuColor(wuyu: String): Color = WUYU_LIGHT[wuyu] ?: WUYU_FALLBACK
}
