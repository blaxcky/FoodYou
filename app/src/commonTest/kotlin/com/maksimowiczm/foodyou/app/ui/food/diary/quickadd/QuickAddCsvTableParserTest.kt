package com.maksimowiczm.foodyou.app.ui.food.diary.quickadd

import com.maksimowiczm.foodyou.common.infrastructure.csv.CsvParserImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class QuickAddCsvTableParserTest {
    private val parser = QuickAddCsvTableParserImpl(CsvParserImpl())

    @Test
    fun restoresFiveCollapsedQuickCaptureEntriesWithoutChangingTheirValues() = runTest {
        assertEquals(
            QuickAddCsvTableParseResult.Success(CollapsedQuickCaptureCsvExample.rows),
            parser.parse(CollapsedQuickCaptureCsvExample.csv),
        )
    }

    @Test
    fun restoresMixedSeparatorsWithoutSplittingQuotedNames() = runTest {
        val expected = listOf(
            QuickAddCsvData("Reis, gekocht", 650.0, 45.0, 72.0, 18.0),
            QuickAddCsvData("Apfel \"Gala\"", 80.0, 0.4, 18.0, 0.2),
            QuickAddCsvData("Huhn\nmit Gemüse", 300.0, 35.0, 0.0, 10.0),
            QuickAddCsvData("2 Eier", 150.0, 12.0, 1.0, 10.0),
        )
        for (separator in listOf(" ", "\t", " \t  ")) {
            val csv = Header +
                "\"Reis, gekocht\",650,45,72,18" + separator +
                "\"Apfel \"\"Gala\"\"\",80,0.4,18,0.2\r\n" +
                "\"Huhn\nmit Gemüse\",300,35,0,10" + separator +
                "2 Eier,150,12,1,10\n"
            assertEquals(QuickAddCsvTableParseResult.Success(expected), parser.parse(csv))
        }
    }

    @Test
    fun restoresRowsWithQuotedNumbersAndWhitespaceAroundValues() = runTest {
        assertEquals(
            QuickAddCsvTableParseResult.Success(listOf(
                QuickAddCsvData("Reis", 650.0, 45.0, 72.0, 18.0),
                QuickAddCsvData("Huhn", 300.0, 35.0, 0.0, 10.0),
            )),
            parser.parse(Header + "Reis,\"650\",\"45\",\"72\",\"18\" Huhn, 300, 35, 0, 10  "),
        )
    }

    @Test
    fun failedRecoveryPreservesTheOriginalErrorAndRejectsTheWholeTable() = runTest {
        val invalidRows = listOf(
            "Huhn,abc,35,0,10",
            "Huhn,300,NaN,0,10",
            "Huhn,300,35,0,Infinity",
            "Huhn,300,-35,0,10",
            "\"\",300,35,0,10",
            "Huhn,300,35,0",
            "Huhn,300,35,0,10,extra",
            "Huhn,300,35,0,13\n\n.0",
            "Huhn,300,35,0,5.\n5",
            "Huhn,300,35,0,10\nZusätzlicher Text",
            "Huhn,300,35,0,10 Nachgestellter Text",
            "\"Huhn,300,35,0,10",
        )
        for (row in invalidRows) {
            assertEquals(
                QuickAddCsvTableParseResult.Failure(QuickAddCsvError.InvalidDataRowCount),
                parser.parse(Header + "Reis,650,45,72,18 " + row),
                row,
            )
        }
    }

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

internal object CollapsedQuickCaptureCsvExample {
    const val csv = "Untitled\n\n36s nachgedacht\n\n" +
        "name,energy,proteins,carbohydrates,fats " +
        "Palatschinken mit Marmelade,220,5.0,36.5,6.0 " +
        "Hühner Schnitzel,545,41.0,29.5,29.2 " +
        "Laugenstangerl,284,9.0,49.5,5.5 " +
        "Faschierter Braten aus dem Ofen,655,48.0,19.0,43.0 " +
        "Creme Spinat Semmel Knödel,340,13.0,43.0,13.0"

    val rows = listOf(
        QuickAddCsvData("Palatschinken mit Marmelade", 220.0, 5.0, 36.5, 6.0),
        QuickAddCsvData("Hühner Schnitzel", 545.0, 41.0, 29.5, 29.2),
        QuickAddCsvData("Laugenstangerl", 284.0, 9.0, 49.5, 5.5),
        QuickAddCsvData("Faschierter Braten aus dem Ofen", 655.0, 48.0, 19.0, 43.0),
        QuickAddCsvData("Creme Spinat Semmel Knödel", 340.0, 13.0, 43.0, 13.0),
    )
}
