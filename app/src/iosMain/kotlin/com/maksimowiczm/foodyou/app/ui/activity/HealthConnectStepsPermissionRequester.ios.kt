package com.maksimowiczm.foodyou.app.ui.activity

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
internal actual fun rememberHealthConnectStepsPermissionRequester(
    onResult: (Boolean) -> Unit
): HealthConnectStepsPermissionRequester =
    remember(onResult) {
        object : HealthConnectStepsPermissionRequester {
            override fun request() {
                onResult(false)
            }
        }
    }
