package com.maksimowiczm.foodyou.workout

import com.maksimowiczm.foodyou.app.infrastructure.room.FoodYouDatabase
import com.maksimowiczm.foodyou.settings.domain.entity.Settings
import com.maksimowiczm.foodyou.workout.domain.WorkoutGoals
import com.maksimowiczm.foodyou.workout.domain.WorkoutRepository
import com.maksimowiczm.foodyou.workout.infrastructure.RoomWorkoutRepository
import com.maksimowiczm.foodyou.workout.infrastructure.WorkoutDao
import org.koin.dsl.module

val workoutModule = module {
    factory<WorkoutDao> { get<FoodYouDatabase>().workoutDao }
    factory<WorkoutRepository> { RoomWorkoutRepository(get()) }
}

val Settings.workoutGoals: WorkoutGoals
    get() =
        WorkoutGoals(
            strengthDaysPerWeek = workoutStrengthDaysPerWeek,
            strengthDayMinSets = workoutStrengthDayMinSets,
            cardioGoalEnabled = workoutCardioGoalEnabled,
            cardioDaysPerWeek = workoutCardioDaysPerWeek,
            cardioMinMinutes = workoutCardioMinMinutes,
        )
