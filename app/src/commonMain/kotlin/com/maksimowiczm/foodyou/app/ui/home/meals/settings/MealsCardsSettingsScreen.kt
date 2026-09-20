package com.maksimowiczm.foodyou.app.ui.home.meals.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import com.maksimowiczm.foodyou.app.ui.home.meals.card.iconPainter
import com.maksimowiczm.foodyou.app.ui.home.meals.card.labelResource
import com.maksimowiczm.foodyou.common.compose.extension.add
import com.maksimowiczm.foodyou.common.compose.extension.performToggle
import com.maksimowiczm.foodyou.fooddiary.domain.entity.MealCardMacro
import com.maksimowiczm.foodyou.fooddiary.domain.entity.MealCardMacroStyle
import com.maksimowiczm.foodyou.fooddiary.domain.entity.MealsCardsLayout
import foodyou.app.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MealsCardsSettingsScreen(
    onBack: () -> Unit,
    onMealSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: MealsCardsSettingsViewModel = koinViewModel()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()

    val layout = preferences.layout
    val useTimeBasedSorting = preferences.useTimeBasedSorting
    val ignoreAllDayMeals = preferences.ignoreAllDayMeals
    val displayedMacros = preferences.displayedMacros
    val showMacrosInFoodEntries = preferences.showMacrosInFoodEntries
    val macroStyle = preferences.macroStyle

    MealCardSettings(
        layout = layout,
        onLayoutChange = { viewModel.updatePreferences(preferences.copy(layout = it)) },
        useTimeBasedSorting = useTimeBasedSorting,
        toggleTimeBased = {
            viewModel.updatePreferences(preferences.copy(useTimeBasedSorting = it))
        },
        ignoreAllDayMeals = ignoreAllDayMeals,
        toggleIgnoreAllDayMeals = {
            viewModel.updatePreferences(preferences.copy(ignoreAllDayMeals = it))
        },
        displayedMacros = displayedMacros,
        onMacroDisplayChange = viewModel::updateDisplayedMacro,
        showMacrosInFoodEntries = showMacrosInFoodEntries,
        toggleShowMacrosInFoodEntries = viewModel::updateShowMacrosInFoodEntries,
        macroStyle = macroStyle,
        onMacroStyleChange = viewModel::updateMacroStyle,
        onMealsSettings = onMealSettings,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
internal fun MealCardSettings(
    layout: MealsCardsLayout,
    onLayoutChange: (MealsCardsLayout) -> Unit,
    useTimeBasedSorting: Boolean,
    toggleTimeBased: (Boolean) -> Unit,
    ignoreAllDayMeals: Boolean,
    toggleIgnoreAllDayMeals: (Boolean) -> Unit,
    displayedMacros: Set<MealCardMacro>,
    onMacroDisplayChange: (MealCardMacro, Boolean) -> Unit,
    showMacrosInFoodEntries: Boolean,
    toggleShowMacrosInFoodEntries: (Boolean) -> Unit,
    macroStyle: MealCardMacroStyle,
    onMacroStyleChange: (MealCardMacroStyle) -> Unit,
    onMealsSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hapticFeedback = LocalHapticFeedback.current

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(Res.string.headline_meals)) },
                navigationIcon = { ArrowBackIconButton(onBack) },
                scrollBehavior = scrollBehavior,
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = paddingValues.add(vertical = 8.dp),
        ) {
            item {
                LayoutPicker(
                    layout = layout,
                    onLayoutChange = {
                        if (layout != it) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.ToggleOn)
                            onLayoutChange(it)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
            }

            macroDisplaySettings(
                displayedMacros = displayedMacros,
                toggleMacro = { macro, enabled ->
                    hapticFeedback.performToggle(enabled)
                    onMacroDisplayChange(macro, enabled)
                },
                showMacrosInFoodEntries = showMacrosInFoodEntries,
                toggleShowMacrosInFoodEntries = {
                    hapticFeedback.performToggle(it)
                    toggleShowMacrosInFoodEntries(it)
                },
                macroStyle = macroStyle,
                onMacroStyleChange = {
                    if (macroStyle != it) {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.ToggleOn)
                        onMacroStyleChange(it)
                    }
                },
            )

            item { HorizontalDivider() }

            advancedLayoutSettings(
                useTimeBasedSorting = useTimeBasedSorting,
                toggleTimeBased = {
                    hapticFeedback.performToggle(it)
                    toggleTimeBased(it)
                },
                ignoreAllDayMeals = ignoreAllDayMeals,
                toggleIgnoreAllDayMeals = {
                    hapticFeedback.performToggle(it)
                    toggleIgnoreAllDayMeals(it)
                },
            )

            item { HorizontalDivider() }

            item {
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.headline_meals_settings)) },
                    modifier = Modifier.clickable { onMealsSettings() },
                )
            }
        }
    }
}

