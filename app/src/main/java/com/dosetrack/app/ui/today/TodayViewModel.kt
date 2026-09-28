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
import com.dosetrack.app.data.ProfileRepository
import com.dosetrack.app.data.ProfileSelection
import com.dosetrack.app.data.SettingsRepository
import com.dosetrack.app.data.db.DoseEntity
import com.dosetrack.app.data.db.DoseWithMedication
import com.dosetrack.app.data.db.MedicationEntity
import com.dosetrack.app.data.db.ProfileEntity
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
import kotlinx.coroutines.flow.onEach
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
    /** Owner of the medication (shown as a chip in the "Everyone" view). */
    val profile: ProfileEntity?,
)

data class TodayUiState(
    val loading: Boolean = true,
    val date: LocalDate = LocalDate.now(),
    val doses: List<TodayDose> = emptyList(),
    /** Medications exist for what's shown (active profile, or anyone in "Everyone"). */
    val hasMedications: Boolean = false,
    val selection: ProfileSelection? = null,
    val profiles: List<ProfileEntity> = emptyList(),
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
    private val profileRepository: ProfileRepository,
    private val doseSync: DoseSync,
) : ViewModel() {

    /**
     * Ticks every 30 s while the screen is visible to refresh Upcoming/Due/Missed labels
     * (Missed is derived from the clock, so no database work is needed per tick).
     */
    private val clock: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(TICK_MILLIS)
        }
    }

    /** (now, local date) shared so the clock runs once per tick for all consumers. */
    private val ticks: SharedFlow<Pair<Long, LocalDate>> = clock
        .map { now -> now to Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate() }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    /**
     * Syncs when the screen starts and whenever the day rolls over (creates the new day's doses,
     * records misses, re-arms alarms); alarms and the background worker cover the rest.
     */
    private val days = ticks
        .map { it.second }
        .distinctUntilChanged()
        .onEach { doseSync.sync() }

    private data class DayDoses(val day: LocalDate, val selection: ProfileSelection, val doses: List<DoseWithMedication>)

    /** Only the view changes with the selection; reminders never depend on it. */
    private val dosesOfDay = combine(days, profileRepository.selection) { day, selection -> day to selection }
        .distinctUntilChanged()
        .flatMapLatest { (day, selection) ->
            val profileId = if (selection.everyone) null else selection.profile.id
            doseRepository.observeDay(day, profileId).map { DayDoses(day, selection, it) }
        }

    private val profilesAndCounts = combine(
        profileRepository.observeAll(),
        profileRepository.observeMedicationCounts(),
    ) { profiles, counts -> profiles to counts.associate { it.profileId to it.count } }

    val state: StateFlow<TodayUiState> = combine(
        ticks,
        dosesOfDay,
        profilesAndCounts,
        settingsRepository.settings,
        medicationRepository.observeAnyAlarmMode(),
    ) { tick, dayDoses, (profiles, counts), settings, anyAlarmMode ->
        val now = tick.first
        val selection = dayDoses.selection
        val profilesById = profiles.associateBy { it.id }
        TodayUiState(
            loading = false,
            date = dayDoses.day,
            doses = dayDoses.doses.map { item ->
                TodayDose(
                    dose = item.dose,
                    medication = item.medication,
                    state = displayStateOf(item.dose.status, item.dose.scheduledAt, now, item.dose.snoozedUntil),
                    profile = profilesById[item.medication.profileId],
                )
            },
            hasMedications = if (selection.everyone) {
                counts.values.any { it > 0 }
            } else {
                (counts[selection.profile.id] ?: 0) > 0
            },
            selection = selection,
            profiles = profiles,
            remindersEnabled = settings.remindersEnabled,
            usesAlarmMode = anyAlarmMode,
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
                    container.profileRepository,
                    container.doseSync,
                )
            }
        }
    }
}
