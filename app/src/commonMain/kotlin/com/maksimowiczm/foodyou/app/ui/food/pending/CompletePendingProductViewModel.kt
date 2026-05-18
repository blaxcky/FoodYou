package com.maksimowiczm.foodyou.app.ui.food.pending

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.app.ui.food.product.ProductFormState
import com.maksimowiczm.foodyou.app.ui.food.product.nutritionFacts
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.result.onError
import com.maksimowiczm.foodyou.common.result.onSuccess
import com.maksimowiczm.foodyou.food.domain.entity.FoodHistory
import com.maksimowiczm.foodyou.food.domain.entity.PendingProduct
import com.maksimowiczm.foodyou.food.domain.usecase.AddPendingProductPhotoUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.CompletePendingProductUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.CreateProductUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.DeletePendingProductUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.ObservePendingProductUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal sealed interface CompletePendingProductEvent {
    data object Completed : CompletePendingProductEvent
}

internal class CompletePendingProductViewModel(
    pendingProductId: Long,
    observePendingProductUseCase: ObservePendingProductUseCase,
    private val createProductUseCase: CreateProductUseCase,
    private val completePendingProductUseCase: CompletePendingProductUseCase,
    private val deletePendingProductUseCase: DeletePendingProductUseCase,
    private val addPendingProductPhotoUseCase: AddPendingProductPhotoUseCase,
    private val dateProvider: DateProvider,
) : ViewModel() {
    val pendingProduct =
        observePendingProductUseCase
            .observe(pendingProductId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val eventBus = Channel<CompletePendingProductEvent>()
    val events = eventBus.receiveAsFlow()

    fun createProduct(pendingProduct: PendingProduct, form: ProductFormState) {
        if (!form.isValid) return

        val multiplier =
            when (form.measurement) {
                is Measurement.ImmutableMeasurement -> 1f
                is Measurement.Package -> form.packageWeight.value?.let { 1 / it * 100 }
                is Measurement.Serving -> form.servingWeight.value?.let { 1 / it * 100 }
            } ?: return

        viewModelScope.launch {
            createProductUseCase
                .create(
                    name = form.name.value,
                    brand = form.brand.value,
                    barcode = form.barcode.value,
                    note = form.note.value,
                    isLiquid = form.isLiquid,
                    packageWeight = form.packageWeight.value?.toDouble(),
                    servingWeight = form.servingWeight.value?.toDouble(),
                    source = FoodSource(type = form.sourceType, url = form.sourceUrl.value),
                    nutritionFacts = form.nutritionFacts(multiplier),
                    history = FoodHistory.Created(dateProvider.nowInstant()),
                )
                .onSuccess {
                    completePendingProductUseCase.complete(pendingProduct)
                    eventBus.send(CompletePendingProductEvent.Completed)
                }
                .onError { error("Failed to create product: $it") }
        }
    }

    fun delete(pendingProduct: PendingProduct) {
        viewModelScope.launch {
            deletePendingProductUseCase.delete(pendingProduct)
            eventBus.send(CompletePendingProductEvent.Completed)
        }
    }

    fun addPhoto(pendingProduct: PendingProduct, photoPath: String) {
        viewModelScope.launch {
            addPendingProductPhotoUseCase.addPhoto(pendingProduct, photoPath)
        }
    }
}
