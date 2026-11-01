package com.fabriquefutur.qupa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fabriquefutur.qupa.ui.AboutScreen
import com.fabriquefutur.qupa.ui.CategoriesScreen
import com.fabriquefutur.qupa.ui.HomeScreen
import com.fabriquefutur.qupa.ui.PlayScreen
import com.fabriquefutur.qupa.ui.QupaTheme
import com.fabriquefutur.qupa.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    private val viewModel: QupaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { QupaTheme { QupaApp(viewModel) } }
    }

    /** The server must not keep listening once the app leaves the screen. */
    override fun onStop() {
        viewModel.onAppStopped()
        super.onStop()
    }
}

@Composable
private fun QupaApp(vm: QupaViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()

    // Back leaves the current screen; on the home screen the system takes it and exits to the launcher.
    BackHandler(enabled = state.screen != Screen.HOME || state.confirmExit) { vm.back() }

    // The TV must not dim or start its screensaver during a game.
    val view = LocalView.current
    val playing = state.screen == Screen.PLAY
    DisposableEffect(playing) {
        view.keepScreenOn = playing
        onDispose { view.keepScreenOn = false }
    }

    when (state.screen) {
        Screen.HOME -> HomeScreen(vm)
        Screen.SETTINGS -> SettingsScreen(vm, state)
        Screen.ABOUT -> AboutScreen(vm)
        Screen.CATEGORIES -> CategoriesScreen(vm, state)
        Screen.PLAY -> PlayScreen(vm, state)
    }
}
