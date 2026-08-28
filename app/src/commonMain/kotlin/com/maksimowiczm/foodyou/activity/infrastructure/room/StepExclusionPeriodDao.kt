package com.maksimowiczm.foodyou.activity.infrastructure.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface StepExclusionPeriodDao {
    @Query(
        "SELECT * FROM StepExclusionPeriod WHERE dateEpochDay = :dateEpochDay " +
            "ORDER BY startMinute, endMinute"
    )
    fun observeAll(dateEpochDay: Long): Flow<List<StepExclusionPeriodEntity>>

    @Query("DELETE FROM StepExclusionPeriod WHERE dateEpochDay = :dateEpochDay")
    suspend fun deleteAll(dateEpochDay: Long)

    @Insert suspend fun insertAll(periods: List<StepExclusionPeriodEntity>)

    @Transaction
    suspend fun replaceAll(dateEpochDay: Long, periods: List<StepExclusionPeriodEntity>) {
        deleteAll(dateEpochDay)
        if (periods.isNotEmpty()) insertAll(periods)
    }
}
