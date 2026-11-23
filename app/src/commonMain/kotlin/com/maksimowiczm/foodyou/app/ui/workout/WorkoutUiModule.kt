package com.maksimowiczm.foodyou.app.ui.workout

import com.maksimowiczm.foodyou.common.infrastructure.koin.userPreferencesRepository
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel

fun Module.workoutUi() {
    viewModel {
        TrainingViewModel(
            repository = get(),
            settingsRepository = userPreferencesRepository(),
            weightRepository = get(),
            healthConnectActivitySync = get(),
            dateProvider = get(),
        )
    }
    viewModel {
        WorkoutExerciseEditorViewModel(
            repository = get(),
            dateProvider = get(),
            weightRepository = get(),
        )
    }
    viewModel {
        WorkoutGoalsViewModel(repository = get(), settingsRepository = userPreferencesRepository())
    }
}
