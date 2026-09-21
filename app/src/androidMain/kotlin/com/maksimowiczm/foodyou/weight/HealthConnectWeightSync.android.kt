package com.maksimowiczm.foodyou.weight

import android.content.Context
import android.os.RemoteException
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.maksimowiczm.foodyou.activity.HealthConnectAvailability
import com.maksimowiczm.foodyou.activity.HealthConnectSyncResult
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.common.infrastructure.koin.userPreferencesRepository
import com.maksimowiczm.foodyou.settings.domain.entity.Settings
import com.maksimowiczm.foodyou.weight.domain.entity.DailyWeightEntry
import com.maksimowiczm.foodyou.weight.domain.repository.WeightRepository
import com.maksimowiczm.foodyou.weight.domain.usecase.foodYouHealthConnectDate
import java.io.IOException
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.bind

actual fun Module.healthConnectWeightSync() {
    single<HealthConnectWeightSync> {
        AndroidHealthConnectWeightSync(
            repository = get(),
            settingsRepository = userPreferencesRepository(),
            context = androidContext(),
        )
    }
}

private class AndroidHealthConnectWeightSync(
    private val repository: WeightRepository,
    private val settingsRepository: UserPreferencesRepository<Settings>,
    private val context: Context,
) : HealthConnectWeightSync {
    private val syncMutex = Mutex()
    private val backfillWindowSeconds = 30L * 24 * 60 * 60

    override suspend fun availability(): HealthConnectAvailability =
        when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.Available
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                HealthConnectAvailability.UpdateRequired
            else -> HealthConnectAvailability.Unavailable
        }

    override suspend fun hasWeightPermission(): Boolean {
        if (availability() != HealthConnectAvailability.Available) return false
        return try {
            val granted = client().permissionController.getGrantedPermissions()
            readWeightPermission in granted && writeWeightPermission in granted
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

    override suspend fun syncHistorical(): HealthConnectSyncResult {
        if (!settingsRepository.observe().first().healthConnectWeightEnabled) {
            return HealthConnectSyncResult.Disabled
        }
        return when (availability()) {
            HealthConnectAvailability.Unavailable -> HealthConnectSyncResult.Unavailable
            HealthConnectAvailability.UpdateRequired -> HealthConnectSyncResult.UpdateRequired
            HealthConnectAvailability.Available -> syncAvailableHistorical()
        }
    }

    override suspend fun writeToday(entry: DailyWeightEntry): HealthConnectSyncResult {
        if (!settingsRepository.observe().first().healthConnectWeightEnabled) {
            return HealthConnectSyncResult.Disabled
        }
        return try {
            if (!hasWeightPermission()) return HealthConnectSyncResult.MissingPermission
            syncMutex.withLock {
                entry.healthConnectRecordId?.let { id ->
                    client().deleteRecords(
                        recordType = WeightRecord::class,
                        recordIdsList = listOf(id),
                        clientRecordIdsList = emptyList(),
                    )
                }
                val metadata = Metadata.manualEntry(clientRecordId = foodYouClientRecordId(entry.date))
                val record =
                    WeightRecord(
                        time = entry.measuredAt.toJavaInstant(),
                        zoneOffset = java.time.ZoneId.systemDefault().rules.getOffset(entry.measuredAt.toJavaInstant()),
                        weight = androidx.health.connect.client.units.Mass.kilograms(entry.weightKg),
                        metadata = metadata,
                    )
                val insertedId = client().insertRecords(listOf(record)).recordIdsList.singleOrNull()
                if (insertedId != null) {
                    repository.upsert(entry.copy(healthConnectRecordId = insertedId))
                }
            }
            syncHistorical()
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

    private suspend fun syncAvailableHistorical(): HealthConnectSyncResult {
        return try {
            if (!hasWeightPermission()) return HealthConnectSyncResult.MissingPermission
            syncMutex.withLock {
                val end = java.time.Instant.ofEpochSecond(java.time.Instant.now().epochSecond + 60)
                val start = end.minusSeconds(backfillWindowSeconds)
                repository.upsertAll(readWeightRecords(start, end).map(::toEntry))
                settingsRepository.update {
                    copy(healthConnectWeightLastSyncedEpochSeconds = Clock.System.now().epochSeconds)
                }
                backfillAvailableHistory(start)
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

    private suspend fun readWeightRecords(
        start: java.time.Instant,
        end: java.time.Instant,
    ): List<WeightRecord> {
        val records = mutableListOf<WeightRecord>()
        var pageToken: String? = null
        do {
            val response = client().readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    pageToken = pageToken,
                )
            )
            records += response.records
            pageToken = response.pageToken
        } while (!pageToken.isNullOrEmpty())
        return records
    }

    private suspend fun backfillAvailableHistory(recentStart: java.time.Instant) {
        val settings = settingsRepository.observe().first()
        if (settings.healthConnectWeightBackfillComplete) return
        var before =
            settings.healthConnectWeightBackfillBeforeEpochSeconds
                ?.let(java.time.Instant::ofEpochSecond) ?: recentStart
        // Limit each foreground sync to one year of older windows; the saved cursor resumes later.
        for (window in 0 until 12) {
            val start = before.minusSeconds(backfillWindowSeconds)
            if (start <= java.time.Instant.EPOCH) {
                settingsRepository.update { copy(healthConnectWeightBackfillComplete = true) }
                break
            }
            val records = try {
                readWeightRecords(start, before)
            } catch (error: SecurityException) {
                if (!hasWeightPermission()) throw error
                settingsRepository.update { copy(healthConnectWeightBackfillComplete = true) }
                break
            }
            repository.upsertAll(records.map(::toEntry))
            before = start
            settingsRepository.update {
                copy(healthConnectWeightBackfillBeforeEpochSeconds = before.epochSecond)
            }
        }
    }

    private fun toEntry(record: WeightRecord): DailyWeightEntry {
        val timeZone = TimeZone.currentSystemDefault()
        val ownDate = foodYouHealthConnectDate(
            originPackage = record.metadata.dataOrigin.packageName,
            ownPackage = context.packageName,
            clientRecordId = record.metadata.clientRecordId,
        )
        val date = ownDate ?: record.time.toKotlinInstant().toLocalDateTime(timeZone).date
        val ownRecord = ownDate != null
        return DailyWeightEntry(
            date = date,
            weightKg = record.weight.inKilograms,
            measuredAt = record.time.toKotlinInstant(),
            healthConnectRecordId = record.metadata.id,
            isFoodYouRecord = ownRecord,
            id = if (ownRecord) "local:${date.toEpochDays()}" else "hc:${record.metadata.id}",
            sourcePackageName = record.metadata.dataOrigin.packageName,
            sourceDeviceType = record.metadata.device?.type,
        )
    }

    private fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(context)
}

private fun foodYouClientRecordId(date: LocalDate): String = "foodyou-weight-${date.toEpochDays()}"

private fun Instant.toJavaInstant(): java.time.Instant =
    java.time.Instant.ofEpochSecond(epochSeconds, nanosecondsOfSecond.toLong())

private fun java.time.Instant.toKotlinInstant(): Instant =
    Instant.fromEpochSeconds(epochSecond, nano.toLong())

private val readWeightPermission = HealthPermission.getReadPermission(WeightRecord::class)
private val writeWeightPermission = HealthPermission.getWritePermission(WeightRecord::class)

object WeightHealthConnectPermissions {
    val readWeight = readWeightPermission
    val writeWeight = writeWeightPermission
}
