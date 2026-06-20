package com.maksimowiczm.foodyou.app.widget

import android.content.Context
import org.koin.core.context.GlobalContext

internal actual fun updateCalorieWidgetValues() {
    val context = runCatching { GlobalContext.get().get<Context>() }.getOrNull() ?: return
    CalorieWidgetProvider.updateAllValues(context)
}
