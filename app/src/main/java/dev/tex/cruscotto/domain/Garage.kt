package dev.tex.cruscotto.domain

import dev.tex.cruscotto.data.Car
import dev.tex.cruscotto.data.Deadline
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class Level { OK, WARN, BAD }

/**
 * Status of a deadline. [daysLeft] is negative when overdue; [kmLeft] is negative when the mileage
 * was exceeded. Either is null when the deadline has no date / no mileage. The UI turns this into text.
 */
data class DeadlineStatus(val level: Level, val daysLeft: Long?, val kmLeft: Long?)

/** Red when overdue, yellow within 30 days or 1,000 km. */
fun deadlineStatus(d: Deadline, car: Car?, today: LocalDate = LocalDate.now()): DeadlineStatus {
    var level = Level.OK
    val days = d.dueEpochDay?.let { ChronoUnit.DAYS.between(today, LocalDate.ofEpochDay(it)) }
    if (days != null) level = when {
        days < 0 -> Level.BAD
        days <= 30 -> Level.WARN
        else -> Level.OK
    }
    val km = if (d.kmDue != null && car != null) d.kmDue - car.km else null
    if (km != null) {
        val l = when {
            km <= 0 -> Level.BAD
            km <= 1000 -> Level.WARN
            else -> Level.OK
        }
        if (l.ordinal > level.ordinal) level = l
    }
    return DeadlineStatus(level, days, km)
}
