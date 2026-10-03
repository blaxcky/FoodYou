package com.maksimowiczm.foodyou.food.infrastructure.fddb

import com.maksimowiczm.foodyou.food.domain.entity.FddbDiaryEntry
import com.maksimowiczm.foodyou.food.domain.repository.FddbDiaryGateway
import kotlinx.datetime.LocalDate

internal class FddbDiaryRemoteDataSource(
    private val sessions: FddbSessionManager,
    private val parser: FddbDiaryParser,
) : FddbDiaryGateway {
    override suspend fun login(username: String, password: String) = sessions.login(username, password)

    override suspend fun getLastSevenDays(referenceDate: LocalDate): List<FddbDiaryEntry> =
        parser.parse(sessions.getDiaryHtml(), referenceDate)
}
