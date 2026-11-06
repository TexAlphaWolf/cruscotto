package dev.tex.cruscotto.ui.components

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import dev.tex.cruscotto.R
import dev.tex.cruscotto.domain.Level
import dev.tex.cruscotto.ui.theme.LocalAppColors
import dev.tex.cruscotto.ui.theme.LocalStatus
import dev.tex.cruscotto.ui.theme.NumberStyle
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle as JTextStyle
import java.util.Currency
import java.util.Locale

// ---------- formats (follow the phone's language; amounts are always in euro) ----------
private fun locale(): Locale = Locale.getDefault()

fun money(cents: Long): String =
    NumberFormat.getCurrencyInstance(locale()).apply { currency = Currency.getInstance("EUR") }.format(cents / 100.0)

fun signedMoney(cents: Long, income: Boolean): String = (if (income) "+" else "−") + money(cents)

/** "12,50" / "12.50" / "1.250,00" / "1,250.00" → cents. Null when invalid or negative. */
fun parseMoney(text: String): Long? {
    var t = text.trim().replace("€", "").replace(" ", "").replace("\u00A0", "")
    if (t.isEmpty()) return null
    val lastComma = t.lastIndexOf(',')
    val lastDot = t.lastIndexOf('.')
    t = when {
        lastComma >= 0 && lastDot >= 0 ->
            if (lastComma > lastDot) t.replace(".", "").replace(',', '.') else t.replace(",", "")
        lastComma >= 0 -> t.replace(',', '.')
        else -> t
    }
    val v = t.toBigDecimalOrNull() ?: return null
    if (v.signum() < 0) return null
    return v.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).toLong()
}

/** Amount for an input field, using the phone's decimal separator. */
fun centsToInput(cents: Long): String = if (cents == 0L) "" else String.format(locale(), "%.2f", cents / 100.0)

fun pct(v: Double): String {
    val nf = NumberFormat.getNumberInstance(locale()).apply { maximumFractionDigits = 1; minimumFractionDigits = 0 }
    return nf.format(v) + "%"
}

fun fmtKm(km: Long): String = NumberFormat.getIntegerInstance(locale()).format(km) + " km"

fun fmtDate(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale()))

fun fmtShort(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("d MMM", locale()))

fun fmtNumeric(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale()))

fun monthTitle(ym: YearMonth): String =
    ym.month.getDisplayName(JTextStyle.FULL_STANDALONE, locale()).replaceFirstChar { it.titlecase(locale()) } + " " + ym.year

fun monthShort(ym: YearMonth): String = ym.month.getDisplayName(JTextStyle.SHORT_STANDALONE, locale()).take(3)

fun upper(s: String): String = s.uppercase(locale())

fun Context.findActivity(): FragmentActivity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is FragmentActivity) return c
        c = c.baseContext
    }
    return null
}

/** Click without ripple, for the transparent areas laid over read-only fields. */
fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.clickable(interactionSource = null, indication = null, onClick = onClick)

// ---------- building blocks ----------
val CardShape = RoundedCornerShape(20.dp)
val FieldShape = RoundedCornerShape(14.dp)

/** Card with the theme's accent stripe along the top. */
@Composable
fun SectionCard(modifier: Modifier = Modifier, title: String? = null, trailing: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val app = LocalAppColors.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (app.isDark) 0.dp else 2.dp),
        border = app.cardBorder?.let { BorderStroke(1.dp, it) },
    ) {
        Box(Modifier.fillMaxWidth().height(3.dp).background(app.stripe))
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null || trailing != null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (title != null) SectionTitle(title, Modifier.weight(1f))
                    trailing?.invoke()
                }
            }
            content()
        }
    }
}

/**
 * A [SectionCard] split into lazy-list items, so long lists only compose the rows on screen:
 * [CardHeader] first, then any number of [CardBody] rows, the last one with `last = true`.
 */
@Composable
fun CardHeader(title: String, trailing: (@Composable () -> Unit)? = null, content: (@Composable ColumnScope.() -> Unit)? = null) {
    val app = LocalAppColors.current
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surface)
            .then(if (app.cardBorder != null) Modifier.sideBorders(app.cardBorder, top = true, bottom = false) else Modifier),
    ) {
        Box(Modifier.fillMaxWidth().height(3.dp).background(app.stripe))
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(title, Modifier.weight(1f))
                trailing?.invoke()
            }
            content?.invoke(this)
        }
    }
}

@Composable
fun CardBody(last: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val app = LocalAppColors.current
    val shape = if (last) RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp) else RoundedCornerShape(0.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surface)
            .then(if (app.cardBorder != null) Modifier.sideBorders(app.cardBorder, top = false, bottom = last) else Modifier)
            .padding(start = 18.dp, end = 18.dp, bottom = if (last) 12.dp else 0.dp),
        content = content,
    )
}

/** 1-px border on the left and right (and optionally top/bottom) edges, for card pieces in the dark theme. */
private fun Modifier.sideBorders(color: Color, top: Boolean, bottom: Boolean): Modifier = drawBehind {
    val w = 1.dp.toPx()
    drawRect(color, size = Size(w, size.height))
    drawRect(color, topLeft = androidx.compose.ui.geometry.Offset(size.width - w, 0f), size = Size(w, size.height))
    if (top) drawRect(color, size = Size(size.width, w))
    if (bottom) drawRect(color, topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - w), size = Size(size.width, w))
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(upper(text), modifier = modifier, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.8.sp))
}

