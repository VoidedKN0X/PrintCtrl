package com.vknxworks.printctrl.data.bambuddy

import com.vknxworks.printctrl.data.net.ApiJson
import com.vknxworks.printctrl.data.servers.ConnState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** One temperature reading, kept for the small graphs on the printer page. */
data class TempSample(val atMs: Long, val nozzle: Float?, val nozzleTarget: Float?, val bed: Float?, val bedTarget: Float?)

data class LivePrinter(
    val printer: Printer,
    val status: PrinterStatus?,
    val temps: List<TempSample> = emptyList(),
    /** When the running job started, from its archive entry. Null when unknown. */
    val startedAtMs: Long? = null,
) {
    val id: Int get() = printer.id
    val name: String get() = printer.name.ifBlank { status?.name.orEmpty() }
}

data class PrintersState(
    /** False until the first answer (or error) for the current server. */
    val loaded: Boolean = false,
    val printers: List<LivePrinter> = emptyList(),
    val error: Throwable? = null,
    /** True while the live-updates socket is connected; otherwise the list is polled. */
    val live: Boolean = false,
)

/**
 * Live printer state while the app is open: a full refresh over REST, then Bambuddy's WebSocket for
 * updates as they happen (each message carries the whole status of one printer). If the socket can't
 * be opened, for example because the API key may not use it, the status is polled every 10 seconds.
 */
