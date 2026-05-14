package com.maksimowiczm.foodyou.app.ui.food.product

internal data class NutritionLabelScanResult(
    val energy: NutritionLabelField?,
    val proteins: NutritionLabelField?,
    val fats: NutritionLabelField?,
    val carbohydrates: NutritionLabelField?,
    val hasPer100Basis: Boolean,
) {
    val fields: List<NutritionLabelField>
        get() = listOfNotNull(energy, proteins, fats, carbohydrates)
}

internal data class NutritionLabelField(
    val nutrient: ScannedNutrient,
    val value: Float,
    val unit: NutritionLabelUnit,
    val sourceText: String,
    val isPer100Basis: Boolean,
)

internal enum class ScannedNutrient {
    Energy,
    Proteins,
    Fats,
    Carbohydrates,
}

internal enum class NutritionLabelUnit {
    Kcal,
    Kj,
    Gram,
}

internal object NutritionLabelParser {
    fun parse(lines: List<String>): NutritionLabelScanResult {
        val normalizedLines = lines.mapNotNull { line -> line.trim().takeIf { it.isNotBlank() } }
        val hasPer100Basis = normalizedLines.any(::containsPer100Basis)

        return NutritionLabelScanResult(
            energy = findEnergy(normalizedLines)?.copy(isPer100Basis = hasPer100Basis),
            proteins =
                findNutrient(normalizedLines, ScannedNutrient.Proteins, proteinKeywords)
                    ?.copy(isPer100Basis = hasPer100Basis),
            fats =
                findNutrient(normalizedLines, ScannedNutrient.Fats, fatKeywords)
                    ?.copy(isPer100Basis = hasPer100Basis),
            carbohydrates =
                findNutrient(normalizedLines, ScannedNutrient.Carbohydrates, carbohydrateKeywords)
                    ?.copy(isPer100Basis = hasPer100Basis),
            hasPer100Basis = hasPer100Basis,
        )
    }

    private fun findEnergy(lines: List<String>): NutritionLabelField? =
        lines.withIndex().firstNotNullOfOrNull { (index, line) ->
            val normalized = normalize(line)
            if (energyKeywords.none { normalized.contains(it) }) {
                return@firstNotNullOfOrNull null
            }

            findEnergyValue(line, sourceText = line)
                ?: lines
                    .drop(index + 1)
                    .take(FOLLOWING_VALUE_LOOKAHEAD)
                    .mapNotNull { followingLine ->
                        findEnergyValue(
                            line = followingLine,
                            sourceText = "$line | $followingLine",
                        )
                    }
                    .preferKcal()
        }

    private fun findEnergyValue(line: String, sourceText: String): NutritionLabelField? {
        if (containsPer100Basis(line)) {
            return null
        }

        val matches = numberWithOptionalUnitRegex.findAll(line).toList()
        val kcal = matches.firstOrNull { it.groupValues[2].equals("kcal", ignoreCase = true) }
        val kj = matches.firstOrNull { it.groupValues[2].equals("kj", ignoreCase = true) }
        val selected = kcal ?: kj ?: return null
        val unit =
            if (selected.groupValues[2].equals("kcal", ignoreCase = true)) {
                NutritionLabelUnit.Kcal
            } else {
                NutritionLabelUnit.Kj
            }

        return NutritionLabelField(
            nutrient = ScannedNutrient.Energy,
            value = selected.groupValues[1].parseDecimal() ?: return null,
            unit = unit,
            sourceText = sourceText,
            isPer100Basis = false,
        )
    }

    private fun List<NutritionLabelField>.preferKcal(): NutritionLabelField? =
        firstOrNull { it.unit == NutritionLabelUnit.Kcal } ?: firstOrNull()

    private fun findNutrient(
        lines: List<String>,
        nutrient: ScannedNutrient,
        keywords: List<String>,
    ): NutritionLabelField? =
        lines.withIndex().firstNotNullOfOrNull { (index, line) ->
            val normalized = normalize(line)
            if (keywords.none { normalized.contains(it) }) {
                return@firstNotNullOfOrNull null
            }

            val sameLineValue = findGramValue(line)
            val followingLine =
                if (sameLineValue == null) {
                    lines
                        .drop(index + 1)
                        .take(FOLLOWING_VALUE_LOOKAHEAD)
                        .firstOrNull { findGramValue(it) != null }
                } else {
                    null
                }
            val value =
                sameLineValue ?: followingLine?.let(::findGramValue)
                    ?: return@firstNotNullOfOrNull null

            NutritionLabelField(
                nutrient = nutrient,
                value = value,
                unit = NutritionLabelUnit.Gram,
                sourceText = followingLine?.let { "$line | $it" } ?: line,
                isPer100Basis = false,
            )
        }

    private fun findGramValue(line: String): Float? {
        if (containsPer100Basis(line) || containsServingBasis(line)) {
            return null
        }

        return gramValueRegex.find(line)?.groupValues?.get(1)?.parseDecimal()
            ?: numberRegex.findAll(line).firstOrNull()?.groupValues?.get(1)?.parseDecimal()
    }

    private fun containsPer100Basis(line: String): Boolean {
        val normalized = normalize(line)
        return per100Regex.containsMatchIn(normalized)
    }

    private fun containsServingBasis(line: String): Boolean {
        val normalized = normalize(line)
        return servingRegex.containsMatchIn(normalized)
    }

    private fun normalize(value: String): String =
        value
            .lowercase()
            .replace('ß', 's')
            .replace("eiwei0s", "eiweiss")
            .replace(Regex("[^a-z0-9]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun String.parseDecimal(): Float? = replace(',', '.').toFloatOrNull()

    private val numberRegex = Regex("""(?<![\p{L}\d])(\d+(?:[,.]\d+)?)(?![\p{L}\d])""")
    private val numberWithOptionalUnitRegex =
        Regex("""(?<!\d)(\d+(?:[,.]\d+)?)\s*(kcal|kj)?""", RegexOption.IGNORE_CASE)
    private val gramValueRegex =
        Regex("""(?<![\p{L}\d])(\d+(?:[,.]\d+)?)\s*g\b""", RegexOption.IGNORE_CASE)
    private val per100Regex = Regex("""\b(?:pro|per|je)?\s*100\s*(?:g|gr|ml|milliliter)\b""")
    private val servingRegex = Regex("""\b(?:portion|serving|porcja|250\s*g)\b""")

    private val energyKeywords = listOf("energie", "energy", "brennwert")
    private val proteinKeywords = listOf("eiweis", "eiweiss", "eiwei", "protein", "proteins")
    private val fatKeywords = listOf("fett", "fat")
    private val carbohydrateKeywords =
        listOf("kohlenhydrate", "kohlen hydrate", "carbohydrates", "carbs")

    private const val FOLLOWING_VALUE_LOOKAHEAD = 6
}
