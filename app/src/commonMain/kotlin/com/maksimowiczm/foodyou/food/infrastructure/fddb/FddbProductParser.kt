package com.maksimowiczm.foodyou.food.infrastructure.fddb

import com.maksimowiczm.foodyou.common.domain.food.NutrientValue
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.food.domain.entity.FddbPortion
import com.maksimowiczm.foodyou.food.domain.entity.FddbProduct

class FddbProductParser {
    fun parse(html: String): FddbProduct {
        val lines = html.toTextLines()
        val nutrientRows = html.nutrientRows()
        val servingUnit = lines.findServingUnit()
        val portions = lines.findPortions()

        return FddbProduct(
            name = html.tagText("h1", "fddb-headline1") ?: error("FDDB product name not found"),
            brand = html.tagText("h2", "fddb-headline2")?.toBrand(),
            barcode = lines.findBarcode(),
            isLiquid = servingUnit == "ml",
            packageWeight = portions.findPackageWeight(),
            servingWeight = null,
            portions = portions,
            nutritionFacts =
                NutritionFacts(
                    proteins = nutrientRows.nutrient("Protein", "Eiweiß"),
                    carbohydrates = nutrientRows.nutrient("Carbohydrates", "Kohlenhydrate"),
                    energy = nutrientRows.nutrient("Calories", "Kalorien"),
                    fats = nutrientRows.nutrient("Fat", "Fett"),
                    saturatedFats =
                        nutrientRows.nutrient("thereof Saturates", "davon gesättigte Fettsäuren"),
                    sugars = nutrientRows.nutrient("thereof Sugar", "davon Zucker"),
                    dietaryFiber = nutrientRows.nutrient("Fiber", "Ballaststoffe"),
                    salt = nutrientRows.nutrient("Salt", "Salz"),
                    vitaminA = nutrientRows.nutrient("Retinol", "Vitamin A"),
                    vitaminB1 = nutrientRows.nutrient("Thiamine", "Vitamin B1"),
                    vitaminB2 = nutrientRows.nutrient("Riboflavin", "Vitamin B2"),
                    vitaminB3 = nutrientRows.nutrient("Niacin", "Vitamin B3"),
                    vitaminB5 = nutrientRows.nutrient("Pantothenic acid", "Vitamin B5"),
                    vitaminB6 = nutrientRows.nutrient("Vitamin B6"),
                    vitaminB7 = nutrientRows.nutrient("Biotin", "Vitamin B7"),
                    vitaminB9 = nutrientRows.nutrient("Folic acid", "Folsäure", "Vitamin B9"),
                    vitaminB12 = nutrientRows.nutrient("Vitamin B12"),
                    vitaminC = nutrientRows.nutrient("Vitamin C"),
                    vitaminD = nutrientRows.nutrient("Vitamin D"),
                    vitaminE = nutrientRows.nutrient("Vitamin E"),
                    vitaminK = nutrientRows.nutrient("Vitamin K"),
                    magnesium = nutrientRows.nutrient("Magnesium"),
                    potassium = nutrientRows.nutrient("Potassium", "Kalium"),
                    calcium = nutrientRows.nutrient("Calcium", "Kalzium"),
                    copper = nutrientRows.nutrient("Copper", "Kupfer"),
                    zinc = nutrientRows.nutrient("Zinc", "Zink"),
                    sodium = nutrientRows.nutrient("Sodium", "Natrium"),
                    iron = nutrientRows.nutrient("Iron", "Eisen"),
                    phosphorus = nutrientRows.nutrient("Phosphorus", "Phosphor"),
                    selenium = nutrientRows.nutrient("Selenium", "Selen"),
                    iodine = nutrientRows.nutrient("Iodine", "Jod"),
                ),
        )
    }
}

private data class FddbNutrientRow(
    val label: String,
    val value: String,
)

