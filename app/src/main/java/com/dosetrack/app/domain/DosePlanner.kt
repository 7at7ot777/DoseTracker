package com.dosetrack.app.domain

import com.dosetrack.app.data.db.DoseEntity
import com.dosetrack.app.data.db.MedicationWithSchedules
import com.dosetrack.app.data.db.timesOfDay
import com.dosetrack.app.data.model.DoseStatus
import com.dosetrack.app.data.model.MedicationStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object DosePlanner {
    /** How far back missing dose rows are created (so days the app wasn't opened still record misses). */
    const val LOOKBACK_MILLIS: Long = 30L * 24 * 60 * 60 * 1000

    /** How far ahead dose rows (and later, alarms) are created. */
    const val LOOKAHEAD_MILLIS: Long = 48L * 60 * 60 * 1000

    /**
     * Dose rows that should exist for the active medications. Rows that already exist are
     * dropped by the unique (medicationId, scheduledAt) index on insert.
     */
    fun plan(medications: List<MedicationWithSchedules>, now: Long, zone: ZoneId): List<DoseEntity> =
        medications
            .filter { it.medication.status == MedicationStatus.ACTIVE }
            .flatMap { planFor(it, now, zone) }

    private fun planFor(data: MedicationWithSchedules, now: Long, zone: ZoneId): List<DoseEntity> {
        val medication = data.medication
        val windowStart = maxOf(medication.scheduleEffectiveFrom, now - LOOKBACK_MILLIS)
        val windowEnd = now + LOOKAHEAD_MILLIS
        val notesByMinute = data.schedules.associate { it.timeOfDay to it.note }

        return DoseGenerator.occurrences(
            startDate = LocalDate.ofEpochDay(medication.startDate),
            endDate = medication.endDate?.let(LocalDate::ofEpochDay),
            timesOfDay = data.timesOfDay(),
            windowStart = Instant.ofEpochMilli(windowStart),
            windowEnd = Instant.ofEpochMilli(windowEnd),
            zone = zone,
        ).map { instant ->
            val local = instant.atZone(zone)
            DoseEntity(
                medicationId = medication.id,
                scheduledAt = instant.toEpochMilli(),
                status = DoseStatus.PENDING,
                reminderNote = notesByMinute[local.hour * 60 + local.minute],
                createdAt = now,
                updatedAt = now,
            )
        }
    }
}
