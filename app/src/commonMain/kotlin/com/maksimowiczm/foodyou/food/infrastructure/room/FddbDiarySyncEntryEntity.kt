package com.maksimowiczm.foodyou.food.infrastructure.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "FddbDiarySyncEntry", indices = [Index(value = ["fddbEntryId"], unique = true)])
data class FddbDiarySyncEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fddbEntryId: String,
    val syncedAt: Long,
)
