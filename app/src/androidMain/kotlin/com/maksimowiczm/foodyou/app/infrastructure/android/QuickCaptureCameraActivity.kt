package com.maksimowiczm.foodyou.app.infrastructure.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.maksimowiczm.foodyou.app.ui.food.quickcapture.QuickCaptureCameraScreen
import com.maksimowiczm.foodyou.app.ui.theme.FoodYouTheme

class QuickCaptureCameraActivity : FoodYouAbstractActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    closeCamera()
                }
            },
        )

        setContent {
            FoodYouTheme {
                Surface(Modifier.fillMaxSize()) {
                    QuickCaptureCameraScreen(onClose = ::closeCamera)
                }
            }
        }
    }

    private fun closeCamera() {
        finishAndRemoveTask()
    }
}

internal fun quickCaptureCameraIntent(context: Context): Intent =
    Intent(context, QuickCaptureCameraActivity::class.java).apply {
        action = ACTION_QUICK_CAPTURE_CAMERA
        addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        )
    }

internal const val ACTION_QUICK_CAPTURE_CAMERA =
    "com.maksimowiczm.foodyou.action.QUICK_CAPTURE_CAMERA"
