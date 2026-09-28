package com.dosetrack.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import kotlinx.coroutines.launch
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

        // Create today's doses and record misses from while the app was closed.
        container.applicationScope.launch { container.doseSync.sync() }
    }
}
