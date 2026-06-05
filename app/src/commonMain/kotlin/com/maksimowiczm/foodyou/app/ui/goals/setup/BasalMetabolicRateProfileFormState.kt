package com.maksimowiczm.foodyou.app.ui.goals.setup

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.maksimowiczm.foodyou.app.ui.common.form.FormField
import com.maksimowiczm.foodyou.app.ui.common.form.nullableDoubleParser
import com.maksimowiczm.foodyou.app.ui.common.form.rememberFormField
import com.maksimowiczm.foodyou.common.compose.utility.formatClipZeros
import com.maksimowiczm.foodyou.goals.domain.entity.BasalMetabolicRateProfile
import com.maksimowiczm.foodyou.goals.domain.entity.BiologicalSex
import kotlinx.datetime.LocalDate

internal class BasalMetabolicRateProfileFormState(
    val weightKg: FormField<Double?, DailyGoalsFormError>,
    val heightCm: FormField<Double?, DailyGoalsFormError>,
    birthDateEpochDayState: MutableState<Long?>,
    sexState: MutableState<BiologicalSex?>,
    isModifiedState: State<Boolean>,
) {
    val isValid: Boolean by derivedStateOf { weightKg.error == null && heightCm.error == null }

    val isModified: Boolean by isModifiedState

    var birthDateEpochDay by birthDateEpochDayState

    var birthDate: LocalDate?
        get() = birthDateEpochDay?.let(LocalDate::fromEpochDays)
        set(value) {
            birthDateEpochDay = value?.toEpochDays()
        }

    var sex by sexState

    fun intoProfile(): BasalMetabolicRateProfile =
        BasalMetabolicRateProfile(
            weightKg = weightKg.value,
            heightCm = heightCm.value,
            birthDate = birthDate,
            sex = sex,
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
    val birthDateEpochDayState = rememberSaveableProfileState(profile.birthDate?.toEpochDays())
    val sexState = rememberSaveableProfileState(profile.sex)

    val isModifiedState = remember {
        derivedStateOf {
            weightKg.value != profile.weightKg ||
                heightCm.value != profile.heightCm ||
                birthDateEpochDayState.value != profile.birthDate?.toEpochDays() ||
                sexState.value != profile.sex
        }
    }

    return remember(weightKg, heightCm, birthDateEpochDayState, sexState, isModifiedState) {
        BasalMetabolicRateProfileFormState(
            weightKg = weightKg,
            heightCm = heightCm,
            birthDateEpochDayState = birthDateEpochDayState,
            sexState = sexState,
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
