package com.dosetrack.app.domain

import com.dosetrack.app.data.model.DurationType
import java.time.LocalDate

enum class MedicationFieldError { NAME_REQUIRED, AMOUNT_INVALID, NO_TIMES, DUPLICATE_TIMES }

data class MedicationValidationResult(
    val fieldErrors: Set<MedicationFieldError>,
    val durationError: DurationError?,
) {
    val isValid: Boolean get() = fieldErrors.isEmpty() && durationError == null
}

object MedicationValidation {
    /** Accepts "1", "2.5", "2,5" and Arabic-Indic digits; returns null unless a positive finite number. */
    fun parseAmount(text: String): Double? =
        Digits.normalize(text).trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() }

    fun duplicateTimes(times: List<Int>): Set<Int> =
        times.groupingBy { it }.eachCount().filterValues { it > 1 }.keys

    fun validate(
        name: String,
        amountText: String,
        times: List<Int>,
        durationType: DurationType,
        startDate: LocalDate,
        daysText: String,
        endDate: LocalDate?,
    ): MedicationValidationResult {
        val errors = mutableSetOf<MedicationFieldError>()
        if (name.isBlank()) errors += MedicationFieldError.NAME_REQUIRED
        if (parseAmount(amountText) == null) errors += MedicationFieldError.AMOUNT_INVALID
        if (times.isEmpty()) errors += MedicationFieldError.NO_TIMES
        if (duplicateTimes(times).isNotEmpty()) errors += MedicationFieldError.DUPLICATE_TIMES

        val days = if (durationType == DurationType.DAYS) parseDaysOrInvalid(daysText) else null
        val durationError = MedicationDuration.validate(durationType, startDate, days, endDate)
        return MedicationValidationResult(errors, durationError)
    }

    /** Non-empty but unparseable input maps to 0 so it reports "must be positive" rather than "required". */
    private fun parseDaysOrInvalid(text: String): Int? =
        if (text.isBlank()) null else MedicationDuration.parseDays(text) ?: 0
}

object Digits {
    /** Maps Arabic-Indic (U+0660..U+0669) and Eastern Arabic-Indic (U+06F0..U+06F9) digits to ASCII. */
    fun normalize(text: String): String = buildString(text.length) {
        for (c in text) {
            append(
                when (c) {
                    in '٠'..'٩' -> '0' + (c - '٠')
                    in '۰'..'۹' -> '0' + (c - '۰')
                    '٫' -> '.' // Arabic decimal separator
                    else -> c
                },
            )
        }
    }
}
