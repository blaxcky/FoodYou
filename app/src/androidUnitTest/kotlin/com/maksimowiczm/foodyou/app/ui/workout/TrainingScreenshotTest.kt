package com.maksimowiczm.foodyou.app.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.locale
import com.github.takahirom.roborazzi.size
import com.maksimowiczm.foodyou.app.ui.common.utility.EnergyFormatter
import com.maksimowiczm.foodyou.app.ui.common.utility.EnergyFormatterProvider
import com.maksimowiczm.foodyou.app.ui.home.activity.ActivitiesCardContent
import com.maksimowiczm.foodyou.app.ui.home.activity.ActivitiesCardModel
import com.maksimowiczm.foodyou.app.ui.home.activity.workoutCardRows
import com.maksimowiczm.foodyou.workout.domain.CardioConversion
import com.maksimowiczm.foodyou.workout.domain.StrengthMeasure
import com.maksimowiczm.foodyou.workout.domain.WorkoutEntry
import com.maksimowiczm.foodyou.workout.domain.WorkoutEntryId
import com.maksimowiczm.foodyou.workout.domain.WorkoutExercise
import com.maksimowiczm.foodyou.workout.domain.WorkoutExerciseId
import com.maksimowiczm.foodyou.workout.domain.WorkoutGoals
import com.maksimowiczm.foodyou.workout.domain.WorkoutKind
import com.maksimowiczm.foodyou.workout.domain.WorkoutMilestone
import com.maksimowiczm.foodyou.workout.domain.WorkoutMilestoneId
import com.maksimowiczm.foodyou.workout.domain.planWorkoutToday
import com.maksimowiczm.foodyou.workout.domain.planWorkoutWeek
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "de-rDE-w390dp-h1000dp-mdpi")
@OptIn(ExperimentalRoborazziApi::class)
class TrainingScreenshotTest {
    @After fun tearDown() = stopKoin()

    private val created = Instant.fromEpochSeconds(0)
    private val today = LocalDate(2026, 11, 26)

    private fun strength(
        id: Long,
        name: String,
        measure: StrengthMeasure = StrengthMeasure.Reps,
        weights: List<Double> = emptyList(),
        kcal: Double = 4.0,
        helpsWith: String = "",
    ) =
        WorkoutExercise(
            id = WorkoutExerciseId(id),
            name = name,
            kind = WorkoutKind.Strength,
            measure = measure,
            usesWeight = weights.isNotEmpty(),
            weightOptionsKg = weights,
            kcalValue = kcal,
            helpsWith = helpsWith,
            createdAt = created,
        )

    private fun cardio(id: Long, name: String, conversion: CardioConversion, discount: Double) =
        WorkoutExercise(
            id = WorkoutExerciseId(id),
            name = name,
            kind = WorkoutKind.Cardio,
            cardioConversion = conversion,
            cardioDiscountPercent = discount,
            createdAt = created,
        )

    private val exercises =
        listOf(
            strength(1, "Kurzhantel-Rudern", weights = listOf(4.0, 6.0, 8.0, 10.0), helpsWith = "Klimmzug"),
            strength(2, "Bodendrücken", weights = listOf(6.0, 8.0), helpsWith = "Liegestütz"),
            strength(3, "Goblet-Kniebeuge", weights = listOf(8.0), kcal = 5.0),
            strength(4, "Toter Hang", measure = StrengthMeasure.Seconds, kcal = 2.0, helpsWith = "Klimmzug"),
            strength(5, "Schulterdrücken", weights = listOf(4.0, 6.0)),
            strength(6, "Latzug, Luftwiderstand", measure = StrengthMeasure.Level, kcal = 6.0),
            cardio(7, "Crosstrainer", CardioConversion.Device, 15.0),
            cardio(8, "Rudern", CardioConversion.Concept2, 10.0),
        )

    private var nextId = 1L

