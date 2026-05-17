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
    val sourceBounds: TextBounds? = null,
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
            return parseGeometryFallback(normalizedLines) ?: emptyResult()
        }

        val rows = normalizedLines.map(::tableRow)

        val strictResult =
            NutritionLabelScanResult(
                energy = findEnergy(rows, column),
                proteins =
                    findMacro(
                        rows = rows,
                        column = column,
                        nutrient = ScannedNutrient.Proteins,
                        keywords = proteinKeywords,
                    ),
                fats =
                    findMacro(
                        rows = rows,
                        column = column,
                        nutrient = ScannedNutrient.Fats,
                        keywords = fatKeywords,
                    ),
                carbohydrates =
                    findMacro(
                        rows = rows,
                        column = column,
                        nutrient = ScannedNutrient.Carbohydrates,
                        keywords = carbohydrateKeywords,
                    ),
                hasPer100Basis = true,
            )
        return strictResult.takeIf { it.fields.isNotEmpty() }
            ?: parseGeometryFallback(normalizedLines)
            ?: strictResult
    }

    private fun emptyResult(): NutritionLabelScanResult =
        NutritionLabelScanResult(
            energy = null,
            proteins = null,
            fats = null,
            carbohydrates = null,
            hasPer100Basis = false,
        )

    private fun findPer100Column(lines: List<RecognizedTextLine>): Column? =
        lines
            .flatMap { line -> per100Headers(line) }
            .filterNot { span -> containsExcludedHeader(span.text) }
            .minByOrNull { it.bounds.top }
            ?.let {
                Column(
                    centerX = it.bounds.centerX,
                    tolerance = max(54f, it.bounds.width * 1.25f),
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
        rows: List<TableRow>,
        column: Column,
    ): NutritionLabelField? =
        findNutrientRow(rows, energyKeywords)?.let { row ->
            val rowUnit = row.unitBefore(column)
            row.cells
                .filter { cell -> cell.isValueCell && column.contains(cell.bounds) }
                .mapNotNull { cell -> cell.toEnergyField(rowUnit) }
                .preferKcal()
        }

    private fun findMacro(
        rows: List<TableRow>,
        column: Column,
        nutrient: ScannedNutrient,
        keywords: List<String>,
    ): NutritionLabelField? =
        findNutrientRow(rows, keywords)?.let { row ->
            val rowUnit = row.unitBefore(column)
            row.cells
                .asSequence()
                .filter { cell -> cell.isValueCell && column.contains(cell.bounds) }
                .mapNotNull { cell -> cell.toMacroField(nutrient, rowUnit) }
                .firstOrNull()
        }

    private fun findNutrientRow(
        rows: List<TableRow>,
        keywords: List<String>,
    ): TableRow? =
        rows.firstOrNull { row ->
            row.hasExactNutrientLabel(keywords) && !row.isSubNutrientRow
        }

    private fun TableRow.hasExactNutrientLabel(keywords: List<String>): Boolean =
        labelText
            .split(" ")
            .windowed(2, 1, partialWindows = true)
            .any { words ->
                val phrase = words.joinToString(" ")
                keywords.any { keyword -> phrase == keyword || words.first() == keyword }
            } ||
            keywords.any { keyword -> labelText == keyword }

    private fun tableRow(line: RecognizedTextLine): TableRow {
        val elements = line.elements.sortedBy { it.bounds.left }
        val cells = textSpans(line).map { span -> TableCell(span.text, span.bounds) }
        val labelText =
            normalize(
                elements
                    .takeWhile { element -> !element.text.mayStartValueCell() }
                    .joinToString(" ") { it.text }
                    .ifBlank { line.text }
            )
        val labelRight =
            elements
                .takeWhile { element -> !element.text.mayStartValueCell() }
                .maxOfOrNull { it.bounds.right }
        return TableRow(labelText = labelText, labelRight = labelRight, cells = cells)
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

    private fun TableCell.toEnergyField(rowUnit: RowUnit?): NutritionLabelField? {
        if (containsPer100Basis(text) || containsExcludedHeader(text) || text.contains("%")) {
            return null
        }

        val matches = numberWithEnergyUnitRegex.findAll(text).toList()
        val selected =
            matches.firstOrNull { it.groupValues[2].equals("kcal", ignoreCase = true) }
                ?: matches.firstOrNull { it.groupValues[2].equals("kj", ignoreCase = true) }
                ?: return toEnergyFieldFromRowUnit(rowUnit)
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
            sourceBounds = bounds,
        )
    }

    private fun TableCell.toEnergyFieldFromRowUnit(rowUnit: RowUnit?): NutritionLabelField? {
        val energyUnit = rowUnit as? RowUnit.Energy ?: return null
        val values = bareNumberRegex.findAll(text).mapNotNull { it.value.parseDecimal() }.toList()
        val selected =
            energyUnit.units.zip(values).firstOrNull { (unit, _) -> unit == NutritionLabelUnit.Kcal }
                ?: energyUnit.units.zip(values).firstOrNull()
                ?: return null

        return NutritionLabelField(
            nutrient = ScannedNutrient.Energy,
            value = selected.second,
            unit = selected.first,
            sourceText = text,
            isPer100Basis = true,
            sourceBounds = bounds,
        )
    }

    private fun TableCell.toMacroField(
        nutrient: ScannedNutrient,
        rowUnit: RowUnit?,
    ): NutritionLabelField? {
        if (
            containsPer100Basis(text) ||
                containsExcludedHeader(text) ||
                text.contains("%") ||
                energyUnitRegex.containsMatchIn(text)
        ) {
            return null
        }

        val valueText =
            gramValueRegex.find(text)?.groupValues?.get(1)
                ?: bareNumberRegex.find(text)?.value?.takeIf { rowUnit == RowUnit.Gram }
                ?: return null
        if (valueText.isAmbiguousMacroInteger()) return null
        val value = valueText.parseDecimal() ?: return null
        if (value < 0f || value > 100f || value == 100f) {
            return null
        }

        return NutritionLabelField(
            nutrient = nutrient,
            value = value,
            unit = NutritionLabelUnit.Gram,
            sourceText = text,
            isPer100Basis = true,
            sourceBounds = bounds,
        )
    }

    private fun parseGeometryFallback(lines: List<RecognizedTextLine>): NutritionLabelScanResult? {
        val rows = geometryRows(lines)
        if (rows.size < 3) return null

        val fallbackColumns = findFallbackColumns(rows) ?: return null
        val valueColumn = fallbackColumns.valueColumn
        val gramRows =
            rows
                .mapNotNull { row -> row.toFallbackGramRow(fallbackColumns) }
                .sortedBy { it.row.bounds.top }
        if (gramRows.size < 3) return null

        val energy =
            findFallbackEnergy(
                rows = rows,
                valueColumn = valueColumn,
                firstGramRow = gramRows.first(),
            )
                ?: return null

        val readableMacroCount = gramRows.count { !it.row.isSubNutrientRow && it.row.fallbackNutrient() != null }
        val macrosByNutrient = fallbackMacrosByNutrient(gramRows)
        val fats = macrosByNutrient[ScannedNutrient.Fats]?.toFallbackMacroField(ScannedNutrient.Fats)
        val carbohydrates =
            macrosByNutrient[ScannedNutrient.Carbohydrates]?.toFallbackMacroField(
                ScannedNutrient.Carbohydrates
            )
        val proteins =
            macrosByNutrient[ScannedNutrient.Proteins]?.toFallbackMacroField(
                ScannedNutrient.Proteins
            )
        val macroCount = listOfNotNull(fats, carbohydrates, proteins).size
        if (macroCount < 2 && readableMacroCount == 0) return null

        return NutritionLabelScanResult(
            energy = energy,
            proteins = proteins,
            fats = fats,
            carbohydrates = carbohydrates,
            hasPer100Basis = true,
        )
    }

    private fun geometryRows(lines: List<RecognizedTextLine>): List<GeometryRow> {
        val elements =
            lines.flatMap { line ->
                line.elements.takeIf { it.isNotEmpty() }
                    ?: listOf(RecognizedTextElement(line.text, line.bounds))
            }
        if (elements.isEmpty()) return emptyList()

        val rows = mutableListOf<MutableList<RecognizedTextElement>>()
        elements.sortedBy { it.bounds.centerY }.forEach { element ->
            val row =
                rows.firstOrNull { current ->
                    abs(current.map { it.bounds.centerY }.average().toFloat() - element.bounds.centerY) <=
                        max(18f, element.bounds.height.toFloat())
                }
            if (row == null) {
                rows += mutableListOf(element)
            } else {
                row += element
            }
        }

        return rows.map { rowElements ->
            val sorted = rowElements.sortedBy { it.bounds.left }
            val text = sorted.joinToString(" ") { it.text }
            GeometryRow(
                text = text,
                normalizedText = normalize(text),
                bounds = sorted.map { it.bounds }.unionTextBounds(),
                cells = sorted.map { TableCell(it.text, it.bounds) },
            )
        }
    }

    private fun fallbackMacrosByNutrient(
        gramRows: List<FallbackGramRow>
    ): Map<ScannedNutrient, FallbackGramRow> {
        val selected = mutableMapOf<ScannedNutrient, FallbackGramRow>()
        val mainRows = gramRows.filterNot { it.row.isSubNutrientRow }

        mainRows
            .mapNotNull { row -> row.row.fallbackNutrient()?.let { nutrient -> nutrient to row } }
            .forEach { (nutrient, row) ->
                selected.putIfAbsent(nutrient, row)
            }

        val unlabeledRows =
            mainRows.filter { row -> row.row.fallbackNutrient() == null && row.row.isFallbackOrderReadable() }
        val order = listOf(ScannedNutrient.Fats, ScannedNutrient.Carbohydrates, ScannedNutrient.Proteins)
        if (selected.isEmpty() && unlabeledRows.size >= 4) {
            val orderedRows =
                listOfNotNull(
                    ScannedNutrient.Fats to unlabeledRows.first(),
                    unlabeledRows.getOrNull(2)?.let { ScannedNutrient.Carbohydrates to it },
                    unlabeledRows.lastOrNull()?.let { ScannedNutrient.Proteins to it },
                ).distinctBy { it.second }
            orderedRows.forEach { (nutrient, row) -> selected[nutrient] = row }
        } else {
            unlabeledRows.forEach { row ->
                val nutrient = order.firstOrNull { it !in selected.keys } ?: return@forEach
                selected[nutrient] = row
            }
        }

        return selected
    }

    private fun findFallbackColumns(rows: List<GeometryRow>): FallbackColumns? {
        val candidates =
            rows.flatMap { row ->
                row.numericCells().mapNotNull { cell ->
                    val valueText = bareNumberRegex.find(cell.text)?.value ?: return@mapNotNull null
                    if (valueText.isAmbiguousMacroInteger()) return@mapNotNull null
                    val value = valueText.parseDecimal() ?: return@mapNotNull null
                    if (value <= 0f || value >= 100f) return@mapNotNull null
                    val unit = row.gramUnitBefore(cell) ?: return@mapNotNull null
                    if (cell.bounds.left - unit.bounds.right !in 0..140) return@mapNotNull null
                    FallbackColumnCandidate(row = row, cell = cell, unit = unit)
                }
            }
        if (candidates.size < 2) return null

        val clusters = mutableListOf<MutableList<FallbackColumnCandidate>>()
        candidates.sortedBy { it.cell.bounds.centerX }.forEach { candidate ->
            val cluster =
                clusters.firstOrNull { existing ->
                    abs(existing.map { it.cell.bounds.centerX }.average().toFloat() - candidate.cell.bounds.centerX) <=
                        38f
                }
            if (cluster == null) {
                clusters += mutableListOf(candidate)
            } else {
                cluster += candidate
            }
        }

        val selected =
            clusters
                .filter { cluster ->
                    cluster.map { it.row.bounds.top }.distinct().size >= 2 &&
                        cluster.hasCoherentUnitColumn()
                }
                .maxWithOrNull(
                    compareBy<MutableList<FallbackColumnCandidate>> {
                        cluster ->
                        cluster.map { it.row.bounds.top }.distinct().size
                    }
                        .thenByDescending { cluster -> cluster.count { it.cell.text.hasDecimalSeparator() } }
                        .thenByDescending { cluster -> -cluster.map { it.cell.bounds.centerX }.average() }
                )
                ?: return null
        return FallbackColumns(
            valueColumn =
                Column(
                    centerX = selected.map { it.cell.bounds.centerX }.average().toFloat(),
                    tolerance = 42f,
                ),
            unitColumn =
                Column(
                    centerX = selected.map { it.unit.bounds.centerX }.average().toFloat(),
                    tolerance = 42f,
                ),
        )
    }

    private fun List<FallbackColumnCandidate>.hasCoherentUnitColumn(): Boolean =
        map { it.unit.bounds.centerX }.let { centers ->
            centers.maxOrNull()?.let { max -> centers.minOrNull()?.let { min -> max - min <= 42f } } == true
        }

    private fun GeometryRow.toFallbackGramRow(columns: FallbackColumns): FallbackGramRow? {
        val cell =
            numericCells()
                .filter { columns.valueColumn.contains(it.bounds) }
                .mapNotNull { cell ->
                    val valueText = bareNumberRegex.find(cell.text)?.value ?: return@mapNotNull null
                    if (valueText.isAmbiguousMacroInteger()) return@mapNotNull null
                    val value = valueText.parseDecimal() ?: return@mapNotNull null
                    if (value <= 0f || value >= 100f) return@mapNotNull null
                    FallbackGramRow(row = this, valueCell = cell, value = value)
                }
                .firstOrNull()
                ?: return null
        if (gramUnitBefore(cell.valueCell)?.let { columns.unitColumn.contains(it.bounds) } != true) {
            return null
        }
        return cell
    }

    private fun findFallbackEnergy(
        rows: List<GeometryRow>,
        valueColumn: Column,
        firstGramRow: FallbackGramRow,
    ): NutritionLabelField? =
        rows
            .filter { row -> row.bounds.top < firstGramRow.row.bounds.top }
            .filter { row -> row.isEnergyLikeRow() }
            .mapNotNull { row -> row.toFallbackEnergyField(valueColumn) }
            .preferKcal()

    private fun GeometryRow.toFallbackEnergyField(valueColumn: Column): NutritionLabelField? {
        val numbers = numericCells()
        if (numbers.isEmpty()) return null

        val kcalToken = cells.firstOrNull { normalize(it.text).contains("kcal") }
        if (kcalToken != null) {
            val kcalNumber =
                numbers
                    .filter { it.bounds.centerX <= kcalToken.bounds.centerX || it.bounds.centerX >= valueColumn.centerX }
                    .minByOrNull { abs(it.bounds.centerX - kcalToken.bounds.centerX) }
                    ?: return null
            return kcalNumber.toEnergyField(NutritionLabelUnit.Kcal)
        }

        val kjToken = cells.firstOrNull { normalize(it.text).contains("kj") }
        if (kjToken != null) {
            val kjNumber =
                numbers
                    .filter { it.bounds.centerX <= kjToken.bounds.centerX || it.bounds.centerX >= valueColumn.centerX }
                    .minByOrNull { abs(it.bounds.centerX - kjToken.bounds.centerX) }
                    ?: return null
            return kjNumber.toEnergyField(NutritionLabelUnit.Kj)
        }

        val values = numbers.mapNotNull { cell -> cell.text.parseDecimal()?.let { cell to it } }
        val pairedKcal =
            values
                .zipWithNext()
                .firstOrNull { (left, right) -> left.second >= 1000f && right.second in 100f..900f }
                ?.second
        if (pairedKcal != null) {
            return pairedKcal.first.toEnergyField(NutritionLabelUnit.Kcal)
        }

        return values.firstOrNull { (_, value) -> value >= 1000f }
            ?.first
            ?.toEnergyField(NutritionLabelUnit.Kj)
    }

    private fun TableCell.toEnergyField(unit: NutritionLabelUnit): NutritionLabelField? =
        NutritionLabelField(
            nutrient = ScannedNutrient.Energy,
            value = bareNumberRegex.find(text)?.value?.parseDecimal() ?: return null,
            unit = unit,
            sourceText = text,
            isPer100Basis = true,
            sourceBounds = bounds,
        )

    private fun FallbackGramRow.toFallbackMacroField(nutrient: ScannedNutrient): NutritionLabelField =
        NutritionLabelField(
            nutrient = nutrient,
            value = value,
            unit = NutritionLabelUnit.Gram,
            sourceText = valueCell.text,
            isPer100Basis = true,
            sourceBounds = valueCell.bounds,
        )

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

    private fun String.mayStartValueCell(): Boolean =
        contains(Regex("""\d|%|kcal|kj|g\b""", RegexOption.IGNORE_CASE))

    private fun String.isAmbiguousMacroInteger(): Boolean =
        !contains(',') && !contains('.') && toIntOrNull()?.let { it in 10..60 } == true

    private fun String.hasDecimalSeparator(): Boolean = contains(',') || contains('.')

    private data class TextSpan(val text: String, val bounds: TextBounds)

    private data class Column(val centerX: Float, val tolerance: Float)

    private data class GeometryRow(
        val text: String,
        val normalizedText: String,
        val bounds: TextBounds,
        val cells: List<TableCell>,
    ) {
        val isSubNutrientRow: Boolean =
            subNutrientPrefixes.any { normalizedText.startsWith(it) } ||
                subNutrientKeywords.any { normalizedText.contains(it) }

        fun numericCells(): List<TableCell> =
            cells.filter { cell ->
                cell.isValueCell &&
                    !cell.text.contains("%") &&
                    !containsPer100Basis(cell.text) &&
                    bareNumberRegex.containsMatchIn(cell.text)
            }

        fun gramUnitBefore(cell: TableCell): TableCell? =
            cells
                .filter { it.bounds.centerX < cell.bounds.centerX && it.isFallbackGramUnit() }
                .maxByOrNull { it.bounds.right }

        fun isEnergyLikeRow(): Boolean {
            val values = numericCells().mapNotNull { it.text.parseDecimal() }
            return energyKeywords.any { normalizedText.contains(it) } ||
                energyUnitRegex.containsMatchIn(normalizedText) ||
                values.zipWithNext().any { (left, right) -> left >= 1000f && right in 100f..900f }
        }

        fun fallbackNutrient(): ScannedNutrient? =
            when {
                hasMainNutrientLabel(fatKeywords) -> ScannedNutrient.Fats
                hasMainNutrientLabel(carbohydrateKeywords) -> ScannedNutrient.Carbohydrates
                hasMainNutrientLabel(proteinKeywords) -> ScannedNutrient.Proteins
                else -> null
            }

        fun isFallbackOrderReadable(): Boolean =
            normalizedText
                .split(" ")
                .filterNot { token -> token in fallbackGramUnitTokens || token in setOf("o") }
                .none { token -> token.length > 1 && token.any { it.isLetter() } }

        private fun hasMainNutrientLabel(keywords: List<String>): Boolean =
            !isSubNutrientRow &&
                keywords.any { keyword ->
                    normalizedText == keyword ||
                        normalizedText.startsWith("$keyword ") ||
                        normalizedText.contains(" $keyword ")
                }
    }

    private data class FallbackColumns(val valueColumn: Column, val unitColumn: Column)

    private data class FallbackColumnCandidate(
        val row: GeometryRow,
        val cell: TableCell,
        val unit: TableCell,
    )

    private data class FallbackGramRow(
        val row: GeometryRow,
        val valueCell: TableCell,
        val value: Float,
    )

    private data class TableRow(
        val labelText: String,
        val labelRight: Int?,
        val cells: List<TableCell>,
    ) {
        val isSubNutrientRow: Boolean =
            subNutrientPrefixes.any { labelText.startsWith(it) } ||
                subNutrientKeywords.any { labelText.contains(it) }

        fun unitBefore(column: Column): RowUnit? =
            cells
                .asSequence()
                .filter { cell ->
                    !cell.isValueCell &&
                        cell.bounds.centerX < column.centerX &&
                        labelRight?.let { cell.bounds.left >= it } != false
                }
                .sortedByDescending { it.bounds.right }
                .mapNotNull { it.toRowUnit() }
                .firstOrNull()
    }

    private data class TableCell(val text: String, val bounds: TextBounds) {
        val isValueCell: Boolean
            get() = text.any { it.isDigit() }

        fun toRowUnit(): RowUnit? {
            val normalized = normalize(text)
            if (normalized == "g" || normalized == "gram" || normalized == "grams") {
                return RowUnit.Gram
            }
            val energyUnits =
                energyUnitRegex
                    .findAll(normalized)
                    .map { match ->
                        if (match.value == "kcal") NutritionLabelUnit.Kcal else NutritionLabelUnit.Kj
                    }
                    .toList()
            return energyUnits.takeIf { it.isNotEmpty() }?.let(RowUnit::Energy)
        }

        fun isFallbackGramUnit(): Boolean {
            val trimmed = text.trim().lowercase()
            val normalized = normalize(trimmed)
            return normalized in fallbackGramUnitTokens || trimmed in fallbackDamagedGramUnitTokens
        }
    }

    private sealed interface RowUnit {
        data object Gram : RowUnit

        data class Energy(val units: List<NutritionLabelUnit>) : RowUnit
    }

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
    private val bareNumberRegex = Regex("""(?<![\p{L}\d])\d+(?:[,.]\d+)?(?![\p{L}\d])""")
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
    private val subNutrientPrefixes = listOf("davon", "of which", "of wich", "dont")
    private val subNutrientKeywords =
        listOf("saturated", "saturates", "fettsauren", "sugar", "sugars", "zucker", "sucre")
    private val fallbackGramUnitTokens = setOf("g", "gram", "grams", "o")
    private val fallbackDamagedGramUnitTokens = setOf("(g)", "()", "(o)", "(0)", "(q)")

    private const val MAX_VALUE_ELEMENTS = 3
}
