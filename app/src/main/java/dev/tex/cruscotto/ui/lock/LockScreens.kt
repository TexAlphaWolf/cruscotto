package dev.tex.cruscotto.ui.lock

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tex.cruscotto.R
import dev.tex.cruscotto.security.BioKeys
import dev.tex.cruscotto.security.showBiometricPrompt
import dev.tex.cruscotto.session.Session
import dev.tex.cruscotto.ui.components.findActivity
import dev.tex.cruscotto.ui.theme.LocalAppColors
import dev.tex.cruscotto.ui.theme.NumberStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PIN_MIN = 4
private const val PIN_MAX = 12

@Composable
private fun LockScaffold(title: String, subtitle: String?, content: @Composable () -> Unit) {
    val app = LocalAppColors.current
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).background(app.lockBackground)) {
        if (app.isDark) Box(Modifier.fillMaxWidth().height(3.dp).background(app.stripe))
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Gauge()
            Text(
                stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onBackground)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 360.dp))
            content()
        }
    }
}

/** The logo: a dial ringed with the theme's gradient. */
@Composable
private fun Gauge() {
    val app = LocalAppColors.current
    Box(Modifier.size(56.dp).border(5.dp, app.stripe, CircleShape), contentAlignment = Alignment.Center) {
        Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.onBackground, CircleShape))
    }
}

@Composable
private fun waitMessage(waitMs: Long): String = stringResource(R.string.lock_wait, ((waitMs + 999) / 1000).toInt())

/** Numeric keypad with dots. Calls [onSubmit] on OK. */
@Composable
fun PinPad(error: String?, busy: Boolean, waitMs: Long, onSubmit: (String) -> Unit, extra: (@Composable () -> Unit)? = null) {
    var pin by remember { mutableStateOf("") }
    val disabled = busy || waitMs > 0
    val okLabel = stringResource(R.string.action_ok)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.height(16.dp)) {
            repeat(maxOf(PIN_MIN, pin.length)) { i ->
                val on = i < pin.length
                Box(
                    Modifier.size(14.dp).clip(CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                        .background(if (on) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.background)
                )
            }
        }
        val msg = when {
            waitMs > 0 -> waitMessage(waitMs)
            busy -> stringResource(R.string.lock_checking)
            else -> error
        }
        Text(msg ?: " ", color = if (busy) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "OK")
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { k ->
                    val isOk = k == "OK"
                    val enabled = !disabled && (k != "OK" || pin.length >= PIN_MIN)
                    Box(
                        Modifier.size(72.dp).clip(CircleShape)
                            .background(if (isOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                            .border(1.dp, if (isOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            .then(
                                if (enabled) Modifier.clickable {
                                    when (k) {
                                        "⌫" -> { pin = pin.dropLast(1) }
                                        "OK" -> { val p = pin; pin = ""; onSubmit(p) }
                                        else -> { if (pin.length < PIN_MAX) pin += k }
                                    }
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (isOk) okLabel else k,
                            style = if (isOk) MaterialTheme.typography.titleMedium else NumberStyle.copy(fontSize = 24.sp),
                            color = when {
                                isOk -> MaterialTheme.colorScheme.onPrimary.copy(alpha = if (enabled) 1f else 0.5f)
                                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = if (disabled) 0.4f else 1f)
                            },
                        )
                    }
                }
            }
        }
        extra?.invoke()
    }
}

/**
 * Countdown of the wait after too many attempts. It ticks only while a wait is running;
 * [attempt] changes after every failed try, which re-reads the state.
 */
@Composable
private fun rememberLockout(attempt: Int): Long {
    var wait by remember { mutableLongStateOf(Session.vault.lockoutRemaining()) }
    LaunchedEffect(attempt) {
        wait = Session.vault.lockoutRemaining()
        while (wait > 0) {
            delay(minOf(wait, 1000L))
            wait = Session.vault.lockoutRemaining()
        }
    }
    return wait
}

// ---------- first launch ----------
@Composable
fun SetupScreen() {
    val scope = rememberCoroutineScope()
    var first by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val mismatch = stringResource(R.string.lock_pin_mismatch)
    LockScaffold(
        title = stringResource(if (first == null) R.string.setup_choose_pin else R.string.setup_repeat_pin),
        subtitle = if (first == null) stringResource(R.string.setup_pin_hint, PIN_MIN, PIN_MAX) else null,
    ) {
        PinPad(error = error, busy = busy, waitMs = 0, onSubmit = { p ->
            if (first == null) { first = p; error = null }
            else if (p != first) { first = null; error = mismatch }
            else {
                busy = true
                scope.launch { Session.setup(p) }
            }
        })
        if (first != null && !busy) TextButton(onClick = { first = null }) { Text(stringResource(R.string.action_start_over)) }
    }
}

@Composable
fun RecoveryCodeScreen(code: String) {
    var saved by remember { mutableStateOf(false) }
    val context = LocalContext.current
    LockScaffold(
        title = stringResource(R.string.recovery_title),
        subtitle = stringResource(R.string.recovery_hint),
    ) {
        SelectionContainer {
            Text(
                code, style = NumberStyle.copy(fontSize = 24.sp, letterSpacing = 2.sp),
                modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)).padding(horizontal = 18.dp, vertical = 14.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = saved, onCheckedChange = { saved = it })
            Text(stringResource(R.string.recovery_saved))
        }
        Button(onClick = { Session.recoveryAcknowledged(BioKeys.isAvailable(context)) }, enabled = saved, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_continue))
        }
    }
}

