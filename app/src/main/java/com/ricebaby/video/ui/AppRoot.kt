package com.ricebaby.video.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ricebaby.video.ui.detail.DetailScreen
import com.ricebaby.video.ui.home.HomeScreen
import com.ricebaby.video.ui.settings.SettingsScreen
import com.ricebaby.video.ui.unlock.BrandSplash
import com.ricebaby.video.ui.unlock.UnlockScreen
import com.ricebaby.video.ui.unlock.UnlockViewModel
import com.ricebaby.video.ui.watchlist.WatchlistScreen

data class TopTab(val route: String, val label: String, val icon: ImageVector)

/** 沉浸式页面：播放中不显示底部导航 */
private val chromelessRoutes = listOf("video")

val topTabs = listOf(
    TopTab("home", "首页", Icons.Outlined.Home),
    TopTab("watchlist", "待看", Icons.Outlined.Bookmarks),
    TopTab("settings", "设置", Icons.Outlined.Settings),
)

@Composable
fun AppRoot(navController: NavHostController = rememberNavController()) {
    val unlockViewModel: UnlockViewModel = hiltViewModel()
    val unlockState by unlockViewModel.uiState.collectAsStateWithLifecycle()
    when {
        !unlockState.resolved -> BrandSplash()
        !unlockState.unlocked -> UnlockScreen(unlockViewModel)
        else -> AppShell(navController)
    }
}

@Composable
private fun AppShell(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    // 播放器全屏时要连 Scaffold 的内衬一起去掉，否则状态栏那一侧会露出浅色容器背景
    var playerFullscreen by rememberSaveable { mutableStateOf(false) }
    val immersive = playerFullscreen && currentRoute.orEmpty().startsWith("video")
    Scaffold(
        bottomBar = {
            // 播放器页需要真正全屏，不显示底部导航
            if (chromelessRoutes.none { currentRoute.orEmpty().startsWith(it) }) {
                NavigationBar(
                    tonalElevation = 0.dp,
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    topTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = if (immersive) Modifier else Modifier.padding(innerPadding),
            enterTransition = {
                androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200)) +
                    androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(280)) { it / 8 }
            },
            exitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(140)) },
            popEnterTransition = {
                androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200)) +
                    androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(280)) { -it / 8 }
            },
            popExitTransition = {
                androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(140)) +
                    androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(280)) { it / 8 }
            },
        ) {
            composable("home") { HomeScreen(navController = navController) }
            composable("watchlist") { WatchlistScreen(navController = navController) }
            composable("settings") { SettingsScreen() }
            composable(
                route = "detail?vid={vid}",
                arguments = listOf(
                    androidx.navigation.navArgument("vid") {
                        defaultValue = ""
                        type = androidx.navigation.NavType.StringType
                    },
                ),
            ) { DetailScreen(navController = navController) }
            composable(
                route = "video?vid={vid}&ep={ep}",
                arguments = listOf(
                    androidx.navigation.navArgument("vid") {
                        defaultValue = ""
                        type = androidx.navigation.NavType.StringType
                    },
                    androidx.navigation.navArgument("ep") {
                        defaultValue = "0"
                        type = androidx.navigation.NavType.StringType
                    },
                ),
            ) { com.ricebaby.video.player.VideoPlayerScreen(navController = navController, onFullscreenChange = { playerFullscreen = it }) }
        }
    }
}
