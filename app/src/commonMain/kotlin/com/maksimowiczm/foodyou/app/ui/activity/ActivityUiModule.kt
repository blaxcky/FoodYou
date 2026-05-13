package com.maksimowiczm.foodyou.app.ui.activity

import com.maksimowiczm.foodyou.common.infrastructure.koin.userPreferencesRepository
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel

fun Module.activityUi() {
    viewModel { ManualActivityViewModel(repository = get()) }
    viewModel {
        ActivitySettingsViewModel(
            settingsRepository = userPreferencesRepository(),
            healthConnectActivitySync = get(),
        )
    }
}
