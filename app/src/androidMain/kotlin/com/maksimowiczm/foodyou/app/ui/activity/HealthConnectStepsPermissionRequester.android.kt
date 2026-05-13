package com.maksimowiczm.foodyou.app.ui.activity

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.health.connect.client.PermissionController
import com.maksimowiczm.foodyou.activity.ActivityHealthConnectPermissions

@Composable
internal actual fun rememberHealthConnectStepsPermissionRequester(
    onResult: (Boolean) -> Unit
): HealthConnectStepsPermissionRequester {
    val launcher =
        rememberLauncherForActivityResult(
            PermissionController.createRequestPermissionResultContract()
        ) { granted ->
            onResult(ActivityHealthConnectPermissions.readSteps in granted)
        }

    return remember(launcher) {
        object : HealthConnectStepsPermissionRequester {
            override fun request() {
                launcher.launch(setOf(ActivityHealthConnectPermissions.readSteps))
            }
        }
    }
}
