package com.vknxworks.printctrl

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.vknxworks.printctrl.data.auth.KeystoreCrypto
import com.vknxworks.printctrl.data.bambuddy.BambuddyClient
import com.vknxworks.printctrl.data.lock.LockVault
import com.vknxworks.printctrl.ui.lock.AppLock
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import com.vknxworks.printctrl.data.bambuddy.PrintersRepository
import com.vknxworks.printctrl.data.net.CleartextPolicy
import com.vknxworks.printctrl.data.net.RedirectGuard
import com.vknxworks.printctrl.data.net.origin
import com.vknxworks.printctrl.data.servers.ConnState
import com.vknxworks.printctrl.data.servers.Connections
import com.vknxworks.printctrl.data.servers.ServerKind
import com.vknxworks.printctrl.data.servers.ServerStore
import com.vknxworks.printctrl.data.settings.SettingsRepository
import com.vknxworks.printctrl.data.spoolman.SpoolmanClient
import com.vknxworks.printctrl.notify.EventDetector
import com.vknxworks.printctrl.notify.Notifier
import com.vknxworks.printctrl.notify.PollWorker
import com.vknxworks.printctrl.notify.PrintMonitorService
import com.vknxworks.printctrl.notify.UnifiedPush
import com.vknxworks.printctrl.widget.WidgetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Manual dependency container; one per process. */
class AppContainer(val context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Base client. Each server gets a copy with its credentials and pinned certificate added. */
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "PrintCtrl/${BuildConfig.VERSION_NAME} (Android)")
                    .build(),
            )
        }
        // Plain HTTP only to a server address the user chose, and never via a redirect from HTTPS.
        .addNetworkInterceptor(CleartextPolicy.interceptor)
        // The API key and proxy login never follow a redirect to another server.
        .addNetworkInterceptor(RedirectGuard.interceptor)
        .build()

    /**
     * The app lock's PIN, fingerprint key and the key that seals the API key and proxy login while locked.
     * Handed to KeystoreCrypto before anything reads a saved secret.
     */
    val vault = LockVault(context, "printctrl").also { KeystoreCrypto.vault = it }

    val settings = SettingsRepository(context, appScope)
    val servers = ServerStore(context, appScope)
    val connections = Connections(http, servers, appScope)

    val bambuddy: StateFlow<BambuddyClient?> = connections.bambuddy
        .map { (it as? ConnState.Ready)?.let { r -> BambuddyClient(r.conn) } }
        .stateIn(appScope, SharingStarted.Eagerly, null)

    val spoolman: StateFlow<SpoolmanClient?> = connections.spoolman
        .map { (it as? ConnState.Ready)?.let { r -> SpoolmanClient(r.conn) } }
        .stateIn(appScope, SharingStarted.Eagerly, null)

    val printers = PrintersRepository(connections.bambuddy, bambuddy, appScope)

    /** Live camera streams, shared between screens and kept warm for a little while after leaving. */
    val cameras = com.vknxworks.printctrl.data.bambuddy.CameraStreams(appScope, bambuddy, settings.settings.map { it.cameraFps })

    /**
     * Images (job covers) are loaded through the client of the server they come from, so they carry
     * its API key and pinned certificate. Anything else goes through the plain client.
     */
    val imageCalls: Call.Factory = object : Call.Factory {
        override fun newCall(request: Request): Call {
            val origin = request.url.origin()
            val client = listOfNotNull(bambuddy.value?.conn, spoolman.value?.conn)
                .firstOrNull { it.baseUrl.origin() == origin }?.client ?: http
            return client.newCall(request)
        }
    }

    val events = EventDetector(context)
    val firstLayer = com.vknxworks.printctrl.notify.FirstLayerCheck(context, appScope)

    /** A printer page to open, from a notification, the widget or the tile: id and whether to ask about stopping. */
    val openRequest = kotlinx.coroutines.flow.MutableStateFlow<Pair<Int, Boolean>?>(null)

    /** A spool to open from a low-stock notification; -1 opens the Spools tab. */
    val openSpoolRequest = kotlinx.coroutines.flow.MutableStateFlow<Int?>(null)

    /** A file another app shared to PrintCtrl, waiting to be uploaded to Bambuddy's library. */
    val sharedFile = kotlinx.coroutines.flow.MutableStateFlow<android.net.Uri?>(null)

    /**
     * After a server is removed: drops what the app kept from it on the phone, so no printer or job
     * names, pictures, timelapses or notifications from it stay behind.
     */
    suspend fun forgetServerData(kind: ServerKind): Unit = withContext(Dispatchers.IO) {
        runCatching {
            val images = coil3.SingletonImageLoader.get(context)
            images.memoryCache?.clear()
            images.diskCache?.clear()
        }
        when (kind) {
            ServerKind.BAMBUDDY -> {
                runCatching { WidgetState.clear(context) }
                listOf("first_layer", "print_events").forEach { name ->
                    context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().apply()
                }
                context.cacheDir.listFiles()?.filter { it.name.startsWith("timelapse-") }?.forEach { it.delete() }
                runCatching {
                    val nm = context.getSystemService(android.app.NotificationManager::class.java)
                    if (nm != null) {
                        nm.activeNotifications.filter { it.notification.channelId != Notifier.CHANNEL_STOCK }.forEach { nm.cancel(it.tag, it.id) }
                    }
                }
            }
            ServerKind.SPOOLMAN -> context.getSharedPreferences("low_stock", Context.MODE_PRIVATE).edit().clear().apply()
        }
    }

    /**
     * Whether the secrets should be sealed with the app PIN: the lock is on and nothing in the background
     * (notifications, the progress notification) has to talk to the servers while PrintCtrl is locked.
     */
    private suspend fun shouldSeal(): Boolean = vault.enabled && !settings.current().backgroundUse

    /** Background work can't read the API key or proxy login right now: they're sealed and PrintCtrl is locked. */
    suspend fun backgroundBlocked(): Boolean {
        if (!vault.enabled || vault.isOpen) return false
        val setup = withTimeoutOrNull(10_000) { servers.setup.filterNotNull().first() } ?: return false
        return listOfNotNull(setup.bambuddy, setup.spoolman).any { it.anySealed }
    }

    /** Turns the app lock on with [pin]. */
    suspend fun enableLock(pin: String) {
        // Unlocked first, so the lock screen doesn't flash up while the PIN is being saved.
        AppLock.markUnlocked()
        withContext(Dispatchers.Default) { vault.setUp(pin) }
        servers.applySealing(shouldSeal())
    }

    /** Turns the app lock off (after the PIN was confirmed): secrets go back to Keystore-only encryption. */
    suspend fun disableLock() {
        servers.applySealing(false)
        vault.disable()
    }

    /** "Forgot PIN?": the sealed secrets can't be read any more, so remove both servers and start over. */
    suspend fun forgetEverything() {
        servers.removeAll()
        forgetServerData(ServerKind.BAMBUDDY)
        forgetServerData(ServerKind.SPOOLMAN)
        vault.disable()
        AppLock.markUnlocked()
    }

    /** Locked: wipe the key from memory; with sealed secrets, the clients drop them too. */
    fun onLocked() {
        vault.close()
        if (servers.anySealed()) connections.rebuild()
    }

    fun onUnlocked() {
        AppLock.markUnlocked()
        if (servers.anySealed()) connections.rebuild()
        // Notifications may have been turned on or off since the secrets were last sealed.
        appScope.launch { runCatching { servers.applySealing(shouldSeal()) } }
    }

    init {
        KeystoreCrypto.sealing = { vault.enabled && !settings.settings.value.backgroundUse }
        AppLock.onLocked = { onLocked() }
        // Turning notifications or the progress notification on unseals the secrets (they sign in while
        // PrintCtrl is locked), and turning both off seals them again. Only possible while unlocked.
        appScope.launch {
            settings.settings.map { it.backgroundUse }.distinctUntilChanged().drop(1).collect {
                if (vault.isOpen) runCatching { servers.applySealing(shouldSeal()) }
            }
        }
        Notifier.createChannels(context)
        UnifiedPush.load(context)
        // Every printer update feeds the notifications, the widget and the tile.
        appScope.launch {
            var activeBefore = emptySet<Int>()
            printers.state.collect { s ->
                val st = settings.settings.value
                // A print just ended: check the spools for low stock straight away.
                val activeNow = s.printers.filter { it.status?.phase?.isActive == true }.map { it.id }.toSet()
                if ((activeBefore - activeNow).isNotEmpty() && st.notifyMode != com.vknxworks.printctrl.data.settings.NotifyMode.OFF) {
                    PollWorker.now(context)
                }
                activeBefore = activeNow
                s.printers.forEach { p ->
                    p.status?.let {
                        events.observe(p.id, p.name, it, st)
                        firstLayer.observe(p.id, p.name, it, st)
                    }
                }
                if (s.loaded && s.error == null) runCatching { WidgetState.publish(context, s.printers) }
                if (s.printers.any { it.status?.phase?.isActive == true }) PrintMonitorService.start(context)
            }
        }
        // Get a camera token as soon as Bambuddy is reachable, so the camera opens without waiting for one.
        appScope.launch {
            bambuddy.collect { if (it != null) cameras.warmUp() }
        }
        // Background checks follow the chosen notification mode.
        appScope.launch {
            settings.settings.map { it.notifyMode }.distinctUntilChanged().collect { PollWorker.schedule(context, it) }
        }
        // Live printer updates run only while the app is on screen.
        appScope.launch(Dispatchers.Main) {
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    AppLock.onForeground(vault.enabled, settings.settings.value.lockTimeout.millis)
                    printers.acquire("ui")
                }

                override fun onStop(owner: LifecycleOwner) {
                    printers.release("ui")
                    AppLock.onBackground(vault.enabled, settings.settings.value.lockTimeout.millis)
                }
            })
        }
    }
}

val Context.container: AppContainer
    get() = (applicationContext as PrintCtrlApp).container
