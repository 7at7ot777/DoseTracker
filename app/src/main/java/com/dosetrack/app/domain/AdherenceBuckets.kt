package com.dosetrack.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Dose counts for one local day. Upcoming/due doses are [pending] and don't affect the percentage. */
data class DayAdherence(
    val date: LocalDate,
    val taken: Int = 0,
    val missed: Int = 0,
    val skipped: Int = 0,
    val pending: Int = 0,
) {
    val stats: AdherenceStats get() = AdherenceStats(taken, missed, skipped)
    val total: Int get() = taken + missed + skipped + pending
}

object AdherenceBuckets {
    /**
     * Groups doses by local day for the [days] days ending on [today] (oldest first).
     * Days without doses are included so a chart always has [days] entries.
     */
    fun daily(
        doses: List<Pair<Long, DoseDisplayState>>,
        today: LocalDate,
        zone: ZoneId,
        days: Int = 7,
    ): List<DayAdherence> {
        val first = today.minusDays(days - 1L)
        val byDay = doses
            .groupBy { (scheduledAt, _) -> Instant.ofEpochMilli(scheduledAt).atZone(zone).toLocalDate() }
        return (0 until days).map { offset ->
            val date = first.plusDays(offset.toLong())
            val states = byDay[date].orEmpty().map { it.second }
            DayAdherence(
                date = date,
                taken = states.count { it == DoseDisplayState.TAKEN },
                missed = states.count { it == DoseDisplayState.MISSED },
                skipped = states.count { it == DoseDisplayState.SKIPPED },
                pending = states.count { it == DoseDisplayState.UPCOMING || it == DoseDisplayState.DUE },
            )
        }
    }

    fun total(buckets: List<DayAdherence>): AdherenceStats = AdherenceStats(
        taken = buckets.sumOf { it.taken },
        missed = buckets.sumOf { it.missed },
        skipped = buckets.sumOf { it.skipped },
    )
}
