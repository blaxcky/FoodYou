package com.maksimowiczm.foodyou.app.ui.database.importfddbproducts

import com.maksimowiczm.foodyou.food.domain.usecase.FddbImportProgress

internal data class ImportFddbProductsUiState(
    val isImporting: Boolean = false,
    val progress: FddbImportProgress = FddbImportProgress(total = 0, completed = 0, results = emptyList()),
)
