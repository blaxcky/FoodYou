package com.maksimowiczm.foodyou.app.ui.food.diary.component

import androidx.compose.runtime.*
import com.maksimowiczm.foodyou.app.ui.food.component.LabeledMeasurementSuggestion
import com.maksimowiczm.foodyou.app.ui.food.component.MeasurementPickerState
import com.maksimowiczm.foodyou.app.ui.food.component.rememberMeasurementPickerState
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.domain.measurement.MeasurementType
import com.maksimowiczm.foodyou.common.domain.measurement.type
import kotlinx.datetime.LocalDate

@Composable
fun rememberFoodMeasurementFormState(
    today: LocalDate,
    possibleDates: List<LocalDate>,
    selectedDate: LocalDate,
    meals: List<String>,
    selectedMeal: String?,
    suggestions: List<Measurement>,
    labelSuggestions: List<LabeledMeasurementSuggestion> = emptyList(),
    possibleTypes: List<MeasurementType>,
    selectedMeasurement: Measurement,
): FoodMeasurementFormState {
    val dateState =
        rememberChipsDatePickerState(
            today = today,
            initialDates = possibleDates,
            selectedDate = selectedDate,
        )
    val mealsState = rememberChipsMealPickerState(meals = meals, selectedMeal = selectedMeal)

    val measurementState =
        rememberMeasurementPickerState(
            suggestions = suggestions,
            labelSuggestions = labelSuggestions,
            possibleTypes = possibleTypes,
            selectedMeasurement = selectedMeasurement,
        )

    return remember(dateState, mealsState, measurementState) {
        FoodMeasurementFormState(dateState, mealsState, measurementState)
    }
}

@Stable
class FoodMeasurementFormState(
    val dateState: ChipsDatePickerState,
    val mealsState: ChipsMealPickerState,
    val measurementState: MeasurementPickerState,
) {
    val isValid by derivedStateOf {
        measurementState.inputField.error == null &&
            measurementState.measurement.type in measurementState.possibleTypes &&
            mealsState.selectedMeal != null
    }
}
