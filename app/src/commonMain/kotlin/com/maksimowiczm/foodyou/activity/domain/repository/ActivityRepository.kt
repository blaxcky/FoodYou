package com.maksimowiczm.foodyou.activity.domain.repository

import com.maksimowiczm.foodyou.activity.domain.entity.DailyActivitySummary
import com.maksimowiczm.foodyou.activity.domain.entity.DailyStepSummary
import com.maksimowiczm.foodyou.activity.domain.entity.ManualActivityEntry
import com.maksimowiczm.foodyou.activity.domain.entity.ManualActivityEntryId
import com.maksimowiczm.foodyou.activity.domain.entity.StepExclusionPeriod
import com.maksimowiczm.foodyou.training.ImportedActivity
import com.maksimowiczm.foodyou.training.ImportedActivityId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate

interface ActivityRepository {
    fun observeImportedEntries(date: LocalDate): Flow<List<ImportedActivity>> = kotlinx.coroutines.flow.flowOf(emptyList())

    fun observeImportedEntry(id: ImportedActivityId): Flow<ImportedActivity?> = kotlinx.coroutines.flow.flowOf(null)

    suspend fun updateImportedEntry(entry: ImportedActivity)

    suspend fun deleteImportedEntry(id: ImportedActivityId)

    fun observeManualEntry(id: ManualActivityEntryId): Flow<ManualActivityEntry?>

    fun observeManualEntries(date: LocalDate): Flow<List<ManualActivityEntry>>

    fun observeDailySummary(date: LocalDate, kcalPerStep: Double?): Flow<DailyActivitySummary>

    fun observeStepExclusionPeriods(date: LocalDate): Flow<List<StepExclusionPeriod>>

    suspend fun readStepExclusionPeriods(dates: List<LocalDate>): List<StepExclusionPeriod> =
        dates.distinct().flatMap { observeStepExclusionPeriods(it).first() }

    suspend fun upsertStepSummaries(summaries: List<DailyStepSummary>) {
        summaries.forEach { upsertStepSummary(it) }
    }

    suspend fun createManualEntry(entry: ManualActivityEntry): ManualActivityEntryId

    suspend fun updateManualEntry(entry: ManualActivityEntry)

    suspend fun deleteManualEntry(id: ManualActivityEntryId)

    suspend fun upsertStepSummary(summary: DailyStepSummary)

    suspend fun replaceStepExclusionPeriods(
        date: LocalDate,
        periods: List<StepExclusionPeriod>,
    )
}
