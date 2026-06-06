package com.maksimowiczm.foodyou.app.ui.food.product.update

internal sealed interface UpdateProductEvent {

    data object Updated : UpdateProductEvent

    data object Resynced : UpdateProductEvent

    data class ResyncFailed(val error: ResyncFddbProductUiError) : UpdateProductEvent
}

internal enum class ResyncFddbProductUiError {
    ProductNotFound,
    NotFddbProduct,
    MissingSourceUrl,
    Blocked,
    NetworkOrParseFailed,
}
