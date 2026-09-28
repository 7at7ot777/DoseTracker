package com.dosetrack.app.data.backup

import androidx.room.withTransaction
import com.dosetrack.app.data.db.AppDatabase
import com.dosetrack.app.domain.DoseSync
import com.dosetrack.app.reminders.NotificationHelper
import com.dosetrack.app.reminders.ReminderManager

/** Export, import (replace) and wipe of all medication data. Settings are not included. */
class BackupManager(
    private val db: AppDatabase,
    private val reminders: ReminderManager,
    private val notifications: NotificationHelper,
    private val doseSync: DoseSync,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun exportJson(): String = db.withTransaction {
        BackupCodec.encode(
            medications = db.medicationDao().getAll(),
            schedules = db.scheduleDao().getAll(),
            doses = db.doseDao().getAll(),
            exportedAt = clock(),
        )
    }

    /** Replaces everything with [backup], then re-creates upcoming doses and alarms. */
    suspend fun restore(backup: BackupFile) {
        stopAllReminders()
        db.withTransaction {
            deleteAllRows()
            db.medicationDao().insertAll(backup.medications.map { it.toEntity() })
            db.scheduleDao().insertAll(backup.schedules.map { it.toEntity() })
            db.doseDao().insertAllReplacing(backup.doses.map { it.toEntity() })
        }
        doseSync.sync()
    }

    suspend fun clearAll() {
        stopAllReminders()
        db.withTransaction { deleteAllRows() }
    }

    private suspend fun stopAllReminders() {
        reminders.onDosesResolved(db.doseDao().getAllPendingIds())
        notifications.cancelAll()
    }

    private suspend fun deleteAllRows() {
        db.doseDao().deleteAll()
        db.scheduleDao().deleteAll()
        db.medicationDao().deleteAll()
    }
}
