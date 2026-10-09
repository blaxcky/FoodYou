package com.maksimowiczm.foodyou.app.ui.food.diary.quickadd

import com.maksimowiczm.foodyou.common.infrastructure.csv.CsvParserImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class QuickAddCsvTableParserTest {
    private val parser = QuickAddCsvTableParserImpl(CsvParserImpl())

    @Test
    fun parsesRowsInOrderWithIndependentNutritionAndQuotedNames() = runTest {
        val csv = Header +
            "\"Reis, gekocht\",650,45,72,18\n" +
            "\"Apfel \"\"Gala\"\"\",80,0.4,18,0.2"
        assertEquals(
            QuickAddCsvTableParseResult.Success(listOf(
                QuickAddCsvData("Reis, gekocht", 650.0, 45.0, 72.0, 18.0),
                QuickAddCsvData("Apfel \"Gala\"", 80.0, 0.4, 18.0, 0.2),
            )),
            parser.parse(csv),
        )
    }

    @Test
    fun preservesLeadingTextBomWhitespaceAndMissingHeaderLineBreakSupport() = runTest {
        val expected = QuickAddCsvTableParseResult.Success(listOf(
            QuickAddCsvData("Reis", 650.0, 45.0, 72.0, 18.0),
            QuickAddCsvData("Huhn", 300.0, 35.0, 0.0, 10.0),
        ))
        for (header in listOf(
            "name,energy,proteins,carbohydrates,fats\n",
            "\uFEFF name, energy, proteins, carbohydrates, fats\r\n",
            "\uFEFF name, energy, proteins, carbohydrates, fats ",
        )) {
            assertEquals(
                expected,
                parser.parse("41s nachgedacht\nEinleitung mit einem \"Zitat\n" +
                    header + "Reis,650,45,72,18\r\nHuhn,300,35,0,10"),
            )
        }
    }

    @Test
    fun acceptsSingleRowAndReportsZeroRowsForHeaderOnly() = runTest {
        assertEquals(
            QuickAddCsvTableParseResult.Success(listOf(QuickAddCsvData("Reis", 650.0, 45.0, 72.0, 18.0))),
            parser.parse(Header + "Reis,650,45,72,18"),
        )
        assertEquals(QuickAddCsvTableParseResult.Success(emptyList()), parser.parse(Header))
    }

    @Test
    fun rejectsEntireTableWhenLaterRowIsInvalid() = runTest {
        val cases = listOf(
            "Huhn,abc,35,0,10" to QuickAddCsvError.InvalidNumber,
            "Huhn,NaN,35,0,10" to QuickAddCsvError.InvalidNumber,
            "Huhn,300,Infinity,0,10" to QuickAddCsvError.InvalidNumber,
            "Huhn,300,-35,0,10" to QuickAddCsvError.NegativeNumber,
            "\"\",300,35,0,10" to QuickAddCsvError.EmptyName,
            "Huhn,300,35,0" to QuickAddCsvError.InvalidDataRowCount,
            "Huhn,300,35,0,10,extra" to QuickAddCsvError.InvalidDataRowCount,
            "Zusätzlicher Text" to QuickAddCsvError.InvalidDataRowCount,
        )
        for ((row, error) in cases) {
            assertEquals(
                QuickAddCsvTableParseResult.Failure(error),
                parser.parse(Header + "Reis,650,45,72,18\n" + row),
            )
        }
        assertEquals(QuickAddCsvTableParseResult.Failure(QuickAddCsvError.Empty), parser.parse(" "))
        assertEquals(
            QuickAddCsvTableParseResult.Failure(QuickAddCsvError.InvalidHeader),
            parser.parse("title,energy,proteins,carbohydrates,fats\nReis,650,45,72,18"),
        )
    }

    private companion object {
        const val Header = "name,energy,proteins,carbohydrates,fats\n"
    }
}