    private fun entry(
        exercise: Long,
        daysAgo: Int,
        minute: Int,
        kcal: Double,
        reps: Int? = 10,
        weight: Double? = null,
        seconds: Int? = null,
        level: Int? = null,
        duration: Int? = null,
        device: Double? = null,
    ) =
        WorkoutEntry(
            id = WorkoutEntryId(nextId++),
            exerciseId = WorkoutExerciseId(exercise),
            date = today.minus(daysAgo, DateTimeUnit.DAY),
            minuteOfDay = minute,
            reps = reps,
            seconds = seconds,
            level = level,
            weightKg = weight,
            durationMinutes = duration,
            deviceKcal = device,
            energyKcal = kcal,
            createdAt = created,
            updatedAt = created,
        )

    private val entries =
        listOf(
            entry(1, 0, 460, 4.0, weight = 8.0),
            entry(1, 0, 462, 4.0, weight = 8.0),
            entry(4, 0, 735, 2.0, reps = null, seconds = 25),
            entry(8, 0, 965, 238.0, reps = null, duration = 20, device = 260.0),
            entry(3, 0, 1110, 5.0, reps = 12, weight = 8.0),
            entry(2, 1, 790, 4.0, weight = 8.0),
            entry(7, 1, 435, 68.0, reps = null, duration = 10, device = 80.0),
            entry(6, 2, 1140, 6.0, level = 4),
            entry(1, 3, 480, 4.0, weight = 8.0),
            entry(5, 6, 600, 4.0, weight = 6.0),
        )

