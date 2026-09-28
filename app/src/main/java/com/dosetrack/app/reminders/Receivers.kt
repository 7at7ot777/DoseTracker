package com.dosetrack.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dosetrack.app.appContainer
import kotlinx.coroutines.launch

/** Runs [block] on the app scope while keeping the broadcast alive until it finishes. */
private fun BroadcastReceiver.runAsync(context: Context, block: suspend () -> Unit) {
    val pending = goAsync()
    context.appContainer.applicationScope.launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

/** Fires at a dose's scheduled (or snoozed) time. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DOSE_ALARM) return
        val doseId = intent.getLongExtra(NotificationHelper.EXTRA_DOSE_ID, -1L)
        if (doseId < 0) return
        val container = context.appContainer
        runAsync(context) {
            container.reminderManager.onAlarm(doseId)
            // Keeps the rolling 48 h window of doses and alarms topped up.
            container.doseSync.sync()
        }
    }

    companion object {
        const val ACTION_DOSE_ALARM = "com.dosetrack.app.action.DOSE_ALARM"
    }
}

/** "Taken" and "Snooze" buttons on the reminder notification. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val doseId = intent.getLongExtra(NotificationHelper.EXTRA_DOSE_ID, -1L)
        if (doseId < 0) return
        val container = context.appContainer
        when (intent.action) {
            ACTION_TAKE -> runAsync(context) { container.doseRepository.markTaken(doseId) }
            ACTION_SNOOZE -> runAsync(context) {
                val minutes = container.settingsRepository.current().snoozeMinutes
                container.doseRepository.snooze(doseId, System.currentTimeMillis() + minutes * 60_000L)
            }
        }
    }

    companion object {
        const val ACTION_TAKE = "com.dosetrack.app.action.TAKE"
        const val ACTION_SNOOZE = "com.dosetrack.app.action.SNOOZE"
    }
}

/**
 * Alarms are cleared on reboot and app update, and a clock or time-zone change shifts local
 * dose times, so reminders are rebuilt on each of these.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = context.appContainer
        when (intent.action) {
            Intent.ACTION_TIMEZONE_CHANGED -> runAsync(context) { container.doseSync.rebuildFuture() }
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            ACTION_EXACT_ALARM_PERMISSION_CHANGED,
            -> runAsync(context) { container.doseSync.sync() }
        }
    }

    companion object {
        /** AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED (API 31). */
        private const val ACTION_EXACT_ALARM_PERMISSION_CHANGED =
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
    }
}
