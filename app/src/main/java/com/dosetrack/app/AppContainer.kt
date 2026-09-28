package com.dosetrack.app

import android.content.Context
import com.dosetrack.app.data.DoseRepository
import com.dosetrack.app.data.MedicationRepository
import com.dosetrack.app.data.SettingsRepository
import com.dosetrack.app.data.db.AppDatabase
import com.dosetrack.app.domain.DoseSync
import com.dosetrack.app.reminders.AlarmScheduler
import com.dosetrack.app.reminders.NotificationHelper
import com.dosetrack.app.reminders.ReminderManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency container; one instance per process, owned by [DoseTrackApplication]. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** Process-wide scope for work that must outlive a screen (sync, receivers). */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: AppDatabase = AppDatabase.build(appContext)
    val settingsRepository = SettingsRepository(appContext)
    val notificationHelper = NotificationHelper(appContext)
    val reminderManager = ReminderManager(database, settingsRepository, AlarmScheduler(appContext), notificationHelper)
    val doseSync = DoseSync(database, reminderManager)
    val doseRepository = DoseRepository(database, reminderManager)
    val medicationRepository = MedicationRepository(database, doseSync, reminderManager)
}

val Context.appContainer: AppContainer
    get() = (applicationContext as DoseTrackApplication).container
