package com.maksimowiczm.foodyou.activity

import com.maksimowiczm.foodyou.activity.infrastructure.room.DailyStepSummaryDao
import com.maksimowiczm.foodyou.activity.infrastructure.room.ManualActivityEntryDao
import com.maksimowiczm.foodyou.activity.infrastructure.room.StepExclusionPeriodDao

interface ActivityDatabase {
    val manualActivityEntryDao: ManualActivityEntryDao
    val dailyStepSummaryDao: DailyStepSummaryDao
    val stepExclusionPeriodDao: StepExclusionPeriodDao
}
