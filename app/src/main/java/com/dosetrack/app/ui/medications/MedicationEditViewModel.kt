package com.dosetrack.app.ui.medications

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dosetrack.app.appContainer
import com.dosetrack.app.data.MedicationInput
import com.dosetrack.app.data.MedicationRepository
import com.dosetrack.app.data.ProfileRepository
import com.dosetrack.app.data.ScheduleInput
import com.dosetrack.app.data.db.MedicationWithSchedules
import com.dosetrack.app.data.db.effectiveReminderStyle
import com.dosetrack.app.data.model.ReminderStyle
import com.dosetrack.app.data.model.DosageUnit
import com.dosetrack.app.data.model.DurationType
import com.dosetrack.app.data.model.FoodInstruction
import com.dosetrack.app.data.model.Frequency
import com.dosetrack.app.data.model.MedicationType
import com.dosetrack.app.domain.MedicationDuration
import com.dosetrack.app.domain.MedicationValidation
import com.dosetrack.app.domain.MedicationValidationResult
import com.dosetrack.app.domain.ReminderText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** One dose time in the form; [key] keeps list items stable while editing. */
data class TimeRow(val key: Long, val minutes: Int, val note: String = "")

data class MedicationFormState(
    val loading: Boolean = false,
    val isEdit: Boolean = false,
    val notFound: Boolean = false,
    val name: String = "",
    val genericName: String = "",
    val amountText: String = "1",
    val unit: DosageUnit = DosageUnit.TABLET,
    val strength: String = "",
    val type: MedicationType = MedicationType.TABLET,
    val frequency: Frequency = Frequency.ONCE_DAILY,
    val times: List<TimeRow> = emptyList(),
    val startDate: LocalDate = LocalDate.now(),
    val durationType: DurationType = DurationType.ONGOING,
    val daysText: String = "7",
    val endDate: LocalDate? = null,
    val foodInstruction: FoodInstruction = FoodInstruction.ANY_TIME,
    val customInstructions: String = "",
    val notes: String = "",
    val reminderStyle: ReminderStyle = ReminderStyle.NOTIFICATION,
    val showErrors: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
) {
    val validation: MedicationValidationResult
        get() = MedicationValidation.validate(
            name = name,
            amountText = amountText,
            times = times.map { it.minutes },
            durationType = durationType,
            startDate = startDate,
            daysText = daysText,
            endDate = endDate,
        )

    /** Resolved end date for the "Number of days" preview, or null while the input is invalid. */
    val computedEndDate: LocalDate?
        get() = MedicationDuration.endDate(durationType, startDate, MedicationDuration.parseDays(daysText), endDate)
}

class MedicationEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: MedicationRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {
    private val editId: Long? = savedStateHandle.get<Long>(ARG_ID)?.takeIf { it > 0 }
    private var nextKey = 0L

    private val _state = MutableStateFlow(
        MedicationFormState(
            loading = editId != null,
            isEdit = editId != null,
            times = rowsFor(Frequency.ONCE_DAILY.defaultTimes),
        ),
    )
    val state: StateFlow<MedicationFormState> = _state.asStateFlow()

    init {
        if (editId != null) {
            viewModelScope.launch {
                val existing = repository.get(editId)
                _state.value = if (existing == null) {
                    _state.value.copy(loading = false, notFound = true)
                } else {
                    formFrom(existing)
                }
            }
        }
    }

    /** Generic setter for simple fields: `viewModel.edit { copy(name = it) }`. */
    fun edit(transform: MedicationFormState.() -> MedicationFormState) = _state.update { it.transform() }

    fun setFrequency(frequency: Frequency) = _state.update { state ->
        if (frequency == state.frequency) return@update state
        val times = if (frequency == Frequency.CUSTOM) {
            state.times
        } else {
            // Presets reset to their default times; notes carry over by position.
            frequency.defaultTimes.mapIndexed { index, minutes ->
                TimeRow(newKey(), minutes, state.times.getOrNull(index)?.note.orEmpty())
            }
        }
        state.copy(frequency = frequency, times = times)
    }

    fun setTime(key: Long, minutes: Int) = _state.update { state ->
        state.copy(times = state.times.map { if (it.key == key) it.copy(minutes = minutes) else it })
    }

    fun setTimeNote(key: Long, note: String) = _state.update { state ->
        state.copy(times = state.times.map { if (it.key == key) it.copy(note = note) else it })
    }

    fun addTime() = _state.update { state ->
        val last = state.times.maxOfOrNull { it.minutes }
        val minutes = if (last == null) 9 * 60 else (last + 60) % (24 * 60)
        state.copy(frequency = Frequency.CUSTOM, times = state.times + TimeRow(newKey(), minutes))
    }

    fun removeTime(key: Long) = _state.update { state ->
        state.copy(frequency = Frequency.CUSTOM, times = state.times.filterNot { it.key == key })
    }

    fun save() {
        val form = _state.value
        if (form.saving) return
        if (!form.validation.isValid) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            // A new medication belongs to the profile shown when it is saved; edits keep theirs.
            repository.save(form.toInput(profileRepository.activeProfileNow().id))
            _state.update { it.copy(saving = false, saved = true) }
        }
    }

    private fun MedicationFormState.toInput(profileId: Long) = MedicationInput(
        profileId = profileId,
        id = editId,
        name = name,
        genericName = genericName,
        dosageAmount = MedicationValidation.parseAmount(amountText) ?: 1.0,
        dosageUnit = unit,
        strength = strength,
        medicationType = type,
        frequency = frequency,
        foodInstruction = foodInstruction,
        customInstructions = if (foodInstruction == FoodInstruction.CUSTOM) customInstructions else null,
        notes = notes,
        startDate = startDate,
        durationType = durationType,
        durationDays = if (durationType == DurationType.DAYS) MedicationDuration.parseDays(daysText) else null,
        endDate = if (durationType == DurationType.END_DATE) endDate else null,
        times = times.map { ScheduleInput(it.minutes, it.note) },
        reminderStyle = reminderStyle,
    )

    private fun formFrom(data: MedicationWithSchedules): MedicationFormState {
        val m = data.medication
        return MedicationFormState(
            isEdit = true,
            name = m.name,
            genericName = m.genericName.orEmpty(),
            amountText = ReminderText.formatAmount(m.dosageAmount),
            unit = m.dosageUnit,
            strength = m.strength.orEmpty(),
            type = m.medicationType,
            frequency = m.frequency,
            times = data.schedules.sortedBy { it.timeOfDay }.map { TimeRow(newKey(), it.timeOfDay, it.note.orEmpty()) },
            startDate = LocalDate.ofEpochDay(m.startDate),
            durationType = m.durationType,
            daysText = (m.durationDays ?: 7).toString(),
            endDate = m.endDate?.let(LocalDate::ofEpochDay),
            foodInstruction = m.foodInstruction,
            customInstructions = m.customInstructions.orEmpty(),
            notes = m.notes.orEmpty(),
            reminderStyle = m.effectiveReminderStyle,
        )
    }

    private fun rowsFor(minutes: List<Int>) = minutes.map { TimeRow(newKey(), it) }

    private fun newKey(): Long = nextKey++

    companion object {
        const val ARG_ID = "id"

        val Factory = viewModelFactory {
            initializer {
                MedicationEditViewModel(
                    createSavedStateHandle(),
                    this[APPLICATION_KEY]!!.appContainer.medicationRepository,
                    this[APPLICATION_KEY]!!.appContainer.profileRepository,
                )
            }
        }
    }
}
