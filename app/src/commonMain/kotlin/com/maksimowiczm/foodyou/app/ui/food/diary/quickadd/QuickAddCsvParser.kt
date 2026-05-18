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

internal class QuickAddCsvParserImpl(private val csvParser: CsvParser) : QuickAddCsvParser {

    override suspend fun parse(csv: String): QuickAddCsvParseResult {
        if (csv.isBlank()) {
            return QuickAddCsvParseResult.Failure(QuickAddCsvError.Empty)
        }

        val records =
            try {
                csvParser.parse(csv.encodeToByteArray().toList().asFlow()).toList()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return QuickAddCsvParseResult.Failure(QuickAddCsvError.InvalidDataRowCount)
            }

        if (records.firstOrNull() != Header) {
            return QuickAddCsvParseResult.Failure(QuickAddCsvError.InvalidHeader)
        }

        if (records.size != 2) {
            return QuickAddCsvParseResult.Failure(QuickAddCsvError.InvalidDataRowCount)
        }

        val row = records[1]
        if (row.size != Header.size) {
            return QuickAddCsvParseResult.Failure(QuickAddCsvError.InvalidDataRowCount)
        }

        val name = row[0]?.trim()
        if (name.isNullOrEmpty()) {
            return QuickAddCsvParseResult.Failure(QuickAddCsvError.EmptyName)
        }

        val values =
            row.drop(1).map { value ->
                value?.toDoubleOrNull()
                    ?: return QuickAddCsvParseResult.Failure(QuickAddCsvError.InvalidNumber)
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
    }
}
