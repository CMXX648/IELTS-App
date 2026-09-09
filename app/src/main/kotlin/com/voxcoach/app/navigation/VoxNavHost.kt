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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
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
import com.voxcoach.app.ui.onboarding.OnboardingScreen
import com.voxcoach.app.ui.profile.ProfileScreen
import com.voxcoach.app.ui.report.ReportScreen
import com.voxcoach.core.domain.settings.OnboardingRepository
import com.voxcoach.feature.conversation.ui.ConversationRoute
import com.voxcoach.feature.conversation.ui.part1.Part1Route
import com.voxcoach.feature.conversation.ui.part2.Part2Route
import com.voxcoach.feature.drill.ui.DrillListRoute
import com.voxcoach.feature.drill.ui.DrillRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

object Routes {
    const val Onboarding = "onboarding"
    const val Home = "home"
    const val Conversation = "conversation/{topicId}"
    const val Part1Mock = "mock/part1"
    const val Part2Mock = "mock/part2"
    const val Report = "report/{sessionId}"
    const val Profile = "profile"
    const val Settings = "settings"
    const val DebugSmoke = "debug/smoke"
    const val DrillList = "drill"
    const val Drill = "drill/{pointId}"

    fun conversation(topicId: String) = "conversation/$topicId"
    fun report(sessionId: String) = "report/$sessionId"
    fun drill(pointId: String) = "drill/$pointId"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

@HiltViewModel
class RootNavViewModel @Inject constructor(
    onboardingRepository: OnboardingRepository,
) : ViewModel() {
    val onboardingDone: StateFlow<Boolean?> = onboardingRepository.onboardingDone
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun VoxNavHost(
    rootViewModel: RootNavViewModel = hiltViewModel(),
) {
    val onboardingDone by rootViewModel.onboardingDone.collectAsStateWithLifecycle()
    if (onboardingDone == null) {
        Text("加载中…")
        return
    }

    val navController = rememberNavController()
    val start = if (onboardingDone == true) Routes.Home else Routes.Onboarding
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
            startDestination = start,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.Onboarding) {
                OnboardingScreen(
                    onOpenSettings = {
                        navController.navigate(Routes.Settings)
                    },
                    onFinished = {
                        navController.navigate(Routes.Home) {
                            popUpTo(Routes.Onboarding) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.Home) {
                HomeScreen(
                    onStartConversation = { topicId ->
                        navController.navigate(Routes.conversation(topicId))
                    },
                    onStartPart1 = { navController.navigate(Routes.Part1Mock) },
                    onStartPart2 = { navController.navigate(Routes.Part2Mock) },
                    onOpenGrammar = { navController.navigate(Routes.DrillList) },
                    onOpenDebug = { navController.navigate(Routes.DebugSmoke) },
                    onOpenSettings = { navController.navigate(Routes.Settings) },
                )
            }
            composable(Routes.Part1Mock) {
                Part1Route(
                    onBack = { navController.popBackStack() },
                    onOpenReport = { sessionId ->
                        navController.navigate(Routes.report(sessionId)) {
                            popUpTo(Routes.Home)
                        }
                    },
                )
            }
            composable(Routes.Part2Mock) {
                Part2Route(
                    onBack = { navController.popBackStack() },
                    onOpenReport = { sessionId ->
                        navController.navigate(Routes.report(sessionId)) {
                            popUpTo(Routes.Home)
                        }
                    },
                )
            }
            composable(Routes.DrillList) {
                DrillListRoute(
                    onBack = { navController.popBackStack() },
                    onOpenDrill = { pointId ->
                        navController.navigate(Routes.drill(pointId))
                    },
                )
            }
            composable(
                route = Routes.Drill,
                arguments = listOf(navArgument("pointId") { type = NavType.StringType }),
            ) {
                DrillRoute(onBack = { navController.popBackStack() })
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
                SettingsScreen(
                    onBack = {
                        if (!navController.popBackStack()) {
                            navController.navigate(Routes.Home)
                        }
                    },
                )
            }
            composable(Routes.DebugSmoke) {
                LatencySmokeScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
