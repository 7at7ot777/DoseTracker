package com.dosetrack.app.ui.alarm

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dosetrack.app.R
import com.dosetrack.app.appContainer
import com.dosetrack.app.data.AppSettings
import com.dosetrack.app.data.db.DoseWithMedication
import com.dosetrack.app.data.db.ProfileEntity
import com.dosetrack.app.ui.profiles.ProfileAvatar
import com.dosetrack.app.data.model.DoseStatus
import com.dosetrack.app.reminders.NotificationHelper
import com.dosetrack.app.ui.Formats
import com.dosetrack.app.ui.Labels
import com.dosetrack.app.ui.appResources
import com.dosetrack.app.ui.currentLocale
import com.dosetrack.app.ui.is24HourClock
import com.dosetrack.app.ui.theme.DoseTrackTheme
import kotlinx.coroutines.launch

/**
 * Full-screen alarm for medications in alarm mode; shown over the lock screen by the
 * notification's full-screen intent. The notification keeps ringing until an action cancels it.
 */
class AlarmActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        enableEdgeToEdge()
        val doseId = intent.getLongExtra(NotificationHelper.EXTRA_DOSE_ID, -1L)
        setContent {
            DoseTrackTheme {
                AlarmScreen(doseId = doseId, onFinished = this::finish)
            }
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

@Composable
private fun AlarmScreen(doseId: Long, onFinished: () -> Unit) {
    val container = LocalContext.current.appContainer
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    var item by remember { mutableStateOf<DoseWithMedication?>(null) }
    var profile by remember { mutableStateOf<ProfileEntity?>(null) }

    LaunchedEffect(doseId) {
        val loaded = container.doseRepository.get(doseId)
        // Already handled elsewhere (e.g. "Taken" from the notification): nothing to show.
        if (loaded == null || loaded.dose.status != DoseStatus.PENDING) {
            onFinished()
        } else {
            // The dose's owner, whichever profile the app happens to show.
            profile = container.profileRepository.get(loaded.medication.profileId)
            item = loaded
        }
    }

    // Actions outlive this screen, so they run on the application scope.
    fun act(block: suspend () -> Unit) {
        container.applicationScope.launch { block() }
        onFinished()
    }

    val current = item
    if (current != null) {
        AlarmContent(
            item = current,
            profile = profile,
            snoozeMinutes = settings.snoozeMinutes,
            onTaken = { act { container.doseRepository.markTaken(doseId) } },
            onSnooze = {
                val until = System.currentTimeMillis() + settings.snoozeMinutes * 60_000L
                act { container.doseRepository.snooze(doseId, until) }
            },
            onDismiss = { act { container.notificationHelper.cancel(doseId) } },
        )
    }
}

@Composable
private fun AlarmContent(
    item: DoseWithMedication,
    profile: ProfileEntity?,
    snoozeMinutes: Int,
    onTaken: () -> Unit,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit,
) {
    val res = appResources()
    val locale = currentLocale()
    val is24 = is24HourClock()
    val current = item
    val medication = current.medication
    val note = Labels.doseNote(res, current.dose.reminderNote, medication)

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.weight(1f))
            if (profile != null) {
                ProfileAvatar(profile, size = 96.dp)
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Medication,
                    contentDescription = null,
                    modifier = Modifier.size(96.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = NotificationHelper.reminderTitle(res, profile, medication.name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = Labels.doseLine(res, medication),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            if (note != null) {
                Text(text = note, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            }
            Text(
                text = stringResource(R.string.alarm_scheduled_for, Formats.time(current.dose.scheduledAt, is24, locale)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onTaken,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp),
            ) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Text(
                    text = stringResource(R.string.notification_action_taken),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            OutlinedButton(
                onClick = onSnooze,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
            ) {
                Icon(Icons.Filled.Snooze, contentDescription = null)
                Text(
                    text = stringResource(R.string.notification_action_snooze, snoozeMinutes),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.notification_action_dismiss))
            }
        }
    }
}
