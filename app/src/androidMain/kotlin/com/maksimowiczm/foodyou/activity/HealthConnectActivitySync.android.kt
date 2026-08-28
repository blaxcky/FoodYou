package com.maksimowiczm.foodyou.activity

import android.content.Context
import android.os.RemoteException
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.maksimowiczm.foodyou.activity.domain.entity.DailyStepSummary
import com.maksimowiczm.foodyou.activity.domain.repository.ActivityRepository
import com.maksimowiczm.foodyou.activity.domain.entity.StepExclusionPeriod
import com.maksimowiczm.foodyou.activity.domain.usecase.MINUTES_PER_DAY
import com.maksimowiczm.foodyou.activity.domain.usecase.boundedExcludedSteps
import com.maksimowiczm.foodyou.activity.domain.usecase.mergeStepExclusionPeriods
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.common.infrastructure.koin.userPreferencesRepository
import com.maksimowiczm.foodyou.settings.domain.entity.Settings
import java.io.IOException
import kotlin.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module

actual fun Module.healthConnectActivitySync() {
    single<HealthConnectActivitySync> {
        AndroidHealthConnectActivitySync(
            repository = get(),
            settingsRepository = userPreferencesRepository(),
            context = androidContext(),
        )
    }
}

private class AndroidHealthConnectActivitySync(
    private val repository: ActivityRepository,
    private val settingsRepository: UserPreferencesRepository<Settings>,
    private val context: Context,
) : HealthConnectActivitySync {
    override suspend fun availability(): HealthConnectAvailability =
        when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.Available
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                HealthConnectAvailability.UpdateRequired
            else -> HealthConnectAvailability.Unavailable
        }

    override suspend fun hasReadStepsPermission(): Boolean {
        if (availability() != HealthConnectAvailability.Available) return false

        return try {
            readStepsPermission in client().permissionController.getGrantedPermissions()
        } catch (_: SecurityException) {
            false
        } catch (_: IOException) {
            false
        } catch (_: RemoteException) {
            false
        } catch (_: RuntimeException) {
            false
        }
    }

    override suspend fun syncSteps(dates: List<LocalDate>): HealthConnectSyncResult {
        if (!settingsRepository.observe().first().healthConnectStepsEnabled) {
            return HealthConnectSyncResult.Disabled
        }

        return when (availability()) {
            HealthConnectAvailability.Unavailable -> HealthConnectSyncResult.Unavailable
            HealthConnectAvailability.UpdateRequired -> HealthConnectSyncResult.UpdateRequired
            HealthConnectAvailability.Available -> syncAvailableSteps(dates)
        }
    }

    private suspend fun syncAvailableSteps(dates: List<LocalDate>): HealthConnectSyncResult {
        if (!hasReadStepsPermission()) return HealthConnectSyncResult.MissingPermission

        return try {
            val client = client()
            val timeZone = TimeZone.currentSystemDefault()
            dates.distinct().forEach { date ->
                val rawSteps = client.aggregateSteps(date.dayStart(timeZone), date.dayEnd(timeZone))
                val periods =
                    mergeStepExclusionPeriods(
                        repository.observeStepExclusionPeriods(date).first()
                    )
                val excludedSteps =
                    boundedExcludedSteps(
                        rawSteps = rawSteps,
                        intervalSteps =
                            periods.map { period ->
                                client.aggregateSteps(
                                    period.startInstant(timeZone),
                                    period.endInstant(timeZone),
                                )
                            },
                    )
                repository.upsertStepSummary(
                    DailyStepSummary(
                        date = date,
                        rawSteps = rawSteps,
                        excludedSteps = excludedSteps,
                        syncedAt = Clock.System.now(),
                    )
                )
            }
            settingsRepository.update {
                copy(healthConnectStepsLastSyncedEpochSeconds = Clock.System.now().epochSeconds)
            }
            HealthConnectSyncResult.Synced
        } catch (_: SecurityException) {
            HealthConnectSyncResult.MissingPermission
        } catch (_: IOException) {
            HealthConnectSyncResult.Failed
        } catch (_: RemoteException) {
            HealthConnectSyncResult.Failed
        } catch (_: RuntimeException) {
            HealthConnectSyncResult.Failed
        }
    }

    private fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(context)

    override fun cancelPeriodicSync() {
        WorkManager.getInstance(context).cancelUniqueWork(ActivityStepsSyncWorker.NAME)
    }
}

private suspend fun HealthConnectClient.aggregateSteps(
    start: java.time.Instant,
    end: java.time.Instant,
): Long =
    aggregate(
        AggregateRequest(
            metrics = setOf(StepsRecord.COUNT_TOTAL),
            timeRangeFilter =
                androidx.health.connect.client.time.TimeRangeFilter.between(start, end),
        )
    )[StepsRecord.COUNT_TOTAL] ?: 0

private fun LocalDate.dayStart(timeZone: TimeZone): java.time.Instant =
    atStartOfDayIn(timeZone).toJavaInstant()

private fun LocalDate.dayEnd(timeZone: TimeZone): java.time.Instant =
    plus(1, kotlinx.datetime.DateTimeUnit.DAY).atStartOfDayIn(timeZone).toJavaInstant()

private fun StepExclusionPeriod.startInstant(timeZone: TimeZone): java.time.Instant =
    date.atTime(LocalTime(startMinute / 60, startMinute % 60)).toInstant(timeZone).toJavaInstant()

private fun StepExclusionPeriod.endInstant(timeZone: TimeZone): java.time.Instant =
    if (endMinute == MINUTES_PER_DAY) {
        date.dayEnd(timeZone)
    } else {
        date.atTime(LocalTime(endMinute / 60, endMinute % 60)).toInstant(timeZone).toJavaInstant()
    }

private fun kotlin.time.Instant.toJavaInstant(): java.time.Instant =
    java.time.Instant.ofEpochSecond(epochSeconds, nanosecondsOfSecond.toLong())

private val readStepsPermission = HealthPermission.getReadPermission(StepsRecord::class)

object ActivityHealthConnectPermissions {
    val readSteps = readStepsPermission
    val backgroundRead = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND
}

class ActivityStepsSyncWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = Result.success()

    companion object {
        const val NAME = "activity_steps_sync"
    }
}
