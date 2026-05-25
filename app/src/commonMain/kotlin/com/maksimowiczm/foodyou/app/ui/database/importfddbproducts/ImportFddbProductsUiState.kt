package com.maksimowiczm.foodyou.app.ui.database.importfddbproducts

import com.maksimowiczm.foodyou.food.domain.usecase.FddbImportProgress
import com.maksimowiczm.foodyou.food.domain.entity.FddbImportQueueItem

internal data class ImportFddbProductsUiState(
    val isImporting: Boolean = false,
    val queue: List<FddbImportQueueItem> = emptyList(),
    val progress: FddbImportProgress = FddbImportProgress(total = 0, completed = 0, results = emptyList()),
)
