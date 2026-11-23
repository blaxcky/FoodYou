package com.maksimowiczm.foodyou.workout.infrastructure

import com.maksimowiczm.foodyou.workout.domain.CardioConversion
import com.maksimowiczm.foodyou.workout.domain.StrengthKcalMode
import com.maksimowiczm.foodyou.workout.domain.StrengthMeasure
import com.maksimowiczm.foodyou.workout.domain.WorkoutEntry
import com.maksimowiczm.foodyou.workout.domain.WorkoutEntryId
import com.maksimowiczm.foodyou.workout.domain.WorkoutExercise
import com.maksimowiczm.foodyou.workout.domain.WorkoutExerciseId
import com.maksimowiczm.foodyou.workout.domain.WorkoutFeel
import com.maksimowiczm.foodyou.workout.domain.WorkoutKind
import com.maksimowiczm.foodyou.workout.domain.WorkoutMilestone
import com.maksimowiczm.foodyou.workout.domain.WorkoutMilestoneId
import com.maksimowiczm.foodyou.workout.domain.WorkoutRepository
import com.maksimowiczm.foodyou.workout.domain.stepExclusionWindow
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

internal class RoomWorkoutRepository(private val dao: WorkoutDao) : WorkoutRepository {
    override fun observeExercises(): Flow<List<WorkoutExercise>> =
        dao.observeExercises().map { list -> list.map { it.toModel() } }

    override fun observeExercise(id: WorkoutExerciseId): Flow<WorkoutExercise?> =
        dao.observeExercise(id.value).map { it?.toModel() }

    override suspend fun createExercise(exercise: WorkoutExercise): WorkoutExerciseId =
        WorkoutExerciseId(dao.insertExercise(exercise.toEntity().copy(id = 0)))

    override suspend fun updateExercise(exercise: WorkoutExercise) {
        dao.updateExercise(exercise.toEntity())
    }

    override suspend fun setExerciseArchived(id: WorkoutExerciseId, archived: Boolean) {
        dao.setArchived(id.value, archived)
    }

    override fun observeEntries(from: LocalDate, to: LocalDate): Flow<List<WorkoutEntry>> =
        dao.observeEntries(from.toEpochDays(), to.toEpochDays()).map { list ->
            list.map { it.toModel() }
        }

    override fun observeEntry(id: WorkoutEntryId): Flow<WorkoutEntry?> =
        dao.observeEntry(id.value).map { it?.toModel() }

    override fun observeEnergySum(date: LocalDate): Flow<Double> =
        dao.observeEnergySum(date.toEpochDays())

    override suspend fun createEntry(entry: WorkoutEntry): WorkoutEntryId =
        WorkoutEntryId(dao.insertEntry(entry.toEntity().copy(id = 0)))

    override suspend fun updateEntry(entry: WorkoutEntry) {
        dao.updateEntry(entry.toEntity())
    }

    override suspend fun deleteEntry(id: WorkoutEntryId) {
        dao.deleteEntry(id.value)
    }

    override suspend fun readStepExclusionWindows(
        dates: List<LocalDate>
    ): Map<LocalDate, List<IntRange>> {
        val entries = dao.readEntries(dates.distinct().map { it.toEpochDays() }).map { it.toModel() }
        if (entries.isEmpty()) return emptyMap()
        val exercises =
            dao.readExercises(entries.map { it.exerciseId.value }.distinct())
                .map { it.toModel() }
                .associateBy { it.id }
        return entries
            .mapNotNull { entry ->
                val exercise = exercises[entry.exerciseId] ?: return@mapNotNull null
                entry.stepExclusionWindow(exercise)?.let { entry.date to it }
            }
            .groupBy({ it.first }, { it.second })
    }

    override fun observeMilestones(): Flow<List<WorkoutMilestone>> =
        dao.observeMilestones().map { list -> list.map { it.toModel() } }

    override suspend fun saveMilestone(milestone: WorkoutMilestone): WorkoutMilestoneId {
        val entity = milestone.toEntity()
        return if (entity.id == 0L) WorkoutMilestoneId(dao.insertMilestone(entity))
        else {
            dao.updateMilestone(entity)
            milestone.id
        }
    }

