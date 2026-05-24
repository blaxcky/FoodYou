package com.maksimowiczm.foodyou.app.ui.database.importfddbproducts

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf

internal fun Module.importFddbProductsModule() {
    viewModelOf(::ImportFddbProductsViewModel)
}
