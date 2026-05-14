package com.maksimowiczm.foodyou.app.ui.food.product

import com.maksimowiczm.foodyou.barcodescanner.ui.RecognizedTextElement
import com.maksimowiczm.foodyou.barcodescanner.ui.RecognizedTextLine
import com.maksimowiczm.foodyou.barcodescanner.ui.TextBounds
import kotlin.math.abs
import kotlin.math.max

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
    fun parse(lines: List<RecognizedTextLine>): NutritionLabelScanResult {
        val normalizedLines = lines.filter { it.text.isNotBlank() }
        val column = findPer100Column(normalizedLines)

        if (column == null) {
            return NutritionLabelScanResult(
                energy = null,
                proteins = null,
                fats = null,
                carbohydrates = null,
                hasPer100Basis = false,
            )
        }

        val textSpans = normalizedLines.flatMap(::textSpans)

        return NutritionLabelScanResult(
            energy = findEnergy(normalizedLines, textSpans, column),
            proteins =
                findMacro(
                    lines = normalizedLines,
                    textSpans = textSpans,
                    column = column,
                    nutrient = ScannedNutrient.Proteins,
                    keywords = proteinKeywords,
                ),
            fats =
                findMacro(
                    lines = normalizedLines,
                    textSpans = textSpans,
                    column = column,
                    nutrient = ScannedNutrient.Fats,
                    keywords = fatKeywords,
                ),
            carbohydrates =
                findMacro(
                    lines = normalizedLines,
                    textSpans = textSpans,
                    column = column,
                    nutrient = ScannedNutrient.Carbohydrates,
                    keywords = carbohydrateKeywords,
                ),
            hasPer100Basis = true,
        )
    }

    private fun findPer100Column(lines: List<RecognizedTextLine>): Column? =
        lines
            .flatMap { line -> per100Headers(line) }
            .filterNot { span -> containsExcludedHeader(span.text) }
            .minByOrNull { it.bounds.top }
            ?.let {
                Column(
                    centerX = it.bounds.centerX,
                    tolerance = max(44f, it.bounds.width * 0.75f),
                )
            }

    private fun per100Headers(line: RecognizedTextLine): List<TextSpan> {
        val elementHeaders = mutableListOf<TextSpan>()
        val elements = line.elements.sortedBy { it.bounds.left }
        elements.forEachIndexed { index, _ ->
            for (endExclusive in (index + 1)..minOf(index + 4, elements.size)) {
                val group = elements.subList(index, endExclusive)
                val text = group.joinToString(" ") { it.text }
                if (containsPer100Basis(text) && !containsExcludedHeader(text)) {
                    elementHeaders += TextSpan(text = text, bounds = group.unionElementBounds())
                }
            }
        }

        if (elementHeaders.isNotEmpty()) {
            return elementHeaders
        }

        return if (containsPer100Basis(line.text) && !containsExcludedHeader(line.text)) {
            listOf(TextSpan(text = line.text, bounds = line.bounds))
        } else {
            emptyList()
        }
    }

    private fun findEnergy(
        lines: List<RecognizedTextLine>,
        textSpans: List<TextSpan>,
        column: Column,
    ): NutritionLabelField? =
        findNutrientLine(lines, energyKeywords)?.let { line ->
            textSpans
                .filter { span -> span.isInRow(line.bounds) && column.contains(span.bounds) }
                .mapNotNull { span -> span.toEnergyField() }
                .preferKcal()
        }

    private fun findMacro(
        lines: List<RecognizedTextLine>,
        textSpans: List<TextSpan>,
        column: Column,
        nutrient: ScannedNutrient,
        keywords: List<String>,
    ): NutritionLabelField? =
        findNutrientLine(lines, keywords)?.let { line ->
            textSpans
                .asSequence()
                .filter { span -> span.isInRow(line.bounds) && column.contains(span.bounds) }
                .mapNotNull { span -> span.toMacroField(nutrient) }
                .firstOrNull()
        }

    private fun findNutrientLine(
        lines: List<RecognizedTextLine>,
        keywords: List<String>,
    ): RecognizedTextLine? =
        lines.firstOrNull { line ->
            val normalized = normalize(line.text)
            keywords.any { normalized.contains(it) }
        }

    private fun textSpans(line: RecognizedTextLine): List<TextSpan> {
        val elements = line.elements.sortedBy { it.bounds.left }
        val spans = mutableListOf<TextSpan>()

        elements.forEachIndexed { index, _ ->
            for (endExclusive in (index + 1)..minOf(index + MAX_VALUE_ELEMENTS, elements.size)) {
                val group = elements.subList(index, endExclusive)
                spans += TextSpan(
                    text = group.joinToString(" ") { it.text },
                    bounds = group.unionElementBounds(),
                )
            }
        }

        return spans
    }

    private fun TextSpan.toEnergyField(): NutritionLabelField? {
        if (containsPer100Basis(text) || containsExcludedHeader(text) || text.contains("%")) {
            return null
        }

        val matches = numberWithEnergyUnitRegex.findAll(text).toList()
        val selected =
            matches.firstOrNull { it.groupValues[2].equals("kcal", ignoreCase = true) }
                ?: matches.firstOrNull { it.groupValues[2].equals("kj", ignoreCase = true) }
                ?: return null
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
            sourceText = text,
            isPer100Basis = true,
        )
    }

    private fun TextSpan.toMacroField(nutrient: ScannedNutrient): NutritionLabelField? {
        if (
            containsPer100Basis(text) ||
                containsExcludedHeader(text) ||
                text.contains("%") ||
                energyUnitRegex.containsMatchIn(text)
        ) {
            return null
        }

        val value =
            gramValueRegex.find(text)?.groupValues?.get(1)?.parseDecimal()
                ?: decimalNumberRegex.find(text)?.groupValues?.get(1)?.parseDecimal()
                ?: return null
        if (value < 0f || value > 100f || value == 100f) {
            return null
        }

        return NutritionLabelField(
            nutrient = nutrient,
            value = value,
            unit = NutritionLabelUnit.Gram,
            sourceText = text,
            isPer100Basis = true,
        )
    }

    private fun TextSpan.isInRow(rowBounds: TextBounds): Boolean =
        abs(bounds.centerY - rowBounds.centerY) <= max(rowBounds.height, bounds.height).toFloat()

    private fun Column.contains(bounds: TextBounds): Boolean =
        abs(bounds.centerX - centerX) <= tolerance

    private fun List<NutritionLabelField>.preferKcal(): NutritionLabelField? =
        firstOrNull { it.unit == NutritionLabelUnit.Kcal } ?: firstOrNull()

    private fun List<RecognizedTextElement>.unionElementBounds(): TextBounds =
        map { it.bounds }.unionTextBounds()

    private fun List<TextBounds>.unionTextBounds(): TextBounds =
        TextBounds(
            left = minOf { it.left },
            top = minOf { it.top },
            right = maxOf { it.right },
            bottom = maxOf { it.bottom },
        )

    private fun containsPer100Basis(text: String): Boolean {
        val normalized = normalize(text)
        return per100Regex.containsMatchIn(normalized) || bare100Regex.matches(normalized)
    }

    private fun containsExcludedHeader(text: String): Boolean {
        val normalized = normalize(text)
        return excludedHeaderRegex.containsMatchIn(normalized)
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

    private data class TextSpan(val text: String, val bounds: TextBounds)

    private data class Column(val centerX: Float, val tolerance: Float)

    private val TextBounds.centerX: Float
        get() = (left + right) / 2f

    private val TextBounds.centerY: Float
        get() = (top + bottom) / 2f

    private val TextBounds.width: Int
        get() = right - left

    private val TextBounds.height: Int
        get() = bottom - top

    private val gramValueRegex =
        Regex("""(?<![\p{L}\d])(\d+(?:[,.]\d+)?)\s*g\b""", RegexOption.IGNORE_CASE)
    private val decimalNumberRegex = Regex("""(?<![\p{L}\d])(\d+[,.]\d+)(?![\p{L}\d])""")
    private val numberWithEnergyUnitRegex =
        Regex("""(?<![\p{L}\d])(\d+(?:[,.]\d+)?)\s*(kcal|kj)\b""", RegexOption.IGNORE_CASE)
    private val energyUnitRegex = Regex("""\b(?:kcal|kj)\b""", RegexOption.IGNORE_CASE)
    private val per100Regex = Regex("""\b(?:pro|per|je)?\s*100\s*(?:g|gr|ml|milliliter)\b""")
    private val bare100Regex = Regex("""100\s*(?:g|gr|ml|milliliter)""")
    private val excludedHeaderRegex =
        Regex("""\b(?:portion|serving|servings|porcja|reference|intake|ri|250\s*g)\b""")

    private val energyKeywords = listOf("energie", "energy", "brennwert")
    private val proteinKeywords = listOf("eiweis", "eiweiss", "eiwei", "protein", "proteins")
    private val fatKeywords = listOf("fett", "fat")
    private val carbohydrateKeywords =
        listOf("kohlenhydrate", "kohlen hydrate", "carbohydrates", "carbs")

    private const val MAX_VALUE_ELEMENTS = 3
}
