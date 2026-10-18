package com.vknxworks.printctrl

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.vknxworks.printctrl.data.bambuddy.BambuddyClient
import com.vknxworks.printctrl.data.bambuddy.PrintersRepository
import com.vknxworks.printctrl.data.net.CleartextPolicy
import com.vknxworks.printctrl.data.net.RedirectGuard
import com.vknxworks.printctrl.data.net.origin
import com.vknxworks.printctrl.data.servers.ConnState
import com.vknxworks.printctrl.data.servers.Connections
import com.vknxworks.printctrl.data.servers.ServerStore
import com.vknxworks.printctrl.data.settings.SettingsRepository
import com.vknxworks.printctrl.data.spoolman.SpoolmanClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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

    val settings = SettingsRepository(context, appScope)
    val servers = ServerStore(context, appScope)
    val connections = Connections(context, http, servers, appScope)

    val bambuddy: StateFlow<BambuddyClient?> = connections.bambuddy
        .map { (it as? ConnState.Ready)?.let { r -> BambuddyClient(r.conn) } }
        .stateIn(appScope, SharingStarted.Eagerly, null)

    val spoolman: StateFlow<SpoolmanClient?> = connections.spoolman
        .map { (it as? ConnState.Ready)?.let { r -> SpoolmanClient(r.conn) } }
        .stateIn(appScope, SharingStarted.Eagerly, null)

    val printers = PrintersRepository(connections.bambuddy, bambuddy, appScope)

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

    init {
        // Live printer updates run only while the app is on screen.
        appScope.launch(Dispatchers.Main) {
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = printers.start()
                override fun onStop(owner: LifecycleOwner) = printers.stop()
            })
        }
    }
}

val Context.container: AppContainer
    get() = (applicationContext as PrintCtrlApp).container
