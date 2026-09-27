package com.dosetrack.app.domain

import com.dosetrack.app.data.model.DoseStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class DoseRulesTest {
    private val utc: ZoneId = ZoneOffset.UTC

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0) =
        LocalDateTime.of(y, mo, d, h, mi).atZone(utc).toInstant()

    @Test
    fun generatesTimesInsideWindowOnly() {
        val result = DoseGenerator.occurrences(
            startDate = LocalDate.of(2026, 9, 1),
            endDate = null,
            timesOfDay = listOf(21 * 60, 9 * 60),
            windowStart = at(2026, 9, 28, 10),
            windowEnd = at(2026, 9, 29, 22),
            zone = utc,
        )
        assertEquals(
            listOf(at(2026, 9, 28, 21), at(2026, 9, 29, 9), at(2026, 9, 29, 21)),
            result,
        )
    }

    @Test
    fun respectsStartAndEndDates() {
        val result = DoseGenerator.occurrences(
            startDate = LocalDate.of(2026, 9, 28),
            endDate = LocalDate.of(2026, 9, 29),
            timesOfDay = listOf(8 * 60),
            windowStart = at(2026, 9, 20, 0),
            windowEnd = at(2026, 10, 5, 0),
            zone = utc,
        )
        assertEquals(listOf(at(2026, 9, 28, 8), at(2026, 9, 29, 8)), result)
    }

    @Test
    fun endBeforeStartYieldsNothing() {
        val result = DoseGenerator.occurrences(
            startDate = LocalDate.of(2026, 9, 28),
            endDate = LocalDate.of(2026, 9, 27),
            timesOfDay = listOf(8 * 60),
            windowStart = at(2026, 9, 20, 0),
            windowEnd = at(2026, 10, 5, 0),
            zone = utc,
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun duplicateTimesCollapse() {
        val result = DoseGenerator.occurrences(
            startDate = LocalDate.of(2026, 9, 28),
            endDate = LocalDate.of(2026, 9, 28),
            timesOfDay = listOf(8 * 60, 8 * 60),
            windowStart = at(2026, 9, 28, 0),
            windowEnd = at(2026, 9, 29, 0),
            zone = utc,
        )
        assertEquals(1, result.size)
    }

    @Test
    fun displayStateTransitions() {
        val scheduled = 1_000_000L
        assertEquals(DoseDisplayState.UPCOMING, displayStateOf(DoseStatus.PENDING, scheduled, scheduled - 1))
        assertEquals(DoseDisplayState.DUE, displayStateOf(DoseStatus.PENDING, scheduled, scheduled))
        assertEquals(
            DoseDisplayState.DUE,
            displayStateOf(DoseStatus.PENDING, scheduled, scheduled + MISSED_AFTER_MILLIS - 1),
        )
        assertEquals(
            DoseDisplayState.MISSED,
            displayStateOf(DoseStatus.PENDING, scheduled, scheduled + MISSED_AFTER_MILLIS),
        )
        assertEquals(DoseDisplayState.TAKEN, displayStateOf(DoseStatus.TAKEN, scheduled, 0))
    }

    @Test
    fun adherencePercentMatchesSpecExample() {
        val stats = AdherenceStats(taken = 13, missed = 2, skipped = 0)
        assertEquals(87, stats.percent)
        assertNull(AdherenceStats.EMPTY.percent)
    }
}
