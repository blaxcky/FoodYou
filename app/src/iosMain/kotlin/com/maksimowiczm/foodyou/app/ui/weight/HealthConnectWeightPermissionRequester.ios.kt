package com.maksimowiczm.foodyou.app.ui.weight

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
internal actual fun rememberHealthConnectWeightPermissionRequester(
    onResult: (Boolean) -> Unit
): HealthConnectWeightPermissionRequester =
    remember {
        object : HealthConnectWeightPermissionRequester {
            override fun request() {
                onResult(false)
            }
        }
    }
