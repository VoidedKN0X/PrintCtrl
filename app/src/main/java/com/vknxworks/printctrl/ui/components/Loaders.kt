package com.vknxworks.printctrl.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vknxworks.printctrl.R
import com.vknxworks.printctrl.data.net.ApiException
import com.vknxworks.printctrl.data.net.NotThisServerException
import com.vknxworks.printctrl.data.net.Tls
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.UnknownHostException
import kotlin.coroutines.cancellation.CancellationException

/**
 * A message the user can act on for any error the servers or the network give us. Each case says
 * what went wrong and, where there is one, what to do about it.
 */
fun Throwable.friendlyMessage(context: Context): String {
    Tls.pinMismatchMessage(this)?.let { return context.getString(R.string.err_pin_changed) }
    return when {
        this is ApiException && code == 401 -> context.getString(R.string.err_unauthorized)
        this is ApiException && code == 403 -> context.getString(R.string.err_forbidden, detail ?: "")
        this is ApiException && code == 404 -> context.getString(R.string.err_not_found)
        this is ApiException && code == 410 -> context.getString(R.string.err_gone)
        this is ApiException -> context.getString(R.string.err_server, code, detail ?: "")
        this is NotThisServerException -> context.getString(if (spoolman) R.string.err_not_spoolman else R.string.err_not_bambuddy)
        Tls.isCertificateError(this) -> context.getString(R.string.err_certificate)
        this is UnknownHostException -> context.getString(R.string.err_unknown_host)
        this is java.net.ConnectException -> context.getString(R.string.err_refused)
        this is java.net.SocketTimeoutException -> context.getString(R.string.err_timeout)
        this is javax.net.ssl.SSLException -> context.getString(R.string.err_tls, message ?: "")
        this is IOException -> message ?: context.getString(R.string.err_network)
        else -> message ?: context.getString(R.string.err_generic)
    }.redactTokens().trim()
}

/** Camera and socket tokens never show up in a message on screen. */
private val TOKEN_QUERY = Regex("""([?&]token=)[^&#\s]*""", RegexOption.IGNORE_CASE)

private fun String.redactTokens(): String = replace(TOKEN_QUERY, "$1…")

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ok<T>(val value: T) : Load<T>
    data class Err(val error: Throwable) : Load<Nothing>
}

/** Loads a value once and keeps it for the life of the screen's back stack entry. */
class Loader<T>(private val scope: CoroutineScope, private val block: suspend () -> T) {
    var state by mutableStateOf<Load<T>>(Load.Loading)
        private set
    var refreshing by mutableStateOf(false)
        private set
    private var job: Job? = null

    val value: T? get() = (state as? Load.Ok<T>)?.value

    init {
        reload()
    }

    /** Re-fetch. A silent reload keeps the current content on screen while it runs. */
    fun reload(silent: Boolean = false) {
        job?.cancel()
        if (!silent || state !is Load.Ok) state = Load.Loading
        refreshing = silent
        job = scope.launch {
            try {
                state = Load.Ok(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (!silent || state !is Load.Ok) state = Load.Err(e)
            } finally {
                refreshing = false
            }
        }
    }
}

/** Incrementally loaded list for big inventories. */
class PagedLoader<T>(
    private val scope: CoroutineScope,
    private val pageSize: Int = 50,
    private val fetch: suspend (start: Int, limit: Int) -> Pair<List<T>, Int>,
) {
    val items = mutableStateListOf<T>()
    var total by mutableIntStateOf(-1)
        private set
    var loading by mutableStateOf(false)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var error by mutableStateOf<Throwable?>(null)
        private set
    private var job: Job? = null

    val endReached: Boolean get() = total >= 0 && items.size >= total

    init {
        loadMore()
    }

    fun loadMore() {
        if (loading || endReached) return
        loading = true
        job = scope.launch {
            try {
                val (page, count) = fetch(items.size, pageSize)
                items.addAll(page)
                total = if (page.isEmpty()) items.size else count
                error = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                error = e
            } finally {
                loading = false
            }
        }
    }

    /** Reloads from the start, keeping the old list on screen until the first page arrives. */
    fun refresh() {
        job?.cancel()
        refreshing = true
        loading = true
        job = scope.launch {
            try {
                val (page, count) = fetch(0, pageSize)
                items.clear()
                items.addAll(page)
                total = if (page.isEmpty()) 0 else count
                error = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                error = e
            } finally {
                loading = false
                refreshing = false
            }
        }
    }
}

class LoaderStore : ViewModel() {
    val map = mutableMapOf<String, Any>()
}

@Composable
fun <T> rememberLoader(key: String, block: suspend () -> T): Loader<T> {
    val store: LoaderStore = viewModel()
    @Suppress("UNCHECKED_CAST")
    return remember(key) { store.map.getOrPut("L:$key") { Loader(store.viewModelScope, block) } as Loader<T> }
}

@Composable
fun <T> rememberPagedLoader(key: String, pageSize: Int = 50, fetch: suspend (Int, Int) -> Pair<List<T>, Int>): PagedLoader<T> {
    val store: LoaderStore = viewModel()
    @Suppress("UNCHECKED_CAST")
    return remember(key) { store.map.getOrPut("P:$key") { PagedLoader(store.viewModelScope, pageSize, fetch) } as PagedLoader<T> }
}

@Composable
fun LoadingView(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun ErrorView(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    MessageView(
        Icons.Outlined.ErrorOutline,
        stringResource(R.string.couldnt_load),
        message,
        modifier,
        actionLabel = onRetry?.let { stringResource(R.string.retry) },
        onAction = onRetry,
    )
}

@Composable
fun MessageView(
    icon: ImageVector,
    title: String,
    body: String? = null,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 360.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            if (body != null) {
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (actionLabel != null && onAction != null) {
                FilledTonalButton(onClick = onAction, shape = CircleShape) { Text(actionLabel) }
            }
        }
    }
}
