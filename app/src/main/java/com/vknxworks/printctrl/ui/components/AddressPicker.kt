package com.vknxworks.printctrl.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.printctrl.R
import com.vknxworks.printctrl.container
import com.vknxworks.printctrl.data.servers.ServerConfig
import com.vknxworks.printctrl.data.servers.ServerKind
import com.vknxworks.printctrl.ui.theme.Gutter
import kotlinx.coroutines.launch

/** "First address" or "Second address", as on the setup screen. */
@Composable
fun addressKind(index: Int): String = stringResource(if (index == 0) R.string.address_first else R.string.address_second)

@Composable
fun serverName(kind: ServerKind): String = stringResource(if (kind == ServerKind.BAMBUDDY) R.string.bambuddy else R.string.spoolman)

/**
 * Lets the user pick which saved address PrintCtrl uses for [kind]. The choice is kept until the
 * user changes it again; PrintCtrl never switches on its own.
 */
@Composable
fun AddressPickerSheet(kind: ServerKind, config: ServerConfig, onDismiss: () -> Unit) {
    val c = LocalContext.current.container
    val options = config.addresses.mapIndexed { i, a -> i to stringResource(R.string.address_option, addressKind(i), a) }
    ChoiceSheet(
        stringResource(R.string.address_pick_title, serverName(kind)),
        options,
        config.activeIndex,
        onDismiss = onDismiss,
    ) { index ->
        onDismiss()
        c.appScope.launch { c.connections.useAddress(kind, index) }
    }
}

/**
 * Shown when [kind] can't be reached and it has a second address: says which address is in use and
 * offers the other one. Only switches when the user taps the button.
 */
@Composable
fun SwitchAddressBar(kind: ServerKind, modifier: Modifier = Modifier) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val setup by c.servers.setup.collectAsStateWithLifecycle()
    val config = setup?.get(kind) ?: return
    if (config.addresses.size < 2) return
    val current = config.activeIndex
    val other = if (current == 0) 1 else 0
    Row(
        modifier.padding(horizontal = Gutter).fillMaxWidth().clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.address_unreachable, serverName(kind), config.addresses[current]),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { scope.launch { c.connections.useAddress(kind, other) } }) {
            Text(stringResource(if (other == 0) R.string.address_use_first else R.string.address_use_second))
        }
    }
}
