package com.dosetrack.app.domain

/** Profile limits and presentation rules (pure, unit-tested). */
object ProfileRules {
    const val MAX_PROFILES = 5
    const val MAX_NAME_LENGTH = 30

    /** Avatar colours chosen to read well with white initials in light and dark themes. */
    val PALETTE: List<Int> = listOf(
        0xFF1E6B5C.toInt(), // teal
        0xFF3558A8.toInt(), // blue
        0xFF7B3FA0.toInt(), // purple
        0xFFB23A6B.toInt(), // pink
        0xFFB5452C.toInt(), // red-orange
        0xFF8A6100.toInt(), // amber
        0xFF4E7A1E.toInt(), // green
        0xFF5A5F66.toInt(), // grey
    )

    fun canCreate(existingCount: Int): Boolean = existingCount < MAX_PROFILES

    enum class NameError { REQUIRED, TOO_LONG }

    fun validateName(name: String): NameError? = when {
        name.isBlank() -> NameError.REQUIRED
        name.trim().length > MAX_NAME_LENGTH -> NameError.TOO_LONG
        else -> null
    }

    /**
     * Up to two initials: first letters of the first and last words ("Mary Ann Lee" → "ML",
     * "Mom" → "M", "أحمد علي" → "أع"). Returns "?" for a blank name.
     */
    fun initials(name: String): String {
        val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return "?"
        val first = words.first().firstCodePointString()
        val last = if (words.size > 1) words.last().firstCodePointString() else ""
        return (first + last).uppercase()
    }

    /** Colour for a profile without an explicit one, stable per id. */
    fun defaultColor(profileId: Long): Int = PALETTE[Math.floorMod(profileId - 1, PALETTE.size.toLong()).toInt()]

    private fun String.firstCodePointString(): String = String(Character.toChars(codePointAt(0)))
}

class ProfileLimitReachedException : IllegalStateException("A maximum of ${ProfileRules.MAX_PROFILES} profiles is allowed")

class LastProfileException : IllegalStateException("The only profile can't be deleted")
