package com.maksimowiczm.foodyou.app.ui.food.pending

import com.maksimowiczm.foodyou.common.infrastructure.csv.CsvParserImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail
import kotlinx.coroutines.runBlocking

class PendingProductCsvParserTest {
    private val parser = PendingProductCsvParserImpl(CsvParserImpl())

    @Test
    fun parsesValidCsv() = runBlocking {
        val result =
            parser.parse(
                """
                name,brand,barcode,packageWeight,servingWeight,isLiquid,energyKcal,proteins,carbohydrates,fats
                "Apple Juice",Acme,1234567890123,1000,250,true,46,0.1,11.2,0
                """
                    .trimIndent()
            )

        assertEquals(
            PendingProductCsvData(
                name = "Apple Juice",
                brand = "Acme",
                barcode = "1234567890123",
                packageWeight = 1000.0,
                servingWeight = 250.0,
                isLiquid = true,
                energyKcal = 46.0,
                proteins = 0.1,
                carbohydrates = 11.2,
                fats = 0.0,
            ),
            result.dataOrFail(),
        )
    }

    @Test
    fun keepsEmptyCellsAsNull() = runBlocking {
        val result =
            parser.parse(
                """
                name,brand,barcode,packageWeight,servingWeight,isLiquid,energyKcal,proteins,carbohydrates,fats
                "Apple Juice",,,1000,,ja,46,,11.2,
                """
                    .trimIndent()
            )

        assertEquals(
            PendingProductCsvData(
                name = "Apple Juice",
                brand = null,
                barcode = null,
                packageWeight = 1000.0,
                servingWeight = null,
                isLiquid = true,
                energyKcal = 46.0,
                proteins = null,
                carbohydrates = 11.2,
                fats = null,
            ),
            result.dataOrFail(),
        )
    }

    @Test
    fun parsesChatGptCsvWithMissingHeaderLineBreak() = runBlocking {
        val result =
            parser.parse(
                "name,brand,barcode,packageWeight,servingWeight,isLiquid,energyKcal," +
                    "proteins,carbohydrates,fats Freeze Dried Strawberries,Acme,," +
                    "25,10,false,360,7.8,82.4,3.1"
            )

        assertEquals(
            PendingProductCsvData(
                name = "Freeze Dried Strawberries",
                brand = "Acme",
                barcode = null,
                packageWeight = 25.0,
                servingWeight = 10.0,
                isLiquid = false,
                energyKcal = 360.0,
                proteins = 7.8,
                carbohydrates = 82.4,
                fats = 3.1,
            ),
            result.dataOrFail(),
        )
    }

    @Test
    fun parsesHeaderAndDataRowSeparatedBySpaces() = runBlocking {
        val result =
            parser.parse(
                "name,brand,barcode,packageWeight,servingWeight,isLiquid,energyKcal," +
                    "proteins,carbohydrates,fats   \"Apple Juice\",Acme,1234567890123," +
                    "1000,250,true,46,0.1,11.2,0"
            )

        assertEquals("Apple Juice", result.dataOrFail().name)
    }

    @Test
    fun parsesMissingHeaderLineBreakWithHeaderWhitespaceBomAndCaseDifferences() = runBlocking {
        val result =
            parser.parse(
                "\uFEFF Name , BRAND , Barcode , PackageWeight , ServingWeight , IsLiquid , " +
                    "EnergyKcal , Proteins , Carbohydrates , Fats \"Apple Juice\",Acme," +
                    "1234567890123,1000,250,true,46,0.1,11.2,0"
            )

        assertEquals("Apple Juice", result.dataOrFail().name)
    }

    @Test
    fun parsesMissingHeaderLineBreakInsideMarkdownFence() = runBlocking {
        val result =
            parser.parse(
                """
                ```csv
                name,brand,barcode,packageWeight,servingWeight,isLiquid,energyKcal,proteins,carbohydrates,fats "Apple Juice",Acme,1234567890123,1000,250,true,46,0.1,11.2,0
                ```
                """
                    .trimIndent()
            )

        assertEquals("Apple Juice", result.dataOrFail().name)
    }

    @Test
    fun rejectsInvalidHeader() = runBlocking {
        val result =
            parser.parse(
                """
                title,brand,barcode,packageWeight,servingWeight,isLiquid,energyKcal,proteins,carbohydrates,fats
                "Apple Juice",Acme,1234567890123,1000,250,true,46,0.1,11.2,0
                """
                    .trimIndent()
            )

        assertEquals(PendingProductCsvError.InvalidHeader, result.errorOrFail())
    }

    @Test
    fun rejectsInvalidHeaderWithMissingLineBreakShape() = runBlocking {
        val result =
            parser.parse(
                "title,brand,barcode,packageWeight,servingWeight,isLiquid,energyKcal," +
                    "proteins,carbohydrates,fats \"Apple Juice\",Acme,1234567890123," +
                    "1000,250,true,46,0.1,11.2,0"
            )

        assertEquals(PendingProductCsvError.InvalidHeader, result.errorOrFail())
    }

