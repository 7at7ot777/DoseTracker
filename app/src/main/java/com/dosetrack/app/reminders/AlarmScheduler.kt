package com.dosetrack.app.reminders

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

/** One exact alarm per pending dose, identified by the dose id. */
class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    // Exact alarms are only used when canScheduleExact() allows them (lint can't see through the helper).
    @SuppressLint("ScheduleExactAlarm")
    fun schedule(doseId: Long, triggerAt: Long) {
        val pendingIntent = pendingIntent(doseId, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            // Without exact-alarm access the system may deliver this a few minutes late.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    fun cancel(doseId: Long) {
        val pendingIntent = pendingIntent(doseId, PendingIntent.FLAG_NO_CREATE) ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    /** Exact alarms are always allowed below Android 12 and via USE_EXACT_ALARM on 13+. */
    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun pendingIntent(doseId: Long, flag: Int): PendingIntent? {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_DOSE_ALARM)
            .setData(Uri.parse("dosetrack://dose/$doseId"))
            .putExtra(NotificationHelper.EXTRA_DOSE_ID, doseId)
        return PendingIntent.getBroadcast(
            context,
            NotificationHelper.notificationId(doseId),
            intent,
            flag or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
