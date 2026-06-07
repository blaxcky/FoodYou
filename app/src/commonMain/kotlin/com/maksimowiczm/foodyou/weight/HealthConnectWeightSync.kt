package com.maksimowiczm.foodyou.weight

import com.maksimowiczm.foodyou.activity.HealthConnectAvailability
import com.maksimowiczm.foodyou.activity.HealthConnectSyncResult
import com.maksimowiczm.foodyou.weight.domain.entity.DailyWeightEntry

interface HealthConnectWeightSync {
    suspend fun availability(): HealthConnectAvailability

    suspend fun hasWeightPermission(): Boolean

    suspend fun syncHistorical(): HealthConnectSyncResult

    suspend fun writeToday(entry: DailyWeightEntry): HealthConnectSyncResult
}

expect fun org.koin.core.module.Module.healthConnectWeightSync()
