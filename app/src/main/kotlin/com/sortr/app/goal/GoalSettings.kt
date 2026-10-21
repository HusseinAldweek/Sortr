package com.sortr.app.goal

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sortr.app.R
import com.sortr.app.ui.common.formatCount
import com.sortr.app.ui.common.formatTimeOfDay
import com.sortr.app.ui.design.GroupedSection
import com.sortr.app.ui.design.ListRow
import com.sortr.app.ui.theme.palette
import com.sortr.domain.model.DailyGoal
import com.sortr.domain.repository.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GoalSettingsState(val goal: Int = 0, val reminderMinutes: Int? = null)

@HiltViewModel
class GoalSettingsViewModel @Inject constructor(
    private val settings: AppSettings,
    private val scheduler: GoalReminderScheduler,
) : ViewModel() {

    val state: StateFlow<GoalSettingsState> = combine(settings.dailyGoal, settings.goalReminderMinutes, ::GoalSettingsState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalSettingsState())

    fun setGoal(goal: Int) {
        viewModelScope.launch {
            settings.setDailyGoal(goal)
            reschedule()
        }
    }

    /** Minutes after midnight, or null to turn the reminder off. */
    fun setReminder(minutes: Int?) {
        viewModelScope.launch {
            settings.setGoalReminderMinutes(minutes)
            reschedule()
        }
    }

    private suspend fun reschedule() = scheduler.apply(settings.dailyGoal.first(), settings.goalReminderMinutes.first())
}

/** Settings rows for the daily goal and its reminder. */
@Composable
fun GoalSettingsSection(snackbarHostState: SnackbarHostState, viewModel: GoalSettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val palette = MaterialTheme.palette
    var pickingGoal by rememberSaveable { mutableStateOf(false) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            viewModel.setReminder(DailyGoal.DEFAULT_REMINDER_MINUTES)
        } else {
            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.settings_reminder_needs_permission)) }
        }
    }
    fun setReminderOn(on: Boolean) {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        when {
            !on -> viewModel.setReminder(null)
            needsPermission -> permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> viewModel.setReminder(DailyGoal.DEFAULT_REMINDER_MINUTES)
        }
    }

    GroupedSection(header = stringResource(R.string.settings_section_goal), footer = stringResource(R.string.settings_goal_footer)) {
        row {
            ListRow(
                title = stringResource(R.string.goal_title),
                icon = Icons.Rounded.Flag,
                iconColor = palette.orange,
                value = goalLabel(state.goal),
                onClick = { pickingGoal = true },
            )
        }
        if (state.goal > 0) {
            val reminderOn = state.reminderMinutes != null
            row {
                ListRow(
                    title = stringResource(R.string.settings_reminder),
                    subtitle = stringResource(R.string.settings_reminder_body),
                    icon = Icons.Rounded.Notifications,
                    iconColor = palette.red,
                    showChevron = false,
                    onClick = { setReminderOn(!reminderOn) },
                    trailing = {
                        Switch(
                            checked = reminderOn,
                            onCheckedChange = ::setReminderOn,
                            colors = SwitchDefaults.colors(checkedTrackColor = palette.green, checkedBorderColor = palette.green),
                        )
                    },
                )
            }
            state.reminderMinutes?.let { minutes ->
                row {
                    ListRow(
                        title = stringResource(R.string.settings_reminder_time),
                        icon = Icons.Rounded.Schedule,
                        iconColor = palette.indigo,
                        value = formatTimeOfDay(context, minutes),
                        onClick = { pickingTime = true },
                    )
                }
            }
        }
    }

    if (pickingGoal) {
        GoalPickerDialog(
            current = state.goal,
            onPick = { goal ->
                pickingGoal = false
                viewModel.setGoal(goal)
            },
            onDismiss = { pickingGoal = false },
        )
    }
    if (pickingTime) {
        ReminderTimeDialog(
            minutes = state.reminderMinutes ?: DailyGoal.DEFAULT_REMINDER_MINUTES,
            onPick = { minutes ->
                pickingTime = false
                viewModel.setReminder(minutes)
            },
            onDismiss = { pickingTime = false },
        )
    }
}

@Composable
private fun goalLabel(goal: Int): String =
    if (goal > 0) stringResource(R.string.settings_goal_value, formatCount(goal)) else stringResource(R.string.settings_goal_off)

@Composable
private fun GoalPickerDialog(current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_goal_dialog_title)) },
        text = {
            Column(Modifier.selectableGroup()) {
                (listOf(0) + DailyGoal.CHOICES).forEach { goal ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.RadioButton) { onPick(goal) }
                            .padding(vertical = 4.dp),
                    ) {
                        RadioButton(selected = goal == current, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(goalLabel(goal), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(minutes: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val pickerState = rememberTimePickerState(
        initialHour = minutes / 60,
        initialMinute = minutes % 60,
        is24Hour = DateFormat.is24HourFormat(context),
    )
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.settings_reminder_time_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                )
                TimePicker(state = pickerState)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                    TextButton(onClick = { onPick(pickerState.hour * 60 + pickerState.minute) }) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            }
        }
    }
}
