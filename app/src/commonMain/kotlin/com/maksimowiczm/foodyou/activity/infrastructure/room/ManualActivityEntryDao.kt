package com.maksimowiczm.foodyou.activity.infrastructure.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ManualActivityEntryDao {
    @Query("SELECT * FROM ManualActivityEntry WHERE id = :id")
    fun observe(id: Long): Flow<ManualActivityEntryEntity?>

    @Query("SELECT * FROM ManualActivityEntry WHERE dateEpochDay = :dateEpochDay ORDER BY createdEpochSeconds DESC")
    fun observeAll(dateEpochDay: Long): Flow<List<ManualActivityEntryEntity>>

    @Query("SELECT COALESCE(SUM(energyKcal), 0.0) FROM ManualActivityEntry WHERE dateEpochDay = :dateEpochDay")
    fun observeEnergySum(dateEpochDay: Long): Flow<Double>

    @Insert suspend fun insert(entry: ManualActivityEntryEntity): Long

    @Update suspend fun update(entry: ManualActivityEntryEntity)

    @Query("DELETE FROM ManualActivityEntry WHERE id = :id") suspend fun delete(id: Long)
}