private fun LazyListScope.macroDisplaySettings(
    displayedMacros: Set<MealCardMacro>,
    toggleMacro: (MealCardMacro, Boolean) -> Unit,
    showMacrosInFoodEntries: Boolean,
    toggleShowMacrosInFoodEntries: (Boolean) -> Unit,
    macroStyle: MealCardMacroStyle,
    onMacroStyleChange: (MealCardMacroStyle) -> Unit,
) {
    item {
        Text(
            text = stringResource(Res.string.headline_displayed_macronutrients),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }

    MealCardMacro.entries.forEach { macro ->
        item(key = "meal-card-macro-${macro.name}") {
            val checked = macro in displayedMacros
            ListItem(
                headlineContent = { Text(stringResource(macro.labelResource)) },
                trailingContent = { Switch(checked = checked, onCheckedChange = null) },
                modifier =
                    Modifier.toggleable(
                        value = checked,
                        role = Role.Switch,
                        onValueChange = { toggleMacro(macro, it) },
                    ),
            )
        }
    }

    item {
        val enabled = displayedMacros.isNotEmpty()
        val contentColor =
            if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline

        ListItem(
            headlineContent = {
                Text(stringResource(Res.string.action_show_macros_in_food_entries))
            },
            supportingContent = {
                Text(stringResource(Res.string.description_show_macros_in_food_entries))
            },
            trailingContent = {
                Switch(
                    checked = showMacrosInFoodEntries,
                    onCheckedChange = null,
                    enabled = enabled,
                )
            },
            colors =
                ListItemDefaults.colors(
                    headlineColor = contentColor,
                    supportingColor = contentColor,
                ),
            modifier =
                Modifier.toggleable(
                    value = showMacrosInFoodEntries,
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange = toggleShowMacrosInFoodEntries,
                ),
        )
    }

    item {
        val enabled = displayedMacros.isNotEmpty()
        val contentColor =
            if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline

        ListItem(
            headlineContent = {
                Text(stringResource(Res.string.headline_macronutrient_label_style))
            },
            supportingContent = {
                MacroStylePicker(
                    macroStyle = macroStyle,
                    onMacroStyleChange = onMacroStyleChange,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            },
            colors = ListItemDefaults.colors(headlineColor = contentColor),
        )
    }
}

@Composable
private fun MacroStylePicker(
    macroStyle: MealCardMacroStyle,
    onMacroStyleChange: (MealCardMacroStyle) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val styles = MealCardMacroStyle.entries

    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        styles.forEachIndexed { index, style ->
            SegmentedButton(
                selected = style == macroStyle,
                onClick = { onMacroStyleChange(style) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = styles.size),
                enabled = enabled,
            ) {
                when (style) {
                    MealCardMacroStyle.Letters ->
                        Text(stringResource(Res.string.action_macro_style_letters))

                    MealCardMacroStyle.Icons ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(Res.string.action_macro_style_icons))
                            Spacer(Modifier.width(8.dp))
                            MealCardMacro.entries.forEach { macro ->
                                Icon(
                                    painter = macro.iconPainter(),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                }
            }
        }
    }
}

private fun LazyListScope.advancedLayoutSettings(
    useTimeBasedSorting: Boolean,
    toggleTimeBased: (Boolean) -> Unit,
    ignoreAllDayMeals: Boolean,
    toggleIgnoreAllDayMeals: (Boolean) -> Unit,
) {
    item {
        Text(
            text = stringResource(Res.string.headline_time_based_ordering),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }

    item {
        ListItem(
            headlineContent = { Text(stringResource(Res.string.action_use_time_based_ordering)) },
            modifier = Modifier.clickable { toggleTimeBased(!useTimeBasedSorting) },
            supportingContent = {
                Text(stringResource(Res.string.description_time_based_meals_sorting))
            },
            trailingContent = {
                Switch(checked = useTimeBasedSorting, onCheckedChange = toggleTimeBased)
            },
        )
    }

    item {
        val contentColor =
            if (useTimeBasedSorting) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.outline
            }

        ListItem(
            headlineContent = { Text(stringResource(Res.string.action_ignore_all_day_meals)) },
            modifier =
                Modifier.clickable(enabled = useTimeBasedSorting) {
                    toggleIgnoreAllDayMeals(!ignoreAllDayMeals)
                },
            supportingContent = {
                Text(stringResource(Res.string.description_action_ignore_all_day_meals))
            },
            trailingContent = {
                Switch(
                    checked = ignoreAllDayMeals,
                    onCheckedChange = toggleIgnoreAllDayMeals,
                    enabled = useTimeBasedSorting,
                )
            },
            colors =
                ListItemDefaults.colors(
                    headlineColor = contentColor,
                    supportingColor = contentColor,
                ),
        )
    }
}
