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
import com.maksimowiczm.foodyou.weight.domain.usecase.latestWeightEntryPerDay
import com.maksimowiczm.foodyou.weight.domain.usecase.resolveWeightConflict
import java.io.IOException
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
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
            entry.healthConnectRecordId?.let { id ->
                client()
                    .deleteRecords(
                        recordType = WeightRecord::class,
                        recordIdsList = listOf(id),
                        clientRecordIdsList = emptyList(),
                    )
            }
            val metadata = Metadata.manualEntry(clientRecordId = foodYouClientRecordId(entry.date))
            val record =
                WeightRecord(
                    time = entry.measuredAt.toJavaInstant(),
                    zoneOffset = null,
                    weight = androidx.health.connect.client.units.Mass.kilograms(entry.weightKg),
                    metadata = metadata,
                )
            client().insertRecords(listOf(record))
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
            val records =
                client()
                    .readRecords(
                        ReadRecordsRequest(
                            recordType = WeightRecord::class,
                            timeRangeFilter =
                                TimeRangeFilter.between(
                                    java.time.Instant.EPOCH,
                                    java.time.Instant.now().plusSeconds(60),
                                ),
                        )
                    )
                    .records
            val timeZone = TimeZone.currentSystemDefault()
            val entries =
                latestWeightEntryPerDay(
                    records.map {
                        val date = it.time.toKotlinInstant().toLocalDateTime(timeZone).date
                        DailyWeightEntry(
                            date = date,
                            weightKg = it.weight.inKilograms,
                            measuredAt = it.time.toKotlinInstant(),
                            healthConnectRecordId = it.metadata.id,
                            isFoodYouRecord =
                                it.metadata.clientRecordId == foodYouClientRecordId(date),
                        )
                    }
                )
                    .map { incoming ->
                        val local = repository.entry(incoming.date)
                        resolveWeightConflict(local, incoming)
                    }
            repository.upsertAll(entries)
            settingsRepository.update {
                copy(healthConnectWeightLastSyncedEpochSeconds = Clock.System.now().epochSeconds)
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
