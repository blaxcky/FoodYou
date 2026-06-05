package com.maksimowiczm.foodyou.goals.domain.repository

import com.maksimowiczm.foodyou.goals.domain.entity.BasalMetabolicRateProfile
import kotlinx.coroutines.flow.Flow

interface BasalMetabolicRateProfileRepository {
    suspend fun updateProfile(profile: BasalMetabolicRateProfile)

    fun observeProfile(): Flow<BasalMetabolicRateProfile>
}
