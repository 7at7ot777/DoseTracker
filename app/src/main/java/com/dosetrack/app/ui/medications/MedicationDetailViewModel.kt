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
import com.dosetrack.app.data.db.MedicationWithSchedules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MedicationDetailUiState(
    val loading: Boolean = true,
    val data: MedicationWithSchedules? = null,
)

class MedicationDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: MedicationRepository,
) : ViewModel() {
    private val medicationId: Long = checkNotNull(savedStateHandle.get<Long>(ARG_ID))

    val state: StateFlow<MedicationDetailUiState> = repository.observe(medicationId)
        .map { MedicationDetailUiState(loading = false, data = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MedicationDetailUiState())

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

        val Factory = viewModelFactory {
            initializer {
                MedicationDetailViewModel(
                    createSavedStateHandle(),
                    this[APPLICATION_KEY]!!.appContainer.medicationRepository,
                )
            }
        }
    }
}
