package com.maksimowiczm.foodyou.workout

import com.maksimowiczm.foodyou.workout.domain.WorkoutEntry
import com.maksimowiczm.foodyou.workout.domain.WorkoutEntryId
import com.maksimowiczm.foodyou.workout.domain.WorkoutExercise
import com.maksimowiczm.foodyou.workout.domain.WorkoutExerciseId
import com.maksimowiczm.foodyou.workout.domain.WorkoutMilestone
import com.maksimowiczm.foodyou.workout.domain.WorkoutMilestoneId
import com.maksimowiczm.foodyou.workout.domain.WorkoutRepository
import com.maksimowiczm.foodyou.workout.domain.stepExclusionWindow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

internal class FakeWorkoutRepository(
    exercises: List<WorkoutExercise> = emptyList(),
    entries: List<WorkoutEntry> = emptyList(),
) : WorkoutRepository {
    val exercises = MutableStateFlow(exercises)
    val entries = MutableStateFlow(entries)
    val milestones = MutableStateFlow(emptyList<WorkoutMilestone>())
    private var nextId = 1000L

    override fun observeExercises(): Flow<List<WorkoutExercise>> = exercises

    override fun observeExercise(id: WorkoutExerciseId): Flow<WorkoutExercise?> =
        exercises.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun createExercise(exercise: WorkoutExercise): WorkoutExerciseId {
        val id = WorkoutExerciseId(nextId++)
        exercises.value += exercise.copy(id = id)
        return id
    }

    override suspend fun updateExercise(exercise: WorkoutExercise) {
        exercises.value = exercises.value.map { if (it.id == exercise.id) exercise else it }
    }

    override suspend fun setExerciseArchived(id: WorkoutExerciseId, archived: Boolean) {
        exercises.value = exercises.value.map { if (it.id == id) it.copy(archived = archived) else it }
    }

    override fun observeEntries(from: LocalDate, to: LocalDate): Flow<List<WorkoutEntry>> =
        entries.map { list -> list.filter { it.date in from..to } }

    override fun observeEntry(id: WorkoutEntryId): Flow<WorkoutEntry?> =
        entries.map { list -> list.firstOrNull { it.id == id } }

    override fun observeEnergySum(date: LocalDate): Flow<Double> =
        entries.map { list -> list.filter { it.date == date }.sumOf { it.energyKcal } }

    override suspend fun createEntry(entry: WorkoutEntry): WorkoutEntryId {
        val id = WorkoutEntryId(nextId++)
        entries.value += entry.copy(id = id)
        return id
    }

    override suspend fun updateEntry(entry: WorkoutEntry) {
        entries.value = entries.value.map { if (it.id == entry.id) entry else it }
    }

    override suspend fun deleteEntry(id: WorkoutEntryId) {
        entries.value = entries.value.filterNot { it.id == id }
    }

    override suspend fun readStepExclusionWindows(
        dates: List<LocalDate>
    ): Map<LocalDate, List<IntRange>> {
        val byId = exercises.value.associateBy { it.id }
        return entries.value
            .filter { it.date in dates }
            .mapNotNull { entry ->
                val exercise = byId[entry.exerciseId] ?: return@mapNotNull null
                entry.stepExclusionWindow(exercise)?.let { entry.date to it }
            }
            .groupBy({ it.first }, { it.second })
    }

    override fun observeMilestones(): Flow<List<WorkoutMilestone>> = milestones

    override suspend fun saveMilestone(milestone: WorkoutMilestone): WorkoutMilestoneId {
        if (milestone.id.value == 0L) {
            val id = WorkoutMilestoneId(nextId++)
            milestones.value += milestone.copy(id = id)
            return id
        }
        milestones.value = milestones.value.map { if (it.id == milestone.id) milestone else it }
        return milestone.id
    }

    override suspend fun deleteMilestone(id: WorkoutMilestoneId) {
        milestones.value = milestones.value.filterNot { it.id == id }
    }
}
