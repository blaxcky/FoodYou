package com.maksimowiczm.foodyou.app.widget

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.maksimowiczm.foodyou.R
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "de-rDE-mdpi")
class CalorieWidgetScreenshotTest {
    private val controllers = mutableListOf<ActivityController<Activity>>()
    private val originalLocale = Locale.getDefault()

    @After
    fun tearDown() {
        controllers.forEach { it.pause().stop().destroy() }
        RuntimeEnvironment.setFontScale(1f)
        Locale.setDefault(originalLocale)
        stopKoin()
    }

    @Test fun mediumNormal() = verifyLayout(false, 360, 1f, "normal")
    @Test fun mediumMinimum() = verifyLayout(false, 250, 1f, "minimum")
    @Test fun mediumLargeFont() = verifyLayout(false, 250, 1.5f, "large-font")
    @Test fun largeNormal() = verifyLayout(true, 420, 1f, "normal")
    @Test fun largeMinimum() = verifyLayout(true, 250, 1f, "minimum")
    @Test fun largeLargeFont() = verifyLayout(true, 250, 1.5f, "large-font")

    @Test
    fun numberFormattingUsesLocale() {
        Locale.setDefault(Locale.US)
        val root = render(false, 360, 1f, 12_345)
        assertEquals("12,345", root.findViewById<TextView>(R.id.widget_calories_steps).text)
    }

    private fun verifyLayout(large: Boolean, width: Int, fontScale: Float, name: String) {
        Locale.setDefault(Locale.GERMANY)
        var contentPositions: List<Int>? = null
        for ((steps, expected) in listOf(0L to "0", 8_432L to "8.432", 12_345L to "12.345")) {
            val root = render(large, width, fontScale, steps)
            val date = root.findViewById<TextView>(R.id.widget_calories_date)
            val count = root.findViewById<TextView>(R.id.widget_calories_steps)
            val icon = root.findViewById<View>(R.id.widget_calories_steps_icon)
            val header = date.parent as ViewGroup
            assertEquals(expected, count.text.toString())
            assertEquals("$expected Schritte", count.contentDescription.toString())
            assertEquals(date.textSize, count.textSize)
            assertEquals(date.currentTextColor, count.currentTextColor)
            assertEquals(1, date.lineCount)
            assertEquals(1, count.lineCount)
            assertTrue(date.right <= icon.left)
            assertTrue(icon.right <= count.left)
            assertTrue(count.right <= header.width)
            assertTrue(count.paint.measureText(expected) <= count.width)
            assertEquals(date.top + date.height / 2, count.top + count.height / 2)
            assertTrue(kotlin.math.abs(icon.top + icon.height / 2 - header.height / 2) <= 1)
            assertEquals(if (large) 34 else 24, header.height)
            if (fontScale > 1f && steps >= 10_000) assertTrue(date.layout.getEllipsisCount(0) > 0)
            val positions = (1 until root.childCount).map { root.getChildAt(it).top }
            assertEquals(if (large) listOf(81, 196, 230) else listOf(46, 122), positions)
            contentPositions?.let { assertEquals(it, positions) }
            contentPositions = positions
            root.captureRoboImage(
                filePath = "CalorieWidgetScreenshotTest.${if (large) "large" else "medium"}-$name-$steps.png"
            )
        }
    }

    private fun render(large: Boolean, width: Int, fontScale: Float, steps: Long): ViewGroup {
        val height = if (large) 380 else 230
        RuntimeEnvironment.setQualifiers("de-rDE-w${width}dp-h${height}dp-mdpi")
        RuntimeEnvironment.setFontScale(fontScale)
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        controllers += controller
        val context = controller.get()
        val model = calorieWidgetModel(
            today = LocalDate(2026, 9, 8),
            eatenKcal = 1521.0,
            burnedKcal = 120.0,
            countedSteps = steps,
            baseGoalKcal = 2000.0,
            dietEnergyDeficitKcal = 300.0,
            previousDays = emptyList(),
        )
        val root = createCalorieWidgetRemoteViews(
            context,
            if (large) R.layout.widget_calories_large else R.layout.widget_calories_medium,
            model,
        ).apply(context, FrameLayout(context)) as ViewGroup
        context.setContentView(root, ViewGroup.LayoutParams(width, height))
        root.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, width, height)
        return root
    }
}
