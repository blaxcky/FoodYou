package com.maksimowiczm.foodyou.weight.domain.repository

import com.maksimowiczm.foodyou.weight.domain.entity.DailyWeightEntry
import com.maksimowiczm.foodyou.weight.domain.entity.WeightGoal
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface WeightRepository {
    fun observeEntries(): Flow<List<DailyWeightEntry>>

    fun observeToday(): Flow<DailyWeightEntry?>

    fun observeGoal(): Flow<WeightGoal>

    suspend fun upsertToday(weightKg: Double)

    suspend fun upsert(entry: DailyWeightEntry)

    suspend fun upsertAll(entries: List<DailyWeightEntry>)

    suspend fun entry(date: LocalDate): DailyWeightEntry?

    suspend fun updateGoal(goal: WeightGoal)
}
