package com.maksimowiczm.foodyou.settings.infrastructure

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.maksimowiczm.foodyou.common.infrastructure.datastore.AbstractDataStoreUserPreferencesRepository
import com.maksimowiczm.foodyou.common.infrastructure.datastore.set
import com.maksimowiczm.foodyou.settings.domain.entity.AppLaunchInfo
import com.maksimowiczm.foodyou.settings.domain.entity.DietEnergyDeficitOverride
import com.maksimowiczm.foodyou.settings.domain.entity.DEFAULT_LOCKED_DAY_SURPLUS_KCAL
import com.maksimowiczm.foodyou.settings.domain.entity.EnergyFormat
import com.maksimowiczm.foodyou.settings.domain.entity.GoalDisplayMode
import com.maksimowiczm.foodyou.settings.domain.entity.HomeCard
import com.maksimowiczm.foodyou.settings.domain.entity.LockedDaySurplus
import com.maksimowiczm.foodyou.settings.domain.entity.NutrientsOrder
import com.maksimowiczm.foodyou.settings.domain.entity.PendingProductPhotoQuality
import com.maksimowiczm.foodyou.settings.domain.entity.Settings
import com.maksimowiczm.foodyou.settings.domain.entity.TodayEnergyGoalAdjustment
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

internal class DataStoreSettingsRepository(dataStore: DataStore<Preferences>) :
    AbstractDataStoreUserPreferencesRepository<Settings>(dataStore) {
    override fun Preferences.toUserPreferences(): Settings =
        Settings(
            lastRememberedVersion = this[SettingsPreferencesKeys.lastRememberedVersion],
            hidePreviewDialog = this[SettingsPreferencesKeys.hidePreviewDialog] ?: false,
            showTranslationWarning = this[SettingsPreferencesKeys.showTranslationWarning] ?: true,
            nutrientsOrder = this.getNutrientsOrder(SettingsPreferencesKeys.nutrientsOrder),
            secureScreen = this[SettingsPreferencesKeys.secureScreen] ?: false,
            homeCardOrder = this.getHomeCardOrder(SettingsPreferencesKeys.homeCardOrder),
            expandGoalCard = this[SettingsPreferencesKeys.expandGoalCard] ?: true,
            goalCardModeSwitchingEnabled = this[SettingsPreferencesKeys.goalCardModeSwitchingEnabled] ?: true,
            supplementalGoalsEnabled = this[SettingsPreferencesKeys.supplementalGoalsEnabled] ?: true,
            goalDisplayMode = this.getGoalDisplayMode(),
            dietEnergyDeficitKcal = this[SettingsPreferencesKeys.dietEnergyDeficitKcal],
            dietEnergyDeficitOverride = this.getDietEnergyDeficitOverride(),
            onboardingFinished = this[SettingsPreferencesKeys.onboardingFinished] ?: false,
            energyFormat = this.getEnergyFormat(SettingsPreferencesKeys.energyFormat),
            appLaunchInfo = this.getAppLaunchInfo(),
            stepsCaloriesPerStepKcal = this[SettingsPreferencesKeys.stepsCaloriesPerStepKcal],
            healthConnectStepsEnabled =
                this[SettingsPreferencesKeys.healthConnectStepsEnabled] ?: false,
            healthConnectWeightEnabled =
                this[SettingsPreferencesKeys.healthConnectWeightEnabled] ?: false,
            healthConnectStepsLastSyncedEpochSeconds =
                this[SettingsPreferencesKeys.healthConnectStepsLastSyncedEpochSeconds],
            healthConnectWeightLastSyncedEpochSeconds =
                this[SettingsPreferencesKeys.healthConnectWeightLastSyncedEpochSeconds],
            homeSyncHealthConnectEnabled =
                this[SettingsPreferencesKeys.homeSyncHealthConnectEnabled] ?: true,
            homeSyncFddbDiaryEnabled =
                this[SettingsPreferencesKeys.homeSyncFddbDiaryEnabled] ?: false,
            fddbDiarySyncLastImported = this[SettingsPreferencesKeys.fddbDiarySyncLastImported],
            fddbDiarySyncLastSkipped = this[SettingsPreferencesKeys.fddbDiarySyncLastSkipped],
            fddbDiarySyncLastFailed = this[SettingsPreferencesKeys.fddbDiarySyncLastFailed],
            fddbDiarySyncLastErrorMessage =
                this[SettingsPreferencesKeys.fddbDiarySyncLastErrorMessage],
            fddbDiarySyncLastAttemptEpochSeconds =
                this[SettingsPreferencesKeys.fddbDiarySyncLastAttemptEpochSeconds],
            fddbProductSyncManualCount =
                this[SettingsPreferencesKeys.fddbProductSyncManualCount] ?: 0,
            pendingProductPhotoQuality = this.getPendingProductPhotoQuality(),
            crosstrainerCalorieDiscountPercent =
                this[SettingsPreferencesKeys.crosstrainerCalorieDiscountPercent] ?: 0.0,
            todayEnergyGoalAdjustment = this.getTodayEnergyGoalAdjustment(),
            defaultLockedDaySurplusKcal =
                this[SettingsPreferencesKeys.defaultLockedDaySurplusKcal]
                    ?.takeIf { it.isFinite() && it >= 0.0 }
                    ?: DEFAULT_LOCKED_DAY_SURPLUS_KCAL,
            lockedDaySurpluses = this.getLockedDaySurpluses(),
        )

    override fun MutablePreferences.applyUserPreferences(updated: Settings) {
        this[SettingsPreferencesKeys.lastRememberedVersion] = updated.lastRememberedVersion
        this[SettingsPreferencesKeys.hidePreviewDialog] = updated.hidePreviewDialog
        this[SettingsPreferencesKeys.showTranslationWarning] = updated.showTranslationWarning
        setNutrientsOrder(SettingsPreferencesKeys.nutrientsOrder, updated.nutrientsOrder)
        this[SettingsPreferencesKeys.secureScreen] = updated.secureScreen
        setHomeCardOrder(SettingsPreferencesKeys.homeCardOrder, updated.homeCardOrder)
        this[SettingsPreferencesKeys.expandGoalCard] = updated.expandGoalCard
        this[SettingsPreferencesKeys.goalCardModeSwitchingEnabled] = updated.goalCardModeSwitchingEnabled
        this[SettingsPreferencesKeys.supplementalGoalsEnabled] = updated.supplementalGoalsEnabled
        setGoalDisplayMode(updated.goalDisplayMode)
        setWithNull(
            SettingsPreferencesKeys.dietEnergyDeficitKcal,
            updated.dietEnergyDeficitKcal?.takeIf { it > 0.0 },
        )
        setDietEnergyDeficitOverride(updated.dietEnergyDeficitOverride)
        this[SettingsPreferencesKeys.onboardingFinished] = updated.onboardingFinished
        setEnergyFormat(SettingsPreferencesKeys.energyFormat, updated.energyFormat)
        setAppLaunchInfo(updated.appLaunchInfo)
        this[SettingsPreferencesKeys.stepsCaloriesPerStepKcal] = updated.stepsCaloriesPerStepKcal
        this[SettingsPreferencesKeys.healthConnectStepsEnabled] = updated.healthConnectStepsEnabled
        this[SettingsPreferencesKeys.healthConnectWeightEnabled] = updated.healthConnectWeightEnabled
        this[SettingsPreferencesKeys.healthConnectStepsLastSyncedEpochSeconds] =
            updated.healthConnectStepsLastSyncedEpochSeconds
        this[SettingsPreferencesKeys.healthConnectWeightLastSyncedEpochSeconds] =
            updated.healthConnectWeightLastSyncedEpochSeconds
        this[SettingsPreferencesKeys.homeSyncHealthConnectEnabled] =
            updated.homeSyncHealthConnectEnabled
        this[SettingsPreferencesKeys.homeSyncFddbDiaryEnabled] = updated.homeSyncFddbDiaryEnabled
        setWithNull(
            SettingsPreferencesKeys.fddbDiarySyncLastImported,
            updated.fddbDiarySyncLastImported,
        )
        setWithNull(
            SettingsPreferencesKeys.fddbDiarySyncLastSkipped,
            updated.fddbDiarySyncLastSkipped,
        )
        setWithNull(
            SettingsPreferencesKeys.fddbDiarySyncLastFailed,
            updated.fddbDiarySyncLastFailed,
        )
        setWithNull(
            SettingsPreferencesKeys.fddbDiarySyncLastErrorMessage,
            updated.fddbDiarySyncLastErrorMessage,
        )
        setWithNull(
            SettingsPreferencesKeys.fddbDiarySyncLastAttemptEpochSeconds,
            updated.fddbDiarySyncLastAttemptEpochSeconds,
        )
        this[SettingsPreferencesKeys.fddbProductSyncManualCount] =
            updated.fddbProductSyncManualCount
        setPendingProductPhotoQuality(updated.pendingProductPhotoQuality)
        this[SettingsPreferencesKeys.crosstrainerCalorieDiscountPercent] =
            updated.crosstrainerCalorieDiscountPercent
        setTodayEnergyGoalAdjustment(updated.todayEnergyGoalAdjustment)
        this[SettingsPreferencesKeys.defaultLockedDaySurplusKcal] =
            updated.defaultLockedDaySurplusKcal.takeIf { it.isFinite() && it >= 0.0 }
                ?: DEFAULT_LOCKED_DAY_SURPLUS_KCAL
        setLockedDaySurpluses(updated.lockedDaySurpluses)
    }
}

