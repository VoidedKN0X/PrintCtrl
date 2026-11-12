package com.vknxworks.printctrl.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope

/**
 * Runs [block] only while the app is on screen: it is cancelled when the app goes to the background and started
 * again when it comes back, so a screen that keeps itself fresh doesn't go on asking the server while nobody is
 * looking. [back] is false the first time and true on every later start, for catching up at once after a pause.
 */
@Composable
fun WhileOnScreen(vararg keys: Any?, block: suspend CoroutineScope.(back: Boolean) -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, *keys) {
        var started = false
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val back = started
            started = true
            block(back)
        }
    }
}
