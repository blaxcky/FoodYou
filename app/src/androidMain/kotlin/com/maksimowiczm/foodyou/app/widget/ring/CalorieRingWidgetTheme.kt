package com.maksimowiczm.foodyou.app.widget.ring

import android.os.Build
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.material3.ColorProviders

/**
 * Always-light theme: dynamic Material You tones on API 31+, the Material 3 baseline below, in
 * both cases ignoring the system's night mode.
 */
@Composable
internal fun CalorieRingWidgetTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dynamicLightColorScheme(context)
        } else {
            lightColorScheme()
        }
    GlanceTheme(colors = ColorProviders(scheme), content = content)
}
