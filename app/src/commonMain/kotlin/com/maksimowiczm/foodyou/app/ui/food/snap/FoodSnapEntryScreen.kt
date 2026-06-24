package com.maksimowiczm.foodyou.app.ui.food.snap

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import com.maksimowiczm.foodyou.app.ui.food.pending.PendingProductPhotoPager
import com.maksimowiczm.foodyou.app.ui.food.search.FoodSearchApp
import com.maksimowiczm.foodyou.app.ui.food.search.FoodSearchLayout
import com.maksimowiczm.foodyou.common.compose.extension.LaunchedCollectWithLifecycle
import com.maksimowiczm.foodyou.food.domain.entity.Food
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.entity.FoodSnapEntry
import com.maksimowiczm.foodyou.food.domain.usecase.ObserveFoodSnapEntriesUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.ObserveFoodUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.ProcessFoodSnapEntryResult
import com.maksimowiczm.foodyou.food.domain.usecase.ProcessFoodSnapEntryUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun FoodSnapEntryScreen(
    entryId: Long,
    onBack: () -> Unit,
    onCompleted: () -> Unit,
    onUpdateUsdaApiKey: () -> Unit,
    onUpdateOpenFoodFactsCredentials: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: FoodSnapEntryViewModel = koinViewModel { parametersOf(entryId) }
    val entry by viewModel.entry.collectAsStateWithLifecycle()
    val selectedFood by viewModel.selectedFood.collectAsStateWithLifecycle()

    LaunchedCollectWithLifecycle(viewModel.events) { onCompleted() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("FoodSnap") },
                navigationIcon = { ArrowBackIconButton(onBack) },
            )
        },
    ) { paddingValues ->
        val currentEntry = entry
        if (currentEntry != null) {
            Column(Modifier.fillMaxSize().padding(paddingValues)) {
                PendingProductPhotoPager(
                    photoPaths = listOf(currentEntry.photoPath),
                    photoDirectory = "food-snap-photos",
                    modifier =
                        Modifier.fillMaxWidth().weight(1f).padding(start = 16.dp, top = 8.dp, end = 16.dp),
                )

                key(selectedFood?.id) {
                    val food = selectedFood
                    if (food == null) {
                        FoodSearchApp(
                            onFoodClick = { model, _ -> viewModel.selectFood(model.id) },
                            onUpdateUsdaApiKey = onUpdateUsdaApiKey,
                            onUpdateOpenFoodFactsCredentials = onUpdateOpenFoodFactsCredentials,
                            modifier = Modifier.fillMaxWidth().weight(2f),
                            layout = FoodSearchLayout.Stacked,
                        )
                    } else {
                        FoodSnapEntryForm(
                            food = food,
                            initialWeight = currentEntry.weightInGrams ?: 100.0,
                            onSearchAgain = viewModel::clearFood,
                            onSave = { weight -> viewModel.process(currentEntry, food, weight) },
                            modifier = Modifier.fillMaxWidth().weight(2f).imePadding(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FoodSnapEntryForm(
    food: Food,
    initialWeight: Double,
    onSearchAgain: () -> Unit,
    onSave: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val initialWeightText = initialWeight.formatWeight()
    var weightFieldValue by
        remember {
            mutableStateOf(
                TextFieldValue(
                    text = initialWeightText,
                    selection = TextRange(0, initialWeightText.length),
                )
            )
        }
    var weightFieldFocused by remember { mutableStateOf(false) }
    val weightText = weightFieldValue.text
    val weight = weightText.replace(',', '.').toDoubleOrNull()
    val validWeight = weight?.isFinite() == true && weight > 0.0

    Column(modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(food.headline)
        TextButton(onClick = onSearchAgain) { Text("Anderes Lebensmittel suchen") }
        OutlinedTextField(
            value = weightFieldValue,
            onValueChange = { weightFieldValue = it },
            modifier =
                Modifier.fillMaxWidth().onFocusChanged { focusState ->
                    if (focusState.isFocused && !weightFieldFocused) {
                        weightFieldValue =
                            weightFieldValue.copy(selection = TextRange(0, weightText.length))
                    }
                    weightFieldFocused = focusState.isFocused
                },
            label = { Text("Menge in Gramm") },
            suffix = { Text("g") },
            isError = weightText.isNotEmpty() && !validWeight,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            supportingText = { if (!validWeight) Text("Eine positive Menge eingeben") },
        )
        Button(
            onClick = { onSave(requireNotNull(weight)) },
            enabled = validWeight,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.Save, contentDescription = null)
            Text("Speichern")
        }
    }
}

private fun Double.formatWeight(): String = if (this % 1.0 == 0.0) toInt().toString() else toString()

internal class FoodSnapEntryViewModel(
    entryId: Long,
    observeEntries: ObserveFoodSnapEntriesUseCase,
    observeFood: ObserveFoodUseCase,
    private val processEntry: ProcessFoodSnapEntryUseCase,
) : ViewModel() {
    private var initialized = false
    private val selectedFoodId = MutableStateFlow<FoodId?>(null)

    val entry =
        observeEntries.observe(entryId)
            .onEach { observed ->
                if (!initialized && observed?.isProcessed == true) {
                    selectedFoodId.value = observed.foodId
                    initialized = true
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val selectedFood =
        selectedFoodId
            .flatMapLatest { id -> if (id == null) flowOf(null) else observeFood.observe(id) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val events = MutableSharedFlow<FoodSnapEntryEvent>()

    fun selectFood(id: FoodId) {
        initialized = true
        selectedFoodId.value = id
    }

    fun clearFood() {
        initialized = true
        selectedFoodId.value = null
    }

    fun process(entry: FoodSnapEntry, food: Food, weightInGrams: Double) {
        viewModelScope.launch {
            if (processEntry.process(entry, food, weightInGrams) == ProcessFoodSnapEntryResult.Processed) {
                events.emit(FoodSnapEntryEvent.Completed)
            }
        }
    }
}

internal enum class FoodSnapEntryEvent { Completed }
