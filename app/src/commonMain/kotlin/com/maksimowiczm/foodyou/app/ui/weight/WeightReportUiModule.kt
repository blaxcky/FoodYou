package com.maksimowiczm.foodyou.app.ui.weight

import com.maksimowiczm.foodyou.common.infrastructure.koin.userPreferencesRepository
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel

fun Module.weightReport() {
    viewModel {
        WeightReportViewModel(
            repository = get(),
            basalMetabolicRateProfileRepository = get(),
            healthConnectWeightSync = get(),
            settingsRepository = userPreferencesRepository(),
        )
    }
}
