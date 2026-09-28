package com.dosetrack.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.dosetrack.app.data.AppSettings
import com.dosetrack.app.ui.navigation.DoseTrackNavHost
import com.dosetrack.app.ui.navigation.Routes
import com.dosetrack.app.ui.theme.DoseTrackTheme

// AppCompatActivity (not ComponentActivity) so per-app language and night mode apply.
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = appContainer
        val settingsRepository = container.settingsRepository
        // The activity is recreated on a language change: refresh channel names in that language.
        lifecycleScope.launch {
            container.notificationHelper.ensureChannel(settingsRepository.current())
            container.notificationHelper.ensureAlarmChannel()
        }
        setContent {
            DoseTrackTheme {
                // Start destination depends on whether onboarding was completed; decided once.
                val settings by produceState<AppSettings?>(initialValue = null) {
                    // Every screen assumes at least one profile exists.
                    container.profileRepository.ensureDefaultProfile()
                    value = settingsRepository.current()
                }
                val loaded = settings
                if (loaded == null) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                } else {
                    DoseTrackNavHost(
                        startDestination = if (loaded.onboardingDone) Routes.TODAY else Routes.ONBOARDING,
                    )
                }
            }
        }
    }
}
