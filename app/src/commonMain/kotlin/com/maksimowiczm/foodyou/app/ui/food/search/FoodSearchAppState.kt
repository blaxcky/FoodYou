package com.maksimowiczm.foodyou.app.ui.food.search

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable

@Composable
fun rememberFoodSearchAppState(
    searchTextFieldState: TextFieldState = rememberTextFieldState(),
    showBarcodeScanner: Boolean = false,
): FoodSearchAppState {
    val showBarcodeScanner =
        rememberSaveable(showBarcodeScanner) { mutableStateOf(showBarcodeScanner) }

    val listStates = rememberListStates()

    return remember(searchTextFieldState, showBarcodeScanner, listStates) {
        FoodSearchAppState(
            searchTextFieldState = searchTextFieldState,
            showBarcodeScannerState = showBarcodeScanner,
            listStates = listStates,
        )
    }
}

@Stable
class FoodSearchAppState(
    val searchTextFieldState: TextFieldState,
    showBarcodeScannerState: MutableState<Boolean>,
    val listStates: ListStates,
) {
    var showBarcodeScanner by showBarcodeScannerState
}

class ListStates(
    val all: LazyListState,
    val recent: LazyListState,
    val yourFood: LazyListState,
    val openFoodFacts: LazyListState,
    val usda: LazyListState,
    val swiss: LazyListState,
    val fddb: LazyListState,
)

@Composable
private fun rememberListStates(): ListStates {
    val all = rememberLazyListState()
    val recent = rememberLazyListState()
    val yourFood = rememberLazyListState()
    val openFoodFacts = rememberLazyListState()
    val usda = rememberLazyListState()
    val swiss = rememberLazyListState()
    val fddb = rememberLazyListState()

    return remember(all, recent, yourFood, openFoodFacts, usda, swiss, fddb) {
        ListStates(
            all = all,
            recent = recent,
            yourFood = yourFood,
            openFoodFacts = openFoodFacts,
            usda = usda,
            swiss = swiss,
            fddb = fddb,
        )
    }
}
