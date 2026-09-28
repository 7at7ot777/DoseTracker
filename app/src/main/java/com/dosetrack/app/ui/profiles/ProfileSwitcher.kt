package com.dosetrack.app.ui.profiles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dosetrack.app.R
import com.dosetrack.app.appContainer
import com.dosetrack.app.data.db.ProfileEntity
import com.dosetrack.app.domain.ProfileRules
import kotlinx.coroutines.launch

/**
 * Top-bar avatar button that opens the profile picker. Switching only changes what the screens
 * show; it never touches reminders (they are scheduled from dose records for every profile).
 *
 * @param offerEveryone show the "Everyone" entry (Today only).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSwitcher(
    offerEveryone: Boolean,
    onManageProfiles: () -> Unit,
    onAddProfile: () -> Unit,
) {
    val repository = LocalContext.current.appContainer.profileRepository
    val profilesFlow = remember(repository) { repository.observeAll() }
    val profiles by profilesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val selection by repository.selection.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }

    val current = selection
    if (current != null) {
        val everyoneShown = offerEveryone && current.everyone
        val switchLabel = stringResource(
            R.string.profile_switch_description,
            if (everyoneShown) stringResource(R.string.profile_everyone) else current.profile.name,
        )
        IconButton(
            onClick = { open = true },
            modifier = Modifier.semantics { contentDescription = switchLabel },
        ) {
            if (everyoneShown) {
                EveryoneAvatar(size = 32.dp)
            } else {
                ProfileAvatar(current.profile, size = 32.dp)
            }
        }
        if (open) {
            PickerSheet(
                profiles = profiles,
                currentProfileId = current.profile.id,
                offerEveryone = offerEveryone,
                everyoneShown = everyoneShown,
                onDismiss = { open = false },
                onSelectEveryone = {
                    open = false
                    scope.launch { repository.selectEveryone(true) }
                },
                onSelect = { id ->
                    open = false
                    scope.launch { repository.select(id) }
                },
                onAddProfile = {
                    open = false
                    onAddProfile()
                },
                onManageProfiles = {
                    open = false
                    onManageProfiles()
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerSheet(
    profiles: List<ProfileEntity>,
    currentProfileId: Long,
    offerEveryone: Boolean,
    everyoneShown: Boolean,
    onDismiss: () -> Unit,
    onSelectEveryone: () -> Unit,
    onSelect: (Long) -> Unit,
    onAddProfile: () -> Unit,
    onManageProfiles: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
            Column(modifier = Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
                Text(
                    text = stringResource(R.string.profiles_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                if (offerEveryone) {
                    PickerRow(
                        leading = { EveryoneAvatar() },
                        title = stringResource(R.string.profile_everyone),
                        subtitle = stringResource(R.string.profile_everyone_summary),
                        selected = everyoneShown,
                        onClick = onSelectEveryone,
                    )
                }
                profiles.forEach { profile ->
                    PickerRow(
                        leading = { ProfileAvatar(profile) },
                        title = profile.name,
                        subtitle = null,
                        selected = !everyoneShown && profile.id == currentProfileId,
                        onClick = { onSelect(profile.id) },
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                val canAdd = ProfileRules.canCreate(profiles.size)
                ListItem(
                    headlineContent = { Text(stringResource(R.string.profile_add)) },
                    supportingContent = if (canAdd) {
                        null
                    } else {
                        { Text(stringResource(R.string.profile_limit_reached, ProfileRules.MAX_PROFILES)) }
                    },
                    leadingContent = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                    colors = if (canAdd) {
                        ListItemDefaults.colors()
                    } else {
                        ListItemDefaults.colors(
                            headlineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            leadingIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable(enabled = canAdd, onClick = onAddProfile),
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.profiles_manage)) },
                    leadingContent = { Icon(Icons.Filled.ManageAccounts, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable(onClick = onManageProfiles),
                )
            }
    }
}

@Composable
private fun PickerRow(
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = if (subtitle != null) {
            { Text(subtitle) }
        } else {
            null
        },
        leadingContent = leading,
        trailingContent = if (selected) {
            { Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.profile_selected)) }
        } else {
            null
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick),
    )
}
