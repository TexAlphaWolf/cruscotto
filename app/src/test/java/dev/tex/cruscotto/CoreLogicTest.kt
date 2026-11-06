package dev.tex.cruscotto

import dev.tex.cruscotto.data.Car
import dev.tex.cruscotto.data.Category
import dev.tex.cruscotto.data.Deadline
import dev.tex.cruscotto.data.Kind
import dev.tex.cruscotto.data.Txn
import dev.tex.cruscotto.domain.Level
import dev.tex.cruscotto.domain.computeMonth
import dev.tex.cruscotto.domain.deadlineStatus
import dev.tex.cruscotto.security.Crypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CryptoTest {
    private val secret = Crypto.base32Encode("12345678901234567890".toByteArray())

    @Test fun totpMatchesRfc6238Vectors() {
        assertEquals("287082", Crypto.totp(secret, 59L / 30))
        assertEquals("081804", Crypto.totp(secret, 1111111109L / 30))
        assertEquals("65353130", Crypto.totp(secret, 20000000000L / 30, digits = 8))
    }

    @Test fun base32RoundTrip() {
        assertEquals("12345678901234567890", String(Crypto.base32Decode(secret)))
    }

    @Test fun pinWrapsAndUnwrapsKey() {
        val key = Crypto.randomBytes(32)
        val salt = Crypto.randomBytes(16)
        val wrapped = Crypto.seal(Crypto.deriveKey("48291375", salt, 1000), key)
        assertArrayEquals(key, Crypto.open(Crypto.deriveKey("48291375", salt, 1000), wrapped))
        assertTrue(runCatching { Crypto.open(Crypto.deriveKey("48291376", salt, 1000), wrapped) }.isFailure)
    }

    @Test fun recoveryCodeFormat() {
        val code = Crypto.newRecoveryCode()
        assertTrue(Regex("[A-Z2-7]{4}(-[A-Z2-7]{4}){3}").matches(code))
        assertEquals(code.replace("-", ""), Crypto.normalizeRecovery(code.lowercase()))
    }
}

class BudgetTest {
    private val cats = listOf(
        Category("home", Kind.OUT, "Home", 30.0, sort = 0),
        Category("groceries", Kind.OUT, "Groceries", 12.0, sort = 1),
        Category("leisure", Kind.OUT, "Leisure", 10.0, isBuffer = true, bufferOrder = 1, sort = 2),
        Category("savings", Kind.OUT, "Savings", 48.0, isBuffer = true, bufferOrder = 2, sort = 3),
    )
    private val month = YearMonth.of(2026, 11)
    private val day = month.atDay(5).toEpochDay()
    private fun out(cat: String, euro: Long) = Txn(type = Kind.OUT, amountCents = euro * 100, epochDay = day, categoryId = cat)

    @Test fun overflowIsTakenFromBuffersInOrder() {
        val b = computeMonth(month, listOf(out("groceries", 400), out("leisure", 50)), cats, 100_000)
        val eff = b.rows.associate { it.category.id to it.effectiveCents }
        assertEquals(40_000L, eff["groceries"])
        assertEquals(5_000L, eff["leisure"])
        assertEquals(25_000L, eff["savings"])
        assertEquals(100_000L, b.rows.sumOf { it.effectiveCents })
        assertEquals(0L, b.deficitCents)
    }

    @Test fun deficitWhenBuffersRunOut() {
        val b = computeMonth(month, listOf(out("groceries", 800), out("leisure", 50)), cats, 100_000)
        assertEquals(15_000L, b.deficitCents)
    }

    @Test fun incomeReplacesSalary() {
        val inc = Txn(type = Kind.IN, amountCents = 200_000, epochDay = day, categoryId = "salary")
        val b = computeMonth(month, listOf(inc), cats, 100_000)
        assertEquals(200_000L, b.baseCents)
        assertFalse(b.usesSalary)
    }
}

class GarageTest {
    private val car = Car(name = "Golf", km = 59_500)
    private val today = LocalDate.of(2026, 11, 4)

    @Test fun statusLevels() {
        assertEquals(Level.BAD, deadlineStatus(Deadline(carId = car.id, type = "ROAD_TAX", dueEpochDay = today.minusDays(3).toEpochDay()), car, today).level)
        assertEquals(Level.WARN, deadlineStatus(Deadline(carId = car.id, type = "INSURANCE", dueEpochDay = today.plusDays(20).toEpochDay()), car, today).level)
        assertEquals(Level.WARN, deadlineStatus(Deadline(carId = car.id, type = "SERVICE", kmDue = 60_000), car, today).level)
        assertEquals(Level.OK, deadlineStatus(Deadline(carId = car.id, type = "INSPECTION", dueEpochDay = today.plusDays(200).toEpochDay()), car, today).level)
    }

    @Test fun keepsRawValuesForTheUi() {
        val s = deadlineStatus(Deadline(carId = car.id, type = "SERVICE", dueEpochDay = today.plusDays(10).toEpochDay(), kmDue = 59_000), car, today)
        assertEquals(Level.BAD, s.level)
        assertEquals(10L, s.daysLeft)
        assertEquals(-500L, s.kmLeft)
    }
}

class YearEndTest {
    @Test fun remindersOnFirstAndTwentiethOfDecember() {
        fun next(y: Int, m: Int, d: Int, h: Int) = dev.tex.cruscotto.yearend.YearEnd.nextReminder(java.time.LocalDateTime.of(y, m, d, h, 0))
        assertEquals(java.time.LocalDateTime.of(2026, 12, 1, 10, 0) to false, next(2026, 6, 15, 12))
        assertEquals(java.time.LocalDateTime.of(2026, 12, 20, 10, 0) to true, next(2026, 12, 1, 11))
        assertEquals(java.time.LocalDateTime.of(2027, 12, 1, 10, 0) to false, next(2026, 12, 20, 11))
        assertTrue(dev.tex.cruscotto.yearend.YearEnd.isReminderPeriod(LocalDate.of(2026, 12, 5)))
        assertFalse(dev.tex.cruscotto.yearend.YearEnd.isFinalPeriod(LocalDate.of(2026, 12, 19)))
        assertTrue(dev.tex.cruscotto.yearend.YearEnd.isFinalPeriod(LocalDate.of(2026, 12, 20)))
        assertFalse(dev.tex.cruscotto.yearend.YearEnd.isReminderPeriod(LocalDate.of(2027, 1, 2)))
    }
}
