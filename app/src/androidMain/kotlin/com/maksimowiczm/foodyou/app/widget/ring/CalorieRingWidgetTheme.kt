package com.maksimowiczm.foodyou.app.widget.ring

import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.glance.GlanceTheme
import androidx.glance.material3.ColorProviders
import com.maksimowiczm.foodyou.app.ui.home.goals.GoalsCardColor
import com.maksimowiczm.foodyou.app.ui.home.goals.GoalsErrorColor
import com.maksimowiczm.foodyou.app.ui.home.goals.GoalsMutedTextColor
import com.maksimowiczm.foodyou.app.ui.home.goals.GoalsProgressColor
import com.maksimowiczm.foodyou.app.ui.home.goals.GoalsTextColor
import com.maksimowiczm.foodyou.app.ui.home.goals.GoalsTrackColor

/**
 * The fixed palette of the in-app goals card: white card, blue progress on a light blue track and
 * red overflow. Deliberately ignores Material You wallpaper tones and the system's night mode so
 * the widget always matches the app.
 */
@Composable
internal fun CalorieRingWidgetTheme(content: @Composable () -> Unit) {
    val scheme =
        lightColorScheme(
            surface = GoalsCardColor,
            onSurface = GoalsTextColor,
            onSurfaceVariant = GoalsMutedTextColor,
            primary = GoalsProgressColor,
            primaryContainer = GoalsTrackColor,
            error = GoalsErrorColor,
        )
    GlanceTheme(colors = ColorProviders(scheme), content = content)
}
