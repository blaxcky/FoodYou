package com.maksimowiczm.foodyou.goals.infrastructure

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.maksimowiczm.foodyou.goals.domain.entity.BasalMetabolicRateProfile
import com.maksimowiczm.foodyou.goals.domain.entity.BiologicalSex
import com.maksimowiczm.foodyou.goals.domain.repository.BasalMetabolicRateProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal class DataStoreBasalMetabolicRateProfileRepository(
    private val dataStore: DataStore<Preferences>
) : BasalMetabolicRateProfileRepository {
    override suspend fun updateProfile(profile: BasalMetabolicRateProfile) {
        dataStore.updateData {
            it.toMutablePreferences().apply {
                val serialized = Json.encodeToString(DataStoreBasalMetabolicRateProfile(profile))
                set(BasalMetabolicRateProfileDataStoreKeys.profile, serialized)
            }
        }
    }

    override fun observeProfile(): Flow<BasalMetabolicRateProfile> =
        dataStore.data.map { preferences ->
            preferences[BasalMetabolicRateProfileDataStoreKeys.profile]?.let { serialized ->
                Json.decodeFromString<DataStoreBasalMetabolicRateProfile>(serialized).toProfile()
            } ?: BasalMetabolicRateProfile.Empty
        }
}

private object BasalMetabolicRateProfileDataStoreKeys {
    val profile = stringPreferencesKey("goals:basal_metabolic_rate_profile")
}

@Serializable
private data class DataStoreBasalMetabolicRateProfile(
    val weightKg: Double?,
    val heightCm: Double?,
    val birthDateEpochDay: Long?,
    val sex: BiologicalSex?,
) {
    constructor(
        profile: BasalMetabolicRateProfile
    ) : this(
        weightKg = profile.weightKg,
        heightCm = profile.heightCm,
        birthDateEpochDay = profile.birthDate?.toEpochDays(),
        sex = profile.sex,
    )

    fun toProfile(): BasalMetabolicRateProfile =
        BasalMetabolicRateProfile(
            weightKg = weightKg,
            heightCm = heightCm,
            birthDate = birthDateEpochDay?.let(LocalDate::fromEpochDays),
            sex = sex,
        )
}
