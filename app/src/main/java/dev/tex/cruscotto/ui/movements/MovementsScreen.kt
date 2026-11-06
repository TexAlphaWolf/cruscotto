package dev.tex.cruscotto.ui.movements

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.tex.cruscotto.R
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tex.cruscotto.data.Category
import dev.tex.cruscotto.data.Kind
import dev.tex.cruscotto.data.Repository
import dev.tex.cruscotto.data.Txn
import dev.tex.cruscotto.export.rememberYearPdfExport
import dev.tex.cruscotto.domain.contains
import dev.tex.cruscotto.ui.LocalSnackbar
import dev.tex.cruscotto.ui.budget.MonthNav
import dev.tex.cruscotto.ui.budget.TxnRow
import dev.tex.cruscotto.ui.components.CardBody
import dev.tex.cruscotto.ui.components.CardHeader
import dev.tex.cruscotto.ui.components.EmptyState
import dev.tex.cruscotto.ui.components.Gap
import dev.tex.cruscotto.ui.components.GradientBar
import dev.tex.cruscotto.ui.components.KpiRow
import dev.tex.cruscotto.ui.components.ScreenTitle
import dev.tex.cruscotto.ui.components.SectionCard
import dev.tex.cruscotto.ui.components.money
import dev.tex.cruscotto.ui.components.monthShort
import dev.tex.cruscotto.ui.components.pct
import dev.tex.cruscotto.ui.theme.LocalAppColors
import dev.tex.cruscotto.ui.theme.LocalStatus
import dev.tex.cruscotto.ui.theme.NumberStyle
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

private enum class Period { MONTH, YEAR, ALL }

private data class Group(val key: String, val name: String, val sum: Long, val items: List<Txn>)

