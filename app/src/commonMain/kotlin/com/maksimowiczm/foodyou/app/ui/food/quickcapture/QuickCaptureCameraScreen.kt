package com.maksimowiczm.foodyou.app.ui.food.quickcapture

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import com.maksimowiczm.foodyou.app.ui.food.pending.PendingProductPhotoCapture
import com.maksimowiczm.foodyou.food.domain.usecase.CaptureQuickCapturePhotoUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.ObserveQuickCaptureUseCase
import foodyou.app.generated.resources.Res
import foodyou.app.generated.resources.headline_quick_capture
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun QuickCaptureCameraScreen(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: QuickCaptureCameraViewModel = koinViewModel()
    val photoCount by viewModel.photoCount.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.headline_quick_capture)) },
                navigationIcon = { ArrowBackIconButton(onClose) },
            )
        },
    ) { padding ->
        PendingProductPhotoCapture(
            photoCount = photoCount,
            photoDirectory = QUICK_CAPTURE_PHOTO_DIRECTORY,
            onPhotoTaken = viewModel::capturePhoto,
            onClose = onClose,
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }
}

internal class QuickCaptureCameraViewModel(
    observe: ObserveQuickCaptureUseCase,
    private val capture: CaptureQuickCapturePhotoUseCase,
) : ViewModel() {
    val photoCount =
        observe.entries()
            .map { entries -> entries.count { it.isPendingPhoto } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun capturePhoto(path: String) {
        viewModelScope.launch { capture.capture(path) }
    }
}
