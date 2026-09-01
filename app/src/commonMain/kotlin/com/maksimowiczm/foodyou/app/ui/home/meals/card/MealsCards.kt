package com.maksimowiczm.foodyou.app.ui.home.meals.card

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.food.component.MeasurementPicker
import com.maksimowiczm.foodyou.app.ui.food.component.rememberMeasurementPickerState
import com.maksimowiczm.foodyou.app.ui.home.shared.HomeState
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.fooddiary.domain.entity.MealsCardsLayout
import foodyou.app.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun rememberMealsCardsState(
    homeState: HomeState,
    onAdd: (epochDay: Long, mealId: Long) -> Unit,
    onQuickAdd: (epochDay: Long, mealId: Long) -> Unit,
    onBarcodeScan: (epochDay: Long, mealId: Long) -> Unit,
    onEditEntry: (foodEntryId: Long?, manualEntryId: Long?) -> Unit,
    onEditFood: (FoodId.Product) -> Unit,
): MealsCardsState {
    val viewModel: MealsCardsViewModel = koinViewModel()
    val diaryMeals = viewModel.diaryMeals.collectAsStateWithLifecycle().value
    val layout by viewModel.layout.collectAsStateWithLifecycle()
    val selectedEntries by viewModel.selectedEntries.collectAsStateWithLifecycle()
    val collapsedMealIds by viewModel.collapsedMealIds.collectAsStateWithLifecycle()
    val quickCaptureProducts by viewModel.quickCaptureProducts.collectAsStateWithLifecycle()
    val selectedQuickCaptureMeasurement by
        viewModel.selectedQuickCaptureMeasurement.collectAsStateWithLifecycle()
    var quickCaptureMealId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedQuickCaptureProduct by remember { mutableStateOf<QuickCaptureProductModel?>(null) }

    LaunchedEffect(homeState.selectedDate, viewModel) { viewModel.setDate(homeState.selectedDate) }

    if (quickCaptureMealId != null) {
        QuickCaptureSheet(
            products = quickCaptureProducts,
            onDismissRequest = {
                quickCaptureMealId = null
                selectedQuickCaptureProduct = null
                viewModel.selectQuickCaptureProduct(null)
            },
            onProductClick = { product ->
                selectedQuickCaptureProduct = product
                viewModel.selectQuickCaptureProduct(product)
            },
        )
    }

    val product = selectedQuickCaptureProduct
    val measurement =
        selectedQuickCaptureMeasurement?.takeIf { it.productId == product?.id }?.measurement
    val mealId = quickCaptureMealId
    if (product != null && measurement != null && mealId != null) {
        QuickCaptureAmountDialog(
            product = product,
            suggestedMeasurement = measurement,
            onDismissRequest = {
                selectedQuickCaptureProduct = null
                viewModel.selectQuickCaptureProduct(null)
            },
            onSave = { selectedMeasurement ->
                viewModel.createQuickCaptureEntry(
                    product = product,
                    measurement = selectedMeasurement,
                    mealId = mealId,
                )
                selectedQuickCaptureProduct = null
                quickCaptureMealId = null
                viewModel.selectQuickCaptureProduct(null)
            },
        )
    }

    return MealsCardsState(
        meals = diaryMeals,
        layout = layout,
        selectedEntries = selectedEntries,
        collapsedMealIds = collapsedMealIds,
        onAdd = { mealId -> onAdd(homeState.selectedDate.toEpochDays(), mealId) },
        onQuickAdd = { mealId -> onQuickAdd(homeState.selectedDate.toEpochDays(), mealId) },
        onBarcodeScan = { mealId -> onBarcodeScan(homeState.selectedDate.toEpochDays(), mealId) },
        onEditEntry = { model ->
            val foodEntry = model as? FoodMealEntryModel
            val manualEntry = model as? ManualMealEntryModel
            onEditEntry(foodEntry?.id?.value, manualEntry?.id?.value)
        },
        onEditFood = onEditFood,
        onAddToEntry = viewModel::onAddToEntry,
        onDeleteEntry = viewModel::onDeleteEntry,
        onEnterSelection = viewModel::enterSelection,
        onToggleSelection = viewModel::toggleSelection,
        onToggleMealCollapsed = viewModel::toggleMealCollapsed,
        onClearSelection = viewModel::clearSelection,
        onDeleteSelectedEntries = viewModel::deleteSelectedEntries,
        onMoveSelectedEntries = viewModel::moveSelectedEntries,
        onLongClick = { mealId -> quickCaptureMealId = mealId },
    )
}

