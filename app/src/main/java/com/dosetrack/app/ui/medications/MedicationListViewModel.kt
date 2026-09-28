package com.dosetrack.app.ui.medications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dosetrack.app.appContainer
import com.dosetrack.app.data.MedicationRepository
import com.dosetrack.app.data.ProfileRepository
import com.dosetrack.app.data.db.MedicationWithSchedules
import com.dosetrack.app.data.db.ProfileEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

data class MedicationListItem(val data: MedicationWithSchedules, val nextDoseAt: Long?)

data class MedicationListUiState(
    val loading: Boolean = true,
    val items: List<MedicationListItem> = emptyList(),
    val profile: ProfileEntity? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class MedicationListViewModel(
    repository: MedicationRepository,
    profileRepository: ProfileRepository,
) : ViewModel() {
    /** "Next dose" must move on as doses pass, so the lower bound follows the clock. */
    private val nextDoses = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(TICK_MILLIS)
        }
    }.flatMapLatest { now -> repository.observeNextDoses(now) }

    private val activeProfile = profileRepository.activeProfile

    val state: StateFlow<MedicationListUiState> = combine(
        activeProfile.flatMapLatest { profile -> repository.observeVisible(profile.id) },
        nextDoses,
        activeProfile,
    ) { medications, next, profile ->
        val nextById = next.associate { it.medicationId to it.nextAt }
        MedicationListUiState(
            loading = false,
            items = medications.map { MedicationListItem(it, nextById[it.medication.id]) },
            profile = profile,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MedicationListUiState())

    companion object {
        private const val TICK_MILLIS = 60_000L

        val Factory = viewModelFactory {
            initializer {
                val container = this[APPLICATION_KEY]!!.appContainer
                MedicationListViewModel(container.medicationRepository, container.profileRepository)
            }
        }
    }
}