private fun <T> MutablePreferences.setWithNull(key: Preferences.Key<T>, value: T?) {
    if (value != null) {
        this[key] = value
    } else {
        this.remove(key)
    }
}

private fun MutablePreferences.setNutrientsOrder(
    key: Preferences.Key<String>,
    value: List<NutrientsOrder>,
) = setWithNull(key, value.joinToString(",") { it.ordinal.toString() })

private fun Preferences.getNutrientsOrder(key: Preferences.Key<String>): List<NutrientsOrder> =
    runCatching { this[key]?.split(",")?.map { NutrientsOrder.entries[it.toInt()] } }.getOrNull()
        ?: NutrientsOrder.defaultOrder

private fun MutablePreferences.setHomeCardOrder(
    key: Preferences.Key<String>,
    value: List<HomeCard>,
) = setWithNull(key, value.joinToString(",") { it.name })

private fun Preferences.getHomeCardOrder(key: Preferences.Key<String>): List<HomeCard> =
    runCatching {
            this[key]
                ?.split(",")
                ?.filter(String::isNotBlank)
                ?.map {
                    it.toIntOrNull()?.let { ordinal -> HomeCard.entries[ordinal] }
                        ?: HomeCard.valueOf(it)
                }
                ?.let { savedOrder ->
                    savedOrder + HomeCard.defaultOrder.filterNot(savedOrder::contains)
                }
        }
        .getOrNull() ?: HomeCard.defaultOrder

