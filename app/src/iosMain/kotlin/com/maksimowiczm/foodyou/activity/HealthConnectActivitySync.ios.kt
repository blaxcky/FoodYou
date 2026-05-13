package com.maksimowiczm.foodyou.activity

import org.koin.core.module.Module
import org.koin.dsl.single

actual fun Module.healthConnectActivitySync() {
    single<HealthConnectActivitySync> { NoOpHealthConnectActivitySync }
}

private object NoOpHealthConnectActivitySync : HealthConnectActivitySync {
    override suspend fun syncSteps(dates: List<kotlinx.datetime.LocalDate>) = Unit

    override fun schedulePeriodicSync() = Unit
}
