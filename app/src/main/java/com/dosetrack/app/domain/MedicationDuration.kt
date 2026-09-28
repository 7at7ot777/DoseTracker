package com.dosetrack.app.domain

import com.dosetrack.app.data.model.DurationType
import java.time.LocalDate

/** Longest course accepted for "Number of days" (10 years). */
const val MAX_DURATION_DAYS = 3650

enum class DurationError { DAYS_REQUIRED, DAYS_NOT_POSITIVE, DAYS_TOO_LARGE, END_DATE_REQUIRED, END_BEFORE_START }

object MedicationDuration {
    /**
     * Last day of treatment (inclusive), or null for an ongoing medication.
     * A 7-day course starting Sept 28 ends Oct 4.
     */
    fun endDate(type: DurationType, startDate: LocalDate, days: Int?, endDate: LocalDate?): LocalDate? =
        when (type) {
            DurationType.ONGOING -> null
            DurationType.DAYS -> days?.takeIf { it > 0 }?.let { startDate.plusDays(it - 1L) }
            DurationType.END_DATE -> endDate
        }

    fun validate(type: DurationType, startDate: LocalDate, days: Int?, endDate: LocalDate?): DurationError? =
        when (type) {
            DurationType.ONGOING -> null
            DurationType.DAYS -> when {
                days == null -> DurationError.DAYS_REQUIRED
                days <= 0 -> DurationError.DAYS_NOT_POSITIVE
                days > MAX_DURATION_DAYS -> DurationError.DAYS_TOO_LARGE
                else -> null
            }
            DurationType.END_DATE -> when {
                endDate == null -> DurationError.END_DATE_REQUIRED
                endDate.isBefore(startDate) -> DurationError.END_BEFORE_START
                else -> null
            }
        }

    /** Parses the numeric "Number of days" field; only plain positive whole numbers are accepted. */
    fun parseDays(input: String): Int? = Digits.normalize(input).trim()
        .takeIf { s -> s.isNotEmpty() && s.all { it in '0'..'9' } }
        ?.toIntOrNull()
}