private fun MutablePreferences.setEnergyFormat(key: Preferences.Key<Int>, value: EnergyFormat) =
    setWithNull(key, value.ordinal)

private fun Preferences.getEnergyFormat(key: Preferences.Key<Int>): EnergyFormat =
    runCatching { EnergyFormat.entries[this[key] ?: EnergyFormat.DEFAULT.ordinal] }
        .getOrElse { EnergyFormat.DEFAULT }

private fun MutablePreferences.setGoalDisplayMode(value: GoalDisplayMode) =
    setWithNull(SettingsPreferencesKeys.goalDisplayMode, value.name)

private fun Preferences.getGoalDisplayMode(): GoalDisplayMode =
    runCatching {
            this[SettingsPreferencesKeys.goalDisplayMode]?.let(GoalDisplayMode::valueOf)
                ?: if (this[SettingsPreferencesKeys.optimizedGoalDisplayEnabled] == true) {
                    GoalDisplayMode.Optimized
                } else {
                    GoalDisplayMode.Normal
                }
        }
        .getOrElse { GoalDisplayMode.Normal }

private fun MutablePreferences.setDietEnergyDeficitOverride(
    value: DietEnergyDeficitOverride?
) {
    if (value != null && value.energyDeficitKcal > 0.0 && value.endDate >= value.startDate) {
        this[SettingsPreferencesKeys.dietEnergyDeficitOverrideKcal] = value.energyDeficitKcal
        this[SettingsPreferencesKeys.dietEnergyDeficitOverrideStartEpochDay] =
            value.startDate.toEpochDays()
        this[SettingsPreferencesKeys.dietEnergyDeficitOverrideEndEpochDay] =
            value.endDate.toEpochDays()
    } else {
        remove(SettingsPreferencesKeys.dietEnergyDeficitOverrideKcal)
        remove(SettingsPreferencesKeys.dietEnergyDeficitOverrideStartEpochDay)
        remove(SettingsPreferencesKeys.dietEnergyDeficitOverrideEndEpochDay)
    }
}

