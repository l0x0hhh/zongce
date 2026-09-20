// 暨存的轻档案视觉主题：米白底、深墨文字、蓝色主色和暖金强调。
package com.zongce.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val JicunLightColors = lightColorScheme(
    primary = Color(0xFF2D5F9A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDEAFF),
    onPrimaryContainer = Color(0xFF12345D),
    secondary = Color(0xFF5B6D82),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE7EDF4),
    onSecondaryContainer = Color(0xFF1D2A38),
    tertiary = Color(0xFFAD762A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF7E6C7),
    onTertiaryContainer = Color(0xFF3C280E),
    error = Color(0xFFB94B47),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD7),
    onErrorContainer = Color(0xFF410003),
    background = Color(0xFFF6F8FA),
    onBackground = Color(0xFF1B2430),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B2430),
    surfaceVariant = Color(0xFFEDF1F5),
    onSurfaceVariant = Color(0xFF5D6875),
    outline = Color(0xFFC7D0DA),
    outlineVariant = Color(0xFFE0E5EB)
)

private val JicunShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun JicunTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JicunLightColors,
        typography = Typography(),
        shapes = JicunShapes,
        content = content
    )
}
