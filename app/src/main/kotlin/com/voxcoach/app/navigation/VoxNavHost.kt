package com.voxcoach.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.voxcoach.app.ui.SettingsScreen
import com.voxcoach.feature.conversation.ui.ConversationRoute

object Routes {
    const val Conversation = "conversation"
    const val Settings = "settings"
}

@Composable
fun VoxNavHost() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Routes.Conversation,
    ) {
        composable(Routes.Conversation) {
            ConversationRoute(
                onOpenSettings = { navController.navigate(Routes.Settings) },
            )
        }
        composable(Routes.Settings) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
