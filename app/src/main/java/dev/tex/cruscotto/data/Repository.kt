package dev.tex.cruscotto.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

class NotABackupException : Exception("Not a Cruscotto backup")

class Repository(private val db: AppDatabase) {
    private val dao = db.dao()

    val settings: Flow<AppSettings?> = dao.settings()
    val categories: Flow<List<Category>> = dao.categories()
    val txns: Flow<List<Txn>> = dao.txns()
    val cars: Flow<List<Car>> = dao.cars()
    val deadlines: Flow<List<Deadline>> = dao.deadlines()
    val serviceLogs: Flow<List<ServiceLog>> = dao.serviceLogs()

    suspend fun settingsOnce(): AppSettings = dao.settingsOnce() ?: AppSettings()

    /** Only the transactions of a date range: screens never load the whole history. */
    fun txnsBetween(from: LocalDate, to: LocalDate): Flow<List<Txn>> = dao.txnsBetween(from.toEpochDay(), to.toEpochDay())
    suspend fun txnsOfYear(year: Int): List<Txn> =
        dao.txnsBetweenOnce(LocalDate.of(year, 1, 1).toEpochDay(), LocalDate.of(year, 12, 31).toEpochDay())
    suspend fun categoriesOnce(): List<Category> = dao.categoriesOnce()
    fun serviceLogsOfCar(carId: String): Flow<List<ServiceLog>> = dao.serviceLogsOfCarFlow(carId)
    fun deadlinesOfCar(carId: String): Flow<List<Deadline>> = dao.deadlinesOfCarFlow(carId)

    /**
     * Deletes transactions and/or service logs dated before [before]. Cars, deadlines and categories stay.
     * Returns how many rows were removed.
     */
    suspend fun purgeBefore(before: LocalDate, txns: Boolean, logs: Boolean): Int = db.withTransaction {
        var n = 0
        if (txns) n += dao.deleteTxnsBefore(before.toEpochDay())
        if (logs) n += dao.deleteServiceLogsBefore(before.toEpochDay())
        dao.unlinkOrphanTxns()
        dao.unlinkOrphanLogs()
        n
    }

    /**
     * Renames the default categories still carrying a default name (in any language) to [nameOf]'s
     * name, so they follow a language change. Categories the user renamed are left alone.
     */
    suspend fun relocalizeDefaults(defaultNames: Map<String, Set<String>>, nameOf: (String) -> String) {
        val cats = dao.categoriesByIds(defaultNames.keys.toList())
        val changed = cats.mapNotNull { c ->
            val target = nameOf(c.id)
            if (c.name != target && c.name in defaultNames[c.id].orEmpty()) c.copy(name = target) else null
        }
        if (changed.isNotEmpty()) dao.upsertCategories(changed)
    }

    /** On first launch, inserts settings and default categories; [nameOf] maps a category id to its localized name. */
    suspend fun ensureDefaults(nameOf: (String) -> String) {
        if (dao.settingsOnce() == null) {
            db.withTransaction {
                dao.upsertSettings(AppSettings())
                dao.upsertCategories((Defaults.outCategories + Defaults.inCategories).map { it.copy(name = nameOf(it.id)) })
            }
        }
    }

    suspend fun saveSettings(s: AppSettings) = dao.upsertSettings(s)

    /** Saves the whole category list edited in Settings. */
    suspend fun saveCategories(list: List<Category>) = db.withTransaction {
        val keep = list.map { it.id }.toSet()
        val removed = dao.categoriesOnce().map { it.id }.filter { it !in keep }
        if (removed.isNotEmpty()) dao.deleteCategories(removed)
        dao.upsertCategories(list)
    }

    // ---------- transactions ----------
    suspend fun saveTxn(t: Txn) = dao.upsertTxn(t)

    suspend fun deleteTxn(t: Txn) = db.withTransaction {
        dao.deleteTxn(t.id)
        t.serviceLogId?.let { id -> dao.serviceLog(id)?.let { dao.upsertServiceLog(it.copy(txnId = null)) } }
    }

