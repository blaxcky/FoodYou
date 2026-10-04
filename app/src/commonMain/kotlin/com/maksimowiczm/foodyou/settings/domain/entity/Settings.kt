package com.maksimowiczm.foodyou.settings.domain.entity

import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferences
import kotlinx.datetime.LocalDate

data class Settings(
    val lastRememberedVersion: String?,
    val hidePreviewDialog: Boolean,
    val showTranslationWarning: Boolean,
    val nutrientsOrder: List<NutrientsOrder>,
    val secureScreen: Boolean,
    val homeCardOrder: List<HomeCard>,
    val expandGoalCard: Boolean,
    val supplementalGoalsEnabled: Boolean = true,
    val goalCardModeSwitchingEnabled: Boolean = true,
    val goalDisplayMode: GoalDisplayMode,
    val dietEnergyDeficitKcal: Double?,
    val dietEnergyDeficitOverride: DietEnergyDeficitOverride? = null,
    val onboardingFinished: Boolean,
    val energyFormat: EnergyFormat,
    val appLaunchInfo: AppLaunchInfo,
    val stepsCaloriesPerStepKcal: Double?,
    val healthConnectStepsEnabled: Boolean,
    val healthConnectWeightEnabled: Boolean = false,
    val healthConnectStepsLastSyncedEpochSeconds: Long?,
    val healthConnectStepsLastFullSyncEpochDay: Long? = null,
    val healthConnectStepsLastFullSyncTimeZoneId: String? = null,
    val healthConnectWeightLastSyncedEpochSeconds: Long? = null,
    val healthConnectWeightBackfillBeforeEpochSeconds: Long? = null,
    val healthConnectWeightBackfillComplete: Boolean = false,
    val homeSyncHealthConnectEnabled: Boolean = true,
    val homeSyncFddbDiaryEnabled: Boolean = false,
    val fddbDiarySyncLastImported: Int? = null,
    val fddbDiarySyncLastSkipped: Int? = null,
    val fddbDiarySyncLastFailed: Int? = null,
    val fddbDiarySyncLastErrorMessage: String? = null,
    val fddbDiarySyncLastAttemptEpochSeconds: Long? = null,
    val fddbProductSyncLastAttemptEpochSeconds: Long? = null,
    val fddbProductSyncMode: FddbProductSyncMode = FddbProductSyncMode.WithManualFddbSync,
    val fddbProductSyncManualFrequency: FddbProductSyncManualFrequency =
        FddbProductSyncManualFrequency.EveryThirdSync,
    val fddbProductSyncManualTriggerCount: Int = 0,
    val pendingProductPhotoQuality: PendingProductPhotoQuality = PendingProductPhotoQuality.Balanced,
    val crosstrainerCalorieDiscountPercent: Double = 0.0,
    val todayEnergyGoalAdjustment: TodayEnergyGoalAdjustment? = null,
    val defaultLockedDaySurplusKcal: Double = DEFAULT_LOCKED_DAY_SURPLUS_KCAL,
    val lockedDaySurpluses: List<LockedDaySurplus> = emptyList(),
    val quickCaptureAggregateSameFoods: Boolean = false,
    val foodEntryAmountPickerStyle: FoodEntryAmountPickerStyle =
        FoodEntryAmountPickerStyle.PortionList,
    val weeklyDetailsStyle: WeeklyDetailsStyle = WeeklyDetailsStyle.DifferenceBars,
) : UserPreferences

data class LockedDaySurplus(val date: LocalDate, val surplusKcal: Double)

const val DEFAULT_LOCKED_DAY_SURPLUS_KCAL = 500.0

fun Settings.lockedDaySurplus(date: LocalDate): LockedDaySurplus? =
    lockedDaySurpluses.firstOrNull { it.date == date }

data class TodayEnergyGoalAdjustment(
    val date: LocalDate,
    val reductionKcal: Double,
)

fun Settings.effectiveTodayEnergyGoalAdjustment(
    selectedDate: LocalDate,
    today: LocalDate,
): TodayEnergyGoalAdjustment? =
    todayEnergyGoalAdjustment?.takeIf {
        selectedDate == today && it.date == today && it.reductionKcal > 0.0
    }

data class DietEnergyDeficitOverride(
    val energyDeficitKcal: Double,
    val startDate: LocalDate,
    val endDate: LocalDate,
)

fun Settings.effectiveDietEnergyDeficitKcal(date: LocalDate): Double? =
    dietEnergyDeficitOverride
        ?.takeIf { date >= it.startDate && date <= it.endDate }
        ?.energyDeficitKcal
        ?.takeIf { it > 0.0 }
        ?: dietEnergyDeficitKcal?.takeIf { it > 0.0 }

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

/** Layout of the amount input on the food entry screen. */
enum class FoodEntryAmountPickerStyle {
    /** Amount field with a unit dropdown and suggestion chips below the save button. */
    Classic,

    /** Amount field followed by a selectable list of portions and units. */
    PortionList,
}

/** How the days are listed in the expanded weekly goals details. */
enum class WeeklyDetailsStyle {
    /** Goal, consumed, difference and percent columns per day. */
    Table,

    /** One bar per day that grows left of a center line below the goal and right above it. */
    DifferenceBars,
}

enum class PendingProductPhotoQuality {
    Fast,
    Balanced,
    High,
}

enum class FddbProductSyncMode {
    WithManualFddbSync,
    Disabled,
}

enum class FddbProductSyncManualFrequency {
    EverySync,
    EveryThirdSync,
}
