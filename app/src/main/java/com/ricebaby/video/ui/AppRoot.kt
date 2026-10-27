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
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding),
        ) {
            composable("home") { PlaceholderScreen("首页", "即将上线：豆瓣标签流与为你推荐") }
            composable("search") { SearchScreen(navController = navController) }
            composable("watchlist") { WatchlistScreen(navController = navController) }
            composable("live") { PlaceholderScreen("直播", "即将上线：IPTV 频道") }
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
        }
    }
}
