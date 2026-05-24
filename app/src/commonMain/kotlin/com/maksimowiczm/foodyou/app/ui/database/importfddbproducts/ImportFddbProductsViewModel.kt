package com.maksimowiczm.foodyou.app.ui.database.importfddbproducts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.food.domain.usecase.ImportFddbProductsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class ImportFddbProductsViewModel(
    private val importFddbProductsUseCase: ImportFddbProductsUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(ImportFddbProductsUiState())
    val uiState = _uiState.asStateFlow()

    fun import(text: String) {
        if (_uiState.value.isImporting) {
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }
            importFddbProductsUseCase.import(text).collect { progress ->
                _uiState.value =
                    ImportFddbProductsUiState(
                        isImporting = !progress.isFinished,
                        progress = progress,
                    )
            }
            _uiState.update { it.copy(isImporting = false) }
        }
    }

    fun countLinks(text: String): Int = importFddbProductsUseCase.extractLinks(text).size
}
