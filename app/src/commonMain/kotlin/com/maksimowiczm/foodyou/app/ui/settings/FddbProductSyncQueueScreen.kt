package com.maksimowiczm.foodyou.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import com.maksimowiczm.foodyou.common.compose.utility.LocalDateFormatter
import com.maksimowiczm.foodyou.food.domain.entity.FddbProductSyncQueueItem
import foodyou.app.generated.resources.Res
import foodyou.app.generated.resources.headline_fddb_product_sync_queue
import foodyou.app.generated.resources.neutral_fddb_product_sync_empty
import foodyou.app.generated.resources.neutral_fddb_product_sync_error
import foodyou.app.generated.resources.neutral_fddb_product_sync_last_attempt
import foodyou.app.generated.resources.neutral_fddb_product_sync_last_success
import foodyou.app.generated.resources.neutral_fddb_product_sync_never
import foodyou.app.generated.resources.neutral_fddb_product_sync_next
import foodyou.app.generated.resources.neutral_fddb_product_sync_progress
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun FddbProductSyncQueueScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FddbProductSyncQueueViewModel = koinViewModel(),
) {
    val model = viewModel.model.collectAsStateWithLifecycle().value
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier,
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(Res.string.headline_fddb_product_sync_queue)) },
                navigationIcon = { ArrowBackIconButton(onBack) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = paddingValues,
        ) {
            item {
                ListItem(
                    headlineContent = {
                        Text(
                            stringResource(
                                Res.string.neutral_fddb_product_sync_progress,
                                model.progress,
                            )
                        )
                    }
                )
            }

            if (model.queue.isEmpty()) {
                item {
                    ListItem(
                        headlineContent = {
                            Text(stringResource(Res.string.neutral_fddb_product_sync_empty))
                        }
                    )
                }
            } else {
                itemsIndexed(model.queue, key = { _, item -> item.productId.id }) { index, item ->
                    FddbProductSyncQueueListItem(item = item, isNext = index < 2)
                }
            }
        }
    }
}

@Composable
private fun FddbProductSyncQueueListItem(item: FddbProductSyncQueueItem, isNext: Boolean) {
    ListItem(
        headlineContent = { Text(item.headline) },
        supportingContent = {
            Column(
                modifier = Modifier.padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(item.lastSuccessText(), style = MaterialTheme.typography.bodyMedium)
                item.lastAttemptAt?.let {
                    Text(
                        stringResource(
                            Res.string.neutral_fddb_product_sync_last_attempt,
                            it.formatDate(),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                item.lastError?.let {
                    Text(
                        stringResource(Res.string.neutral_fddb_product_sync_error, it),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        trailingContent = {
            if (isNext) {
                AssistChip(
                    onClick = {},
                    label = { Text(stringResource(Res.string.neutral_fddb_product_sync_next)) },
                )
            }
        },
    )
}

private val FddbProductSyncQueueItem.headline: String
    get() = listOfNotNull(name, brand).joinToString(" · ")

@Composable
private fun FddbProductSyncQueueItem.lastSuccessText(): String =
    lastSyncedAt?.let {
        stringResource(Res.string.neutral_fddb_product_sync_last_success, it.formatDate())
    } ?: stringResource(Res.string.neutral_fddb_product_sync_never)

@Composable
private fun Instant.formatDate(): String =
    LocalDateFormatter.current.formatDateShort(
        toLocalDateTime(TimeZone.currentSystemDefault()).date
    )
