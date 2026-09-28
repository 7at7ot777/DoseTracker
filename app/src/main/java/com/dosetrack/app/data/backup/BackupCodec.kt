package com.dosetrack.app.data.backup

import com.dosetrack.app.data.db.DoseEntity
import com.dosetrack.app.data.db.MedicationEntity
import com.dosetrack.app.data.db.ProfileEntity
import com.dosetrack.app.data.db.ScheduleEntity
import com.dosetrack.app.data.model.DosageUnit
import com.dosetrack.app.data.model.DoseStatus
import com.dosetrack.app.data.model.DurationType
import com.dosetrack.app.data.model.FoodInstruction
import com.dosetrack.app.data.model.Frequency
import com.dosetrack.app.data.model.MedicationStatus
import com.dosetrack.app.data.model.MedicationType
import com.dosetrack.app.data.model.ReminderStyle
import com.dosetrack.app.domain.ProfileRules
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class BackupFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Versioned export file. Enums are stored by name; unknown JSON keys are ignored on import.
 * v1 (single person) has no profiles; it is imported as one profile. Avatar photos are not included.
 */
@Serializable
data class BackupFile(
    val app: String = BackupCodec.APP_ID,
    val schemaVersion: Int = BackupCodec.SCHEMA_VERSION,
    val exportedAt: Long,
    val profiles: List<ProfileDto> = emptyList(),
    val medications: List<MedicationDto>,
    val schedules: List<ScheduleDto>,
    val doses: List<DoseDto>,
)

@Serializable
data class ProfileDto(
    val id: Long,
    val name: String,
    val colorArgb: Int? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class MedicationDto(
    val id: Long,
    val name: String,
    val genericName: String? = null,
    val dosageAmount: Double,
    val dosageUnit: DosageUnit,
    val strength: String? = null,
    val medicationType: MedicationType,
    val frequency: Frequency,
    val foodInstruction: FoodInstruction,
    val customInstructions: String? = null,
    val notes: String? = null,
    val startDate: Long,
    val durationType: DurationType,
    val durationDays: Int? = null,
    val endDate: Long? = null,
    val status: MedicationStatus,
    val scheduleEffectiveFrom: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val reminderStyle: ReminderStyle? = null,
    /** Absent in v1 files, where everything belongs to the single imported profile 1. */
    val profileId: Long = 1,
)

@Serializable
data class ScheduleDto(
    val id: Long,
    val medicationId: Long,
    val timeOfDay: Int,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class DoseDto(
    val id: Long,
    val medicationId: Long,
    val scheduledAt: Long,
    val takenAt: Long? = null,
    val status: DoseStatus,
    val snoozedUntil: Long? = null,
    val reminderNote: String? = null,
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

object BackupCodec {
    const val APP_ID = "DoseTrack"
    const val SCHEMA_VERSION = 2

    /** Name of the profile created when importing a v1 (pre-profiles) backup. */
    const val V1_PROFILE_NAME = "Me"

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(
        profiles: List<ProfileEntity>,
        medications: List<MedicationEntity>,
        schedules: List<ScheduleEntity>,
        doses: List<DoseEntity>,
        exportedAt: Long,
    ): String = json.encodeToString(
        BackupFile.serializer(),
        BackupFile(
            exportedAt = exportedAt,
            profiles = profiles.map { it.toDto() },
            medications = medications.map { it.toDto() },
            schedules = schedules.map { it.toDto() },
            doses = doses.map { it.toDto() },
        ),
    )

    /** @throws BackupFormatException when the text is not a usable DoseTrack backup. */
    fun decode(text: String): BackupFile {
        val file = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: IllegalArgumentException) {
            // SerializationException is an IllegalArgumentException.
            throw BackupFormatException("Not a valid backup file", e)
        }
        if (file.app != APP_ID) throw BackupFormatException("Not a DoseTrack backup")
        if (file.schemaVersion > SCHEMA_VERSION) {
            throw BackupFormatException("Backup is from a newer app version (${file.schemaVersion})")
        }
        // v1 files have no profiles: everything becomes one profile (id 1, the DTO default).
        val withProfiles = if (file.profiles.isEmpty()) {
            file.copy(profiles = listOf(ProfileDto(1, V1_PROFILE_NAME, null, file.exportedAt, file.exportedAt)))
        } else {
            file
        }
        val profileIds = withProfiles.profiles.map { it.id }
        if (profileIds.size > ProfileRules.MAX_PROFILES) {
            throw BackupFormatException("Backup has more than ${ProfileRules.MAX_PROFILES} profiles")
        }
        if (profileIds.toSet().size != profileIds.size) throw BackupFormatException("Duplicate profile ids")
        if (withProfiles.medications.any { it.profileId !in profileIds }) {
            throw BackupFormatException("Backup references unknown profiles")
        }
        val medicationIds = withProfiles.medications.map { it.id }.toSet()
        if (withProfiles.schedules.any { it.medicationId !in medicationIds } ||
            withProfiles.doses.any { it.medicationId !in medicationIds }
        ) {
            throw BackupFormatException("Backup references unknown medications")
        }
        return withProfiles
    }
}

fun ProfileEntity.toDto() = ProfileDto(id = id, name = name, colorArgb = colorArgb, createdAt = createdAt, updatedAt = updatedAt)

/** Photos are not part of backups, so restored profiles have none. */
fun ProfileDto.toEntity() = ProfileEntity(
    id = id,
    name = name,
    colorArgb = colorArgb,
    photoPath = null,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun MedicationEntity.toDto() = MedicationDto(
    id = id, name = name, genericName = genericName, dosageAmount = dosageAmount, dosageUnit = dosageUnit,
    strength = strength, medicationType = medicationType, frequency = frequency, foodInstruction = foodInstruction,
    customInstructions = customInstructions, notes = notes, startDate = startDate, durationType = durationType,
    durationDays = durationDays, endDate = endDate, status = status, scheduleEffectiveFrom = scheduleEffectiveFrom,
    createdAt = createdAt, updatedAt = updatedAt, reminderStyle = reminderStyle, profileId = profileId,
)

fun MedicationDto.toEntity() = MedicationEntity(
    id = id, name = name, genericName = genericName, dosageAmount = dosageAmount, dosageUnit = dosageUnit,
    strength = strength, medicationType = medicationType, frequency = frequency, foodInstruction = foodInstruction,
    customInstructions = customInstructions, notes = notes, startDate = startDate, durationType = durationType,
    durationDays = durationDays, endDate = endDate, status = status, scheduleEffectiveFrom = scheduleEffectiveFrom,
    createdAt = createdAt, updatedAt = updatedAt, reminderStyle = reminderStyle, profileId = profileId,
)

fun ScheduleEntity.toDto() = ScheduleDto(id, medicationId, timeOfDay, note, createdAt, updatedAt)

fun ScheduleDto.toEntity() = ScheduleEntity(id, medicationId, timeOfDay, note, createdAt, updatedAt)

fun DoseEntity.toDto() = DoseDto(
    id = id, medicationId = medicationId, scheduledAt = scheduledAt, takenAt = takenAt, status = status,
    snoozedUntil = snoozedUntil, reminderNote = reminderNote, notes = notes, createdAt = createdAt, updatedAt = updatedAt,
)

fun DoseDto.toEntity() = DoseEntity(
    id = id, medicationId = medicationId, scheduledAt = scheduledAt, takenAt = takenAt, status = status,
    snoozedUntil = snoozedUntil, reminderNote = reminderNote, notes = notes, createdAt = createdAt, updatedAt = updatedAt,
)