class PrintersRepository(
    private val connection: StateFlow<ConnState>,
    private val client: StateFlow<BambuddyClient?>,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(PrintersState())
    val state: StateFlow<PrintersState> = _state.asStateFlow()

    private val archiveStarts = ConcurrentHashMap<Int, Long>()
    private val refreshRequests = Channel<Unit>(Channel.CONFLATED)
    private var job: Job? = null

    fun printer(id: Int): Flow<LivePrinter?> = state.map { s -> s.printers.firstOrNull { it.id == id } }.distinctUntilChanged()

    private val holders = mutableSetOf<String>()

    /**
     * Live updates run while anyone holds them: the app on screen ("ui") or the print progress
     * notification ("monitor").
     */
    @Synchronized
    fun acquire(holder: String) {
        holders += holder
        start()
    }

    @Synchronized
    fun release(holder: String) {
        holders -= holder
        if (holders.isEmpty()) stop()
    }

    private fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            combine(connection, client) { c, cl -> c to cl }.collectLatest { (c, cl) ->
                when {
                    cl != null -> run(cl)
                    c is ConnState.NotSet -> _state.value = PrintersState(loaded = true)
                    else -> Unit
                }
            }
        }
    }

    private fun stop() {
        job?.cancel()
        job = null
        _state.update { it.copy(live = false) }
    }

    /** Pull to refresh. */
    fun refresh() {
        refreshRequests.trySend(Unit)
    }

    private suspend fun run(client: BambuddyClient) = coroutineScope {
        var forceNewToken = false
        var backoff = 2_000L
        while (true) {
            refreshAll(client)
            val closed = CompletableDeferred<Int>()
            val socket = openSocket(client, forceNewToken, closed)
            if (socket == null) {
                // No live updates: poll instead.
                select<Unit> {
                    refreshRequests.onReceive { }
                    onTimeout(10_000) { }
                }
                continue
            }
            val openedAt = System.currentTimeMillis()
            _state.update { it.copy(live = true) }
            try {
                while (!closed.isCompleted) {
                    // A full refresh now and then picks up renamed printers and new covers.
                    val again = select<Boolean> {
                        closed.onAwait { false }
                        refreshRequests.onReceive { true }
                        onTimeout(60_000) { true }
                    }
                    if (again) refreshAll(client)
                }
            } finally {
                socket.cancel()
                _state.update { it.copy(live = false) }
            }
            val code = closed.getCompleted()
            forceNewToken = code == CLOSE_UNAUTHORIZED
            backoff = if (System.currentTimeMillis() - openedAt > 30_000) 2_000L else (backoff * 2).coerceAtMost(30_000L)
            delay(backoff)
        }
    }

    private suspend fun refreshAll(client: BambuddyClient) {
        try {
            val printers = client.printers().filter { it.isActive }
            val statuses = coroutineScope {
                printers.map { p -> async { p.id to runCatching { client.status(p.id) }.getOrNull() } }.awaitAll()
            }.toMap()
            _state.update { s ->
                val old = s.printers.associateBy { it.id }
                s.copy(
                    loaded = true,
                    error = null,
                    printers = printers.map { p ->
                        val prev = old[p.id]
                        val status = statuses[p.id]?.merge(prev?.status) ?: prev?.status
                        LivePrinter(p, status, prev?.temps.orEmpty(), prev?.startedAtMs).withSample(status)
                    },
                )
            }
            statuses.values.filterNotNull().forEach { lookUpStart(client, it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            _state.update { it.copy(loaded = true, error = e) }
        }
    }

    private suspend fun openSocket(client: BambuddyClient, forceNewToken: Boolean, closed: CompletableDeferred<Int>): WebSocket? {
        val token = try {
            client.socketToken(forceNewToken)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return null
        }
        val opened = CompletableDeferred<Boolean>()
        val http = client.conn.client.newBuilder().pingInterval(20, TimeUnit.SECONDS).readTimeout(0, TimeUnit.MILLISECONDS).build()
        val ws = http.newWebSocket(
            Request.Builder().url(client.socketUrl(token)).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    opened.complete(true)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleMessage(client, text)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                    opened.complete(false)
                    closed.complete(code)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    opened.complete(false)
                    closed.complete(code)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    opened.complete(false)
                    closed.complete(-1)
                }
            },
        )
        return if (opened.await()) ws else {
            ws.cancel()
            null
        }
    }

    private fun handleMessage(client: BambuddyClient, text: String) {
        val msg = runCatching { ApiJson.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return
        val type = (msg["type"] as? JsonPrimitive)?.contentOrNull
        val printerId = (msg["printer_id"] as? JsonPrimitive)?.intOrNull
        when (type) {
            "printer_status" -> {
                val data = msg["data"] as? JsonObject ?: return
                val status = runCatching { ApiJson.decodeFromJsonElement(PrinterStatus.serializer(), data) }.getOrNull() ?: return
                if (printerId == null) return
                var known = false
                _state.update { s ->
                    s.copy(
                        printers = s.printers.map { lp ->
                            if (lp.id != printerId) lp
                            else {
                                known = true
                                lp.copy(status = status.copy(id = printerId).merge(lp.status)).withSample(status)
                            }
                        },
                    )
                }
                if (!known) refresh()
            }
            // The job changed: get the new cover, name and archive entry.
            "print_start", "print_complete" -> refresh()
            else -> Unit
        }
    }

    private fun lookUpStart(client: BambuddyClient, status: PrinterStatus) {
        val archiveId = status.currentArchiveId ?: return
        if (!status.phase.isActive) return
        archiveStarts[archiveId]?.let { start -> applyStart(status.id, start); return }
        scope.launch {
            val started = runCatching { client.archive(archiveId).startedAt }.getOrNull()?.let { parseInstant(it) } ?: return@launch
            archiveStarts[archiveId] = started
            applyStart(status.id, started)
        }
    }

    private fun applyStart(printerId: Int, start: Long) {
        _state.update { s -> s.copy(printers = s.printers.map { if (it.id == printerId) it.copy(startedAtMs = start) else it }) }
    }

    private fun LivePrinter.withSample(status: PrinterStatus?): LivePrinter {
        if (status == null) return this
        val now = System.currentTimeMillis()
        val last = temps.lastOrNull()
        if (last != null && now - last.atMs < SAMPLE_EVERY_MS) return this
        val sample = TempSample(
            now,
            status.nozzle?.toFloat(),
            status.nozzleTarget?.toFloat(),
            status.bed?.toFloat(),
            status.bedTarget?.toFloat(),
        )
        val kept = temps.dropWhile { now - it.atMs > HISTORY_MS }
        // Without a running job the start time no longer applies.
        val start = if (status.phase.isActive) startedAtMs else null
        return copy(temps = kept + sample, startedAtMs = start)
    }

    companion object {
        private const val CLOSE_UNAUTHORIZED = 4401
        private const val SAMPLE_EVERY_MS = 2_000L
        const val HISTORY_MS = 15 * 60 * 1000L

        /** Bambuddy stores times in UTC; some come back without an offset. */
        fun parseInstant(text: String): Long? =
            runCatching { OffsetDateTime.parse(text).toInstant().toEpochMilli() }.getOrNull()
                ?: runCatching { Instant.parse(text).toEpochMilli() }.getOrNull()
                ?: runCatching { LocalDateTime.parse(text).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
    }
}
