package com.maksimowiczm.foodyou.weight

import com.maksimowiczm.foodyou.activity.HealthConnectAvailability
import com.maksimowiczm.foodyou.activity.HealthConnectSyncResult
import com.maksimowiczm.foodyou.weight.domain.entity.DailyWeightEntry
import org.koin.core.module.Module
import org.koin.dsl.bind

actual fun Module.healthConnectWeightSync() {
    single { NoOpHealthConnectWeightSync }.bind<HealthConnectWeightSync>()
}

private object NoOpHealthConnectWeightSync : HealthConnectWeightSync {
    override suspend fun availability(): HealthConnectAvailability =
        HealthConnectAvailability.Unavailable

    override suspend fun hasWeightPermission(): Boolean = false

    override suspend fun syncHistorical(): HealthConnectSyncResult =
        HealthConnectSyncResult.Unavailable

    override suspend fun writeToday(entry: DailyWeightEntry): HealthConnectSyncResult =
        HealthConnectSyncResult.Unavailable
}
