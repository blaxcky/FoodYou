package com.maksimowiczm.foodyou.app.ui.food.snap

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import com.maksimowiczm.foodyou.app.ui.food.pending.PendingProductPhoto
import com.maksimowiczm.foodyou.app.ui.food.pending.PendingProductPhotoCapture
import com.maksimowiczm.foodyou.food.domain.usecase.CaptureFoodSnapPhotoUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.ObserveFoodSnapEntriesUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun FoodSnapInboxScreen(
    onBack: () -> Unit,
    onEntry: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: FoodSnapInboxViewModel = koinViewModel()
    val entries = viewModel.entries.collectAsStateWithLifecycle().value
    val cameraOpen = viewModel.cameraOpen.collectAsStateWithLifecycle().value
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("FoodSnap") },
                navigationIcon = {
                    ArrowBackIconButton(if (cameraOpen) viewModel::closeCamera else onBack)
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            if (!cameraOpen) {
                FloatingActionButton(onClick = viewModel::openCamera) {
                    Icon(Icons.Outlined.PhotoCamera, contentDescription = "Foto aufnehmen")
                }
            }
        },
    ) { padding ->
        if (cameraOpen) {
            PendingProductPhotoCapture(
                photoCount = entries.size,
                photoDirectory = "food-snap-photos",
                onPhotoTaken = viewModel::onPhotoTaken,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Noch keine FoodSnap-Fotos", style = MaterialTheme.typography.bodyLarge)
                    FloatingActionButton(onClick = viewModel::openCamera) {
                        Icon(Icons.Outlined.PhotoCamera, contentDescription = "Foto aufnehmen")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 88.dp),
            ) {
                items(entries, key = { it.id }) { entry ->
                    Surface(modifier = Modifier.fillMaxWidth().clickable { onEntry(entry.id) }) {
                        ListItem(
                            headlineContent = { Text(entry.foodName ?: "Noch nicht verarbeitet") },
                            supportingContent = { Text(entry.weightInGrams?.let { "$it g" } ?: "Lebensmittel und Gewicht auswählen") },
                            leadingContent = {
                                PendingProductPhoto(
                                    photoPath = entry.photoPath,
                                    photoDirectory = "food-snap-photos",
                                    modifier = Modifier.size(64.dp),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

internal class FoodSnapInboxViewModel(
    observeEntries: ObserveFoodSnapEntriesUseCase,
    private val capturePhoto: CaptureFoodSnapPhotoUseCase,
) : ViewModel() {
    val entries = observeEntries.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val cameraOpen = kotlinx.coroutines.flow.MutableStateFlow(false)

    fun openCamera() { cameraOpen.value = true }

    fun closeCamera() { cameraOpen.value = false }

    fun onPhotoTaken(photoPath: String) {
        viewModelScope.launch { capturePhoto.capture(photoPath) }
    }
}
