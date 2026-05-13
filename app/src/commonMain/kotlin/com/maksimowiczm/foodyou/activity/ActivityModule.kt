package com.maksimowiczm.foodyou.activity

import com.maksimowiczm.foodyou.activity.domain.repository.ActivityRepository
import com.maksimowiczm.foodyou.activity.infrastructure.repository.RoomActivityRepository
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.bind
import org.koin.dsl.module

val activityModule = module { activity() }

fun Module.activity() {
    factoryOf(::RoomActivityRepository).bind<ActivityRepository>()
    factory { get<ActivityDatabase>().manualActivityEntryDao }
    factory { get<ActivityDatabase>().dailyStepSummaryDao }
    healthConnectActivitySync()
}
