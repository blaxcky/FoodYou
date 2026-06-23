package com.maksimowiczm.foodyou.food.infrastructure.room

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodSnapEntryDao {
    @Query("SELECT * FROM FoodSnapEntry ORDER BY createdAt ASC, id ASC")
    fun observeEntries(): Flow<List<FoodSnapEntryEntity>>

    @Query("SELECT * FROM FoodSnapEntry WHERE id = :id")
    fun observeEntry(id: Long): Flow<FoodSnapEntryEntity?>

    @Insert suspend fun insert(entry: FoodSnapEntryEntity): Long

    @Update suspend fun update(entry: FoodSnapEntryEntity)

    @Delete suspend fun delete(entry: FoodSnapEntryEntity)
}
