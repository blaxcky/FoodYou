package com.maksimowiczm.foodyou.activity.infrastructure.room

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyStepSummaryDao {
    @Query("SELECT * FROM DailyStepSummary WHERE dateEpochDay = :dateEpochDay")
    fun observe(dateEpochDay: Long): Flow<DailyStepSummaryEntity?>

    @Upsert suspend fun upsert(summary: DailyStepSummaryEntity)
}
