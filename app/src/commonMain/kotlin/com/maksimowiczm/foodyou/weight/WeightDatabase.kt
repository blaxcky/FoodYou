package com.maksimowiczm.foodyou.weight

import com.maksimowiczm.foodyou.weight.infrastructure.room.DailyWeightEntryDao

interface WeightDatabase {
    val dailyWeightEntryDao: DailyWeightEntryDao
}
