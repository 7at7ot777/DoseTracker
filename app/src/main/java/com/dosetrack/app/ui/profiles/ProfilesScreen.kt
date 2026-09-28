package com.dosetrack.app.ui.profiles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dosetrack.app.R
import com.dosetrack.app.domain.ProfileRules
import com.dosetrack.app.ui.Formats
import com.dosetrack.app.ui.appResources
import com.dosetrack.app.ui.components.ConfirmDialog
import com.dosetrack.app.ui.components.StatusBadge
import com.dosetrack.app.ui.currentLocale
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    onBack: () -> Unit,
    onAddProfile: () -> Unit,
    onEditProfile: (Long) -> Unit,
    viewModel: ProfilesViewModel = viewModel(factory = ProfilesViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val res = appResources()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf<ProfileRow?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(
                res.getString(
                    when (message) {
                        ProfilesMessage.DELETED -> R.string.profile_deleted
                        ProfilesMessage.LAST_PROFILE -> R.string.profile_delete_last
                    },
                ),
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profiles_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.canAdd) {
                ExtendedFloatingActionButton(
                    onClick = onAddProfile,
                    icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                    text = { Text(stringResource(R.string.profile_add)) },
                )
            }
        },
    ) { padding ->
        if (!state.loading) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "info") {
                    Text(
                        text = stringResource(R.string.profiles_info, state.rows.size, ProfileRules.MAX_PROFILES),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!state.canAdd) {
                    item(key = "limit") {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                        ) {
                            Text(
                                text = stringResource(R.string.profile_limit_reached, ProfileRules.MAX_PROFILES),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }
                items(state.rows, key = { it.profile.id }) { row ->
                    ProfileCard(
                        row = row,
                        canDelete = state.canDelete,
                        onClick = { onEditProfile(row.profile.id) },
                        onDelete = { confirmDelete = row },
                    )
                }
            }
        }
    }

    confirmDelete?.let { row ->
        ConfirmDialog(
            title = stringResource(R.string.profile_delete_title, row.profile.name),
            message = stringResource(
                R.string.profile_delete_message,
                res.getQuantityString(R.plurals.medications_count, row.medicationCount, row.medicationCount),
            ),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                viewModel.delete(row.profile.id)
                confirmDelete = null
            },
            onDismiss = { confirmDelete = null },
        )
    }
}

@Composable
private fun ProfileCard(
    row: ProfileRow,
    canDelete: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val res = appResources()
    val locale = currentLocale()
    val created = Instant.ofEpochMilli(row.profile.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
    Card(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(row.profile.name, style = MaterialTheme.typography.titleMedium) },
            supportingContent = {
                Column {
                    Text(res.getQuantityString(R.plurals.medications_count, row.medicationCount, row.medicationCount))
                    Text(stringResource(R.string.profile_created, Formats.date(created, locale)))
                    if (row.isActive) {
                        StatusBadge(
                            text = stringResource(R.string.profile_active),
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            },
            leadingContent = { ProfileAvatar(row.profile, size = 48.dp) },
            trailingContent = if (canDelete) {
                {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.profile_delete_description, row.profile.name),
                        )
                    }
                }
            } else {
                null
            },
            modifier = Modifier
                .heightIn(min = 72.dp)
                .clickable(onClick = onClick),
        )
    }
}
