package com.maksimowiczm.foodyou.app.widget

import android.appwidget.AppWidgetManager
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.maksimowiczm.foodyou.R
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.After
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin

@RunWith(AndroidJUnit4::class)
class CalorieWidgetLayoutSelectorTest {

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun missingHeightUsesMediumLayout() {
        val options = Bundle()

        assertFalse(CalorieWidgetLayoutSelector.isLarge(options))
        assertEquals(R.layout.widget_calories_medium, CalorieWidgetLayoutSelector.layout(options))
    }

    @Test
    fun smallHeightUsesMediumLayout() {
        val options =
            Bundle().apply { putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 159) }

        assertFalse(CalorieWidgetLayoutSelector.isLarge(options))
        assertEquals(R.layout.widget_calories_medium, CalorieWidgetLayoutSelector.layout(options))
    }

    @Test
    fun largeHeightUsesLargeLayout() {
        val options =
            Bundle().apply { putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 160) }

        assertTrue(CalorieWidgetLayoutSelector.isLarge(options))
        assertEquals(R.layout.widget_calories_large, CalorieWidgetLayoutSelector.layout(options))
    }
}
