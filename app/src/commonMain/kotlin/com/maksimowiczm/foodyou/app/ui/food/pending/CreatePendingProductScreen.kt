package com.maksimowiczm.foodyou.app.ui.food.pending

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
    var scannerVisible by rememberSaveable { mutableStateOf(false) }
    var barcodeStepFinished by rememberSaveable { mutableStateOf(false) }
    val photoPaths = remember { mutableStateListOf<String>() }

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
            barcodeStepFinished = true
        },
        onClose = {
            scannerVisible = false
            barcodeStepFinished = true
        },
    )

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.action_create_pending_product)) },
                navigationIcon = { ArrowBackIconButton(onBack) },
                actions = {
                    FilledIconButton(
                        onClick = { viewModel.create(barcode, photoPaths.toList()) },
                        enabled = barcodeStepFinished && photoPaths.isNotEmpty(),
                    ) {
                        Icon(imageVector = Icons.Outlined.Save, contentDescription = null)
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val scannedBarcode = barcode
            if (!barcodeStepFinished) {
                Text(stringResource(Res.string.action_scan_barcode))
                FilledTonalButton(
                    onClick = { scannerVisible = true },
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    Icon(imageVector = Icons.Outlined.QrCodeScanner, contentDescription = null)
                    Text(
                        text = stringResource(Res.string.action_scan_barcode),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                OutlinedButton(
                    onClick = {
                        scannerVisible = false
                        barcodeStepFinished = true
                    },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text(stringResource(Res.string.action_skip_barcode))
                }
            } else {
                Text(scannedBarcode ?: stringResource(Res.string.neutral_no_barcode))
                Text(
                    text =
                        stringResource(
                            Res.string.neutral_pending_product_photo_count,
                            photoPaths.size,
                        ),
                    modifier = Modifier.padding(top = 8.dp),
                )
                TakeNutritionPhotoButton(
                    onPhotoTaken = { photoPaths += it },
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}
