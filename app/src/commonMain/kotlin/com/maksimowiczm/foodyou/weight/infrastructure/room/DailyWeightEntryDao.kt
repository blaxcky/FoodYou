package com.maksimowiczm.foodyou.weight.infrastructure.room

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyWeightEntryDao {
    @Query("SELECT * FROM DailyWeightEntry ORDER BY dateEpochDay DESC, measuredEpochSeconds DESC")
    fun observeAll(): Flow<List<DailyWeightEntryEntity>>

    @Query("SELECT * FROM DailyWeightEntry WHERE id = :id")
    suspend fun find(id: String): DailyWeightEntryEntity?

    @Query("UPDATE DailyWeightEntry SET isHidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: String, hidden: Boolean)

    @Upsert suspend fun upsert(entry: DailyWeightEntryEntity)

    @Upsert suspend fun upsertAll(entries: List<DailyWeightEntryEntity>)

    @Transaction
    suspend fun upsertImportedPreservingHidden(entries: List<DailyWeightEntryEntity>) {
        entries.forEach { incoming ->
            val existing = find(incoming.id)
            if (existing?.isFoodYouRecord == true &&
                existing.measuredEpochSeconds > incoming.measuredEpochSeconds
            ) return@forEach
            upsert(incoming.copy(isHidden = existing?.isHidden ?: incoming.isHidden))
        }
    }
}
