package com.vknxworks.printctrl.ui.settings

import com.vknxworks.printctrl.ui.lock.AppLock
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.printctrl.BuildConfig
import com.vknxworks.printctrl.R
import com.vknxworks.printctrl.container
import com.vknxworks.printctrl.data.servers.ConnState
import com.vknxworks.printctrl.data.servers.ServerConfig
import com.vknxworks.printctrl.data.servers.ServerKind
import com.vknxworks.printctrl.ui.components.AddressPickerSheet
import com.vknxworks.printctrl.ui.components.Group
import com.vknxworks.printctrl.ui.components.GroupRow
import com.vknxworks.printctrl.ui.components.ScreenTopBar
import com.vknxworks.printctrl.ui.components.addressKind
import com.vknxworks.printctrl.ui.components.serverName
import com.vknxworks.printctrl.ui.nav.LocalNavigator
import com.vknxworks.printctrl.ui.nav.ServerSetupRoute
import com.vknxworks.printctrl.ui.nav.SettingsRoute
import com.vknxworks.printctrl.ui.components.readableWidth

private const val SPONSOR_URL = "https://github.com/sponsors/VoidedKN0X"
private const val REPO_URL = "https://github.com/VoidedKN0X/PrintCtrl"
private const val ISSUES_URL = "https://github.com/VoidedKN0X/PrintCtrl/issues"
private const val LICENCE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"

/** Opened from the profile button: the servers PrintCtrl talks to, settings, and about. */
@Composable
fun AccountScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val setup by c.servers.setup.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf<ServerKind?>(null) }
    fun open(url: String) {
        runCatching { AppLock.expectReturn(); context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar(stringResource(R.string.account)) },
    ) { padding ->
        Column(Modifier.padding(padding).readableWidth().fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Group(stringResource(R.string.servers), footer = stringResource(R.string.servers_footer)) {
                ServerRow(ServerKind.BAMBUDDY, setup?.bambuddy, Icons.Filled.Print) { nav.navigate(ServerSetupRoute(ServerKind.BAMBUDDY.name)) }
                ServerRow(ServerKind.SPOOLMAN, setup?.spoolman, Icons.Filled.Inventory2) { nav.navigate(ServerSetupRoute(ServerKind.SPOOLMAN.name)) }
            }
            val withTwo = ServerKind.entries.filter { (setup?.get(it)?.addresses?.size ?: 0) > 1 }
            if (withTwo.isNotEmpty()) {
                Group(stringResource(R.string.address_title), footer = stringResource(R.string.address_footer)) {
                    withTwo.forEach { kind ->
                        val cfg = setup?.get(kind) ?: return@forEach
                        GroupRow(
                            serverName(kind),
                            stringResource(R.string.address_option, addressKind(cfg.activeIndex), cfg.activeAddress().orEmpty()),
                            icon = Icons.Filled.Dns,
                            showChevron = true,
                            onClick = { picking = kind },
                        )
                    }
                }
            }
            Group {
                GroupRow(stringResource(R.string.settings), icon = Icons.Filled.Settings, showChevron = true, onClick = { nav.navigate(SettingsRoute) })
            }
            Group(stringResource(R.string.group_about)) {
                GroupRow(
                    stringResource(R.string.support_title),
                    stringResource(R.string.support_subtitle),
                    icon = Icons.Filled.Favorite,
                    showChevron = true,
                    onClick = { open(SPONSOR_URL) },
                )
                GroupRow(
                    stringResource(R.string.bugs_title),
                    stringResource(R.string.bugs_subtitle),
                    icon = Icons.Filled.BugReport,
                    showChevron = true,
                    onClick = { open(ISSUES_URL) },
                )
                GroupRow(
                    stringResource(R.string.source_title),
                    stringResource(R.string.source_subtitle),
                    icon = Icons.Filled.Code,
                    showChevron = true,
                    onClick = { open(REPO_URL) },
                )
                GroupRow(
                    stringResource(R.string.licence_title),
                    stringResource(R.string.licence_subtitle),
                    icon = Icons.Filled.Gavel,
                    showChevron = true,
                    onClick = { open(LICENCE_URL) },
                )
            }
            picking?.let { kind ->
                setup?.get(kind)?.let { cfg -> AddressPickerSheet(kind, cfg, onDismiss = { picking = null }) }
            }
            Text(
                stringResource(R.string.about_footer, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ServerRow(kind: ServerKind, config: ServerConfig?, icon: ImageVector, onClick: () -> Unit) {
    val c = LocalContext.current.container
    val state by c.connections.state(kind).collectAsStateWithLifecycle()
    val name = stringResource(if (kind == ServerKind.BAMBUDDY) R.string.bambuddy else R.string.spoolman)
    if (config == null) {
        GroupRow(
            stringResource(R.string.server_add, name),
            stringResource(if (kind == ServerKind.BAMBUDDY) R.string.welcome_bambuddy else R.string.welcome_spoolman),
            icon = Icons.Filled.Add,
            showChevron = true,
            onClick = onClick,
        )
        return
    }
    val subtitle = when (val s = state) {
        is ConnState.Ready -> stringResource(R.string.server_using, s.conn.baseUrl.toString().trimEnd('/')) +
            // The API key or proxy login travels unencrypted: say so where the server is shown.
            (if (s.conn.baseUrl.isHttps) "" else "  ·  " + stringResource(R.string.server_not_encrypted))
        ConnState.NotSet -> null
    }
    GroupRow(
        name,
        subtitle,
        icon = icon,
        showChevron = true,
        onClick = onClick,
    )
}
