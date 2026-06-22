package com.maksimowiczm.foodyou.app.ui.database.exportcsvproducts

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.maksimowiczm.foodyou.common.domain.food.FoodSource

@Composable
expect fun ExportCsvProductsScreen(
    onBack: () -> Unit,
    onFinish: () -> Unit,
    source: FoodSource.Type? = null,
    modifier: Modifier = Modifier,
)
