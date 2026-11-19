package com.maksimowiczm.foodyou.app.ui.goals.setup

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.maksimowiczm.foodyou.app.ui.common.form.FormField
import com.maksimowiczm.foodyou.app.ui.common.form.nullableDoubleParser
import com.maksimowiczm.foodyou.app.ui.common.form.rememberFormField
import com.maksimowiczm.foodyou.common.compose.utility.formatClipZeros
import com.maksimowiczm.foodyou.goals.domain.entity.BasalMetabolicRateProfile
import com.maksimowiczm.foodyou.goals.domain.entity.BiologicalSex
import kotlin.math.roundToInt
import kotlinx.datetime.LocalDate

internal class BasalMetabolicRateProfileFormState(
    val weightKg: FormField<Double?, DailyGoalsFormError>,
    val heightCm: FormField<Double?, DailyGoalsFormError>,
    val basalMetabolicRate: FormField<Double?, DailyGoalsFormError>,
    val activityLevel: FormField<Double?, DailyGoalsFormError>,
    birthDateEpochDayState: MutableState<Long?>,
    sexState: MutableState<BiologicalSex?>,
    calculateEnergyGoalState: MutableState<Boolean>,
    isModifiedState: State<Boolean>,
) {
    val isBodyDataValid: Boolean by derivedStateOf {
        weightKg.error == null && heightCm.error == null
    }

    val isBasalMetabolicRateValid: Boolean by derivedStateOf {
        basalMetabolicRate.error == null &&
            // A calculated energy goal needs a basal metabolic rate to calculate from.
            !(calculateEnergyGoal && basalMetabolicRate.value == null)
    }

    val isActivityLevelValid: Boolean by derivedStateOf { activityLevel.error == null }

    val isValid: Boolean by derivedStateOf {
        isBodyDataValid && isBasalMetabolicRateValid && isActivityLevelValid
    }

    val isModified: Boolean by isModifiedState

    var birthDateEpochDay by birthDateEpochDayState

    var birthDate: LocalDate?
        get() = birthDateEpochDay?.let(LocalDate::fromEpochDays)
        set(value) {
            birthDateEpochDay = value?.toEpochDays()
        }

    var sex by sexState

    var calculateEnergyGoal by calculateEnergyGoalState

    /** The activity level the user entered, or the default when the field is empty. */
    val activityLevelPercent: Int by derivedStateOf {
        activityLevel.value?.roundToInt()
            ?: BasalMetabolicRateProfile.DEFAULT_PHYSICAL_ACTIVITY_LEVEL_PERCENT
    }

    /** Fixed basal metabolic rate × activity level, or null while either is missing or invalid. */
    val calculatedEnergyGoalKcal: Double? by derivedStateOf {
        if (basalMetabolicRate.error != null || activityLevel.error != null) {
            null
        } else {
            BasalMetabolicRateProfile.calculatedEnergyGoalKcal(
                basalMetabolicRateKcal = basalMetabolicRate.value,
                physicalActivityLevelPercent = activityLevelPercent,
            )
        }
    }

    fun setActivityLevel(percent: Int) {
        val clamped =
            percent.coerceIn(
                BasalMetabolicRateProfile.MIN_PHYSICAL_ACTIVITY_LEVEL_PERCENT,
                BasalMetabolicRateProfile.MAX_PHYSICAL_ACTIVITY_LEVEL_PERCENT,
            )
        activityLevel.textFieldState.setTextAndPlaceCursorAtEnd(clamped.toString())
    }

    fun adoptBasalMetabolicRate(kcal: Int) {
        basalMetabolicRate.textFieldState.setTextAndPlaceCursorAtEnd(kcal.toString())
    }

    fun intoProfile(): BasalMetabolicRateProfile =
        BasalMetabolicRateProfile(
            weightKg = weightKg.value,
            heightCm = heightCm.value,
            birthDate = birthDate,
            sex = sex,
            basalMetabolicRateKcal = basalMetabolicRate.value,
            physicalActivityLevelPercent = activityLevel.value?.roundToInt(),
            calculateEnergyGoal = calculateEnergyGoal && basalMetabolicRate.value != null,
        )
}

