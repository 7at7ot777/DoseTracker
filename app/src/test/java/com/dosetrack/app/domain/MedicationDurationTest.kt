package com.dosetrack.app.domain

import com.dosetrack.app.data.model.DurationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class MedicationDurationTest {
    private val start = LocalDate.of(2026, 9, 28)

    @Test
    fun sevenDayCourseEndsOnSixthDayAfterStart() {
        assertEquals(
            LocalDate.of(2026, 10, 4),
            MedicationDuration.endDate(DurationType.DAYS, start, 7, null),
        )
    }

    @Test
    fun oneDayCourseEndsSameDay() {
        assertEquals(start, MedicationDuration.endDate(DurationType.DAYS, start, 1, null))
    }

    @Test
    fun ongoingHasNoEndDate() {
        assertNull(MedicationDuration.endDate(DurationType.ONGOING, start, 7, LocalDate.of(2026, 10, 1)))
    }

    @Test
    fun explicitEndDateIsUsed() {
        val end = LocalDate.of(2026, 10, 7)
        assertEquals(end, MedicationDuration.endDate(DurationType.END_DATE, start, null, end))
    }

    @Test
    fun validation() {
        assertEquals(DurationError.DAYS_REQUIRED, MedicationDuration.validate(DurationType.DAYS, start, null, null))
        assertEquals(DurationError.DAYS_NOT_POSITIVE, MedicationDuration.validate(DurationType.DAYS, start, 0, null))
        assertEquals(
            DurationError.DAYS_TOO_LARGE,
            MedicationDuration.validate(DurationType.DAYS, start, MAX_DURATION_DAYS + 1, null),
        )
        assertNull(MedicationDuration.validate(DurationType.DAYS, start, 7, null))
        assertEquals(
            DurationError.END_BEFORE_START,
            MedicationDuration.validate(DurationType.END_DATE, start, null, start.minusDays(1)),
        )
        assertEquals(DurationError.END_DATE_REQUIRED, MedicationDuration.validate(DurationType.END_DATE, start, null, null))
        assertNull(MedicationDuration.validate(DurationType.ONGOING, start, null, null))
    }

    @Test
    fun parseDaysAcceptsOnlyWholeNumbers() {
        assertEquals(7, MedicationDuration.parseDays(" 7 "))
        assertNull(MedicationDuration.parseDays("7.5"))
        assertNull(MedicationDuration.parseDays("-3"))
        assertNull(MedicationDuration.parseDays(""))
        assertNull(MedicationDuration.parseDays("abc"))
    }
}
