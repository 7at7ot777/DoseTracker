package com.dosetrack.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class AdherenceBucketsTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 9, 28)

    private fun at(day: LocalDate, hour: Int) =
        LocalDateTime.of(day, java.time.LocalTime.of(hour, 0)).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun alwaysReturnsSevenDaysOldestFirst() {
        val buckets = AdherenceBuckets.daily(emptyList(), today, zone)
        assertEquals(7, buckets.size)
        assertEquals(today.minusDays(6), buckets.first().date)
        assertEquals(today, buckets.last().date)
        assertEquals(null, AdherenceBuckets.total(buckets).percent)
    }

    @Test
    fun groupsByDayAndExcludesPendingFromStats() {
        val doses = listOf(
            at(today, 8) to DoseDisplayState.TAKEN,
            at(today, 14) to DoseDisplayState.DUE,
            at(today, 22) to DoseDisplayState.UPCOMING,
            at(today.minusDays(1), 8) to DoseDisplayState.MISSED,
            at(today.minusDays(1), 20) to DoseDisplayState.SKIPPED,
            // Outside the 7-day window: ignored.
            at(today.minusDays(10), 8) to DoseDisplayState.MISSED,
        )
        val buckets = AdherenceBuckets.daily(doses, today, zone)
        val todayBucket = buckets.last()
        assertEquals(1, todayBucket.taken)
        assertEquals(2, todayBucket.pending)
        assertEquals(100, todayBucket.stats.percent)

        val yesterday = buckets[5]
        assertEquals(1, yesterday.missed)
        assertEquals(1, yesterday.skipped)

        val total = AdherenceBuckets.total(buckets)
        assertEquals(AdherenceStats(taken = 1, missed = 1, skipped = 1), total)
    }

    @Test
    fun specExample() {
        val doses = List(13) { at(today, 8) to DoseDisplayState.TAKEN } +
            List(2) { at(today.minusDays(2), 8) to DoseDisplayState.MISSED }
        assertEquals(87, AdherenceBuckets.total(AdherenceBuckets.daily(doses, today, zone)).percent)
    }
}
