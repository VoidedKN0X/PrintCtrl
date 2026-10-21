package com.vknxworks.printctrl.ui.settings

import com.vknxworks.printctrl.ui.lock.AppLock
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.vknxworks.printctrl.BuildConfig
import com.vknxworks.printctrl.R
import com.vknxworks.printctrl.container
import com.vknxworks.printctrl.data.servers.ServerKind
import com.vknxworks.printctrl.data.settings.BackupFormatException
import com.vknxworks.printctrl.data.settings.SettingsBackup
import com.vknxworks.printctrl.data.settings.SettingsBackups
import com.vknxworks.printctrl.ui.components.ConfirmDialog
import com.vknxworks.printctrl.ui.components.Group
import com.vknxworks.printctrl.ui.components.GroupRow
import com.vknxworks.printctrl.ui.nav.LocalNavigator
import com.vknxworks.printctrl.ui.nav.ServerSetupRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate
import java.time.OffsetDateTime
import com.vknxworks.printctrl.data.net.UserMessageException

/**
 * Back up and restore settings: a JSON file in a place the user picks (Downloads, a cloud folder…),
 * with the app settings and server addresses but no API keys, passwords or proxy logins.
 */
@Composable
fun BackupGroup() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<SettingsBackup?>(null) }
    var needsSecrets by remember { mutableStateOf<List<ServerKind>?>(null) }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = runCatching {
                val text = SettingsBackups.export(c.settings.settings.value, c.servers.setup.value, BuildConfig.VERSION_NAME, OffsetDateTime.now().toString())
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } ?: throw UserMessageException(R.string.err_write_file)
                }
            }.isSuccess
            Toast.makeText(context, if (ok) R.string.backup_saved else R.string.backup_save_failed, Toast.LENGTH_LONG).show()
        }
    }

    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = runCatching {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        // A settings file is a few kilobytes; anything far bigger isn't one.
                        val out = java.io.ByteArrayOutputStream()
                        val buf = ByteArray(8192)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            if (out.size() > MAX_BYTES) throw BackupFormatException(newer = false)
                        }
                        out.toString(Charsets.UTF_8.name())
                    } ?: throw UserMessageException(R.string.err_read_file)
                }
                SettingsBackups.parse(text)
            }
            result.onSuccess { pending = it }.onFailure { e ->
                val msg = if (e is BackupFormatException && e.newer) R.string.backup_too_new else R.string.backup_not_valid
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    Group(stringResource(R.string.backup_title), footer = stringResource(R.string.backup_footer)) {
        GroupRow(
            stringResource(R.string.backup_save),
            stringResource(R.string.backup_save_sub),
            Icons.Outlined.FileDownload,
            onClick = { AppLock.expectReturn(); save.launch("printctrl-settings-${LocalDate.now()}.json") },
        )
        GroupRow(
            stringResource(R.string.backup_restore),
            stringResource(R.string.backup_restore_sub),
            Icons.Outlined.FileUpload,
            onClick = { AppLock.expectReturn(); open.launch(arrayOf("application/json", "text/*", "application/octet-stream")) },
        )
    }

    pending?.let { b ->
        val servers = listOfNotNull(
            b.bambuddy?.let { "Bambuddy: " + it.addresses.joinToString(", ") },
            b.spoolman?.let { "Spoolman: " + it.addresses.joinToString(", ") },
        )
        ConfirmDialog(
            stringResource(R.string.backup_restore_title),
            stringResource(
                R.string.backup_restore_body,
                b.createdAt?.take(10) ?: "?",
                servers.joinToString("\n").ifEmpty { stringResource(R.string.backup_no_servers) },
            ),
            stringResource(R.string.backup_restore_confirm),
            onDismiss = { pending = null },
            destructive = false,
        ) {
            pending = null
            scope.launch {
                runCatching { SettingsBackups.restore(b, c.settings, c.servers) }
                    .onSuccess { missing ->
                        if (missing.isEmpty()) Toast.makeText(context, R.string.backup_restored, Toast.LENGTH_LONG).show()
                        else needsSecrets = missing
                    }
                    .onFailure { Toast.makeText(context, R.string.backup_restore_failed, Toast.LENGTH_LONG).show() }
            }
        }
    }

    needsSecrets?.let { kinds ->
        AlertDialog(
            onDismissRequest = { needsSecrets = null },
            title = { Text(stringResource(R.string.backup_restored)) },
            text = { Text(stringResource(R.string.backup_enter_secrets)) },
            confirmButton = {
                TextButton(onClick = {
                    needsSecrets = null
                    nav.navigate(ServerSetupRoute(kinds.first().name))
                }) {
                    Text(stringResource(if (kinds.first() == ServerKind.BAMBUDDY) R.string.backup_open_bambuddy else R.string.backup_open_spoolman))
                }
            },
            dismissButton = { TextButton(onClick = { needsSecrets = null }) { Text(stringResource(R.string.backup_later)) } },
        )
    }
}

private const val MAX_BYTES = 256 * 1024
