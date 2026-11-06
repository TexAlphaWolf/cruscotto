package dev.tex.cruscotto.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

object Kind {
    const val OUT = "OUT"
    const val IN = "IN"
}

/** Expense category (with % share and buffer settings) or income category (name only). */
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey val id: String = newId(),
    val kind: String,
    val name: String,
    val pct: Double = 0.0,
    val isBuffer: Boolean = false,
    val bufferOrder: Int = 0,
    val sort: Int = 0,
)

/** Income or expense. Amounts are always in cents. */
@Entity(tableName = "txns", indices = [Index("epochDay"), Index("categoryId")])
data class Txn(
    @PrimaryKey val id: String = newId(),
    val type: String,
    val amountCents: Long,
    val epochDay: Long,
    val categoryId: String,
    val note: String = "",
    val serviceLogId: String? = null,
)

/** [fuel] holds a key from [Defaults.fuels]. */
@Entity(tableName = "cars")
data class Car(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val plate: String = "",
    val model: String = "",
    val year: Int? = null,
    val fuel: String = "PETROL",
    val km: Long = 0,
)

/** Deadline by date and/or mileage, optionally recurring. [type] is a key from [Defaults.deadlineTypes]. */
@Entity(tableName = "deadlines", indices = [Index("carId")])
data class Deadline(
    @PrimaryKey val id: String = newId(),
    val carId: String,
    val type: String,
    val dueEpochDay: Long? = null,
    val recurMonths: Int = 0,
    val kmDue: Long? = null,
    val recurKm: Long = 0,
    val costCents: Long = 0,
)

/** Work done on a car: date, mileage, cost, place/garage. [type] is a key from [Defaults.serviceTypes]. */
@Entity(tableName = "service_logs", indices = [Index("carId")])
data class ServiceLog(
    @PrimaryKey val id: String = newId(),
    val carId: String,
    val type: String,
    val epochDay: Long,
    val km: Long? = null,
    val costCents: Long = 0,
    val description: String = "",
    val place: String = "",
    val txnId: String? = null,
)

@Entity(tableName = "settings")
data class AppSettings(
    @PrimaryKey val id: Int = 1,
    val salaryCents: Long = 0,
    val carCategoryId: String = "car",
    val totpSecret: String = "",
    val totpOn: Boolean = false,
)

object Defaults {
    /** Seeded on first launch; names are filled in from string resources in the phone's language. */
    val outCategories = listOf(
        Category("home", Kind.OUT, "", 30.0, sort = 0),
        Category("groceries", Kind.OUT, "", 12.0, sort = 1),
        Category("car", Kind.OUT, "", 12.0, sort = 2),
        Category("bills", Kind.OUT, "", 8.0, sort = 3),
        Category("leisure", Kind.OUT, "", 10.0, isBuffer = true, bufferOrder = 1, sort = 4),
        Category("emergencies", Kind.OUT, "", 8.0, isBuffer = true, bufferOrder = 2, sort = 5),
        Category("savings", Kind.OUT, "", 20.0, isBuffer = true, bufferOrder = 3, sort = 6),
    )
    val inCategories = listOf(
        Category("salary", Kind.IN, "", sort = 0),
        Category("bonus", Kind.IN, "", sort = 1),
        Category("refunds", Kind.IN, "", sort = 2),
        Category("other-income", Kind.IN, "", sort = 3),
    )
    val deadlineTypes = listOf("INSURANCE", "ROAD_TAX", "INSPECTION", "SERVICE", "TYRES", "TIMING_BELT", "OTHER")
    val serviceTypes = listOf("SERVICE", "INSPECTION", "INSURANCE", "ROAD_TAX", "TYRES", "REPAIR", "BODYWORK", "WASH", "OTHER")
    val fuels = listOf("PETROL", "DIESEL", "LPG", "CNG", "HYBRID", "ELECTRIC")
    /** Suggested repeat interval in months per deadline type. */
    val suggestedRecurMonths = mapOf("INSURANCE" to 12, "ROAD_TAX" to 12, "INSPECTION" to 24, "SERVICE" to 12)
}
