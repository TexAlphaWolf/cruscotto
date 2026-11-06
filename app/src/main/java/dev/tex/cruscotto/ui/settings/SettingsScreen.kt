package dev.tex.cruscotto.ui.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import dev.tex.cruscotto.R
import dev.tex.cruscotto.data.Category
import dev.tex.cruscotto.data.Kind
import dev.tex.cruscotto.data.NotABackupException
import dev.tex.cruscotto.data.Repository
import dev.tex.cruscotto.data.newId
import dev.tex.cruscotto.export.rememberYearPdfExport
import dev.tex.cruscotto.security.BioKeys
import dev.tex.cruscotto.security.Crypto
import dev.tex.cruscotto.session.Session
import dev.tex.cruscotto.ui.LocalSnackbar
import dev.tex.cruscotto.ui.components.Dropdown
import dev.tex.cruscotto.ui.components.FormDialog
import dev.tex.cruscotto.ui.components.ScreenTitle
import dev.tex.cruscotto.ui.components.SectionCard
import dev.tex.cruscotto.ui.components.TextInput
import dev.tex.cruscotto.ui.components.centsToInput
import dev.tex.cruscotto.ui.components.money
import dev.tex.cruscotto.ui.components.parseMoney
import dev.tex.cruscotto.ui.components.pct
import dev.tex.cruscotto.ui.lock.enableBiometric
import dev.tex.cruscotto.ui.theme.LocalStatus
import dev.tex.cruscotto.ui.theme.NumberStyle
import dev.tex.cruscotto.prefs.AppLanguage
import dev.tex.cruscotto.prefs.AppPrefs
import dev.tex.cruscotto.prefs.ThemeMode
import dev.tex.cruscotto.ui.components.findActivity
import dev.tex.cruscotto.yearend.YearEnd
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(repo: Repository) {
    val settings by repo.settings.collectAsStateWithLifecycle(null)
    val cats by repo.categories.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    val context = LocalContext.current
    val status = LocalStatus.current
    fun say(res: Int, vararg args: Any) { scope.launch { snackbar.showSnackbar(context.getString(res, *args)) } }

    // ---- local drafts, saved with the buttons ----
    var salary by remember(settings?.salaryCents) { mutableStateOf(centsToInput(settings?.salaryCents ?: 0)) }
    var outDraft by remember(cats) { mutableStateOf(cats.filter { it.kind == Kind.OUT }.sortedBy { it.sort }) }
    var inDraft by remember(cats) { mutableStateOf(cats.filter { it.kind == Kind.IN }.sortedBy { it.sort }) }
    val sum = outDraft.sumOf { it.pct }
    val sumOk = kotlin.math.abs(sum - 100.0) < 0.01
    val unnamed = stringResource(R.string.cat_unnamed)

    // ---- security ----
    var bioOn by remember { mutableStateOf(Session.vault.hasBiometric()) }
    var autoLock by remember { mutableIntStateOf(Session.vault.autoLockMinutes()) }
    var changePin by remember { mutableStateOf(false) }
    var otpSetup by remember { mutableStateOf(false) }
    var otpOff by remember { mutableStateOf(false) }
    val bioAvailable = remember { BioKeys.isAvailable(context) }

    // ---- backup ----
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            try {
                val json = repo.exportJson()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                }
                say(R.string.msg_backup_exported)
            } catch (e: Exception) {
                say(R.string.msg_backup_export_failed)
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
                }
                val n = repo.importJson(text)
                say(R.string.msg_backup_imported, n)
            } catch (e: NotABackupException) {
                say(R.string.msg_not_a_backup)
            } catch (e: Exception) {
                say(R.string.msg_backup_invalid)
            }
        }
    }

    val themeMode by AppPrefs.theme.collectAsState()
    val language = remember { AppPrefs.language }
    var purgeTxns by remember { mutableStateOf(AppPrefs.purgeTxns) }
    var purgeLogs by remember { mutableStateOf(AppPrefs.purgeLogs) }
    val lastYear = remember { java.time.LocalDate.now().year }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val exportPdf = rememberYearPdfExport(repo)
    fun setPurge(txns: Boolean, logs: Boolean) {
        purgeTxns = txns; purgeLogs = logs
        AppPrefs.purgeTxns = txns; AppPrefs.purgeLogs = logs
        YearEnd.schedule(context)
        if ((txns || logs) && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { ScreenTitle(stringResource(R.string.tab_settings)) }
        item {
            SectionCard(title = stringResource(R.string.set_appearance)) {
                Text(stringResource(R.string.set_appearance_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ThemeMode.SYSTEM to R.string.theme_system,
                        ThemeMode.LIGHT to R.string.theme_light,
                        ThemeMode.DARK to R.string.theme_dark,
                    ).forEach { (m, label) ->
                        FilterChip(selected = themeMode == m, onClick = { AppPrefs.setTheme(m) }, label = { Text(stringResource(label)) })
                    }
                }
                Text(stringResource(R.string.set_language), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        AppLanguage.SYSTEM to stringResource(R.string.lang_system),
                        // language names are written in their own language, so they can always be found
                        AppLanguage.ENGLISH to "English",
                        AppLanguage.GERMAN to "Deutsch",
                        AppLanguage.ITALIAN to "Italiano",
                    ).forEach { (l, label) ->
                        FilterChip(selected = language == l, onClick = {
                            if (l != language) {
                                AppPrefs.setLanguage(l)
                                Session.relocalizeDefaults()
                                context.findActivity()?.recreate()
                            }
                        }, label = { Text(label) })
                    }
                }
            }
        }
        item {
            SectionCard(title = stringResource(R.string.set_yearly)) {
                Text(stringResource(R.string.set_yearly_hint, lastYear + 1), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SettingRow(stringResource(R.string.set_purge_txns), stringResource(R.string.set_purge_txns_hint)) {
                    Switch(checked = purgeTxns, onCheckedChange = { setPurge(it, purgeLogs) })
                }
                SettingRow(stringResource(R.string.set_purge_logs), stringResource(R.string.set_purge_logs_hint)) {
                    Switch(checked = purgeLogs, onCheckedChange = { setPurge(purgeTxns, it) })
                }
            }
        }
        item {
            SectionCard(title = stringResource(R.string.set_salary)) {
                TextInput(salary, { salary = it }, stringResource(R.string.field_salary), keyboard = KeyboardType.Decimal, placeholder = "1650")
                Text(stringResource(R.string.set_salary_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val outs = cats.filter { it.kind == Kind.OUT }.sortedBy { it.sort }
                Dropdown(stringResource(R.string.field_garage_category), outs, outs.firstOrNull { it.id == settings?.carCategoryId }, { it.name }, { c ->
                    settings?.let { s -> scope.launch { repo.saveSettings(s.copy(carCategoryId = c.id)) } }
                })
                Button(onClick = {
                    val cents = parseMoney(salary)
                    val s = settings
                    if (cents == null || s == null) say(R.string.err_amount_invalid)
                    else scope.launch { repo.saveSettings(s.copy(salaryCents = cents)); snackbar.showSnackbar(context.getString(R.string.msg_salary_saved, money(cents))) }
                }) { Text(stringResource(R.string.action_save_salary)) }
            }
        }
        item {
            SectionCard(title = stringResource(R.string.set_categories), trailing = {
                Text(stringResource(R.string.set_total, pct(sum)), style = NumberStyle.copy(fontSize = 13.sp), color = if (sumOk) status.ok else status.bad)
            }) {
                Text(stringResource(R.string.set_categories_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                outDraft.forEachIndexed { i, c ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    OutCategoryEditor(
                        c,
                        onChange = { nc -> outDraft = outDraft.toMutableList().also { it[i] = nc } },
                        onDelete = { outDraft = outDraft.filterIndexed { j, _ -> j != i } },
                    )
                }
                val newName = stringResource(R.string.cat_new)
                OutlinedButton(onClick = {
                    outDraft = outDraft + Category(id = newId(), kind = Kind.OUT, name = newName, sort = outDraft.size)
                }) { Text(stringResource(R.string.action_add_category)) }
            }
        }
        item {
            SectionCard(title = stringResource(R.string.set_income_categories)) {
                inDraft.forEachIndexed { i, c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(c.name, { v -> inDraft = inDraft.toMutableList().also { it[i] = c.copy(name = v) } },
                            singleLine = true, modifier = Modifier.weight(1f))
                        IconButton(onClick = { if (inDraft.size > 1) inDraft = inDraft.filterIndexed { j, _ -> j != i } }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_delete_category))
                        }
                    }
                }
                val newIncome = stringResource(R.string.cat_new_income)
                OutlinedButton(onClick = { inDraft = inDraft + Category(id = newId(), kind = Kind.IN, name = newIncome, sort = inDraft.size) }) {
                    Text(stringResource(R.string.action_add_income_category))
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = {
                    val all = outDraft.mapIndexed { i, c -> c.copy(sort = i, name = c.name.trim().ifEmpty { unnamed }) } +
                        inDraft.mapIndexed { i, c -> c.copy(sort = i, name = c.name.trim().ifEmpty { unnamed }) }
                    scope.launch {
                        repo.saveCategories(all)
                        val s = settings
                        if (s != null && all.none { it.id == s.carCategoryId && it.kind == Kind.OUT }) {
                            outDraft.firstOrNull()?.let { repo.saveSettings(s.copy(carCategoryId = it.id)) }
                        }
                        snackbar.showSnackbar(context.getString(R.string.msg_categories_saved))
                    }
                }, enabled = sumOk && outDraft.isNotEmpty() && inDraft.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_save_categories)) }
                if (!sumOk) Text(stringResource(R.string.set_fix_shares, pct(sum)), color = status.bad, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            SectionCard(title = stringResource(R.string.set_security)) {
                Text(stringResource(R.string.set_security_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SettingRow(stringResource(R.string.set_pin), stringResource(R.string.set_pin_hint)) {
                    OutlinedButton(onClick = { changePin = true }) { Text(stringResource(R.string.action_change)) }
                }
                SettingRow(
                    stringResource(R.string.set_biometric),
                    stringResource(if (bioAvailable) R.string.set_biometric_hint else R.string.set_biometric_unavailable),
                ) {
                    Switch(checked = bioOn, enabled = bioAvailable || bioOn, onCheckedChange = { on ->
                        if (on) enableBiometric(context, onDone = { bioOn = true }, onError = { msg -> scope.launch { snackbar.showSnackbar(msg) } })
                        else { Session.vault.clearBiometric(); bioOn = false }
                    })
                }
                val otpOn = settings?.totpOn == true
                SettingRow(stringResource(R.string.set_otp), stringResource(if (otpOn) R.string.set_otp_on else R.string.set_otp_off)) {
                    if (otpOn) OutlinedButton(onClick = { otpOff = true }) { Text(stringResource(R.string.action_disable)) }
                    else OutlinedButton(onClick = { otpSetup = true }) { Text(stringResource(R.string.action_enable)) }
                }
                val lockOptions = listOf(0, 1, 5, 15, 30)
                Dropdown(
                    stringResource(R.string.set_auto_lock), lockOptions, lockOptions.firstOrNull { it == autoLock } ?: 1,
                    { m -> if (m == 0) stringResource(R.string.lock_immediately) else stringResource(R.string.lock_after_min, m) },
                    { m -> autoLock = m; Session.vault.setAutoLockMinutes(m) },
                )
                OutlinedButton(onClick = { Session.lock() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_lock_now)) }
            }
        }
        item {
            SectionCard(title = stringResource(R.string.set_backup)) {
                Text(stringResource(R.string.set_backup_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { Session.suspendAutoLock = true; exportLauncher.launch("cruscotto-backup-${LocalDate.now()}.json") }) { Text(stringResource(R.string.action_export)) }
                    OutlinedButton(onClick = { Session.suspendAutoLock = true; importLauncher.launch(arrayOf("application/json", "text/plain")) }) { Text(stringResource(R.string.action_import)) }
                }
                Text(stringResource(R.string.set_pdf_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { exportPdf(lastYear) }) { Text(stringResource(R.string.action_pdf_year, lastYear)) }
            }
        }
    }

    if (changePin) ChangePinDialog(onDone = { changePin = false; say(R.string.msg_pin_changed) }, onDismiss = { changePin = false })
    if (otpSetup) OtpSetupDialog(onConfirm = { secret ->
        settings?.let { s -> scope.launch { repo.saveSettings(s.copy(totpSecret = secret, totpOn = true)); snackbar.showSnackbar(context.getString(R.string.msg_otp_on)) } }
        otpSetup = false
    }, onDismiss = { otpSetup = false })
    if (otpOff) {
        var code by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }
        val invalid = stringResource(R.string.otp_invalid_short)
        FormDialog(stringResource(R.string.otp_disable_title), stringResource(R.string.action_disable), error = err, onDismiss = { otpOff = false }, onConfirm = {
            val s = settings
            if (s != null && Crypto.verifyTotp(s.totpSecret, code)) {
                scope.launch { repo.saveSettings(s.copy(totpOn = false, totpSecret = "")); snackbar.showSnackbar(context.getString(R.string.msg_otp_off)) }
                otpOff = false
            } else err = invalid
        }) {
            TextInput(code, { code = it.filter { c -> c.isDigit() }.take(6) }, stringResource(R.string.field_otp_current), keyboard = KeyboardType.NumberPassword)
        }
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, action: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        action()
    }
}

@Composable
private fun OutCategoryEditor(c: Category, onChange: (Category) -> Unit, onDelete: () -> Unit) {
    var pctText by remember(c.id) { mutableStateOf(if (c.pct == 0.0) "" else c.pct.toString().removeSuffix(".0")) }
    Column(Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(c.name, { onChange(c.copy(name = it)) }, singleLine = true, label = { Text(stringResource(R.string.field_name)) }, modifier = Modifier.weight(1f))
            OutlinedTextField(
                pctText,
                { v -> pctText = v; onChange(c.copy(pct = v.replace(',', '.').toDoubleOrNull() ?: 0.0)) },
                singleLine = true, label = { Text("%") }, modifier = Modifier.width(84.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_delete_category)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = c.isBuffer, onCheckedChange = { on -> onChange(c.copy(isBuffer = on, bufferOrder = if (on && c.bufferOrder == 0) 1 else c.bufferOrder)) })
            Text(stringResource(R.string.field_buffer), modifier = Modifier.weight(1f))
            if (c.isBuffer) {
                Text(stringResource(R.string.field_order), style = MaterialTheme.typography.bodySmall)
                IconButton(onClick = { onChange(c.copy(bufferOrder = (c.bufferOrder - 1).coerceAtLeast(1))) }) {
                    Text("−", fontSize = 20.sp)
                }
                Text(c.bufferOrder.toString(), style = NumberStyle)
                IconButton(onClick = { onChange(c.copy(bufferOrder = c.bufferOrder + 1)) }) { Text("+", fontSize = 20.sp) }
            }
        }
    }
}

@Composable
private fun ChangePinDialog(onDone: () -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var cur by remember { mutableStateOf("") }
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val errLength = stringResource(R.string.err_pin_length)
    val errMismatch = stringResource(R.string.lock_pin_mismatch)
    val errCurrent = stringResource(R.string.err_pin_current)
    FormDialog(stringResource(R.string.pin_change_title), stringResource(R.string.action_change), error = err, confirmEnabled = !busy, onDismiss = onDismiss, onConfirm = {
        when {
            !p1.matches(Regex("\\d{4,12}")) -> err = errLength
            p1 != p2 -> err = errMismatch
            else -> {
                busy = true; err = null
                scope.launch {
                    val ok = Session.changePin(cur, p1)
                    busy = false
                    if (ok) onDone() else err = errCurrent
                }
            }
        }
    }) {
        PinField(cur, { cur = it }, stringResource(R.string.field_pin_current))
        PinField(p1, { p1 = it }, stringResource(R.string.field_pin_new))
        PinField(p2, { p2 = it }, stringResource(R.string.field_pin_repeat))
        if (busy) Text(stringResource(R.string.lock_checking), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PinField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value, { v -> onChange(v.filter { it.isDigit() }.take(12)) }, label = { Text(label) }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth(),
    )
}

/** OTP setup: add the account to an authenticator app, then confirm with one code. */
@Composable
private fun OtpSetupDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val secret = remember { Crypto.newTotpSecret() }
    val qr = remember(secret) { qrBitmap(Crypto.otpAuthUri(secret), 512) }
    var code by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    val invalid = stringResource(R.string.otp_invalid)
    val noApp = stringResource(R.string.otp_no_app)
    FormDialog(stringResource(R.string.otp_enable_title), stringResource(R.string.action_verify_enable), error = err, onDismiss = onDismiss, onConfirm = {
        if (Crypto.verifyTotp(secret, code)) onConfirm(secret) else err = invalid
    }) {
        Text(stringResource(R.string.otp_step1))
        TextButton(onClick = {
            Session.suspendAutoLock = true
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Crypto.otpAuthUri(secret))))
            } catch (e: ActivityNotFoundException) {
                Session.suspendAutoLock = false
                err = noApp
            }
        }) { Text(stringResource(R.string.otp_open_app)) }
        SelectionContainer {
            Text(secret.chunked(4).joinToString(" "), style = NumberStyle.copy(fontSize = 13.sp))
        }
        Image(
            qr.asImageBitmap(), contentDescription = stringResource(R.string.cd_otp_qr),
            modifier = Modifier.size(200.dp).background(Color.White, RoundedCornerShape(8.dp)).padding(8.dp).align(Alignment.CenterHorizontally),
        )
        Text(stringResource(R.string.otp_step2))
        TextInput(code, { code = it.filter { c -> c.isDigit() }.take(6) }, stringResource(R.string.field_code), keyboard = KeyboardType.NumberPassword)
    }
}

private fun qrBitmap(text: String, size: Int): Bitmap {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    val px = IntArray(size * size) { i -> if (m.get(i % size, i / size)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
}
