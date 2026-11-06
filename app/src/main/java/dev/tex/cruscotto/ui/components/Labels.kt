package dev.tex.cruscotto.ui.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.tex.cruscotto.R
import dev.tex.cruscotto.domain.DeadlineStatus

/** Localized names for the categories seeded on first launch (stored in the database afterwards). */
@StringRes
fun defaultCategoryName(id: String): Int = when (id) {
    "home" -> R.string.cat_home
    "groceries" -> R.string.cat_groceries
    "car" -> R.string.cat_car
    "bills" -> R.string.cat_bills
    "leisure" -> R.string.cat_leisure
    "emergencies" -> R.string.cat_emergencies
    "savings" -> R.string.cat_savings
    "salary" -> R.string.cat_salary
    "bonus" -> R.string.cat_bonus
    "refunds" -> R.string.cat_refunds
    "other-income" -> R.string.cat_other_income
    else -> R.string.cat_unnamed
}

@StringRes
private fun deadlineTypeRes(key: String): Int? = when (key) {
    "INSURANCE" -> R.string.type_insurance
    "ROAD_TAX" -> R.string.type_road_tax
    "INSPECTION" -> R.string.type_inspection
    "SERVICE" -> R.string.type_service
    "TYRES" -> R.string.type_tyres
    "TIMING_BELT" -> R.string.type_timing_belt
    "REPAIR" -> R.string.type_repair
    "BODYWORK" -> R.string.type_bodywork
    "WASH" -> R.string.type_wash
    "OTHER" -> R.string.type_other
    else -> null
}

@StringRes
private fun fuelRes(key: String): Int? = when (key) {
    "PETROL" -> R.string.fuel_petrol
    "DIESEL" -> R.string.fuel_diesel
    "LPG" -> R.string.fuel_lpg
    "CNG" -> R.string.fuel_cng
    "HYBRID" -> R.string.fuel_hybrid
    "ELECTRIC" -> R.string.fuel_electric
    else -> null
}

/** Label for a deadline or service type key; unknown values are shown as stored. */
@Composable
fun typeLabel(key: String): String = deadlineTypeRes(key)?.let { stringResource(it) } ?: key

@Composable
fun fuelLabel(key: String): String = fuelRes(key)?.let { stringResource(it) } ?: key

@Composable
fun statusText(s: DeadlineStatus): String {
    val parts = mutableListOf<String>()
    s.daysLeft?.let { d ->
        parts += when {
            d < 0 -> pluralStringResource(R.plurals.status_overdue_days, (-d).toInt(), (-d).toInt())
            d == 0L -> stringResource(R.string.status_due_today)
            else -> pluralStringResource(R.plurals.status_in_days, d.toInt(), d.toInt())
        }
    }
    s.kmLeft?.let { k ->
        parts += if (k <= 0) stringResource(R.string.status_km_exceeded, fmtKm(-k)) else stringResource(R.string.status_km_left, fmtKm(k))
    }
    return if (parts.isEmpty()) stringResource(R.string.status_ok) else parts.joinToString(" · ")
}
