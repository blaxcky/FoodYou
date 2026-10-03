package com.maksimowiczm.foodyou.activity

import com.maksimowiczm.foodyou.activity.domain.entity.DailyStepSummary
import com.maksimowiczm.foodyou.activity.domain.entity.StepExclusionPeriod
import com.maksimowiczm.foodyou.activity.domain.repository.ActivityRepository
import com.maksimowiczm.foodyou.activity.domain.usecase.MINUTES_PER_DAY
import com.maksimowiczm.foodyou.activity.domain.usecase.boundedExcludedSteps
import com.maksimowiczm.foodyou.activity.domain.usecase.mergeStepExclusionPeriods
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.settings.domain.entity.Settings
import com.maksimowiczm.foodyou.sync.SyncLog
import com.maksimowiczm.foodyou.sync.SyncLogOutcome
import com.maksimowiczm.foodyou.sync.logOutcome
import com.maksimowiczm.foodyou.sync.recordSyncStep
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

internal data class StepSyncPlan(
    val dates: List<LocalDate>,
    val fullSyncDate: LocalDate? = null,
    val reason: String,
) {
    val title: String
        get() = "${if (fullSyncDate != null) "Vollabgleich" else "Kurzabgleich"} · ${dates.size} Tage"
}

internal fun planHomeStepSync(selectedDate: LocalDate, today: LocalDate, timeZone: TimeZone, settings: Settings): StepSyncPlan {
    val reason = when {
        settings.healthConnectStepsLastFullSyncEpochDay == null -> "Noch kein vollständiger Abgleich gespeichert"
        settings.healthConnectStepsLastFullSyncTimeZoneId != timeZone.id -> "Zeitzone geändert"
        settings.healthConnectStepsLastFullSyncEpochDay != today.toEpochDays() -> "Erster vollständiger Abgleich für heute"
        else -> null
    }
    val dates = if (reason != null) {
        (0..30).map { today.minus(it, DateTimeUnit.DAY) }
    } else listOf(today, today.minus(1, DateTimeUnit.DAY))
    return StepSyncPlan(
        dates = (dates + selectedDate).distinct().sorted(),
        fullSyncDate = if (reason != null) today else null,
        reason = reason ?: "Vollabgleich heute bereits abgeschlossen; jüngste und ausgewählte Tage aktualisieren",
    )
}

internal data class StepAggregationBatch(val dates: List<LocalDate>, val start: Instant, val end: Instant) {
    val grouped: Boolean get() = dates.size > 1
}

/** Absolute midnight boundaries preserve the existing semantics, including travel and DST. */
internal fun stepAggregationBatches(dates: List<LocalDate>, timeZone: TimeZone): List<StepAggregationBatch> {
    val batches = mutableListOf<StepAggregationBatch>()
    for (date in dates.distinct().sorted()) {
        val start = date.atStartOfDayIn(timeZone)
        val end = date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone)
        val last = batches.lastOrNull()
        if (end - start == 24.hours && last != null && last.end == start &&
            last.end - last.start == 24.hours * last.dates.size) {
            batches[batches.lastIndex] = last.copy(dates = last.dates + date, end = end)
        } else {
            batches += StepAggregationBatch(listOf(date), start, end)
        }
    }
    return batches
}

internal interface StepAggregationSource {
    suspend fun aggregate(start: Instant, end: Instant): Long
    suspend fun aggregateByDay(start: Instant, end: Instant, timeZone: TimeZone): Map<LocalDate, Long>
}

internal class StepSyncPermissionException : Exception()

