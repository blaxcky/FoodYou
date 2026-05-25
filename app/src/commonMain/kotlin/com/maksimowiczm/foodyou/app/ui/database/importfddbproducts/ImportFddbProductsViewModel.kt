package com.maksimowiczm.foodyou.app.ui.database.importfddbproducts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.food.domain.usecase.AddFddbLinksToQueueUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.DeleteFddbImportQueueItemUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.ImportFddbProductsUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.ObserveFddbImportQueueUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class ImportFddbProductsViewModel(
    private val addFddbLinksToQueueUseCase: AddFddbLinksToQueueUseCase,
    private val deleteFddbImportQueueItemUseCase: DeleteFddbImportQueueItemUseCase,
    private val importFddbProductsUseCase: ImportFddbProductsUseCase,
    observeFddbImportQueueUseCase: ObserveFddbImportQueueUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ImportFddbProductsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeFddbImportQueueUseCase().collect { queue ->
                _uiState.update { it.copy(queue = queue) }
            }
        }
    }

    fun addLinks(text: String) {
        if (_uiState.value.isImporting) {
            return
        }

        viewModelScope.launch {
            addFddbLinksToQueueUseCase(text)
        }
    }

    fun importQueue() {
        if (_uiState.value.isImporting) {
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }
            importFddbProductsUseCase.importQueue().collect { progress ->
                _uiState.update {
                    it.copy(
                        isImporting = !progress.isFinished,
                        progress = progress,
                    )
                }
            }
            _uiState.update { it.copy(isImporting = false) }
        }
    }

    fun deleteQueueItem(id: Long) {
        if (_uiState.value.isImporting) {
            return
        }

        viewModelScope.launch {
            deleteFddbImportQueueItemUseCase(id)
        }
    }

    fun countLinks(text: String): Int = addFddbLinksToQueueUseCase.countLinks(text)
}
