package com.dosetrack.app.data

import androidx.room.withTransaction
import com.dosetrack.app.data.db.AppDatabase
import com.dosetrack.app.data.db.MedicationEntity
import com.dosetrack.app.data.db.MedicationWithSchedules
import com.dosetrack.app.data.db.NextDose
import com.dosetrack.app.data.db.ScheduleEntity
import com.dosetrack.app.data.model.DosageUnit
import com.dosetrack.app.data.model.DurationType
import com.dosetrack.app.data.model.FoodInstruction
import com.dosetrack.app.data.model.Frequency
import com.dosetrack.app.data.model.MedicationStatus
import com.dosetrack.app.data.model.MedicationType
import com.dosetrack.app.data.model.ReminderStyle
import com.dosetrack.app.domain.DoseSync
import com.dosetrack.app.domain.MedicationDuration
import com.dosetrack.app.reminders.ReminderManager
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class ScheduleInput(val timeOfDay: Int, val note: String?)

/** Validated form data for creating (id == null) or updating a medication. */
data class MedicationInput(
    val id: Long?,
    val name: String,
    val genericName: String?,
    val dosageAmount: Double,
    val dosageUnit: DosageUnit,
    val strength: String?,
    val medicationType: MedicationType,
    val frequency: Frequency,
    val foodInstruction: FoodInstruction,
    val customInstructions: String?,
    val notes: String?,
    val startDate: LocalDate,
    val durationType: DurationType,
    val durationDays: Int?,
    val endDate: LocalDate?,
    val times: List<ScheduleInput>,
    val reminderStyle: ReminderStyle = ReminderStyle.NOTIFICATION,
)

/**
 * @param removedDoseIds future pending doses deleted because the schedule changed;
 *   their reminders must be cancelled.
 */
data class ChangeResult(val medicationId: Long, val removedDoseIds: List<Long>)

class MedicationRepository(
    private val db: AppDatabase,
    private val doseSync: DoseSync,
    private val reminders: ReminderManager,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val medicationDao = db.medicationDao()
    private val scheduleDao = db.scheduleDao()
    private val doseDao = db.doseDao()

    fun observeVisible(): Flow<List<MedicationWithSchedules>> = medicationDao.observeVisibleWithSchedules()

    fun observe(id: Long): Flow<MedicationWithSchedules?> = medicationDao.observeWithSchedules(id)

    fun observeNextDoses(from: Long): Flow<List<NextDose>> = doseDao.observeNextPendingPerMedication(from)

    suspend fun get(id: Long): MedicationWithSchedules? = medicationDao.getWithSchedules(id)

    suspend fun save(input: MedicationInput): ChangeResult {
        val result = saveInTransaction(input)
        reminders.onDosesResolved(result.removedDoseIds)
        doseSync.sync()
        return result
    }

    private suspend fun saveInTransaction(input: MedicationInput): ChangeResult = db.withTransaction {
        val now = clock()
        val existing = input.id?.let { medicationDao.getWithSchedules(it) }
        val endDate = MedicationDuration.endDate(input.durationType, input.startDate, input.durationDays, input.endDate)
        val times = input.times.map { ScheduleInput(it.timeOfDay, it.note.cleaned()) }.sortedBy { it.timeOfDay }

        val scheduleChanged = existing == null ||
            existing.schedules.sortedBy { it.timeOfDay }.map { ScheduleInput(it.timeOfDay, it.note) } != times ||
            existing.medication.startDate != input.startDate.toEpochDay() ||
            existing.medication.endDate != endDate?.toEpochDay()

        val entity = MedicationEntity(
            id = existing?.medication?.id ?: 0,
            name = input.name.trim(),
            genericName = input.genericName.cleaned(),
            dosageAmount = input.dosageAmount,
            dosageUnit = input.dosageUnit,
            strength = input.strength.cleaned(),
            medicationType = input.medicationType,
            frequency = input.frequency,
            foodInstruction = input.foodInstruction,
            customInstructions = input.customInstructions.cleaned(),
            notes = input.notes.cleaned(),
            startDate = input.startDate.toEpochDay(),
            durationType = input.durationType,
            durationDays = if (input.durationType == DurationType.DAYS) input.durationDays else null,
            endDate = endDate?.toEpochDay(),
            status = existing?.medication?.status ?: MedicationStatus.ACTIVE,
            scheduleEffectiveFrom = if (scheduleChanged) now else existing!!.medication.scheduleEffectiveFrom,
            createdAt = existing?.medication?.createdAt ?: now,
            updatedAt = now,
            reminderStyle = input.reminderStyle,
        )

        val id: Long
        var removed = emptyList<Long>()
        if (existing == null) {
            id = medicationDao.insert(entity)
        } else {
            id = entity.id
            medicationDao.update(entity)
            if (scheduleChanged) removed = deleteFuturePending(id, now)
        }

        scheduleDao.deleteForMedication(id)
        scheduleDao.insertAll(
            times.map { ScheduleEntity(medicationId = id, timeOfDay = it.timeOfDay, note = it.note, createdAt = now, updatedAt = now) },
        )
        ChangeResult(id, removed)
    }

    /** Stops reminders; historical doses are untouched. */
    suspend fun pause(id: Long): ChangeResult = stopReminders(id, MedicationStatus.PAUSED)

    /** Doses are generated again from now on (not back-filled for the paused period). */
    suspend fun resume(id: Long): ChangeResult {
        medicationDao.setStatusAndEffectiveFrom(id, MedicationStatus.ACTIVE, clock())
        doseSync.sync()
        return ChangeResult(id, emptyList())
    }

    /** Soft delete: hidden from lists, dose history kept. */
    suspend fun archive(id: Long): ChangeResult = stopReminders(id, MedicationStatus.ARCHIVED)

    private suspend fun stopReminders(id: Long, status: MedicationStatus): ChangeResult {
        val result = db.withTransaction {
            val now = clock()
            medicationDao.setStatus(id, status, now)
            ChangeResult(id, deleteFuturePending(id, now))
        }
        reminders.onDosesResolved(result.removedDoseIds)
        // Also cancels alarms of this medication's still-pending past doses.
        reminders.rescheduleAll()
        return result
    }

    private suspend fun deleteFuturePending(medicationId: Long, from: Long): List<Long> {
        val ids = doseDao.getFuturePendingIds(medicationId, from)
        doseDao.deleteFuturePending(medicationId, from)
        return ids
    }

    private fun String?.cleaned(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}
