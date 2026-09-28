package com.dosetrack.app.reminders

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Locks in the multi-profile rule: which profile the UI shows is presentation state only.
 * Reminder scheduling, delivery and dose sync must never read it, so switching profiles can't
 * cancel, disable or change any profile's reminders.
 */
class ReminderIndependenceTest {
    private val forbidden = listOf("activeProfileId", "todayShowsEveryone", "activeProfile", "selection")

    // Unit tests run with the module directory (app/) as working directory.
    private val sourceRoot = File("src/main/java/com/dosetrack/app")

    @Test
    fun reminderCodeNeverReadsTheSelectedProfile() {
        val files = File(sourceRoot, "reminders").listFiles { f -> f.extension == "kt" }.orEmpty().toList() +
            File(sourceRoot, "domain/DoseSync.kt")
        assertTrue("Reminder sources not found from ${File(".").absolutePath}", files.size >= 5 && files.all { it.exists() })

        val offenders = files.flatMap { file ->
            val text = file.readText()
            forbidden.filter { text.contains(it) }.map { "${file.name}: $it" }
        }
        if (offenders.isNotEmpty()) fail("Reminder code depends on UI profile selection: $offenders")
    }
}
