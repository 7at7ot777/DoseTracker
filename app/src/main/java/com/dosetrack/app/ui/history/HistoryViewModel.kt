package com.dosetrack.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dosetrack.app.appContainer
import com.dosetrack.app.data.DoseRepository
import com.dosetrack.app.data.db.DoseEntity
import com.dosetrack.app.data.db.DoseWithMedication
import com.dosetrack.app.data.db.MedicationEntity
import com.dosetrack.app.domain.AdherenceBuckets
import com.dosetrack.app.domain.AdherenceStats
import com.dosetrack.app.domain.DayAdherence
import com.dosetrack.app.domain.DoseDisplayState
import com.dosetrack.app.domain.displayStateOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class HistoryRow(
    val dose: DoseEntity,
    val medication: MedicationEntity,
    val state: DoseDisplayState,
)

data class HistoryUiState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val rows: List<HistoryRow> = emptyList(),
    val week: List<DayAdherence> = emptyList(),
    val weekStats: AdherenceStats = AdherenceStats.EMPTY,
)

sealed interface Correction {
    data class Taken(val takenAt: Long) : Correction
    data object Skipped : Correction
    data object Missed : Correction
}

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(private val doseRepository: DoseRepository) : ViewModel() {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val selectedDate = MutableStateFlow(LocalDate.now())

    /** Re-evaluates Due → Missed labels and the "today" boundary once a minute while visible. */
    private val clock: SharedFlow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(TICK_MILLIS)
        }
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private val today = clock
        .map { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        .distinctUntilChanged()

    private val dayDoses = selectedDate.flatMapLatest { date ->
        doseRepository.observeDay(date).map { date to it }
    }

    private val weekDoses = today.flatMapLatest { day ->
        doseRepository.observeRange(day.minusDays(WEEK_DAYS - 1L), day).map { day to it }
    }

    val state: StateFlow<HistoryUiState> = combine(clock, dayDoses, weekDoses) { now, day, week ->
        val todayDate = week.first
        val weekStates = week.second.map { it.dose.scheduledAt to it.displayState(now) }
        val buckets = AdherenceBuckets.daily(weekStates, todayDate, zone, WEEK_DAYS)
        HistoryUiState(
            loading = false,
            today = todayDate,
            selectedDate = day.first,
            rows = day.second.map { HistoryRow(it.dose, it.medication, it.displayState(now)) },
            week = buckets,
            weekStats = AdherenceBuckets.total(buckets),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun selectDate(date: LocalDate) = selectedDate.update { date }

    fun previousDay() = selectedDate.update { it.minusDays(1) }

    fun nextDay() = selectedDate.update { it.plusDays(1) }

    fun goToToday() = selectedDate.update { LocalDate.now() }

    fun correct(row: HistoryRow, correction: Correction) {
        viewModelScope.launch {
            when (correction) {
                is Correction.Taken -> doseRepository.markTaken(row.dose.id, correction.takenAt)
                Correction.Skipped -> doseRepository.markSkipped(row.dose.id)
                Correction.Missed -> doseRepository.markMissed(row.dose.id)
            }
        }
    }

    private fun DoseWithMedication.displayState(now: Long) =
        displayStateOf(dose.status, dose.scheduledAt, now, dose.snoozedUntil)

    companion object {
        private const val TICK_MILLIS = 60_000L
        const val WEEK_DAYS = 7

        val Factory = viewModelFactory {
            initializer {
                HistoryViewModel(this[APPLICATION_KEY]!!.appContainer.doseRepository)
            }
        }
    }
}
