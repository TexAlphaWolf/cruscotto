package dev.tex.cruscotto.ui.garage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.tex.cruscotto.R
import dev.tex.cruscotto.data.Car
import dev.tex.cruscotto.data.Category
import dev.tex.cruscotto.data.Deadline
import dev.tex.cruscotto.data.Defaults
import dev.tex.cruscotto.data.ServiceLog
import dev.tex.cruscotto.ui.components.DateInput
import dev.tex.cruscotto.ui.components.Dropdown
import dev.tex.cruscotto.ui.components.FormDialog
import dev.tex.cruscotto.ui.components.TextInput
import dev.tex.cruscotto.ui.components.centsToInput
import dev.tex.cruscotto.ui.components.fuelLabel
import dev.tex.cruscotto.ui.components.parseMoney
import dev.tex.cruscotto.ui.components.typeLabel
import java.time.LocalDate

private fun parseLong(s: String): Long? = s.filter { it.isDigit() }.toLongOrNull()
private fun digits(s: String, max: Int = 9) = s.filter { it.isDigit() }.take(max)

@Composable
fun CarDialog(initial: Car?, onSave: (Car) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var plate by remember { mutableStateOf(initial?.plate ?: "") }
    var model by remember { mutableStateOf(initial?.model ?: "") }
    var year by remember { mutableStateOf(initial?.year?.toString() ?: "") }
    var fuel by remember { mutableStateOf(initial?.fuel ?: Defaults.fuels.first()) }
    var km by remember { mutableStateOf(initial?.km?.toString() ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val errName = stringResource(R.string.err_car_name)
    val deleteAction: (@Composable () -> Unit)? = if (onDelete == null) null else {
        @Composable {
            TextButton(onClick = { if (confirmDelete) onDelete() else confirmDelete = true }) {
                Text(stringResource(if (confirmDelete) R.string.car_delete_confirm else R.string.action_delete), color = MaterialTheme.colorScheme.error)
            }
        }
    }

    FormDialog(
        title = stringResource(if (initial == null) R.string.car_new else R.string.car_edit),
        confirmLabel = stringResource(if (initial == null) R.string.action_add else R.string.action_save),
        error = error,
        onDismiss = onDismiss,
        onConfirm = {
            if (name.isBlank()) { error = errName; return@FormDialog }
            val base = initial ?: Car(name = name)
            onSave(base.copy(
                name = name.trim(), plate = plate.uppercase().filter { !it.isWhitespace() }, model = model.trim(),
                year = year.toIntOrNull(), fuel = fuel, km = parseLong(km) ?: 0,
            ))
        },
        extraAction = deleteAction,
    ) {
        TextInput(name, { name = it }, stringResource(R.string.field_car_name), placeholder = stringResource(R.string.hint_car_name))
        TextInput(plate, { plate = it.uppercase() }, stringResource(R.string.field_plate), placeholder = "AB123CD")
        TextInput(model, { model = it }, stringResource(R.string.field_model), placeholder = stringResource(R.string.hint_model))
        TextInput(year, { year = digits(it, 4) }, stringResource(R.string.field_year), keyboard = KeyboardType.Number)
        Dropdown(stringResource(R.string.field_fuel), Defaults.fuels, fuel, { fuelLabel(it) }, { fuel = it })
        TextInput(km, { km = digits(it) }, stringResource(R.string.field_current_km), keyboard = KeyboardType.Number)
        if (confirmDelete) Text(stringResource(R.string.car_delete_warning), color = MaterialTheme.colorScheme.error)
    }
}

@Composable
fun KmDialog(car: Car, onSave: (Long) -> Unit, onDismiss: () -> Unit) {
    var km by remember { mutableStateOf(car.km.toString()) }
    FormDialog(title = stringResource(R.string.km_update), onDismiss = onDismiss, onConfirm = { onSave(parseLong(km) ?: car.km) }) {
        TextInput(km, { km = digits(it) }, stringResource(R.string.field_km_of, car.name), keyboard = KeyboardType.Number)
    }
}

@Composable
fun DeadlineDialog(carId: String, onSave: (Deadline) -> Unit, onDismiss: () -> Unit) {
    var type by remember { mutableStateOf(Defaults.deadlineTypes.first()) }
    var due by remember { mutableStateOf<Long?>(null) }
    var rec by remember { mutableStateOf(Defaults.suggestedRecurMonths[Defaults.deadlineTypes.first()]?.toString() ?: "") }
    var kmDue by remember { mutableStateOf("") }
    var kmRec by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val errNeed = stringResource(R.string.err_deadline_need)

    FormDialog(
        title = stringResource(R.string.deadline_new),
        confirmLabel = stringResource(R.string.action_add),
        error = error,
        onDismiss = onDismiss,
        onConfirm = {
            val kd = parseLong(kmDue)
            if (due == null && kd == null) { error = errNeed; return@FormDialog }
            onSave(Deadline(
                carId = carId, type = type, dueEpochDay = due, recurMonths = rec.toIntOrNull() ?: 0,
                kmDue = kd, recurKm = parseLong(kmRec) ?: 0, costCents = parseMoney(cost) ?: 0,
            ))
        },
    ) {
        Dropdown(stringResource(R.string.field_type), Defaults.deadlineTypes, type, { typeLabel(it) }, {
            type = it
            rec = Defaults.suggestedRecurMonths[it]?.toString() ?: ""
        })
        DateInput(stringResource(R.string.field_due_date), due, { due = it }, allowEmpty = true)
        TextInput(rec, { rec = digits(it, 3) }, stringResource(R.string.field_repeat_months), keyboard = KeyboardType.Number, placeholder = stringResource(R.string.hint_no_repeat))
        TextInput(kmDue, { kmDue = digits(it) }, stringResource(R.string.field_due_km), keyboard = KeyboardType.Number, placeholder = "60000")
        TextInput(kmRec, { kmRec = digits(it) }, stringResource(R.string.field_repeat_km), keyboard = KeyboardType.Number, placeholder = "15000")
        TextInput(cost, { cost = it }, stringResource(R.string.field_expected_cost), keyboard = KeyboardType.Decimal)
    }
}

/**
 * New service log: date, mileage, cost, place/garage. When [closes] is set, the log closes that
 * deadline (which then renews itself if it repeats). [onSave] gets the log, the budget category
 * (null = do not add to the budget) and the description to use for the budget expense.
 */
@Composable
fun ServiceLogDialog(
    car: Car,
    closes: Deadline?,
    outCategories: List<Category>,
    defaultCategoryId: String,
    onSave: (ServiceLog, String?, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialType = when {
        closes == null -> "SERVICE"
        closes.type in Defaults.serviceTypes -> closes.type
        else -> "OTHER"
    }
    val closesLabel = closes?.let { typeLabel(it.type) }
    var type by remember { mutableStateOf(initialType) }
    var day by remember { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var km by remember { mutableStateOf(car.km.toString()) }
    var cost by remember { mutableStateOf(closes?.costCents?.let(::centsToInput) ?: "") }
    var desc by remember { mutableStateOf(if (closes != null && initialType == "OTHER") closesLabel ?: "" else "") }
    var place by remember { mutableStateOf("") }
    var toBudget by remember { mutableStateOf(true) }
    var catId by remember { mutableStateOf(defaultCategoryId) }
    var error by remember { mutableStateOf<String?>(null) }
    val errCost = stringResource(R.string.err_cost)
    val typeText = typeLabel(type)

    FormDialog(
        title = if (closesLabel != null) stringResource(R.string.log_done_title, closesLabel) else stringResource(R.string.log_new),
        error = error,
        onDismiss = onDismiss,
        onConfirm = {
            val c = if (cost.isBlank()) 0L else parseMoney(cost)
            if (c == null) { error = errCost; return@FormDialog }
            val note = listOfNotNull(car.name, typeText, desc.trim().ifBlank { null }).joinToString(" · ")
            onSave(
                ServiceLog(carId = car.id, type = type, epochDay = day, km = parseLong(km), costCents = c, description = desc.trim(), place = place.trim()),
                if (toBudget) catId else null,
                note,
            )
        },
    ) {
        Dropdown(stringResource(R.string.field_type), Defaults.serviceTypes, type, { typeLabel(it) }, { type = it })
        DateInput(stringResource(R.string.field_date), day, { it?.let { d -> day = d } })
        TextInput(km, { km = digits(it) }, stringResource(R.string.field_km_at_time), keyboard = KeyboardType.Number)
        TextInput(cost, { cost = it }, stringResource(R.string.field_cost), keyboard = KeyboardType.Decimal)
        TextInput(desc, { desc = it }, stringResource(R.string.field_work_done), placeholder = stringResource(R.string.hint_work_done), singleLine = false)
        TextInput(place, { place = it }, stringResource(R.string.field_place), placeholder = stringResource(R.string.hint_place))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Checkbox(checked = toBudget, onCheckedChange = { toBudget = it })
            Text(stringResource(R.string.log_add_to_budget))
        }
        if (toBudget) Dropdown(stringResource(R.string.field_budget_category), outCategories, outCategories.firstOrNull { it.id == catId }, { it.name }, { catId = it.id })
        if (closes != null && (closes.recurMonths > 0 || closes.recurKm > 0)) {
            Text(stringResource(R.string.log_will_renew), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
