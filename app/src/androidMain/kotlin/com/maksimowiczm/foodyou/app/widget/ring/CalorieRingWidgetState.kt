package com.maksimowiczm.foodyou.app.widget.ring

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.maksimowiczm.foodyou.app.widget.CalorieWidgetModel
import kotlinx.datetime.LocalDate

/** Stores the widget model in the Glance widget state so `update()` always renders fresh data. */
internal object CalorieRingWidgetState {
    val date = stringPreferencesKey("date")
    val countedSteps = longPreferencesKey("counted_steps")
    val eatenKcal = intPreferencesKey("eaten_kcal")
    val burnedKcal = intPreferencesKey("burned_kcal")
    val netKcal = intPreferencesKey("net_kcal")
    val normalGoalKcal = intPreferencesKey("normal_goal_kcal")
    val normalLeftKcal = intPreferencesKey("normal_left_kcal")
    val optimizedGoalKcal = intPreferencesKey("optimized_goal_kcal")
    val optimizedLeftKcal = intPreferencesKey("optimized_left_kcal")
    val dietGoalKcal = intPreferencesKey("diet_goal_kcal")
    val dietLeftKcal = intPreferencesKey("diet_left_kcal")
}

internal fun MutablePreferences.write(model: CalorieWidgetModel) {
    this[CalorieRingWidgetState.date] = model.date.toString()
    this[CalorieRingWidgetState.countedSteps] = model.countedSteps
    this[CalorieRingWidgetState.eatenKcal] = model.eatenKcal
    this[CalorieRingWidgetState.burnedKcal] = model.burnedKcal
    this[CalorieRingWidgetState.netKcal] = model.netKcal
    this[CalorieRingWidgetState.normalGoalKcal] = model.normalGoalKcal
    this[CalorieRingWidgetState.normalLeftKcal] = model.normalLeftKcal
    setOrRemove(CalorieRingWidgetState.optimizedGoalKcal, model.optimizedGoalKcal)
    setOrRemove(CalorieRingWidgetState.optimizedLeftKcal, model.optimizedLeftKcal)
    setOrRemove(CalorieRingWidgetState.dietGoalKcal, model.dietGoalKcal)
    setOrRemove(CalorieRingWidgetState.dietLeftKcal, model.dietLeftKcal)
}

private fun MutablePreferences.setOrRemove(key: Preferences.Key<Int>, value: Int?) {
    if (value == null) remove(key) else this[key] = value
}

internal fun Preferences.toCalorieWidgetModel(): CalorieWidgetModel? {
    val date = this[CalorieRingWidgetState.date]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: return null
    return CalorieWidgetModel(
        date = date,
        countedSteps = this[CalorieRingWidgetState.countedSteps] ?: 0L,
        eatenKcal = this[CalorieRingWidgetState.eatenKcal] ?: 0,
        burnedKcal = this[CalorieRingWidgetState.burnedKcal] ?: 0,
        netKcal = this[CalorieRingWidgetState.netKcal] ?: 0,
        normalGoalKcal = this[CalorieRingWidgetState.normalGoalKcal] ?: 0,
        normalLeftKcal = this[CalorieRingWidgetState.normalLeftKcal] ?: 0,
        optimizedGoalKcal = this[CalorieRingWidgetState.optimizedGoalKcal],
        optimizedLeftKcal = this[CalorieRingWidgetState.optimizedLeftKcal],
        dietGoalKcal = this[CalorieRingWidgetState.dietGoalKcal],
        dietLeftKcal = this[CalorieRingWidgetState.dietLeftKcal],
    )
}
