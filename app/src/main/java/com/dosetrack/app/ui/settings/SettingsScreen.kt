package com.dosetrack.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dosetrack.app.BuildConfig
import com.dosetrack.app.R
import com.dosetrack.app.data.ThemeMode
import com.dosetrack.app.ui.appResources
import com.dosetrack.app.ui.components.ConfirmDialog
import com.dosetrack.app.ui.components.SnoozeDialog
import com.dosetrack.app.ui.components.openNotificationSettings
import java.time.LocalDate

private enum class SettingsDialog { SNOOZE, THEME, LANGUAGE, CLEAR, ABOUT, PRIVACY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val pendingImport by viewModel.pendingImport.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val res = appResources()
    val snackbarHostState = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.export(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.readBackup(uri)
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            val text = res.getString(
                when (message) {
                    SettingsMessage.BACKUP_SAVED -> R.string.message_backup_saved
                    SettingsMessage.BACKUP_RESTORED -> R.string.message_backup_restored
                    SettingsMessage.BACKUP_INVALID -> R.string.message_backup_invalid
                    SettingsMessage.BACKUP_FAILED -> R.string.message_backup_failed
                    SettingsMessage.DATA_CLEARED -> R.string.message_data_cleared
                },
            )
            snackbarHostState.showSnackbar(text)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_settings)) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            SectionHeader(stringResource(R.string.settings_section_notifications))
            SwitchRow(
                icon = Icons.Filled.Notifications,
                title = stringResource(R.string.settings_reminders),
                subtitle = stringResource(R.string.settings_reminders_summary),
                checked = settings.remindersEnabled,
                onCheckedChange = viewModel::setRemindersEnabled,
            )
            ClickRow(
                icon = Icons.Filled.Snooze,
                title = stringResource(R.string.settings_default_snooze),
                subtitle = res.getQuantityString(R.plurals.minutes_count, settings.snoozeMinutes, settings.snoozeMinutes),
                onClick = { dialog = SettingsDialog.SNOOZE },
            )
            SwitchRow(
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                title = stringResource(R.string.settings_sound),
                subtitle = stringResource(R.string.settings_sound_vibration_summary),
                checked = settings.soundEnabled,
                onCheckedChange = viewModel::setSoundEnabled,
            )
            SwitchRow(
                icon = Icons.Filled.Vibration,
                title = stringResource(R.string.settings_vibration),
                subtitle = null,
                checked = settings.vibrationEnabled,
                onCheckedChange = viewModel::setVibrationEnabled,
            )
            ClickRow(
                icon = Icons.Filled.Tune,
                title = stringResource(R.string.settings_system_notifications),
                subtitle = stringResource(R.string.settings_system_notifications_summary),
                onClick = { openNotificationSettings(context) },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionHeader(stringResource(R.string.settings_section_appearance))
            ClickRow(
                icon = Icons.Filled.DarkMode,
                title = stringResource(R.string.settings_theme),
                subtitle = themeLabel(settings.themeMode),
                onClick = { dialog = SettingsDialog.THEME },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionHeader(stringResource(R.string.settings_section_data))
            ClickRow(
                icon = Icons.Filled.Upload,
                title = stringResource(R.string.settings_export),
                subtitle = stringResource(R.string.settings_export_summary),
                onClick = { exportLauncher.launch("dosetrack-backup-${LocalDate.now()}.json") },
            )
            ClickRow(
                icon = Icons.Filled.Download,
                title = stringResource(R.string.settings_import),
                subtitle = stringResource(R.string.settings_import_summary),
                onClick = { importLauncher.launch(arrayOf("*/*")) },
            )
            ClickRow(
                icon = Icons.Filled.Delete,
                title = stringResource(R.string.settings_clear),
                subtitle = stringResource(R.string.settings_clear_summary),
                onClick = { dialog = SettingsDialog.CLEAR },
                destructive = true,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionHeader(stringResource(R.string.settings_section_general))
            ClickRow(
                icon = Icons.Filled.Language,
                title = stringResource(R.string.settings_language),
                subtitle = languageLabel(viewModel.currentLanguage()),
                onClick = { dialog = SettingsDialog.LANGUAGE },
            )
            ClickRow(
                icon = Icons.Filled.Info,
                title = stringResource(R.string.settings_about),
                subtitle = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                onClick = { dialog = SettingsDialog.ABOUT },
            )
            ClickRow(
                icon = Icons.Filled.PrivacyTip,
                title = stringResource(R.string.settings_privacy),
                subtitle = stringResource(R.string.settings_privacy_summary),
                onClick = { dialog = SettingsDialog.PRIVACY },
            )
        }
    }

    when (dialog) {
        SettingsDialog.SNOOZE -> SnoozeDialog(
            title = stringResource(R.string.settings_default_snooze),
            initialMinutes = settings.snoozeMinutes,
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = { minutes ->
                viewModel.setSnoozeMinutes(minutes)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        SettingsDialog.THEME -> ChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = ThemeMode.entries,
            selected = settings.themeMode,
            label = { themeLabel(it) },
            onSelect = { mode ->
                dialog = null
                viewModel.setTheme(mode)
            },
            onDismiss = { dialog = null },
        )
        SettingsDialog.LANGUAGE -> ChoiceDialog(
            title = stringResource(R.string.settings_language),
            options = AppLanguage.entries,
            selected = viewModel.currentLanguage(),
            label = { languageLabel(it) },
            onSelect = { language ->
                dialog = null
                viewModel.setLanguage(language)
            },
            onDismiss = { dialog = null },
        )
        SettingsDialog.CLEAR -> ConfirmDialog(
            title = stringResource(R.string.clear_confirm_title),
            message = stringResource(R.string.clear_confirm_message),
            confirmLabel = stringResource(R.string.action_clear),
            destructive = true,
            onConfirm = {
                dialog = null
                viewModel.clearAll()
            },
            onDismiss = { dialog = null },
        )
        SettingsDialog.ABOUT -> InfoDialog(
            title = stringResource(R.string.app_name),
            text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME) + "\n\n" +
                stringResource(R.string.about_text),
            onDismiss = { dialog = null },
        )
        SettingsDialog.PRIVACY -> InfoDialog(
            title = stringResource(R.string.settings_privacy),
            text = stringResource(R.string.privacy_text),
            onDismiss = { dialog = null },
        )
        null -> Unit
    }

    pendingImport?.let { backup ->
        ConfirmDialog(
            title = stringResource(R.string.import_confirm_title),
            message = stringResource(
                R.string.import_confirm_message,
                res.getQuantityString(R.plurals.medications_count, backup.medications.size, backup.medications.size),
                res.getQuantityString(R.plurals.doses_count, backup.doses.size, backup.doses.size),
            ),
            confirmLabel = stringResource(R.string.action_replace),
            destructive = true,
            onConfirm = viewModel::confirmImport,
            onDismiss = viewModel::cancelImport,
        )
    }
}

@Composable
private fun themeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    },
)

@Composable
private fun languageLabel(language: AppLanguage): String = when (language) {
    AppLanguage.SYSTEM -> stringResource(R.string.language_system)
    // Language names are shown in their own language so they are recognisable in either UI language.
    AppLanguage.ENGLISH -> "English"
    AppLanguage.ARABIC -> "العربية"
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun ClickRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = if (subtitle != null) {
            { Text(subtitle) }
        } else {
            null
        },
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(headlineColor = color, leadingIconColor = color),
        modifier = Modifier
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick),
    )
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = if (subtitle != null) {
            { Text(subtitle) }
        } else {
            null
        },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier
            .heightIn(min = 56.dp)
            .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch),
    )
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = option == selected, onClick = { onSelect(option) }, role = Role.RadioButton),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Text(label(option))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun InfoDialog(title: String, text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) { Text(text) }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
        },
    )
}
