package com.dosetrack.app.domain

import androidx.room.withTransaction
import com.dosetrack.app.data.db.AppDatabase
import com.dosetrack.app.reminders.ReminderManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZoneId

/**
 * Brings stored doses in line with the medication schedules: creates missing dose rows,
 * records overdue pending doses as missed, and refreshes reminder alarms.
 * Idempotent and safe to call often.
 */
class DoseSync(
    private val db: AppDatabase,
    private val reminders: ReminderManager,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()

    /** @return ids of doses that were just marked missed. */
    suspend fun sync(zone: ZoneId = ZoneId.systemDefault()): List<Long> {
        val missedIds = mutex.withLock { syncLocked(zone) }
        reminders.onDosesResolved(missedIds)
        reminders.rescheduleAll()
        return missedIds
    }

    /**
     * Time zone changed: future pending doses were computed for the old zone's local times.
     * Drop them and regenerate; past doses and history stay as recorded.
     */
    suspend fun rebuildFuture(zone: ZoneId = ZoneId.systemDefault()) {
        val removed = mutex.withLock {
            val now = clock()
            db.withTransaction {
                db.medicationDao().getActiveWithSchedules().flatMap { item ->
                    val id = item.medication.id
                    db.doseDao().getFuturePendingIds(id, now).also { db.doseDao().deleteFuturePending(id, now) }
                }
            }
        }
        reminders.onDosesResolved(removed)
        sync(zone)
    }

    private suspend fun syncLocked(zone: ZoneId): List<Long> {
        val now = clock()
        val medications = db.medicationDao().getActiveWithSchedules()
        val planned = DosePlanner.plan(medications, now, zone)
        if (planned.isNotEmpty()) db.doseDao().insertAllIgnoring(planned)

        val threshold = now - MISSED_AFTER_MILLIS
        val missedIds = db.doseDao().getPendingIdsOlderThan(threshold)
        if (missedIds.isNotEmpty()) db.doseDao().markPendingMissedOlderThan(threshold, now)
        return missedIds
    }
}
