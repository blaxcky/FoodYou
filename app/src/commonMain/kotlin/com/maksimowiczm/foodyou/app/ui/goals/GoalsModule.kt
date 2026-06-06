package com.maksimowiczm.foodyou.app.ui.goals

import com.maksimowiczm.foodyou.app.ui.goals.master.GoalsViewModel
import com.maksimowiczm.foodyou.app.ui.goals.setup.DailyGoalsViewModel
import com.maksimowiczm.foodyou.common.infrastructure.koin.userPreferencesRepository
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf

fun Module.goals() {
    viewModelOf(::GoalsViewModel)
    viewModel {
        DailyGoalsViewModel(
            goalsRepository = get(),
            basalMetabolicRateProfileRepository = get(),
            settingsRepository = userPreferencesRepository(),
        )
    }
}
