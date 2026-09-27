package com.dosetrack.app.domain

import com.dosetrack.app.data.model.FoodInstruction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderTextTest {
    @Test
    fun perTimeNoteWins() {
        assertEquals(
            ReminderNote.Text("Take with lunch"),
            ReminderText.noteFor("  Take with lunch ", FoodInstruction.AFTER_FOOD, "Take after breakfast"),
        )
    }

    @Test
    fun fallsBackToCustomInstructions() {
        assertEquals(
            ReminderNote.Text("Take after breakfast"),
            ReminderText.noteFor(null, FoodInstruction.CUSTOM, "Take after breakfast"),
        )
        assertEquals(
            ReminderNote.Text("Take after breakfast"),
            ReminderText.noteFor("   ", FoodInstruction.AFTER_FOOD, "Take after breakfast"),
        )
    }

    @Test
    fun fallsBackToFoodInstruction() {
        assertEquals(
            ReminderNote.Food(FoodInstruction.BEFORE_FOOD),
            ReminderText.noteFor(null, FoodInstruction.BEFORE_FOOD, null),
        )
    }

    @Test
    fun omittedWhenNothingConfigured() {
        assertNull(ReminderText.noteFor(null, FoodInstruction.ANY_TIME, null))
        assertNull(ReminderText.noteFor("", FoodInstruction.CUSTOM, " "))
    }

    @Test
    fun doseLineFormatting() {
        assertEquals("10 mg · 1 tablet", ReminderText.doseLine("10 mg", "1 tablet"))
        assertEquals("1 tablet", ReminderText.doseLine(null, "1 tablet"))
        assertEquals("1", ReminderText.formatAmount(1.0))
        assertEquals("2.5", ReminderText.formatAmount(2.5))
    }
}
