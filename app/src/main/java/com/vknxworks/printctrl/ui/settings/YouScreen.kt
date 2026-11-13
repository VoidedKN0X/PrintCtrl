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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import com.vknxworks.printctrl.ui.components.PillButton
import com.vknxworks.printctrl.ui.theme.CtrlColors
import com.vknxworks.printctrl.ui.theme.CtrlType
import com.vknxworks.printctrl.ui.theme.Gutter
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

/** The You page, opened from the profile picture: the servers PrintCtrl talks to, Settings, and About. */
@Composable
fun YouScreen() {
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
        topBar = { ScreenTopBar(stringResource(R.string.you_title)) },
    ) { padding ->
        Column(Modifier.padding(padding).readableWidth().fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            // Profile header: accent square with the app's initials, the name, and the server address in mono.
            Row(
                Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 8.dp, bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(68.dp).clip(RoundedCornerShape(22.dp)).background(CtrlColors.Accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("PC", style = MaterialTheme.typography.headlineSmall, color = CtrlColors.OnAccent)
                }
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                    val set = ServerKind.entries.filter { setup?.get(it) != null }
                    val address = set.firstNotNullOfOrNull { setup?.get(it)?.activeAddress() }
                    Text(
                        when {
                            set.isEmpty() -> stringResource(R.string.you_no_servers)
                            address != null -> address
                            else -> set.map { serverName(it) }.joinToString("  ·  ")
                        },
                        style = CtrlType.Data,
                        color = CtrlColors.Muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Group(stringResource(R.string.servers), footer = stringResource(R.string.servers_footer)) {
                ServerRow(ServerKind.BAMBUDDY, setup?.bambuddy, Icons.Outlined.Print) { nav.navigate(ServerSetupRoute(ServerKind.BAMBUDDY.name)) }
                ServerRow(ServerKind.SPOOLMAN, setup?.spoolman, Icons.Outlined.Inventory2) { nav.navigate(ServerSetupRoute(ServerKind.SPOOLMAN.name)) }
            }
            val withTwo = ServerKind.entries.filter { (setup?.get(it)?.addresses?.size ?: 0) > 1 }
            if (withTwo.isNotEmpty()) {
                Group(stringResource(R.string.address_title), footer = stringResource(R.string.address_footer)) {
                    withTwo.forEach { kind ->
                        val cfg = setup?.get(kind) ?: return@forEach
                        GroupRow(
                            serverName(kind),
                            stringResource(R.string.address_option, addressKind(cfg.activeIndex), cfg.activeAddress().orEmpty()),
                            icon = Icons.Outlined.Dns,
                            showChevron = true,
                            onClick = { picking = kind },
                        )
                    }
                }
            }
            Group {
                GroupRow(stringResource(R.string.settings), icon = Icons.Outlined.Tune, showChevron = true, onClick = { nav.navigate(SettingsRoute) })
            }
            Group(stringResource(R.string.group_about)) {
                GroupRow(
                    stringResource(R.string.bugs_title),
                    stringResource(R.string.bugs_subtitle),
                    icon = Icons.Outlined.BugReport,
                    showChevron = true,
                    onClick = { open(ISSUES_URL) },
                )
                GroupRow(
                    stringResource(R.string.source_title),
                    stringResource(R.string.source_subtitle),
                    icon = Icons.Outlined.Code,
                    showChevron = true,
                    onClick = { open(REPO_URL) },
                )
                GroupRow(
                    stringResource(R.string.licence_title),
                    stringResource(R.string.licence_subtitle),
                    icon = Icons.Outlined.Gavel,
                    showChevron = true,
                    onClick = { open(LICENCE_URL) },
                )
            }
            picking?.let { kind ->
                setup?.get(kind)?.let { cfg -> AddressPickerSheet(kind, cfg, onDismiss = { picking = null }) }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(
                    stringResource(R.string.support_title),
                    onClick = { open(SPONSOR_URL) },
                    icon = Icons.Outlined.FavoriteBorder,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.support_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.about_footer, BuildConfig.VERSION_NAME).uppercase(),
                    style = CtrlType.Label,
                    color = CtrlColors.Muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "GOOGLE-FREE · TELEMETRY-FREE · ARM64",
                    style = CtrlType.Label,
                    color = CtrlColors.Muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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
            icon = Icons.Outlined.Add,
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
