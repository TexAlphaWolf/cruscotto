package dev.tex.cruscotto.ui.budget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tex.cruscotto.R
import dev.tex.cruscotto.data.Category
import dev.tex.cruscotto.data.Kind
import dev.tex.cruscotto.data.Txn
import dev.tex.cruscotto.ui.components.DateInput
import dev.tex.cruscotto.ui.components.Dropdown
import dev.tex.cruscotto.ui.components.FormDialog
import dev.tex.cruscotto.ui.components.TextInput
import dev.tex.cruscotto.ui.components.fmtNumeric
import dev.tex.cruscotto.ui.components.fmtShort
import dev.tex.cruscotto.ui.components.monthTitle
import dev.tex.cruscotto.ui.components.parseMoney
import dev.tex.cruscotto.ui.components.signedMoney
import dev.tex.cruscotto.ui.theme.LocalStatus
import dev.tex.cruscotto.ui.theme.NumberStyle
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun MonthNav(label: String, onPrev: (() -> Unit)?, onNext: (() -> Unit)?, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (onPrev != null) IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.cd_previous)) }
        Text(label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 4.dp))
        if (onNext != null) IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.cd_next)) }
    }
}

@Composable
fun MonthNav(month: YearMonth, onMonth: (YearMonth) -> Unit, modifier: Modifier = Modifier) =
    MonthNav(monthTitle(month), { onMonth(month.minusMonths(1)) }, { onMonth(month.plusMonths(1)) }, modifier)

@Composable
fun categoryName(t: Txn, cats: List<Category>): String =
    cats.firstOrNull { it.id == t.categoryId && it.kind == t.type }?.name ?: stringResource(R.string.no_category)

@Composable
fun TxnRow(t: Txn, cats: List<Category>, showYear: Boolean, divider: Boolean = true, onDelete: () -> Unit) {
    val income = t.type == Kind.IN
    val catName = categoryName(t, cats)
    val meta = buildList {
        if (income) add(stringResource(R.string.txn_income))
        add(catName)
        if (t.serviceLogId != null) add(stringResource(R.string.txn_from_garage))
    }.joinToString(" · ")
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (showYear) fmtNumeric(t.epochDay) else fmtShort(t.epochDay),
                style = NumberStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Normal),
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(66.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(t.note.ifBlank { catName }, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                signedMoney(t.amountCents, income), style = NumberStyle.copy(fontSize = 14.sp),
                color = if (income) LocalStatus.current.ok else MaterialTheme.colorScheme.onSurface,
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_delete_txn), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (divider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** New transaction: expense (category with a share) or income (income category). */
@Composable
fun TxnDialog(cats: List<Category>, month: YearMonth, onSave: (Txn) -> Unit, onDismiss: () -> Unit) {
    var type by remember { mutableStateOf(Kind.OUT) }
    var amount by remember { mutableStateOf("") }
    val today = LocalDate.now()
    var day by remember { mutableLongStateOf((if (YearMonth.from(today) == month) today else month.atDay(1)).toEpochDay()) }
    var catId by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val options = cats.filter { it.kind == type }.sortedBy { it.sort }
    val selected = options.firstOrNull { it.id == catId } ?: options.firstOrNull()
    val errAmount = stringResource(R.string.err_amount_positive)
    val errNoCat = stringResource(R.string.err_no_category)

    FormDialog(
        title = stringResource(R.string.txn_new),
        confirmLabel = stringResource(R.string.action_add),
        error = error,
        onDismiss = onDismiss,
        onConfirm = {
            val cents = parseMoney(amount)
            when {
                cents == null || cents <= 0 -> error = errAmount
                selected == null -> error = errNoCat
                else -> onSave(Txn(type = type, amountCents = cents, epochDay = day, categoryId = selected.id, note = note.trim()))
            }
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = type == Kind.OUT, onClick = { type = Kind.OUT; catId = null }, label = { Text(stringResource(R.string.txn_expense)) })
            FilterChip(selected = type == Kind.IN, onClick = { type = Kind.IN; catId = null }, label = { Text(stringResource(R.string.txn_income)) })
        }
        TextInput(amount, { amount = it }, stringResource(R.string.field_amount), keyboard = KeyboardType.Decimal, placeholder = "0,00")
        DateInput(stringResource(R.string.field_date), day, { it?.let { d -> day = d } })
        Dropdown(stringResource(R.string.field_category), options, selected, { it.name }, { catId = it.id })
        TextInput(
            note, { note = it }, stringResource(R.string.field_description),
            placeholder = stringResource(if (type == Kind.IN) R.string.hint_income_note else R.string.hint_expense_note),
        )
    }
}