    /** Undo of [deleteTxn]: puts the transaction back and relinks its service log, if that still exists. */
    suspend fun restoreTxn(t: Txn) = db.withTransaction {
        val log = t.serviceLogId?.let { dao.serviceLog(it) }
        dao.upsertTxn(if (t.serviceLogId != null && log == null) t.copy(serviceLogId = null) else t)
        if (log != null) dao.upsertServiceLog(log.copy(txnId = t.id))
    }

    // ---------- garage ----------
    suspend fun saveCar(c: Car) = dao.upsertCar(c)

    suspend fun deleteCar(carId: String) = db.withTransaction {
        dao.deleteDeadlinesOfCar(carId)
        for (l in dao.serviceLogsOfCar(carId)) {
            l.txnId?.let { tid -> dao.txn(tid)?.let { dao.upsertTxn(it.copy(serviceLogId = null)) } }
            dao.deleteServiceLog(l.id)
        }
        dao.deleteCar(carId)
    }

    suspend fun saveDeadline(d: Deadline) = dao.upsertDeadline(d)
    suspend fun deleteDeadline(id: String) = dao.deleteDeadline(id)

    /**
     * Saves a service log. If [budgetCategoryId] is set and the cost is > 0, it also adds the expense to the budget
     * (with [txnNote] as description). If the log closes a deadline, the deadline is renewed (by months and/or km)
     * or deleted when it does not repeat.
     */
    suspend fun addServiceLog(log: ServiceLog, budgetCategoryId: String?, closes: Deadline?, txnNote: String = "") = db.withTransaction {
        val car = dao.car(log.carId)
        var saved = log
        if (budgetCategoryId != null && log.costCents > 0) {
            val txn = Txn(
                type = Kind.OUT, amountCents = log.costCents, epochDay = log.epochDay, categoryId = budgetCategoryId,
                note = txnNote,
                serviceLogId = log.id,
            )
            dao.upsertTxn(txn)
            saved = log.copy(txnId = txn.id)
        }
        dao.upsertServiceLog(saved)
        if (car != null && log.km != null && log.km > car.km) dao.upsertCar(car.copy(km = log.km))
        if (closes != null) {
            if (closes.recurMonths > 0 || closes.recurKm > 0) {
                val doneDay = LocalDate.ofEpochDay(log.epochDay)
                // Keep the deadline's own calendar (e.g. insurance renews on the same date every year);
                // if the work was so late that the next date would already be past, count from the work date.
                val nextDue = if (closes.recurMonths > 0) {
                    val months = closes.recurMonths.toLong()
                    val fromDue = (closes.dueEpochDay?.let { LocalDate.ofEpochDay(it) } ?: doneDay).plusMonths(months)
                    (if (fromDue.isAfter(doneDay)) fromDue else doneDay.plusMonths(months)).toEpochDay()
                } else closes.dueEpochDay
                val kmNow = log.km ?: car?.km ?: 0
                val nextKm = if (closes.recurKm > 0) kmNow + closes.recurKm else closes.kmDue
                dao.upsertDeadline(closes.copy(dueEpochDay = nextDue, kmDue = nextKm))
            } else {
                dao.deleteDeadline(closes.id)
            }
        }
    }

    /** Deletes a service log and its budget expense. Returns the deleted expense, for undo. */
    suspend fun deleteServiceLog(l: ServiceLog): Txn? = db.withTransaction {
        val txn = l.txnId?.let { dao.txn(it) }
        dao.deleteServiceLog(l.id)
        l.txnId?.let { dao.deleteTxn(it) }
        txn
    }

    /** Undo of [deleteServiceLog]. Does not touch deadlines or the odometer. */
    suspend fun restoreServiceLog(l: ServiceLog, txn: Txn?) = db.withTransaction {
        dao.upsertServiceLog(if (txn == null) l.copy(txnId = null) else l)
        if (txn != null) dao.upsertTxn(txn)
    }

