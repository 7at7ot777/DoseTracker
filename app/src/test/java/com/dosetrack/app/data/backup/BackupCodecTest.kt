package com.dosetrack.app.data.backup

import com.dosetrack.app.data.db.DoseEntity
import com.dosetrack.app.data.db.MedicationEntity
import com.dosetrack.app.data.db.ProfileEntity
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
    private val me = ProfileEntity(id = 1, name = "Me", createdAt = 1, updatedAt = 1)
    private val mom = ProfileEntity(id = 2, name = "Mom", colorArgb = 0xFF3558A8.toInt(), createdAt = 2, updatedAt = 2)

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
        profileId = 2,
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

    private fun encode(
        profiles: List<ProfileEntity> = listOf(me, mom),
        medications: List<MedicationEntity> = listOf(medication),
        doses: List<DoseEntity> = listOf(dose),
    ) = BackupCodec.encode(profiles, medications, listOf(schedule).filter { medications.isNotEmpty() }, doses, 9_999)

    @Test
    fun roundTripKeepsEverything() {
        val file = BackupCodec.decode(encode())
        assertEquals(listOf(me, mom), file.profiles.map { it.toEntity() })
        assertEquals(listOf(medication), file.medications.map { it.toEntity() })
        assertEquals(listOf(schedule), file.schedules.map { it.toEntity() })
        assertEquals(listOf(dose), file.doses.map { it.toEntity() })
        assertEquals(9_999L, file.exportedAt)
    }

    @Test
    fun photosAreNotExported() {
        val withPhoto = mom.copy(photoPath = "/data/avatars/mom.jpg")
        val file = BackupCodec.decode(encode(profiles = listOf(me, withPhoto)))
        assertEquals(null, file.profiles[1].toEntity().photoPath)
    }

    @Test
    fun importsVersionOneFileAsSingleProfile() {
        val v1 = """
            {
              "app": "DoseTrack",
              "schemaVersion": 1,
              "exportedAt": 42,
              "medications": [{
                "id": 7, "name": "Vitamin D", "dosageAmount": 1.0, "dosageUnit": "TABLET",
                "medicationType": "TABLET", "frequency": "ONCE_DAILY", "foodInstruction": "ANY_TIME",
                "startDate": 20000, "durationType": "ONGOING", "status": "ACTIVE",
                "scheduleEffectiveFrom": 1, "createdAt": 1, "updatedAt": 1
              }],
              "schedules": [],
              "doses": []
            }
        """.trimIndent()
        val file = BackupCodec.decode(v1)
        assertEquals(1, file.profiles.size)
        assertEquals(BackupCodec.V1_PROFILE_NAME, file.profiles[0].name)
        assertEquals(file.profiles[0].id, file.medications[0].profileId)
    }

    @Test
    fun rejectsGarbage() {
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode("not json at all") }
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode("""{"hello": 1}""") }
    }

    @Test
    fun rejectsOtherAppsAndNewerVersions() {
        val text = encode()
        assertThrows(BackupFormatException::class.java) {
            BackupCodec.decode(text.replace("\"DoseTrack\"", "\"OtherApp\""))
        }
        assertThrows(BackupFormatException::class.java) {
            BackupCodec.decode(text.replace("\"schemaVersion\": 2", "\"schemaVersion\": 99"))
        }
    }

    @Test
    fun rejectsMoreThanFiveProfiles() {
        val six = (1L..6L).map { ProfileEntity(id = it, name = "P$it", createdAt = it, updatedAt = it) }
        assertThrows(BackupFormatException::class.java) {
            BackupCodec.decode(encode(profiles = six, medications = emptyList(), doses = emptyList()))
        }
    }

    @Test
    fun rejectsOrphanRecords() {
        // Medication of a profile that isn't in the file.
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode(encode(profiles = listOf(me))) }
        // Dose of a medication that isn't in the file.
        assertThrows(BackupFormatException::class.java) {
            BackupCodec.decode(encode(medications = emptyList()))
        }
    }
}
