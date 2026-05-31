package com.maksimowiczm.foodyou.app.ui.database.swissfoodcompositiondatabase

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.food.domain.repository.ProductRepository
import com.maksimowiczm.foodyou.importexport.swissfoodcompositiondatabase.domain.DeleteSwissFoodCompositionDatabaseUseCase
import com.maksimowiczm.foodyou.importexport.swissfoodcompositiondatabase.domain.ImportSwissFoodCompositionDatabaseUseCase
import com.maksimowiczm.foodyou.importexport.swissfoodcompositiondatabase.domain.SwissFoodCompositionDatabaseRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class SwissFoodCompositionDatabaseViewModel(
    private val importSwissUseCase: ImportSwissFoodCompositionDatabaseUseCase,
    private val deleteSwissUseCase: DeleteSwissFoodCompositionDatabaseUseCase,
    productRepository: ProductRepository,
) : ViewModel() {

    private val operationState = MutableStateFlow<SwissFoodCompositionDatabaseUiState?>(null)

    val uiState: StateFlow<SwissFoodCompositionDatabaseUiState> =
        combine(
            operationState,
            productRepository.observeProductCountBySource(
                FoodSource.Type.SwissFoodCompositionDatabase
            ),
        ) { operationState, importedProducts ->
            operationState ?: SwissFoodCompositionDatabaseUiState.LanguagePick(importedProducts)
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(2_000),
                initialValue = SwissFoodCompositionDatabaseUiState.LanguagePick(0),
            )

    private val mutex = Mutex()

    fun import(languages: Set<SwissFoodCompositionDatabaseRepository.Language>) {
        if (mutex.isLocked) {
            return
        }

        val size = languages.sumOf { it.size }
        viewModelScope.launch {
            mutex.withLock {
                operationState.value = SwissFoodCompositionDatabaseUiState.Importing(0f)

                importSwissUseCase.import(languages).collectLatest { count ->
                    val progress = count.toFloat() / size
                    operationState.value = SwissFoodCompositionDatabaseUiState.Importing(progress)
                }

                delay(200)
                operationState.value = SwissFoodCompositionDatabaseUiState.ImportFinished
            }
        }
    }

    fun delete() {
        if (mutex.isLocked) {
            return
        }

        viewModelScope.launch {
            mutex.withLock {
                operationState.value = SwissFoodCompositionDatabaseUiState.Deleting
                deleteSwissUseCase.delete()
                delay(200)
                operationState.value = SwissFoodCompositionDatabaseUiState.DeleteFinished
            }
        }
    }

    fun reset() {
        if (!mutex.isLocked) {
            operationState.value = null
        }
    }
}
