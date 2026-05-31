package com.maksimowiczm.foodyou.app.ui.database.swissfoodcompositiondatabase

internal sealed interface SwissFoodCompositionDatabaseUiState {
    data class LanguagePick(val importedProducts: Int) : SwissFoodCompositionDatabaseUiState

    data class Importing(val progress: Float) : SwissFoodCompositionDatabaseUiState

    data object Deleting : SwissFoodCompositionDatabaseUiState

    data object ImportFinished : SwissFoodCompositionDatabaseUiState

    data object DeleteFinished : SwissFoodCompositionDatabaseUiState
}
