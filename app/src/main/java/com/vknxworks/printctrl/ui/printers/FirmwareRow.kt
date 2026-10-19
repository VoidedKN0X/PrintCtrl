package com.vknxworks.printctrl.ui.printers

import android.text.Html
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vknxworks.printctrl.R
import com.vknxworks.printctrl.data.bambuddy.BambuddyClient
import com.vknxworks.printctrl.data.bambuddy.firmware
import com.vknxworks.printctrl.ui.components.GroupRow
import com.vknxworks.printctrl.ui.components.rememberLoader

/**
 * The printer's firmware version, and when Bambu Lab has a newer one (as Bambuddy checks it), a notice
 * with its release notes. Updating is done on the printer or in Bambu Handy; PrintCtrl only tells you.
 */
@Composable
fun FirmwareRow(api: BambuddyClient?, printerId: Int, current: String?) {
    val info = if (api == null) null else rememberLoader("firmware:${api.conn.key}:$printerId") { runCatching { api.firmware(printerId) }.getOrNull() }.value
    val version = info?.currentVersion ?: current
    val latest = info?.latestVersion
    var open by remember { mutableStateOf(false) }
    if (info?.updateAvailable == true && latest != null) {
        GroupRow(
            stringResource(R.string.firmware),
            stringResource(R.string.firmware_update_available, version ?: "?", latest),
            Icons.Outlined.SystemUpdate,
            iconTint = MaterialTheme.colorScheme.tertiary,
            showChevron = true,
            onClick = { open = true },
        )
    } else if (version != null) {
        GroupRow(stringResource(R.string.firmware), if (latest != null) stringResource(R.string.firmware_up_to_date, version) else version)
    }
    if (open && info != null) {
        val notes = info.releaseNotes ?: info.availableVersions.firstOrNull { it.version == latest }?.releaseNotes
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.firmware_update_title, latest.orEmpty())) },
            text = {
                Text(
                    listOfNotNull(
                        stringResource(R.string.firmware_update_how),
                        notes?.takeIf { it.isNotBlank() }?.let { Html.fromHtml(it, Html.FROM_HTML_MODE_COMPACT).toString().trim() },
                    ).joinToString("\n\n"),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.close)) } },
        )
    }
}
