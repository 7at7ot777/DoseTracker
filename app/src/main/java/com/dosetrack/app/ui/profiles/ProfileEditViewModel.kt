package com.dosetrack.app.ui.profiles

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dosetrack.app.appContainer
import com.dosetrack.app.data.ProfileRepository
import com.dosetrack.app.domain.ProfileLimitReachedException
import com.dosetrack.app.domain.ProfileRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileFormState(
    val loading: Boolean = false,
    val isEdit: Boolean = false,
    val notFound: Boolean = false,
    val profileId: Long = 0,
    val name: String = "",
    /** null = automatic colour. */
    val colorArgb: Int? = null,
    val savedPhotoPath: String? = null,
    /** Picked but not yet saved. */
    val pendingPhoto: Uri? = null,
    val removePhoto: Boolean = false,
    val showErrors: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val limitReached: Boolean = false,
    val photoFailed: Boolean = false,
) {
    val nameError: ProfileRules.NameError? get() = ProfileRules.validateName(name)
    val hasPhoto: Boolean get() = pendingPhoto != null || (savedPhotoPath != null && !removePhoto)
}

class ProfileEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: ProfileRepository,
) : ViewModel() {
    private val editId: Long? = savedStateHandle.get<Long>(ARG_ID)?.takeIf { it > 0 }

    private val _state = MutableStateFlow(ProfileFormState(loading = editId != null, isEdit = editId != null))
    val state: StateFlow<ProfileFormState> = _state.asStateFlow()

    init {
        if (editId != null) {
            viewModelScope.launch {
                val profile = repository.get(editId)
                _state.value = if (profile == null) {
                    ProfileFormState(notFound = true)
                } else {
                    ProfileFormState(
                        isEdit = true,
                        profileId = profile.id,
                        name = profile.name,
                        colorArgb = profile.colorArgb,
                        savedPhotoPath = profile.photoPath,
                    )
                }
            }
        }
    }

    fun setName(name: String) = _state.update { it.copy(name = name) }

    fun setColor(colorArgb: Int?) = _state.update { it.copy(colorArgb = colorArgb) }

    fun pickPhoto(uri: Uri) = _state.update { it.copy(pendingPhoto = uri, removePhoto = false) }

    fun removePhoto() = _state.update { it.copy(pendingPhoto = null, removePhoto = true) }

    fun save() {
        val form = _state.value
        if (form.saving) return
        if (form.nameError != null) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val id = try {
                if (editId != null) {
                    repository.update(editId, form.name, form.colorArgb)
                    editId
                } else {
                    repository.create(form.name, form.colorArgb)
                }
            } catch (e: ProfileLimitReachedException) {
                _state.update { it.copy(saving = false, limitReached = true) }
                return@launch
            }

            // The profile is saved even if the photo can't be read.
            val photoOk = try {
                when {
                    form.pendingPhoto != null -> repository.setPhoto(id, form.pendingPhoto)
                    form.removePhoto -> repository.setPhoto(id, null)
                }
                true
            } catch (e: Exception) {
                false
            }
            // A newly added person becomes the one shown, ready for their medications.
            if (editId == null) repository.select(id)
            _state.update { it.copy(saving = false, saved = photoOk, photoFailed = !photoOk, isEdit = true) }
        }
    }

    fun photoErrorShown() = _state.update { it.copy(photoFailed = false, saved = true) }

    companion object {
        const val ARG_ID = "id"

        val Factory = viewModelFactory {
            initializer {
                ProfileEditViewModel(
                    createSavedStateHandle(),
                    this[APPLICATION_KEY]!!.appContainer.profileRepository,
                )
            }
        }
    }
}
