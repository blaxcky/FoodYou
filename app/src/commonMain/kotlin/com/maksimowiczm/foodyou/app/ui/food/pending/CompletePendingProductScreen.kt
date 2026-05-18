package com.maksimowiczm.foodyou.app.ui.food.pending

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import com.maksimowiczm.foodyou.app.ui.food.product.ProductForm
import com.maksimowiczm.foodyou.app.ui.food.product.rememberProductFormState
import com.maksimowiczm.foodyou.common.compose.extension.LaunchedCollectWithLifecycle
import foodyou.app.generated.resources.*
import org.jetbrains.compose.resources.stringResource
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

        Scaffold(
            modifier = modifier,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(Res.string.action_complete_product)) },
                    navigationIcon = { ArrowBackIconButton(onBack) },
                    actions = {
                        if (product != null) {
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
