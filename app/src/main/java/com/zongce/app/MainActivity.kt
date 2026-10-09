// 应用导航与组件入口：组件传来的学年作为一次性请求交给成果页，兼容冷启动和已打开页面。
// 三个 ViewModel（Record / Export / Update）在这里作为组合根接线，
// 跨域的协作（如导出分享 → 删除询问闸门）只发生在这一层。
package com.zongce.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zongce.app.ui.AchievementScreen
import com.zongce.app.ui.CaptureScreen
import com.zongce.app.ui.EntryScreen
import com.zongce.app.ui.ExportScreen
import com.zongce.app.ui.ExportViewModel
import com.zongce.app.ui.JicunGlassNavigationBar
import com.zongce.app.ui.JicunTheme
import com.zongce.app.ui.ListScreen
import com.zongce.app.ui.RecordViewModel
import com.zongce.app.ui.UpdateDialog
import com.zongce.app.ui.UpdateViewModel
import com.zongce.app.ui.YearDeleteDialog
import com.zongce.app.ui.defaultAppTabs
import com.zongce.app.update.UpdateChecker

private const val ROUTE_CAPTURE = "capture"
private const val ROUTE_ACHIEVEMENT = "achievement"
private const val ROUTE_LIST = "list"
private const val ROUTE_EXPORT = "export"
private const val ROUTE_ENTRY = "entry/{recordId}"

