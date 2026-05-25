package com.maksimowiczm.foodyou.food.infrastructure.fddb

import com.maksimowiczm.foodyou.food.domain.repository.FddbProductGateway
import com.maksimowiczm.foodyou.food.domain.repository.FddbDiaryGateway
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.HttpTimeout
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.onClose

internal fun Module.fddbModule() {
    single(named(FddbRemoteDataSource::class.qualifiedName!!)) {
            HttpClient {
                install(HttpTimeout)
                install(HttpCookies) { storage = AcceptAllCookiesStorage() }
            }
        }
        .onClose { it?.close() }

    factoryOf(::FddbProductParser)
    factoryOf(::FddbDiaryParser)
    factory {
            FddbRemoteDataSource(
                client = get(named(FddbRemoteDataSource::class.qualifiedName!!)),
                parser = get(),
                networkConfig = get(),
            )
        }
        .bind<FddbProductGateway>()
    factory {
            FddbDiaryRemoteDataSource(
                client = get(named(FddbRemoteDataSource::class.qualifiedName!!)),
                parser = get(),
                credentialsRepository = get(),
                networkConfig = get(),
            )
        }
        .bind<FddbDiaryGateway>()
}
