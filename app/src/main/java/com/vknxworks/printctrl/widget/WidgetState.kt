package com.vknxworks.printctrl.widget

import android.content.ComponentName
import android.content.Context
import android.graphics.BitmapFactory
import android.service.quicksettings.TileService
import androidx.glance.appwidget.updateAll
import com.vknxworks.printctrl.container
import com.vknxworks.printctrl.data.bambuddy.LivePrinter
import com.vknxworks.printctrl.data.bambuddy.PrintPhase
import com.vknxworks.printctrl.data.net.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.Request
import java.io.File

/** What the widget and the Quick Settings tile show for one printer. */
@Serializable
data class PrinterSnapshot(
    val id: Int,
    val name: String,
    val phase: String,
    val job: String? = null,
    val progress: Int? = null,
    /** Epoch ms when the print should be done. */
    val etaMs: Long? = null,
    val layer: Int? = null,
    val totalLayers: Int? = null,
    /** File in the app's cache with the job's picture, if one was fetched. */
    val cover: String? = null,
) {
    val printPhase: PrintPhase get() = PrintPhase.entries.firstOrNull { it.name == phase } ?: PrintPhase.UNKNOWN
}

/**
 * Keeps a small copy of the printers' state on disk for the widget and the tile, which run outside the
 * app's screens and can't wait for a connection. Updated from the live connection, the background
 * checks and the progress notification; covers are fetched once per job.
 */
object WidgetState {
    private val json = Json { ignoreUnknownKeys = true }
    private const val PREFS = "widget"
    private const val KEY = "printers"
    @Volatile
    private var lastPublish = 0L
    @Volatile
    private var lastKey = ""

    fun read(context: Context): List<PrinterSnapshot> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        return runCatching { json.decodeFromString(ListSerializer(PrinterSnapshot.serializer()), raw) }.getOrDefault(emptyList())
    }

    /**
     * Saves [printers] and refreshes the widget and tile. Throttled: the widget is redrawn at most every
     * 30 seconds unless something other than the numbers changed (state, job).
     */
    suspend fun publish(context: Context, printers: List<LivePrinter>, force: Boolean = false) {
        val now = System.currentTimeMillis()
        val key = printers.joinToString("|") { "${it.id}:${it.status?.phase}:${it.status?.jobName}" }
        if (!force && key == lastKey && now - lastPublish < 30_000) return
        lastKey = key
        lastPublish = now
        val old = read(context).associateBy { it.id }
        val snaps = printers.map { p ->
            val s = p.status
            val job = s?.jobName
            val coverFile = File(context.cacheDir, "widget-cover-${p.id}.jpg")
            val keepCover = old[p.id]?.job == job && old[p.id]?.cover != null && coverFile.exists()
            val cover = when {
                job == null -> null
                keepCover -> coverFile.path
                else -> fetchCover(context, p.id, coverFile)
            }
            PrinterSnapshot(
                id = p.id,
                name = p.name,
                phase = (s?.phase ?: PrintPhase.UNKNOWN).name,
                job = job,
                progress = s?.progress?.toInt(),
                etaMs = s?.remainingTime?.takeIf { it > 0 && s?.phase?.isActive == true }?.let { now + it * 60_000L },
                layer = s?.layerNum,
                totalLayers = s?.totalLayers,
                cover = cover,
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, json.encodeToString(ListSerializer(PrinterSnapshot.serializer()), snaps)).apply()
        runCatching { PrinterWidget().updateAll(context) }
        runCatching { TileService.requestListeningState(context, ComponentName(context, PrintTileService::class.java)) }
    }

    private suspend fun fetchCover(context: Context, printerId: Int, file: File): String? = withContext(Dispatchers.IO) {
        val api = context.container.bambuddy.value ?: return@withContext null
        runCatching {
            api.conn.client.newCall(Request.Builder().url(api.coverUrl(printerId)).build()).await().use { r ->
                if (!r.isSuccessful) return@use null
                val bytes = r.body?.bytes() ?: return@use null
                // Keep it small: widgets have a tight memory budget.
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return@use null
                file.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }
                file.path
            }
        }.getOrNull()
    }
}
