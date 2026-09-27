package com.dosetrack.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.dosetrack.app.data.model.DosageUnit
import com.dosetrack.app.data.model.DoseStatus
import com.dosetrack.app.data.model.DurationType
import com.dosetrack.app.data.model.FoodInstruction
import com.dosetrack.app.data.model.Frequency
import com.dosetrack.app.data.model.MedicationStatus
import com.dosetrack.app.data.model.MedicationType

@Entity(tableName = "medications")
data class MedicationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val genericName: String? = null,
    val dosageAmount: Double,
    val dosageUnit: DosageUnit,
    /** Free-text strength such as "10 mg"; optional. */
    val strength: String? = null,
    val medicationType: MedicationType,
    val frequency: Frequency,
    val foodInstruction: FoodInstruction = FoodInstruction.ANY_TIME,
    val customInstructions: String? = null,
    val notes: String? = null,
    /** LocalDate.toEpochDay() */
    val startDate: Long,
    val durationType: DurationType = DurationType.ONGOING,
    /** Course length in days when [durationType] is DAYS. */
    val durationDays: Int? = null,
    /** Last day (inclusive) as LocalDate.toEpochDay(); null = ongoing. Computed for DAYS. */
    val endDate: Long? = null,
    val status: MedicationStatus = MedicationStatus.ACTIVE,
    /** Doses are only generated at or after this instant (set on create, schedule edit, resume). */
    val scheduleEffectiveFrom: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "medication_schedules",
    foreignKeys = [
        ForeignKey(
            entity = MedicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["medicationId", "timeOfDay"], unique = true)],
)
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicationId: Long,
    /** Minutes after local midnight, 0..1439. */
    val timeOfDay: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "doses",
    foreignKeys = [
        ForeignKey(
            entity = MedicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["medicationId", "scheduledAt"], unique = true),
        Index(value = ["scheduledAt"]),
        Index(value = ["status"]),
    ],
)
data class DoseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicationId: Long,
    /** Epoch millis of the planned intake. */
    val scheduledAt: Long,
    /** Epoch millis the user recorded the dose as taken (or skipped). */
    val takenAt: Long? = null,
    val status: DoseStatus = DoseStatus.PENDING,
    /** Epoch millis a snoozed reminder should fire again. */
    val snoozedUntil: Long? = null,
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

data class MedicationWithSchedules(
    @Embedded val medication: MedicationEntity,
    @Relation(parentColumn = "id", entityColumn = "medicationId")
    val schedules: List<ScheduleEntity>,
)

fun MedicationWithSchedules.timesOfDay(): List<Int> = schedules.map { it.timeOfDay }.sorted()

data class DoseWithMedication(
    @Embedded val dose: DoseEntity,
    @Relation(parentColumn = "medicationId", entityColumn = "id")
    val medication: MedicationEntity,
)

data class StatusCount(
    @ColumnInfo(name = "status") val status: DoseStatus,
    @ColumnInfo(name = "count") val count: Int,
)
