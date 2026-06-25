package com.maksimowiczm.foodyou.app.infrastructure.android

import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import androidx.compose.runtime.mutableStateOf
import com.maksimowiczm.foodyou.R
import com.maksimowiczm.foodyou.app.ui.FoodYouApp
import com.maksimowiczm.foodyou.app.ui.FoodYouLaunchAction
import com.maksimowiczm.foodyou.app.ui.FoodYouLaunchRequest
import com.maksimowiczm.foodyou.app.widget.CalorieWidgetProvider

class MainActivity : FoodYouAbstractActivity() {
    private val launchRequestState = mutableStateOf<FoodYouLaunchRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        publishShortcuts()
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
    }

    private fun publishShortcuts() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return

        val shortcutIntent =
            Intent(this, MainActivity::class.java)
                .setAction(ACTION_SCAN_BARCODE)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        val shortcut =
            ShortcutInfo.Builder(this, SHORTCUT_SCAN_BARCODE_ID)
                .setShortLabel(getString(R.string.shortcut_scan_barcode_short))
                .setLongLabel(getString(R.string.shortcut_scan_barcode_long))
                .setIcon(Icon.createWithResource(this, R.drawable.ic_shortcut_scan_barcode))
                .setIntent(shortcutIntent)
                .build()

        getSystemService(ShortcutManager::class.java).dynamicShortcuts = listOf(shortcut)
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

    private companion object {
        const val ACTION_SCAN_BARCODE = "com.maksimowiczm.foodyou.action.SCAN_BARCODE"
        const val SHORTCUT_SCAN_BARCODE_ID = "scan_barcode"
    }
}
