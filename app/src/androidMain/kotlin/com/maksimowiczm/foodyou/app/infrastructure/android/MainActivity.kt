package com.maksimowiczm.foodyou.app.infrastructure.android

import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.mutableStateOf
import com.maksimowiczm.foodyou.app.ui.FoodYouApp
import com.maksimowiczm.foodyou.app.ui.FoodYouLaunchAction
import com.maksimowiczm.foodyou.app.ui.FoodYouLaunchRequest
import com.maksimowiczm.foodyou.app.widget.CalorieWidgetProvider
import com.maksimowiczm.foodyou.app.widget.ring.CalorieRingWidget
import com.maksimowiczm.foodyou.food.domain.usecase.FddbProductSyncForegroundLauncher
import org.koin.android.ext.android.inject

class MainActivity : FoodYouAbstractActivity() {
    private val launchRequestState = mutableStateOf<FoodYouLaunchRequest?>(null)
    private val fddbProductSyncForegroundLauncher: FddbProductSyncForegroundLauncher by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        publishAppShortcuts(this)
        if (savedInstanceState == null) {
            launchRequestState.value = intent.toLaunchRequest()
        }

        setContent {
            FoodYouApp(
                launchRequest = launchRequestState.value,
                onDatabaseBackup = {
                    val intent =
                        Intent(this, FullBackupActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }

                    startActivity(intent)
                }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchRequestState.value = intent.toLaunchRequest()
    }

    override fun onStart() {
        super.onStart()
        CalorieWidgetProvider.updateAll(this)
        CalorieRingWidget.requestUpdateAll(this)
        fddbProductSyncForegroundLauncher.onForeground()
    }

    private fun Intent?.toLaunchRequest(): FoodYouLaunchRequest? =
        when (this?.action) {
            ACTION_SCAN_BARCODE ->
                FoodYouLaunchRequest(
                    action = FoodYouLaunchAction.ScanBarcode,
                    nonce = System.nanoTime(),
                )

            else -> null
        }
}
