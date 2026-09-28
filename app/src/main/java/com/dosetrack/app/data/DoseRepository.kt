package com.dosetrack.app.data

import com.dosetrack.app.data.db.AppDatabase
import com.dosetrack.app.data.db.DoseEntity
import com.dosetrack.app.data.db.DoseWithMedication
import com.dosetrack.app.data.model.DoseStatus
import com.dosetrack.app.reminders.ReminderManager
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId

class DoseRepository(
    private val db: AppDatabase,
    private val reminders: ReminderManager,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val doseDao = db.doseDao()

    fun observeDay(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Flow<List<DoseWithMedication>> =
        doseDao.observeInRange(
            date.atStartOfDay(zone).toInstant().toEpochMilli(),
            date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
        )

    suspend fun get(id: Long): DoseWithMedication? = doseDao.getWithMedication(id)

    /**
     * Records the dose as taken now; scheduledAt is kept, so history shows both times.
     * @return the dose as it was before, for undo.
     */
    suspend fun markTaken(id: Long): DoseEntity? = changeStatus(id, DoseStatus.TAKEN)

    /** @return the dose as it was before, for undo. */
    suspend fun markSkipped(id: Long): DoseEntity? = changeStatus(id, DoseStatus.SKIPPED)

    /** Correction: clears a taken/skipped record; it becomes Due or Missed again by the clock. */
    suspend fun markNotTaken(id: Long): DoseEntity? = changeStatus(id, DoseStatus.PENDING)

    /** Delays the reminder; the dose stays pending (not taken). */
    suspend fun snooze(id: Long, until: Long) {
        val dose = doseDao.getById(id) ?: return
        if (dose.status != DoseStatus.PENDING) return
        doseDao.update(dose.copy(snoozedUntil = until, updatedAt = clock()))
        reminders.onSnoozed(id, until)
    }

    /** Restores a dose exactly as it was before a status change (undo). */
    suspend fun restore(previous: DoseEntity) {
        doseDao.update(previous.copy(updatedAt = clock()))
        afterStatusChange(previous.id, previous.status)
    }

    private suspend fun changeStatus(id: Long, status: DoseStatus): DoseEntity? {
        val dose = doseDao.getById(id) ?: return null
        val now = clock()
        doseDao.update(
            dose.copy(
                status = status,
                takenAt = if (status == DoseStatus.TAKEN) now else null,
                snoozedUntil = null,
                updatedAt = now,
            ),
        )
        afterStatusChange(id, status)
        return dose
    }

    private suspend fun afterStatusChange(id: Long, status: DoseStatus) {
        if (status == DoseStatus.PENDING) reminders.rescheduleAll() else reminders.onDoseResolved(id)
    }
}
