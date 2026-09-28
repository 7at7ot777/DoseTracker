package com.dosetrack.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dosetrack.app.R
import com.dosetrack.app.ui.history.HistoryScreen
import com.dosetrack.app.ui.medications.MedicationDetailScreen
import com.dosetrack.app.ui.medications.MedicationDetailViewModel
import com.dosetrack.app.ui.medications.MedicationEditScreen
import com.dosetrack.app.ui.medications.MedicationEditViewModel
import com.dosetrack.app.ui.medications.MedicationListScreen
import com.dosetrack.app.ui.onboarding.OnboardingScreen
import com.dosetrack.app.ui.profiles.ProfileEditScreen
import com.dosetrack.app.ui.profiles.ProfileEditViewModel
import com.dosetrack.app.ui.profiles.ProfilesScreen
import com.dosetrack.app.ui.settings.SettingsScreen
import com.dosetrack.app.ui.today.TodayScreen

object Routes {
    const val TODAY = "today"
    const val MEDICATIONS = "medications"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val ONBOARDING = "onboarding"

    const val MEDICATION_DETAIL = "medication/{${MedicationDetailViewModel.ARG_ID}}"
    const val MEDICATION_EDIT = "medication_edit?${MedicationEditViewModel.ARG_ID}={${MedicationEditViewModel.ARG_ID}}"

    const val PROFILES = "profiles"
    const val PROFILE_EDIT = "profile_edit?${ProfileEditViewModel.ARG_ID}={${ProfileEditViewModel.ARG_ID}}"

    fun medicationDetail(id: Long) = "medication/$id"
    fun medicationEdit(id: Long? = null) = "medication_edit?${MedicationEditViewModel.ARG_ID}=${id ?: -1L}"
    fun profileEdit(id: Long? = null) = "profile_edit?${ProfileEditViewModel.ARG_ID}=${id ?: -1L}"
}

/** Where a dose notification should lead: the owner's medication. */
data class DeepLink(val profileId: Long, val medicationId: Long)

private enum class TopLevelDestination(
    val route: String,
    @StringRes val label: Int,
    val icon: ImageVector,
) {
    TODAY(Routes.TODAY, R.string.nav_today, Icons.Filled.Today),
    MEDICATIONS(Routes.MEDICATIONS, R.string.nav_medications, Icons.Filled.Medication),
    HISTORY(Routes.HISTORY, R.string.nav_history, Icons.Filled.History),
    SETTINGS(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings),
}

@Composable
fun DoseTrackNavHost(
    startDestination: String,
    deepLink: DeepLink? = null,
    onDeepLinkHandled: () -> Unit = {},
) {
    val navController = rememberNavController()

    // The profile was already selected by MainActivity; open the medication on top of Today.
    LaunchedEffect(deepLink) {
        if (deepLink != null) {
            navController.navigate(Routes.medicationDetail(deepLink.medicationId)) {
                popUpTo(Routes.TODAY)
                launchSingleTop = true
            }
            onDeepLinkHandled()
        }
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = TopLevelDestination.entries.any { dest ->
        currentDestination?.hierarchy?.any { it.route == dest.route } == true
    }

    Scaffold(
        // Screens own their top bars and insets; this scaffold only reserves room for the bottom bar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { dest ->
                        val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(dest.route) {
                                    // Today is the root tab (onboarding, when shown, is removed from the stack).
                                    popUpTo(Routes.TODAY) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = null) },
                            label = { Text(stringResource(dest.label)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onFinished = {
                        navController.navigate(Routes.TODAY) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    },
                )
            }
            val openProfiles = { navController.navigate(Routes.PROFILES) }
            val addProfile = { navController.navigate(Routes.profileEdit()) }

            composable(Routes.TODAY) {
                TodayScreen(
                    onAddMedication = { navController.navigate(Routes.medicationEdit()) },
                    onOpenMedication = { id -> navController.navigate(Routes.medicationDetail(id)) },
                    onManageProfiles = openProfiles,
                    onAddProfile = addProfile,
                )
            }
            composable(Routes.MEDICATIONS) {
                MedicationListScreen(
                    onAddMedication = { navController.navigate(Routes.medicationEdit()) },
                    onOpenMedication = { id -> navController.navigate(Routes.medicationDetail(id)) },
                    onManageProfiles = openProfiles,
                    onAddProfile = addProfile,
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(onManageProfiles = openProfiles, onAddProfile = addProfile)
            }
            composable(Routes.SETTINGS) { SettingsScreen(onOpenProfiles = openProfiles) }

            composable(Routes.PROFILES) {
                ProfilesScreen(
                    onBack = { navController.popBackStack() },
                    onAddProfile = addProfile,
                    onEditProfile = { id -> navController.navigate(Routes.profileEdit(id)) },
                )
            }
            composable(
                route = Routes.PROFILE_EDIT,
                arguments = listOf(
                    navArgument(ProfileEditViewModel.ARG_ID) {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                ),
            ) {
                ProfileEditScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }

            composable(
                route = Routes.MEDICATION_DETAIL,
                arguments = listOf(navArgument(MedicationDetailViewModel.ARG_ID) { type = NavType.LongType }),
            ) {
                MedicationDetailScreen(
                    onBack = { navController.popBackStack() },
                    onEdit = { id -> navController.navigate(Routes.medicationEdit(id)) },
                )
            }
            composable(
                route = Routes.MEDICATION_EDIT,
                arguments = listOf(
                    navArgument(MedicationEditViewModel.ARG_ID) {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                ),
            ) {
                MedicationEditScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }
        }
    }
}
