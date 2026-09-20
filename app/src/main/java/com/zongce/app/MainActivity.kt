package com.zongce.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zongce.app.ui.AppViewModel
import com.zongce.app.ui.CaptureScreen
import com.zongce.app.ui.EntryScreen
import com.zongce.app.ui.ExportScreen
import com.zongce.app.ui.ListScreen

private const val ROUTE_CAPTURE = "capture"
private const val ROUTE_LIST = "list"
private const val ROUTE_EXPORT = "export"
private const val ROUTE_ENTRY = "entry/{recordId}"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface { App() }
            }
        }
    }
}

@Composable
private fun App(vm: AppViewModel = viewModel()) {
    val nav = rememberNavController()
    val items by vm.items.collectAsState()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val tabs = listOf(
        Triple(ROUTE_CAPTURE, "拍摄", Icons.Default.PhotoCamera),
        Triple(ROUTE_LIST, "记录", Icons.Default.List),
        Triple(ROUTE_EXPORT, "导出", Icons.Default.Archive)
    )

    Scaffold(
        bottomBar = {
            if (currentRoute in tabs.map { it.first }) {
                NavigationBar {
                    tabs.forEach { (route, label, icon) ->
                        val selected = backStack?.destination?.hierarchy?.any { it.route == route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                nav.navigate(route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = ROUTE_CAPTURE,
            modifier = Modifier.padding(padding)
        ) {
            composable(ROUTE_CAPTURE) {
                CaptureScreen(
                    vm = vm,
                    recordCount = items.size,
                    onGoEntry = { nav.navigate("entry/0") }
                )
            }
            composable(
                route = ROUTE_ENTRY,
                arguments = listOf(navArgument("recordId") { type = NavType.LongType })
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getLong("recordId") ?: 0L
                EntryScreen(vm = vm, recordId = id) { nav.popBackStack() }
            }
            composable(ROUTE_LIST) {
                ListScreen(items = items, vm = vm) { id -> nav.navigate("entry/$id") }
            }
            composable(ROUTE_EXPORT) {
                ExportScreen(vm = vm, items = items)
            }
        }
    }
}
