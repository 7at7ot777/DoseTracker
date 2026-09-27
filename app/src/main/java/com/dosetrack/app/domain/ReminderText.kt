package com.dosetrack.app.domain

import com.dosetrack.app.data.model.FoodInstruction

/** Which note line a reminder should show, before localisation. */
sealed interface ReminderNote {
    data class Text(val text: String) : ReminderNote
    data class Food(val instruction: FoodInstruction) : ReminderNote
}

object ReminderText {
    /**
     * Picks the single note shown in a dose reminder:
     * the per-time note, else the medication's custom instructions, else its food instruction.
     * Returns null when nothing is configured (or the instruction is "any time"), so the line is omitted.
     */
    fun noteFor(
        doseNote: String?,
        foodInstruction: FoodInstruction,
        customInstructions: String?,
    ): ReminderNote? {
        doseNote.clean()?.let { return ReminderNote.Text(it) }
        if (foodInstruction == FoodInstruction.CUSTOM) {
            return customInstructions.clean()?.let { ReminderNote.Text(it) }
        }
        customInstructions.clean()?.let { return ReminderNote.Text(it) }
        return if (foodInstruction == FoodInstruction.ANY_TIME) null else ReminderNote.Food(foodInstruction)
    }

    /** "10 mg · 1 tablet", or just "1 tablet" without a strength. */
    fun doseLine(strength: String?, amountWithUnit: String): String =
        strength.clean()?.let { "$it · $amountWithUnit" } ?: amountWithUnit

    /** Formats 1.0 as "1" and 2.5 as "2.5". */
    fun formatAmount(amount: Double): String =
        if (amount == Math.floor(amount) && !amount.isInfinite()) amount.toLong().toString()
        else amount.toBigDecimal().stripTrailingZeros().toPlainString()

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}
