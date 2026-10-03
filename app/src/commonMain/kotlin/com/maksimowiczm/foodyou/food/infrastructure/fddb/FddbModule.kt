package com.maksimowiczm.foodyou.food.infrastructure.fddb

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.maksimowiczm.foodyou.food.domain.repository.FddbCredentialsRepository
import com.maksimowiczm.foodyou.food.domain.repository.FddbDiaryGateway
import com.maksimowiczm.foodyou.food.domain.repository.FddbProductGateway
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.bind
import org.koin.dsl.onClose

internal fun Module.fddbModule() {
    single { FddbRequestQueue(syncLog = get()) }
    single(named(FddbRemoteDataSource::class.qualifiedName!!)) {
            HttpClient {
                install(HttpTimeout)
                install(HttpCookies) { storage = AcceptAllCookiesStorage() }
            }
        }
        .onClose { it?.close() }

    single { FddbCredentialStore(get(), get()) }
    single {
        val path = produceFddbSessionFile().toPath()
        FddbCookieStorage(PreferenceDataStoreFactory.createWithPath { path }, get())
    }
    single(named(FddbDiaryRemoteDataSource::class.qualifiedName!!)) {
        val cookies = get<FddbCookieStorage>()
        HttpClient {
            install(HttpTimeout)
            install(HttpCookies) { storage = cookies }
        }
    }.onClose { it?.close() }
    single {
        FddbSessionManager(
            client = get(named(FddbDiaryRemoteDataSource::class.qualifiedName!!)),
            cookies = get(), credentials = get(), networkConfig = get(),
            requestQueue = get(), syncLog = get(),
        )
    }.bind<FddbCredentialsRepository>()

    factoryOf(::FddbProductParser)
    factoryOf(::FddbDiaryParser)
    factory {
            FddbRemoteDataSource(
                client = get(named(FddbRemoteDataSource::class.qualifiedName!!)),
                parser = get(),
                networkConfig = get(),
                requestQueue = get(),
            )
        }
        .bind<FddbProductGateway>()
    factory {
            FddbDiaryRemoteDataSource(
                sessions = get(),
                parser = get(),
            )
        }
        .bind<FddbDiaryGateway>()
}

internal expect fun Scope.produceFddbSessionFile(): String