/** One coordinator owns both home and explicit syncs, preventing stale concurrent writes. */
internal class StepSyncCoordinator(
    private val repository: ActivityRepository,
    private val settingsRepository: UserPreferencesRepository<Settings>,
    private val source: StepAggregationSource,
    private val checkAccess: suspend () -> HealthConnectSyncResult?,
    private val syncLog: SyncLog? = null,
    private val clock: Clock = Clock.System,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {
    private val mutex = Mutex()

    suspend fun syncForHome(selectedDate: LocalDate): HealthConnectSyncResult = sync { settings, zone, today ->
        planHomeStepSync(selectedDate, today, zone, settings)
    }

    suspend fun syncDates(dates: List<LocalDate>): HealthConnectSyncResult = sync { _, _, _ ->
        StepSyncPlan(dates.distinct().sorted(), reason = "Ausdrücklich angeforderte Tage neu berechnen")
    }

    private suspend fun sync(plan: (Settings, TimeZone, LocalDate) -> StepSyncPlan): HealthConnectSyncResult =
        syncLog.recordSyncStep("Schritte synchronisieren", HealthConnectSyncResult::logOutcome) {
            try {
                mutex.withLock {
                    val settings = settingsRepository.observe().first()
                    if (!settings.healthConnectStepsEnabled) return@withLock HealthConnectSyncResult.Disabled
                    checkAccess()?.let { return@withLock it }
                    val zone = timeZone()
                    val today = clock.now().toLocalDateTime(zone).date
                    val selectedPlan = plan(settings, zone, today)
                    syncLog.recordSyncStep(selectedPlan.title, { SyncLogOutcome.success(selectedPlan.reason) }) {
                        execute(selectedPlan, zone)
                    }
                    HealthConnectSyncResult.Synced
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: StepSyncPermissionException) {
                HealthConnectSyncResult.MissingPermission
            } catch (_: Exception) {
                HealthConnectSyncResult.Failed
            }
        }

    private suspend fun execute(plan: StepSyncPlan, zone: TimeZone) {
        val periods = syncLog.recordSyncStep("Schrittausschlüsse gesammelt laden") {
            repository.readStepExclusionPeriods(plan.dates).groupBy { it.date }
                .mapValues { (_, periods) -> mergeStepExclusionPeriods(periods) }
        }
        val batches = stepAggregationBatches(plan.dates, zone)
        val rawSteps = syncLog.recordSyncStep("Tageswerte aus Health Connect lesen", {
            SyncLogOutcome.success("${batches.count { it.grouped }} gebündelte Abfragen · " +
                "${batches.count { !it.grouped && it.start < it.end }} Einzelabfragen")
        }) {
            val results = plan.dates.associateWith { 0L }.toMutableMap()
            for (batch in batches) {
                if (batch.grouped) {
                    val values = source.aggregateByDay(batch.start, batch.end, zone)
                    batch.dates.forEach { results[it] = values[it] ?: 0L }
                } else {
                    // A skipped civil date can have no elapsed time at all.
                    results[batch.dates.single()] = if (batch.start == batch.end) 0L
                        else source.aggregate(batch.start, batch.end)
                }
            }
            results
        }
        val excludedSteps = syncLog.recordSyncStep("Schrittausschlüsse aus Health Connect berechnen") {
            coroutineScope {
                val semaphore = Semaphore(4)
                plan.dates.flatMap { date ->
                    if (rawSteps.getValue(date) == 0L) emptyList() else periods[date].orEmpty().map { period ->
                        async {
                            date to semaphore.withPermit {
                                val start = period.startInstant(zone)
                                val end = period.endInstant(zone)
                                if (start >= end) 0L else source.aggregate(start, end)
                            }
                        }
                    }
                }.awaitAll().groupBy({ it.first }, { it.second })
            }
        }
        syncLog.recordSyncStep("Schrittzahlen gesammelt speichern") {
            val syncedAt = clock.now()
            repository.upsertStepSummaries(plan.dates.map { date ->
                DailyStepSummary(date, rawSteps.getValue(date),
                    boundedExcludedSteps(rawSteps.getValue(date), excludedSteps[date].orEmpty()), syncedAt)
            })
            settingsRepository.update {
                copy(
                    healthConnectStepsLastSyncedEpochSeconds = syncedAt.epochSeconds,
                    healthConnectStepsLastFullSyncEpochDay = plan.fullSyncDate?.toEpochDays()
                        ?: healthConnectStepsLastFullSyncEpochDay,
                    healthConnectStepsLastFullSyncTimeZoneId = if (plan.fullSyncDate != null) zone.id
                        else healthConnectStepsLastFullSyncTimeZoneId,
                )
            }
        }
    }
}

private fun StepExclusionPeriod.startInstant(zone: TimeZone): Instant =
    date.atTime(LocalTime(startMinute / 60, startMinute % 60)).toInstant(zone)

private fun StepExclusionPeriod.endInstant(zone: TimeZone): Instant =
    if (endMinute == MINUTES_PER_DAY) date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)
    else date.atTime(LocalTime(endMinute / 60, endMinute % 60)).toInstant(zone)
