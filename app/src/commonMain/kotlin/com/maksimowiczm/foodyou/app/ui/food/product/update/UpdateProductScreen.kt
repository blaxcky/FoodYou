package com.maksimowiczm.foodyou.app.ui.food.product.update

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationEventHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import com.maksimowiczm.foodyou.app.ui.common.component.DiscardDialog
import com.maksimowiczm.foodyou.app.ui.food.product.ProductForm
import com.maksimowiczm.foodyou.app.ui.food.product.rememberProductFormState
import com.maksimowiczm.foodyou.common.compose.extension.LaunchedCollectWithLifecycle
import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import foodyou.app.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun UpdateProductScreen(
    onBack: () -> Unit,
    onUpdate: () -> Unit,
    viewModel: UpdateProductViewModel,
    modifier: Modifier = Modifier,
) {
    val latestOnUpdate by rememberUpdatedState(onUpdate)
    val snackbarHostState = remember { SnackbarHostState() }
    val resyncedMessage = stringResource(Res.string.message_product_resynced)
    val resyncBlockedMessage = stringResource(Res.string.error_fddb_resync_blocked)
    val resyncMissingSourceMessage = stringResource(Res.string.error_fddb_resync_missing_source)
    val resyncFailedMessage = stringResource(Res.string.error_fddb_resync_failed)
    LaunchedCollectWithLifecycle(viewModel.events) { event ->
        when (event) {
            UpdateProductEvent.Updated -> latestOnUpdate()
            UpdateProductEvent.Resynced -> snackbarHostState.showSnackbar(resyncedMessage)
            is UpdateProductEvent.ResyncFailed -> {
                val message =
                    when (event.error) {
                        ResyncFddbProductUiError.Blocked -> resyncBlockedMessage
                        ResyncFddbProductUiError.MissingSourceUrl -> resyncMissingSourceMessage
                        ResyncFddbProductUiError.ProductNotFound,
                        ResyncFddbProductUiError.NotFddbProduct,
                        ResyncFddbProductUiError.NetworkOrParseFailed -> resyncFailedMessage
                    }
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    val product = viewModel.product.collectAsStateWithLifecycle().value
    val isResyncing by viewModel.isResyncing.collectAsStateWithLifecycle()

    if (product == null) {
        // TODO loading state
        return
    } else {
        val productForm =
            key(product.copy(isFavorite = false, isQuickCapture = false)) {
                rememberProductFormState(product)
            }
        val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
        val showFddbResync =
            product.source.type == FoodSource.Type.FDDB && !product.source.url.isNullOrBlank()

        var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
        val handleBack = {
            if (!productForm.isModified) {
                onBack()
            } else {
                showDiscardDialog = true
            }
        }
        NavigationEventHandler(
            state = rememberNavigationEventState(NavigationEventInfo.None),
            isBackEnabled = productForm.isModified,
            onBackCompleted = { showDiscardDialog = true },
        )
        if (showDiscardDialog) {
            DiscardDialog(
                onDismissRequest = { showDiscardDialog = false },
                onDiscard = {
                    showDiscardDialog = false
                    onBack()
                },
            ) {
                Text(stringResource(Res.string.question_discard_changes))
            }
        }

        Scaffold(
            modifier = modifier,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(Res.string.headline_edit_product)) },
                    navigationIcon = { ArrowBackIconButton(handleBack) },
                    actions = {
                        IconButton(onClick = { viewModel.setFavorite(!product.isFavorite) }) {
                            Icon(
                                imageVector =
                                    if (product.isFavorite) {
                                        Icons.Filled.Star
                                    } else {
                                        Icons.Outlined.StarBorder
                                    },
                                contentDescription =
                                    stringResource(
                                        if (product.isFavorite) {
                                            Res.string.action_remove_from_favorites
                                        } else {
                                            Res.string.action_mark_as_favorite
                                        }
                                    ),
                            )
                        }
                        IconButton(
                            onClick = { viewModel.setQuickCapture(!product.isQuickCapture) }
                        ) {
                            Icon(
                                imageVector =
                                    if (product.isQuickCapture) {
                                        Icons.Filled.FlashOn
                                    } else {
                                        Icons.Outlined.FlashOn
                                    },
                                contentDescription =
                                    stringResource(
                                        if (product.isQuickCapture) {
                                            Res.string.action_remove_from_quick_capture
                                        } else {
                                            Res.string.action_add_to_quick_capture
                                        }
                                    ),
                            )
                        }
                        FilledIconButton(
                            onClick = { viewModel.updateProduct(productForm) },
                            enabled = productForm.isValid,
                        ) {
                            Icon(imageVector = Icons.Outlined.Save, contentDescription = null)
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { paddingValues ->
            LazyColumn(
                modifier =
                    Modifier.fillMaxSize()
                        .imePadding()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = paddingValues,
            ) {
                item {
                    ProductForm(
                        state = productForm,
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        showPortions = true,
                        sourceContent =
                            if (showFddbResync) {
                                {
                                    FilledTonalButton(
                                        onClick = viewModel::resyncFddbProduct,
                                        enabled = !isResyncing,
                                        modifier =
                                            Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Sync,
                                            contentDescription = null,
                                        )
                                        Text(
                                            text =
                                                stringResource(
                                                    Res.string.action_resync_fddb_product
                                                ),
                                            modifier =
                                                Modifier.padding(
                                                    start = ButtonDefaults.IconSpacing
                                                ),
                                        )
                                    }
                                }
                            } else {
                                null
                            },
                    )
                }
            }
        }
    }
}
