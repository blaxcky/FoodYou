package com.maksimowiczm.foodyou.app.widget

import android.content.Context
import org.koin.core.context.GlobalContext

internal actual fun updateCalorieWidgetValues() {
    val context = GlobalContext.get().get<Context>()
    CalorieWidgetProvider.updateAllValues(context)
}
