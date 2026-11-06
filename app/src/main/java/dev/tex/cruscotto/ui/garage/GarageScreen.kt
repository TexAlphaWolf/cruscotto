package dev.tex.cruscotto.ui.garage

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tex.cruscotto.R
import dev.tex.cruscotto.data.Car
import dev.tex.cruscotto.data.Deadline
import dev.tex.cruscotto.data.Repository
import dev.tex.cruscotto.domain.DeadlineStatus
import dev.tex.cruscotto.domain.Level
import dev.tex.cruscotto.domain.deadlineStatus
import dev.tex.cruscotto.ui.components.CardShape
import dev.tex.cruscotto.ui.components.EmptyState
import dev.tex.cruscotto.ui.components.Notice
import dev.tex.cruscotto.ui.components.Plate
import dev.tex.cruscotto.ui.components.SectionCard
import dev.tex.cruscotto.ui.components.ScreenTitle
import dev.tex.cruscotto.ui.components.StatusChip
import dev.tex.cruscotto.ui.components.fmtDate
import dev.tex.cruscotto.ui.components.fmtKm
import dev.tex.cruscotto.ui.components.fuelLabel
import dev.tex.cruscotto.ui.components.statusText
import dev.tex.cruscotto.ui.components.typeLabel
import dev.tex.cruscotto.ui.theme.LocalAppColors
import dev.tex.cruscotto.ui.theme.NumberStyle
import kotlinx.coroutines.launch

data class DeadlineView(val deadline: Deadline, val car: Car, val status: DeadlineStatus)

/** Deadlines sorted: overdue first, then upcoming, then by date. */
fun deadlineViews(deadlines: List<Deadline>, cars: List<Car>): List<DeadlineView> {
    val byId = cars.associateBy { it.id }
    return deadlines.mapNotNull { d -> byId[d.carId]?.let { c -> DeadlineView(d, c, deadlineStatus(d, c)) } }
        .sortedWith(compareByDescending<DeadlineView> { it.status.level.ordinal }.thenBy { it.deadline.dueEpochDay ?: Long.MAX_VALUE })
}

@Composable
fun dueLine(d: Deadline): String = listOfNotNull(
    d.dueEpochDay?.let(::fmtDate),
    d.kmDue?.let { stringResource(R.string.due_at_km, fmtKm(it)) },
).joinToString(" · ")

@Composable
fun carSubtitle(car: Car): String =
    listOfNotNull(car.model.ifBlank { null }, car.year?.toString(), fuelLabel(car.fuel)).joinToString(" · ")

@Composable
fun GarageScreen(repo: Repository, onOpenCar: (String) -> Unit) {
    val cars by repo.cars.collectAsStateWithLifecycle(emptyList())
    val deadlines by repo.deadlines.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    val views = remember(cars, deadlines) { deadlineViews(deadlines, cars) }
    val urgent = views.filter { it.status.level != Level.OK }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { ScreenTitle(stringResource(R.string.tab_garage)) }
            if (urgent.isNotEmpty()) {
                item {
                    Notice(if (urgent.any { it.status.level == Level.BAD }) Level.BAD else Level.WARN) {
                        Text(pluralStringResource(R.plurals.garage_attention, urgent.size, urgent.size), fontWeight = FontWeight.Bold)
                        urgent.take(4).forEach { Text("${typeLabel(it.deadline.type)} · ${it.car.name}: ${statusText(it.status)}") }
                    }
                }
            }
            if (cars.isEmpty()) {
                item { SectionCard { EmptyState(stringResource(R.string.garage_empty)) } }
            }
            items(cars, key = { it.id }) { car ->
                CarCard(car, views.filter { it.car.id == car.id }, onClick = { onOpenCar(car.id) })
            }
            if (views.isNotEmpty()) {
                item {
                    SectionCard(title = stringResource(R.string.garage_upcoming)) {
                        views.take(8).forEachIndexed { i, v ->
                            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${typeLabel(v.deadline.type)} · ${v.car.name}", fontWeight = FontWeight.SemiBold)
                                    Text(dueLine(v.deadline), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                StatusChip(v.status.level, statusText(v.status))
                            }
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { adding = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.fab_car)) },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
    if (adding) {
        CarDialog(null, onSave = { c -> scope.launch { repo.saveCar(c) }; adding = false }, onDelete = null, onDismiss = { adding = false })
    }
}

@Composable
private fun CarCard(car: Car, deadlines: List<DeadlineView>, onClick: () -> Unit) {
    val app = LocalAppColors.current
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (app.isDark) 0.dp else 2.dp),
        border = app.cardBorder?.let { BorderStroke(1.dp, it) },
    ) {
        Box(Modifier.fillMaxWidth().height(3.dp).background(app.stripe))
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(car.name, style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Plate(car.plate)
                        Text(carSubtitle(car), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Odometer(car.km)
            }
            val next = deadlines.firstOrNull()
            if (next != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.garage_next, typeLabel(next.deadline.type)), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    StatusChip(next.status.level, statusText(next.status))
                }
            } else {
                Text(stringResource(R.string.garage_no_deadlines), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Odometer: light digits on a dark plate, with the accent stripe underneath. */
@Composable
fun Odometer(km: Long) {
    val app = LocalAppColors.current
    Column(Modifier.width(IntrinsicSize.Max).clip(RoundedCornerShape(8.dp)).background(Color(0xFF16181B))) {
        Text(
            fmtKm(km),
            style = NumberStyle.copy(fontSize = 14.sp, letterSpacing = 1.5.sp),
            color = Color(0xFFF2F2F2),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
        Box(Modifier.fillMaxWidth().height(2.dp).background(app.stripe))
    }
}

