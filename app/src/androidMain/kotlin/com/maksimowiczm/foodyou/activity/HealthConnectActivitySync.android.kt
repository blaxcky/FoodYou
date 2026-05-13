package com.maksimowiczm.foodyou.activity

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import com.maksimowiczm.foodyou.activity.domain.entity.DailyStepSummary
import com.maksimowiczm.foodyou.activity.domain.repository.ActivityRepository
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.common.infrastructure.koin.userPreferencesRepository
import com.maksimowiczm.foodyou.settings.domain.entity.Settings
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlinx.coroutines.flow.first
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.module.Module
import org.koin.dsl.single
import java.util.concurrent.TimeUnit

actual fun Module.healthConnectActivitySync() {
    single<HealthConnectActivitySync> {
        AndroidHealthConnectActivitySync(
            client =
                when (HealthConnectClient.getSdkStatus(androidContext())) {
                    HealthConnectClient.SDK_AVAILABLE ->
                        HealthConnectClient.getOrCreate(androidContext())
                    else -> null
                },
            repository = get(),
            settingsRepository = userPreferencesRepository(),
            context = androidContext(),
        )
    }
}

private class AndroidHealthConnectActivitySync(
    private val client: HealthConnectClient?,
    private val repository: ActivityRepository,
    private val settingsRepository: UserPreferencesRepository<Settings>,
    private val context: Context,
) : HealthConnectActivitySync {
    override suspend fun syncSteps(dates: List<LocalDate>) {
        if (!settingsRepository.observe().first().healthConnectStepsEnabled) return
        val client = client ?: return
        val granted = client.permissionController.getGrantedPermissions()
        if (HealthPermission.getReadPermission(StepsRecord::class) !in granted) return

        val timeZone = TimeZone.currentSystemDefault()
        dates.distinct().forEach { date ->
            val start = date.atStartOfDayIn(timeZone).toJavaInstant()
            val end =
                date.plus(1, kotlinx.datetime.DateTimeUnit.DAY)
                    .atStartOfDayIn(timeZone)
                    .toJavaInstant()
            val result =
                client.aggregate(
                    AggregateRequest(
                        metrics = setOf(StepsRecord.COUNT_TOTAL),
                        timeRangeFilter =
                            androidx.health.connect.client.time.TimeRangeFilter.between(
                                start,
                                end,
                            ),
                    )
                )
            repository.upsertStepSummary(
                DailyStepSummary(
                    date = date,
                    steps = result[StepsRecord.COUNT_TOTAL] ?: 0,
                    syncedAt = Clock.System.now(),
                )
            )
        }
        settingsRepository.update {
            copy(healthConnectStepsLastSyncedEpochSeconds = Clock.System.now().epochSeconds)
        }
    }

    override fun schedulePeriodicSync() {
        val request =
            PeriodicWorkRequestBuilder<ActivityStepsSyncWorker>(1, TimeUnit.HOURS).build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                ActivityStepsSyncWorker.NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
    }
}

private fun kotlin.time.Instant.toJavaInstant(): java.time.Instant =
    java.time.Instant.ofEpochSecond(epochSeconds, nanosecondsOfSecond.toLong())

object ActivityHealthConnectPermissions {
    val readSteps = HealthPermission.getReadPermission(StepsRecord::class)
    val backgroundRead = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND
}

class ActivityStepsSyncWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val sync: HealthConnectActivitySync = GlobalContext.get().get()
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        sync.syncSteps(listOf(today, today.minus(1, kotlinx.datetime.DateTimeUnit.DAY)))
        return Result.success()
    }

    companion object {
        const val NAME = "activity_steps_sync"
    }
}