private fun Preferences.getDietEnergyDeficitOverride(): DietEnergyDeficitOverride? {
    val kcal = this[SettingsPreferencesKeys.dietEnergyDeficitOverrideKcal]?.takeIf { it > 0.0 }
    val startEpochDay = this[SettingsPreferencesKeys.dietEnergyDeficitOverrideStartEpochDay]
    val endEpochDay = this[SettingsPreferencesKeys.dietEnergyDeficitOverrideEndEpochDay]

    if (kcal == null || startEpochDay == null || endEpochDay == null) return null

    val startDate = LocalDate.fromEpochDays(startEpochDay.toInt())
    val endDate = LocalDate.fromEpochDays(endEpochDay.toInt())
    return DietEnergyDeficitOverride(
        energyDeficitKcal = kcal,
        startDate = startDate,
        endDate = endDate,
    ).takeIf { it.endDate >= it.startDate }
}

private fun MutablePreferences.setTodayEnergyGoalAdjustment(
    value: TodayEnergyGoalAdjustment?
) {
    if (value != null && value.reductionKcal > 0.0) {
        this[SettingsPreferencesKeys.todayEnergyGoalAdjustmentEpochDay] = value.date.toEpochDays()
        this[SettingsPreferencesKeys.todayEnergyGoalAdjustmentKcal] = value.reductionKcal
    } else {
        remove(SettingsPreferencesKeys.todayEnergyGoalAdjustmentEpochDay)
        remove(SettingsPreferencesKeys.todayEnergyGoalAdjustmentKcal)
    }
}

private fun Preferences.getTodayEnergyGoalAdjustment(): TodayEnergyGoalAdjustment? {
    val epochDay = this[SettingsPreferencesKeys.todayEnergyGoalAdjustmentEpochDay] ?: return null
    val reductionKcal =
        this[SettingsPreferencesKeys.todayEnergyGoalAdjustmentKcal]?.takeIf { it > 0.0 }
            ?: return null
    return TodayEnergyGoalAdjustment(
        date = LocalDate.fromEpochDays(epochDay.toInt()),
        reductionKcal = reductionKcal,
    )
}

private fun MutablePreferences.setLockedDaySurpluses(value: List<LockedDaySurplus>) {
    val serialized =
        value
            .asSequence()
            .filter { it.surplusKcal.isFinite() && it.surplusKcal >= 0.0 }
            .associateBy { it.date }
            .values
            .sortedBy { it.date }
            .joinToString(",") { "${it.date.toEpochDays()}:${it.surplusKcal}" }
    setWithNull(SettingsPreferencesKeys.lockedDaySurpluses, serialized.takeIf(String::isNotEmpty))
}

