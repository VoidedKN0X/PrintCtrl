package com.vknxworks.printctrl.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vknxworks.printctrl.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "printctrl")

enum class ThemeChoice(val label: Int) {
    SYSTEM(R.string.theme_system),
    DARK(R.string.theme_dark),
    LIGHT(R.string.theme_light),
    BLACK(R.string.theme_black),
}

data class AppSettings(
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val dynamicColor: Boolean = false,
    /** Frames per second asked of Bambuddy's camera stream. */
    val cameraFps: Int = 5,
    /** Keep the screen on while the full-screen camera is open. */
    val keepScreenOnCamera: Boolean = true,
)

class SettingsRepository(private val context: Context, scope: CoroutineScope) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val fps = intPreferencesKey("camera_fps")
        val keepOn = booleanPreferencesKey("camera_keep_screen_on")
    }

    val settings: StateFlow<AppSettings> = context.appDataStore.data
        .map { it.toSettings() }
        .stateIn(scope, SharingStarted.Eagerly, AppSettings())

    private fun Preferences.toSettings() = AppSettings(
        theme = this[K.theme]?.let { n -> ThemeChoice.entries.firstOrNull { it.name == n } } ?: ThemeChoice.SYSTEM,
        dynamicColor = this[K.dynamic] ?: false,
        cameraFps = this[K.fps] ?: 5,
        keepScreenOnCamera = this[K.keepOn] ?: true,
    )

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.appDataStore.edit { p ->
            val s = transform(p.toSettings())
            p[K.theme] = s.theme.name
            p[K.dynamic] = s.dynamicColor
            p[K.fps] = s.cameraFps
            p[K.keepOn] = s.keepScreenOnCamera
        }
    }
}
