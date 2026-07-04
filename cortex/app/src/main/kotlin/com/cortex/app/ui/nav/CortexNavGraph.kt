package com.cortex.app.ui.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.cortex.app.CortexApp
import com.cortex.app.ui.chat.ChatScreen
import com.cortex.app.ui.chat.ChatViewModel
import com.cortex.app.ui.chat.ChatViewModelFactory
import com.cortex.app.ui.chatlist.ChatListScreen
import com.cortex.app.ui.chatlist.ChatListViewModel
import com.cortex.app.ui.chatlist.ChatListViewModelFactory
import com.cortex.app.ui.settings.SettingsScreen
import com.cortex.app.ui.settings.SettingsViewModel
import com.cortex.app.ui.settings.SettingsViewModelFactory

object Routes {
    const val CHAT_LIST = "chatList"
    const val CHAT = "chat/{chatId}"
    const val SETTINGS = "settings"

    fun chat(chatId: Long) = "chat/$chatId"
}

@Composable
fun CortexNavGraph() {
    val navController = rememberNavController()
    val app = CortexApp.instance

    NavHost(
        navController = navController,
        startDestination = Routes.CHAT_LIST,
        enterTransition = { slideInHorizontally(tween(220)) { it / 6 } + fadeIn(tween(180)) },
        exitTransition = { fadeOut(tween(120)) },
        popEnterTransition = { slideInHorizontally(tween(220)) { -it / 6 } + fadeIn(tween(180)) },
        popExitTransition = { slideOutHorizontally(tween(220)) { it / 2 } + fadeOut(tween(180)) }
    ) {
        composable(Routes.CHAT_LIST) {
            val vm: ChatListViewModel = viewModel(factory = ChatListViewModelFactory(app.chatRepo, app.gatewayRepo))
            ChatListScreen(
                vm = vm,
                onOpenChat = { id -> navController.navigate(Routes.chat(id)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onNewChat = { id -> navController.navigate(Routes.chat(id)) }
            )
        }
        composable(
            route = Routes.CHAT,
            arguments = listOf(navArgument("chatId") { type = NavType.LongType })
        ) { entry ->
            val chatId = entry.arguments?.getLong("chatId") ?: -1L
            val vm: ChatViewModel = viewModel(factory = ChatViewModelFactory(chatId, app.chatRepo, app.gatewayRepo, app.settingsRepo))
            ChatScreen(
                vm = vm,
                onBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.SETTINGS) {
            val vm: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(app.gatewayRepo, app.settingsRepo))
            SettingsScreen(
                vm = vm,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
