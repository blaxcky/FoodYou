package com.maksimowiczm.foodyou.app.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.weight.domain.repository.WeightRepository
import com.maksimowiczm.foodyou.workout.domain.CardioConversion
import com.maksimowiczm.foodyou.workout.domain.StrengthKcalMode
import com.maksimowiczm.foodyou.workout.domain.StrengthMeasure
import com.maksimowiczm.foodyou.workout.domain.WorkoutExercise
import com.maksimowiczm.foodyou.workout.domain.WorkoutExerciseId
import com.maksimowiczm.foodyou.workout.domain.WorkoutKind
import com.maksimowiczm.foodyou.workout.domain.WorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Text fields stay strings while typing; [toExercise] parses them. */
internal data class WorkoutExerciseForm(
    val id: WorkoutExerciseId? = null,
    val kind: WorkoutKind = WorkoutKind.Strength,
    val name: String = "",
    val measure: StrengthMeasure = StrengthMeasure.Reps,
    val usesWeight: Boolean = false,
    val weightOptionsKg: List<Double> = emptyList(),
    val note: String = "",
    val kcalMode: StrengthKcalMode = StrengthKcalMode.PerSet,
    val kcalValue: String = "",
    val helpsWith: String = "",
    val reminderDays: String = "",
    val cardioConversion: CardioConversion = CardioConversion.Device,
    val cardioFactor: String = "1",
    val cardioDiscountPercent: String = "0",
    val askDistance: Boolean = false,
    val excludeSteps: Boolean = true,
    val archived: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
) {
    /** Only the fields visible for the chosen kind and conversion are checked. */
    val canSave: Boolean
        get() {
            if (name.isBlank() || isLoading || isSaving) return false
            return when (kind) {
                WorkoutKind.Strength ->
                    kcalValue.isBlank() || kcalValue.parseDecimal().let { it != null && it >= 0.0 }
                WorkoutKind.Cardio ->
                    (cardioConversion != CardioConversion.Factor ||
                        cardioFactor.parseDecimal().let { it != null && it >= 0.0 }) &&
                        (cardioDiscountPercent.isBlank() ||
                            cardioDiscountPercent.parseDecimal().let { it != null && it in 0.0..100.0 })
            }
        }
}

/** Parses "1,5" or "1.5"; infinite or NaN values count as invalid. */
internal fun String.parseDecimal(): Double? =
    trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

internal fun Double.formatDecimal(): String =
    if (this % 1.0 == 0.0) toLong().toString() else toString().replace('.', ',')

internal class WorkoutExerciseEditorViewModel(
    private val repository: WorkoutRepository,
    private val dateProvider: DateProvider,
    weightRepository: WeightRepository,
) : ViewModel() {
    private val mutableForm = MutableStateFlow(WorkoutExerciseForm())
    val form: StateFlow<WorkoutExerciseForm> = mutableForm.asStateFlow()

    val latestWeightKg: StateFlow<Double?> =
        weightRepository
            .observeEntries()
            .map { it.firstOrNull()?.weightKg }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), null)

    private var loadedId: Long? = -1

    fun load(id: Long?, kind: WorkoutKind?) {
        if (loadedId == id) return
        loadedId = id
        if (id == null) {
            mutableForm.value = WorkoutExerciseForm(kind = kind ?: WorkoutKind.Strength)
            return
        }
        mutableForm.value = WorkoutExerciseForm(isLoading = true)
        viewModelScope.launch {
            val exercise = repository.observeExercise(WorkoutExerciseId(id)).first()
            mutableForm.value = exercise?.toForm() ?: WorkoutExerciseForm()
        }
    }

    fun update(transform: WorkoutExerciseForm.() -> WorkoutExerciseForm) {
        mutableForm.value = mutableForm.value.transform()
    }

    fun save(onDone: () -> Unit) {
        val form = mutableForm.value
        if (!form.canSave) return
        // Blocks a second tap until the first save has finished.
        mutableForm.value = form.copy(isSaving = true)
        viewModelScope.launch {
            val existing = form.id?.let { repository.observeExercise(it).first() }
            val exercise = form.toExercise(existing?.createdAt ?: dateProvider.nowInstant())
            if (existing == null) repository.createExercise(exercise)
            else repository.updateExercise(exercise.copy(kind = existing.kind))
            onDone()
        }
    }

    fun setArchived(archived: Boolean, onDone: () -> Unit) {
        val id = mutableForm.value.id ?: return
        viewModelScope.launch {
            repository.setExerciseArchived(id, archived)
            onDone()
        }
    }
}

internal fun WorkoutExercise.toForm(): WorkoutExerciseForm =
    WorkoutExerciseForm(
        id = id,
        kind = kind,
        name = name,
        measure = measure,
        usesWeight = usesWeight,
        weightOptionsKg = weightOptionsKg,
        note = note,
        kcalMode = kcalMode,
        kcalValue = kcalValue.formatDecimal(),
        helpsWith = helpsWith,
        reminderDays = reminderDays?.toString().orEmpty(),
        cardioConversion = cardioConversion,
        cardioFactor = cardioFactor.formatDecimal(),
        cardioDiscountPercent = cardioDiscountPercent.formatDecimal(),
        askDistance = askDistance,
        excludeSteps = excludeSteps,
        archived = archived,
    )

internal fun WorkoutExerciseForm.toExercise(createdAt: kotlin.time.Instant): WorkoutExercise =
    WorkoutExercise(
        id = id ?: WorkoutExerciseId(0),
        name = name.trim(),
        kind = kind,
        measure = measure,
        usesWeight = usesWeight && kind == WorkoutKind.Strength,
        weightOptionsKg = weightOptionsKg.filter { it > 0.0 }.distinct().sorted(),
        note = note.trim(),
        kcalMode = kcalMode,
        kcalValue = kcalValue.parseDecimal()?.coerceAtLeast(0.0) ?: 0.0,
        helpsWith = helpsWith.trim(),
        reminderDays = reminderDays.trim().toIntOrNull()?.takeIf { it > 0 },
        cardioConversion = cardioConversion,
        cardioFactor = cardioFactor.parseDecimal()?.coerceAtLeast(0.0) ?: 1.0,
        cardioDiscountPercent = cardioDiscountPercent.parseDecimal()?.coerceIn(0.0, 100.0) ?: 0.0,
        askDistance = askDistance,
        excludeSteps = excludeSteps,
        archived = archived,
        createdAt = createdAt,
    )
