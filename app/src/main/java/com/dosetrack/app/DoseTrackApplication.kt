package com.dosetrack.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.dosetrack.app.reminders.SyncWorker
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class DoseTrackApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Apply the saved theme before any activity is created so the window matches Compose.
        val settings = runBlocking { container.settingsRepository.current() }
        AppCompatDelegate.setDefaultNightMode(settings.themeMode.nightMode)
        container.notificationHelper.ensureChannel(settings)

        // Create upcoming doses, record misses from while the app was closed, and (re)arm alarms.
        container.applicationScope.launch {
            container.profileRepository.ensureDefaultProfile()
            container.doseSync.sync()
        }
        SyncWorker.schedule(this)
    }
}
