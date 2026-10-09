package com.maksimowiczm.foodyou.app.ui.food.diary.quickadd

import com.maksimowiczm.foodyou.common.csv.CsvParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList

internal data class QuickAddCsvData(
    val name: String,
    val energyKcal: Double,
    val proteins: Double,
    val carbohydrates: Double,
    val fats: Double,
)

internal sealed interface QuickAddCsvParseResult {
    data class Success(val data: QuickAddCsvData) : QuickAddCsvParseResult

    data class Failure(val error: QuickAddCsvError) : QuickAddCsvParseResult
}

internal enum class QuickAddCsvError {
    Empty,
    InvalidHeader,
    InvalidDataRowCount,
    InvalidNumber,
    NegativeNumber,
    EmptyName,
}

internal fun interface QuickAddCsvParser {
    suspend fun parse(csv: String): QuickAddCsvParseResult
}

internal class QuickAddCsvParserImpl(csvParser: CsvParser) : QuickAddCsvParser {
    private val tableParser = QuickAddCsvTableParserImpl(csvParser)

    override suspend fun parse(csv: String): QuickAddCsvParseResult =
        when (val result = tableParser.parse(csv, singleRowOnly = true)) {
            is QuickAddCsvTableParseResult.Success ->
                QuickAddCsvParseResult.Success(result.data.single())
            is QuickAddCsvTableParseResult.Failure -> QuickAddCsvParseResult.Failure(result.error)
        }
}

internal sealed interface QuickAddCsvTableParseResult {
    data class Success(val data: List<QuickAddCsvData>) : QuickAddCsvTableParseResult
    data class Failure(val error: QuickAddCsvError) : QuickAddCsvTableParseResult
}

internal fun interface QuickAddCsvTableParser {
    suspend fun parse(csv: String): QuickAddCsvTableParseResult
}

internal class QuickAddCsvTableParserImpl(private val csvParser: CsvParser) : QuickAddCsvTableParser {

    override suspend fun parse(csv: String): QuickAddCsvTableParseResult =
        parse(csv, singleRowOnly = false)

    internal suspend fun parse(csv: String, singleRowOnly: Boolean): QuickAddCsvTableParseResult {
        if (csv.isBlank()) {
            return QuickAddCsvTableParseResult.Failure(QuickAddCsvError.Empty)
        }

        val normalizedCsv = csv.withoutLeadingText().normalizeMissingHeaderLineBreak()
        val records =
            try {
                csvParser.parse(normalizedCsv.encodeToByteArray().toList().asFlow()).toList()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return QuickAddCsvTableParseResult.Failure(QuickAddCsvError.InvalidDataRowCount)
            }

        if (records.firstOrNull()?.normalizedHeader() != Header) {
            return QuickAddCsvTableParseResult.Failure(QuickAddCsvError.InvalidHeader)
        }

        if (singleRowOnly && records.size != 2) {
            return QuickAddCsvTableParseResult.Failure(QuickAddCsvError.InvalidDataRowCount)
        }

        val data = mutableListOf<QuickAddCsvData>()
        for (row in records.drop(1)) {
            when (val result = parseRow(row)) {
                is QuickAddCsvParseResult.Success -> data += result.data
                is QuickAddCsvParseResult.Failure ->
                    return QuickAddCsvTableParseResult.Failure(result.error)
            }
        }
        return QuickAddCsvTableParseResult.Success(data)
    }

    private fun parseRow(row: List<String?>): QuickAddCsvParseResult {
        if (row.size != Header.size) {
            return QuickAddCsvParseResult.Failure(QuickAddCsvError.InvalidDataRowCount)
        }

        val name = row[0]?.trim()
        if (name.isNullOrEmpty()) {
            return QuickAddCsvParseResult.Failure(QuickAddCsvError.EmptyName)
        }

        val values =
            row.drop(1).map { value ->
                value?.trim()?.toDoubleOrNull()
                    ?: return QuickAddCsvParseResult.Failure(QuickAddCsvError.InvalidNumber)
            }

        if (values.any { !it.isFinite() }) {
            return QuickAddCsvParseResult.Failure(QuickAddCsvError.InvalidNumber)
        }

        if (values.any { it < 0 }) {
            return QuickAddCsvParseResult.Failure(QuickAddCsvError.NegativeNumber)
        }

        return QuickAddCsvParseResult.Success(
            QuickAddCsvData(
                name = name,
                energyKcal = values[0],
                proteins = values[1],
                carbohydrates = values[2],
                fats = values[3],
            )
        )
    }

    private companion object {
        val Header = listOf("name", "energy", "proteins", "carbohydrates", "fats")
        val HeaderStartRegex =
            Regex(
                "^[ \\t]*\\uFEFF?[ \\t]*name[ \\t]*,[ \\t]*energy[ \\t]*," +
                    "[ \\t]*proteins[ \\t]*,[ \\t]*carbohydrates[ \\t]*,[ \\t]*fats(?=\\s|$)",
                RegexOption.MULTILINE,
            )
        val MissingHeaderLineBreakRegex =
            Regex(
                "^\\s*\\uFEFF?\\s*name\\s*,\\s*energy\\s*,\\s*proteins\\s*,\\s*" +
                    "carbohydrates\\s*,\\s*fats[ \\t]+(?=\\S)"
            )

        fun String.withoutLeadingText(): String =
            HeaderStartRegex.find(this)?.let { substring(it.range.first) } ?: this

        fun String.normalizeMissingHeaderLineBreak(): String =
            replaceFirst(MissingHeaderLineBreakRegex, Header.joinToString(",") + "\n")

        fun List<String?>.normalizedHeader(): List<String?> =
            mapIndexed { index, value ->
                value?.let {
                    if (index == 0) {
                        it.removePrefix("\uFEFF")
                    } else {
                        it
                    }
                }?.trim()
            }
    }
}
