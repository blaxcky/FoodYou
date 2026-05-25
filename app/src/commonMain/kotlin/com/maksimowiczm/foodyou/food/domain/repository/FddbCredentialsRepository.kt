package com.maksimowiczm.foodyou.food.domain.repository

import kotlinx.coroutines.flow.Flow

interface FddbCredentialsRepository {
    suspend fun store(login: String, password: String)

    suspend fun clear()

    fun hasCredentials(): Flow<Boolean>

    suspend fun loadCredentials(): Pair<String, String>?
}
