package com.dosetrack.app.ui.medications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dosetrack.app.appContainer
import com.dosetrack.app.data.MedicationRepository
import com.dosetrack.app.data.db.MedicationWithSchedules
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class MedicationListItem(val data: MedicationWithSchedules, val nextDoseAt: Long?)

data class MedicationListUiState(
    val loading: Boolean = true,
    val items: List<MedicationListItem> = emptyList(),
)

class MedicationListViewModel(repository: MedicationRepository) : ViewModel() {
    val state: StateFlow<MedicationListUiState> = combine(
        repository.observeVisible(),
        repository.observeNextDoses(System.currentTimeMillis()),
    ) { medications, nextDoses ->
        val nextById = nextDoses.associate { it.medicationId to it.nextAt }
        MedicationListUiState(
            loading = false,
            items = medications.map { MedicationListItem(it, nextById[it.medication.id]) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MedicationListUiState())

    companion object {
        val Factory = viewModelFactory {
            initializer {
                MedicationListViewModel(this[APPLICATION_KEY]!!.appContainer.medicationRepository)
            }
        }
    }
}
