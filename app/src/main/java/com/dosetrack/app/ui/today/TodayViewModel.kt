package com.dosetrack.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dosetrack.app.appContainer
import com.dosetrack.app.data.AppSettings
import com.dosetrack.app.data.DoseRepository
import com.dosetrack.app.data.MedicationRepository
import com.dosetrack.app.data.SettingsRepository
import com.dosetrack.app.data.db.DoseEntity
import com.dosetrack.app.data.db.MedicationEntity
import com.dosetrack.app.data.db.effectiveReminderStyle
import com.dosetrack.app.data.model.MedicationStatus
import com.dosetrack.app.data.model.ReminderStyle
import com.dosetrack.app.domain.DoseDisplayState
import com.dosetrack.app.domain.DoseSync
import com.dosetrack.app.domain.displayStateOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class TodayDose(
    val dose: DoseEntity,
    val medication: MedicationEntity,
    val state: DoseDisplayState,
)

data class TodayUiState(
    val loading: Boolean = true,
    val date: LocalDate = LocalDate.now(),
    val doses: List<TodayDose> = emptyList(),
    val hasMedications: Boolean = false,
    val remindersEnabled: Boolean = true,
    /** Any active medication rings as an alarm (needs full-screen alarm permission on Android 14+). */
    val usesAlarmMode: Boolean = false,
    val defaultSnoozeMinutes: Int = AppSettings.DEFAULT_SNOOZE_MINUTES,
) {
    val total: Int get() = doses.size
    val taken: Int get() = doses.count { it.state == DoseDisplayState.TAKEN }
    val missed: Int get() = doses.count { it.state == DoseDisplayState.MISSED }
    val skipped: Int get() = doses.count { it.state == DoseDisplayState.SKIPPED }
}

sealed interface TodayEvent {
    data class Taken(val medicationName: String, val previous: DoseEntity) : TodayEvent
    data class Skipped(val medicationName: String, val previous: DoseEntity) : TodayEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val doseRepository: DoseRepository,
    medicationRepository: MedicationRepository,
    settingsRepository: SettingsRepository,
    private val doseSync: DoseSync,
) : ViewModel() {

    /**
     * Ticks every 30 s while the screen is visible: re-syncs doses (creates tomorrow's rows,
     * records misses) and refreshes Upcoming/Due/Missed labels.
     */
    private val clock: Flow<Long> = flow {
        while (true) {
            doseSync.sync()
            emit(System.currentTimeMillis())
            delay(TICK_MILLIS)
        }
    }

    /** (now, local date) shared so the clock (and its sync) runs once per tick. */
    private val ticks: SharedFlow<Pair<Long, LocalDate>> = clock
        .map { now -> now to Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate() }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private val dosesOfDay = ticks
        .map { it.second }
        .distinctUntilChanged()
        .flatMapLatest { day -> doseRepository.observeDay(day).map { day to it } }

    val state: StateFlow<TodayUiState> = combine(
        ticks,
        dosesOfDay,
        medicationRepository.observeVisible(),
        settingsRepository.settings,
    ) { tick, dayDoses, medications, settings ->
        val now = tick.first
        TodayUiState(
            loading = false,
            date = dayDoses.first,
            doses = dayDoses.second.map { item ->
                TodayDose(
                    dose = item.dose,
                    medication = item.medication,
                    state = displayStateOf(item.dose.status, item.dose.scheduledAt, now, item.dose.snoozedUntil),
                )
            },
            hasMedications = medications.isNotEmpty(),
            remindersEnabled = settings.remindersEnabled,
            usesAlarmMode = medications.any {
                it.medication.status == MedicationStatus.ACTIVE &&
                    it.medication.effectiveReminderStyle == ReminderStyle.ALARM
            },
            defaultSnoozeMinutes = settings.snoozeMinutes,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    private val _events = Channel<TodayEvent>(Channel.BUFFERED)
    val events: Flow<TodayEvent> = _events.receiveAsFlow()

    fun take(item: TodayDose) {
        viewModelScope.launch {
            doseRepository.markTaken(item.dose.id)?.let { _events.send(TodayEvent.Taken(item.medication.name, it)) }
        }
    }

    fun skip(item: TodayDose) {
        viewModelScope.launch {
            doseRepository.markSkipped(item.dose.id)?.let { _events.send(TodayEvent.Skipped(item.medication.name, it)) }
        }
    }

    fun snooze(item: TodayDose, minutes: Int) {
        viewModelScope.launch {
            doseRepository.snooze(item.dose.id, System.currentTimeMillis() + minutes * 60_000L)
        }
    }

    fun markNotTaken(item: TodayDose) {
        viewModelScope.launch { doseRepository.markNotTaken(item.dose.id) }
    }

    fun undo(previous: DoseEntity) {
        viewModelScope.launch { doseRepository.restore(previous) }
    }

    companion object {
        private const val TICK_MILLIS = 30_000L

        val Factory = viewModelFactory {
            initializer {
                val container = this[APPLICATION_KEY]!!.appContainer
                TodayViewModel(
                    container.doseRepository,
                    container.medicationRepository,
                    container.settingsRepository,
                    container.doseSync,
                )
            }
        }
    }
}
