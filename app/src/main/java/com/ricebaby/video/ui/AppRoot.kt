package com.ricebaby.video.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ricebaby.video.ui.common.PlaceholderScreen
import com.ricebaby.video.ui.detail.DetailScreen
import com.ricebaby.video.ui.home.HomeScreen
import com.ricebaby.video.ui.live.IptvPlayerScreen
import com.ricebaby.video.ui.live.LiveScreen
import com.ricebaby.video.ui.search.SearchScreen
import com.ricebaby.video.ui.settings.SettingsScreen
import com.ricebaby.video.ui.watchlist.WatchlistScreen

data class TopTab(val route: String, val label: String, val icon: ImageVector)

val topTabs = listOf(
    TopTab("home", "首页", Icons.Outlined.Home),
    TopTab("search", "搜索", Icons.Outlined.Search),
    TopTab("watchlist", "待看", Icons.Outlined.Bookmarks),
    TopTab("live", "直播", Icons.Outlined.LiveTv),
    TopTab("settings", "设置", Icons.Outlined.Settings),
)

@Composable
fun AppRoot(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    Scaffold(
        bottomBar = {
            // 播放器页需要真正全屏，不显示底部导航
            if (!currentRoute.orEmpty().startsWith("player")) {
                NavigationBar {
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
            modifier = Modifier.padding(innerPadding),
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
            composable(
                route = "search?q={q}",
                arguments = listOf(
                    androidx.navigation.navArgument("q") {
                        defaultValue = ""
                        type = androidx.navigation.NavType.StringType
                    },
                ),
            ) { SearchScreen(navController = navController) }
            composable("watchlist") { WatchlistScreen(navController = navController) }
            composable("live") { LiveScreen(navController = navController) }
            composable(
                route = "liveplayer?url={url}&name={name}",
                arguments = listOf(
                    androidx.navigation.navArgument("url") { type = androidx.navigation.NavType.StringType },
                    androidx.navigation.navArgument("name") {
                        defaultValue = ""
                        type = androidx.navigation.NavType.StringType
                    },
                ),
            ) { IptvPlayerScreen(navController = navController) }
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
                route = "player?vid={vid}&ep={ep}",
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
            ) { com.ricebaby.video.player.PlayerScreen(navController = navController) }
        }
    }
}