    override suspend fun deleteMilestone(id: WorkoutMilestoneId) {
        dao.deleteMilestone(id.value)
    }
}

private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default

internal fun WorkoutExerciseEntity.toModel(): WorkoutExercise =
    WorkoutExercise(
        id = WorkoutExerciseId(id),
        name = name,
        kind = enumOrDefault(kind, WorkoutKind.Strength),
        measure = enumOrDefault(measure, StrengthMeasure.Reps),
        usesWeight = usesWeight,
        weightOptionsKg =
            weightOptionsKg.split(';').mapNotNull { it.trim().toDoubleOrNull() }.distinct().sorted(),
        note = note,
        kcalMode = enumOrDefault(kcalMode, StrengthKcalMode.PerSet),
        kcalValue = kcalValue,
        helpsWith = helpsWith,
        reminderDays = reminderDays,
        cardioConversion = enumOrDefault(cardioConversion, CardioConversion.Device),
        cardioFactor = cardioFactor,
        cardioDiscountPercent = cardioDiscountPercent,
        askDistance = askDistance,
        excludeSteps = excludeSteps,
        archived = archived,
        createdAt = Instant.fromEpochSeconds(createdEpochSeconds),
    )

internal fun WorkoutExercise.toEntity(): WorkoutExerciseEntity =
    WorkoutExerciseEntity(
        id = id.value,
        name = name,
        kind = kind.name,
        measure = measure.name,
        usesWeight = usesWeight,
        weightOptionsKg = weightOptionsKg.distinct().sorted().joinToString(";"),
        note = note,
        kcalMode = kcalMode.name,
        kcalValue = kcalValue,
        helpsWith = helpsWith,
        reminderDays = reminderDays,
        cardioConversion = cardioConversion.name,
        cardioFactor = cardioFactor,
        cardioDiscountPercent = cardioDiscountPercent,
        askDistance = askDistance,
        excludeSteps = excludeSteps,
        archived = archived,
        createdEpochSeconds = createdAt.epochSeconds,
    )

internal fun WorkoutEntryEntity.toModel(): WorkoutEntry =
    WorkoutEntry(
        id = WorkoutEntryId(id),
        exerciseId = WorkoutExerciseId(exerciseId),
        date = LocalDate.fromEpochDays(dateEpochDay),
        minuteOfDay = minuteOfDay,
        reps = reps,
        seconds = seconds,
        level = level,
        weightKg = weightKg,
        durationMinutes = durationMinutes,
        deviceKcal = deviceKcal,
        distanceMeters = distanceMeters,
        energyKcal = energyKcal,
        feel = feel?.let { name -> WorkoutFeel.entries.firstOrNull { it.name == name } },
        createdAt = Instant.fromEpochSeconds(createdEpochSeconds),
        updatedAt = Instant.fromEpochSeconds(updatedEpochSeconds),
    )

internal fun WorkoutEntry.toEntity(): WorkoutEntryEntity =
    WorkoutEntryEntity(
        id = id.value,
        exerciseId = exerciseId.value,
        dateEpochDay = date.toEpochDays(),
        minuteOfDay = minuteOfDay,
        reps = reps,
        seconds = seconds,
        level = level,
        weightKg = weightKg,
        durationMinutes = durationMinutes,
        deviceKcal = deviceKcal,
        distanceMeters = distanceMeters,
        energyKcal = energyKcal,
        feel = feel?.name,
        createdEpochSeconds = createdAt.epochSeconds,
        updatedEpochSeconds = updatedAt.epochSeconds,
    )

internal fun WorkoutMilestoneEntity.toModel(): WorkoutMilestone =
    WorkoutMilestone(WorkoutMilestoneId(id), title, description, achieved, sortOrder)

internal fun WorkoutMilestone.toEntity(): WorkoutMilestoneEntity =
    WorkoutMilestoneEntity(id.value, title, description, achieved, sortOrder)
