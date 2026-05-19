package com.maksimowiczm.foodyou.app.ui.food.pending

import com.maksimowiczm.foodyou.common.csv.CsvParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList

internal const val PendingProductChatGptPrompt =
    "Analyze the attached product nutrition photos. Return exactly one CSV with this header " +
        "and exactly one data row: " +
        "name,brand,barcode,packageWeight,servingWeight,isLiquid,energyKcal,proteins,carbohydrates,fats. " +
        "Use values per 100 g/ml for nutrition. Use grams for macros, kcal for energy, and g/ml " +
        "for weights. Use true or false for isLiquid. Leave fields empty when unsure. Do not use " +
        "Markdown code blocks or any explanation."

internal data class PendingProductCsvData(
    val name: String?,
    val brand: String?,
    val barcode: String?,
    val packageWeight: Double?,
    val servingWeight: Double?,
    val isLiquid: Boolean?,
    val energyKcal: Double?,
    val proteins: Double?,
    val carbohydrates: Double?,
    val fats: Double?,
)

internal sealed interface PendingProductCsvParseResult {
    data class Success(val data: PendingProductCsvData) : PendingProductCsvParseResult

    data class Failure(val error: PendingProductCsvError) : PendingProductCsvParseResult
}

internal enum class PendingProductCsvError {
    Empty,
    InvalidHeader,
    InvalidDataRowCount,
    InvalidNumber,
}

internal fun interface PendingProductCsvParser {
    suspend fun parse(csv: String): PendingProductCsvParseResult
}

internal class PendingProductCsvParserImpl(private val csvParser: CsvParser) :
    PendingProductCsvParser {

    override suspend fun parse(csv: String): PendingProductCsvParseResult {
        if (csv.isBlank()) {
            return PendingProductCsvParseResult.Failure(PendingProductCsvError.Empty)
        }

        val csvInput = csv.withoutMarkdownFences()
        if (csvInput.isBlank()) {
            return PendingProductCsvParseResult.Failure(PendingProductCsvError.Empty)
        }

        val records =
            try {
                csvParser
                    .parse(csvInput.encodeToByteArray().toList().asFlow())
                    .toList()
                    .filterNot { it.isBlankRecord() }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return PendingProductCsvParseResult.Failure(
                    PendingProductCsvError.InvalidDataRowCount
                )
            }

        if (records.isEmpty()) {
            return PendingProductCsvParseResult.Failure(PendingProductCsvError.Empty)
        }

        val headerIndex = records.indexOfFirst { it.normalizedHeader() == NormalizedHeader }
        if (headerIndex == -1) {
            return PendingProductCsvParseResult.Failure(PendingProductCsvError.InvalidHeader)
        }

        val dataRows = records.drop(headerIndex + 1)
        if (dataRows.size != 1 || dataRows[0].size != Header.size) {
            return PendingProductCsvParseResult.Failure(PendingProductCsvError.InvalidDataRowCount)
        }

        val row = dataRows[0].map { it.normalizedValue() }

        fun number(index: Int): Double? {
            val value = row[index] ?: return null
            return value.toDoubleOrNullLenient()
        }

        val numbers =
            listOf(
                number(Header.indexOf("packageWeight")),
                number(Header.indexOf("servingWeight")),
                number(Header.indexOf("energyKcal")),
                number(Header.indexOf("proteins")),
                number(Header.indexOf("carbohydrates")),
                number(Header.indexOf("fats")),
            )

        if (numbers.indices.any { index -> numbers[index] == null && row[NumberIndexes[index]] != null }) {
            return PendingProductCsvParseResult.Failure(PendingProductCsvError.InvalidNumber)
        }

        return PendingProductCsvParseResult.Success(
            PendingProductCsvData(
                name = row[0],
                brand = row[1],
                barcode = row[2],
                packageWeight = numbers[0],
                servingWeight = numbers[1],
                isLiquid = row[5]?.toBooleanOrNullLenient(),
                energyKcal = numbers[2],
                proteins = numbers[3],
                carbohydrates = numbers[4],
                fats = numbers[5],
            )
        )
    }

    private companion object {
        val Header =
            listOf(
                "name",
                "brand",
                "barcode",
                "packageWeight",
                "servingWeight",
                "isLiquid",
                "energyKcal",
                "proteins",
                "carbohydrates",
                "fats",
            )

        val NormalizedHeader = Header.map { it.lowercase() }
        val NumberIndexes = listOf(3, 4, 6, 7, 8, 9)

        fun String.withoutMarkdownFences(): String =
            lineSequence()
                .filterNot { it.trim().startsWith("```") }
                .joinToString("\n")

        fun List<String?>.isBlankRecord(): Boolean = all { it.normalizedValue() == null }

        fun List<String?>.normalizedHeader(): List<String?> =
            mapIndexed { index, value ->
                value?.let {
                    if (index == 0) {
                        it.removePrefix("\uFEFF")
                    } else {
                        it
                    }
                }.normalizedValue()?.lowercase()
            }

        fun String?.normalizedValue(): String? =
            this?.replace('\u00A0', ' ')?.trim()?.takeIf(String::isNotEmpty)

        fun String.toDoubleOrNullLenient(): Double? =
            toDoubleOrNull() ?: replace(',', '.').toDoubleOrNull()

        fun String.toBooleanOrNullLenient(): Boolean? =
            when (trim().lowercase()) {
                "true", "1", "yes", "ja" -> true
                "false", "0", "no", "nein" -> false
                else -> null
            }
    }
}
