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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dosetrack.app.R
import com.dosetrack.app.ui.screens.PlaceholderScreen

object Routes {
    const val TODAY = "today"
    const val MEDICATIONS = "medications"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
}

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
fun DoseTrackNavHost() {
    val navController = rememberNavController()
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
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
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
            startDestination = Routes.TODAY,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.TODAY) { PlaceholderScreen(stringResource(R.string.nav_today)) }
            composable(Routes.MEDICATIONS) { PlaceholderScreen(stringResource(R.string.nav_medications)) }
            composable(Routes.HISTORY) { PlaceholderScreen(stringResource(R.string.nav_history)) }
            composable(Routes.SETTINGS) { PlaceholderScreen(stringResource(R.string.nav_settings)) }
        }
    }
}
