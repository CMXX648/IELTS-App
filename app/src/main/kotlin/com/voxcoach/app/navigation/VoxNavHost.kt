package com.voxcoach.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.voxcoach.app.ui.SettingsScreen
import com.voxcoach.app.ui.debug.LatencySmokeScreen
import com.voxcoach.app.ui.home.HomeScreen
import com.voxcoach.app.ui.profile.ProfileScreen
import com.voxcoach.app.ui.report.ReportScreen
import com.voxcoach.feature.conversation.ui.ConversationRoute

object Routes {
    const val Home = "home"
    const val Conversation = "conversation/{topicId}"
    const val Report = "report/{sessionId}"
    const val Profile = "profile"
    const val Settings = "settings"
    const val DebugSmoke = "debug/smoke"

    fun conversation(topicId: String) = "conversation/$topicId"
    fun report(sessionId: String) = "report/$sessionId"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

@Composable
fun VoxNavHost() {
    val navController = rememberNavController()
    val tabs = listOf(
        Tab(Routes.Home, "首页", Icons.Default.Home),
        Tab(Routes.Profile, "档案", Icons.Default.Person),
        Tab(Routes.Settings, "设置", Icons.Default.Settings),
    )
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = currentRoute in setOf(Routes.Home, Routes.Profile, Routes.Settings)

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
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
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.Home,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.Home) {
                HomeScreen(
                    onStartConversation = { topicId ->
                        navController.navigate(Routes.conversation(topicId))
                    },
                    onOpenDebug = { navController.navigate(Routes.DebugSmoke) },
                )
            }
            composable(
                route = Routes.Conversation,
                arguments = listOf(navArgument("topicId") { type = NavType.StringType }),
            ) {
                ConversationRoute(
                    onBack = { navController.popBackStack() },
                    onOpenReport = { sessionId ->
                        navController.navigate(Routes.report(sessionId)) {
                            popUpTo(Routes.Home)
                        }
                    },
                )
            }
            composable(
                route = Routes.Report,
                arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
            ) {
                ReportScreen(onBack = { navController.popBackStack(Routes.Home, inclusive = false) })
            }
            composable(Routes.Profile) {
                ProfileScreen()
            }
            composable(Routes.Settings) {
                SettingsScreen(onBack = { navController.navigate(Routes.Home) })
            }
            composable(Routes.DebugSmoke) {
                LatencySmokeScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
