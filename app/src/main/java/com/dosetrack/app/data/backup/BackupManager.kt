package com.dosetrack.app.data.backup

import androidx.room.withTransaction
import com.dosetrack.app.data.ProfileRepository
import com.dosetrack.app.data.db.AppDatabase
import com.dosetrack.app.domain.DoseSync
import com.dosetrack.app.reminders.NotificationHelper
import com.dosetrack.app.reminders.ReminderManager

/** Export, import (replace) and wipe of all profiles and medication data. Settings are not included. */
class BackupManager(
    private val db: AppDatabase,
    private val reminders: ReminderManager,
    private val notifications: NotificationHelper,
    private val doseSync: DoseSync,
    private val profileRepository: ProfileRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun exportJson(): String = db.withTransaction {
        BackupCodec.encode(
            profiles = db.profileDao().getAll(),
            medications = db.medicationDao().getAll(),
            schedules = db.scheduleDao().getAll(),
            doses = db.doseDao().getAll(),
            exportedAt = clock(),
        )
    }

    /** Replaces everything with [backup], then re-creates upcoming doses and alarms for every profile. */
    suspend fun restore(backup: BackupFile) {
        stopAllReminders()
        profileRepository.deleteAllAvatarFiles()
        db.withTransaction {
            deleteAllRows()
            db.profileDao().insertAll(backup.profiles.map { it.toEntity() })
            db.medicationDao().insertAll(backup.medications.map { it.toEntity() })
            db.scheduleDao().insertAll(backup.schedules.map { it.toEntity() })
            db.doseDao().insertAllReplacing(backup.doses.map { it.toEntity() })
        }
        profileRepository.ensureDefaultProfile()
        doseSync.sync()
    }

    /** Deletes all profiles and data, then starts over with one default profile. */
    suspend fun clearAll() {
        stopAllReminders()
        profileRepository.deleteAllAvatarFiles()
        db.withTransaction { deleteAllRows() }
        profileRepository.ensureDefaultProfile()
    }

    private suspend fun stopAllReminders() {
        reminders.onDosesResolved(db.doseDao().getAllPendingIds())
        notifications.cancelAll()
    }

    private suspend fun deleteAllRows() {
        db.doseDao().deleteAll()
        db.scheduleDao().deleteAll()
        db.medicationDao().deleteAll()
        db.profileDao().deleteAll()
    }
}
