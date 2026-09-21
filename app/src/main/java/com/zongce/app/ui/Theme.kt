// 暨存设计系统：品牌深蓝 + 苹果 HIG 语义色阶、Dynamic Type 字阶、4pt 间距与功能层材质。
//
// 设计意图：让"填综测时找材料"这件事安静、可信、不打扰。
// 数值依据 Apple Human Interface Guidelines（Liquid Glass 版）。
// 中文行高在 HIG 英文值基础上放宽约 8%：方块字视觉重心比拉丁字母高，
// 直接套用 1.3x 行高会显得拥挤，这是中文本地化必须做的补偿。
package com.zongce.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------------
// 色板
// ---------------------------------------------------------------------------

/**
 * 品牌主色沿用时保留的深蓝，但已补齐三档色阶。
 * 深色模式的提亮值不是简单调亮，而是重新选了明度让它在深底上仍达 4.5:1。
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF2D5F9A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE9FA),
    onPrimaryContainer = Color(0xFF12345D),

    secondary = Color(0xFF5B6D82),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE7EDF4),
    onSecondaryContainer = Color(0xFF1D2A38),

    // 暖琥珀：承载"待补充""边界日"这类提醒语义，白底上 4.6:1 可达标。
    tertiary = Color(0xFFA9601A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFCEDD8),
    onTertiaryContainer = Color(0xFF4A2A05),

    // 苹果红压暗一档：systemRed #FF3B30 在白底仅 3.5:1，不足以承载正文。
    error = Color(0xFFC93A34),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFE1DE),
    onErrorContainer = Color(0xFF4A0A08),

    // 页面底比卡片低一档，层级靠色差表达而不是阴影 —— iOS 分组列表的观感来源。
    background = Color(0xFFF4F6F9),
    onBackground = Color(0xFF18202B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF18202B),
    surfaceVariant = Color(0xFFEEF1F6),
    onSurfaceVariant = Color(0xFF5D6875),
    outline = Color(0xFFC7D0DA),
    outlineVariant = Color(0xFFE3E8EE),

    // 容器色阶必须显式覆盖：Material3 未覆盖时会回落到自带的紫调基线色，
    // 底部面板、对话框、Card 会集体漏出一层淡紫 —— 这正是"泛 AI 审美"的来源。
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFDCE1E8),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = Color(0xFFF2F5F9),
    surfaceContainerHigh = Color(0xFFECF0F5),
    surfaceContainerHighest = Color(0xFFE6EBF1),
    surfaceTint = Color(0xFF2D5F9A),
    inverseSurface = Color(0xFF2C3138),
    inverseOnSurface = Color(0xFFF1F3F6),
    inversePrimary = Color(0xFF8AB4E8),
    scrim = Color(0xFF000000)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AB4E8),
    onPrimary = Color(0xFF06203F),
    primaryContainer = Color(0xFF1B3D66),
    onPrimaryContainer = Color(0xFFD6E5FA),

    secondary = Color(0xFF9DAEBF),
    onSecondary = Color(0xFF101820),
    secondaryContainer = Color(0xFF2A3642),
    onSecondaryContainer = Color(0xFFDDE5EC),

    tertiary = Color(0xFFE0B075),
    onTertiary = Color(0xFF3A2306),
    tertiaryContainer = Color(0xFF52310E),
    onTertiaryContainer = Color(0xFFFBE3C4),

    error = Color(0xFFFF8A80),
    onError = Color(0xFF4A0A08),
    errorContainer = Color(0xFF5C1D19),
    onErrorContainer = Color(0xFFFFDAD6),

    background = Color(0xFF121417),
    onBackground = Color(0xFFE6E9ED),
    surface = Color(0xFF1C1F24),
    onSurface = Color(0xFFE6E9ED),
    surfaceVariant = Color(0xFF262A30),
    onSurfaceVariant = Color(0xFFA8B1BC),
    outline = Color(0xFF4A525C),
    outlineVariant = Color(0xFF33383F),

    surfaceBright = Color(0xFF33383F),
    surfaceDim = Color(0xFF121417),
    surfaceContainerLowest = Color(0xFF0D0F12),
    surfaceContainerLow = Color(0xFF1A1D21),
    surfaceContainer = Color(0xFF1E2126),
    surfaceContainerHigh = Color(0xFF282C32),
    surfaceContainerHighest = Color(0xFF33383F),
    surfaceTint = Color(0xFF8AB4E8),
    inverseSurface = Color(0xFFE6E9ED),
    inverseOnSurface = Color(0xFF2C3138),
    inversePrimary = Color(0xFF2D5F9A),
    scrim = Color(0xFF000000)
)

/**
 * 五育语义色：颜色本身就是信息，不是一个装饰点。
 * 同一育在列表圆点、详情标签、导出统计里必须是同一个色 —— 一致性即信任。
 * 深色模式统一提亮，保证作图形用时对比度 ≥ 3:1。
 */
