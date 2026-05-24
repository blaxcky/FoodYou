package com.maksimowiczm.foodyou.food.infrastructure.fddb

import com.maksimowiczm.foodyou.common.domain.food.NutrientValue
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.food.domain.entity.FddbProduct

class FddbProductParser {
    fun parse(html: String): FddbProduct {
        val lines = html.toTextLines()
        val servingUnit = lines.findServingUnit()
        val portions = lines.findPortions()

        return FddbProduct(
            name = html.tagText("h1", "fddb-headline1") ?: error("FDDB product name not found"),
            brand = html.tagText("h2", "fddb-headline2")?.toBrand(),
            barcode = lines.findBarcode(),
            isLiquid = servingUnit == "ml",
            packageWeight = portions.findPackageWeight(),
            servingWeight = portions.findServingWeight(),
            nutritionFacts =
                NutritionFacts(
                    proteins = lines.nutrient("Protein", "Eiweiß"),
                    carbohydrates = lines.nutrient("Carbohydrates", "Kohlenhydrate"),
                    energy = lines.nutrient("Calories", "Kalorien"),
                    fats = lines.nutrient("Fat", "Fett"),
                    saturatedFats =
                        lines.nutrient("thereof Saturates", "davon gesättigte Fettsäuren"),
                    sugars = lines.nutrient("thereof Sugar", "davon Zucker"),
                    dietaryFiber = lines.nutrient("Fiber", "Ballaststoffe"),
                    salt = lines.nutrient("Salt", "Salz"),
                    vitaminA = lines.nutrient("Retinol", "Vitamin A"),
                    vitaminB1 = lines.nutrient("Thiamine", "Vitamin B1"),
                    vitaminB2 = lines.nutrient("Riboflavin", "Vitamin B2"),
                    vitaminB3 = lines.nutrient("Niacin", "Vitamin B3"),
                    vitaminB5 = lines.nutrient("Pantothenic acid", "Vitamin B5"),
                    vitaminB6 = lines.nutrient("Vitamin B6"),
                    vitaminB7 = lines.nutrient("Biotin", "Vitamin B7"),
                    vitaminB9 = lines.nutrient("Folic acid", "Folsäure", "Vitamin B9"),
                    vitaminB12 = lines.nutrient("Vitamin B12"),
                    vitaminC = lines.nutrient("Vitamin C"),
                    vitaminD = lines.nutrient("Vitamin D"),
                    vitaminE = lines.nutrient("Vitamin E"),
                    vitaminK = lines.nutrient("Vitamin K"),
                    magnesium = lines.nutrient("Magnesium"),
                    potassium = lines.nutrient("Potassium", "Kalium"),
                    calcium = lines.nutrient("Calcium", "Kalzium"),
                    copper = lines.nutrient("Copper", "Kupfer"),
                    zinc = lines.nutrient("Zinc", "Zink"),
                    sodium = lines.nutrient("Sodium", "Natrium"),
                    iron = lines.nutrient("Iron", "Eisen"),
                    phosphorus = lines.nutrient("Phosphorus", "Phosphor"),
                    selenium = lines.nutrient("Selenium", "Selen"),
                    iodine = lines.nutrient("Iodine", "Jod"),
                ),
        )
    }
}

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

            FddbPortion(
                label = normalizedLabel,
                weight = match.groupValues[2].replace(',', '.').toDoubleOrNull()
                    ?: return@mapNotNull null,
            )
        }
        .filter { it.weight > 0.0 }

private fun List<FddbPortion>.findPackageWeight(): Double? =
    firstOrNull { portion -> PackageLabels.any { portion.label.contains(it) } }?.weight

private fun List<FddbPortion>.findServingWeight(): Double? =
    firstOrNull { portion -> ServingLabels.any { portion.label.contains(it) } }?.weight

private fun List<String>.nutrient(vararg labels: String): NutrientValue {
    val value =
        labels.firstNotNullOfOrNull { label ->
            valueAfterLabel(label)
        }
    return NutrientValue.from(value)
}

private fun List<String>.valueAfterLabel(label: String): Double? {
    forEachIndexed { index, line ->
        val labelIndex = line.indexOf(label, ignoreCase = true)
        if (labelIndex == -1) {
            return@forEachIndexed
        }

        line.drop(labelIndex + label.length).parseFddbNumber()?.let { return it }

        for (next in index + 1..minOf(index + 3, lastIndex)) {
            val nextLine = this[next]
            if (nextLine.isUnknown()) {
                return null
            }
            nextLine.parseFddbNumber()?.let { return it }
        }
    }

    return null
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

private data class FddbPortion(val label: String, val weight: Double)

private val PortionRegex =
    Regex("""^(.+?)\s*\(\s*([-+]?\d+(?:[,.]\d+)?)\s*(?:g|ml)\s*\)$""", RegexOption.IGNORE_CASE)

private val BasePortionRegex = Regex("""^100\s*(?:g|ml)$""", RegexOption.IGNORE_CASE)

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

private val ServingLabels =
    listOf(
        "stück",
        "stueck",
        "portion",
        "scheibe",
        "riegel",
        "stick",
        "sticks",
        "kugel",
        "tasse",
        "löffel",
        "loeffel",
        "teelöffel",
        "teeloeffel",
        "esslöffel",
        "essloeffel",
    )
