package com.maksimowiczm.foodyou.app.ui.food.product

internal data class NutritionLabelStableScanResult(
    val energy: NutritionLabelField?,
    val proteins: NutritionLabelField?,
    val fats: NutritionLabelField?,
    val carbohydrates: NutritionLabelField?,
    val hasPer100Basis: Boolean,
) {
    val fields: List<NutritionLabelField>
        get() = listOfNotNull(energy, proteins, fats, carbohydrates)
}

internal data class NutritionLabelScanAccumulatorState(
    val energy: NutritionLabelAccumulatedField,
    val proteins: NutritionLabelAccumulatedField,
    val fats: NutritionLabelAccumulatedField,
    val carbohydrates: NutritionLabelAccumulatedField,
) {
    val hasStableValues: Boolean
        get() = stableResult.fields.isNotEmpty()

    val stableResult: NutritionLabelStableScanResult
        get() =
            NutritionLabelStableScanResult(
                energy = energy.stable,
                proteins = proteins.stable,
                fats = fats.stable,
                carbohydrates = carbohydrates.stable,
                hasPer100Basis = hasStableValues,
            )
}

internal data class NutritionLabelAccumulatedField(
    val candidate: NutritionLabelField?,
    val stable: NutritionLabelField?,
    val candidateCount: Int,
)

internal class NutritionLabelScanAccumulator {
    private val fields = ScannedNutrient.entries.associateWith { FieldAccumulator() }

    fun add(result: NutritionLabelScanResult): NutritionLabelScanAccumulatorState {
        if (!result.hasPer100Basis) {
            return state()
        }

        fields.getValue(ScannedNutrient.Energy).add(result.energy)
        fields.getValue(ScannedNutrient.Proteins).add(result.proteins)
        fields.getValue(ScannedNutrient.Fats).add(result.fats)
        fields.getValue(ScannedNutrient.Carbohydrates).add(result.carbohydrates)

        return state()
    }

    fun state(): NutritionLabelScanAccumulatorState =
        NutritionLabelScanAccumulatorState(
            energy = fields.getValue(ScannedNutrient.Energy).state(),
            proteins = fields.getValue(ScannedNutrient.Proteins).state(),
            fats = fields.getValue(ScannedNutrient.Fats).state(),
            carbohydrates = fields.getValue(ScannedNutrient.Carbohydrates).state(),
        )

    private class FieldAccumulator {
        private var candidate: NutritionLabelField? = null
        private var candidateKey: String? = null
        private var candidateCount: Int = 0
        private var stable: NutritionLabelField? = null
        private var stableKey: String? = null

        fun add(field: NutritionLabelField?) {
            if (field == null || !field.isPer100Basis || !field.isPlausible()) {
                return
            }

            val key = field.stabilityKey()
            if (key == candidateKey) {
                candidateCount += 1
            } else {
                candidate = field
                candidateKey = key
                candidateCount = 1
            }

            if (stable == null && candidateCount >= STABLE_MATCHES) {
                stable = field
                stableKey = key
            } else if (stableKey != key && candidateCount >= REPLACEMENT_MATCHES) {
                stable = field
                stableKey = key
            }
        }

        fun state(): NutritionLabelAccumulatedField =
            NutritionLabelAccumulatedField(
                candidate = candidate.takeIf { stable == null },
                stable = stable,
                candidateCount = candidateCount,
            )
    }
}

private fun NutritionLabelField.isPlausible(): Boolean =
    when (unit) {
        NutritionLabelUnit.Kcal -> value >= 0f && value <= 900f
        NutritionLabelUnit.Kj -> value >= 0f && value <= 3800f
        NutritionLabelUnit.Gram -> value >= 0f && value < 100f
    }

private fun NutritionLabelField.stabilityKey(): String =
    "${nutrient.name}:${unit.name}:${(value * 10).toInt()}"

private const val STABLE_MATCHES = 2
private const val REPLACEMENT_MATCHES = 3
