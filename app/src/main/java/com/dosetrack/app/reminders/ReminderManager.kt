package com.dosetrack.app.reminders

import com.dosetrack.app.data.SettingsRepository
import com.dosetrack.app.data.db.AppDatabase
import com.dosetrack.app.data.model.DoseStatus
import com.dosetrack.app.data.model.MedicationStatus
import com.dosetrack.app.domain.DosePlanner

/**
 * Keeps alarms and notifications consistent with stored doses. Holds no dose-changing logic
 * itself; repositories call it after they change a dose.
 */
class ReminderManager(
    private val db: AppDatabase,
    private val settingsRepository: SettingsRepository,
    private val alarms: AlarmScheduler,
    private val notifications: NotificationHelper,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Schedules an alarm for every pending dose due in the look-ahead window (idempotent). */
    suspend fun rescheduleAll() {
        val settings = settingsRepository.current()
        val now = clock()
        val pending = db.doseDao().getPendingBefore(now + DosePlanner.LOOKAHEAD_MILLIS)
        for (item in pending) {
            val trigger = item.dose.snoozedUntil ?: item.dose.scheduledAt
            val active = item.medication.status == MedicationStatus.ACTIVE
            if (settings.remindersEnabled && active && trigger > now) {
                alarms.schedule(item.dose.id, trigger)
            } else {
                alarms.cancel(item.dose.id)
            }
        }
    }

    /** Dose taken, skipped, missed or deleted: no further reminder for it. */
    fun onDoseResolved(doseId: Long) {
        alarms.cancel(doseId)
        notifications.cancel(doseId)
    }

    fun onDosesResolved(doseIds: Collection<Long>) = doseIds.forEach(::onDoseResolved)

    suspend fun onSnoozed(doseId: Long, until: Long) {
        notifications.cancel(doseId)
        if (settingsRepository.current().remindersEnabled) alarms.schedule(doseId, until)
    }

    /** Called by [AlarmReceiver] when a dose's alarm fires. */
    suspend fun onAlarm(doseId: Long) {
        val item = db.doseDao().getWithMedication(doseId) ?: return
        val settings = settingsRepository.current()
        if (!settings.remindersEnabled) return
        if (item.dose.status != DoseStatus.PENDING || item.medication.status != MedicationStatus.ACTIVE) return
        notifications.showDoseReminder(item, settings)
    }

    /** Settings changed (reminders toggle, sound, vibration). */
    suspend fun onSettingsChanged() {
        val settings = settingsRepository.current()
        notifications.ensureChannel(settings)
        if (!settings.remindersEnabled) notifications.cancelAll()
        rescheduleAll()
    }

    fun canScheduleExact(): Boolean = alarms.canScheduleExact()
}
