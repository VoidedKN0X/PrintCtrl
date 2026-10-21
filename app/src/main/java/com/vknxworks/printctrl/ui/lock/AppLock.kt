package com.vknxworks.printctrl.ui.lock

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.vknxworks.printctrl.R
import com.vknxworks.printctrl.container
import com.vknxworks.printctrl.data.lock.LockVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.crypto.Cipher
import kotlin.math.roundToInt

/**
 * App lock state (the shared Ctrl series lock): whether the app is unlocked, and when it locks again.
 * The secrets live in [LockVault]. Locking wipes the vault key from memory, so sealed server logins
 * can't be read until the next unlock.
 */
object AppLock {
    var unlocked by mutableStateOf(false)
        private set

    /** Goes up every time the app locks, so the lock screen offers the fingerprint once per lock. */
    var lockCount by mutableIntStateOf(0)
        private set

    /** A PIN screen shown on top of the app: turning the lock on or off, or changing the PIN. */
    var request by mutableStateOf<PinRequest?>(null)

    /** Set by the app: wipes the vault key and drops the server logins from memory. */
    var onLocked: () -> Unit = {}

    private var authenticating = false
    private var backgroundAt = 0L
    private var expectReturnAt = 0L

    /**
     * The app itself is opening another app (file picker, browser, ntfy, Android settings). That trip doesn't count as leaving, so you
     * don't have to unlock again when you come back.
     */
    fun expectReturn() {
        expectReturnAt = SystemClock.elapsedRealtime()
    }

    /** The current trip away was started by the app itself. */
    private var exemptTrip = false

    fun onBackground(enabled: Boolean, timeoutMillis: Long) {
        if (authenticating) return
        backgroundAt = SystemClock.elapsedRealtime()
        // Only right after the app opened something itself; leaving later still counts.
        exemptTrip = expectReturnAt > 0 && backgroundAt - expectReturnAt < 5_000
        expectReturnAt = 0L
        // "As soon as you leave" locks right away, so the key is gone while the app is in the background.
        if (enabled && unlocked && timeoutMillis == 0L && !exemptTrip) lock()
    }

    fun onForeground(enabled: Boolean, timeoutMillis: Long) {
        val since = backgroundAt
        val exempt = exemptTrip
        backgroundAt = 0L
        expectReturnAt = 0L
        exemptTrip = false
        if (!enabled || !unlocked || since <= 0 || timeoutMillis < 0) return
        // A trip the app started still locks if you stay away long: after 5 minutes, or the chosen time if longer.
        val limit = if (exempt) maxOf(timeoutMillis, EXEMPT_TRIP_MAX) else timeoutMillis
        if (SystemClock.elapsedRealtime() - since >= limit) lock()
    }

    private const val EXEMPT_TRIP_MAX = 5 * 60_000L

    fun lock() {
        unlocked = false
        lockCount++
        request = null
        onLocked()
    }

    fun markUnlocked() {
        backgroundAt = 0L
        unlocked = true
    }

    /** Only class 3 ("strong") biometrics: camera-only face unlock can be fooled by a photo. */
    fun biometricAvailable(context: Context): Boolean = runCatching {
        context.getSystemService(BiometricManager::class.java)
            ?.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }.getOrDefault(false)

    /**
     * Shows the fingerprint prompt for [cipher], a Keystore key that only works after a strong match.
     * [onSuccess] gets the unlocked cipher; [onError] gets a message, or null when cancelled.
     */
    fun biometricPrompt(
        activity: Activity,
        title: String,
        subtitle: String,
        cipher: Cipher,
        onError: (String?) -> Unit = {},
        onSuccess: (Cipher) -> Unit,
    ) {
        if (authenticating) return
        authenticating = true
        try {
            val prompt = BiometricPrompt.Builder(activity)
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButton(activity.getString(R.string.lock_use_pin), activity.mainExecutor) { _, _ ->
                    authenticating = false
                    onError(null)
                }
                .build()
            prompt.authenticate(
                BiometricPrompt.CryptoObject(cipher),
                CancellationSignal(),
                activity.mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        authenticating = false
                        val c = result.cryptoObject?.cipher
                        if (c != null) onSuccess(c) else onError(activity.getString(R.string.lock_fingerprint_failed))
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        authenticating = false
                        val cancelled = errorCode == BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED ||
                            errorCode == BiometricPrompt.BIOMETRIC_ERROR_CANCELED
                        onError(if (cancelled) null else errString.toString())
                    }
                },
            )
        } catch (e: Exception) {
            authenticating = false
            onError(e.message ?: activity.getString(R.string.lock_fingerprint_failed))
        }
    }
}

