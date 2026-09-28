package com.dosetrack.app

import android.content.Intent
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.dosetrack.app.data.AppSettings
import com.dosetrack.app.reminders.NotificationHelper
import com.dosetrack.app.ui.navigation.DeepLink
import com.dosetrack.app.ui.navigation.DoseTrackNavHost
import com.dosetrack.app.ui.navigation.Routes
import com.dosetrack.app.ui.theme.DoseTrackTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

// AppCompatActivity (not ComponentActivity) so per-app language and night mode apply.
class MainActivity : AppCompatActivity() {
    /** Set when opened from a dose notification; consumed once by the nav host. */
    private val deepLink = MutableStateFlow<DeepLink?>(null)

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
        // Only a fresh launch carries a link; a recreation (rotation, language) must not replay it.
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            DoseTrackTheme {
                // Start destination depends on whether onboarding was completed; decided once.
                val settings by produceState<AppSettings?>(initialValue = null) {
                    // Every screen assumes at least one profile exists.
                    container.profileRepository.ensureDefaultProfile()
                    value = settingsRepository.current()
                }
                val link by deepLink.collectAsStateWithLifecycle()
                val loaded = settings
                if (loaded == null) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                } else {
                    DoseTrackNavHost(
                        startDestination = if (loaded.onboardingDone) Routes.TODAY else Routes.ONBOARDING,
                        deepLink = link.takeIf { loaded.onboardingDone },
                        onDeepLinkHandled = { deepLink.value = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** Notification tap: show the dose owner's profile (UI selection only), then its medication. */
    private fun handleIntent(intent: Intent?) {
        val profileId = intent?.getLongExtra(NotificationHelper.EXTRA_PROFILE_ID, -1L) ?: -1L
        val medicationId = intent?.getLongExtra(NotificationHelper.EXTRA_MEDICATION_ID, -1L) ?: -1L
        if (profileId < 0 || medicationId < 0) return
        lifecycleScope.launch {
            appContainer.profileRepository.select(profileId)
            deepLink.value = DeepLink(profileId, medicationId)
        }
    }
}
