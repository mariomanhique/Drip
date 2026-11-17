package com.mariomanhique.drip

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
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
import com.mariomanhique.drip.ui.LocalPhotoPicker
import com.mariomanhique.drip.ui.LocalSharer
import com.mariomanhique.drip.ui.PhotoPicker
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
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(sharer: Sharer = Sharer { _, _ -> false }, photoPicker: PhotoPicker = PhotoPicker { it(null) }) {
    CompositionLocalProvider(LocalSharer provides sharer, LocalPhotoPicker provides photoPicker) {
        Box(Modifier.dismissKeyboardOnOutsideInteraction()) { AppContent() }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun AppContent() {
    DripTheme {
        val vm = koinViewModel<DripViewModel>()
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
        BackHandler(enabled = screen != null) { vm.back() }
        Scaffold(
            topBar = { if (screen != null) BackBar(onBack = { vm.back() }) },
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

/** Taps on empty space and scrolling both close the keyboard. */
@Composable
private fun Modifier.dismissKeyboardOnOutsideInteraction(): Modifier {
    val focus = LocalFocusManager.current
    val connection = remember(focus) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                focus.clearFocus()
                return Offset.Zero
            }
        }
    }
    return this
        .nestedScroll(connection)
        .pointerInput(focus) { detectTapGestures { focus.clearFocus() } }
}

@Composable
private fun BackBar(onBack: () -> Unit) {
    androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).height(48.dp)
                .clickable(onClick = onBack).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { Text("‹  Back", fontSize = 16.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground) }
    }
}