private fun String.tagText(tag: String, id: String): String? =
    Regex(
            """<\s*$tag\b[^>]*\bid\s*=\s*["']$id["'][^>]*>(.*?)</\s*$tag\s*>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        .find(this)
        ?.groupValues
        ?.get(1)
        ?.stripTags()
        ?.decodeHtml()
        ?.trim()

private fun String.toBrand(): String? =
    replace(Regex("""\s+"""), " ")
        .split(',')
        .firstOrNull()
        ?.trim()
        ?.takeIf { it.isNotBlank() }

private fun String.toTextLines(): List<String> =
    replace(Regex("""<\s*br\s*/?\s*>""", RegexOption.IGNORE_CASE), "\n")
        .replace(
            Regex("""</\s*(tr|td|th|div|p|li|h[1-6])\s*>""", RegexOption.IGNORE_CASE),
            "\n",
        )
        .stripTags()
        .decodeHtml()
        .lineSequence()
        .map { it.replace(Regex("""\s+"""), " ").trim() }
        .filter { it.isNotBlank() }
        .toList()

private fun String.stripTags(): String = replace(Regex("""<[^>]+>"""), "\n")

private fun String.decodeHtml(): String =
    replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#34;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&micro;", "µ")
        .replace("&Ouml;", "Ö")
        .replace("&ouml;", "ö")
        .replace("&Auml;", "Ä")
        .replace("&auml;", "ä")
        .replace("&Uuml;", "Ü")
        .replace("&uuml;", "ü")
        .replace("&szlig;", "ß")

private fun String.nutrientRows(): List<FddbNutrientRow> =
    (tableNutrientRows() + sidNutrientRows()).sortedBy { it.first }.map { it.second }

private fun String.tableNutrientRows(): List<Pair<Int, FddbNutrientRow>> =
    TableRowRegex.findAll(this).mapNotNull { rowMatch ->
        val cells =
            TableCellRegex.findAll(rowMatch.groupValues[1])
                .map { it.groupValues[1].toNutrientCellText() }
                .filter { it.isNotBlank() }
                .toList()

        if (cells.size < 2) {
            null
        } else {
            rowMatch.range.first to
                FddbNutrientRow(label = cells[0].toNutrientLabel(), value = cells[1])
        }
    }.toList()

private fun String.sidNutrientRows(): List<Pair<Int, FddbNutrientRow>> =
    SidRowRegex.findAll(this).mapNotNull { match ->
        val label = match.groupValues[1].toNutrientCellText().toNutrientLabel()
        val value = match.groupValues[2].toNutrientCellText()
        if (label.isBlank() || value.isBlank()) {
            null
        } else {
            match.range.first to FddbNutrientRow(label = label, value = value)
        }
    }.toList()

private fun String.toNutrientCellText(): String =
    stripTags().decodeHtml().replace(Regex("""\s+"""), " ").trim()

private fun String.toNutrientLabel(): String = trim().trimEnd(':').trim()

private fun List<String>.findServingUnit(): String {
    val joined = joinToString(" ")
    return Regex("""(?:Nährwerte für|Data for)\s+100\s*(g|ml)\b""", RegexOption.IGNORE_CASE)
        .find(joined)
        ?.groupValues
        ?.get(1)
        ?.lowercase()
        ?: "g"
}

private fun List<String>.findBarcode(): String? =
    firstNotNullOfOrNull { line ->
        Regex("""\bEAN\s*:?\s*([0-9]{8,14})\b""", RegexOption.IGNORE_CASE)
            .find(line)
            ?.groupValues
            ?.get(1)
    }

private fun List<String>.findPortions(): List<FddbPortion> =
    mapNotNull { line ->
            val match = PortionRegex.matchEntire(line) ?: return@mapNotNull null
            val label = match.groupValues[1].trim()
            val normalizedLabel = label.lowercase()
            if (BasePortionRegex.matches(normalizedLabel)) {
                return@mapNotNull null
            }

            val amount =
                match.groupValues[2].replace(',', '.').toDoubleOrNull()
                    ?: return@mapNotNull null
            val unit =
                when (match.groupValues[3].lowercase()) {
                    "g" -> FddbPortion.Unit.Gram
                    "ml" -> FddbPortion.Unit.Milliliter
                    else -> return@mapNotNull null
                }

            FddbPortion(
                label = label,
                amount = amount,
                unit = unit,
            )
        }
        .filter { it.amount > 0.0 }

private fun List<FddbPortion>.findPackageWeight(): Double? =
    firstOrNull { portion ->
        portion.unit == FddbPortion.Unit.Gram &&
            PackageLabels.any { portion.label.contains(it, ignoreCase = true) }
    }?.amount

private fun List<FddbNutrientRow>.nutrient(vararg labels: String): NutrientValue {
    val value =
        firstNotNullOfOrNull { row ->
            if (labels.any { label -> row.label.equals(label, ignoreCase = true) }) {
                row.value.parseFddbNumber()
            } else {
                null
            }
        }
    return NutrientValue.from(value)
}

private fun String.isUnknown(): Boolean =
    lowercase().let { it == "k. a." || it == "k.a." || it == "n/a" || it == "-" }

private fun String.parseFddbNumber(): Double? {
    if (isUnknown()) {
        return null
    }

    return Regex("""[-+]?\d+(?:[,.]\d+)?""")
        .find(this)
        ?.value
        ?.replace(',', '.')
        ?.toDoubleOrNull()
}

private val PortionRegex =
    Regex("""^(.+?)\s*\(\s*([-+]?\d+(?:[,.]\d+)?)\s*(g|ml)\s*\)$""", RegexOption.IGNORE_CASE)

private val BasePortionRegex = Regex("""^100\s*(?:g|ml)$""", RegexOption.IGNORE_CASE)

private val TableRowRegex =
    Regex(
        """<\s*tr\b[^>]*>(.*?)</\s*tr\s*>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

private val TableCellRegex =
    Regex(
        """<\s*t[dh]\b[^>]*>(.*?)</\s*t[dh]\s*>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

private val SidRowRegex =
    Regex(
        """<\s*div\b[^>]*>\s*<\s*div\b[^>]*\bclass\s*=\s*["'][^"']*\bsidrow\b[^"']*["'][^>]*>(.*?)</\s*div\s*>\s*<\s*div\b[^>]*>(.*?)</\s*div\s*>\s*</\s*div\s*>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

private val PackageLabels =
    listOf(
        "packung",
        "becher",
        "flasche",
        "dose",
        "glas",
        "tube",
        "schale",
        "beutel",
        "sack",
        "karton",
    )