@Composable
internal fun rememberBasalMetabolicRateProfileFormState(
    profile: BasalMetabolicRateProfile
): BasalMetabolicRateProfileFormState {
    val weightKg =
        rememberNullablePositiveDoubleFormField(
            initialValue = profile.weightKg,
            text = profile.weightKg?.formatClipZeros().orEmpty(),
        )
    val heightCm =
        rememberNullablePositiveDoubleFormField(
            initialValue = profile.heightCm,
            text = profile.heightCm?.formatClipZeros().orEmpty(),
        )
    val basalMetabolicRate =
        rememberNullablePositiveDoubleFormField(
            initialValue = profile.basalMetabolicRateKcal,
            text = profile.basalMetabolicRateKcal?.formatClipZeros().orEmpty(),
        )
    // Existing profiles have no stored activity level; show the former fixed 105 % as the default.
    val initialActivityLevel = profile.effectivePhysicalActivityLevelPercent.toDouble()
    val activityLevel =
        rememberFormField(
            initialValue = initialActivityLevel,
            parser = nullableDoubleParser(onNotANumber = { DailyGoalsFormError.NotANumber }),
            validator = { value ->
                if (
                    value == null ||
                        value < BasalMetabolicRateProfile.MIN_PHYSICAL_ACTIVITY_LEVEL_PERCENT ||
                        value > BasalMetabolicRateProfile.MAX_PHYSICAL_ACTIVITY_LEVEL_PERCENT
                ) {
                    DailyGoalsFormError.OutOfRange
                } else {
                    null
                }
            },
            textFieldState =
                rememberTextFieldState(profile.effectivePhysicalActivityLevelPercent.toString()),
        )
    val birthDateEpochDayState = rememberSaveableProfileState(profile.birthDate?.toEpochDays())
    val sexState = rememberSaveableProfileState(profile.sex)
    val calculateEnergyGoalState = rememberSaveableProfileState(profile.calculateEnergyGoal)

    val isModifiedState = remember {
        derivedStateOf {
            weightKg.value != profile.weightKg ||
                heightCm.value != profile.heightCm ||
                basalMetabolicRate.value != profile.basalMetabolicRateKcal ||
                activityLevel.value != initialActivityLevel ||
                birthDateEpochDayState.value != profile.birthDate?.toEpochDays() ||
                sexState.value != profile.sex ||
                calculateEnergyGoalState.value != profile.calculateEnergyGoal
        }
    }

    return remember(
        weightKg,
        heightCm,
        basalMetabolicRate,
        activityLevel,
        birthDateEpochDayState,
        sexState,
        calculateEnergyGoalState,
        isModifiedState,
    ) {
        BasalMetabolicRateProfileFormState(
            weightKg = weightKg,
            heightCm = heightCm,
            basalMetabolicRate = basalMetabolicRate,
            activityLevel = activityLevel,
            birthDateEpochDayState = birthDateEpochDayState,
            sexState = sexState,
            calculateEnergyGoalState = calculateEnergyGoalState,
            isModifiedState = isModifiedState,
        )
    }
}

@Composable
private fun rememberNullablePositiveDoubleFormField(initialValue: Double?, text: String) =
    rememberFormField(
        initialValue = initialValue,
        parser = nullableDoubleParser(onNotANumber = { DailyGoalsFormError.NotANumber }),
        validator = { value ->
            if (value != null && value <= 0) DailyGoalsFormError.Negative else null
        },
        textFieldState = rememberTextFieldState(text),
    )

@Composable
private fun <T> rememberSaveableProfileState(value: T) =
    rememberSaveable { mutableStateOf(value) }
