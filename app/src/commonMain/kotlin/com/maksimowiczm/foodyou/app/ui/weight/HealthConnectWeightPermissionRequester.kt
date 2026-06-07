package com.maksimowiczm.foodyou.app.ui.weight

import androidx.compose.runtime.Composable

internal interface HealthConnectWeightPermissionRequester {
    fun request()
}

@Composable
internal expect fun rememberHealthConnectWeightPermissionRequester(
    onResult: (Boolean) -> Unit
): HealthConnectWeightPermissionRequester
