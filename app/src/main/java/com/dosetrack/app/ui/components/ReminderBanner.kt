package com.dosetrack.app.ui.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.dosetrack.app.R
import com.dosetrack.app.appContainer

/**
 * Warns when reminders can't reach the user (notifications blocked, exact alarms denied,
 * or reminders turned off) and offers the fix. Re-checks whenever the screen resumes.
 */
@Composable
fun ReminderHealthBanners(
    remindersEnabled: Boolean,
    usesAlarmMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val container = context.appContainer
    var notificationsAllowed by remember { mutableStateOf(container.notificationHelper.canPost()) }
    var exactAllowed by remember { mutableStateOf(container.reminderManager.canScheduleExact()) }
    var fullScreenAllowed by remember { mutableStateOf(container.notificationHelper.canUseFullScreenIntent()) }

    LifecycleResumeEffect(Unit) {
        notificationsAllowed = container.notificationHelper.canPost()
        exactAllowed = container.reminderManager.canScheduleExact()
        fullScreenAllowed = container.notificationHelper.canUseFullScreenIntent()
        onPauseOrDispose { }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsAllowed = container.notificationHelper.canPost()
        val activity = context.findActivity()
        // Denied without a dialog (permanently denied): the only way left is system settings.
        if (!granted && activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
        ) {
            openNotificationSettings(context)
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            !remindersEnabled -> Banner(
                title = stringResource(R.string.banner_reminders_disabled_title),
                message = stringResource(R.string.banner_reminders_disabled_message),
            )
            !notificationsAllowed -> Banner(
                title = stringResource(R.string.banner_notifications_off_title),
                message = stringResource(R.string.banner_notifications_off_message),
                actionLabel = stringResource(R.string.action_allow_notifications),
                onAction = {
                    val needsRuntimePermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED
                    if (needsRuntimePermission) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        openNotificationSettings(context)
                    }
                },
            )
            !exactAllowed -> Banner(
                title = stringResource(R.string.banner_exact_title),
                message = stringResource(R.string.banner_exact_message),
                actionLabel = stringResource(R.string.action_open_settings),
                onAction = { openExactAlarmSettings(context) },
            )
            usesAlarmMode && !fullScreenAllowed -> Banner(
                title = stringResource(R.string.banner_fullscreen_title),
                message = stringResource(R.string.banner_fullscreen_message),
                actionLabel = stringResource(R.string.action_open_settings),
                onAction = { openFullScreenIntentSettings(context) },
            )
        }
    }
}

@Composable
private fun Banner(
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(message, style = MaterialTheme.typography.bodyMedium)
            if (actionLabel != null && onAction != null) {
                FilledTonalButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

fun openFullScreenIntentSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
    val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
