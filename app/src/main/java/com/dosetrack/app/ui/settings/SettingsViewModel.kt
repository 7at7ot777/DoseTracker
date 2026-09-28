package com.dosetrack.app.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dosetrack.app.appContainer
import com.dosetrack.app.data.AppSettings
import com.dosetrack.app.data.SettingsRepository
import com.dosetrack.app.data.ThemeMode
import com.dosetrack.app.data.backup.BackupCodec
import com.dosetrack.app.data.backup.BackupFile
import com.dosetrack.app.data.backup.BackupFormatException
import com.dosetrack.app.data.backup.BackupManager
import com.dosetrack.app.reminders.ReminderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** In-app language choice; SYSTEM follows the phone language. */
enum class AppLanguage(val tag: String?) { SYSTEM(null), ENGLISH("en"), ARABIC("ar") }

enum class SettingsMessage { BACKUP_SAVED, BACKUP_RESTORED, BACKUP_INVALID, BACKUP_FAILED, DATA_CLEARED }

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val reminderManager: ReminderManager,
    private val backupManager: BackupManager,
    private val contentResolver: ContentResolver,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _pendingImport = MutableStateFlow<BackupFile?>(null)

    /** A parsed backup waiting for the user to confirm replacing current data. */
    val pendingImport: StateFlow<BackupFile?> = _pendingImport.asStateFlow()

    private val _messages = Channel<SettingsMessage>(Channel.BUFFERED)
    val messages: Flow<SettingsMessage> = _messages.receiveAsFlow()

    fun setRemindersEnabled(enabled: Boolean) = updateReminders { settingsRepository.setRemindersEnabled(enabled) }

    fun setSoundEnabled(enabled: Boolean) = updateReminders { settingsRepository.setSoundEnabled(enabled) }

    fun setVibrationEnabled(enabled: Boolean) = updateReminders { settingsRepository.setVibrationEnabled(enabled) }

    fun setSnoozeMinutes(minutes: Int) {
        viewModelScope.launch { settingsRepository.setSnoozeMinutes(minutes) }
    }

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
            AppCompatDelegate.setDefaultNightMode(mode.nightMode)
        }
    }

    fun currentLanguage(): AppLanguage {
        val tag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        return AppLanguage.entries.firstOrNull { it.tag != null && tag.startsWith(it.tag) } ?: AppLanguage.SYSTEM
    }

    fun setLanguage(language: AppLanguage) {
        val locales = language.tag?.let { LocaleListCompat.forLanguageTags(it) } ?: LocaleListCompat.getEmptyLocaleList()
        // Recreates activities in the new language; persisted by AppCompat.
        AppCompatDelegate.setApplicationLocales(locales)
    }

    fun export(uri: Uri) {
        viewModelScope.launch {
            val ok = try {
                val text = backupManager.exportJson()
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                        ?: throw IOException("Cannot open $uri")
                }
                true
            } catch (e: IOException) {
                false
            }
            _messages.send(if (ok) SettingsMessage.BACKUP_SAVED else SettingsMessage.BACKUP_FAILED)
        }
    }

    fun readBackup(uri: Uri) {
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                        ?: throw IOException("Cannot open $uri")
                }
                _pendingImport.value = BackupCodec.decode(text)
            } catch (e: BackupFormatException) {
                _messages.send(SettingsMessage.BACKUP_INVALID)
            } catch (e: IOException) {
                _messages.send(SettingsMessage.BACKUP_FAILED)
            }
        }
    }

    fun confirmImport() {
        val backup = _pendingImport.value ?: return
        _pendingImport.value = null
        viewModelScope.launch {
            backupManager.restore(backup)
            _messages.send(SettingsMessage.BACKUP_RESTORED)
        }
    }

    fun cancelImport() {
        _pendingImport.value = null
    }

    fun clearAll() {
        viewModelScope.launch {
            backupManager.clearAll()
            _messages.send(SettingsMessage.DATA_CLEARED)
        }
    }

    private fun updateReminders(save: suspend () -> Unit) {
        viewModelScope.launch {
            save()
            reminderManager.onSettingsChanged()
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY]!!
                val container = app.appContainer
                SettingsViewModel(
                    container.settingsRepository,
                    container.reminderManager,
                    container.backupManager,
                    app.contentResolver,
                )
            }
        }
    }
}
