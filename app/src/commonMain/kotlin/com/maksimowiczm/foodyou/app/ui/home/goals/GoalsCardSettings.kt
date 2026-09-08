package com.maksimowiczm.foodyou.app.ui.home.goals

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.settings.domain.entity.Settings
import foodyou.app.generated.resources.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.core.qualifier.named

@Composable
internal fun GoalsCardSettings(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository: UserPreferencesRepository<Settings> =
        koinInject(named(Settings::class.qualifiedName!!))
    val settings by repository.observe().collectAsStateWithLifecycle(null)
    val scope = rememberCoroutineScope()
    settings?.let { current ->
        GoalsCardSettingsContent(
            onBack = onBack,
            goalCardModeSwitchingEnabled = current.goalCardModeSwitchingEnabled,
            supplementalGoalsEnabled = current.supplementalGoalsEnabled,
            onModeSwitchingChange = { enabled ->
                scope.launch { repository.update { copy(goalCardModeSwitchingEnabled = enabled) } }
            },
            onSupplementalGoalsChange = { enabled ->
                scope.launch { repository.update { copy(supplementalGoalsEnabled = enabled) } }
            },
            modifier = modifier,
        )
    }
}

@Composable
internal fun GoalsCardSettingsContent(
    onBack: () -> Unit,
    goalCardModeSwitchingEnabled: Boolean,
    supplementalGoalsEnabled: Boolean,
    onModeSwitchingChange: (Boolean) -> Unit,
    onSupplementalGoalsChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var previewMode by remember { mutableStateOf(GoalDisplayMode.Normal) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier,
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(Res.string.headline_goals_card)) },
                navigationIcon = { ArrowBackIconButton(onBack) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = paddingValues,
        ) {
            stickyHeader {
                GoalsCard(
                    energy = 1600,
                    burnedEnergy = 250,
                    netEnergy = 1350,
                    energyGoal = 2000,
                    goalCardModeSwitchingEnabled = goalCardModeSwitchingEnabled,
                    supplementalGoalsEnabled = supplementalGoalsEnabled,
                    goalDisplayMode = previewMode,
                    onSelectGoalDisplayMode = { previewMode = it },
                    goalDisplaySummaries = listOf(
                        GoalDisplaySummaryModel(GoalDisplayMode.Normal, 2000, true),
                        GoalDisplaySummaryModel(GoalDisplayMode.Optimized, 2200, true),
                        GoalDisplaySummaryModel(GoalDisplayMode.Diet, 1700, true),
                    ),
                    proteins = 50,
                    proteinsGoal = 75,
                    carbohydrates = 200,
                    carbohydratesGoal = 300,
                    fats = 70,
                    fatsGoal = 90,
                    onClick = {},
                    onLongClick = {},
                    modifier = Modifier.padding(16.dp),
                )
            }

            item { HorizontalDivider() }
            item {
                GoalCardSettingSwitch(
                    title = stringResource(Res.string.goal_card_mode_switching_title),
                    description = stringResource(Res.string.goal_card_mode_switching_description),
                    checked = goalCardModeSwitchingEnabled,
                    onCheckedChange = onModeSwitchingChange,
                )
            }
            item {
                GoalCardSettingSwitch(
                    title = stringResource(Res.string.supplemental_goals_title),
                    description = stringResource(Res.string.supplemental_goals_description),
                    checked = supplementalGoalsEnabled,
                    onCheckedChange = onSupplementalGoalsChange,
                )
            }
        }
    }
}

@Composable
private fun GoalCardSettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(
            value = checked,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        ),
    )
}
