package com.maksimowiczm.foodyou.food.infrastructure.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FddbDiarySyncEntryDao {
    @Query("SELECT fddbEntryId FROM FddbDiarySyncEntry WHERE fddbEntryId IN (:ids)")
    suspend fun findSyncedIds(ids: List<String>): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: FddbDiarySyncEntryEntity): Long
}
