package com.maksimowiczm.foodyou.food.infrastructure.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FddbDiarySyncEntryDao {
    @Query("SELECT EXISTS(SELECT 1 FROM FddbDiarySyncEntry WHERE fddbEntryId = :fddbEntryId)")
    suspend fun contains(fddbEntryId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: FddbDiarySyncEntryEntity): Long
}