    @Test
    fun rejectsMultipleDataRows() = runBlocking {
        val result =
            parser.parse(
                """
                name,brand,barcode,packageWeight,servingWeight,isLiquid,energyKcal,proteins,carbohydrates,fats
                "Apple Juice",Acme,1234567890123,1000,250,true,46,0.1,11.2,0
                "Orange Juice",Acme,1234567890124,1000,250,true,45,0.2,10.5,0
                """
                    .trimIndent()
            )

        assertEquals(PendingProductCsvError.InvalidDataRowCount, result.errorOrFail())
    }

    @Test
    fun rejectsMultipleDataRowsAfterMissingHeaderLineBreakRepair() = runBlocking {
        val result =
            parser.parse(
                """
                name,brand,barcode,packageWeight,servingWeight,isLiquid,energyKcal,proteins,carbohydrates,fats "Apple Juice",Acme,1234567890123,1000,250,true,46,0.1,11.2,0
                "Orange Juice",Acme,1234567890124,1000,250,true,45,0.2,10.5,0
                """
                    .trimIndent()
            )

        assertEquals(PendingProductCsvError.InvalidDataRowCount, result.errorOrFail())
    }

    @Test
    fun acceptsDecimalNumbersWithDot() = runBlocking {
        val result =
            parser.parse(
                """
                name,brand,barcode,packageWeight,servingWeight,isLiquid,energyKcal,proteins,carbohydrates,fats
                "Apple Juice",Acme,1234567890123,1000.5,250.25,yes,46.5,0.1,11.2,0.05
                """
                    .trimIndent()
            )

        assertEquals(1000.5, result.dataOrFail().packageWeight)
        assertEquals(250.25, result.dataOrFail().servingWeight)
        assertEquals(46.5, result.dataOrFail().energyKcal)
    }

    @Test
    fun ignoresTrailingBlankLines() = runBlocking {
        val result =
            parser.parse(
                """
                name,brand,barcode,packageWeight,servingWeight,isLiquid,energyKcal,proteins,carbohydrates,fats
                "Apple Juice",Acme,1234567890123,1000,250,true,46,0.1,11.2,0
                
                
                """
                    .trimIndent()
            )

        assertEquals("Apple Juice", result.dataOrFail().name)
    }

    @Test
    fun parsesCsvInsideMarkdownFence() = runBlocking {
        val result =
            parser.parse(
                """
                ```csv
                name,brand,barcode,packageWeight,servingWeight,isLiquid,energyKcal,proteins,carbohydrates,fats
                "Apple Juice",Acme,1234567890123,1000,250,true,46,0.1,11.2,0
                ```
                """
                    .trimIndent()
            )

        assertEquals("Apple Juice", result.dataOrFail().name)
    }

    @Test
    fun acceptsHeaderCaseAndWhitespaceDifferences() = runBlocking {
        val result =
            parser.parse(
                "\uFEFF Name , BRAND , Barcode , PackageWeight , ServingWeight , IsLiquid , " +
                    "EnergyKcal , Proteins , Carbohydrates , Fats \n" +
                    "\"Apple Juice\",Acme,1234567890123,1000,250,true,46,0.1,11.2,0"
            )

        assertEquals("Apple Juice", result.dataOrFail().name)
    }

    @Test
    fun acceptsQuotedDecimalCommaNumbers() = runBlocking {
        val result =
            parser.parse(
                """
                name,brand,barcode,packageWeight,servingWeight,isLiquid,energyKcal,proteins,carbohydrates,fats
                "Apple Juice",Acme,1234567890123,"1000,5","250,25",yes,"46,5","0,1","11,2","0,05"
                """
                    .trimIndent()
            )

        assertEquals(1000.5, result.dataOrFail().packageWeight)
        assertEquals(250.25, result.dataOrFail().servingWeight)
        assertEquals(46.5, result.dataOrFail().energyKcal)
    }

    @Test
    fun rejectsInvalidNumber() = runBlocking {
        val result =
            parser.parse(
                """
                name,brand,barcode,packageWeight,servingWeight,isLiquid,energyKcal,proteins,carbohydrates,fats
                "Apple Juice",Acme,1234567890123,heavy,250,true,46,0.1,11.2,0
                """
                    .trimIndent()
            )

        assertEquals(PendingProductCsvError.InvalidNumber, result.errorOrFail())
    }

    private fun PendingProductCsvParseResult.dataOrFail(): PendingProductCsvData =
        when (this) {
            is PendingProductCsvParseResult.Success -> data
            is PendingProductCsvParseResult.Failure -> fail("Expected success, got $error")
        }

    private fun PendingProductCsvParseResult.errorOrFail(): PendingProductCsvError =
        when (this) {
            is PendingProductCsvParseResult.Success -> fail("Expected failure, got $data")
            is PendingProductCsvParseResult.Failure -> error
        }
}