private fun Preferences.getLockedDaySurpluses(): List<LockedDaySurplus> =
    this[SettingsPreferencesKeys.lockedDaySurpluses]
        ?.split(',')
        .orEmpty()
        .mapNotNull { entry ->
            val parts = entry.split(':')
            if (parts.size != 2) return@mapNotNull null
            val epochDay = parts[0].toIntOrNull() ?: return@mapNotNull null
            val surplus = parts[1].toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
                ?: return@mapNotNull null
            runCatching { LockedDaySurplus(LocalDate.fromEpochDays(epochDay), surplus) }.getOrNull()
        }
        .associateBy { it.date }
        .values
        .sortedBy { it.date }

private fun MutablePreferences.setPendingProductPhotoQuality(value: PendingProductPhotoQuality) =
    setWithNull(SettingsPreferencesKeys.pendingProductPhotoQuality, value.name)

private fun Preferences.getPendingProductPhotoQuality(): PendingProductPhotoQuality =
    runCatching {
            this[SettingsPreferencesKeys.pendingProductPhotoQuality]?.let(
                PendingProductPhotoQuality::valueOf
            ) ?: PendingProductPhotoQuality.Balanced
        }
        .getOrElse { PendingProductPhotoQuality.Balanced }

private fun Preferences.getAppLaunchInfo(): AppLaunchInfo =
    AppLaunchInfo(
        firstLaunch = getInstantFromEpochSeconds(SettingsPreferencesKeys.firstLaunchEpoch),
        firstLaunchCurrentVersion =
            run {
                val version = this[SettingsPreferencesKeys.firstLaunchCurrentVersionName]
                val epoch =
                    getInstantFromEpochSeconds(
                        SettingsPreferencesKeys.firstLaunchCurrentVersionEpoch
                    )
                if (version != null && epoch != null) version to epoch else null
            },
        launchesCount = this[SettingsPreferencesKeys.launchesCount] ?: 0,
    )

private fun MutablePreferences.setAppLaunchInfo(appLaunchInfo: AppLaunchInfo): MutablePreferences =
    apply {
        setInstantAsEpochSeconds(
            SettingsPreferencesKeys.firstLaunchEpoch,
            appLaunchInfo.firstLaunch,
        )
        setWithNull(
            SettingsPreferencesKeys.firstLaunchCurrentVersionName,
            appLaunchInfo.firstLaunchCurrentVersion?.first,
        )
        setInstantAsEpochSeconds(
            SettingsPreferencesKeys.firstLaunchCurrentVersionEpoch,
            appLaunchInfo.firstLaunchCurrentVersion?.second,
        )
        setWithNull(SettingsPreferencesKeys.launchesCount, appLaunchInfo.launchesCount)
    }

private fun Preferences.getInstantFromEpochSeconds(key: Preferences.Key<Long>): Instant? =
    this[key]?.let(Instant::fromEpochSeconds)

private fun MutablePreferences.setInstantAsEpochSeconds(
    key: Preferences.Key<Long>,
    value: Instant?,
) =
    when (val epochSeconds = value?.epochSeconds) {
        null -> remove(key)
        else -> this[key] = epochSeconds
    }

