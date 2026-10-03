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
import com.maksimowiczm.foodyou.activity.domain.repository.ActivityRepository
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.common.infrastructure.koin.userPreferencesRepository
import com.maksimowiczm.foodyou.settings.domain.entity.Settings
import com.maksimowiczm.foodyou.sync.SyncLog
import java.io.IOException
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.TimeZone
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module

actual fun Module.healthConnectActivitySync() {
    single<HealthConnectActivitySync> {
        AndroidHealthConnectActivitySync(
            repository = get(),
            settingsRepository = userPreferencesRepository(),
            context = androidContext(),
            syncLog = get(),
        )
    }
}

private class AndroidHealthConnectActivitySync(
    private val repository: ActivityRepository,
    private val settingsRepository: UserPreferencesRepository<Settings>,
    private val context: Context,
    private val syncLog: SyncLog,
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
        } catch (exception: RuntimeException) {
            if (exception is kotlinx.coroutines.CancellationException) throw exception
            false
        }
    }

    private val coordinator = StepSyncCoordinator(
        repository = repository,
        settingsRepository = settingsRepository,
        source = object : StepAggregationSource {
            override suspend fun aggregate(start: kotlin.time.Instant, end: kotlin.time.Instant): Long =
                withStepReadPermission {
                    client().aggregateSteps(start.toJavaInstant(), end.toJavaInstant())
                }

            override suspend fun aggregateByDay(
                start: kotlin.time.Instant,
                end: kotlin.time.Instant,
                timeZone: TimeZone,
            ): Map<LocalDate, Long> = withStepReadPermission {
                client().aggregateGroupByDuration(
                    androidx.health.connect.client.request.AggregateGroupByDurationRequest(
                        metrics = setOf(StepsRecord.COUNT_TOTAL),
                        timeRangeFilter = androidx.health.connect.client.time.TimeRangeFilter.between(
                            start.toJavaInstant(), end.toJavaInstant(),
                        ),
                        timeRangeSlicer = java.time.Duration.ofDays(1),
                    )
                ).associate { bucket ->
                    kotlin.time.Instant.fromEpochSeconds(bucket.startTime.epochSecond, bucket.startTime.nano)
                        .toLocalDateTime(timeZone).date to (bucket.result[StepsRecord.COUNT_TOTAL] ?: 0L)
                }
            }
        },
        checkAccess = {
            when (availability()) {
                HealthConnectAvailability.Unavailable -> HealthConnectSyncResult.Unavailable
                HealthConnectAvailability.UpdateRequired -> HealthConnectSyncResult.UpdateRequired
                HealthConnectAvailability.Available ->
                    if (hasReadStepsPermission()) null else HealthConnectSyncResult.MissingPermission
            }
        },
        syncLog = syncLog,
    )

    override suspend fun syncStepsForHome(selectedDate: LocalDate): HealthConnectSyncResult =
        coordinator.syncForHome(selectedDate)

    override suspend fun syncSteps(dates: List<LocalDate>): HealthConnectSyncResult =
        coordinator.syncDates(dates)

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

private suspend fun <T> withStepReadPermission(block: suspend () -> T): T =
    try { block() } catch (_: SecurityException) { throw StepSyncPermissionException() }
