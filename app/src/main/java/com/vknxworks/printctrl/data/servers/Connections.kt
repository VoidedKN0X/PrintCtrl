package com.vknxworks.printctrl.data.servers

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import com.vknxworks.printctrl.data.net.Tls
import com.vknxworks.printctrl.data.net.below
import com.vknxworks.printctrl.data.net.origin
import com.vknxworks.printctrl.data.net.text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext

/** A server address the app is talking to, with an HTTP client set up for it (credentials, pinned certificate). */
class ServerConnection(val kind: ServerKind, val baseUrl: HttpUrl, val client: OkHttpClient) {
    /** Changes whenever the address or its settings change, so screens can reload. */
    val key: String = "${kind.name}:$baseUrl:${System.identityHashCode(client)}"
}

sealed interface ConnState {
    /** The user hasn't set this server up (or skipped it). */
    data object NotSet : ConnState
    data object Connecting : ConnState
    data class Ready(val conn: ServerConnection) : ConnState
    /** None of the saved addresses answered. */
    data class Unreachable(val error: Throwable) : ConnState
}

/** Builds HTTP clients for one server address. */
object ServerClients {
    fun build(
        base: OkHttpClient,
        kind: ServerKind,
        url: HttpUrl,
        apiKey: String?,
        username: String?,
        password: String?,
        pin: String?,
    ): OkHttpClient {
        val origin = url.origin()
        val b = base.newBuilder()
        // Credentials are only ever added to requests for this server; RedirectGuard strips them if a
        // redirect leaves it.
        b.interceptors().add(0, okhttp3.Interceptor { chain ->
            val req = chain.request()
            if (req.url.origin() != origin) return@Interceptor chain.proceed(req)
            val nb = req.newBuilder()
            when (kind) {
                ServerKind.BAMBUDDY -> if (!apiKey.isNullOrBlank()) nb.header("X-API-Key", apiKey)
                ServerKind.SPOOLMAN -> if (!username.isNullOrBlank()) {
                    nb.header("Authorization", Credentials.basic(username, password.orEmpty()))
                }
            }
            // Spoolman refuses writes that carry a browser Origin header; the app never sends one.
            nb.removeHeader("Origin")
            chain.proceed(nb.build())
        })
        if (pin != null && url.isHttps) {
            val tm = Tls.PinnedTrustManager(pin, Tls.systemTrustManager())
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, arrayOf(tm), null)
            b.sslSocketFactory(ctx.socketFactory, tm)
            b.hostnameVerifier(Tls.pinnedHostnameVerifier(pin, url.host))
        }
        return b.build()
    }

    fun build(base: OkHttpClient, kind: ServerKind, url: HttpUrl, config: ServerConfig): OkHttpClient =
        build(base, kind, url, config.apiKeyPlain(), config.username, config.passwordPlain(), config.pins[url.origin()])

    /** A cheap request that any version of the server answers. */
    fun probeRequest(kind: ServerKind, url: HttpUrl): Request = Request.Builder().url(
        when (kind) {
            ServerKind.BAMBUDDY -> url.below("api/v1/updates/version").build()
            ServerKind.SPOOLMAN -> url.below("api/v1/info").build()
        },
    ).build()
}

/**
 * Keeps one live connection per configured server. With several addresses saved, the first one that
 * answers wins; this is checked again whenever the phone switches networks.
 */
class Connections(
    context: Context,
    private val base: OkHttpClient,
    store: ServerStore,
    scope: CoroutineScope,
) {
    private val _bambuddy = MutableStateFlow<ConnState>(ConnState.NotSet)
    private val _spoolman = MutableStateFlow<ConnState>(ConnState.NotSet)
    val bambuddy: StateFlow<ConnState> = _bambuddy.asStateFlow()
    val spoolman: StateFlow<ConnState> = _spoolman.asStateFlow()

    private val trigger = MutableStateFlow(0)

    init {
        scope.launch {
            combine(store.setup.filterNotNull(), trigger) { s, _ -> s }.collectLatest { s ->
                coroutineScope {
                    launch { resolve(ServerKind.BAMBUDDY, s.bambuddy, _bambuddy) }
                    launch { resolve(ServerKind.SPOOLMAN, s.spoolman, _spoolman) }
                }
            }
        }
        context.getSystemService(ConnectivityManager::class.java)?.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    trigger.value++
                }
            },
        )
    }

    fun state(kind: ServerKind): StateFlow<ConnState> = when (kind) {
        ServerKind.BAMBUDDY -> bambuddy
        ServerKind.SPOOLMAN -> spoolman
    }

    /** Checks the saved addresses again (pull to refresh on an offline screen). */
    fun retry() {
        trigger.value++
    }

    private suspend fun resolve(kind: ServerKind, cfg: ServerConfig?, out: MutableStateFlow<ConnState>) {
        val urls = cfg?.addresses.orEmpty().mapNotNull { it.toHttpUrlOrNull() }
        if (cfg == null || urls.isEmpty()) {
            out.value = ConnState.NotSet
            return
        }
        val conns = urls.map { ServerConnection(kind, it, ServerClients.build(base, kind, it, cfg)) }
        if (conns.size == 1) {
            out.value = ConnState.Ready(conns.single())
            return
        }
        if (out.value !is ConnState.Ready) out.value = ConnState.Connecting
        var last: Throwable? = null
        for (c in conns) {
            try {
                val quick = c.client.newBuilder().callTimeout(5, TimeUnit.SECONDS).build()
                quick.text(ServerClients.probeRequest(kind, c.baseUrl))
                out.value = ConnState.Ready(c)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                last = e
            }
        }
        out.value = ConnState.Unreachable(last ?: IOException("No address answered"))
    }
}
