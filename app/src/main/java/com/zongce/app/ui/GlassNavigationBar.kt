// 暨存的液态玻璃底部导航：悬浮圆角、半透明、细描边。
// 选中反馈是"图标变实心 + 变品牌色" —— iOS 的做法。
// Material 默认会在选中项后面压一块胶囊色块，那层色块和半透明材质叠在一起会显脏，去掉。
package com.zongce.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

data class AppTab(
    val route: String,
    val label: String,
    /** 未选中：描边图标 */
    val icon: ImageVector,
    /** 选中：实心图标 */
    val selectedIcon: ImageVector
)

@Composable
fun JicunGlassNavigationBar(
    tabs: List<AppTab>,
    selectedRoute: String?,
    onSelect: (String) -> Unit
) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = Space.md, vertical = Space.sm)
            .shadow(
                elevation = Elevation.floating,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.08f),
                spotColor = Color.Black.copy(alpha = 0.10f)
            )
            .clip(shape)
            // Android 没有 backdrop blur（RenderEffect 只能作用于自身内容，糊不到背后的东西），
            // 所以这里用高不透明度表面模拟玻璃：透出一点底色，但不牺牲文字可读性。
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.86f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                shape = shape
            )
    ) {
        NavigationBar(
            modifier = Modifier.height(64.dp),
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            windowInsets = WindowInsets(0, 0, 0, 0)
        ) {
            tabs.forEach { tab ->
                val selected = selectedRoute == tab.route
                NavigationBarItem(
                    selected = selected,
                    onClick = { onSelect(tab.route) },
                    icon = {
                        Icon(
                            if (selected) tab.selectedIcon else tab.icon,
                            contentDescription = tab.label
                        )
                    },
                    label = { Text(tab.label) },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = Color.Transparent,
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

fun defaultAppTabs(): List<AppTab> = listOf(
    AppTab("capture", "拍摄", Icons.Outlined.PhotoCamera, Icons.Filled.PhotoCamera),
    AppTab("achievement", "成果", Icons.Outlined.EmojiEvents, Icons.Filled.EmojiEvents),
    AppTab("list", "记录", Icons.AutoMirrored.Outlined.List, Icons.AutoMirrored.Filled.List),
    AppTab("export", "导出", Icons.Outlined.Archive, Icons.Filled.Archive)
)
