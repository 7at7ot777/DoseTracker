package com.dosetrack.app.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dosetrack.app.MainActivity
import com.dosetrack.app.R
import com.dosetrack.app.data.AppSettings
import com.dosetrack.app.data.db.DoseWithMedication
import com.dosetrack.app.ui.Labels

class NotificationHelper(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    /**
     * Channel sound/vibration can't be changed after creation, so each combination gets its own
     * channel id and the others are deleted.
     */
    fun ensureChannel(settings: AppSettings): String {
        val id = channelId(settings.soundEnabled, settings.vibrationEnabled)
        if (manager.getNotificationChannel(id) == null) {
            val res = localizedContext().resources
            val channel = NotificationChannel(
                id,
                res.getString(R.string.channel_reminders_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = res.getString(R.string.channel_reminders_description)
                enableVibration(settings.vibrationEnabled)
                if (settings.vibrationEnabled) vibrationPattern = longArrayOf(0, 400, 200, 400)
                if (!settings.soundEnabled) setSound(null, null)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            }
            manager.createNotificationChannel(channel)
        }
        manager.notificationChannels
            .filter { it.id.startsWith(CHANNEL_PREFIX) && it.id != id }
            .forEach { manager.deleteNotificationChannel(it.id) }
        return id
    }

    /**
     * 💊 Time to take Cipralex
     * 10 mg · 1 tablet
     * Take after breakfast.        (omitted when no note applies)
     */
    fun showDoseReminder(item: DoseWithMedication, settings: AppSettings) {
        if (!canPost()) return
        val channelId = ensureChannel(settings)
        val res = localizedContext().resources
        val dose = item.dose
        val medication = item.medication
        val doseLine = Labels.doseLine(res, medication)
        val note = Labels.doseNote(res, dose.reminderNote, medication)
        val body = listOfNotNull(doseLine, note).joinToString("\n")
        val notificationId = notificationId(dose.id)
        val openApp = openAppIntent(notificationId)

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(res.getString(R.string.notification_title, medication.name))
            .setContentText(doseLine)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setWhen(dose.scheduledAt)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .addAction(
                0,
                res.getString(R.string.notification_action_taken),
                actionIntent(NotificationActionReceiver.ACTION_TAKE, dose.id),
            )
            .addAction(
                0,
                res.getString(R.string.notification_action_snooze, settings.snoozeMinutes),
                actionIntent(NotificationActionReceiver.ACTION_SNOOZE, dose.id),
            )
            .addAction(0, res.getString(R.string.notification_action_open), openApp)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call; nothing to show.
        }
    }

    fun cancel(doseId: Long) {
        NotificationManagerCompat.from(context).cancel(notificationId(doseId))
    }

    fun cancelAll() {
        NotificationManagerCompat.from(context).cancelAll()
    }

    fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun openAppIntent(requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun actionIntent(action: String, doseId: Long): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java)
            .setAction(action)
            .setData(Uri.parse("dosetrack://dose/$doseId"))
            .putExtra(EXTRA_DOSE_ID, doseId)
        return PendingIntent.getBroadcast(
            context,
            notificationId(doseId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * On Android 12 and below the in-app language (AppCompat) only applies to activities,
     * so build a context with it for notification text.
     */
    private fun localizedContext(): Context {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty || Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return context
        val config = Configuration(context.resources.configuration)
        config.setLocales(LocaleList.forLanguageTags(locales.toLanguageTags()))
        return context.createConfigurationContext(config)
    }

    companion object {
        const val EXTRA_DOSE_ID = "dose_id"
        private const val CHANNEL_PREFIX = "reminders_"

        fun channelId(sound: Boolean, vibration: Boolean) =
            "${CHANNEL_PREFIX}s${if (sound) 1 else 0}_v${if (vibration) 1 else 0}"

        fun notificationId(doseId: Long): Int = (doseId % Int.MAX_VALUE).toInt()
    }
}