@Composable
private fun QuickCaptureSheet(
    products: List<QuickCaptureProductModel>,
    onDismissRequest: () -> Unit,
    onProductClick: (QuickCaptureProductModel) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(2f / 3f)) {
            Text(
                text = stringResource(Res.string.headline_quick_capture),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                style = MaterialTheme.typography.titleLarge,
            )
            if (products.isEmpty()) {
                Text(
                    text = stringResource(Res.string.description_quick_capture_empty),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    items(count = products.size, key = { products[it].id.id }) { index ->
                        val product = products[index]
                        ListItem(
                            headlineContent = { Text(product.name) },
                            supportingContent =
                                product.brand?.takeIf { it.isNotBlank() }?.let { brand ->
                                    { Text(brand) }
                                },
                            modifier = Modifier.clickable { onProductClick(product) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                        if (index != products.lastIndex) {
                            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickCaptureAmountDialog(
    product: QuickCaptureProductModel,
    suggestedMeasurement: Measurement,
    onDismissRequest: () -> Unit,
    onSave: (Measurement) -> Unit,
) {
    val measurementPickerState =
        rememberMeasurementPickerState(
            suggestions = emptyList(),
            totalWeight = product.totalWeight,
            servingWeight = product.servingWeight,
            isLiquid = product.isLiquid,
            possibleTypes = product.possibleMeasurementTypes,
            selectedMeasurement = suggestedMeasurement,
        )

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = { onSave(measurementPickerState.measurement) },
                enabled = measurementPickerState.inputField.error == null,
            ) {
                Text(stringResource(Res.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
        title = { Text(product.headline) },
        text = {
            Column {
                MeasurementPicker(
                    state = measurementPickerState,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

internal class MealsCardsState(
    val meals: List<MealModel>?,
    val layout: MealsCardsLayout,
    val selectedEntries: Set<MealEntrySelectionKey>,
    val collapsedMealIds: Set<Long>,
    val onAdd: (mealId: Long) -> Unit,
    val onQuickAdd: (mealId: Long) -> Unit,
    val onBarcodeScan: (mealId: Long) -> Unit,
    val onEditEntry: (MealEntryModel) -> Unit,
    val onEditFood: (FoodId.Product) -> Unit,
    val onAddToEntry: (MealEntryModel, Double) -> Unit,
    val onDeleteEntry: (MealEntryModel) -> Unit,
    val onEnterSelection: (MealEntryModel) -> Unit,
    val onToggleSelection: (MealEntryModel) -> Unit,
    val onToggleMealCollapsed: (mealId: Long) -> Unit,
    val onClearSelection: () -> Unit,
    val onDeleteSelectedEntries: () -> Unit,
    val onMoveSelectedEntries: (mealId: Long) -> Unit,
    val onLongClick: (mealId: Long) -> Unit,
) {
    val isSelectionMode: Boolean
        get() = selectedEntries.isNotEmpty()
}

internal fun LazyListScope.mealsCards(
    state: MealsCardsState,
    contentPadding: PaddingValues,
    bottomSpacing: Dp,
    modifier: Modifier = Modifier,
) {
    when (state.layout) {
        MealsCardsLayout.Horizontal ->
            item(key = "meals-horizontal", contentType = "meals-horizontal") {
                HorizontalMealsCards(
                    meals = state.meals,
                    onAdd = state.onAdd,
                    onQuickAdd = state.onQuickAdd,
                    onBarcodeScan = state.onBarcodeScan,
                    onEditEntry = state.onEditEntry,
                    onEditFood = state.onEditFood,
                    onAddToEntry = state.onAddToEntry,
                    onDeleteEntry = state.onDeleteEntry,
                    selectedEntries = state.selectedEntries,
                    collapsedMealIds = state.collapsedMealIds,
                    isSelectionMode = state.isSelectionMode,
                    onEnterSelection = state.onEnterSelection,
                    onToggleSelection = state.onToggleSelection,
                    onToggleMealCollapsed = state.onToggleMealCollapsed,
                    onLongClick = state.onLongClick,
                    contentPadding = contentPadding,
                    modifier = modifier.padding(bottom = bottomSpacing),
                )
            }

        MealsCardsLayout.Vertical ->
            if (state.meals == null) {
                items(
                    count = 4,
                    key = { index -> "meal-skeleton-$index" },
                    contentType = { "meal-skeleton" },
                ) { index ->
                    MealCardSkeleton(
                        modifier =
                            modifier.padding(contentPadding).padding(
                                bottom = if (index == 3) bottomSpacing else DefaultMealCardSpacing
                            )
                    )
                }
            } else {
                val meals = requireNotNull(state.meals)
                items(
                    items = meals,
                    key = { meal -> "meal-${meal.id}" },
                    contentType = { meal ->
                        if (meal.foods.isEmpty()) {
                            "meal-empty"
                        } else {
                            "meal-foods"
                        }
                    },
                ) { meal ->
                    MealCard(
                        meal = meal,
                        onAddFood = { state.onAdd(meal.id) },
                        onQuickAdd = { state.onQuickAdd(meal.id) },
                        onBarcodeScan = { state.onBarcodeScan(meal.id) },
                        onEditEntry = state.onEditEntry,
                        onEditFood = state.onEditFood,
                        onAddToEntry = state.onAddToEntry,
                        onDeleteEntry = state.onDeleteEntry,
                        selectedEntries = state.selectedEntries,
                        isCollapsed = meal.id in state.collapsedMealIds,
                        isSelectionMode = state.isSelectionMode,
                        onEnterSelection = state.onEnterSelection,
                        onToggleSelection = state.onToggleSelection,
                        onToggleCollapsed = { state.onToggleMealCollapsed(meal.id) },
                        onLongClick = { state.onLongClick(meal.id) },
                        modifier =
                            modifier.padding(contentPadding).padding(
                                bottom =
                                    if (meal == meals.last()) bottomSpacing
                                    else DefaultMealCardSpacing
                            ),
                    )
                }
            }
    }
}

private val DefaultMealCardSpacing = 12.dp
