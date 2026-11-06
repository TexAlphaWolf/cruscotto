package dev.tex.cruscotto.ui.budget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tex.cruscotto.R
import dev.tex.cruscotto.data.Repository
import dev.tex.cruscotto.prefs.AppPrefs
import dev.tex.cruscotto.yearend.YearEnd
import dev.tex.cruscotto.data.Txn
import dev.tex.cruscotto.export.rememberYearPdfExport
import dev.tex.cruscotto.domain.CategoryRow
import dev.tex.cruscotto.domain.Level
import dev.tex.cruscotto.domain.computeMonth
import dev.tex.cruscotto.ui.LocalSnackbar
import dev.tex.cruscotto.ui.components.CardBody
import dev.tex.cruscotto.ui.components.CardHeader
import dev.tex.cruscotto.ui.components.EmptyState
import dev.tex.cruscotto.ui.components.Gap
import dev.tex.cruscotto.ui.components.GradientBar
import dev.tex.cruscotto.ui.components.KpiRow
import dev.tex.cruscotto.ui.components.Notice
import dev.tex.cruscotto.ui.components.SectionCard
import dev.tex.cruscotto.ui.components.Tag
import dev.tex.cruscotto.ui.components.money
import dev.tex.cruscotto.ui.components.monthTitle
import dev.tex.cruscotto.ui.components.pct
import dev.tex.cruscotto.ui.theme.LocalAppColors
import dev.tex.cruscotto.ui.theme.LocalStatus
import dev.tex.cruscotto.ui.theme.NumberStyle
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun BudgetScreen(repo: Repository, month: YearMonth, onMonth: (YearMonth) -> Unit) {
    val settings by repo.settings.collectAsStateWithLifecycle(null)
    val cats by repo.categories.collectAsStateWithLifecycle(emptyList())
    // only this month's transactions are loaded
    val txns by remember(month) { repo.txnsBetween(month.atDay(1), month.atEndOfMonth()) }.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    val status = LocalStatus.current
    var showAdd by remember { mutableStateOf(false) }
    val msgDeleted = stringResource(R.string.msg_txn_deleted)
    val actionUndo = stringResource(R.string.action_undo)

    val exportPdf = rememberYearPdfExport(repo)
    val bm = remember(month, txns, cats, settings) { computeMonth(month, txns, cats, settings?.salaryCents ?: 0) }
    val balance = bm.baseCents - bm.outCents

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        ) {
            item(key = "nav", contentType = "nav") { MonthNav(month, onMonth) }
            yearEndNotices(onPdf = exportPdf)
            item(key = "kpi", contentType = "kpi") {
                KpiRow(
                    Triple(stringResource(if (bm.usesSalary) R.string.kpi_salary else R.string.kpi_income), money(bm.baseCents), null),
                    Triple(stringResource(R.string.kpi_expenses), money(bm.outCents), null),
                    Triple(stringResource(R.string.kpi_balance), money(balance), if (balance < 0) status.bad else status.ok),
                )
                Gap()
            }
            if ((settings?.salaryCents ?: 0) == 0L && bm.incomeCents == 0L) {
                item { Notice(Level.OK) { Text(stringResource(R.string.budget_need_salary)) }; Gap() }
            }
            if (bm.totalOverCents > 0) {
                item {
                    Notice(if (bm.deficitCents > 0) Level.BAD else Level.WARN) {
                        Text(stringResource(R.string.budget_overspend, money(bm.totalOverCents)), fontWeight = FontWeight.Bold)
                        val overs = bm.rows.filter { it.overCents > 0 }.joinToString(", ") { "${it.category.name} +${money(it.overCents)}" }
                        if (overs.isNotEmpty()) Text(overs)
                        if (bm.orphanCents > 0) Text(stringResource(R.string.budget_uncategorized, money(bm.orphanCents)))
                        val from = bm.rows.filter { it.takenCents > 0 }.joinToString(", ") { "${it.category.name} (−${money(it.takenCents)})" }
                        if (from.isNotEmpty()) Text(stringResource(R.string.budget_covered_by, from))
                        if (bm.deficitCents > 0) Text(stringResource(R.string.budget_deficit, money(bm.deficitCents)), fontWeight = FontWeight.Bold)
                    }
                    Gap()
                }
            }
            item(key = "split", contentType = "card") {
                SectionCard(title = stringResource(R.string.budget_split)) {
                    if (bm.rows.isEmpty()) EmptyState(stringResource(R.string.budget_no_categories))
                    bm.rows.forEachIndexed { i, r ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        CategoryBudgetRow(r)
                    }
                }
                Gap()
            }
            // the month's transactions as lazy rows: only the visible ones are composed
            item(key = "txh", contentType = "cardHeader") { CardHeader(stringResource(R.string.budget_month_txns, monthTitle(month))) }
            if (bm.txns.isEmpty()) {
                item(key = "txempty", contentType = "cardBody") { CardBody(last = true) { EmptyState(stringResource(R.string.budget_no_txns)) } }
            } else {
                itemsIndexed(bm.txns, key = { _, t -> t.id }, contentType = { _, _ -> "txn" }) { i, t ->
                    CardBody(last = i == bm.txns.lastIndex) {
                        TxnRow(t, cats, showYear = false, divider = i != bm.txns.lastIndex, onDelete = {
                            scope.launch {
                                repo.deleteTxn(t)
                                val r = snackbar.showSnackbar(msgDeleted, actionLabel = actionUndo, withDismissAction = true)
                                if (r == SnackbarResult.ActionPerformed) repo.restoreTxn(t)
                            }
                        })
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { showAdd = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.fab_txn)) },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (showAdd) {
        TxnDialog(cats, month, onSave = { t: Txn ->
            scope.launch { repo.saveTxn(t) }
            showAdd = false
        }, onDismiss = { showAdd = false })
    }
}

@Composable
private fun CategoryBudgetRow(r: CategoryRow) {
    val ratio = if (r.effectiveCents > 0) r.spentCents.toFloat() / r.effectiveCents else if (r.spentCents > 0) 1f else 0f
    val app = LocalAppColors.current
    val brush = when {
        r.overCents > 0 -> app.barBad
        ratio >= 0.85f -> app.barWarn
        else -> app.barOk
    }
    val changed = kotlin.math.abs(r.effectivePct - r.category.pct) > 0.05
    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(r.category.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (r.category.isBuffer) Tag(stringResource(R.string.tag_buffer, r.category.bufferOrder))
            if (r.overCents > 0) Tag(stringResource(R.string.tag_over))
            if (r.takenCents > 0) Tag(stringResource(R.string.tag_reduced))
        }
        GradientBar(ratio, brush)
        Row {
            val share = if (changed) stringResource(R.string.budget_share_changed, pct(r.category.pct), pct(r.effectivePct))
                else stringResource(R.string.budget_share, pct(r.category.pct))
            Text(share, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("${money(r.spentCents)} / ${money(r.effectiveCents)}", style = NumberStyle.copy(fontSize = 12.sp))
        }
        Text(stringResource(R.string.budget_left, money(r.remainingCents)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** December reminder before the yearly clean-up, and a one-time notice after it ran. */
private fun LazyListScope.yearEndNotices(onPdf: (Int) -> Unit) {
    val today = LocalDate.now()
    if (AppPrefs.purgeEnabled && YearEnd.isReminderPeriod(today)) {
        val final = YearEnd.isFinalPeriod(today)
        item(key = "yearend", contentType = "notice") {
            Notice(if (final) Level.BAD else Level.WARN) {
                Text(
                    stringResource(if (final) R.string.yearend_banner_final_title else R.string.yearend_banner_title, today.year),
                    fontWeight = FontWeight.Bold,
                )
                Text(stringResource(R.string.yearend_banner_text))
                OutlinedButton(onClick = { onPdf(today.year) }) { Text(stringResource(R.string.action_pdf_year, today.year)) }
            }
            Gap()
        }
    }
    val purged = AppPrefs.purgedNoticeYear
    if (purged > 0) {
        item(key = "purged", contentType = "notice") {
            var visible by remember { mutableStateOf(true) }
            if (visible) {
                Notice(Level.OK) {
                    Text(stringResource(R.string.yearend_purged, purged))
                    TextButton(onClick = { AppPrefs.purgedNoticeYear = 0; visible = false }) { Text(stringResource(R.string.action_ok)) }
                }
                Gap()
            }
        }
    }
}
