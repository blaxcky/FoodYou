package com.maksimowiczm.foodyou.food.infrastructure.fddb

import com.maksimowiczm.foodyou.food.domain.repository.FddbProductGateway
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.onClose

internal fun Module.fddbModule() {
    single(named(FddbRemoteDataSource::class.qualifiedName!!)) {
            HttpClient { install(HttpTimeout) }
        }
        .onClose { it?.close() }

    factoryOf(::FddbProductParser)
    factory {
            FddbRemoteDataSource(
                client = get(named(FddbRemoteDataSource::class.qualifiedName!!)),
                parser = get(),
            )
        }
        .bind<FddbProductGateway>()
}
