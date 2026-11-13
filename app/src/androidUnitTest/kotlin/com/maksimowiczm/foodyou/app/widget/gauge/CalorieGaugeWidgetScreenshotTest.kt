package com.maksimowiczm.foodyou.app.widget.gauge

import com.maksimowiczm.foodyou.settings.domain.entity.GoalGaugeCapMarkerStyle
import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.appwidget.compose
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.maksimowiczm.foodyou.app.widget.CalorieWidgetModel
import com.maksimowiczm.foodyou.app.widget.ring.write
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
class CalorieGaugeWidgetScreenshotTest {
    private val controllers = mutableListOf<ActivityController<Activity>>()
    private val originalLocale = Locale.getDefault()

    @Before
    fun setUp() {
        Locale.setDefault(Locale.GERMANY)
    }

    @After
    fun tearDown() {
        controllers.forEach { it.pause().stop().destroy() }
        RuntimeEnvironment.setFontScale(1f)
        Locale.setDefault(originalLocale)
        stopKoin()
    }

    @Test fun cellToday() = capture("cell", 320, 180, case = "today", model = today())
    @Test fun cellMorning() = capture("cell", 320, 180, case = "morning", model = morning())
    @Test fun cellOverflow() = capture("cell", 320, 180, case = "overflow", model = overflow())
    @Test fun cellNoDiet() = capture("cell", 320, 180, case = "no-diet", model = noDiet())
    @Test fun cellNoZones() = capture("cell", 320, 180, case = "no-zones", model = noZones())

    /** The diet budget is used up: its marker sits on the ring's start cap. */
    @Test fun cellBudgetUsedUp() = capture("cell", 320, 180, case = "budget-used-up", model = budgetUsedUp())

    /** Both goals lie above the normal goal: their dots sit past the ring's end. */
    @Test fun cellSavedAhead() = capture("cell", 320, 180, case = "saved-ahead", model = savedAhead())

    /** Each cap marker style with one goal off each end of the ring. */
    @Test
    fun cellCapMarkerStyles() {
        for (style in GoalGaugeCapMarkerStyle.entries) {
            capture(
                "cell",
                320,
                180,
                case = "cap-${style.name.lowercase()}",
                model = offBothEnds().copy(capMarkerStyle = style),
            )
        }
    }

    @Test fun minSavedAhead() = capture("min", 250, 130, case = "saved-ahead", model = savedAhead())

    /** Night mode must still render the light design. */
    @Test
    fun cellNightToday() = capture("cell", 320, 180, case = "today", model = today(), night = true)

    @Test fun flatToday() = capture("flat", 320, 130, case = "today", model = today())
    @Test fun flatFarOverflow() = capture("flat", 320, 130, case = "far-overflow", model = farOverflow())
    @Test fun roomyFarOverflow() = capture("roomy", 330, 200, case = "far-overflow", model = farOverflow())
    @Test fun minToday() = capture("min", 250, 130, case = "today", model = today())
    @Test fun tallToday() = capture("tall", 320, 240, case = "today", model = today())
    @Test fun wideToday() = capture("wide", 400, 150, case = "today", model = today())

    /** A larger system font grows the values on the ring; the remaining energy drops its unit. */
    @Test
    fun cellLargeFont() =
        capture("cell", 320, 180, case = "large-font", model = today(), fontScale = 1.3f)

    /** High-resolution capture that doubles as the widget picker preview image. */
    @Test
    fun previewImage() {
        val root = render(width = 320, height = 180, night = false, model = today(), density = "xxhdpi")
        root.captureRoboImage(filePath = "CalorieGaugeWidgetScreenshotTest.preview-xxhdpi.png")
    }

    private fun capture(
        size: String,
        width: Int,
        height: Int,
        case: String,
        model: CalorieWidgetModel,
        night: Boolean = false,
        fontScale: Float = 1f,
    ) {
        RuntimeEnvironment.setFontScale(fontScale)
        val root = render(width, height, night, model)
        root.captureRoboImage(
            filePath =
                "CalorieGaugeWidgetScreenshotTest.$size-${if (night) "night" else "light"}-$case.png"
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
            CalorieGaugeWidget(loadModel = { model })
                .compose(
                    context = context,
                    size = DpSize(width.dp, height.dp),
                    state = mutablePreferencesOf().apply { write(model) },
                )
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

    private fun model(
        eatenKcal: Int,
        burnedKcal: Int,
        steps: Long = 1_999,
        optimizedGoalKcal: Int? = 1684,
        dietGoalKcal: Int? = 721,
    ): CalorieWidgetModel {
        val netKcal = eatenKcal - burnedKcal
        return CalorieWidgetModel(
            date = LocalDate(2026, 11, 5),
            countedSteps = steps,
            eatenKcal = eatenKcal,
            burnedKcal = burnedKcal,
            netKcal = netKcal,
            normalGoalKcal = 2100,
            normalLeftKcal = 2100 - netKcal,
            optimizedGoalKcal = optimizedGoalKcal,
            optimizedLeftKcal = optimizedGoalKcal?.let { it - netKcal },
            dietGoalConfigured = dietGoalKcal != null,
            dietGoalKcal = dietGoalKcal,
            dietLeftKcal = dietGoalKcal?.let { it - netKcal },
        )
    }

    /** The diet goal is exceeded while Optimized and Normal still have energy left. */
    private fun today() = model(eatenKcal = 1169, burnedKcal = 80)

    private fun morning() = model(eatenKcal = 420, burnedKcal = 0, steps = 312)

    private fun overflow() = model(eatenKcal = 2480, burnedKcal = 80, steps = 6_420)

    private fun farOverflow() = model(eatenKcal = 4725, burnedKcal = 173, steps = 4_315)

    private fun noDiet() = model(eatenKcal = 1169, burnedKcal = 80, dietGoalKcal = null)

    private fun budgetUsedUp() =
        model(eatenKcal = 1169, burnedKcal = 80, optimizedGoalKcal = 1935, dietGoalKcal = -240)

    private fun savedAhead() =
        model(eatenKcal = 1169, burnedKcal = 80, optimizedGoalKcal = 2760, dietGoalKcal = 2260)

    private fun offBothEnds() =
        model(eatenKcal = 1169, burnedKcal = 80, optimizedGoalKcal = 2760, dietGoalKcal = -240)

    private fun noZones() =
        model(eatenKcal = 1169, burnedKcal = 80, optimizedGoalKcal = null, dietGoalKcal = null)
}
