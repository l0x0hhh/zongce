// 应用导航、页面过渡、品牌主题和更新入口。
package com.zongce.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zongce.app.ui.AchievementScreen
import com.zongce.app.ui.AppViewModel
import com.zongce.app.ui.CaptureScreen
import com.zongce.app.ui.EntryScreen
import com.zongce.app.ui.ExportScreen
import com.zongce.app.ui.JicunGlassNavigationBar
import com.zongce.app.ui.JicunTheme
import com.zongce.app.ui.ListScreen
import com.zongce.app.ui.UpdateDialog
import com.zongce.app.ui.defaultAppTabs
import com.zongce.app.update.UpdateChecker

private const val ROUTE_CAPTURE = "capture"
private const val ROUTE_ACHIEVEMENT = "achievement"
private const val ROUTE_LIST = "list"
private const val ROUTE_EXPORT = "export"
private const val ROUTE_ENTRY = "entry/{recordId}"

class MainActivity : ComponentActivity() {
    private var widgetAction by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetAction = savedInstanceState?.getString("widgetAction")
            ?: intent?.action?.takeIf(WidgetActions::isWidgetAction)
        enableEdgeToEdge()
        setContent {
            JicunTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    App(
                        widgetAction = widgetAction,
                        onWidgetActionConsumed = { widgetAction = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        widgetAction = intent.action?.takeIf(WidgetActions::isWidgetAction)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("widgetAction", widgetAction)
        super.onSaveInstanceState(outState)
    }
}

@Composable
private fun App(
    vm: AppViewModel = viewModel(),
    widgetAction: String? = null,
    onWidgetActionConsumed: () -> Unit = {}
) {
    val nav = rememberNavController()
    val items by vm.items.collectAsState()
    val updateState by vm.updateState.collectAsState()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val context = LocalContext.current

    val tabs = defaultAppTabs()

    // 底部 tab 之间切换：保持各自的滚动位置，不堆栈。
    val selectTab: (String) -> Unit = { route ->
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // 小组件来的 action 分两路：录入类先切到拍摄页（实际动作由 CaptureScreen 触发），
    // 浏览类直接落到成果页。
    androidx.compose.runtime.LaunchedEffect(widgetAction) {
        when (widgetAction) {
            WidgetActions.CAPTURE, WidgetActions.PICK_PHOTOS ->
                if (currentRoute != ROUTE_CAPTURE) selectTab(ROUTE_CAPTURE)
            WidgetActions.OPEN_ACHIEVEMENT -> selectTab(ROUTE_ACHIEVEMENT)
        }
    }

    Scaffold(
        bottomBar = {
            if (currentRoute in tabs.map { it.route }) {
                JicunGlassNavigationBar(
                    tabs = tabs,
                    selectedRoute = currentRoute,
                    onSelect = selectTab
                )
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = ROUTE_CAPTURE,
            modifier = Modifier.padding(padding)
        ) {
            composable(
                route = ROUTE_CAPTURE,
                enterTransition = { fadeIn(tween(180)) },
                exitTransition = { fadeOut(tween(120)) }
            ) {
                CaptureScreen(
                    vm = vm,
                    recordCount = items.size,
                    widgetAction = widgetAction,
                    onWidgetActionConsumed = onWidgetActionConsumed,
                    onGoEntry = { nav.navigate("entry/0") },
                    onGoAchievement = { selectTab(ROUTE_ACHIEVEMENT) },
                    onCheckUpdate = vm::checkForUpdate
                )
            }
            composable(
                route = ROUTE_ACHIEVEMENT,
                enterTransition = { fadeIn(tween(180)) },
                exitTransition = { fadeOut(tween(120)) }
            ) {
                AchievementScreen(
                    items = items,
                    onOpenRecord = { id -> nav.navigate("entry/$id") },
                    onAddRecord = {
                        vm.clearPending()
                        nav.navigate("entry/0")
                    }
                )
            }
            composable(
                route = ROUTE_ENTRY,
                arguments = listOf(navArgument("recordId") { type = NavType.LongType }),
                enterTransition = { fadeIn(tween(180)) },
                exitTransition = { fadeOut(tween(120)) }
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getLong("recordId") ?: 0L
                EntryScreen(vm = vm, recordId = id) { nav.popBackStack() }
            }
            composable(
                route = ROUTE_LIST,
                enterTransition = { fadeIn(tween(180)) },
                exitTransition = { fadeOut(tween(120)) }
            ) {
                ListScreen(items = items, vm = vm) { id -> nav.navigate("entry/$id") }
            }
            composable(
                route = ROUTE_EXPORT,
                enterTransition = { fadeIn(tween(180)) },
                exitTransition = { fadeOut(tween(120)) }
            ) {
                ExportScreen(vm = vm, items = items)
            }
        }
    }

    UpdateDialog(
        state = updateState,
        onDownload = vm::downloadUpdate,
        onInstall = { file ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !context.packageManager.canRequestPackageInstalls()
            ) {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            } else {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(
                        UpdateChecker.contentUri(context, file),
                        "application/vnd.android.package-archive"
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(intent)
            }
        },
        onDismiss = vm::resetUpdate,
        onRetry = vm::checkForUpdate
    )
}
