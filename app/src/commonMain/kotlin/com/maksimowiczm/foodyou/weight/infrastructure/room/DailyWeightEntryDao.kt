package com.maksimowiczm.foodyou.weight.infrastructure.room

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyWeightEntryDao {
    @Query("SELECT * FROM DailyWeightEntry ORDER BY dateEpochDay DESC")
    fun observeAll(): Flow<List<DailyWeightEntryEntity>>

    @Query("SELECT * FROM DailyWeightEntry WHERE dateEpochDay = :dateEpochDay")
    fun observe(dateEpochDay: Long): Flow<DailyWeightEntryEntity?>

    @Query("SELECT * FROM DailyWeightEntry WHERE dateEpochDay = :dateEpochDay")
    suspend fun find(dateEpochDay: Long): DailyWeightEntryEntity?

    @Upsert suspend fun upsert(entry: DailyWeightEntryEntity)

    @Upsert suspend fun upsertAll(entries: List<DailyWeightEntryEntity>)
}
