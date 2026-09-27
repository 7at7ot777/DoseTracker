package com.dosetrack.app.data.model

/** Unit the user takes per dose, e.g. "1 tablet", "5 ml". */
enum class DosageUnit { TABLET, CAPSULE, ML, MG, DROPS, OTHER }

enum class MedicationType { TABLET, CAPSULE, LIQUID, INJECTION, CREAM, DROPS, OTHER }

/**
 * How often a medication is taken. Presets come with default times the user can adjust;
 * [CUSTOM] lets the user add any number of times.
 */
enum class Frequency(val timesPerDay: Int?, val defaultTimes: List<Int>) {
    ONCE_DAILY(1, listOf(9 * 60)),
    TWICE_DAILY(2, listOf(9 * 60, 21 * 60)),
    THREE_TIMES_DAILY(3, listOf(8 * 60, 14 * 60, 20 * 60)),
    FOUR_TIMES_DAILY(4, listOf(8 * 60, 12 * 60, 16 * 60, 20 * 60)),
    CUSTOM(null, listOf(9 * 60)),
}

/**
 * How the treatment length was entered. The resolved last day is always stored in
 * MedicationEntity.endDate so dose generation only needs that one field.
 */
enum class DurationType { ONGOING, DAYS, END_DATE }

enum class FoodInstruction { ANY_TIME, BEFORE_FOOD, WITH_FOOD, AFTER_FOOD, CUSTOM }

/** ARCHIVED is the soft-delete state: hidden from lists, history kept. */
enum class MedicationStatus { ACTIVE, PAUSED, ARCHIVED }

/** Stored dose status. "Upcoming" vs "Due" is derived from [PENDING] and the clock. */
enum class DoseStatus { PENDING, TAKEN, SKIPPED, MISSED }