@Composable
fun OfferBiometricScreen() {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    LockScaffold(
        title = stringResource(R.string.bio_offer_title),
        subtitle = stringResource(R.string.bio_offer_hint),
    ) {
        Button(onClick = {
            enableBiometric(context, onDone = { Session.biometricOfferDone() }, onError = { error = it })
        }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.bio_enable)) }
        OutlinedButton(onClick = { Session.biometricOfferDone() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_later)) }
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
    }
}

/** Wraps the database key with the biometric Keystore key. */
fun enableBiometric(context: Context, onDone: () -> Unit, onError: (String) -> Unit) {
    val activity = context.findActivity() ?: return onError(context.getString(R.string.bio_error_prompt))
    val cipher = BioKeys.encryptCipher() ?: return onError(context.getString(R.string.bio_error_unavailable))
    activity.showBiometricPrompt(
        context.getString(R.string.bio_enable), context.getString(R.string.bio_confirm_subtitle),
        context.getString(R.string.action_cancel), cipher,
        onSuccess = { c ->
            val key = Session.currentKeyCopy()
            if (key == null) { onError(context.getString(R.string.bio_error_session)); return@showBiometricPrompt }
            try {
                val wrapped = c.doFinal(key)
                Session.vault.setBiometric(c.iv, wrapped)
                onDone()
            } catch (e: Exception) {
                onError(context.getString(R.string.bio_error_enable))
            } finally {
                key.fill(0)
            }
        },
        onError = { msg -> if (msg != null) onError(msg.ifEmpty { context.getString(R.string.bio_error_enable) }) },
    )
}

