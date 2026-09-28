package com.dosetrack.app.domain

import com.dosetrack.app.data.db.MedicationEntity
import com.dosetrack.app.data.db.MedicationWithSchedules
import com.dosetrack.app.data.db.ScheduleEntity
import com.dosetrack.app.data.model.DosageUnit
import com.dosetrack.app.data.model.DoseStatus
import com.dosetrack.app.data.model.Frequency
import com.dosetrack.app.data.model.MedicationStatus
import com.dosetrack.app.data.model.MedicationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class DosePlannerTest {
    private val zone = ZoneOffset.UTC

    private fun millis(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0) =
        LocalDateTime.of(y, mo, d, h, mi).toInstant(zone).toEpochMilli()

    private fun medication(
        effectiveFrom: Long,
        times: List<Pair<Int, String?>>,
        status: MedicationStatus = MedicationStatus.ACTIVE,
        start: LocalDate = LocalDate.of(2026, 9, 1),
        end: LocalDate? = null,
    ) = MedicationWithSchedules(
        medication = MedicationEntity(
            id = 1,
            name = "Vitamin D",
            dosageAmount = 1.0,
            dosageUnit = DosageUnit.TABLET,
            medicationType = MedicationType.TABLET,
            frequency = Frequency.CUSTOM,
            startDate = start.toEpochDay(),
            endDate = end?.toEpochDay(),
            status = status,
            scheduleEffectiveFrom = effectiveFrom,
            createdAt = effectiveFrom,
            updatedAt = effectiveFrom,
        ),
        schedules = times.mapIndexed { i, (minutes, note) ->
            ScheduleEntity(id = i.toLong(), medicationId = 1, timeOfDay = minutes, note = note, createdAt = 0, updatedAt = 0)
        },
    )

    @Test
    fun startsAtEffectiveTimeAndCopiesNotes() {
        val created = millis(2026, 9, 28, 8, 30)
        val med = medication(created, listOf(8 * 60 to "After breakfast", 14 * 60 to null))
        val doses = DosePlanner.plan(listOf(med), now = created, zone = zone)

        // 08:00 today is before creation and must not be generated.
        assertEquals(millis(2026, 9, 28, 14), doses.first().scheduledAt)
        assertTrue(doses.all { it.status == DoseStatus.PENDING })
        assertEquals("After breakfast", doses.first { it.scheduledAt == millis(2026, 9, 29, 8) }.reminderNote)
        assertNull(doses.first().reminderNote)
        // 48 h look-ahead: 14:00 on 28th, 08:00 + 14:00 on 29th, 08:00 on 30th.
        assertEquals(4, doses.size)
    }

    @Test
    fun backfillsDaysTheAppWasClosed() {
        val created = millis(2026, 9, 20, 0)
        val now = millis(2026, 9, 23, 12)
        val doses = DosePlanner.plan(listOf(medication(created, listOf(9 * 60 to null))), now, zone)
        assertEquals(millis(2026, 9, 20, 9), doses.first().scheduledAt)
    }

    @Test
    fun respectsEndDate() {
        val created = millis(2026, 9, 28, 0)
        val med = medication(created, listOf(9 * 60 to null), end = LocalDate.of(2026, 9, 28))
        val doses = DosePlanner.plan(listOf(med), created, zone)
        assertEquals(listOf(millis(2026, 9, 28, 9)), doses.map { it.scheduledAt })
    }

    @Test
    fun skipsPausedAndArchived() {
        val created = millis(2026, 9, 28, 0)
        val paused = medication(created, listOf(9 * 60 to null), status = MedicationStatus.PAUSED)
        val archived = medication(created, listOf(9 * 60 to null), status = MedicationStatus.ARCHIVED)
        assertTrue(DosePlanner.plan(listOf(paused, archived), created, zone).isEmpty())
    }

    @Test
    fun snoozeDelaysMissed() {
        val scheduled = 1_000_000L
        val snoozed = scheduled + 60 * 60 * 1000L
        val now = scheduled + MISSED_AFTER_MILLIS + 1
        assertEquals(DoseDisplayState.MISSED, displayStateOf(DoseStatus.PENDING, scheduled, now))
        assertEquals(DoseDisplayState.DUE, displayStateOf(DoseStatus.PENDING, scheduled, now, snoozed))
    }
}
