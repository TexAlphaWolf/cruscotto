package dev.tex.cruscotto.ui.garage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import dev.tex.cruscotto.data.Deadline
import dev.tex.cruscotto.data.Kind
import dev.tex.cruscotto.data.Repository
import dev.tex.cruscotto.data.ServiceLog
import dev.tex.cruscotto.ui.LocalSnackbar
import dev.tex.cruscotto.ui.components.ConfirmDialog
import dev.tex.cruscotto.ui.components.EmptyState
import dev.tex.cruscotto.ui.components.Label
import dev.tex.cruscotto.ui.components.Plate
import dev.tex.cruscotto.ui.components.SectionCard
import dev.tex.cruscotto.ui.components.StatusChip
import dev.tex.cruscotto.ui.components.fmtDate
import dev.tex.cruscotto.ui.components.fmtKm
import dev.tex.cruscotto.ui.components.money
import dev.tex.cruscotto.ui.components.statusText
import dev.tex.cruscotto.ui.components.typeLabel
import dev.tex.cruscotto.ui.theme.NumberStyle
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CarDetailScreen(repo: Repository, carId: String, onBack: () -> Unit) {
    val cars by repo.cars.collectAsStateWithLifecycle(null)
    // only this car's deadlines and history
    val deadlines by remember(carId) { repo.deadlinesOfCar(carId) }.collectAsStateWithLifecycle(emptyList())
    val logs by remember(carId) { repo.serviceLogsOfCar(carId) }.collectAsStateWithLifecycle(emptyList())
    val cats by repo.categories.collectAsStateWithLifecycle(emptyList())
    val settings by repo.settings.collectAsStateWithLifecycle(null)
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current

    val car = cars?.firstOrNull { it.id == carId }
    // car deleted (or list loaded without it): go back
    LaunchedEffect(cars, carId) { if (cars != null && car == null) onBack() }
    if (car == null) return

    var editCar by remember { mutableStateOf(false) }
    var editKm by remember { mutableStateOf(false) }
    var addDeadline by remember { mutableStateOf(false) }
    var addLog by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf<Deadline?>(null) }
    var deletingDeadline by remember { mutableStateOf<Deadline?>(null) }
    var showAllLogs by remember { mutableStateOf(false) }
    val msgLogDeleted = stringResource(R.string.msg_log_deleted)
    val msgRenewed = stringResource(R.string.msg_log_renewed)
    val msgClosed = stringResource(R.string.msg_log_closed)
    val actionUndo = stringResource(R.string.action_undo)

    val myDeadlines = remember(deadlines, car) { deadlineViews(deadlines, listOf(car)) }
    val myLogs = logs
    val year = LocalDate.now().year
    val yearCost = myLogs.filter { LocalDate.ofEpochDay(it.epochDay).year == year }.sumOf { it.costCents }
    val outCats = cats.filter { it.kind == Kind.OUT }.sortedBy { it.sort }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back)) }
                Text(car.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { editCar = true }) { Text(stringResource(R.string.action_edit)) }
            }
        }
        item {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Plate(car.plate)
                        Text(carSubtitle(car), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Label(stringResource(R.string.odometer))
                        Odometer(car.km)
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(onClick = { editKm = true }) { Text(stringResource(R.string.km_update)) }
                    OutlinedButton(onClick = { addDeadline = true }) { Text(stringResource(R.string.action_add_deadline)) }
                    Button(onClick = { addLog = true }) { Text(stringResource(R.string.action_add_log)) }
                }
            }
        }
        item {
            SectionCard(title = stringResource(R.string.deadlines)) {
                if (myDeadlines.isEmpty()) EmptyState(stringResource(R.string.deadlines_empty))
                myDeadlines.forEachIndexed { i, v ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    val d = v.deadline
                    Column(Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(typeLabel(d.type), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            StatusChip(v.status.level, statusText(v.status))
                        }
                        val extra = listOfNotNull(
                            dueLine(d).ifBlank { null },
                            if (d.recurMonths > 0) stringResource(R.string.every_months, d.recurMonths) else null,
                            if (d.recurKm > 0) stringResource(R.string.every_km, fmtKm(d.recurKm)) else null,
                            if (d.costCents > 0) "~${money(d.costCents)}" else null,
                        ).joinToString(" · ")
                        Text(extra, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { closing = d }) { Text(stringResource(R.string.action_done_renew)) }
                            TextButton(onClick = { deletingDeadline = d }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
        item {
            SectionCard(title = stringResource(R.string.service_history), trailing = {
                Text("$year: ${money(yearCost)}", style = NumberStyle.copy(fontSize = 12.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }) {
                if (myLogs.isEmpty()) EmptyState(stringResource(R.string.service_history_empty))
                val shown = if (showAllLogs) myLogs else myLogs.take(6)
                shown.forEachIndexed { i, l ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    LogRow(l, onDelete = {
                        scope.launch {
                            val txn = repo.deleteServiceLog(l)
                            val r = snackbar.showSnackbar(msgLogDeleted, actionLabel = actionUndo, withDismissAction = true)
                            if (r == SnackbarResult.ActionPerformed) repo.restoreServiceLog(l, txn)
                        }
                    })
                }
                if (myLogs.size > 6) TextButton(onClick = { showAllLogs = !showAllLogs }) {
                    Text(if (showAllLogs) stringResource(R.string.action_show_less) else stringResource(R.string.action_show_all, myLogs.size))
                }
            }
        }
    }

    val defaultCat = settings?.carCategoryId ?: "car"
    if (editCar) CarDialog(car, onSave = { c -> scope.launch { repo.saveCar(c) }; editCar = false },
        onDelete = { editCar = false; scope.launch { repo.deleteCar(car.id) } }, onDismiss = { editCar = false })
    if (editKm) KmDialog(car, onSave = { km -> scope.launch { repo.saveCar(car.copy(km = km)) }; editKm = false }, onDismiss = { editKm = false })
    if (addDeadline) DeadlineDialog(car.id, onSave = { d -> scope.launch { repo.saveDeadline(d) }; addDeadline = false }, onDismiss = { addDeadline = false })
    if (addLog) ServiceLogDialog(car, null, outCats, defaultCat, onSave = { l, cat, note -> scope.launch { repo.addServiceLog(l, cat, null, note) }; addLog = false }, onDismiss = { addLog = false })
    closing?.let { d ->
        ServiceLogDialog(car, d, outCats, defaultCat, onSave = { l, cat, note ->
            scope.launch { repo.addServiceLog(l, cat, d, note); snackbar.showSnackbar(if (d.recurMonths > 0 || d.recurKm > 0) msgRenewed else msgClosed) }
            closing = null
        }, onDismiss = { closing = null })
    }
    deletingDeadline?.let { d ->
        ConfirmDialog(stringResource(R.string.deadline_delete_title), stringResource(R.string.deadline_delete_text, typeLabel(d.type), car.name), stringResource(R.string.action_delete),
            onConfirm = { scope.launch { repo.deleteDeadline(d.id) } }, onDismiss = { deletingDeadline = null })
    }
}

@Composable
private fun LogRow(l: ServiceLog, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(typeLabel(l.type) + if (l.description.isNotBlank()) " · ${l.description}" else "", fontWeight = FontWeight.SemiBold)
            Text(
                listOfNotNull(
                    fmtDate(l.epochDay), l.km?.let(::fmtKm), l.place.ifBlank { null },
                    if (l.txnId != null) stringResource(R.string.log_in_budget) else null,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(money(l.costCents), style = NumberStyle.copy(fontSize = 14.sp))
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_delete_log), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
