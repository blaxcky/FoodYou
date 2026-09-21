package com.maksimowiczm.foodyou.app.ui.weight

import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun sourceAppLabel(packageName: String): String? {
    val packageManager = LocalContext.current.packageManager
    return remember(packageManager, packageName) {
        try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString().takeIf { it.isNotBlank() }
        } catch (_: PackageManager.NameNotFoundException) {
            when (packageName) {
                "com.fitbit.FitbitMobile" -> "Fitbit"
                else -> packageName.substringAfterLast('.').takeIf { it.isNotBlank() }
            }
        }
    }
}
