package com.dosetrack.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import kotlinx.coroutines.runBlocking

class DoseTrackApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Apply the saved theme before any activity is created so the window matches Compose.
        val themeMode = runBlocking { container.settingsRepository.current().themeMode }
        AppCompatDelegate.setDefaultNightMode(themeMode.nightMode)
    }
}
