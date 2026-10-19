package com.vknxworks.printctrl.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.vknxworks.printctrl.MainActivity
import com.vknxworks.printctrl.R
import com.vknxworks.printctrl.data.bambuddy.LivePrinter
import com.vknxworks.printctrl.data.bambuddy.PrintPhase
import com.vknxworks.printctrl.data.settings.PrintEvent
import com.vknxworks.printctrl.ui.components.formatClock
import com.vknxworks.printctrl.ui.components.formatMinutes
import kotlin.math.roundToInt

/** Notification channels and the notifications PrintCtrl shows. */
object Notifier {
    const val CHANNEL_PROGRESS = "progress"
    const val CHANNEL_EVENTS = "events"
    const val CHANNEL_PROBLEMS = "problems"
    const val CHANNEL_SERVICE = "service"
    const val CHANNEL_STOCK = "stock"

    /** Extras on the intent that opens the app from a notification. */
    const val EXTRA_OPEN_PRINTER = "open_printer"
    const val EXTRA_CONFIRM_STOP = "confirm_stop"
    const val EXTRA_OPEN_SPOOL = "open_spool"
    /** Any value: open the Spools tab. */
    const val EXTRA_OPEN_SPOOLS = "open_spools"

    // Notification extras of Android 16 (API 36), spelled out so they also compile against older SDKs.
    private const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"
    private const val EXTRA_SHORT_CRITICAL_TEXT = "android.shortCriticalText"
    private const val PROGRESS_COLOR = 0xFFFFB27A.toInt()
    private const val PAUSED_COLOR = 0xFFE8A33D.toInt()

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_PROGRESS, context.getString(R.string.channel_progress), NotificationManager.IMPORTANCE_LOW).apply {
                    description = context.getString(R.string.channel_progress_desc)
                    setShowBadge(false)
                },
                NotificationChannel(CHANNEL_EVENTS, context.getString(R.string.channel_events), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = context.getString(R.string.channel_events_desc)
                },
                NotificationChannel(CHANNEL_PROBLEMS, context.getString(R.string.channel_problems), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = context.getString(R.string.channel_problems_desc)
                },
                NotificationChannel(CHANNEL_STOCK, context.getString(R.string.channel_stock), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = context.getString(R.string.channel_stock_desc)
                },
                NotificationChannel(CHANNEL_SERVICE, context.getString(R.string.channel_service), NotificationManager.IMPORTANCE_MIN).apply {
                    description = context.getString(R.string.channel_service_desc)
                    setShowBadge(false)
                },
            ),
        )
    }

    fun canPost(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Opens the app on [printerId]'s page; with [confirmStop] it asks straight away whether to stop the print. */
    fun openPrinter(context: Context, printerId: Int, confirmStop: Boolean = false): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_PRINTER, printerId)
            putExtra(EXTRA_CONFIRM_STOP, confirmStop)
        }
        val request = printerId * 4 + if (confirmStop) 1 else 0
        return PendingIntent.getActivity(context, request, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun progressId(printerId: Int) = 10_000 + printerId
    private fun eventId(printerId: Int, event: PrintEvent) = 20_000 + printerId * 16 + event.ordinal

    /** The ongoing notification for a running print: progress, time left, a camera picture and controls. */
    fun progress(context: Context, p: LivePrinter, picture: Bitmap?): android.app.Notification {
        val s = p.status
        val phase = s?.phase ?: PrintPhase.UNKNOWN
        val percent = s?.progress?.roundToInt()?.coerceIn(0, 100) ?: 0
        val title = when (phase) {
            PrintPhase.PAUSED -> context.getString(R.string.notif_paused_title, p.name, percent)
            PrintPhase.PREPARING -> context.getString(R.string.notif_preparing_title, p.name)
            else -> context.getString(R.string.notif_progress_title, p.name, percent)
        }
        val facts = buildList {
            val layer = s?.layerNum
            val total = s?.totalLayers
            if (layer != null && total != null && total > 0) add(context.getString(R.string.layers_value, layer, total))
            val left = s?.remainingTime
            if (left != null && left > 0) {
                add(context.getString(R.string.time_left, formatMinutes(context, left)))
                add(context.getString(R.string.eta_value, formatClock(context, System.currentTimeMillis() + left * 60_000L)))
            }
        }.joinToString("  ·  ")
        val b = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(s?.jobName ?: "")
            .setSubText(facts.ifEmpty { null })
            .setStyle(NotificationCompat.BigTextStyle().bigText(listOfNotNull(s?.jobName, facts.ifEmpty { null }).joinToString("\n")))
            .setProgress(100, percent, phase == PrintPhase.PREPARING)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openPrinter(context, p.id))
        if (picture != null) b.setLargeIcon(picture)
        // Android 16 Live Update: a chip in the status bar and a place at the top of the lock screen and
        // shade. Android decides whether it's shown that way (the user can turn it off per app).
        b.addExtras(
            Bundle().apply {
                putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true)
                putString(
                    EXTRA_SHORT_CRITICAL_TEXT,
                    when (phase) {
                        PrintPhase.PAUSED -> context.getString(R.string.chip_paused)
                        PrintPhase.PREPARING -> context.getString(R.string.chip_preparing)
                        else -> "$percent%"
                    },
                )
            },
        )
        if (phase == PrintPhase.PAUSED) {
            b.addAction(0, context.getString(R.string.resume), ActionReceiver.intent(context, ActionReceiver.ACTION_RESUME, p.id))
        } else if (phase == PrintPhase.PRINTING) {
            b.addAction(0, context.getString(R.string.pause), ActionReceiver.intent(context, ActionReceiver.ACTION_PAUSE, p.id))
        }
        // Stopping can't be undone, so it opens the app and asks first.
        b.addAction(0, context.getString(R.string.stop), openPrinter(context, p.id, confirmStop = true))
        val n = b.build()
        return if (Build.VERSION.SDK_INT >= 36) withProgressStyle(context, n, phase, percent) else n
    }

    /**
     * Android 16's progress style, the look of a Live Update: a bar coloured by state (amber while
     * paused) with the printer icon riding along it. Everything else is kept from [n].
     */
    @RequiresApi(36)
    private fun withProgressStyle(context: Context, n: android.app.Notification, phase: PrintPhase, percent: Int): android.app.Notification {
        val color = if (phase == PrintPhase.PAUSED) PAUSED_COLOR else PROGRESS_COLOR
        val style = android.app.Notification.ProgressStyle()
            .setStyledByProgress(true)
            .setProgressSegments(listOf(android.app.Notification.ProgressStyle.Segment(100).setColor(color)))
            .setProgress(percent)
            .setProgressIndeterminate(phase == PrintPhase.PREPARING)
            .setProgressTrackerIcon(Icon.createWithResource(context, R.drawable.ic_notification))
        return android.app.Notification.Builder.recoverBuilder(context, n).setStyle(style).build()
    }

    /** Whether Android shows the progress notification as a Live Update (Android 16 and later). */
    fun liveUpdatesAllowed(context: Context): Boolean? {
        if (Build.VERSION.SDK_INT < 36) return null
        return context.getSystemService(NotificationManager::class.java)?.canPostPromotedNotifications()
    }

    /** The first layers of a print are down: a camera picture to check it started well, with pause and stop. */
    fun firstLayer(context: Context, printerId: Int, printerName: String, job: String?, picture: Bitmap?) {
        if (!canPost(context)) return
        val text = job?.takeIf { it.isNotBlank() }?.let { context.getString(R.string.notif_first_layer_body, it) }
            ?: context.getString(R.string.notif_first_layer_body_unnamed)
        val b = NotificationCompat.Builder(context, CHANNEL_EVENTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_first_layer, printerName))
            .setContentText(text)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(openPrinter(context, printerId))
            .addAction(0, context.getString(R.string.pause), ActionReceiver.intent(context, ActionReceiver.ACTION_PAUSE, printerId))
            .addAction(0, context.getString(R.string.stop), openPrinter(context, printerId, confirmStop = true))
        if (picture != null) {
            b.setLargeIcon(picture)
            b.setStyle(NotificationCompat.BigPictureStyle().bigPicture(picture).bigLargeIcon(null as Bitmap?).setSummaryText(text))
        } else {
            b.setStyle(NotificationCompat.BigTextStyle().bigText(text))
        }
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(firstLayerId(printerId), b.build())
    }

    /** Taken down once the print ends, so an old picture doesn't linger. */
    fun cancelFirstLayer(context: Context, printerId: Int) {
        NotificationManagerCompat.from(context).cancel(firstLayerId(printerId))
    }

    private fun firstLayerId(printerId: Int) = 30_000 + printerId

    /** A quiet placeholder while the progress service starts up or waits for a print. */
    fun serviceIdle(context: Context): android.app.Notification =
        NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_watching))
            .setOngoing(true)
            .setSilent(true)
            .build()

    /** Something happened to a print: finished, failed, paused, an error, … */
    fun event(context: Context, printerId: Int, printerName: String, event: PrintEvent, detail: String?) {
        if (!canPost(context)) return
        val title = context.getString(eventTitle(event), printerName)
        val problem = event != PrintEvent.FINISHED
        val n = NotificationCompat.Builder(context, if (problem) CHANNEL_PROBLEMS else CHANNEL_EVENTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setAutoCancel(true)
            .setCategory(if (problem) NotificationCompat.CATEGORY_ERROR else NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(openPrinter(context, printerId))
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(eventId(printerId, event), n)
    }

    /** A message from Bambuddy that PrintCtrl has no special handling for (its test message, for one). */
    fun plain(context: Context, printerId: Int, title: String, text: String) {
        if (!canPost(context)) return
        val n = NotificationCompat.Builder(context, CHANNEL_EVENTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openPrinter(context, printerId))
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify((title + text).hashCode(), n)
    }

    /** A spool dropped below the low-stock threshold. Tapping it opens the spool. */
    fun lowStock(context: Context, spoolId: Int, name: String, material: String?, leftGrams: Int, threshold: Int) {
        if (!canPost(context)) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_SPOOL, spoolId)
        }
        val n = NotificationCompat.Builder(context, CHANNEL_STOCK)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_low_stock, listOfNotNull(name, material?.takeIf { !name.contains(it, ignoreCase = true) }).joinToString(" ")))
            .setContentText(context.getString(R.string.notif_low_stock_body, leftGrams, threshold))
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(context, 40_000 + spoolId, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(40_000 + spoolId, n)
    }

    /** Several spools dropped below the threshold at once. */
    fun lowStockSummary(context: Context, count: Int, threshold: Int) {
        if (!canPost(context)) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_SPOOLS, true)
        }
        val n = NotificationCompat.Builder(context, CHANNEL_STOCK)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.resources.getQuantityString(R.plurals.notif_low_stock_many, count, count))
            .setContentText(context.getString(R.string.notif_low_stock_many_body, threshold))
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(context, 39_999, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(39_999, n)
    }

    private fun eventTitle(event: PrintEvent): Int = when (event) {
        PrintEvent.FINISHED -> R.string.notif_finished
        PrintEvent.FAILED -> R.string.notif_failed
        PrintEvent.PAUSED -> R.string.notif_paused
        PrintEvent.HMS -> R.string.notif_hms
        PrintEvent.RUNOUT -> R.string.notif_runout
        PrintEvent.AMS -> R.string.notif_ams
        PrintEvent.AI -> R.string.notif_ai
    }
}