    @Composable
    private fun Screen(height: Int, content: @Composable () -> Unit) {
        MaterialTheme {
            EnergyFormatterProvider(EnergyFormatter.kilocalories) {
                Box(
                    Modifier.requiredSize(390.dp, height.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    content()
                }
            }
        }
    }

    private fun capture(name: String, height: Int, content: @Composable () -> Unit) {
        captureRoboImage(
            filePath = "TrainingScreenshotTest.$name.png",
            roborazziComposeOptions =
                RoborazziComposeOptions.Builder().size(390, height).locale("de-rDE").build(),
        ) {
            Screen(height, content)
        }
    }

    @Test
    fun today() =
        capture("today", 1000) {
            TrainingTodayContent(
                plan = planWorkoutToday(exercises, entries, today),
                query = "",
                onQuery = {},
                onLog = {},
                onEditExercise = {},
                onAddStrength = {},
                onAddCardio = {},
            )
        }

    @Test
    fun todayFewExercises() =
        capture("today-few", 600) {
            TrainingTodayContent(
                plan = planWorkoutToday(exercises.take(3), entries, today),
                query = "",
                onQuery = {},
                onLog = {},
                onEditExercise = {},
                onAddStrength = {},
                onAddCardio = {},
            )
        }

    @Test
    fun todayEmpty() =
        capture("today-empty", 400) {
            TrainingTodayContent(
                plan = planWorkoutToday(emptyList(), emptyList(), today),
                query = "",
                onQuery = {},
                onLog = {},
                onEditExercise = {},
                onAddStrength = {},
                onAddCardio = {},
            )
        }

    @Test
    fun week() =
        capture("week", 1000) {
            TrainingWeekContent(
                week = planWorkoutWeek(exercises, entries, today, today, WorkoutGoals()),
                goals = WorkoutGoals(cardioGoalEnabled = true),
                isCurrentWeek = true,
                onPreviousWeek = {},
                onNextWeek = {},
                onChangeGoal = {},
            )
        }

    @Test
    fun history() =
        capture("history", 700) {
            val byId = exercises.associateBy { it.id }
            TrainingHistoryContent(
                date = today,
                today = today,
                rows =
                    entries
                        .filter { it.date == today }
                        .sortedBy { it.minuteOfDay }
                        .map { TrainingHistoryRow(it, byId[it.exerciseId]) },
                onPreviousDay = {},
                onNextDay = {},
                onPickDay = {},
                onEdit = {},
                onDelete = {},
                onAdd = {},
            )
        }

    @Composable
    private fun Sheet(content: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Box(Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) { content() }
            }
        }
    }

    @Test
    fun strengthSheet() =
        capture("sheet-strength", 760) {
            Sheet {
                val exercise = exercises[0]
                val last = entries.first { it.exerciseId == exercise.id }
                WorkoutEntrySheetContent(
                    exercise = exercise,
                    initialDraft = newWorkoutEntryDraft(exercise, today, last),
                    today = today,
                    nowMinuteOfDay = 600,
                    lastEntry = last,
                    bodyWeightKg = 82.4,
                    onSave = { _, _ -> },
                    onCancel = {},
                )
            }
        }

    @Test
    fun secondsSheet() =
        capture("sheet-seconds", 640) {
            Sheet {
                val exercise = exercises[3]
                WorkoutEntrySheetContent(
                    exercise = exercise,
                    initialDraft = WorkoutEntryDraft(date = today, seconds = 25),
                    today = today,
                    nowMinuteOfDay = 600,
                    lastEntry = null,
                    bodyWeightKg = null,
                    onSave = { _, _ -> },
                    onCancel = {},
                )
            }
        }

    @Test
    fun levelSheet() =
        capture("sheet-level", 640) {
            Sheet {
                val exercise = exercises[5]
                WorkoutEntrySheetContent(
                    exercise = exercise,
                    initialDraft = WorkoutEntryDraft(date = today, level = 4, reps = 10),
                    today = today,
                    nowMinuteOfDay = 600,
                    lastEntry = null,
                    bodyWeightKg = null,
                    onSave = { _, _ -> },
                    onCancel = {},
                )
            }
        }

    @Test
    fun rowingSheet() =
        capture("sheet-rowing", 640) {
            Sheet {
                WorkoutEntrySheetContent(
                    exercise = exercises[7],
                    initialDraft = WorkoutEntryDraft(date = today, durationMinutes = 20, deviceKcal = 260.0),
                    today = today,
                    nowMinuteOfDay = 985,
                    lastEntry = null,
                    bodyWeightKg = 82.4,
                    onSave = { _, _ -> },
                    onCancel = {},
                )
            }
        }

    @Test
    fun strengthEditor() =
        capture("editor-strength", 1000) {
            WorkoutExerciseEditorContent(
                form = exercises[0].toForm(),
                latestWeightKg = 82.4,
                onUpdate = {},
                onArchive = {},
            )
        }

    @Test
    fun cardioEditor() =
        capture("editor-cardio", 1000) {
            WorkoutExerciseEditorContent(
                form = exercises[7].toForm(),
                latestWeightKg = 82.4,
                onUpdate = {},
                onArchive = {},
            )
        }

    @Test
    fun goals() =
        capture("goals", 1000) {
            WorkoutGoalsContent(
                goals = WorkoutGoals(cardioGoalEnabled = true),
                milestones =
                    listOf(
                        WorkoutMilestone(WorkoutMilestoneId(1), "Erster Klimmzug", "Toter Hang 30 s am Stück"),
                        WorkoutMilestone(WorkoutMilestoneId(2), "Erster Liegestütz", "Bodendrücken 10 kg × 10", achieved = true),
                    ),
                onGoals = {},
                onEditMilestone = {},
                onAddMilestone = {},
            )
        }

    @Test
    fun activitiesCard() =
        capture("activities-card", 520) {
            Box(Modifier.requiredSize(374.dp, 500.dp)) {
                ActivitiesCardContent(
                    model =
                        ActivitiesCardModel(
                            countedSteps = 8_436,
                            excludedSteps = 1_284,
                            healthConnectStepsEnabled = true,
                            stepEnergyKcal = 34,
                            manualEnergyKcal = 0,
                            totalEnergyKcal = 287,
                            manualEntries = emptyList(),
                            workoutRows = workoutCardRows(entries.filter { it.date == today }, exercises),
                        ),
                    onAdd = {},
                    onEdit = {},
                    onEditImported = {},
                    onStepExclusions = {},
                    onLongClick = {},
                )
            }
        }
}
