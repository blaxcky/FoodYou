package com.maksimowiczm.foodyou.app.ui

data class FoodYouLaunchRequest(val action: FoodYouLaunchAction, val nonce: Long)

enum class FoodYouLaunchAction {
    ScanBarcode
}
