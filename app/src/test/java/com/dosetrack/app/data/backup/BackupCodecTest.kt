package com.dosetrack.app.data.backup

import com.dosetrack.app.data.db.DoseEntity
import com.dosetrack.app.data.db.MedicationEntity
import com.dosetrack.app.data.db.ScheduleEntity
import com.dosetrack.app.data.model.DosageUnit
import com.dosetrack.app.data.model.DoseStatus
import com.dosetrack.app.data.model.DurationType
import com.dosetrack.app.data.model.Frequency
import com.dosetrack.app.data.model.MedicationType
import com.dosetrack.app.data.model.ReminderStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCodecTest {
    private val medication = MedicationEntity(
        id = 7,
        name = "Cipralex",
        genericName = "Escitalopram",
        dosageAmount = 1.0,
        dosageUnit = DosageUnit.TABLET,
        strength = "10 mg",
        medicationType = MedicationType.TABLET,
        frequency = Frequency.TWICE_DAILY,
        startDate = 20_000,
        durationType = DurationType.DAYS,
        durationDays = 7,
        endDate = 20_006,
        scheduleEffectiveFrom = 1_000,
        createdAt = 1_000,
        updatedAt = 2_000,
        reminderStyle = ReminderStyle.ALARM,
    )
    private val schedule = ScheduleEntity(id = 3, medicationId = 7, timeOfDay = 480, note = "After breakfast", createdAt = 1, updatedAt = 2)
    private val dose = DoseEntity(
        id = 11,
        medicationId = 7,
        scheduledAt = 5_000,
        takenAt = 5_100,
        status = DoseStatus.TAKEN,
        reminderNote = "After breakfast",
        createdAt = 1,
        updatedAt = 2,
    )

    @Test
    fun roundTripKeepsEverything() {
        val text = BackupCodec.encode(listOf(medication), listOf(schedule), listOf(dose), exportedAt = 9_999)
        val file = BackupCodec.decode(text)
        assertEquals(listOf(medication), file.medications.map { it.toEntity() })
        assertEquals(listOf(schedule), file.schedules.map { it.toEntity() })
        assertEquals(listOf(dose), file.doses.map { it.toEntity() })
        assertEquals(9_999L, file.exportedAt)
    }

    @Test
    fun rejectsGarbage() {
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode("not json at all") }
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode("""{"hello": 1}""") }
    }

    @Test
    fun rejectsOtherAppsAndNewerVersions() {
        val text = BackupCodec.encode(listOf(medication), emptyList(), emptyList(), 0)
        assertThrows(BackupFormatException::class.java) {
            BackupCodec.decode(text.replace("\"DoseTrack\"", "\"OtherApp\""))
        }
        assertThrows(BackupFormatException::class.java) {
            BackupCodec.decode(text.replace("\"schemaVersion\": 1", "\"schemaVersion\": 99"))
        }
    }

    @Test
    fun rejectsOrphanRecords() {
        val text = BackupCodec.encode(emptyList(), emptyList(), listOf(dose), 0)
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode(text) }
    }
}
