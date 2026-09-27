package com.dosetrack.app

import android.content.Context
import com.dosetrack.app.data.SettingsRepository
import com.dosetrack.app.data.db.AppDatabase

/** Manual dependency container; one instance per process, owned by [DoseTrackApplication]. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: AppDatabase = AppDatabase.build(appContext)
    val settingsRepository = SettingsRepository(appContext)
}

val Context.appContainer: AppContainer
    get() = (applicationContext as DoseTrackApplication).container