object WuyuPalette {
    private val light = mapOf(
        "德育" to Color(0xFF3B7DD8),
        "智育" to Color(0xFF7A5AF8),
        "体育" to Color(0xFF2FA36B),
        "美育" to Color(0xFFE0603C),
        "劳育" to Color(0xFFC9912A)
    )
    private val dark = mapOf(
        "德育" to Color(0xFF6FA8F0),
        "智育" to Color(0xFFA48CFA),
        "体育" to Color(0xFF5FC896),
        "美育" to Color(0xFFF08A6B),
        "劳育" to Color(0xFFE0B45F)
    )
    private val fallbackLight = Color(0xFF8E8E93)
    private val fallbackDark = Color(0xFF9AA0A8)

    @Composable
    fun of(wuyu: String): Color =
        (if (isSystemInDarkTheme()) dark else light)[wuyu]
            ?: if (isSystemInDarkTheme()) fallbackDark else fallbackLight
}

// ---------------------------------------------------------------------------
// 字号体系（映射 iOS Dynamic Type 默认档）
// ---------------------------------------------------------------------------

/**
 * slot 名沿用 Material3，数值全部换成 HIG 档位 ——
 * 这样既有调用点不用改一处，视觉层级却被整体拉直了。
 * 强调一律靠字重（HIG：强调优先加字重，其次才加字号）。
 */
private val JicunTypography = Typography(
    // Large Title 34 / Title 1 28 / Title 2 22
    displayLarge = TextStyle(fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold),
    displayMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    displaySmall = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),

    // Title 3 20 / Title 3 20 / 页面大标题 28
    headlineLarge = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),

    // Title 3 20 / Headline 17 / Subhead Semibold 15
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),

    // Body 17 / Subhead 15 / Footnote 13
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 19.sp, fontWeight = FontWeight.Normal),

    // Subhead Semibold / Caption 1 / Caption 2
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal)
)

// ---------------------------------------------------------------------------
// 间距 / 圆角 / 高度
// ---------------------------------------------------------------------------

/**
 * 4pt 基准间距。页面左右边距固定用 [page]（20dp），
 * HIG 要求移动端全局一致 —— 边距忽宽忽窄是"廉价感"最主要的来源。
 */
object Space {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp

    /** 页面左右边距，全局统一 */
    val page = xl
    val xxl = 24.dp
    val xxxl = 32.dp

    /** 最小点击目标 44dp（HIG 下限） */
    val touch = 44.dp
}

private val JicunShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/**
 * 高度只给真正的浮层。卡片一律 0 —— 层级由底色差表达，
 * 满屏投影是让界面显得廉价的最快方式。
 */
object Elevation {
    val card = 0.dp
    val floating = 10.dp
    val sheet = 16.dp
}

/**
 * 动效参数（HIG 流体交互）。
 * 默认临界阻尼——不弹跳。弹跳只留给带手指动量的手势，
 * 无端回弹会让人怀疑"这个界面是不是没做完"。
 */
object Motion {
    /** 默认 UI 弹簧：damping 1.0，≈ response 0.35s */
    const val STIFFNESS = 400f
    const val DAMPING = 1f

    /** 底部面板：略快，≈ response 0.30s */
    const val STIFFNESS_SHEET = 500f

    /** 带动量的手势才允许的轻微回弹 */
    const val DAMPING_MOMENTUM = 0.8f
}

// ---------------------------------------------------------------------------
// 主题入口
// ---------------------------------------------------------------------------

/**
 * @param darkTheme 深色色板已就绪但默认不启用：页面上还有少量照片遮罩类硬编码色，
 *                  等这些清理干净再打开，避免出现半深半浅的中间态。
 */
@Composable
fun JicunTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = JicunTypography,
        shapes = JicunShapes,
        content = content
    )
}
