package com.maksimowiczm.foodyou.app.widget.ring

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.appwidget.compose
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.maksimowiczm.foodyou.app.widget.CalorieWidgetModel
import com.maksimowiczm.foodyou.app.widget.calorieWidgetModel
import com.maksimowiczm.foodyou.app.ui.home.goals.GoalEnergyOptimizationDay
import java.util.Locale
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalGlanceApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "de-rDE-mdpi")
class CalorieRingWidgetScreenshotTest {
    private val controllers = mutableListOf<ActivityController<Activity>>()
    private val originalLocale = Locale.getDefault()

    @Before
    fun setUp() {
        Locale.setDefault(Locale.GERMANY)
    }

    @After
    fun tearDown() {
        controllers.forEach { it.pause().stop().destroy() }
        Locale.setDefault(originalLocale)
        stopKoin()
    }

    @Test fun minLightNormal() = capture("min", 250, 110, night = false, case = "normal", model = normal())
    @Test fun minLightOverflow() = capture("min", 250, 110, night = false, case = "overflow", model = overflow())
    @Test fun cellLightNormal() = capture("cell", 320, 170, night = false, case = "normal", model = normal())
    /** Night mode must still render the light design. */
    @Test fun cellNightOverflow() = capture("cell", 320, 170, night = true, case = "overflow", model = overflow())
    @Test fun cellLightNoDiet() = capture("cell", 320, 170, night = false, case = "no-diet", model = noDiet())
    @Test fun tallLightNormal() = capture("tall", 320, 240, night = false, case = "normal", model = normal())
    @Test fun tallLightOverflow() = capture("tall", 320, 240, night = false, case = "overflow", model = overflow())
    @Test fun wideLightNormal() = capture("wide", 400, 150, night = false, case = "normal", model = normal())
    @Test fun midLightNormal() = capture("mid", 300, 190, night = false, case = "normal", model = normal())

    /** High-resolution capture that doubles as the widget picker preview image. */
    @Test
    fun previewImage() {
        val root = render(width = 320, height = 240, night = false, model = normal(), density = "xxhdpi")
        root.captureRoboImage(filePath = "CalorieRingWidgetScreenshotTest.preview-xxhdpi.png")
    }

    @Test
    @Config(sdk = [30])
    fun cellLightFallbackTheme() =
        capture("cell", 320, 170, night = false, case = "fallback", model = normal())

    private fun capture(
        size: String,
        width: Int,
        height: Int,
        night: Boolean,
        case: String,
        model: CalorieWidgetModel,
    ) {
        val root = render(width, height, night, model)
        root.captureRoboImage(
            filePath =
                "CalorieRingWidgetScreenshotTest.$size-${if (night) "night" else "light"}-$case.png"
        )
    }

    private fun render(
        width: Int,
        height: Int,
        night: Boolean,
        model: CalorieWidgetModel,
        density: String = "mdpi",
    ): ViewGroup {
        RuntimeEnvironment.setQualifiers(
            "de-rDE-w${width}dp-h${height}dp-" + (if (night) "night" else "notnight") + "-$density"
        )
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        controllers += controller
        val context = controller.get()
        val remoteViews = runBlocking {
            CalorieRingWidget(loadModel = { model })
                .compose(context = context, size = DpSize(width.dp, height.dp))
        }
        val scale = context.resources.displayMetrics.density
        val widthPx = (width * scale).toInt()
        val heightPx = (height * scale).toInt()
        val root = remoteViews.apply(context, FrameLayout(context)) as ViewGroup
        context.setContentView(root, ViewGroup.LayoutParams(widthPx, heightPx))
        root.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, widthPx, heightPx)
        return root
    }

    private fun normal() =
        calorieWidgetModel(
            today = LocalDate(2026, 9, 15),
            eatenKcal = 1521.0,
            burnedKcal = 307.0,
            countedSteps = 7_677,
            baseGoalKcal = 2000.0,
            dietEnergyDeficitKcal = 300.0,
            previousDays = overEatenDay(),
        )

    private fun overflow() =
        calorieWidgetModel(
            today = LocalDate(2026, 9, 15),
            eatenKcal = 2960.0,
            burnedKcal = 307.0,
            countedSteps = 7_677,
            baseGoalKcal = 2100.0,
            dietEnergyDeficitKcal = 500.0,
            previousDays = overEatenDay(),
        )

    private fun noDiet() =
        calorieWidgetModel(
            today = LocalDate(2026, 9, 15),
            eatenKcal = 900.0,
            burnedKcal = 0.0,
            countedSteps = 0,
            baseGoalKcal = 2000.0,
            dietEnergyDeficitKcal = null,
            previousDays = emptyList(),
        )

    private fun overEatenDay() =
        listOf(
            GoalEnergyOptimizationDay(
                date = LocalDate(2026, 9, 14),
                consumedEnergyKcal = 3400.0,
                baseEnergyGoalKcal = 2000.0,
                burnedEnergyKcal = 0.0,
            )
        )
}
