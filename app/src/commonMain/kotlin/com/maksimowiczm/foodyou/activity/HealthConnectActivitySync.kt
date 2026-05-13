package com.maksimowiczm.foodyou.activity

import kotlinx.datetime.LocalDate
import org.koin.core.module.Module

interface HealthConnectActivitySync {
    suspend fun syncSteps(dates: List<LocalDate>)
    fun schedulePeriodicSync()
}

expect fun Module.healthConnectActivitySync()
