package com.dosetrack.app.ui.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dosetrack.app.appContainer
import com.dosetrack.app.data.ProfileRepository
import com.dosetrack.app.data.db.ProfileEntity
import com.dosetrack.app.domain.LastProfileException
import com.dosetrack.app.domain.ProfileRules
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileRow(val profile: ProfileEntity, val medicationCount: Int, val isActive: Boolean)

data class ProfilesUiState(
    val loading: Boolean = true,
    val rows: List<ProfileRow> = emptyList(),
) {
    val canAdd: Boolean get() = ProfileRules.canCreate(rows.size)
    val canDelete: Boolean get() = rows.size > 1
}

enum class ProfilesMessage { DELETED, LAST_PROFILE }

class ProfilesViewModel(private val repository: ProfileRepository) : ViewModel() {
    val state: StateFlow<ProfilesUiState> = combine(
        repository.observeAll(),
        repository.observeMedicationCounts(),
        repository.activeProfile,
    ) { profiles, counts, active ->
        val countById = counts.associate { it.profileId to it.count }
        ProfilesUiState(
            loading = false,
            rows = profiles.map { ProfileRow(it, countById[it.id] ?: 0, it.id == active.id) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfilesUiState())

    private val _messages = Channel<ProfilesMessage>(Channel.BUFFERED)
    val messages: Flow<ProfilesMessage> = _messages.receiveAsFlow()

    fun select(profileId: Long) {
        viewModelScope.launch { repository.select(profileId) }
    }

    /** Permanently deletes the profile and its data; its reminders are cancelled, others untouched. */
    fun delete(profileId: Long) {
        viewModelScope.launch {
            try {
                repository.delete(profileId)
                _messages.send(ProfilesMessage.DELETED)
            } catch (e: LastProfileException) {
                _messages.send(ProfilesMessage.LAST_PROFILE)
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { ProfilesViewModel(this[APPLICATION_KEY]!!.appContainer.profileRepository) }
        }
    }
}
