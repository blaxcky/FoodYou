package com.maksimowiczm.foodyou.app.ui.food.pending

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.food.domain.usecase.ObservePendingProductsUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

internal class PendingProductsViewModel(observePendingProductsUseCase: ObservePendingProductsUseCase) :
    ViewModel() {
    val pendingProducts =
        observePendingProductsUseCase
            .observe()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
