package com.dosetrack.app.domain

import com.dosetrack.app.data.db.AppDatabase
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZoneId

/**
 * Brings stored doses in line with the medication schedules: creates missing dose rows and
 * records overdue pending doses as missed. Idempotent and safe to call often.
 */
class DoseSync(
    private val db: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()

    /** @return ids of doses that were just marked missed. */
    suspend fun sync(zone: ZoneId = ZoneId.systemDefault()): List<Long> = mutex.withLock {
        val now = clock()
        val medications = db.medicationDao().getActiveWithSchedules()
        val planned = DosePlanner.plan(medications, now, zone)
        if (planned.isNotEmpty()) db.doseDao().insertAllIgnoring(planned)

        val threshold = now - MISSED_AFTER_MILLIS
        val missedIds = db.doseDao().getPendingIdsOlderThan(threshold)
        if (missedIds.isNotEmpty()) db.doseDao().markPendingMissedOlderThan(threshold, now)
        missedIds
    }
}