/** What a PIN screen on top of the app is for. */
enum class PinRequest { ENABLE, CHANGE, DISABLE }

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Shows [content] only while the app is unlocked (or the lock is off), with the PIN screens for
 * turning the lock on or off and changing the PIN on top of it.
 */
@Composable
fun AppLockGate(content: @Composable () -> Unit) {
    val c = LocalContext.current.container
    val vault = c.vault
    if (vault.enabled && !AppLock.unlocked) {
        PinLockScreen()
        return
    }
    val request = AppLock.request
    Box(Modifier.fillMaxSize()) {
        // Hidden from TalkBack while a PIN screen covers it.
        Box(if (request != null) Modifier.fillMaxSize().clearAndSetSemantics { } else Modifier.fillMaxSize()) { content() }
        when (request) {
            PinRequest.ENABLE -> PinSetupScreen(
                title = stringResource(R.string.lock_choose_pin),
                intro = stringResource(R.string.lock_enable_intro, LockVault.PIN_MIN),
                verifyCurrent = false,
                cancellable = true,
                onDone = { pin -> c.enableLock(pin); AppLock.request = null },
                onCancel = { AppLock.request = null },
            )
            PinRequest.CHANGE -> PinSetupScreen(
                title = stringResource(R.string.lock_change_pin),
                intro = stringResource(R.string.lock_change_intro, LockVault.PIN_MIN),
                verifyCurrent = true,
                cancellable = true,
                onDone = { pin -> withContext(Dispatchers.Default) { vault.changePin(pin) }; AppLock.request = null },
                onCancel = { AppLock.request = null },
            )
            PinRequest.DISABLE -> PinSetupScreen(
                title = stringResource(R.string.lock_turn_off),
                intro = stringResource(R.string.lock_turn_off_intro),
                verifyCurrent = true,
                verifyOnly = true,
                cancellable = true,
                onDone = { c.disableLock(); AppLock.request = null },
                onCancel = { AppLock.request = null },
            )
            null -> Unit
        }
    }
}

/** Swallows every touch, so nothing underneath a lock screen can be used. */
@Composable
private fun Modifier.blockTouches(): Modifier =
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }

@Composable
private fun LockBadge() {
    Box(
        Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Outlined.Lock, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary) }
}

