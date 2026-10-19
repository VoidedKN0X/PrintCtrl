package com.vknxworks.printctrl.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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

/** How PrintCtrl learns about prints while it's closed. */
enum class NotifyMode(val label: Int) {
    OFF(R.string.notify_mode_off),

    /** The phone asks Bambuddy every 15 minutes. Works with nothing set up on the server. */
    POLL(R.string.notify_mode_poll),

    /** Bambuddy sends events through your ntfy server (UnifiedPush). Instant. */
    PUSH(R.string.notify_mode_push),
}

/** Things worth a notification. Each can be turned off on its own. */
enum class PrintEvent(val label: Int) {
    FINISHED(R.string.event_finished),
    FAILED(R.string.event_failed),
    PAUSED(R.string.event_paused),
    HMS(R.string.event_hms),
    RUNOUT(R.string.event_runout),
    AMS(R.string.event_ams),
    AI(R.string.event_ai),
}

data class AppSettings(
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val dynamicColor: Boolean = false,
    /** Frames per second asked of Bambuddy's camera stream. */
    val cameraFps: Int = 5,
    /** Keep the screen on while the full-screen camera is open. */
    val keepScreenOnCamera: Boolean = true,
    val notifyMode: NotifyMode = NotifyMode.OFF,
    /** Ongoing notification with progress while a print runs. */
    val progressNotification: Boolean = true,
    val events: Set<PrintEvent> = PrintEvent.entries.toSet(),
    /** Grams; spools with less filament left get a warning in the Spools tab. */
    val lowStockGrams: Int = 150,
    /** Notify when a spool drops below [lowStockGrams]. */
    val lowStockAlerts: Boolean = true,
)

class SettingsRepository(private val context: Context, scope: CoroutineScope) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val fps = intPreferencesKey("camera_fps")
        val keepOn = booleanPreferencesKey("camera_keep_screen_on")
        val notifyMode = stringPreferencesKey("notify_mode")
        val progress = booleanPreferencesKey("progress_notification")
        val events = stringSetPreferencesKey("notify_events")
        val lowStock = intPreferencesKey("low_stock_grams")
        val lowStockAlerts = booleanPreferencesKey("low_stock_alerts")
    }

    val settings: StateFlow<AppSettings> = context.appDataStore.data
        .map { it.toSettings() }
        .stateIn(scope, SharingStarted.Eagerly, AppSettings())

    private fun Preferences.toSettings() = AppSettings(
        theme = this[K.theme]?.let { n -> ThemeChoice.entries.firstOrNull { it.name == n } } ?: ThemeChoice.SYSTEM,
        dynamicColor = this[K.dynamic] ?: false,
        cameraFps = this[K.fps] ?: 5,
        keepScreenOnCamera = this[K.keepOn] ?: true,
        notifyMode = this[K.notifyMode]?.let { n -> NotifyMode.entries.firstOrNull { it.name == n } } ?: NotifyMode.OFF,
        progressNotification = this[K.progress] ?: true,
        events = this[K.events]?.mapNotNull { n -> PrintEvent.entries.firstOrNull { it.name == n } }?.toSet() ?: PrintEvent.entries.toSet(),
        lowStockGrams = this[K.lowStock] ?: 150,
        lowStockAlerts = this[K.lowStockAlerts] ?: true,
    )

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.appDataStore.edit { p ->
            val s = transform(p.toSettings())
            p[K.theme] = s.theme.name
            p[K.dynamic] = s.dynamicColor
            p[K.fps] = s.cameraFps
            p[K.keepOn] = s.keepScreenOnCamera
            p[K.notifyMode] = s.notifyMode.name
            p[K.progress] = s.progressNotification
            p[K.events] = s.events.map { it.name }.toSet()
            p[K.lowStock] = s.lowStockGrams
            p[K.lowStockAlerts] = s.lowStockAlerts
        }
    }
}
