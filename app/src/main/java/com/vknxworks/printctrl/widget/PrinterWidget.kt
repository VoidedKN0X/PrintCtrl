package com.vknxworks.printctrl.widget

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.vknxworks.printctrl.MainActivity
import com.vknxworks.printctrl.R
import com.vknxworks.printctrl.data.bambuddy.PrintPhase
import com.vknxworks.printctrl.notify.Notifier
import com.vknxworks.printctrl.ui.components.formatClock
import com.vknxworks.printctrl.ui.components.phaseLabel

/** Home-screen widget: each printer's state, progress, when it'll be done and the job's picture. */
class PrinterWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(180.dp, 110.dp), DpSize(280.dp, 110.dp), DpSize(280.dp, 220.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val printers = WidgetState.read(context)
        provideContent {
            GlanceTheme {
                WidgetBody(printers)
            }
        }
    }
}

class PrinterWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PrinterWidget()
}

private val Ember = Color(0xFFFFB27A)
private val Surface = Color(0xFF1C1F25)
private val OnSurface = Color(0xFFECEEF2)
private val Muted = Color(0xFF9AA1AD)

@Composable
private fun WidgetBody(printers: List<PrinterSnapshot>) {
    val context = LocalContext.current
    val size = LocalSize.current
    val open = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
    Box(
        GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(Surface).padding(14.dp).clickable(actionStartActivity(open)),
    ) {
        if (printers.isEmpty()) {
            Column {
                Text(context.getString(R.string.app_name), style = TextStyle(color = ColorProvider(OnSurface), fontSize = 16.sp, fontWeight = FontWeight.Bold))
                Text(context.getString(R.string.widget_empty), style = TextStyle(color = ColorProvider(Muted), fontSize = 12.sp))
            }
        } else {
            val shown = if (size.height >= 200.dp) printers.take(2) else printers.take(1)
            Column(GlanceModifier.fillMaxSize()) {
                shown.forEachIndexed { i, p ->
                    if (i > 0) Spacer(GlanceModifier.height(12.dp))
                    PrinterBlock(p, showPicture = size.width >= 260.dp)
                }
            }
        }
    }
}

@Composable
private fun PrinterBlock(p: PrinterSnapshot, showPicture: Boolean) {
    val context = LocalContext.current
    val open = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(Notifier.EXTRA_OPEN_PRINTER, p.id)
    }
    Row(GlanceModifier.fillMaxWidth().clickable(actionStartActivity(open)), verticalAlignment = Alignment.CenterVertically) {
        val bitmap = if (showPicture) p.cover?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() } else null
        if (bitmap != null) {
            Image(ImageProvider(bitmap), contentDescription = null, contentScale = ContentScale.Crop, modifier = GlanceModifier.size(72.dp).cornerRadius(14.dp))
            Spacer(GlanceModifier.width(12.dp))
        } else if (showPicture) {
            Image(
                ImageProvider(R.drawable.ic_notification),
                contentDescription = null,
                colorFilter = ColorFilter.tint(ColorProvider(Muted)),
                modifier = GlanceModifier.size(40.dp),
            )
            Spacer(GlanceModifier.width(12.dp))
        }
        Column(GlanceModifier.defaultWeight()) {
            Text(p.name, style = TextStyle(color = ColorProvider(OnSurface), fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            val phase = p.printPhase
            val status = buildString {
                append(context.getString(phaseLabel(phase)))
                if (phase.isActive && p.progress != null) append("  ·  ${p.progress}%")
            }
            Text(status, style = TextStyle(color = ColorProvider(if (phase.isActive) Ember else Muted), fontSize = 12.sp), maxLines = 1)
            p.job?.takeIf { phase != PrintPhase.IDLE }?.let {
                Text(it, style = TextStyle(color = ColorProvider(Muted), fontSize = 12.sp), maxLines = 1)
            }
            if (phase.isActive) {
                Spacer(GlanceModifier.height(6.dp))
                LinearProgressIndicator(
                    progress = (p.progress ?: 0) / 100f,
                    modifier = GlanceModifier.fillMaxWidth().height(5.dp),
                    color = ColorProvider(Ember),
                    backgroundColor = ColorProvider(Color(0xFF2B3038)),
                )
                p.etaMs?.let { eta ->
                    Spacer(GlanceModifier.height(4.dp))
                    Text(context.getString(R.string.eta_value, formatClock(context, eta)), style = TextStyle(color = ColorProvider(Muted), fontSize = 11.sp))
                }
            }
        }
    }
}
