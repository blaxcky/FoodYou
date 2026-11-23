package com.maksimowiczm.foodyou.workout.infrastructure

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "WorkoutExercise")
data class WorkoutExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: String,
    val measure: String,
    val usesWeight: Boolean,
    /** Semicolon separated kilograms, for example `4;6;8;10`. */
    val weightOptionsKg: String,
    val note: String,
    val kcalMode: String,
    val kcalValue: Double,
    val helpsWith: String,
    val reminderDays: Int?,
    val cardioConversion: String,
    val cardioFactor: Double,
    val cardioDiscountPercent: Double,
    val askDistance: Boolean,
    val excludeSteps: Boolean,
    val archived: Boolean,
    val createdEpochSeconds: Long,
)

@Entity(
    tableName = "WorkoutEntry",
    foreignKeys =
        [
            ForeignKey(
                entity = WorkoutExerciseEntity::class,
                parentColumns = ["id"],
                childColumns = ["exerciseId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices = [Index(value = ["dateEpochDay"]), Index(value = ["exerciseId"])],
)
data class WorkoutEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: Long,
    val dateEpochDay: Long,
    val minuteOfDay: Int,
    val reps: Int?,
    val seconds: Int?,
    val level: Int?,
    val weightKg: Double?,
    val durationMinutes: Int?,
    val deviceKcal: Double?,
    val distanceMeters: Int?,
    val energyKcal: Double,
    val feel: String?,
    val createdEpochSeconds: Long,
    val updatedEpochSeconds: Long,
)

@Entity(tableName = "WorkoutMilestone")
data class WorkoutMilestoneEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    val achieved: Boolean,
    val sortOrder: Int,
)

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM WorkoutExercise ORDER BY name COLLATE NOCASE")
    fun observeExercises(): Flow<List<WorkoutExerciseEntity>>

    @Query("SELECT * FROM WorkoutExercise WHERE id = :id")
    fun observeExercise(id: Long): Flow<WorkoutExerciseEntity?>

    @Query("SELECT * FROM WorkoutExercise WHERE id IN (:ids)")
    suspend fun readExercises(ids: List<Long>): List<WorkoutExerciseEntity>

    @Insert suspend fun insertExercise(exercise: WorkoutExerciseEntity): Long

    @Update suspend fun updateExercise(exercise: WorkoutExerciseEntity)

    @Query("UPDATE WorkoutExercise SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    @Query(
        "SELECT * FROM WorkoutEntry WHERE dateEpochDay BETWEEN :fromEpochDay AND :toEpochDay " +
            "ORDER BY dateEpochDay, minuteOfDay, id"
    )
    fun observeEntries(fromEpochDay: Long, toEpochDay: Long): Flow<List<WorkoutEntryEntity>>

    @Query("SELECT * FROM WorkoutEntry WHERE dateEpochDay IN (:epochDays)")
    suspend fun readEntries(epochDays: List<Long>): List<WorkoutEntryEntity>

    @Query("SELECT * FROM WorkoutEntry WHERE id = :id")
    fun observeEntry(id: Long): Flow<WorkoutEntryEntity?>

    @Query("SELECT COALESCE(SUM(energyKcal), 0.0) FROM WorkoutEntry WHERE dateEpochDay = :epochDay")
    fun observeEnergySum(epochDay: Long): Flow<Double>

    @Insert suspend fun insertEntry(entry: WorkoutEntryEntity): Long

    @Update suspend fun updateEntry(entry: WorkoutEntryEntity)

    @Query("DELETE FROM WorkoutEntry WHERE id = :id") suspend fun deleteEntry(id: Long)

    @Query("SELECT * FROM WorkoutMilestone ORDER BY sortOrder, id")
    fun observeMilestones(): Flow<List<WorkoutMilestoneEntity>>

    @Insert suspend fun insertMilestone(milestone: WorkoutMilestoneEntity): Long

    @Update suspend fun updateMilestone(milestone: WorkoutMilestoneEntity)

    @Query("DELETE FROM WorkoutMilestone WHERE id = :id") suspend fun deleteMilestone(id: Long)
}
