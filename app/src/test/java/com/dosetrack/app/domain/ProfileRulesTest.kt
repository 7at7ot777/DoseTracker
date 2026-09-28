package com.dosetrack.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileRulesTest {
    @Test
    fun limitIsFive() {
        assertTrue(ProfileRules.canCreate(0))
        assertTrue(ProfileRules.canCreate(4))
        assertFalse(ProfileRules.canCreate(5))
        assertFalse(ProfileRules.canCreate(6))
    }

    @Test
    fun nameValidation() {
        assertEquals(ProfileRules.NameError.REQUIRED, ProfileRules.validateName("   "))
        assertEquals(ProfileRules.NameError.TOO_LONG, ProfileRules.validateName("x".repeat(31)))
        assertNull(ProfileRules.validateName("  Mom  "))
    }

    @Test
    fun initials() {
        assertEquals("M", ProfileRules.initials("Mom"))
        assertEquals("ML", ProfileRules.initials("Mary Ann  Lee"))
        assertEquals("أع", ProfileRules.initials("أحمد علي"))
        assertEquals("?", ProfileRules.initials(" "))
    }

    @Test
    fun defaultColorIsStableAndInPalette() {
        assertEquals(ProfileRules.PALETTE[0], ProfileRules.defaultColor(1))
        assertEquals(ProfileRules.defaultColor(3), ProfileRules.defaultColor(3 + ProfileRules.PALETTE.size.toLong()))
    }
}
