package com.maksimowiczm.foodyou.food.search.infrastructure.room

import androidx.room.RoomRawQuery
import com.maksimowiczm.foodyou.common.infrastructure.room.FoodSourceType
import com.maksimowiczm.foodyou.common.infrastructure.room.FoodSourceTypeConverter

/** Builds the same predicates for paged results and their counters. */
internal class FoodSearchTextQuery(
    query: String,
    private val sources: List<FoodSourceType>?,
    private val includeRecipes: Boolean,
    private val excludedRecipeId: Long?,
    private val nowEpochSeconds: Long? = null,
) {
    private val terms = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.distinct()

    fun build(countOnly: Boolean): RoomRawQuery {
        val arguments = mutableListOf<Any>()
        fun bind(value: Any): String {
            arguments += value
            return "?"
        }

        fun textPredicate(alias: String, ftsTable: String, fields: List<String>): String =
            if (terms.isEmpty()) "0"
            else terms.joinToString(" AND ") { term ->
                // FTS4 requires the prefix marker inside the quotes. Quoting also makes OR literal.
                val ftsQuery = "\"${term.replace("\"", "\"\"")}*\""
                val matches = mutableListOf(
                    "$alias.id IN (SELECT rowid FROM $ftsTable WHERE $ftsTable MATCH ${bind(ftsQuery)})"
                )
                val normalized = normalizeFoodSearchTerm(term)
                if (normalized.isNotEmpty()) {
                    fields.forEach { field ->
                        matches += "instr(${normalizeFoodSearchSql("$alias.$field")}, ${bind(normalized)}) > 0"
                    }
                }
                matches.joinToString(" OR ", "(", ")")
            }

        val sourcePredicate = when {
            sources == null -> "1"
            sources.isEmpty() -> "0"
            else -> sources.joinToString(", ", "p.sourceType IN (", ")") {
                bind(FoodSourceTypeConverter().fromFoodSourceType(it).toLong())
            }
        }
        val productTimePredicate = nowEpochSeconds?.let {
            "s.epochSeconds >= ${bind(it - RECENT_FOOD_SECONDS)}"
        } ?: "1"
        val productText = textPredicate("p", "ProductFts", listOf("name", "brand", "note"))
        val recipeExclusion = excludedRecipeId?.let {
            """
            r.id != ${bind(it)} AND NOT EXISTS (
                SELECT 1 FROM RecipeAllIngredientsView rai
                WHERE rai.targetRecipeId = r.id AND rai.ingredientId = ${bind(it)}
            )
            """.trimIndent()
        } ?: "1"
        val recipeTimePredicate = nowEpochSeconds?.let {
            "s.epochSeconds >= ${bind(it - RECENT_FOOD_SECONDS)}"
        } ?: "1"
        val recipeText = textPredicate("r", "RecipeFts", listOf("name", "note"))
        val recent = nowEpochSeconds != null
        val measurementColumns = if (recent && !countOnly) {
            ", s.type AS measurementType, s.value AS measurementValue, s.epochSeconds AS epochSeconds"
        } else ""
        val sql = """
            WITH ProductsSearch AS (
                SELECT ${if (countOnly) "1" else PRODUCT_FOOD_SEARCH_SQL_SELECT}$measurementColumns
                FROM Product p
                ${if (recent) "JOIN LatestMeasurementSuggestion s ON s.productId = p.id" else ""}
                WHERE $sourcePredicate AND $productTimePredicate AND $productText
            ),
            RecipesSearch AS (
                SELECT ${if (countOnly) "1" else RECIPE_FOOD_SEARCH_SQL_SELECT}$measurementColumns
                FROM Recipe r
                ${if (recent) "JOIN LatestMeasurementSuggestion s ON s.recipeId = r.id" else ""}
                WHERE ${if (includeRecipes) "1" else "0"}
                    AND $recipeExclusion AND $recipeTimePredicate AND $recipeText
            )
            ${when {
                countOnly -> "SELECT (SELECT COUNT(*) FROM ProductsSearch) + (SELECT COUNT(*) FROM RecipesSearch)"
                recent -> """
                    SELECT $FOOD_SEARCH_SQL_SELECT
                    FROM (SELECT * FROM ProductsSearch UNION ALL SELECT * FROM RecipesSearch)
                    ORDER BY epochSeconds DESC
                """
                else -> """
                    SELECT *, $LATEST_MEASUREMENT_SQL_SELECT FROM ProductsSearch p
                    UNION ALL
                    SELECT *, $LATEST_MEASUREMENT_SQL_SELECT FROM RecipesSearch p
                    ORDER BY isFavorite DESC, headline COLLATE NOCASE ASC
                """
            }}
        """.trimIndent()

        return RoomRawQuery(sql) { statement ->
            arguments.forEachIndexed { index, value ->
                when (value) {
                    is String -> statement.bindText(index + 1, value)
                    is Long -> statement.bindLong(index + 1, value)
                    else -> error("Unsupported food search argument: ${value::class}")
                }
            }
        }
    }
}

private const val RECENT_FOOD_SECONDS = 2_592_000L

// SQLite lower() folds ASCII only. Apply the same German substitutions on both sides.
private val germanSearchReplacements = listOf(
    "Ä" to "a", "ä" to "a", "Ö" to "o", "ö" to "o", "Ü" to "u", "ü" to "u",
    "ẞ" to "ss", "ß" to "ss", "\u0308" to "",
)

internal fun normalizeFoodSearchTerm(term: String): String =
    germanSearchReplacements.fold(term) { text, (from, to) -> text.replace(from, to) }
        .map { if (it in 'A'..'Z') it.lowercaseChar() else it }
        .joinToString("")

private fun normalizeFoodSearchSql(column: String): String =
    "lower(${germanSearchReplacements.fold("coalesce($column, '')") { sql, (from, to) ->
        "replace($sql, '$from', '$to')"
    }})"
