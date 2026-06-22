package com.maksimowiczm.foodyou.settings.domain.entity

import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferences

data class Settings(
    val lastRememberedVersion: String?,
    val hidePreviewDialog: Boolean,
    val showTranslationWarning: Boolean,
    val nutrientsOrder: List<NutrientsOrder>,
    val secureScreen: Boolean,
    val homeCardOrder: List<HomeCard>,
    val expandGoalCard: Boolean,
    val goalDisplayMode: GoalDisplayMode,
    val dietEnergyDeficitKcal: Double?,
    val onboardingFinished: Boolean,
    val energyFormat: EnergyFormat,
    val appLaunchInfo: AppLaunchInfo,
    val stepsCaloriesPerStepKcal: Double?,
    val healthConnectStepsEnabled: Boolean,
    val healthConnectWeightEnabled: Boolean = false,
    val healthConnectStepsLastSyncedEpochSeconds: Long?,
    val healthConnectWeightLastSyncedEpochSeconds: Long? = null,
    val homeSyncHealthConnectEnabled: Boolean = true,
    val homeSyncFddbDiaryEnabled: Boolean = false,
    val fddbDiarySyncLastImported: Int? = null,
    val fddbDiarySyncLastSkipped: Int? = null,
    val fddbDiarySyncLastFailed: Int? = null,
    val fddbDiarySyncLastErrorMessage: String? = null,
    val fddbDiarySyncLastAttemptEpochSeconds: Long? = null,
    val fddbProductSyncManualCount: Int = 0,
    val pendingProductPhotoQuality: PendingProductPhotoQuality = PendingProductPhotoQuality.Balanced,
) : UserPreferences

data class FddbDiarySyncStatus(
    val imported: Int,
    val skipped: Int,
    val failed: Int,
    val errorMessage: String?,
    val attemptEpochSeconds: Long?,
) {
    val hasFailure: Boolean
        get() = failed > 0 || errorMessage != null
}

fun Settings.fddbDiarySyncStatus(): FddbDiarySyncStatus? {
    if (
        fddbDiarySyncLastImported == null &&
            fddbDiarySyncLastSkipped == null &&
            fddbDiarySyncLastFailed == null &&
            fddbDiarySyncLastErrorMessage == null &&
            fddbDiarySyncLastAttemptEpochSeconds == null
    ) {
        return null
    }

    return FddbDiarySyncStatus(
        imported = fddbDiarySyncLastImported ?: 0,
        skipped = fddbDiarySyncLastSkipped ?: 0,
        failed = fddbDiarySyncLastFailed ?: 0,
        errorMessage = fddbDiarySyncLastErrorMessage,
        attemptEpochSeconds = fddbDiarySyncLastAttemptEpochSeconds,
    )
}

enum class GoalDisplayMode {
    Normal,
    Optimized,
    Diet,
}

enum class PendingProductPhotoQuality {
    Fast,
    Balanced,
    High,
}
