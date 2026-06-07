package com.maksimowiczm.foodyou.weight

import com.maksimowiczm.foodyou.weight.domain.repository.WeightRepository
import com.maksimowiczm.foodyou.weight.infrastructure.repository.RoomWeightRepository
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.bind
import org.koin.dsl.module

val weightModule = module { weight() }

fun Module.weight() {
    factoryOf(::RoomWeightRepository).bind<WeightRepository>()
    factory { get<WeightDatabase>().dailyWeightEntryDao }
    healthConnectWeightSync()
}
