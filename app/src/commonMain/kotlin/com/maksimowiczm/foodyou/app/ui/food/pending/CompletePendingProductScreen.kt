package com.maksimowiczm.foodyou.app.ui.food.pending

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.common.form.FormField
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import com.maksimowiczm.foodyou.app.ui.food.product.ProductForm
import com.maksimowiczm.foodyou.app.ui.food.product.ProductFormFieldError
import com.maksimowiczm.foodyou.app.ui.food.product.ProductFormState
import com.maksimowiczm.foodyou.app.ui.food.product.rememberProductFormState
import com.maksimowiczm.foodyou.common.compose.extension.LaunchedCollectWithLifecycle
import com.maksimowiczm.foodyou.common.compose.utility.LocalClipboardManager
import com.maksimowiczm.foodyou.common.compose.utility.formatClipZeros
import com.maksimowiczm.foodyou.common.csv.CsvParser
import foodyou.app.generated.resources.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun CompletePendingProductScreen(
    pendingProductId: Long,
    onBack: () -> Unit,
    onCompleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel =
        koinViewModel<CompletePendingProductViewModel> { parametersOf(pendingProductId) }
    val pendingProduct by viewModel.pendingProduct.collectAsStateWithLifecycle()

    LaunchedCollectWithLifecycle(viewModel.events) { event ->
        when (event) {
            CompletePendingProductEvent.Completed -> onCompleted()
        }
    }

    val product = pendingProduct
    key(product?.id) {
        val formState = rememberProductFormState(initialBarcode = product?.barcode)
        val csvTextFieldState = rememberTextFieldState()
        var csvError by remember { mutableStateOf<PendingProductCsvError?>(null) }
        val baseCsvParser = koinInject<CsvParser>()
        val csvParser = remember(baseCsvParser) { PendingProductCsvParserImpl(baseCsvParser) }
        val coroutineScope = rememberCoroutineScope()
        val clipboardManager = LocalClipboardManager.current
        val sharePhotos = rememberSharePendingProductPhotosAction(
            photoPaths = product?.photoPaths.orEmpty(),
            prompt = PendingProductChatGptPrompt,
        )

        Scaffold(
            modifier = modifier,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(Res.string.action_complete_product)) },
                    navigationIcon = { ArrowBackIconButton(onBack) },
                    actions = {
                        if (product != null) {
                            IconButton(
                                onClick = {
                                    clipboardManager.copy(
                                        label = "Product CSV prompt",
                                        text = PendingProductChatGptPrompt,
                                    )
                                    sharePhotos()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Share,
                                    contentDescription =
                                        stringResource(Res.string.action_analyze_with_chatgpt),
                                )
                            }
                            TakeNutritionPhotoIconButton(
                                onPhotoTaken = { viewModel.addPhoto(product, it) }
                            )
                            IconButton(onClick = { viewModel.delete(product) }) {
                                Icon(imageVector = Icons.Outlined.Delete, contentDescription = null)
                            }
                            FilledIconButton(
                                onClick = { viewModel.createProduct(product, formState) },
                                enabled = formState.isValid,
                            ) {
                                Icon(imageVector = Icons.Outlined.Save, contentDescription = null)
                            }
                        }
                    },
                )
            },
        ) { paddingValues ->
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                if (product != null) {
                    PendingProductPhotoPager(
                        photoPaths = product.photoPaths,
                        modifier =
                            Modifier.fillMaxWidth()
                                .weight(1f, fill = true)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                    )

                    LazyColumn(modifier = Modifier.fillMaxWidth().weight(2f).imePadding()) {
                        item {
                            PendingProductCsvImport(
                                textFieldState = csvTextFieldState,
                                error = csvError,
                                onApply = {
                                    coroutineScope.launch {
                                        when (
                                            val result =
                                                csvParser.parse(
                                                    csvTextFieldState.text.toString()
                                                )
                                        ) {
                                            is PendingProductCsvParseResult.Failure ->
                                                csvError = result.error

                                            is PendingProductCsvParseResult.Success -> {
                                                csvError = null
                                                formState.apply(result.data)
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )

                            ProductForm(
                                state = formState,
                                contentPadding = PaddingValues(horizontal = 16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingProductCsvImport(
    textFieldState: TextFieldState,
    error: PendingProductCsvError?,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            state = textFieldState,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(Res.string.headline_pending_product_csv)) },
            supportingText = {
                if (error == null) {
                    Text(stringResource(Res.string.description_pending_product_csv))
                } else {
                    Text(error.stringResource())
                }
            },
            isError = error != null,
            lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 2, maxHeightInLines = 5),
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            FilledTonalButton(onClick = onApply) {
                Icon(imageVector = Icons.Outlined.Check, contentDescription = null)
                Text(stringResource(Res.string.action_apply))
            }
        }
    }
}

private fun ProductFormState.apply(data: PendingProductCsvData) {
    name.setStringIfNotNull(data.name)
    brand.setNullableStringIfNotNull(data.brand)
    barcode.setNullableStringIfNotNull(data.barcode)
    packageWeight.setIfNotNull(data.packageWeight)
    servingWeight.setIfNotNull(data.servingWeight)
    data.isLiquid?.let { isLiquid = it }
    data.energyKcal?.let {
        autoCalculateEnergy = false
        energy.textFieldState.setTextAndPlaceCursorAtEnd(
            energyFormatter.fromKcal(it).formatClipZeros()
        )
    }
    proteins.setIfNotNull(data.proteins)
    carbohydrates.setIfNotNull(data.carbohydrates)
    fats.setIfNotNull(data.fats)
}

private fun FormField<String, ProductFormFieldError>.setStringIfNotNull(value: String?) {
    value?.let { textFieldState.setTextAndPlaceCursorAtEnd(it) }
}

private fun FormField<String?, Nothing>.setNullableStringIfNotNull(value: String?) {
    value?.let { textFieldState.setTextAndPlaceCursorAtEnd(it) }
}

private fun FormField<Float?, ProductFormFieldError>.setIfNotNull(value: Double?) {
    value?.let { textFieldState.setTextAndPlaceCursorAtEnd(it.formatClipZeros()) }
}

@Composable
private fun PendingProductCsvError.stringResource(): String =
    when (this) {
        PendingProductCsvError.Empty -> stringResource(Res.string.error_pending_product_csv_empty)
        PendingProductCsvError.InvalidHeader ->
            stringResource(Res.string.error_pending_product_csv_header)
        PendingProductCsvError.InvalidDataRowCount ->
            stringResource(Res.string.error_pending_product_csv_data_row_count)
        PendingProductCsvError.InvalidNumber ->
            stringResource(Res.string.error_pending_product_csv_invalid_number)
    }
