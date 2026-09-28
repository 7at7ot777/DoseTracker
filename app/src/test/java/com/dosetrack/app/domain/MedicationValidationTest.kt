package com.dosetrack.app.domain

import com.dosetrack.app.data.model.DurationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MedicationValidationTest {
    private val start = LocalDate.of(2026, 9, 28)

    private fun validate(
        name: String = "Vitamin D",
        amount: String = "1",
        times: List<Int> = listOf(9 * 60),
        type: DurationType = DurationType.ONGOING,
        days: String = "",
        end: LocalDate? = null,
    ) = MedicationValidation.validate(name, amount, times, type, start, days, end)

    @Test
    fun validInputPasses() {
        assertTrue(validate().isValid)
        assertTrue(validate(type = DurationType.DAYS, days = "7").isValid)
    }

    @Test
    fun reportsFieldErrors() {
        val result = validate(name = " ", amount = "0", times = emptyList())
        assertEquals(
            setOf(
                MedicationFieldError.NAME_REQUIRED,
                MedicationFieldError.AMOUNT_INVALID,
                MedicationFieldError.NO_TIMES,
            ),
            result.fieldErrors,
        )
        assertFalse(result.isValid)
    }

    @Test
    fun detectsDuplicateTimes() {
        val result = validate(times = listOf(480, 600, 480))
        assertTrue(MedicationFieldError.DUPLICATE_TIMES in result.fieldErrors)
        assertEquals(setOf(480), MedicationValidation.duplicateTimes(listOf(480, 600, 480)))
    }

    @Test
    fun durationErrors() {
        assertEquals(DurationError.DAYS_REQUIRED, validate(type = DurationType.DAYS, days = "").durationError)
        assertEquals(DurationError.DAYS_NOT_POSITIVE, validate(type = DurationType.DAYS, days = "abc").durationError)
        assertEquals(
            DurationError.END_BEFORE_START,
            validate(type = DurationType.END_DATE, end = start.minusDays(2)).durationError,
        )
    }

    @Test
    fun parsesAmountsAndArabicDigits() {
        assertEquals(2.5, MedicationValidation.parseAmount("2,5")!!, 0.0)
        assertEquals(7.0, MedicationValidation.parseAmount("٧")!!, 0.0)
        assertNull(MedicationValidation.parseAmount("-1"))
        assertNull(MedicationValidation.parseAmount("abc"))
        assertEquals(14, MedicationDuration.parseDays("١٤"))
    }
}