// ---------- unlock ----------
@Composable
fun UnlockScreen() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var mode by remember { mutableStateOf("pin") } // pin | recover | newpin
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var attempts by remember { mutableIntStateOf(0) }
    val wait = rememberLockout(attempts)
    val hasBio = Session.vault.hasBiometric()
    val needsRecovery = remember { Session.vault.pinNeedsRecovery() }

    // a lock during the recovery flow (e.g. auto-lock) closes the database: start again from the PIN
    LaunchedEffect(Unit) { Session.lockRequests.collect { mode = "pin"; busy = false; error = null } }

    fun startBiometric() {
        val activity = context.findActivity() ?: return
        val iv = Session.vault.biometricIv() ?: return
        val wrapped = Session.vault.biometricWrapped() ?: return
        val cipher = BioKeys.decryptCipher(iv)
        if (cipher == null) {
            Session.vault.clearBiometric()
            error = context.getString(R.string.bio_invalidated)
            return
        }
        activity.showBiometricPrompt(
            context.getString(R.string.bio_unlock_title), context.getString(R.string.bio_unlock_subtitle),
            context.getString(R.string.bio_use_pin), cipher,
            onSuccess = { c ->
                try {
                    val key = c.doFinal(wrapped)
                    scope.launch { busy = true; Session.unlockWithBiometricKey(key) }
                } catch (e: Exception) {
                    error = context.getString(R.string.bio_error_unlock)
                }
            },
            onError = { msg -> if (msg != null) error = msg.ifEmpty { context.getString(R.string.bio_error_unlock) } },
        )
    }

    LaunchedEffect(Unit) { if (hasBio && wait == 0L) startBiometric() }

    when (mode) {
        "recover" -> {
            var code by remember { mutableStateOf("") }
            LockScaffold(stringResource(R.string.recover_title), stringResource(R.string.recover_hint)) {
                OutlinedTextField(
                    value = code, onValueChange = { code = it.uppercase() }, singleLine = true,
                    label = { Text(stringResource(R.string.recover_field)) }, placeholder = { Text("XXXX-XXXX-XXXX-XXXX") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), modifier = Modifier.fillMaxWidth(),
                )
                val msg = if (wait > 0) waitMessage(wait) else error
                if (msg != null) Text(msg, color = MaterialTheme.colorScheme.error)
                val invalid = stringResource(R.string.recover_invalid)
                Button(onClick = {
                    busy = true; error = null
                    scope.launch {
                        val ok = Session.unlockWithRecovery(code)
                        busy = false
                        if (ok) mode = "newpin" else { error = invalid; attempts++ }
                    }
                }, enabled = !busy && wait == 0L, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (busy) R.string.lock_checking else R.string.action_continue))
                }
                TextButton(onClick = { mode = "pin"; error = null }) { Text(stringResource(R.string.recover_back)) }
            }
        }
        "newpin" -> {
            var first by remember { mutableStateOf<String?>(null) }
            val mismatch = stringResource(R.string.lock_pin_mismatch)
            LockScaffold(
                stringResource(if (first == null) R.string.newpin_title else R.string.newpin_repeat),
                stringResource(R.string.newpin_hint),
            ) {
                PinPad(error = error, busy = busy, waitMs = 0, onSubmit = { p ->
                    if (first == null) { first = p; error = null }
                    else if (p != first) { first = null; error = mismatch }
                    else { busy = true; scope.launch { Session.setNewPinAfterRecovery(p) } }
                })
            }
        }
        else -> LockScaffold(
            stringResource(R.string.unlock_title),
            if (needsRecovery) stringResource(R.string.unlock_device_key_lost) else null,
        ) {
            PinPad(error = error, busy = busy, waitMs = wait, onSubmit = { p ->
                busy = true; error = null
                scope.launch {
                    val ok = Session.unlockWithPin(p)
                    busy = false
                    if (!ok) {
                        attempts++
                        val left = Session.vault.remainingAttemptsBeforeWait()
                        error = when {
                            Session.vault.pinNeedsRecovery() -> context.getString(R.string.unlock_device_key_lost)
                            left > 0 -> context.resources.getQuantityString(R.plurals.unlock_wrong_pin, left, left)
                            else -> null
                        }
                    }
                }
            }) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (hasBio) TextButton(onClick = { error = null; startBiometric() }, enabled = wait == 0L) { Text(stringResource(R.string.unlock_use_biometric)) }
                    TextButton(onClick = { mode = "recover"; error = null }) { Text(stringResource(R.string.unlock_forgot)) }
                }
            }
        }
    }
}

@Composable
fun OtpScreen() {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var attempts by remember { mutableIntStateOf(0) }
    val wait = rememberLockout(attempts)
    val invalid = stringResource(R.string.otp_invalid)
    LockScaffold(stringResource(R.string.otp_title), stringResource(R.string.otp_hint)) {
        OutlinedTextField(
            value = code, onValueChange = { v -> code = v.filter { it.isDigit() }.take(6) }, singleLine = true,
            textStyle = NumberStyle.copy(fontSize = 26.sp, letterSpacing = 6.sp, textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth(),
        )
        val msg = if (wait > 0) waitMessage(wait) else error
        Text(msg ?: " ", color = MaterialTheme.colorScheme.error)
        Button(onClick = {
            busy = true
            scope.launch {
                val ok = Session.verifyOtp(code)
                busy = false
                if (!ok) { code = ""; error = invalid; attempts++ }
            }
        }, enabled = code.length == 6 && !busy && wait == 0L, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_verify)) }
        TextButton(onClick = { Session.lock() }) { Text(stringResource(R.string.action_exit)) }
        Spacer(Modifier.height(8.dp))
    }
}
