package com.maksimowiczm.foodyou.app.ui.food.snap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.food.domain.entity.Food
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.entity.FoodSnapEntry
import com.maksimowiczm.foodyou.food.domain.usecase.CompleteFoodSnapEntryResult
import com.maksimowiczm.foodyou.food.domain.usecase.CompleteFoodSnapEntryUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.DeleteFoodSnapEntryUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.ObserveFoodSnapEntriesUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.ObserveFoodUseCase
import com.maksimowiczm.foodyou.fooddiary.domain.entity.Meal
import com.maksimowiczm.foodyou.fooddiary.domain.repository.MealRepository
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
    val meals by viewModel.meals.collectAsStateWithLifecycle()
    val selectedMealId by viewModel.selectedMealId.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedCollectWithLifecycle(viewModel.events) { onCompleted() }

    val currentEntry = entry
    if (showDeleteDialog && currentEntry != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.delete(currentEntry)
                    }
                ) {
                    Text("Löschen")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Abbrechen") }
            },
            icon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
            title = { Text("FoodSnap löschen") },
            text = { Text("Dieses FoodSnap-Foto wird endgültig gelöscht.") },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("FoodSnap") },
                navigationIcon = { ArrowBackIconButton(onBack) },
                actions = {
                    if (currentEntry != null) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "FoodSnap löschen")
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
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
                            meals = meals,
                            selectedMealId = selectedMealId,
                            errorMessage = errorMessage,
                            onSearchAgain = viewModel::clearFood,
                            onMealSelected = viewModel::selectMeal,
                            onSave = { weight, mealId -> viewModel.complete(currentEntry, food, weight, mealId) },
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
    meals: List<Meal>,
    selectedMealId: Long?,
    errorMessage: String?,
    onSearchAgain: () -> Unit,
    onMealSelected: (Long) -> Unit,
    onSave: (Double, Long) -> Unit,
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
    val unit = if (food.isLiquid) "ml" else "g"
    val selectedMealExists = selectedMealId != null && meals.any { it.id == selectedMealId }

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
            label = { Text(if (food.isLiquid) "Menge in Milliliter" else "Menge in Gramm") },
            suffix = { Text(unit) },
            isError = weightText.isNotEmpty() && !validWeight,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            supportingText = { if (!validWeight) Text("Eine positive Menge eingeben") },
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        ) {
            meals.forEach { meal ->
                InputChip(
                    selected = meal.id == selectedMealId,
                    onClick = { onMealSelected(meal.id) },
                    label = { Text(meal.name) },
                )
            }
        }
        if (meals.isEmpty()) {
            Text("Keine Mahlzeiten vorhanden")
        } else if (selectedMealId == null) {
            Text("Mahlzeit auswählen")
        }
        if (errorMessage != null) {
            Text(errorMessage)
        }
        Button(
            onClick = { onSave(requireNotNull(weight), requireNotNull(selectedMealId)) },
            enabled = validWeight && selectedMealExists,
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
    mealRepository: MealRepository,
    dateProvider: DateProvider,
    private val completeEntry: CompleteFoodSnapEntryUseCase,
    private val deleteEntry: DeleteFoodSnapEntryUseCase,
) : ViewModel() {
    private var initialized = false
    private val selectedFoodId = MutableStateFlow<FoodId?>(null)
    val selectedMealId = MutableStateFlow<Long?>(null)
    val errorMessage = MutableStateFlow<String?>(null)

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

    val meals =
        mealRepository.observeMeals()
            .onEach { meals ->
                val currentMealId = selectedMealId.value
                if (currentMealId != null && meals.none { it.id == currentMealId }) {
                    selectedMealId.value = null
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val today =
        dateProvider.observeDate()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), dateProvider.now().date)

    val events = MutableSharedFlow<FoodSnapEntryEvent>()

    fun selectFood(id: FoodId) {
        initialized = true
        selectedFoodId.value = id
        errorMessage.value = null
    }

    fun clearFood() {
        initialized = true
        selectedFoodId.value = null
        errorMessage.value = null
    }

    fun selectMeal(id: Long) {
        selectedMealId.value = id
        errorMessage.value = null
    }

    fun complete(entry: FoodSnapEntry, food: Food, weight: Double, mealId: Long) {
        viewModelScope.launch {
            when (completeEntry.complete(entry, food, weight, mealId, today.value)) {
                CompleteFoodSnapEntryResult.Completed -> events.emit(FoodSnapEntryEvent.Completed)
                CompleteFoodSnapEntryResult.InvalidWeight -> errorMessage.value = "Eine positive Menge eingeben"
                CompleteFoodSnapEntryResult.MissingFood -> errorMessage.value = "Lebensmittel erneut auswählen"
                CompleteFoodSnapEntryResult.MissingMeal -> errorMessage.value = "Mahlzeit erneut auswählen"
                CompleteFoodSnapEntryResult.MissingEntry -> errorMessage.value = "FoodSnap wurde bereits gelöscht"
                CompleteFoodSnapEntryResult.DiaryEntryFailed -> errorMessage.value = "Eintrag konnte nicht gespeichert werden"
            }
        }
    }

    fun delete(entry: FoodSnapEntry) {
        viewModelScope.launch {
            deleteEntry.delete(entry)
            events.emit(FoodSnapEntryEvent.Deleted)
        }
    }
}

internal enum class FoodSnapEntryEvent { Completed, Deleted }
