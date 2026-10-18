package com.vknxworks.printctrl.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
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

    /** Extras on the intent that opens the app from a notification. */
    const val EXTRA_OPEN_PRINTER = "open_printer"
    const val EXTRA_CONFIRM_STOP = "confirm_stop"

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
        if (phase == PrintPhase.PAUSED) {
            b.addAction(0, context.getString(R.string.resume), ActionReceiver.intent(context, ActionReceiver.ACTION_RESUME, p.id))
        } else if (phase == PrintPhase.PRINTING) {
            b.addAction(0, context.getString(R.string.pause), ActionReceiver.intent(context, ActionReceiver.ACTION_PAUSE, p.id))
        }
        // Stopping can't be undone, so it opens the app and asks first.
        b.addAction(0, context.getString(R.string.stop), openPrinter(context, p.id, confirmStop = true))
        return b.build()
    }

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
