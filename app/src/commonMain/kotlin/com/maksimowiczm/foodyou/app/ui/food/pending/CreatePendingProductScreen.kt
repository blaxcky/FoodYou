package com.maksimowiczm.foodyou.app.ui.food.pending

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
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
    val photoPaths = remember { mutableStateListOf<String>() }

    LaunchedCollectWithLifecycle(viewModel.events) { event ->
        when (event) {
            is CreatePendingProductEvent.PendingProductReady -> onPendingProduct(event.id)
            is CreatePendingProductEvent.ProductExists -> onExistingProduct(event.id)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.action_create_pending_product)) },
                navigationIcon = { ArrowBackIconButton(onBack) },
                actions = {
                    FilledIconButton(
                        onClick = {
                            viewModel.create(barcode = null, photoPaths = photoPaths.toList())
                        },
                        enabled = photoPaths.isNotEmpty(),
                    ) {
                        Icon(imageVector = Icons.Outlined.Save, contentDescription = null)
                    }
                },
            )
        },
    ) { paddingValues ->
        PendingProductPhotoCapture(
            photoCount = photoPaths.size,
            onPhotoTaken = { photoPaths += it },
            modifier = Modifier.fillMaxSize().padding(paddingValues),
        )
    }
}
