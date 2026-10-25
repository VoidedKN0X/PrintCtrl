package com.vknxworks.printctrl.notify

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import com.vknxworks.printctrl.container
import com.vknxworks.printctrl.data.bambuddy.LivePrinter
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps the live connection to Bambuddy open while a print runs, and shows an ongoing notification per
 * running printer with its progress, time left, a camera picture, and pause/resume and stop. Stops by
 * itself a minute after the last print ends.
 */
class PrintMonitorService : Service() {
    // A failed update of the notification never takes the app down; the next update tries again.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, _ -> })
    private val pictures = ConcurrentHashMap<Int, Bitmap>()
    private val pictureJobs = ConcurrentHashMap<Int, Job>()
    private var shown = emptySet<Int>()
    private var foregroundId = 0
    private var foregroundNotification: Notification? = null
    private var started = false
    @Volatile
    private var lastStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        if (!started) {
            foregroundId = Notifier.progressId(0)
            if (!goForeground(foregroundId, Notifier.serviceIdle(this))) {
                stopSelf()
                return START_NOT_STICKY
            }
            started = true
            running = true
            container.printers.acquire(HOLDER)
            scope.launch { watch() }
        } else {
            // Every startForegroundService() has to be answered with startForeground(), also while running.
            val n = foregroundNotification ?: Notifier.serviceIdle(this)
            if (!goForeground(foregroundId, n)) stopSelf()
        }
        return START_NOT_STICKY
    }

    /**
     * The "special use" service type exists from Android 14; before that the manifest's type applies.
     * False when Android refuses (started from the background without an exemption, for one).
     */
    private fun goForeground(id: Int, notification: Notification): Boolean = try {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(id, notification)
        }
        foregroundNotification = notification
        true
    } catch (e: Exception) {
        false
    }

    /**
     * Stops unless a newer start came in meanwhile (it would otherwise be left without its
     * startForeground() and Android would crash the app). Tries again later in that case.
     */
    private suspend fun stopWhenIdle() {
        while (true) {
            delay(IDLE_STOP_MS)
            if (stopSelfResult(lastStartId)) return
        }
    }

    private suspend fun watch() {
        val c = container
        launchTicker()
        kotlinx.coroutines.flow.combine(c.printers.state, c.settings.settings) { state, settings -> state to settings }
            .collectLatest { (state, settings) ->
                if (!settings.progressNotification) {
                    stopSelfResult(lastStartId)
                    return@collectLatest
                }
                val active = state.printers.filter { it.status?.phase?.isActive == true }
                render(active)
                if (active.isEmpty()) {
                    // Wait a little in case the state flickers between jobs, then stop. A new state
                    // arriving first cancels this.
                    stopWhenIdle()
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
                    if (!goForeground(foregroundId, n)) {
                        foregroundId = old
                        return
                    }
                    if (old != foregroundId && old != Notifier.progressId(0)) nm.cancel(old)
                    nm.cancel(Notifier.progressId(0))
                } else {
                    foregroundNotification = n
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
                CameraSnapshot.fetch(this@PrintMonitorService, p.id, sample = 4)?.let { pictures[p.id] = it }
                delay(120_000)
            }
        }
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        if (started) container.printers.release(HOLDER)
        val nm = NotificationManagerCompat.from(this)
        shown.forEach { nm.cancel(Notifier.progressId(it)) }
        super.onDestroy()
    }

    companion object {
        private const val HOLDER = "monitor"
        private const val IDLE_STOP_MS = 60_000L

        /** The service is up; printer updates then don't start it again. */
        @Volatile
        private var running = false

        /**
         * Starts the progress notification when the user wants it. From the background this only works
         * when PrintCtrl is exempt from battery optimisation; otherwise Android refuses and nothing happens.
         */
        fun start(context: Context) {
            val c = context.container
            if (running) return
            if (!c.settings.settings.value.progressNotification) return
            if (!Notifier.canPost(context)) return
            try {
                context.startForegroundService(Intent(context, PrintMonitorService::class.java))
            } catch (e: ForegroundServiceStartNotAllowedException) {
                // Background start not allowed right now; the next poll or app start tries again.
            } catch (e: IllegalStateException) {
                // Same, on some OEM builds.
            } catch (e: SecurityException) {
                // Some OEM builds refuse it this way.
            }
        }
    }
}
