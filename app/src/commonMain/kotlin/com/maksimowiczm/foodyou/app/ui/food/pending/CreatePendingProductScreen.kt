package com.maksimowiczm.foodyou.app.ui.food.pending

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import com.maksimowiczm.foodyou.app.ui.common.component.FullScreenCameraBarcodeScanner
import com.maksimowiczm.foodyou.common.compose.extension.LaunchedCollectWithLifecycle
import foodyou.app.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CreatePendingProductScreen(
    onBack: () -> Unit,
    onPendingProduct: (Long) -> Unit,
    onExistingProduct: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: CreatePendingProductViewModel = koinViewModel()
    var barcode by rememberSaveable { mutableStateOf<String?>(null) }
    var scannerVisible by rememberSaveable { mutableStateOf(true) }

    LaunchedCollectWithLifecycle(viewModel.events) { event ->
        when (event) {
            is CreatePendingProductEvent.PendingProductReady -> onPendingProduct(event.id)
            is CreatePendingProductEvent.ProductExists -> onExistingProduct(event.id)
        }
    }

    FullScreenCameraBarcodeScanner(
        visible = scannerVisible,
        onBarcodeScan = {
            barcode = it
            scannerVisible = false
        },
        onClose = onBack,
    )

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.action_create_pending_product)) },
                navigationIcon = { ArrowBackIconButton(onBack) },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val scannedBarcode = barcode
            if (scannedBarcode == null) {
                Text(stringResource(Res.string.action_scan_barcode))
            } else {
                Text(scannedBarcode)
                TakeNutritionPhotoButton(
                    onPhotoTaken = { viewModel.create(scannedBarcode, it) },
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}
