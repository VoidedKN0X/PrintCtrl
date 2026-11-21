package com.vknxworks.printctrl.ui.spools

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.printctrl.R
import com.vknxworks.printctrl.container
import com.vknxworks.printctrl.data.bambuddy.BambuddyClient
import com.vknxworks.printctrl.data.bambuddy.inventorySpoolUsage
import com.vknxworks.printctrl.data.bambuddy.spoolmanSpoolUsage
import com.vknxworks.printctrl.data.net.ApiException
import com.vknxworks.printctrl.ui.archive.Outcome
import com.vknxworks.printctrl.ui.archive.formatDay
import com.vknxworks.printctrl.ui.components.Group
import com.vknxworks.printctrl.ui.components.GroupRow
import com.vknxworks.printctrl.ui.components.Load
import com.vknxworks.printctrl.ui.components.formatMoney
import com.vknxworks.printctrl.ui.components.friendlyMessage
import com.vknxworks.printctrl.ui.components.rememberLoader
import com.vknxworks.printctrl.ui.theme.CtrlType
import kotlin.math.roundToInt

/**
 * What each print took from a spool, newest first, as Bambuddy recorded it. Read-only: PrintCtrl never
 * writes usage itself (Bambuddy does, and syncs it to Spoolman), so nothing is counted twice.
 *
 * [spoolman]: the id is a Spoolman spool's, and the list is what Bambuddy charged to it.
 */
@Composable
fun SpoolUsageGroup(api: BambuddyClient, spoolId: Int, spoolman: Boolean) {
    val context = LocalContext.current
    val c = context.container
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val loader = rememberLoader("spool-usage:${api.conn.key}:$spoolman:$spoolId") {
        val list = if (spoolman) api.spoolmanSpoolUsage(spoolId) else api.inventorySpoolUsage(spoolId)
        list to api.currency()
    }
    when (val s = loader.state) {
        is Load.Loading -> Group(stringResource(R.string.usage_title)) { GroupRow(stringResource(R.string.usage_loading)) }
        is Load.Err -> {
            // A Bambuddy without this list (404): nothing to show, and no error for something it can't do.
            val missing = (s.error as? ApiException)?.code == 404
            if (!missing) Group(stringResource(R.string.usage_title)) {
                GroupRow(stringResource(R.string.usage_failed), s.error.friendlyMessage(context), onClick = { loader.reload() })
            }
        }
        is Load.Ok -> {
            val (list, serverCurrency) = s.value
            val currency = settings.currency.ifEmpty { null } ?: serverCurrency
            Group(stringResource(R.string.usage_title), footer = stringResource(R.string.usage_footer)) {
                if (list.isEmpty()) GroupRow(stringResource(R.string.usage_empty))
                list.forEach { u ->
                    GroupRow(
                        u.printName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.usage_unnamed),
                        listOfNotNull(
                            u.createdAt?.let { formatDay(it) },
                            u.status.takeIf { it.isNotBlank() }?.let { stringResource(Outcome.of(it).label) },
                            u.cost?.takeIf { it > 0 }?.let { formatMoney(it, currency) },
                        ).joinToString("  ·  "),
                    ) {
                        Text(stringResource(R.string.grams_value, u.weightUsed.roundToInt()), style = CtrlType.Data, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}
