package dev.tex.cruscotto.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    // settings
    @Query("SELECT * FROM settings WHERE id = 1")
    fun settings(): Flow<AppSettings?>

    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun settingsOnce(): AppSettings?

    @Upsert
    suspend fun upsertSettings(s: AppSettings)

    // categories
    @Query("SELECT * FROM categories ORDER BY kind, sort")
    fun categories(): Flow<List<Category>>

    @Query("SELECT * FROM categories")
    suspend fun categoriesOnce(): List<Category>

    @Upsert
    suspend fun upsertCategories(list: List<Category>)

    @Query("DELETE FROM categories WHERE id IN (:ids)")
    suspend fun deleteCategories(ids: List<String>)

    // transactions
    @Query("SELECT * FROM txns ORDER BY epochDay DESC")
    fun txns(): Flow<List<Txn>>

    /** Transactions in [from, to] (epoch days, inclusive), newest first. */
    @Query("SELECT * FROM txns WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay DESC")
    fun txnsBetween(from: Long, to: Long): Flow<List<Txn>>

    @Query("SELECT * FROM txns")
    suspend fun txnsOnce(): List<Txn>

    @Query("SELECT * FROM txns WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay")
    suspend fun txnsBetweenOnce(from: Long, to: Long): List<Txn>

    @Query("DELETE FROM txns WHERE epochDay < :before")
    suspend fun deleteTxnsBefore(before: Long): Int

    @Upsert
    suspend fun upsertTxn(t: Txn)

    @Upsert
    suspend fun upsertTxns(list: List<Txn>)

    @Query("DELETE FROM txns WHERE id = :id")
    suspend fun deleteTxn(id: String)

    @Query("SELECT * FROM txns WHERE id = :id")
    suspend fun txn(id: String): Txn?

    // cars
    @Query("SELECT * FROM cars ORDER BY name COLLATE NOCASE")
    fun cars(): Flow<List<Car>>

    @Query("SELECT * FROM cars")
    suspend fun carsOnce(): List<Car>

    @Query("SELECT * FROM cars WHERE id = :id")
    suspend fun car(id: String): Car?

    @Upsert
    suspend fun upsertCar(c: Car)

    @Upsert
    suspend fun upsertCars(list: List<Car>)

    @Query("DELETE FROM cars WHERE id = :id")
    suspend fun deleteCar(id: String)

    // deadlines
    @Query("SELECT * FROM deadlines")
    fun deadlines(): Flow<List<Deadline>>

    @Query("SELECT * FROM deadlines")
    suspend fun deadlinesOnce(): List<Deadline>

    @Upsert
    suspend fun upsertDeadline(d: Deadline)

    @Upsert
    suspend fun upsertDeadlines(list: List<Deadline>)

    @Query("DELETE FROM deadlines WHERE id = :id")
    suspend fun deleteDeadline(id: String)

    @Query("DELETE FROM deadlines WHERE carId = :carId")
    suspend fun deleteDeadlinesOfCar(carId: String)

    // service logs
    @Query("SELECT * FROM service_logs ORDER BY epochDay DESC")
    fun serviceLogs(): Flow<List<ServiceLog>>

    @Query("SELECT * FROM service_logs")
    suspend fun serviceLogsOnce(): List<ServiceLog>

    @Query("SELECT * FROM service_logs WHERE id = :id")
    suspend fun serviceLog(id: String): ServiceLog?

    @Upsert
    suspend fun upsertServiceLog(l: ServiceLog)

    @Upsert
    suspend fun upsertServiceLogs(list: List<ServiceLog>)

    @Query("DELETE FROM service_logs WHERE id = :id")
    suspend fun deleteServiceLog(id: String)

    @Query("SELECT * FROM service_logs WHERE carId = :carId")
    suspend fun serviceLogsOfCar(carId: String): List<ServiceLog>

    @Query("SELECT * FROM service_logs WHERE carId = :carId ORDER BY epochDay DESC")
    fun serviceLogsOfCarFlow(carId: String): Flow<List<ServiceLog>>

    @Query("SELECT * FROM deadlines WHERE carId = :carId")
    fun deadlinesOfCarFlow(carId: String): Flow<List<Deadline>>

    @Query("DELETE FROM service_logs WHERE epochDay < :before")
    suspend fun deleteServiceLogsBefore(before: Long): Int

    /** Unlinks budget expenses whose service log no longer exists, and vice versa. */
    @Query("UPDATE txns SET serviceLogId = NULL WHERE serviceLogId IS NOT NULL AND serviceLogId NOT IN (SELECT id FROM service_logs)")
    suspend fun unlinkOrphanTxns()

    @Query("UPDATE service_logs SET txnId = NULL WHERE txnId IS NOT NULL AND txnId NOT IN (SELECT id FROM txns)")
    suspend fun unlinkOrphanLogs()

    @Query("SELECT * FROM categories WHERE id IN (:ids)")
    suspend fun categoriesByIds(ids: List<String>): List<Category>
}
