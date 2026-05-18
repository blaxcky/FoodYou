package com.maksimowiczm.foodyou.app.ui.food.product

import com.maksimowiczm.foodyou.app.ui.food.product.create.CreateProductViewModel
import com.maksimowiczm.foodyou.app.ui.food.product.download.DownloadProductHolder
import com.maksimowiczm.foodyou.app.ui.food.product.download.DownloadProductViewModel
import com.maksimowiczm.foodyou.app.ui.food.product.update.UpdateProductViewModel
import com.maksimowiczm.foodyou.app.ui.food.pending.CompletePendingProductViewModel
import com.maksimowiczm.foodyou.app.ui.food.pending.CreatePendingProductViewModel
import com.maksimowiczm.foodyou.app.ui.food.pending.PendingProductsViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf

fun Module.foodProduct() {
    viewModelOf(::CreatePendingProductViewModel)
    viewModelOf(::CreateProductViewModel)
    viewModel { (pendingProductId: Long) ->
        CompletePendingProductViewModel(pendingProductId, get(), get(), get(), get(), get())
    }
    viewModelOf(::PendingProductsViewModel)
    viewModelOf(::UpdateProductViewModel)
    viewModel { (text: String?, holder: DownloadProductHolder) ->
        DownloadProductViewModel(text, get(), holder)
    }
    viewModelOf(::DownloadProductHolder)
}
