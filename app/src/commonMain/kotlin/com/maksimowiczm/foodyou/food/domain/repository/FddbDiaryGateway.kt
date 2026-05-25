package com.maksimowiczm.foodyou.food.domain.repository

import com.maksimowiczm.foodyou.food.domain.entity.FddbDiaryEntry
import kotlinx.datetime.LocalDate

interface FddbDiaryGateway {
    suspend fun login(username: String, password: String)

    suspend fun getLastSevenDays(referenceDate: LocalDate): List<FddbDiaryEntry>
}
