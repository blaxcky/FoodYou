package com.maksimowiczm.foodyou.activity.domain.repository

import com.maksimowiczm.foodyou.activity.domain.entity.DailyActivitySummary
import com.maksimowiczm.foodyou.activity.domain.entity.DailyStepSummary
import com.maksimowiczm.foodyou.activity.domain.entity.ManualActivityEntry
import com.maksimowiczm.foodyou.activity.domain.entity.ManualActivityEntryId
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface ActivityRepository {
    fun observeManualEntry(id: ManualActivityEntryId): Flow<ManualActivityEntry?>

    fun observeManualEntries(date: LocalDate): Flow<List<ManualActivityEntry>>

    fun observeDailySummary(date: LocalDate, kcalPerStep: Double?): Flow<DailyActivitySummary>

    suspend fun createManualEntry(entry: ManualActivityEntry): ManualActivityEntryId

    suspend fun updateManualEntry(entry: ManualActivityEntry)

    suspend fun deleteManualEntry(id: ManualActivityEntryId)

    suspend fun upsertStepSummary(summary: DailyStepSummary)
}
