package com.vknxworks.printctrl.ui.setup

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.vknxworks.printctrl.AppContainer
import com.vknxworks.printctrl.R
import com.vknxworks.printctrl.container
import com.vknxworks.printctrl.data.auth.KeystoreCrypto
import com.vknxworks.printctrl.data.bambuddy.BambuddyClient
import com.vknxworks.printctrl.data.net.CleartextPolicy
import com.vknxworks.printctrl.data.net.Tls
import com.vknxworks.printctrl.data.net.origin
import com.vknxworks.printctrl.data.servers.ServerClients
import com.vknxworks.printctrl.data.servers.ServerConfig
import com.vknxworks.printctrl.data.servers.ServerConnection
import com.vknxworks.printctrl.data.servers.ServerKind
import com.vknxworks.printctrl.data.spoolman.SpoolmanClient
import com.vknxworks.printctrl.ui.components.ConfirmDialog
import com.vknxworks.printctrl.ui.components.PillButton
import com.vknxworks.printctrl.ui.components.friendlyMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import java.security.cert.X509Certificate

/** Logins are on in Bambuddy, so it needs an API key. */
private class NeedsKeyException : IOException("API key needed")

/** HTTPS didn't work for an address typed without http(s)://; plain HTTP may. */
private class OfferHttp(val index: Int, val httpUrl: String) : Exception()

/** The server's certificate isn't trusted by the phone; the user can pin it. */
private class OfferTrust(val origin: String, val host: String, val cert: X509Certificate) : Exception()

/**
 * Sets up (or edits) one server. Each address is tested before saving: the server must answer like
 * Bambuddy or Spoolman, and Bambuddy must accept the API key. A self-signed certificate can be
 * trusted after checking its fingerprint; that exact certificate is then pinned for that address.
 */
