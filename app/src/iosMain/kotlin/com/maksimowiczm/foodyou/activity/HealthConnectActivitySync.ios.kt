package com.maksimowiczm.foodyou.activity

import org.koin.core.module.Module

actual fun Module.healthConnectActivitySync() {
    single<HealthConnectActivitySync> { NoOpHealthConnectActivitySync }
}

private object NoOpHealthConnectActivitySync : HealthConnectActivitySync {
    override suspend fun availability(): HealthConnectAvailability =
        HealthConnectAvailability.Unavailable

    override suspend fun hasReadStepsPermission(): Boolean = false

    override suspend fun syncSteps(
        dates: List<kotlinx.datetime.LocalDate>
    ): HealthConnectSyncResult = HealthConnectSyncResult.Unavailable

    override fun cancelPeriodicSync() = Unit
}
