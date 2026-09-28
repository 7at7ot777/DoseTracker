package com.dosetrack.app.ui.profiles

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dosetrack.app.R
import com.dosetrack.app.data.db.ProfileEntity
import com.dosetrack.app.domain.ProfileRules

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: ProfileEditViewModel = viewModel(factory = ProfileEditViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isEdit) R.string.profile_edit_title else R.string.profile_add)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> Unit
            state.notFound -> Text(
                text = stringResource(R.string.profile_not_found),
                modifier = Modifier
                    .padding(padding)
                    .padding(24.dp),
            )
            else -> ProfileForm(state = state, viewModel = viewModel, modifier = Modifier.padding(padding))
        }
    }

    if (state.limitReached) {
        AlertDialog(
            onDismissRequest = onBack,
            title = { Text(stringResource(R.string.profile_add)) },
            text = { Text(stringResource(R.string.profile_limit_reached, ProfileRules.MAX_PROFILES)) },
            confirmButton = { TextButton(onClick = onBack) { Text(stringResource(R.string.action_ok)) } },
        )
    }
    if (state.photoFailed) {
        AlertDialog(
            onDismissRequest = viewModel::photoErrorShown,
            text = { Text(stringResource(R.string.profile_photo_failed)) },
            confirmButton = { TextButton(onClick = viewModel::photoErrorShown) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileForm(state: ProfileFormState, viewModel: ProfileEditViewModel, modifier: Modifier = Modifier) {
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.pickPhoto(uri)
    }
    val previewColor = Color(state.colorArgb ?: ProfileRules.defaultColor(state.profileId.coerceAtLeast(1)))

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val pending = state.pendingPhoto
        if (pending != null) {
            PendingPhotoAvatar(uri = pending, profileName = state.name, color = previewColor, size = 112.dp)
        } else {
            // Unsaved preview: current name/colour, and the saved photo unless it is being removed.
            ProfileAvatar(
                profile = ProfileEntity(
                    id = state.profileId.coerceAtLeast(1),
                    name = state.name,
                    colorArgb = state.colorArgb,
                    photoPath = state.savedPhotoPath.takeUnless { state.removePhoto },
                    createdAt = 0,
                    updatedAt = 0,
                ),
                size = 112.dp,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                Text(stringResource(R.string.profile_choose_photo), modifier = Modifier.padding(start = 8.dp))
            }
            if (state.hasPhoto) {
                TextButton(onClick = viewModel::removePhoto, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.profile_remove_photo))
                }
            }
        }

        val nameErrorText = when (if (state.showErrors) state.nameError else null) {
            ProfileRules.NameError.REQUIRED -> stringResource(R.string.profile_name_required)
            ProfileRules.NameError.TOO_LONG -> stringResource(R.string.profile_name_too_long, ProfileRules.MAX_NAME_LENGTH)
            null -> null
        }
        OutlinedTextField(
            value = state.name,
            onValueChange = viewModel::setName,
            label = { Text(stringResource(R.string.profile_name)) },
            singleLine = true,
            isError = nameErrorText != null,
            supportingText = if (nameErrorText != null) {
                { Text(nameErrorText) }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = stringResource(R.string.profile_color),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            ColorSwatch(
                color = Color(ProfileRules.defaultColor(state.profileId.coerceAtLeast(1))),
                selected = state.colorArgb == null,
                label = stringResource(R.string.profile_color_auto),
                showAutoMark = true,
                onClick = { viewModel.setColor(null) },
            )
            ProfileRules.PALETTE.forEachIndexed { index, argb ->
                ColorSwatch(
                    color = Color(argb),
                    selected = state.colorArgb == argb,
                    label = stringResource(R.string.profile_color_option, index + 1),
                    showAutoMark = false,
                    onClick = { viewModel.setColor(argb) },
                )
            }
        }

        Button(
            onClick = viewModel::save,
            enabled = !state.saving,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        ) {
            Text(stringResource(R.string.action_save))
        }
    }
}

@Composable
private fun ColorSwatch(
    color: Color,
    selected: Boolean,
    label: String,
    showAutoMark: Boolean,
    onClick: () -> Unit,
) {
    val ring = MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .then(if (selected) Modifier.border(3.dp, ring, CircleShape) else Modifier)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                this.selected = selected
            }
            .clickable(role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(38.dp)) { drawCircle(color) }
        when {
            selected -> Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
            showAutoMark -> Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White)
        }
    }
}
