package com.dosetrack.app.ui.onboarding

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.dosetrack.app.R
import com.dosetrack.app.appContainer
import com.dosetrack.app.ui.components.openExactAlarmSettings
import com.dosetrack.app.ui.components.openFullScreenIntentSettings
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 4

/** Short, skippable first-launch introduction (spec §3). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val container = context.appContainer
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })

    fun finish() {
        scope.launch {
            container.settingsRepository.setOnboardingDone(true)
            onFinished()
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
                if (pagerState.currentPage < PAGE_COUNT - 1) {
                    TextButton(onClick = ::finish) { Text(stringResource(R.string.onboarding_skip)) }
                }
            }
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                when (page) {
                    0 -> OnboardingPage(
                        icon = Icons.Filled.Medication,
                        title = stringResource(R.string.onboarding_welcome_title),
                        body = stringResource(R.string.onboarding_welcome_body),
                    )
                    1 -> OnboardingPage(
                        icon = Icons.Filled.TaskAlt,
                        title = stringResource(R.string.onboarding_what_title),
                        body = stringResource(R.string.onboarding_what_body),
                    )
                    2 -> OnboardingPage(
                        icon = Icons.Filled.NotificationsActive,
                        title = stringResource(R.string.onboarding_notifications_title),
                        body = stringResource(R.string.onboarding_notifications_body),
                    ) { NotificationPermissionAction() }
                    else -> OnboardingPage(
                        icon = Icons.Filled.AlarmOn,
                        title = stringResource(R.string.onboarding_reliable_title),
                        body = stringResource(R.string.onboarding_reliable_body),
                    ) { ReliabilityActions() }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PageDots(current = pagerState.currentPage, count = PAGE_COUNT, modifier = Modifier.weight(1f))
                val last = pagerState.currentPage == PAGE_COUNT - 1
                Button(
                    onClick = {
                        if (last) finish() else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    },
                    modifier = Modifier.heightIn(min = 56.dp),
                ) {
                    Text(stringResource(if (last) R.string.onboarding_get_started else R.string.onboarding_next))
                }
            }
        }
    }
}

@Composable
private fun OnboardingPage(
    icon: ImageVector,
    title: String,
    body: String,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(96.dp), tint = MaterialTheme.colorScheme.primary)
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(text = body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        action?.invoke()
    }
}

@Composable
private fun NotificationPermissionAction() {
    val helper = LocalContext.current.appContainer.notificationHelper
    var allowed by remember { mutableStateOf(helper.canPost()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = helper.canPost()
    }
    LifecycleResumeEffect(Unit) {
        allowed = helper.canPost()
        onPauseOrDispose { }
    }
    if (allowed) {
        GrantedText(stringResource(R.string.onboarding_notifications_granted))
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        FilledTonalButton(onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
            Text(stringResource(R.string.action_allow_notifications))
        }
    }
}

@Composable
private fun ReliabilityActions() {
    val context = LocalContext.current
    val container = context.appContainer
    var exactAllowed by remember { mutableStateOf(container.reminderManager.canScheduleExact()) }
    var fullScreenAllowed by remember { mutableStateOf(container.notificationHelper.canUseFullScreenIntent()) }
    LifecycleResumeEffect(Unit) {
        exactAllowed = container.reminderManager.canScheduleExact()
        fullScreenAllowed = container.notificationHelper.canUseFullScreenIntent()
        onPauseOrDispose { }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!exactAllowed) {
            FilledTonalButton(onClick = { openExactAlarmSettings(context) }) {
                Text(stringResource(R.string.onboarding_allow_exact))
            }
        }
        if (!fullScreenAllowed) {
            FilledTonalButton(onClick = { openFullScreenIntentSettings(context) }) {
                Text(stringResource(R.string.banner_fullscreen_title))
            }
        }
        OutlinedButton(
            onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
        ) {
            Text(stringResource(R.string.onboarding_battery_settings))
        }
    }
}

@Composable
private fun GrantedText(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun PageDots(current: Int, count: Int, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.onboarding_page_indicator, current + 1, count)
    Row(
        modifier = modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(count) { index ->
            Box(
                modifier = Modifier
                    .size(if (index == current) 10.dp else 8.dp)
                    .background(
                        color = if (index == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape,
                    ),
            )
        }
    }
}
