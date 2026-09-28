package com.dosetrack.app.ui

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.dosetrack.app.R
import com.dosetrack.app.data.db.MedicationEntity
import com.dosetrack.app.data.model.DosageUnit
import com.dosetrack.app.data.model.DurationType
import com.dosetrack.app.data.model.FoodInstruction
import com.dosetrack.app.data.model.Frequency
import com.dosetrack.app.data.model.MedicationType
import com.dosetrack.app.domain.ReminderNote
import com.dosetrack.app.domain.ReminderText
import java.time.LocalDate
import java.util.Locale
import kotlin.math.floor

/**
 * Resource-based labels, usable from Compose and from non-UI code (notifications),
 * so both always show identical text.
 */
object Labels {
    fun dosageUnit(res: Resources, unit: DosageUnit): String = res.getString(
        when (unit) {
            DosageUnit.TABLET -> R.string.unit_tablet
            DosageUnit.CAPSULE -> R.string.unit_capsule
            DosageUnit.ML -> R.string.unit_ml
            DosageUnit.MG -> R.string.unit_mg
            DosageUnit.DROPS -> R.string.unit_drops
            DosageUnit.OTHER -> R.string.unit_other
        },
    )

    fun medicationType(res: Resources, type: MedicationType): String = res.getString(
        when (type) {
            MedicationType.TABLET -> R.string.type_tablet
            MedicationType.CAPSULE -> R.string.type_capsule
            MedicationType.LIQUID -> R.string.type_liquid
            MedicationType.INJECTION -> R.string.type_injection
            MedicationType.CREAM -> R.string.type_cream
            MedicationType.DROPS -> R.string.type_drops
            MedicationType.OTHER -> R.string.type_other
        },
    )

    /** Chip label in the form. */
    fun frequencyOption(res: Resources, frequency: Frequency): String = res.getString(
        when (frequency) {
            Frequency.ONCE_DAILY -> R.string.frequency_once
            Frequency.TWICE_DAILY -> R.string.frequency_twice
            Frequency.THREE_TIMES_DAILY -> R.string.frequency_three
            Frequency.FOUR_TIMES_DAILY -> R.string.frequency_four
            Frequency.CUSTOM -> R.string.frequency_custom
        },
    )

    /** Summary label; custom schedules show their count ("5 times a day"). */
    fun frequency(res: Resources, frequency: Frequency, timesPerDay: Int): String =
        if (frequency == Frequency.CUSTOM) {
            res.getQuantityString(R.plurals.frequency_custom_times, timesPerDay, timesPerDay)
        } else {
            frequencyOption(res, frequency)
        }

    fun foodInstruction(res: Resources, instruction: FoodInstruction): String = res.getString(
        when (instruction) {
            FoodInstruction.ANY_TIME -> R.string.food_any_time
            FoodInstruction.BEFORE_FOOD -> R.string.food_before
            FoodInstruction.WITH_FOOD -> R.string.food_with
            FoodInstruction.AFTER_FOOD -> R.string.food_after
            FoodInstruction.CUSTOM -> R.string.food_custom
        },
    )

    /** "1 tablet", "2 tablets", "5 ml". */
    fun amountWithUnit(res: Resources, amount: Double, unit: DosageUnit): String {
        val plural = when (unit) {
            DosageUnit.TABLET -> R.plurals.amount_tablet
            DosageUnit.CAPSULE -> R.plurals.amount_capsule
            DosageUnit.ML -> R.plurals.amount_ml
            DosageUnit.MG -> R.plurals.amount_mg
            DosageUnit.DROPS -> R.plurals.amount_drops
            DosageUnit.OTHER -> R.plurals.amount_other
        }
        return res.getQuantityString(plural, pluralQuantity(amount), ReminderText.formatAmount(amount))
    }

    /** "10 mg · 1 tablet". */
    fun doseLine(res: Resources, medication: MedicationEntity): String = ReminderText.doseLine(
        medication.strength,
        amountWithUnit(res, medication.dosageAmount, medication.dosageUnit),
    )

    /** "Ongoing", "7-day course · Ends Oct 4", "Until Oct 7". */
    fun duration(
        res: Resources,
        type: DurationType,
        days: Int?,
        endDate: LocalDate?,
        locale: Locale,
    ): String = when {
        type == DurationType.DAYS && days != null && endDate != null ->
            res.getQuantityString(R.plurals.duration_course, days, days, Formats.date(endDate, locale))
        type != DurationType.ONGOING && endDate != null ->
            res.getString(R.string.duration_until, Formats.date(endDate, locale))
        else -> res.getString(R.string.duration_ongoing)
    }

    fun duration(res: Resources, medication: MedicationEntity, locale: Locale): String = duration(
        res,
        medication.durationType,
        medication.durationDays,
        medication.endDate?.let(LocalDate::ofEpochDay),
        locale,
    )

    /** Localised text of the note shown on dose cards and reminders, or null when there is none. */
    fun reminderNote(res: Resources, note: ReminderNote?): String? = when (note) {
        null -> null
        is ReminderNote.Text -> note.text
        is ReminderNote.Food -> when (note.instruction) {
            FoodInstruction.BEFORE_FOOD -> res.getString(R.string.reminder_food_before)
            FoodInstruction.WITH_FOOD -> res.getString(R.string.reminder_food_with)
            FoodInstruction.AFTER_FOOD -> res.getString(R.string.reminder_food_after)
            FoodInstruction.ANY_TIME, FoodInstruction.CUSTOM -> null
        }
    }

    /** Note for one dose: its per-time note, else the medication's instructions. */
    fun doseNote(res: Resources, doseNote: String?, medication: MedicationEntity): String? = reminderNote(
        res,
        ReminderText.noteFor(doseNote, medication.foodInstruction, medication.customInstructions),
    )

    /** Fractional amounts ("2.5") use the "other" plural form in both English and Arabic. */
    private fun pluralQuantity(amount: Double): Int =
        if (amount == floor(amount) && amount < 1_000_000) amount.toInt() else 100
}

/** Resources that recompose when the configuration (language, night mode) changes. */
@Composable
@ReadOnlyComposable
fun appResources(): Resources {
    LocalConfiguration.current
    return LocalContext.current.resources
}

@Composable
@ReadOnlyComposable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]
