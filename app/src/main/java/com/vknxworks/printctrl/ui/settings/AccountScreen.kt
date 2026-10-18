package com.vknxworks.printctrl.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.vknxworks.printctrl.ui.components.Group
import com.vknxworks.printctrl.ui.components.GroupRow
import com.vknxworks.printctrl.ui.components.ScreenTopBar
import com.vknxworks.printctrl.ui.components.friendlyMessage
import com.vknxworks.printctrl.ui.nav.LocalNavigator
import com.vknxworks.printctrl.ui.nav.ServerSetupRoute
import com.vknxworks.printctrl.ui.nav.SettingsRoute

/** Opened from the profile button: the servers PrintCtrl talks to, settings, and about. */
@Composable
fun AccountScreen() {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val setup by c.servers.setup.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar(stringResource(R.string.account)) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Group(stringResource(R.string.servers), footer = stringResource(R.string.servers_footer)) {
                ServerRow(ServerKind.BAMBUDDY, setup?.bambuddy, Icons.Filled.Print) { nav.navigate(ServerSetupRoute(ServerKind.BAMBUDDY.name)) }
                ServerRow(ServerKind.SPOOLMAN, setup?.spoolman, Icons.Filled.Inventory2) { nav.navigate(ServerSetupRoute(ServerKind.SPOOLMAN.name)) }
            }
            Group {
                GroupRow(stringResource(R.string.settings), icon = Icons.Filled.Settings, showChevron = true, onClick = { nav.navigate(SettingsRoute) })
            }
            Text(
                stringResource(R.string.footer_version, BuildConfig.VERSION_NAME),
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
    val context = LocalContext.current
    val c = context.container
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
        is ConnState.Ready -> stringResource(R.string.server_using, s.conn.baseUrl.toString().trimEnd('/'))
        is ConnState.Connecting -> stringResource(R.string.server_connecting)
        is ConnState.Unreachable -> stringResource(R.string.server_unreachable, s.error.friendlyMessage(context))
        ConnState.NotSet -> null
    }
    GroupRow(
        name,
        subtitle,
        icon = icon,
        showChevron = state !is ConnState.Connecting,
        onClick = onClick,
        trailing = if (state is ConnState.Connecting) { { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) } } else null,
    )
}