/** Vertical space between the blocks of a screen. */
@Composable
fun Gap() = Spacer(Modifier.height(12.dp))

/** Screen title in the display serif. */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.headlineSmall)
}

@Composable
fun Label(text: String, modifier: Modifier = Modifier) {
    Text(upper(text), modifier = modifier, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun Kpi(label: String, value: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
        Label(label)
        Text(value, style = NumberStyle.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold), color = color, maxLines = 1)
    }
}

/** Summary tiles on the theme's gradient (light) or on dark grey with an orange edge (dark). */
@Composable
fun KpiRow(vararg items: Triple<String, String, Color?>) {
    val app = LocalAppColors.current
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(app.hero)
            .then(if (app.heroBorder != null) Modifier.border(1.dp, app.heroBorder, shape) else Modifier)
            .drawBehind { if (app.isDark) drawRect(app.stripe, size = Size(4.dp.toPx(), size.height)) }
            .padding(horizontal = 6.dp, vertical = 8.dp),
    ) {
        items.forEach { (l, v, c) -> Kpi(l, v, Modifier.weight(1f), c ?: MaterialTheme.colorScheme.onSurface) }
    }
}

/** Progress bar filled with a gradient. [progress] is clamped to 0..1. */
@Composable
fun GradientBar(progress: Float, brush: Brush, modifier: Modifier = Modifier) {
    val app = LocalAppColors.current
    Box(modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(app.track)) {
        val p = progress.coerceIn(0f, 1f)
        if (p > 0f) Box(Modifier.fillMaxWidth(p).fillMaxHeight().clip(RoundedCornerShape(50)).background(brush))
    }
}

@Composable
fun StatusChip(level: Level, text: String) {
    val s = LocalStatus.current
    val (fg, bg) = when (level) {
        Level.OK -> s.ok to s.okSoft
        Level.WARN -> s.warn to s.warnSoft
        Level.BAD -> s.bad to s.badSoft
    }
    Text(
        text, color = fg, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.background(bg, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun Tag(text: String) {
    Text(
        upper(text), color = if (LocalAppColors.current.isDark) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onPrimaryContainer,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, letterSpacing = 1.sp),
        modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun Notice(level: Level, content: @Composable ColumnScope.() -> Unit) {
    val s = LocalStatus.current
    val bg = when (level) { Level.OK -> MaterialTheme.colorScheme.primaryContainer; Level.WARN -> s.warnSoft; Level.BAD -> s.badSoft }
    Column(
        Modifier.fillMaxWidth().background(bg, RoundedCornerShape(16.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

@Composable
fun EmptyState(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Number plate with the blue "I" band, like Italian plates. */
@Composable
fun Plate(plate: String) {
    Row(
        Modifier.background(Color.White, RoundedCornerShape(4.dp)).padding(1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("I", color = Color.White, fontSize = 9.sp, modifier = Modifier.background(Color(0xFF1F3F9E), RoundedCornerShape(3.dp)).padding(horizontal = 3.dp, vertical = 4.dp))
        Text(upper(plate.ifBlank { "—" }), color = Color(0xFF111111), style = NumberStyle.copy(fontSize = 13.sp, letterSpacing = 1.5.sp), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

// ---------- form fields ----------
@Composable
fun TextInput(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, keyboard: KeyboardType = KeyboardType.Text, placeholder: String? = null, singleLine: Boolean = true) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, modifier = modifier.fillMaxWidth(),
        singleLine = singleLine, keyboardOptions = KeyboardOptions(keyboardType = keyboard), shape = FieldShape,
        placeholder = placeholder?.let { { Text(it) } },
    )
}

@Composable
fun <T> Dropdown(label: String, options: List<T>, selected: T?, display: @Composable (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selected?.let { display(it) } ?: "", onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { IconButton(onClick = { open = true }) { Icon(Icons.Filled.ArrowDropDown, contentDescription = stringResource(R.string.cd_open_list)) } },
            modifier = Modifier.fillMaxWidth(), shape = FieldShape,
        )
        // transparent layer over the field: tapping anywhere opens the menu
        Box(Modifier.matchParentSize().padding(top = 8.dp).clickableNoRipple { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o ->
                DropdownMenuItem(text = { Text(display(o)) }, onClick = { onSelect(o); open = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateInput(label: String, epochDay: Long?, onChange: (Long?) -> Unit, modifier: Modifier = Modifier, allowEmpty: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = epochDay?.let(::fmtDate) ?: "", onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { IconButton(onClick = { open = true }) { Icon(Icons.Filled.DateRange, contentDescription = stringResource(R.string.cd_pick_date)) } },
            modifier = Modifier.fillMaxWidth(), shape = FieldShape,
        )
        Box(Modifier.matchParentSize().padding(top = 8.dp).clickableNoRipple { open = true })
    }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = (epochDay ?: LocalDate.now().toEpochDay()) * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms -> onChange(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()) }
                    open = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                Row {
                    if (allowEmpty) TextButton(onClick = { onChange(null); open = false }) { Text(stringResource(R.string.action_no_date)) }
                    TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) }
                }
            },
        ) { DatePicker(state = state) }
    }
}

/** Dialog with scrollable content and Save/Cancel buttons. */
@Composable
fun FormDialog(
    title: String,
    confirmLabel: String = stringResource(R.string.action_save),
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    error: String? = null,
    confirmEnabled: Boolean = true,
    extraAction: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                content()
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = confirmEnabled) { Text(confirmLabel) } },
        dismissButton = {
            Row {
                extraAction?.invoke()
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmLabel, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
