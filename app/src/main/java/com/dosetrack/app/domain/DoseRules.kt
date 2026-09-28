package com.dosetrack.app.domain

import com.dosetrack.app.data.model.DoseStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * A pending dose becomes "missed" this long after its scheduled time (or after its snooze time,
 * whichever is later). Mirrored in the SQL of DoseDao.getPendingIdsOlderThan / markPendingMissedOlderThan.
 */
const val MISSED_AFTER_MILLIS: Long = 2 * 60 * 60 * 1000L

/** What the UI shows for a dose. */
enum class DoseDisplayState { UPCOMING, DUE, TAKEN, SKIPPED, MISSED }

/** The instant the missed-dose countdown starts from. */
fun missedClockStart(scheduledAt: Long, snoozedUntil: Long?): Long = maxOf(scheduledAt, snoozedUntil ?: scheduledAt)

fun displayStateOf(
    status: DoseStatus,
    scheduledAt: Long,
    now: Long,
    snoozedUntil: Long? = null,
): DoseDisplayState = when (status) {
    DoseStatus.TAKEN -> DoseDisplayState.TAKEN
    DoseStatus.SKIPPED -> DoseDisplayState.SKIPPED
    DoseStatus.MISSED -> DoseDisplayState.MISSED
    DoseStatus.PENDING -> when {
        now < scheduledAt -> DoseDisplayState.UPCOMING
        now < missedClockStart(scheduledAt, snoozedUntil) + MISSED_AFTER_MILLIS -> DoseDisplayState.DUE
        else -> DoseDisplayState.MISSED
    }
}

object DoseGenerator {
    /**
     * Returns every scheduled instant for a medication that falls inside [windowStart, windowEnd),
     * limited to the medication's [startDate]..[endDate] (inclusive, local dates in [zone]).
     */
    fun occurrences(
        startDate: LocalDate,
        endDate: LocalDate?,
        timesOfDay: List<Int>,
        windowStart: Instant,
        windowEnd: Instant,
        zone: ZoneId,
    ): List<Instant> {
        if (timesOfDay.isEmpty() || !windowStart.isBefore(windowEnd)) return emptyList()
        val firstDay = maxOf(startDate, windowStart.atZone(zone).toLocalDate())
        val lastWindowDay = windowEnd.atZone(zone).toLocalDate()
        val lastDay = if (endDate != null) minOf(endDate, lastWindowDay) else lastWindowDay
        val times = timesOfDay.filter { it in 0 until 24 * 60 }.distinct().sorted()

        val result = mutableListOf<Instant>()
        var day = firstDay
        while (!day.isAfter(lastDay)) {
            for (minutes in times) {
                val instant = day.atTime(LocalTime.of(minutes / 60, minutes % 60)).atZone(zone).toInstant()
                if (!instant.isBefore(windowStart) && instant.isBefore(windowEnd)) result += instant
            }
            day = day.plusDays(1)
        }
        // A DST gap can map two local times onto the same instant.
        return result.distinct()
    }
}

data class AdherenceStats(val taken: Int, val missed: Int, val skipped: Int) {
    val total: Int get() = taken + missed + skipped

    /** Percentage of recorded doses that were taken, or null when there is nothing to measure yet. */
    val percent: Int? get() = if (total == 0) null else (taken * 100.0 / total).roundToInt()

    companion object {
        val EMPTY = AdherenceStats(0, 0, 0)

        fun from(counts: Map<DoseStatus, Int>) = AdherenceStats(
            taken = counts[DoseStatus.TAKEN] ?: 0,
            missed = counts[DoseStatus.MISSED] ?: 0,
            skipped = counts[DoseStatus.SKIPPED] ?: 0,
        )
    }
}
