package com.maksimowiczm.foodyou.app.ui.food.diary.quickadd

import com.maksimowiczm.foodyou.common.infrastructure.csv.CsvParserImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail
import kotlinx.coroutines.runBlocking

class QuickAddCsvParserTest {
    private val parser = QuickAddCsvParserImpl(CsvParserImpl())

    @Test
    fun ignoresThinkingTimeBeforeCsv() = runBlocking {
        val result =
            parser.parse(
                """
                41s nachgedacht

                name,energy,proteins,carbohydrates,fats
                "Palatschinken, Speck-Gemüse-Pizza, Gyros-Huhn und Reis",1442,98.1,137.5,54.4
                """
                    .trimIndent()
            )

        assertEquals(
            QuickAddCsvData(
                name = "Palatschinken, Speck-Gemüse-Pizza, Gyros-Huhn und Reis",
                energyKcal = 1442.0,
                proteins = 98.1,
                carbohydrates = 137.5,
                fats = 54.4,
            ),
            result.dataOrFail(),
        )
    }

    @Test
    fun ignoresArbitraryLeadingTextWithHeaderFormattingVariants() = runBlocking {
        val prefix = "Hier ist deine Auswertung, inklusive Nährwerten.\nEinleitung mit einem \"Zitat\n\n"
        val headers =
            listOf(
                "name,energy,proteins,carbohydrates,fats\n",
                "\uFEFF name, energy, proteins, carbohydrates, fats\r\n",
                "\uFEFF name, energy, proteins, carbohydrates, fats ",
            )

        for (header in headers) {
            val result = parser.parse(prefix + header + "\"Reis\",650,45,72,18")

            assertEquals(QuickAddCsvData("Reis", 650.0, 45.0, 72.0, 18.0), result.dataOrFail())
        }
    }

    @Test
    fun stillRejectsInvalidCsvAfterLeadingText() = runBlocking {
        val header = "name,energy,proteins,carbohydrates,fats\n"
        val cases =
            listOf(
                "title,energy,proteins,carbohydrates,fats\nReis,650,45,72,18" to
                    QuickAddCsvError.InvalidHeader,
                "name,energy,proteins,carbohydrates,fatsExtra\nReis,650,45,72,18" to
                    QuickAddCsvError.InvalidHeader,
                header + "Reis,650,45,72,18\nHuhn,300,35,0,10" to
                    QuickAddCsvError.InvalidDataRowCount,
                header + "Reis,abc,45,72,18" to QuickAddCsvError.InvalidNumber,
                header + "Reis,650,-45,72,18" to QuickAddCsvError.NegativeNumber,
                header + "\"\",650,45,72,18" to QuickAddCsvError.EmptyName,
                header + "Reis,650,45,72,18\nZusätzlicher Text" to
                    QuickAddCsvError.InvalidDataRowCount,
            )

        for ((csv, expectedError) in cases) {
            assertEquals(expectedError, parser.parse("41s nachgedacht\n\n" + csv).errorOrFail())
        }
    }

    @Test
    fun parsesValidHeaderAndDataRow() = runBlocking {
        val result =
            parser.parse(
                """
                name,energy,proteins,carbohydrates,fats
                "Reis mit Huhn und Gemuese",650,45,72,18
                """
                    .trimIndent()
            )

        assertEquals(
            QuickAddCsvData(
                name = "Reis mit Huhn und Gemuese",
                energyKcal = 650.0,
                proteins = 45.0,
                carbohydrates = 72.0,
                fats = 18.0,
            ),
            result.dataOrFail(),
        )
    }

    @Test
    fun parsesQuotedMealNameWithComma() = runBlocking {
        val result =
            parser.parse(
                """
                name,energy,proteins,carbohydrates,fats
                "Reis, Huhn und Gemuese",650,45,72,18
                """
                    .trimIndent()
            )

        assertEquals("Reis, Huhn und Gemuese", result.dataOrFail().name)
    }

    @Test
    fun parsesHeaderWithBomAndWhitespace() = runBlocking {
        val result =
            parser.parse(
                "\uFEFF name, energy, proteins, carbohydrates, fats\n" +
                    "\"Reis\", 650, 45, 72, 18"
            )

        assertEquals("Reis", result.dataOrFail().name)
    }

    @Test
    fun parsesHeaderAndDataRowSeparatedBySpaces() = runBlocking {
        val result =
            parser.parse(
                "name,energy,proteins,carbohydrates,fats " +
                    "\"Reis mit Huhn und Gemuese\",650,45,72,18"
            )

        assertEquals(
            QuickAddCsvData(
                name = "Reis mit Huhn und Gemuese",
                energyKcal = 650.0,
                proteins = 45.0,
                carbohydrates = 72.0,
                fats = 18.0,
            ),
            result.dataOrFail(),
        )
    }

    @Test
    fun rejectsInvalidHeader() = runBlocking {
        val result =
            parser.parse(
                """
                title,energy,proteins,carbohydrates,fats
                "Reis",650,45,72,18
                """
                    .trimIndent()
            )

        assertEquals(QuickAddCsvError.InvalidHeader, result.errorOrFail())
    }

    @Test
    fun rejectsEmptyCsv() = runBlocking {
        val result = parser.parse("")

        assertEquals(QuickAddCsvError.Empty, result.errorOrFail())
    }

    @Test
    fun rejectsMissingDataRow() = runBlocking {
        val result = parser.parse("name,energy,proteins,carbohydrates,fats")

        assertEquals(QuickAddCsvError.InvalidDataRowCount, result.errorOrFail())
    }

    @Test
    fun rejectsMultipleDataRows() = runBlocking {
        val result =
            parser.parse(
                """
                name,energy,proteins,carbohydrates,fats
                "Reis",650,45,72,18
                "Huhn",300,35,0,10
                """
                    .trimIndent()
            )

        assertEquals(QuickAddCsvError.InvalidDataRowCount, result.errorOrFail())
    }

    @Test
    fun rejectsInvalidNumbers() = runBlocking {
        val result =
            parser.parse(
                """
                name,energy,proteins,carbohydrates,fats
                "Reis",abc,45,72,18
                """
                    .trimIndent()
            )

        assertEquals(QuickAddCsvError.InvalidNumber, result.errorOrFail())
    }

    @Test
    fun rejectsNegativeValues() = runBlocking {
        val result =
            parser.parse(
                """
                name,energy,proteins,carbohydrates,fats
                "Reis",650,-45,72,18
                """
                    .trimIndent()
            )

        assertEquals(QuickAddCsvError.NegativeNumber, result.errorOrFail())
    }

    @Test
    fun rejectsEmptyName() = runBlocking {
        val result =
            parser.parse(
                """
                name,energy,proteins,carbohydrates,fats
                "",650,45,72,18
                """
                    .trimIndent()
            )

        assertEquals(QuickAddCsvError.EmptyName, result.errorOrFail())
    }

    private fun QuickAddCsvParseResult.dataOrFail(): QuickAddCsvData =
        when (this) {
            is QuickAddCsvParseResult.Success -> data
            is QuickAddCsvParseResult.Failure -> fail("Expected success, got $error")
        }

    private fun QuickAddCsvParseResult.errorOrFail(): QuickAddCsvError =
        when (this) {
            is QuickAddCsvParseResult.Success -> fail("Expected failure, got $data")
            is QuickAddCsvParseResult.Failure -> error
        }
}
