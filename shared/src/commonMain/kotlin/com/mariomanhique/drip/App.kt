package com.mariomanhique.drip

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import com.mariomanhique.drip.ui.LocalSharer
import com.mariomanhique.drip.ui.Sharer
import com.mariomanhique.drip.ui.DripHeader
import com.mariomanhique.drip.ui.DripTheme
import com.mariomanhique.drip.ui.DripViewModel
import com.mariomanhique.drip.ui.Screen
import com.mariomanhique.drip.ui.Tab
import com.mariomanhique.drip.ui.screens.AuthScreen
import com.mariomanhique.drip.ui.screens.BattlesScreen
import com.mariomanhique.drip.ui.screens.ChallengeDetailScreen
import com.mariomanhique.drip.ui.screens.ChallengesScreen
import com.mariomanhique.drip.ui.screens.CreateOutfitScreen
import com.mariomanhique.drip.ui.screens.EditProfileScreen
import com.mariomanhique.drip.ui.screens.FeedScreen
import com.mariomanhique.drip.ui.screens.ModerationScreen
import com.mariomanhique.drip.ui.screens.OutfitDetailScreen
import com.mariomanhique.drip.ui.screens.ProfileScreen
import com.mariomanhique.drip.ui.screens.RanksScreen
import com.mariomanhique.drip.ui.screens.RulesScreen
import com.mariomanhique.drip.ui.screens.SettingsScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(sharer: Sharer = Sharer { _, _ -> false }) {
    CompositionLocalProvider(LocalSharer provides sharer) { AppContent() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppContent() {
    DripTheme {
        val vm = viewModel { DripViewModel() }
        val state by vm.state.collectAsStateWithLifecycle()
        if (state.currentUserId == null) {
            AuthHost(vm)
            return@DripTheme
        }
        val snackbar = remember { SnackbarHostState() }
        val message = vm.message
        LaunchedEffect(message) {
            if (message != null) {
                snackbar.showSnackbar(message)
                vm.consumeMessage()
            }
        }
        val screen = vm.stack.lastOrNull()
        Scaffold(
           // topBar = { DripHeader(state, vm, showBack = screen != null) },
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEach {
                        NavigationBarItem(
                            selected = vm.tab == it && screen == null,
                            onClick = { vm.selectTab(it) },
                            icon = { Text(it.icon) },
                            label = { Text(it.label) },
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (screen) {
                    null -> when (vm.tab) {
                        Tab.FEED -> FeedScreen(state, vm)
                        Tab.BATTLES -> BattlesScreen(state, vm)
                        Tab.CHALLENGES -> ChallengesScreen(state, vm)
                        Tab.RANKS -> RanksScreen(state, vm)
                        Tab.PROFILE -> ProfileScreen(state, state.currentUserId!!, vm)
                    }
                    is Screen.OutfitDetail -> OutfitDetailScreen(state, screen.outfitId, vm)
                    is Screen.ChallengeDetail -> ChallengeDetailScreen(state, screen.challengeId, vm)
                    is Screen.UserProfile -> ProfileScreen(state, screen.userId, vm)
                    is Screen.Create -> CreateOutfitScreen(state, screen.challengeId, vm)
                    Screen.EditProfile -> EditProfileScreen(state, vm)
                    Screen.Settings -> SettingsScreen(vm)
                    Screen.Moderation -> ModerationScreen(state, vm)
                    Screen.Rules -> RulesScreen()
                }
            }
        }
    }
}

@Composable
private fun AuthHost(vm: DripViewModel) = AuthScreen(vm)
