package com.maksimowiczm.foodyou.food.domain.repository

import com.maksimowiczm.foodyou.food.domain.entity.FddbDiaryEntry
import kotlinx.datetime.LocalDate

interface FddbDiaryGateway {
    /** Verify the account and save its credentials and session in one serialized operation. */
    suspend fun login(username: String, password: String)

    suspend fun getLastSevenDays(referenceDate: LocalDate): List<FddbDiaryEntry>
}
