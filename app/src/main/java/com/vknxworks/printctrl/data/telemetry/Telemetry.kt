package com.vknxworks.printctrl.data.telemetry

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.vknxworks.printctrl.BuildConfig
import com.vknxworks.printctrl.data.servers.ServerStore
import com.vknxworks.printctrl.data.settings.NotifyMode
import com.vknxworks.printctrl.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Opt-in, anonymous usage stats sent to the project's own self-hosted Aptabase server.
 *
 * Nothing is sent unless the user turned "Share anonymous usage stats" on, and builds without an
 * Aptabase key (anyone building from source) can't send anything at all.
 *
 * What an event contains: its name, a few fixed-value properties chosen in code (a screen's name,
 * "pause" or "measure", whether Bambuddy, Spoolman or both are set up), the app version, the Android
 * version, the phone model, the system language and a random session ID that changes every launch or
 * after an hour of inactivity.
 * Never sent: server addresses, API keys, logins, printer names or serial numbers, file or job names,
 * spool details (filament, colour, weight, location), or any persistent device or install ID. (Like any
 * web request, the stats server sees the phone's IP address; Aptabase uses it for a country lookup and
 * doesn't store it.)
 */
class Telemetry(
    private val context: Context,
    private val settings: SettingsRepository,
    private val servers: ServerStore,
    private val scope: CoroutineScope,
) {
    /** Whether this build can send stats at all (it has a key and an HTTPS server address). */
    val available: Boolean = BuildConfig.APTABASE_KEY.startsWith("A-SH-") && BuildConfig.APTABASE_HOST.startsWith("https://")

    // A separate client from the Bambuddy and Spoolman ones: no API keys, logins, pins or redirects.
    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    // Declared before init: the coroutines started there can call track() right away.
    private val systemProps: JSONObject by lazy {
        JSONObject()
            .put("isDebug", BuildConfig.DEBUG)
            .put("osName", "Android")
            .put("osVersion", Build.VERSION.RELEASE ?: "")
            .put("locale", Locale.getDefault().language)
            .put("appVersion", BuildConfig.VERSION_NAME)
            .put("appBuildNumber", BuildConfig.VERSION_CODE.toString())
            .put("sdkVersion", "printctrl@${BuildConfig.VERSION_NAME}")
            .put("deviceModel", Build.MODEL ?: "")
    }

    private val queue = Channel<JSONObject>(capacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    @Volatile private var sessionId = newSessionId()
    @Volatile private var lastEventAt = 0L

    /** Mirrors the stored setting; false until it has been read, so nothing goes out before that. */
    @Volatile private var optedIn = false
    @Volatile private var loaded = false
    @Volatile private var openPending = false

    init {
        if (available) {
            scope.launch {
                settings.stored.collect { s ->
                    val firstRead = !loaded
                    val wasOptedIn = optedIn
                    optedIn = s.usageStats
                    loaded = true
                    when {
                        // The app was opened before the setting had been read.
                        firstRead && openPending && s.usageStats -> { openPending = false; trackOpened(s.notifyMode) }
                        // Switched on in Settings or the first-run question: start a fresh session.
                        !firstRead && !wasOptedIn && s.usageStats -> { resetSession(); trackOpened(s.notifyMode) }
                    }
                }
            }
            // One event at a time, in order. Failures are dropped: stats are never stored or retried.
            scope.launch(Dispatchers.IO) {
                for (event in queue) send(event)
            }
        }
    }

    /** Called when the app's screen is opened (not for notifications, the widget or background checks). */
    fun appOpened() {
        if (!available) return
        if (loaded) {
            trackOpened(settings.settings.value.notifyMode)
        } else {
            openPending = true
            // The setting may have been read in the meantime, after the collector checked openPending.
            if (loaded && openPending) {
                openPending = false
                trackOpened(settings.settings.value.notifyMode)
            }
        }
    }

    /** Which servers are set up ("bambuddy", "spoolman", "both"), never their addresses. */
    private fun trackOpened(notifyMode: NotifyMode) {
        if (!enabled) return
        scope.launch {
            val setup = servers.setup.filterNotNull().first()
            val backend = when {
                setup.bambuddy != null && setup.spoolman != null -> "both"
                setup.bambuddy != null -> "bambuddy"
                setup.spoolman != null -> "spoolman"
                else -> "none"
            }
            track(
                "app_opened",
                mapOf("form_factor" to formFactor(), "backend" to backend, "notifications" to notifyMode.name.lowercase()),
            )
        }
    }

    private val enabled: Boolean
        get() = available && optedIn

    /** Records an event if the user opted in. [props] values must be fixed strings or numbers from code. */
    fun track(name: String, props: Map<String, Any> = emptyMap()) {
        if (!enabled) return
        val now = System.currentTimeMillis()
        if (now - lastEventAt > SESSION_TIMEOUT_MS) sessionId = newSessionId()
        lastEventAt = now
        val event = JSONObject()
            .put("timestamp", timestamp(now))
            .put("sessionId", sessionId)
            .put("eventName", name)
            .put("systemProps", systemProps)
            .put("props", JSONObject(props))
        queue.trySend(event)
    }

    /** Pause, resume or stop, and whether it came from the app or a notification. Never which printer. */
    fun printControl(action: String, source: String = "app") =
        track("print_control", mapOf("action" to action, "source" to source))

    /** The kind of spool change ("measure", "adjust", "assign_slot", ...). Never which spool or how much. */
    fun spoolAction(action: String) = track("spool_action", mapOf("action" to action))

    /** A fresh session after opting back in, so stats from before and after aren't linked. */
    private fun resetSession() {
        sessionId = newSessionId()
        lastEventAt = 0L
    }

    private fun send(event: JSONObject) {
        // The user may have opted out while this was queued.
        if (!enabled) return
        val request = Request.Builder()
            .url("${BuildConfig.APTABASE_HOST.trimEnd('/')}/api/v0/event")
            .header("App-Key", BuildConfig.APTABASE_KEY)
            .header("User-Agent", "PrintCtrl/${BuildConfig.VERSION_NAME} (Android)")
            .post(event.toString().toRequestBody(JSON))
            .build()
        runCatching { http.newCall(request).execute().close() }
    }

    /** "phone" or "tablet", from the screen's smallest width (the same split as the app's layouts). */
    fun formFactor(): String {
        val sw = context.resources.configuration.smallestScreenWidthDp
        return if (sw != Configuration.SMALLEST_SCREEN_WIDTH_DP_UNDEFINED && sw >= 600) "tablet" else "phone"
    }

    private fun newSessionId(): String = UUID.randomUUID().toString()

    private fun timestamp(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(ms))

    companion object {
        private val JSON = "application/json".toMediaType()
        private val SESSION_TIMEOUT_MS = TimeUnit.HOURS.toMillis(1)
    }
}