@Composable
fun MovementsScreen(repo: Repository, month: YearMonth, onMonth: (YearMonth) -> Unit) {
    val cats by repo.categories.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    val status = LocalStatus.current
    val app = LocalAppColors.current
    var period by remember { mutableStateOf(Period.MONTH) }
    var year by remember { mutableIntStateOf(month.year) }
    var type by remember { mutableStateOf(Kind.OUT) }
    var open by remember { mutableStateOf(setOf<String>()) }
    val noCategory = stringResource(R.string.no_category)
    val msgDeleted = stringResource(R.string.msg_txn_deleted)
    val actionUndo = stringResource(R.string.action_undo)

    // load only the chosen period from the database
    val range: Pair<LocalDate, LocalDate>? = when (period) {
        Period.MONTH -> month.atDay(1) to month.atEndOfMonth()
        Period.YEAR -> LocalDate.of(year, 1, 1) to LocalDate.of(year, 12, 31)
        Period.ALL -> null
    }
    val periodTxns by remember(range) {
        if (range == null) repo.txns else repo.txnsBetween(range.first, range.second)
    }.collectAsStateWithLifecycle(emptyList())
    // "All time" chart: the last 12 months
    val now = remember { YearMonth.now() }
    val last12 by remember(period) {
        if (period == Period.ALL) repo.txnsBetween(now.minusMonths(11).atDay(1), now.atEndOfMonth()) else flowOf(emptyList())
    }.collectAsStateWithLifecycle(emptyList())

    val totIn = remember(periodTxns) { periodTxns.filter { it.type == Kind.IN }.sumOf { it.amountCents } }
    val totOut = remember(periodTxns) { periodTxns.filter { it.type == Kind.OUT }.sumOf { it.amountCents } }
    val groups = remember(periodTxns, cats, type) { groupByCategory(periodTxns.filter { it.type == type }, cats.filter { it.kind == type }, noCategory) }
    val total = groups.sumOf { it.sum }
    val max = groups.maxOfOrNull { it.sum } ?: 1L
    val chartMonths: List<YearMonth>? = remember(period, year) {
        when (period) {
            Period.MONTH -> null
            Period.YEAR -> (1..12).map { YearMonth.of(year, it) }
            Period.ALL -> (11 downTo 0).map { now.minusMonths(it.toLong()) }
        }
    }
    val chartTxns = if (period == Period.ALL) last12 else periodTxns
    val exportPdf = rememberYearPdfExport(repo)

    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item(key = "title") { ScreenTitle(stringResource(R.string.tab_movements)); Gap() }
        item(key = "period") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(period == Period.MONTH, { period = Period.MONTH }, label = { Text(stringResource(R.string.period_month)) })
                FilterChip(period == Period.YEAR, { period = Period.YEAR }, label = { Text(stringResource(R.string.period_year)) })
                FilterChip(period == Period.ALL, { period = Period.ALL }, label = { Text(stringResource(R.string.period_all)) })
            }
        }
        item(key = "nav") {
            when (period) {
                Period.MONTH -> MonthNav(month, onMonth)
                Period.YEAR -> MonthNav(stringResource(R.string.period_year_n, year), { year-- }, { year++ })
                Period.ALL -> MonthNav(stringResource(R.string.period_all_history), null, null)
            }
            if (period == Period.YEAR) {
                OutlinedButton(onClick = { exportPdf(year) }, modifier = Modifier.padding(bottom = 12.dp)) {
                    Text(stringResource(R.string.action_pdf_year, year))
                }
            }
        }
        item(key = "kpi") {
            KpiRow(
                Triple(stringResource(R.string.kpi_income), money(totIn), status.ok),
                Triple(stringResource(R.string.kpi_expenses), money(totOut), null),
                Triple(stringResource(R.string.kpi_difference), money(totIn - totOut), if (totIn - totOut < 0) status.bad else status.ok),
            )
            Gap()
        }
        if (chartMonths != null) {
            item(key = "chart") {
                SectionCard(title = stringResource(R.string.mov_monthly_trend), trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        LegendDot(status.income, stringResource(R.string.kpi_income)); LegendDot(status.expense, stringResource(R.string.kpi_expenses))
                    }
                }) {
                    MonthlyChart(chartMonths, chartTxns, status.income, status.expense)
                }
                Gap()
            }
        }
        item(key = "cat-header", contentType = "cardHeader") {
            CardHeader(stringResource(R.string.mov_by_category)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(type == Kind.OUT, { type = Kind.OUT }, label = { Text(stringResource(R.string.kpi_expenses)) })
                    FilterChip(type == Kind.IN, { type = Kind.IN }, label = { Text(stringResource(R.string.kpi_income)) })
                }
            }
        }
        if (groups.isEmpty()) {
            item(key = "cat-empty", contentType = "cardBody") {
                CardBody(last = true) { EmptyState(stringResource(if (type == Kind.OUT) R.string.mov_no_expenses else R.string.mov_no_income)) }
            }
        }
        groups.forEachIndexed { i, g ->
            val key = "$type:${g.key}"
            val isOpen = key in open
            val lastGroup = i == groups.lastIndex
            item(key = "g:$key", contentType = "group") {
                CardBody(last = lastGroup && !isOpen) {
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(
                        Modifier.fillMaxWidth().clickable { open = if (isOpen) open - key else open + key }.padding(vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (isOpen) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = stringResource(if (isOpen) R.string.cd_collapse else R.string.cd_expand), tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(g.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(money(g.sum), style = NumberStyle.copy(fontSize = 14.sp))
                        }
                        GradientBar(g.sum.toFloat() / max, if (type == Kind.OUT) app.barExpense else app.barIncome)
                        Text(
                            stringResource(R.string.mov_share_of_total, pct(if (total > 0) g.sum * 100.0 / total else 0.0)) + " · " +
                                pluralStringResource(R.plurals.mov_count, g.items.size, g.items.size),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (isOpen) {
                itemsIndexed(g.items, key = { _, t -> "t:${t.id}" }, contentType = { _, _ -> "txn" }) { j, t ->
                    CardBody(last = lastGroup && j == g.items.lastIndex) {
                        Column(Modifier.padding(start = 12.dp)) {
                            TxnRow(t, cats, showYear = period != Period.MONTH, divider = j != g.items.lastIndex, onDelete = {
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
        }
    }
}

private fun groupByCategory(list: List<Txn>, cats: List<Category>, noCategory: String): List<Group> {
    val ids = cats.associateBy { it.id }
    return list.groupBy { if (it.categoryId in ids) it.categoryId else "_none" }
        .map { (k, items) ->
            Group(k, ids[k]?.name ?: noCategory, items.sumOf { it.amountCents }, items.sortedByDescending { it.epochDay })
        }
        .sortedByDescending { it.sum }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Side-by-side income/expense bars per month, on a grid of round values. */
@Composable
private fun MonthlyChart(months: List<YearMonth>, txns: List<Txn>, inColor: Color, outColor: Color) {
    val data = remember(months, txns) {
        // one pass over the transactions instead of one filter per month
        val inc = HashMap<YearMonth, Long>(); val out = HashMap<YearMonth, Long>()
        for (t in txns) {
            val m = YearMonth.from(LocalDate.ofEpochDay(t.epochDay))
            val map = if (t.type == Kind.IN) inc else out
            map[m] = (map[m] ?: 0L) + t.amountCents
        }
        months.map { m -> Triple(m, inc[m] ?: 0L, out[m] ?: 0L) }
    }
    val maxV = (data.maxOfOrNull { maxOf(it.second, it.third) } ?: 0L).coerceAtLeast(100L) / 100.0
    val pow10 = 10.0.pow(floor(log10(maxV)))
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * pow10 }.first { maxV / it <= 4 }
    val top = ceil(maxV / step) * step
    val measurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val nowMonth = YearMonth.now()
    val strongColor = MaterialTheme.colorScheme.onSurface

    Canvas(Modifier.fillMaxWidth().height(200.dp)) {
        val left = 44.dp.toPx()
        val bottom = 22.dp.toPx()
        val topPad = 6.dp.toPx()
        val plotW = size.width - left
        val plotH = size.height - bottom - topPad
        val labelStyle = TextStyle(fontSize = 10.sp, color = labelColor)
        fun y(v: Double) = topPad + plotH * (1 - v / top).toFloat()

        var v = 0.0
        while (v <= top + 1e-9) {
            val yy = y(v)
            drawLine(gridColor, Offset(left, yy), Offset(size.width, yy), strokeWidth = 1f)
            drawLabel(measurer, compact(v), labelStyle, Offset(0f, yy - 7.dp.toPx()), left - 6.dp.toPx())
            v += step
        }
        val slot = plotW / data.size
        val barW = slot * 0.32f
        data.forEachIndexed { i, (m, inc, out) ->
            val x0 = left + i * slot + slot * 0.15f
            val hIn = plotH * (inc / 100.0 / top).toFloat()
            val hOut = plotH * (out / 100.0 / top).toFloat()
            drawRoundRect(inColor, Offset(x0, topPad + plotH - hIn), Size(barW, hIn), CornerRadius(6f, 6f))
            drawRoundRect(outColor, Offset(x0 + barW + 2f, topPad + plotH - hOut), Size(barW, hOut), CornerRadius(6f, 6f))
            val isNow = m == nowMonth
            val st = TextStyle(fontSize = 10.sp, color = if (isNow) strongColor else labelColor, fontWeight = if (isNow) FontWeight.Bold else FontWeight.Normal)
            val res = measurer.measure(monthShort(m), st)
            drawText(res, topLeft = Offset(left + i * slot + (slot - res.size.width) / 2f, size.height - res.size.height))
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLabel(m: TextMeasurer, text: String, style: TextStyle, at: Offset, rightEdge: Float) {
    val res = m.measure(text, style)
    drawText(res, topLeft = Offset(rightEdge - res.size.width, at.y))
}

private fun compact(euros: Double): String {
    val nf = java.text.NumberFormat.getNumberInstance(java.util.Locale.getDefault()).apply { maximumFractionDigits = 1 }
    return if (euros >= 1000) nf.format(euros / 1000) + "k €" else nf.format(euros) + " €"
}
