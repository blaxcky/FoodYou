package com.maksimowiczm.foodyou.workout.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface WorkoutRepository {
    fun observeExercises(): Flow<List<WorkoutExercise>>

    fun observeExercise(id: WorkoutExerciseId): Flow<WorkoutExercise?>

    suspend fun createExercise(exercise: WorkoutExercise): WorkoutExerciseId

    suspend fun updateExercise(exercise: WorkoutExercise)

    suspend fun setExerciseArchived(id: WorkoutExerciseId, archived: Boolean)

    fun observeEntries(from: LocalDate, to: LocalDate): Flow<List<WorkoutEntry>>

    fun observeEntry(id: WorkoutEntryId): Flow<WorkoutEntry?>

    fun observeEnergySum(date: LocalDate): Flow<Double>

    suspend fun createEntry(entry: WorkoutEntry): WorkoutEntryId

    suspend fun updateEntry(entry: WorkoutEntry)

    suspend fun deleteEntry(id: WorkoutEntryId)

    /** Cardio windows whose Health Connect steps must not count, merged into the step sync. */
    suspend fun readStepExclusionWindows(dates: List<LocalDate>): Map<LocalDate, List<IntRange>>

    fun observeMilestones(): Flow<List<WorkoutMilestone>>

    suspend fun saveMilestone(milestone: WorkoutMilestone): WorkoutMilestoneId

    suspend fun deleteMilestone(id: WorkoutMilestoneId)
}