class MainActivity : ComponentActivity() {
    private var widgetAction by mutableStateOf<String?>(null)
    private var widgetRecordId by mutableStateOf<Long?>(null)
    private var widgetYear by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetAction = if (savedInstanceState != null) savedInstanceState.getString("widgetAction")
            else intent?.action?.takeIf(WidgetActions::isWidgetAction)
        widgetRecordId = savedInstanceState?.getLong("widgetRecordId", 0L)?.takeIf { it != 0L }
            ?: intent?.getLongExtra(WidgetActions.RECORD_ID_EXTRA, 0L)?.takeIf { it != 0L }
        widgetYear = if (savedInstanceState != null) savedInstanceState.getString("widgetYear")
            else intent?.getStringExtra(WidgetActions.YEAR_EXTRA)
        enableEdgeToEdge()
        setContent {
            JicunTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    App(
                        widgetAction = widgetAction,
                        widgetRecordId = widgetRecordId,
                        widgetYear = widgetYear,
                        onWidgetActionConsumed = { widgetAction = null; widgetYear = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        widgetAction = intent.action?.takeIf(WidgetActions::isWidgetAction)
        widgetRecordId = intent.getLongExtra(WidgetActions.RECORD_ID_EXTRA, 0L).takeIf { it != 0L }
        widgetYear = intent.getStringExtra(WidgetActions.YEAR_EXTRA)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("widgetAction", widgetAction)
        outState.putLong("widgetRecordId", widgetRecordId ?: 0L)
        outState.putString("widgetYear", widgetYear)
        super.onSaveInstanceState(outState)
    }
}

@Composable
private fun App(
    recordVm: RecordViewModel = viewModel(),
    exportVm: ExportViewModel = viewModel(),
    updateVm: UpdateViewModel = viewModel(),
    widgetAction: String? = null,
    widgetRecordId: Long? = null,
    widgetYear: String? = null,
    onWidgetActionConsumed: () -> Unit = {}
) {
    val nav = rememberNavController()
    val items by recordVm.items.collectAsState()
    val updateState by updateVm.updateState.collectAsState()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val context = LocalContext.current
    var requestedAchievementYear by rememberSaveable { mutableStateOf<String?>(null) }

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
    androidx.compose.runtime.LaunchedEffect(widgetAction, widgetRecordId, widgetYear) {
        when (widgetAction) {
            WidgetActions.CAPTURE, WidgetActions.PICK_PHOTOS ->
                if (currentRoute != ROUTE_CAPTURE) selectTab(ROUTE_CAPTURE)
            WidgetActions.OPEN_ACHIEVEMENT -> {
                requestedAchievementYear = widgetYear
                selectTab(ROUTE_ACHIEVEMENT)
            }
            WidgetActions.OPEN_RECORD -> {
                widgetRecordId?.takeIf { it != 0L }?.let { nav.navigate("entry/$it") }
            }
        }
        if (widgetAction == WidgetActions.OPEN_ACHIEVEMENT || widgetAction == WidgetActions.OPEN_RECORD) {
            onWidgetActionConsumed()
        }
    }

    // 启动即静默检查一次更新（每天最多一次；无新版或失败都不打扰用户）。
    // 用 Unit 作 key，保证整个 App 组合期间只触发一次，不会随重组反复发请求。
    androidx.compose.runtime.LaunchedEffect(Unit) { updateVm.autoCheckForUpdate() }

    // 删除结果的 Toast。用 Toast 而不是 SnackBar：本页的 Scaffold 没有 snackbarHost，
    // 为一条提示去加一个要和 NavHost padding、玻璃导航栏叠放的 host 不划算，
    // 而删除反馈本就是"不阻塞操作"的告知。
    LaunchedEffect(Unit) {
        recordVm.deleteMessages.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    // 「从分享面板回到 App」的兜底路径：部分 ROM 的 chooser 不回填 ActivityResult，
    // 只用 launcher 就永远等不到回调。与 launcher 回调走同一个幂等消费函数，
    // 由 VM 内部的 promptConsumed 保证只弹一次 —— 两条路径绝不能各弹一次。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) recordVm.onReturnedFromShare()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val yearDeletePrompt by recordVm.yearDeletePrompt.collectAsState()
    val deleting by recordVm.deleting.collectAsState()

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
                    vm = recordVm,
                    recordCount = items.size,
                    widgetAction = widgetAction,
                    onWidgetActionConsumed = onWidgetActionConsumed,
                    onGoEntry = { nav.navigate("entry/0") },
                    onGoAchievement = { selectTab(ROUTE_ACHIEVEMENT) },
                    onCheckUpdate = updateVm::checkForUpdate
                )
            }
            composable(
                route = ROUTE_ACHIEVEMENT,
                enterTransition = { fadeIn(tween(180)) },
                exitTransition = { fadeOut(tween(120)) }
            ) {
                AchievementScreen(
                    items = items,
                    vm = recordVm,
                    requestedYear = requestedAchievementYear,
                    onYearRequestConsumed = { consumed ->
                        if (requestedAchievementYear == consumed) requestedAchievementYear = null
                    },
                    onOpenRecord = { id -> nav.navigate("entry/$id") },
                    onAddRecord = {
                        recordVm.clearPending()
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
                EntryScreen(vm = recordVm, recordId = id) { nav.popBackStack() }
            }
            composable(
                route = ROUTE_LIST,
                enterTransition = { fadeIn(tween(180)) },
                exitTransition = { fadeOut(tween(120)) }
            ) {
                ListScreen(items = items, vm = recordVm) { id -> nav.navigate("entry/$id") }
            }
            composable(
                route = ROUTE_EXPORT,
                enterTransition = { fadeIn(tween(180)) },
                exitTransition = { fadeOut(tween(120)) }
            ) {
                ExportScreen(
                    vm = exportVm,
                    items = items,
                    onShareYear = recordVm::markShared,
                    onShareReturned = recordVm::onReturnedFromShare,
                    onResetExport = {
                        exportVm.resetExport()
                        recordVm.resetYearDeletePrompt()
                    }
                )
            }
        }
    }

    UpdateDialog(
        state = updateState,
        onDownload = updateVm::downloadUpdate,
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
        onDismiss = updateVm::resetUpdate,
        onRetry = updateVm::checkForUpdate
    )

    // 学年删除询问与更新弹窗同级：从分享面板回来时用户可能停在任何一个 tab，
    // 挂在这一层才能确保无论落在哪都能看到。
    YearDeleteDialog(
        prompt = yearDeletePrompt,
        deleting = deleting,
        onConfirm = recordVm::confirmYearDelete,
        onDismiss = recordVm::dismissYearDelete
    )
}