    // ---------- JSON backup (not encrypted: keep the file safe) ----------
    suspend fun exportJson(): String {
        val s = settingsOnce()
        val o = JSONObject()
            .put("app", "cruscotto-android").put("version", 1)
            .put("settings", JSONObject().put("salaryCents", s.salaryCents).put("carCategoryId", s.carCategoryId))
            .put("categories", JSONArray(dao.categoriesOnce().map {
                JSONObject().put("id", it.id).put("kind", it.kind).put("name", it.name).put("pct", it.pct)
                    .put("isBuffer", it.isBuffer).put("bufferOrder", it.bufferOrder).put("sort", it.sort)
            }))
            .put("txns", JSONArray(dao.txnsOnce().map {
                JSONObject().put("id", it.id).put("type", it.type).put("amountCents", it.amountCents).put("epochDay", it.epochDay)
                    .put("categoryId", it.categoryId).put("note", it.note).put("serviceLogId", it.serviceLogId ?: "")
            }))
            .put("cars", JSONArray(dao.carsOnce().map {
                JSONObject().put("id", it.id).put("name", it.name).put("plate", it.plate).put("model", it.model)
                    .put("year", it.year ?: 0).put("fuel", it.fuel).put("km", it.km)
            }))
            .put("deadlines", JSONArray(dao.deadlinesOnce().map {
                JSONObject().put("id", it.id).put("carId", it.carId).put("type", it.type).put("dueEpochDay", it.dueEpochDay ?: -1)
                    .put("recurMonths", it.recurMonths).put("kmDue", it.kmDue ?: -1).put("recurKm", it.recurKm).put("costCents", it.costCents)
            }))
            .put("serviceLogs", JSONArray(dao.serviceLogsOnce().map {
                JSONObject().put("id", it.id).put("carId", it.carId).put("type", it.type).put("epochDay", it.epochDay)
                    .put("km", it.km ?: -1).put("costCents", it.costCents).put("description", it.description)
                    .put("place", it.place).put("txnId", it.txnId ?: "")
            }))
        return o.toString(2)
    }

    /** Imports a backup: adds or updates by id, never deletes. Returns how many items were read. */
    suspend fun importJson(text: String): Int {
        val o = JSONObject(text)
        if (o.optString("app") != "cruscotto-android") throw NotABackupException()
        fun JSONArray?.objs(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }
        fun JSONObject.optNullableLong(k: String): Long? = optLong(k, -1).takeIf { it >= 0 }
        fun JSONObject.optNullableStr(k: String): String? = optString(k, "").ifEmpty { null }
        var count = 0
        db.withTransaction {
            o.optJSONObject("settings")?.let { js ->
                dao.upsertSettings(settingsOnce().copy(salaryCents = js.optLong("salaryCents"), carCategoryId = js.optString("carCategoryId", "car")))
            }
            val cats = o.optJSONArray("categories").objs().map {
                Category(it.getString("id"), it.getString("kind"), it.getString("name"), it.optDouble("pct", 0.0),
                    it.optBoolean("isBuffer"), it.optInt("bufferOrder"), it.optInt("sort"))
            }
            if (cats.isNotEmpty()) dao.upsertCategories(cats)
            val txns = o.optJSONArray("txns").objs().map {
                Txn(it.getString("id"), it.getString("type"), it.getLong("amountCents"), it.getLong("epochDay"),
                    it.getString("categoryId"), it.optString("note"), it.optNullableStr("serviceLogId"))
            }
            dao.upsertTxns(txns)
            val cars = o.optJSONArray("cars").objs().map {
                Car(it.getString("id"), it.getString("name"), it.optString("plate"), it.optString("model"),
                    it.optInt("year").takeIf { y -> y > 0 }, it.optString("fuel", "PETROL"), it.optLong("km"))
            }
            dao.upsertCars(cars)
            val dls = o.optJSONArray("deadlines").objs().map {
                Deadline(it.getString("id"), it.getString("carId"), it.getString("type"), it.optNullableLong("dueEpochDay"),
                    it.optInt("recurMonths"), it.optNullableLong("kmDue"), it.optLong("recurKm"), it.optLong("costCents"))
            }
            dao.upsertDeadlines(dls)
            val logs = o.optJSONArray("serviceLogs").objs().map {
                ServiceLog(it.getString("id"), it.getString("carId"), it.getString("type"), it.getLong("epochDay"),
                    it.optNullableLong("km"), it.optLong("costCents"), it.optString("description"), it.optString("place"),
                    it.optNullableStr("txnId"))
            }
            dao.upsertServiceLogs(logs)
            count = cats.size + txns.size + cars.size + dls.size + logs.size
        }
        return count
    }
}