/** The lock screen: PIN pad, fingerprint, and "Forgot PIN?". */
@Composable
private fun PinLockScreen() {
    val context = LocalContext.current
    val c = context.container
    val vault = c.vault
    val activity = context.findActivity()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val shake = remember { Animatable(0f) }
    var pin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var lockout by remember { mutableLongStateOf(vault.lockoutRemaining()) }
    var forgetting by remember { mutableStateOf(false) }
    val bioAvailable = remember(AppLock.lockCount) {
        vault.biometricEnabled && AppLock.biometricAvailable(context) && !vault.pinRequired()
    }

    LaunchedEffect(lockout > 0) {
        while (lockout > 0) {
            delay(500)
            lockout = vault.lockoutRemaining()
        }
    }

    fun askFingerprint() {
        val a = activity ?: return
        val cipher = vault.biometricUnlockCipher() ?: run {
            message = if (!vault.biometricEnabled) context.getString(R.string.lock_fingerprint_changed) else null
            return
        }
        AppLock.biometricPrompt(
            a,
            context.getString(R.string.lock_prompt_title),
            context.getString(R.string.lock_prompt_subtitle),
            cipher,
            onError = { message = it },
        ) { unlocked ->
            if (vault.unlockWithBiometric(unlocked)) c.onUnlocked() else message = context.getString(R.string.lock_fingerprint_failed)
        }
    }
    LaunchedEffect(AppLock.lockCount) { if (bioAvailable) askFingerprint() }

    fun submit() {
        if (checking || lockout > 0 || pin.length < LockVault.PIN_MIN) return
        val entered = pin
        checking = true
        scope.launch {
            // Slow on purpose (it makes guessing slow too), so off the main thread.
            val result = withContext(Dispatchers.Default) { vault.unlockWithPin(entered) }
            checking = false
            pin = ""
            when (result) {
                LockVault.PinResult.Ok -> c.onUnlocked()
                LockVault.PinResult.Wrong, LockVault.PinResult.LockedOut -> {
                    lockout = vault.lockoutRemaining()
                    message = context.getString(R.string.lock_wrong_pin)
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    for (x in listOf(18f, -14f, 10f, -6f, 0f)) shake.animateTo(x, tween(45))
                }
            }
        }
    }

    BackHandler { activity?.moveTaskToBack(true) }
    PinPadScreen(
        title = stringResource(R.string.lock_title),
        message = when {
            lockout > 0 -> stringResource(R.string.lock_too_many, ((lockout + 999) / 1000).toInt())
            message != null -> message!!
            vault.biometricEnabled && vault.pinRequired() -> stringResource(R.string.lock_pin_required)
            else -> stringResource(R.string.lock_enter_pin)
        },
        isError = lockout > 0 || message != null,
        pin = pin,
        shake = shake.value,
        checking = checking,
        enabled = lockout == 0L && !checking,
        onDigit = { d ->
            if (pin.length < LockVault.PIN_MAX) {
                message = null
                pin += d
                if (pin.length == vault.pinLength) submit()
            }
        },
        onBackspace = { pin = pin.dropLast(1) },
        onConfirm = ::submit,
        showConfirm = pin.length >= LockVault.PIN_MIN && pin.length != vault.pinLength,
        confirmLabel = stringResource(R.string.lock_ok),
        bottom = {
            if (bioAvailable) {
                TextButton(onClick = ::askFingerprint) {
                    Icon(Icons.Outlined.Fingerprint, null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.lock_use_fingerprint))
                }
            }
            TextButton(onClick = { forgetting = true }) { Text(stringResource(R.string.lock_forgot)) }
        },
    )
    if (forgetting) {
        AlertDialog(
            onDismissRequest = { forgetting = false },
            title = { Text(stringResource(R.string.lock_forgot_title)) },
            text = { Text(stringResource(R.string.lock_forgot_body)) },
            confirmButton = {
                TextButton(onClick = {
                    forgetting = false
                    scope.launch { c.forgetEverything() }
                }) { Text(stringResource(R.string.lock_forgot_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { forgetting = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Choosing a PIN (twice), changing it (current, then new twice) or confirming it to turn the lock off. */
@Composable
private fun PinSetupScreen(
    title: String,
    intro: String,
    verifyCurrent: Boolean,
    cancellable: Boolean,
    onDone: suspend (String) -> Unit,
    onCancel: () -> Unit,
    verifyOnly: Boolean = false,
) {
    val context = LocalContext.current
    val vault = context.container.vault
    val activity = context.findActivity()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val shake = remember { Animatable(0f) }
    // 0 = current PIN, 1 = new PIN, 2 = repeat new PIN
    var step by remember { mutableIntStateOf(if (verifyCurrent) 0 else 1) }
    var pin by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var lockout by remember { mutableLongStateOf(vault.lockoutRemaining()) }

    LaunchedEffect(lockout > 0) {
        while (lockout > 0) {
            delay(500)
            lockout = vault.lockoutRemaining()
        }
    }

    BackHandler { if (cancellable) onCancel() else activity?.moveTaskToBack(true) }

    fun wrong(text: String) {
        message = text
        pin = ""
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch { for (x in listOf(18f, -14f, 10f, -6f, 0f)) shake.animateTo(x, tween(45)) }
    }

    fun finish(newPin: String) {
        busy = true
        scope.launch {
            runCatching { onDone(newPin) }.onFailure { busy = false; wrong(context.getString(R.string.lock_save_failed)) }
        }
    }

    fun next() {
        if (busy || lockout > 0) return
        when (step) {
            0 -> {
                if (pin.length < LockVault.PIN_MIN) return
                val entered = pin
                busy = true
                scope.launch {
                    val result = withContext(Dispatchers.Default) { vault.unlockWithPin(entered) }
                    busy = false
                    if (result == LockVault.PinResult.Ok) {
                        pin = ""
                        message = null
                        if (verifyOnly) finish(entered) else step = 1
                    } else {
                        lockout = vault.lockoutRemaining()
                        wrong(context.getString(R.string.lock_wrong_pin))
                    }
                }
            }
            1 -> {
                if (pin.length < LockVault.PIN_MIN) return
                if (pin.toSet().size == 1 || "0123456789012".contains(pin) || "9876543210987".contains(pin)) {
                    wrong(context.getString(R.string.lock_pin_too_easy))
                    return
                }
                first = pin
                pin = ""
                message = null
                step = 2
            }
            else -> {
                if (pin != first) {
                    first = ""
                    step = 1
                    wrong(context.getString(R.string.lock_pin_mismatch))
                    return
                }
                finish(pin)
            }
        }
    }

    PinPadScreen(
        title = title,
        message = when {
            lockout > 0 -> stringResource(R.string.lock_too_many, ((lockout + 999) / 1000).toInt())
            message != null -> message!!
            step == 0 -> stringResource(R.string.lock_enter_current_pin)
            step == 1 -> intro
            else -> stringResource(R.string.lock_repeat_pin)
        },
        isError = lockout > 0 || message != null,
        pin = pin,
        shake = shake.value,
        checking = busy,
        enabled = !busy && lockout == 0L,
        onDigit = { d -> if (pin.length < LockVault.PIN_MAX) { message = null; pin += d } },
        onBackspace = { pin = pin.dropLast(1) },
        onConfirm = ::next,
        showConfirm = pin.length >= LockVault.PIN_MIN,
        confirmLabel = stringResource(if (step == 2 || (step == 0 && verifyOnly)) R.string.lock_done else R.string.lock_next),
        bottom = { if (cancellable) TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun PinPadScreen(
    title: String,
    message: String,
    isError: Boolean,
    pin: String,
    shake: Float,
    checking: Boolean,
    enabled: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onConfirm: () -> Unit,
    showConfirm: Boolean,
    confirmLabel: String,
    bottom: @Composable () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    Surface(Modifier.fillMaxSize().blockTouches(), color = cs.background) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            LockBadge()
            Spacer(Modifier.height(18.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) cs.error else cs.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 340.dp),
            )
            Spacer(Modifier.height(24.dp))
            Box(Modifier.height(20.dp).offset { IntOffset(shake.roundToInt(), 0) }, contentAlignment = Alignment.Center) {
                if (checking) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(maxOf(pin.length, LockVault.PIN_MIN)) { i ->
                        Box(Modifier.size(12.dp).clip(CircleShape).background(if (i < pin.length) cs.primary else cs.surfaceContainerHighest))
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                listOf("123", "456", "789").forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        row.forEach { d ->
                            DigitKey(d.toString(), enabled) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onDigit(d)
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBackspace, enabled = enabled && pin.isNotEmpty(), modifier = Modifier.size(72.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.Backspace, stringResource(R.string.lock_delete_digit))
                    }
                    DigitKey("0", enabled) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDigit('0')
                    }
                    TextButton(onClick = onConfirm, enabled = enabled && showConfirm, modifier = Modifier.size(72.dp)) { Text(confirmLabel) }
                }
            }
            Spacer(Modifier.height(16.dp))
            bottom()
        }
    }
}

@Composable
private fun DigitKey(label: String, enabled: Boolean, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = Modifier.size(72.dp),
    ) { Text(label, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium) }
}
