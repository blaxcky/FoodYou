package com.maksimowiczm.foodyou.app.widget.ring

import android.os.Build
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.glance.GlanceTheme
import androidx.glance.material3.ColorProviders

@Composable
internal fun CalorieRingWidgetTheme(content: @Composable () -> Unit) {
    val colors =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            GlanceTheme.colors
        } else {
            ColorProviders(light = lightColorScheme(), dark = darkColorScheme())
        }
    GlanceTheme(colors = colors, content = content)
}
