package com.maksimowiczm.foodyou.activity

import kotlinx.datetime.LocalDate
import org.koin.core.module.Module

interface HealthConnectActivitySync {
    suspend fun availability(): HealthConnectAvailability

    suspend fun hasReadStepsPermission(): Boolean

    suspend fun syncSteps(dates: List<LocalDate>): HealthConnectSyncResult

    fun schedulePeriodicSync()
}

enum class HealthConnectAvailability {
    Available,
    Unavailable,
    UpdateRequired,
}

enum class HealthConnectSyncResult {
    Synced,
    Disabled,
    Unavailable,
    UpdateRequired,
    MissingPermission,
    Failed,
}

expect fun Module.healthConnectActivitySync()