@Composable
fun ServerSetupScreen(kind: ServerKind, onDone: () -> Unit, onCancel: (() -> Unit)?) {
    val context = LocalContext.current
    val c = context.container
    val existing = c.servers.setup.value?.get(kind)
    // The API key can be shown in clear text here. MainActivity keeps every screen out of the
    // recent-apps preview, so nothing needs switching on and off here.
    val scope = rememberCoroutineScope()
    var address by rememberSaveable { mutableStateOf(existing?.addresses?.getOrNull(0).orEmpty()) }
    var second by rememberSaveable { mutableStateOf(existing?.addresses?.getOrNull(1).orEmpty()) }
    var apiKey by remember { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf(existing?.username.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var showSecret by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<String?>(null) }
    var pins by remember { mutableStateOf(existing?.pins.orEmpty()) }
    var offerHttp by remember { mutableStateOf<OfferHttp?>(null) }
    var offerTrust by remember { mutableStateOf<OfferTrust?>(null) }
    var partial by remember { mutableStateOf<Pair<ServerConfig, String>?>(null) }
    var confirmRemove by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }

    val isBambuddy = kind == ServerKind.BAMBUDDY
    val keySaved = existing?.apiKey != null
    val passwordSaved = existing?.password != null
    val canSubmit = address.isNotBlank() && !busy

    fun attempt() {
        busy = true
        error = null
        result = null
        scope.launch {
            try {
                val key = apiKey.trim().ifEmpty { if (keySaved) existing?.apiKeyPlain() else null }
                val user = username.trim().ifEmpty { null }
                val pass = password.ifEmpty { if (passwordSaved && user != null) existing?.passwordPlain() else null }
                val typed = listOf(address, second).map { it.trim() }.filter { it.isNotEmpty() }
                val urls = typed.map { normalize(it) ?: throw IOException(context.getString(R.string.setup_bad_address, it)) }
                val summaries = mutableListOf<String>()
                val failures = mutableListOf<String>()
                urls.forEachIndexed { i, url ->
                    try {
                        summaries += test(c, kind, url, key, user, pass, pins[url.origin()])
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        when {
                            // A certificate the phone doesn't trust: show it so the user can pin it.
                            url.isHttps && Tls.isCertificateError(e) && Tls.pinMismatchMessage(e) == null -> {
                                val cert = withContext(Dispatchers.IO) { Tls.fetchLeaf(url.host, url.port) }
                                throw OfferTrust(url.origin(), url.host, cert)
                            }
                            // Typed without a scheme and HTTPS isn't there: offer HTTP.
                            url.isHttps && !hasScheme(typed[i]) && e !is NeedsKeyException &&
                                e !is com.vknxworks.printctrl.data.net.ApiException -> {
                                throw OfferHttp(i, url.newBuilder().scheme("http").port(if (url.port == 443) 80 else url.port).build().toString())
                            }
                            else -> failures += "${url.host}: ${message(context, e)}"
                        }
                    }
                }
                val config = ServerConfig(
                    addresses = urls.map { it.toString().trimEnd('/') },
                    apiKey = key?.let { KeystoreCrypto.encrypt(it) },
                    username = user,
                    password = if (user != null) pass?.let { KeystoreCrypto.encrypt(it) } else null,
                    pins = pins.filterKeys { o -> urls.any { it.origin() == o } },
                )
                when {
                    // None answered: nothing to save.
                    summaries.isEmpty() -> error = failures.joinToString("\n")
                    // Some answered (e.g. the remote address isn't reachable from here): ask.
                    failures.isNotEmpty() -> partial = config to failures.joinToString("\n")
                    else -> {
                        result = summaries.first()
                        c.servers.save(kind, config)
                        onDone()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: OfferHttp) {
                offerHttp = e
            } catch (e: OfferTrust) {
                offerTrust = e
            } catch (e: Throwable) {
                error = message(context, e)
            } finally {
                busy = false
            }
        }
    }

    offerHttp?.let { offer ->
        ConfirmDialog(
            stringResource(R.string.http_title),
            stringResource(R.string.http_body, offer.httpUrl),
            stringResource(R.string.http_confirm),
            onDismiss = {
                offerHttp = null
                error = context.getString(R.string.http_declined)
            },
        ) {
            offerHttp = null
            CleartextPolicy.allowServer(offer.httpUrl)
            if (offer.index == 0) address = offer.httpUrl else second = offer.httpUrl
            attempt()
        }
    }

    offerTrust?.let { offer ->
        AlertDialog(
            onDismissRequest = { offerTrust = null },
            title = { Text(stringResource(R.string.trust_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.trust_body, offer.host))
                    Text(
                        Tls.fingerprint(offer.cert),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(10.dp),
                    )
                    Text(
                        stringResource(R.string.trust_subject, offer.cert.subjectX500Principal.name),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pins = pins + (offer.origin to Tls.fingerprint(offer.cert))
                    offerTrust = null
                    attempt()
                }) { Text(stringResource(R.string.trust_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    offerTrust = null
                    error = context.getString(R.string.err_certificate)
                }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    partial?.let { (config, failures) ->
        ConfirmDialog(
            stringResource(R.string.partial_title),
            stringResource(R.string.partial_body, failures),
            stringResource(R.string.partial_confirm),
            destructive = false,
            onDismiss = { partial = null },
        ) {
            partial = null
            scope.launch {
                c.servers.save(kind, config)
                onDone()
            }
        }
    }

    if (confirmRemove) {
        ConfirmDialog(
            stringResource(if (isBambuddy) R.string.remove_bambuddy_title else R.string.remove_spoolman_title),
            stringResource(R.string.remove_server_body),
            stringResource(R.string.remove),
            onDismiss = { confirmRemove = false },
        ) {
            confirmRemove = false
            scope.launch {
                c.servers.save(kind, null)
                // Pictures, printer and job names and the widget kept from this server go with it. Runs
                // in the app's scope: removing Bambuddy closes this screen straight away.
                c.appScope.launch { c.forgetServerData(kind) }
                onDone()
            }
        }
    }

    if (scanning) {
        BambuddyQrScanner(
            onCancel = { scanning = false },
            onResult = { url, key ->
                scanning = false
                address = url
                apiKey = key
                showSecret = false
                result = context.getString(R.string.setup_qr_filled)
            },
        )
        return
    }

    BackHandler(enabled = onCancel != null) { onCancel?.invoke() }

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding().imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.height(48.dp)) {
                if (onCancel != null) IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            }
            Text(stringResource(if (isBambuddy) R.string.bambuddy else R.string.spoolman), style = MaterialTheme.typography.displaySmall)
            Text(
                stringResource(if (isBambuddy) R.string.setup_bambuddy_intro else R.string.setup_spoolman_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            if (isBambuddy) {
                androidx.compose.material3.FilledTonalButton(onClick = { scanning = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.QrCodeScanner, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.setup_scan_qr))
                }
                Text(
                    stringResource(R.string.setup_scan_qr_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Field(
                address, { address = it },
                label = stringResource(R.string.setup_address),
                placeholder = stringResource(if (isBambuddy) R.string.setup_address_hint_bambuddy else R.string.setup_address_hint_spoolman),
                keyboard = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next, autoCorrectEnabled = false),
                enabled = !busy,
            )
            HttpWarning(address)
            Field(
                second, { second = it },
                label = stringResource(R.string.setup_second_address),
                placeholder = stringResource(R.string.setup_second_address_hint),
                keyboard = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next, autoCorrectEnabled = false),
                enabled = !busy,
            )
            HttpWarning(second)
            Text(
                stringResource(R.string.setup_second_address_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isBambuddy) {
                Field(
                    apiKey, { apiKey = it },
                    label = stringResource(R.string.setup_api_key),
                    placeholder = if (keySaved) stringResource(R.string.setup_secret_saved) else "bb_…",
                    visual = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                    enabled = !busy,
                    trailing = { SecretToggle(showSecret) { showSecret = !showSecret } },
                )
                Text(
                    stringResource(R.string.setup_api_key_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Field(
                    username, { username = it },
                    label = stringResource(R.string.setup_username),
                    keyboard = KeyboardOptions(imeAction = ImeAction.Next, capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                    enabled = !busy,
                )
                Field(
                    password, { password = it },
                    label = stringResource(R.string.setup_password),
                    placeholder = if (passwordSaved) stringResource(R.string.setup_secret_saved) else null,
                    visual = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    enabled = !busy,
                    trailing = { SecretToggle(showSecret) { showSecret = !showSecret } },
                )
                Text(
                    stringResource(R.string.setup_proxy_login_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            result?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.width(8.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (busy) {
                Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp)) }
            } else {
                PillButton(stringResource(R.string.setup_test_save), onClick = ::attempt, enabled = canSubmit, modifier = Modifier.fillMaxWidth())
            }
            if (existing != null && !busy) {
                TextButton(onClick = { confirmRemove = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.remove_server), color = MaterialTheme.colorScheme.error)
                }
            }
            Text(
                stringResource(R.string.setup_storage_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun hasScheme(text: String) = text.startsWith("http://", ignoreCase = true) || text.startsWith("https://", ignoreCase = true)

/** "printer.lan:8000" → https://printer.lan:8000/ ; keeps a path prefix the server lives under. */
private fun normalize(text: String): HttpUrl? {
    val t = text.trim().trimEnd('/')
    val withScheme = if (hasScheme(t)) t else "https://$t"
    val url = withScheme.toHttpUrlOrNull() ?: return null
    if (!url.isHttps) CleartextPolicy.allowServer(url.toString())
    return url.newBuilder().query(null).fragment(null).build()
}

private fun message(context: Context, e: Throwable): String =
    if (e is NeedsKeyException) context.getString(R.string.setup_needs_key) else e.friendlyMessage(context)

/** Talks to one address the way the app will; returns a short line about what answered. */
private suspend fun test(
    c: AppContainer,
    kind: ServerKind,
    url: HttpUrl,
    apiKey: String?,
    username: String?,
    password: String?,
    pin: String?,
): String {
    val client = ServerClients.build(c.http, kind, url, apiKey, username, password, pin)
    val conn = ServerConnection(kind, url, client)
    return when (kind) {
        ServerKind.BAMBUDDY -> {
            val api = BambuddyClient(conn)
            val version = api.version().version
            val auth = runCatching { api.authStatus() }.getOrNull()
            if (auth?.authEnabled == true && apiKey.isNullOrBlank()) throw NeedsKeyException()
            // Needs the key's "read status" permission; a wrong key fails here with 401.
            val printers = api.printers()
            c.context.resources.getQuantityString(R.plurals.setup_ok_bambuddy, printers.size, version, printers.size)
        }
        ServerKind.SPOOLMAN -> {
            val info = SpoolmanClient(conn).info()
            c.context.getString(R.string.setup_ok_spoolman, info.version)
        }
    }
}

@Composable
private fun HttpWarning(text: String) {
    if (!text.trim().startsWith("http://", ignoreCase = true)) return
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.LockOpen, null, tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(R.string.setup_http_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SecretToggle(shown: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(
            if (shown) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
            stringResource(if (shown) R.string.hide else R.string.show),
        )
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboard: KeyboardOptions = KeyboardOptions.Default,
    enabled: Boolean = true,
    visual: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    TextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = if (placeholder != null) { { Text(placeholder) } } else null,
        singleLine = true,
        enabled = enabled,
        keyboardOptions = keyboard,
        visualTransformation = visual,
        trailingIcon = trailing,
        shape = MaterialTheme.shapes.medium,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * Reads the QR code Bambuddy shows when an API key is created ("Scan with your mobile app"):
 * bambuddy://config?v=1&url=…&key=…
 */
@Composable
private fun BambuddyQrScanner(onCancel: () -> Unit, onResult: (String, String) -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED)
    }
    val ask = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted = it }
    androidx.compose.runtime.LaunchedEffect(Unit) { if (!granted) ask.launch(android.Manifest.permission.CAMERA) }
    var message by remember { mutableStateOf<String?>(null) }
    BackHandler { onCancel() }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            com.vknxworks.printctrl.ui.spools.QrScanner(Modifier.fillMaxSize()) { text ->
                val uri = android.net.Uri.parse(text.trim())
                val url = uri.takeIf { it.scheme == "bambuddy" && it.host == "config" }?.getQueryParameter("url")
                val key = uri.takeIf { it.scheme == "bambuddy" }?.getQueryParameter("key")
                if (url != null && key != null) onResult(url.trimEnd('/'), key)
                else message = context.getString(R.string.setup_qr_wrong)
            }
        }
        Row(Modifier.fillMaxWidth().systemBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = Color.White) }
            Text(stringResource(R.string.setup_scan_qr), color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
        Text(
            message ?: stringResource(if (granted) R.string.setup_qr_hint else R.string.scan_camera_needed),
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.align(Alignment.BottomCenter).systemBarsPadding().padding(24.dp)
                .background(Color.Black.copy(alpha = 0.6f), MaterialTheme.shapes.large).padding(16.dp),
        )
    }
}