private object SettingsPreferencesKeys {
    val lastRememberedVersion = stringPreferencesKey("settings:lastRememberedVersion")
    val hidePreviewDialog = booleanPreferencesKey("settings:hidePreviewDialog")
    val showTranslationWarning = booleanPreferencesKey("settings:showTranslationWarning")
    val nutrientsOrder = stringPreferencesKey("settings:nutrientsOrder")
    val secureScreen = booleanPreferencesKey("settings:secureScreen")
    val homeCardOrder = stringPreferencesKey("settings:homeCardOrder")
    val expandGoalCard = booleanPreferencesKey("settings:expandGoalCard")
    val optimizedGoalDisplayEnabled =
        booleanPreferencesKey("settings:optimizedGoalDisplayEnabled")
    val goalCardModeSwitchingEnabled = booleanPreferencesKey("settings:goalCardModeSwitchingEnabled")
    val supplementalGoalsEnabled = booleanPreferencesKey("settings:supplementalGoalsEnabled")
    val goalDisplayMode = stringPreferencesKey("settings:goalDisplayMode")
    val dietEnergyDeficitKcal = doublePreferencesKey("settings:dietEnergyDeficitKcal")
    val dietEnergyDeficitOverrideKcal =
        doublePreferencesKey("settings:dietEnergyDeficitOverrideKcal")
    val dietEnergyDeficitOverrideStartEpochDay =
        longPreferencesKey("settings:dietEnergyDeficitOverrideStartEpochDay")
    val dietEnergyDeficitOverrideEndEpochDay =
        longPreferencesKey("settings:dietEnergyDeficitOverrideEndEpochDay")
    val onboardingFinished = booleanPreferencesKey("settings:onboardingFinished")
    val energyFormat = intPreferencesKey("settings:energyFormat")
    val stepsCaloriesPerStepKcal = doublePreferencesKey("settings:stepsCaloriesPerStepKcal")
    val healthConnectStepsEnabled = booleanPreferencesKey("settings:healthConnectStepsEnabled")
    val healthConnectWeightEnabled = booleanPreferencesKey("settings:healthConnectWeightEnabled")
    val healthConnectStepsLastSyncedEpochSeconds =
        longPreferencesKey("settings:healthConnectStepsLastSyncedEpochSeconds")
    val healthConnectWeightLastSyncedEpochSeconds =
        longPreferencesKey("settings:healthConnectWeightLastSyncedEpochSeconds")
    val homeSyncHealthConnectEnabled =
        booleanPreferencesKey("settings:homeSyncHealthConnectEnabled")
    val homeSyncFddbDiaryEnabled = booleanPreferencesKey("settings:homeSyncFddbDiaryEnabled")
    val fddbDiarySyncLastImported = intPreferencesKey("settings:fddbDiarySyncLastImported")
    val fddbDiarySyncLastSkipped = intPreferencesKey("settings:fddbDiarySyncLastSkipped")
    val fddbDiarySyncLastFailed = intPreferencesKey("settings:fddbDiarySyncLastFailed")
    val fddbDiarySyncLastErrorMessage =
        stringPreferencesKey("settings:fddbDiarySyncLastErrorMessage")
    val fddbDiarySyncLastAttemptEpochSeconds =
        longPreferencesKey("settings:fddbDiarySyncLastAttemptEpochSeconds")
    val fddbProductSyncManualCount = intPreferencesKey("settings:fddbProductSyncManualCount")
    val pendingProductPhotoQuality = stringPreferencesKey("settings:pendingProductPhotoQuality")
    val crosstrainerCalorieDiscountPercent =
        doublePreferencesKey("settings:crosstrainerCalorieDiscountPercent")
    val todayEnergyGoalAdjustmentEpochDay =
        longPreferencesKey("settings:todayEnergyGoalAdjustmentEpochDay")
    val todayEnergyGoalAdjustmentKcal =
        doublePreferencesKey("settings:todayEnergyGoalAdjustmentKcal")
    val defaultLockedDaySurplusKcal =
        doublePreferencesKey("settings:defaultLockedDaySurplusKcal")
    val lockedDaySurpluses = stringPreferencesKey("settings:lockedDaySurpluses")
    val firstLaunchEpoch = longPreferencesKey("first_launch_epoch")
    val firstLaunchCurrentVersionName = stringPreferencesKey("first_launch_current_version_name")
    val firstLaunchCurrentVersionEpoch = longPreferencesKey("first_launch_current_version_epoch")
    val launchesCount = intPreferencesKey("launches_count")
}
