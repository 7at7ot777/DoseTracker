package com.dosetrack.app.ui.medications

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dosetrack.app.appContainer
import com.dosetrack.app.data.MedicationRepository
import com.dosetrack.app.data.ProfileRepository
import com.dosetrack.app.data.db.ProfileEntity
import com.dosetrack.app.data.db.DoseEntity
import com.dosetrack.app.data.db.MedicationWithSchedules
import com.dosetrack.app.domain.AdherenceStats
import com.dosetrack.app.domain.DoseDisplayState
import com.dosetrack.app.domain.displayStateOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MedicationDetailUiState(
    val loading: Boolean = true,
    val data: MedicationWithSchedules? = null,
    val stats: AdherenceStats = AdherenceStats.EMPTY,
    /** Most recent past doses with their display state, newest first. */
    val recentDoses: List<Pair<DoseEntity, DoseDisplayState>> = emptyList(),
    val profile: ProfileEntity? = null,
)

class MedicationDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: MedicationRepository,
    profileRepository: ProfileRepository,
) : ViewModel() {
    private val medicationId: Long = checkNotNull(savedStateHandle.get<Long>(ARG_ID))
    private val openedAt = System.currentTimeMillis()

    val state: StateFlow<MedicationDetailUiState> = combine(
        repository.observe(medicationId),
        repository.observeStatusCounts(medicationId),
        repository.observeRecentDoses(medicationId, before = openedAt, limit = RECENT_LIMIT),
        profileRepository.observeAll(),
    ) { data, counts, recent, profiles ->
        MedicationDetailUiState(
            loading = false,
            data = data,
            profile = profiles.firstOrNull { it.id == data?.medication?.profileId },
            stats = AdherenceStats.from(counts.associate { it.status to it.count }),
            recentDoses = recent.map {
                it to displayStateOf(it.status, it.scheduledAt, openedAt, it.snoozedUntil)
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MedicationDetailUiState())

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    fun pause() {
        viewModelScope.launch { repository.pause(medicationId) }
    }

    fun resume() {
        viewModelScope.launch { repository.resume(medicationId) }
    }

    fun delete() {
        viewModelScope.launch {
            repository.archive(medicationId)
            _deleted.value = true
        }
    }

    companion object {
        const val ARG_ID = "id"
        private const val RECENT_LIMIT = 10

        val Factory = viewModelFactory {
            initializer {
                MedicationDetailViewModel(
                    createSavedStateHandle(),
                    this[APPLICATION_KEY]!!.appContainer.medicationRepository,
                    this[APPLICATION_KEY]!!.appContainer.profileRepository,
                )
            }
        }
    }
}
