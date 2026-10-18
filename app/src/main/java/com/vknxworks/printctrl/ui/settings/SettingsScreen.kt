package com.vknxworks.printctrl.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.printctrl.R
import com.vknxworks.printctrl.container
import com.vknxworks.printctrl.data.settings.AppSettings
import com.vknxworks.printctrl.data.settings.ThemeChoice
import com.vknxworks.printctrl.ui.components.Group
import com.vknxworks.printctrl.ui.components.GroupChoice
import com.vknxworks.printctrl.ui.components.GroupSwitch
import com.vknxworks.printctrl.ui.components.ScreenTopBar
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val s by c.settings.settings.collectAsStateWithLifecycle()
    fun update(t: (AppSettings) -> AppSettings) { scope.launch { c.settings.update(t) } }
    val fpsOptions = listOf(1, 2, 5, 10).map { it to pluralStringResource(R.plurals.camera_fps, it, it) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar(stringResource(R.string.settings)) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Group(stringResource(R.string.settings_camera), footer = stringResource(R.string.settings_camera_footer)) {
                GroupChoice(stringResource(R.string.settings_camera_fps), fpsOptions, s.cameraFps) { v -> update { it.copy(cameraFps = v) } }
                GroupSwitch(stringResource(R.string.settings_keep_screen_on), checked = s.keepScreenOnCamera) { v ->
                    update { it.copy(keepScreenOnCamera = v) }
                }
            }
            Group(stringResource(R.string.settings_appearance)) {
                GroupChoice(
                    stringResource(R.string.settings_theme),
                    ThemeChoice.entries.map { it to stringResource(it.label) },
                    s.theme,
                ) { v -> update { it.copy(theme = v) } }
                GroupSwitch(stringResource(R.string.settings_wallpaper), stringResource(R.string.settings_wallpaper_sub), s.dynamicColor) { v ->
                    update { it.copy(dynamicColor = v) }
                }
            }
        }
    }
}
