package com.maksimowiczm.foodyou.app.ui.weight

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.health.connect.client.PermissionController
import com.maksimowiczm.foodyou.weight.WeightHealthConnectPermissions

@Composable
internal actual fun rememberHealthConnectWeightPermissionRequester(
    onResult: (Boolean) -> Unit
): HealthConnectWeightPermissionRequester {
    val launcher =
        rememberLauncherForActivityResult(
            PermissionController.createRequestPermissionResultContract()
        ) { granted ->
            onResult(
                WeightHealthConnectPermissions.readWeight in granted &&
                    WeightHealthConnectPermissions.writeWeight in granted
            )
        }

    return remember(launcher) {
        object : HealthConnectWeightPermissionRequester {
            override fun request() {
                launcher.launch(
                    setOf(
                        WeightHealthConnectPermissions.readWeight,
                        WeightHealthConnectPermissions.writeWeight,
                    )
                )
            }
        }
    }
}
