package com.vknxworks.printctrl.notify

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import com.vknxworks.printctrl.container
import com.vknxworks.printctrl.data.bambuddy.LivePrinter
import com.vknxworks.printctrl.data.net.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps the live connection to Bambuddy open while a print runs, and shows an ongoing notification per
 * running printer with its progress, time left, a camera picture, and pause/resume and stop. Stops by
 * itself a minute after the last print ends.
 */
class PrintMonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val pictures = ConcurrentHashMap<Int, Bitmap>()
    private val pictureJobs = ConcurrentHashMap<Int, Job>()
    private var shown = emptySet<Int>()
    private var foregroundId = 0
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!started) {
            started = true
            foregroundId = Notifier.progressId(0)
            goForeground(foregroundId, Notifier.serviceIdle(this))
            container.printers.acquire(HOLDER)
            scope.launch { watch() }
        }
        return START_NOT_STICKY
    }

    /** The "special use" service type exists from Android 14; before that the manifest's type applies. */
    private fun goForeground(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(id, notification)
        }
    }

    private suspend fun watch() {
        val c = container
        launchTicker()
        kotlinx.coroutines.flow.combine(c.printers.state, c.settings.settings) { state, settings -> state to settings }
            .collectLatest { (state, settings) ->
                if (!settings.progressNotification) {
                    stopSelf()
                    return@collectLatest
                }
                val active = state.printers.filter { it.status?.phase?.isActive == true }
                render(active)
                if (active.isEmpty()) {
                    // Wait a little in case the state flickers between jobs, then stop. A new state
                    // arriving first cancels this.
                    delay(IDLE_STOP_MS)
                    stopSelf()
                }
            }
    }

    /** Re-renders once a minute so "done at" stays right even when nothing else changes. */
    private fun launchTicker() = scope.launch {
        while (true) {
            delay(60_000)
            render(container.printers.state.value.printers.filter { it.status?.phase?.isActive == true })
        }
    }

    @Synchronized
    private fun render(active: List<LivePrinter>) {
        val nm = NotificationManagerCompat.from(this)
        if (!Notifier.canPost(this)) return
        val ids = active.map { it.id }.toSet()
        active.forEachIndexed { i, p ->
            ensurePicture(p)
            val n = Notifier.progress(this, p, pictures[p.id])
            if (i == 0) {
                // The first running printer carries the service.
                if (foregroundId != Notifier.progressId(p.id)) {
                    val old = foregroundId
                    foregroundId = Notifier.progressId(p.id)
                    goForeground(foregroundId, n)
                    if (old != foregroundId && old != Notifier.progressId(0)) nm.cancel(old)
                    nm.cancel(Notifier.progressId(0))
                } else {
                    @Suppress("MissingPermission")
                    nm.notify(foregroundId, n)
                }
            } else {
                @Suppress("MissingPermission")
                nm.notify(Notifier.progressId(p.id), n)
            }
        }
        (shown - ids).forEach { id ->
            if (Notifier.progressId(id) != foregroundId) nm.cancel(Notifier.progressId(id))
            pictureJobs.remove(id)?.cancel()
            pictures.remove(id)
        }
        shown = ids
    }

    /** A camera picture for the notification, refreshed every two minutes while the print runs. */
    private fun ensurePicture(p: LivePrinter) {
        if (pictureJobs[p.id]?.isActive == true) return
        pictureJobs[p.id] = scope.launch {
            while (true) {
                runCatching { fetchPicture(p.id) }.getOrNull()?.let { pictures[p.id] = it }
                delay(120_000)
            }
        }
    }

    private suspend fun fetchPicture(printerId: Int): Bitmap? = withContext(Dispatchers.IO) {
        val api = container.bambuddy.value ?: return@withContext null
        val token = api.cameraToken()
        api.conn.client.newCall(Request.Builder().url(api.cameraSnapshotUrl(printerId, token)).build()).await().use { r ->
            if (!r.isSuccessful) return@use null
            val bytes = r.body?.bytes() ?: return@use null
            val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        container.printers.release(HOLDER)
        val nm = NotificationManagerCompat.from(this)
        shown.forEach { nm.cancel(Notifier.progressId(it)) }
        super.onDestroy()
    }

    companion object {
        private const val HOLDER = "monitor"
        private const val IDLE_STOP_MS = 60_000L

        /**
         * Starts the progress notification when the user wants it. From the background this only works
         * when PrintCtrl is exempt from battery optimisation; otherwise Android refuses and nothing happens.
         */
        fun start(context: Context) {
            val c = context.container
            if (!c.settings.settings.value.progressNotification) return
            if (!Notifier.canPost(context)) return
            try {
                context.startForegroundService(Intent(context, PrintMonitorService::class.java))
            } catch (e: ForegroundServiceStartNotAllowedException) {
                // Background start not allowed right now; the next poll or app start tries again.
            } catch (e: IllegalStateException) {
                // Same, on some OEM builds.
            }
        }
    }
}
