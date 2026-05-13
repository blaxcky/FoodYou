package com.maksimowiczm.foodyou.app.ui.activity

import androidx.compose.runtime.Composable

internal interface HealthConnectStepsPermissionRequester {
    fun request()
}

@Composable
internal expect fun rememberHealthConnectStepsPermissionRequester(
    onResult: (Boolean) -> Unit
): HealthConnectStepsPermissionRequester
