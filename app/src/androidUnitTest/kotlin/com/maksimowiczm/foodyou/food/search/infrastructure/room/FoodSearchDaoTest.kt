package com.maksimowiczm.foodyou.food.search.infrastructure.room

import android.app.Application
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.maksimowiczm.foodyou.app.infrastructure.room.FoodYouDatabase
import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.common.domain.measurement.MeasurementType
import com.maksimowiczm.foodyou.common.infrastructure.room.FoodSourceType
import com.maksimowiczm.foodyou.food.infrastructure.repository.RoomProductRepository
import com.maksimowiczm.foodyou.food.infrastructure.room.MeasurementSuggestionEntity
import com.maksimowiczm.foodyou.food.infrastructure.room.RecipeEntity
import com.maksimowiczm.foodyou.food.infrastructure.room.RecipeIngredientEntity
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class FoodSearchDaoTest {
    private lateinit var database: FoodYouDatabase
    private val dao get() = database.foodSearchDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), FoodYouDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun findsCompoundProductsAndRecipesWithCaseAndUmlautVariants() = runTest {
        product("Semmelknödel")
        product("Marillenknödel")
        product("Knödel, Zwetschken")
        recipe("SEMMELKNO\u0308DEL")
        product("Reis")

        for (query in listOf("Knödel", "knodel", "KNÖDEL", "KNO\u0308DEL", "nöde")) {
            assertSearch(query, setOf(
                "Semmelknödel", "Marillenknödel", "Knödel, Zwetschken", "SEMMELKNO\u0308DEL",
            ))
        }
        assertSearch("Semmel", setOf("Semmelknödel", "SEMMELKNO\u0308DEL"))
    }

    @Test
    fun requiresEveryTermAcrossNameBrandAndNoteInAnyOrder() = runTest {
        product("Semmelknödel", brand = "Selbstgemacht", note = "Familienrezept")
        product("Marillenknödel", brand = "Andere Marke", note = null)
        recipe("Semmelknödel Rezept", note = "Selbstgemacht nach Familienrezept")

        val expected = setOf("Semmelknödel", "Semmelknödel Rezept")
        for (query in listOf(
            "Knödel selbstgemacht", "selbstgemacht knodel", "  knodel\tgemacht\nFamilien  ",
            "Knödel Knödel selbstgemacht",
        )) {
            assertSearch(query, expected)
        }
        assertSearch("knodel selbstgemacht fehlend", emptySet())
        assertSearch("familien", expected)
        assertSearch("marke", setOf("Marillenknödel"))
    }

    @Test
    fun preservesFtsAccentAndNonLatinCaseMatchingAlongsideGermanSubstrings() = runTest {
        product("Caféteria")
        product("БОРЩ")
        product("SÜẞSPEISE")
        product("Süßspeise")

        assertSearch("cafe", setOf("Caféteria"))
        assertSearch("бор", setOf("БОРЩ"))
        assertSearch("ssspeise", setOf("SÜẞSPEISE", "Süßspeise"))
    }

    @Test
    fun punctuationAndOperatorWordsCannotBecomeSearchSyntax() = runTest {
        product("Knödel")
        product("Knödel *")
        product("Knödel %")
        product("Knödel _")
        product("Knödel \"")
        product("Knödel OR")
        product("Knödel AND")

        for (term in listOf("*", "%", "_", "\"", "OR", "AND")) {
            assertSearch("Knödel $term", setOf("Knödel $term"))
        }
        assertSearch("Knödel' OR 1=1; --", emptySet())
        assertSearch("(", emptySet())
    }

    @Test
    fun sourceFiltersAndFavoriteOrderingKeepListsAndCountersInSync() = runTest {
        val user = product("Semmelknödel")
        product("Marillenknödel", source = FoodSource.Type.FDDB)
        product("Kartoffelknödel", source = FoodSource.Type.OpenFoodFacts)
        product("Reisknödel", source = FoodSource.Type.USDA)
        product("Brotknödel", source = FoodSource.Type.SwissFoodCompositionDatabase)
        recipe("Knödel Rezept")
        database.productDao.setProductFavorite(user, true)

        assertSearch("knödel", setOf("Semmelknödel", "Knödel Rezept"))
        for ((source, expected) in listOf(
            FoodSourceType.FDDB to "Marillenknödel",
            FoodSourceType.OpenFoodFacts to "Kartoffelknödel",
            FoodSourceType.USDA to "Reisknödel",
            FoodSourceType.SwissFoodCompositionDatabase to "Brotknödel",
        )) {
            assertSearch("knödel", setOf(expected), source)
        }
        val sources = listOf(FoodSourceType.User, FoodSourceType.FDDB)
        val rows = assertRows(
            dao.observeFoodByQueryFromSources("knödel", sources, true, null),
            dao.observeFoodCountByQueryFromSources("knödel", sources, true, null),
            setOf("Semmelknödel", "Marillenknödel", "Knödel Rezept"),
        )
        assertEquals(listOf("Semmelknödel", "Knödel Rezept", "Marillenknödel"), rows.map { it.name })
        assertRows(
            dao.observeFoodByQueryFromSources("knödel", sources, false, null),
            dao.observeFoodCountByQueryFromSources("knödel", sources, false, null),
            setOf("Semmelknödel", "Marillenknödel"),
        )
        assertRows(
            dao.observeFoodByQueryFromSources("knödel", emptyList(), false, null),
            dao.observeFoodCountByQueryFromSources("knödel", emptyList(), false, null),
            emptySet(),
        )
        assertSearch("knödel", setOf(
            "Semmelknödel", "Marillenknödel", "Kartoffelknödel", "Reisknödel", "Brotknödel",
        ), source = null)
    }

    @Test
    fun pagesThroughSubstringMatchesWithoutSkippingOrRepeatingFoods() = runTest {
        repeat(9) { product("$it Semmelknödel") }
        recipe("9 Knödel Rezept")
        val pages = dao.observeFoodByQuery("knodel", FoodSourceType.User, null)
        val first = pages.load(PagingSource.LoadParams.Refresh(null, 4, true))
        assertTrue(first is PagingSource.LoadResult.Page)
        val second = pages.load(PagingSource.LoadParams.Append(assertNotNull(first.nextKey), 4, true))
        assertTrue(second is PagingSource.LoadResult.Page)
        val third = pages.load(PagingSource.LoadParams.Append(assertNotNull(second.nextKey), 4, true))
        assertTrue(third is PagingSource.LoadResult.Page)
        val rows = first.data + second.data + third.data

        assertEquals(10, dao.observeFoodCountByQuery("knodel", FoodSourceType.User, null).first())
        assertEquals(10, rows.size)
        assertEquals((0..9).toList(), rows.map { it.name.first().digitToInt() })
        assertEquals(null, third.nextKey)
    }

    @Test
    fun recentlyUsedSearchKeepsTimeBoundaryLatestMeasurementAndOrder() = runTest {
        val semmel = product("Semmelknödel")
        val marille = product("Marillenknödel", source = FoodSource.Type.FDDB)
        val zwetschke = recipe("Zwetschkenknödel")
        val old = recipe("Alte Knödel")
        product("Nie verwendete Knödel")
        suggest(productId = semmel, at = NOW - 100, grams = 50.0)
        suggest(productId = semmel, at = NOW, grams = 150.0)
        suggest(productId = marille, at = NOW - MONTH)
        suggest(recipeId = zwetschke, at = NOW - 50)
        suggest(recipeId = old, at = NOW - MONTH - 1)

        val rows = assertRecent("knödel", setOf("Semmelknödel", "Marillenknödel", "Zwetschkenknödel"))
        assertEquals(listOf("Semmelknödel", "Zwetschkenknödel", "Marillenknödel"), rows.map { it.name })
        assertEquals(150.0, rows.first().measurementValue)
        assertEquals(MeasurementType.Gram, rows.first().measurementType)
    }

    @Test
    fun excludedRecipeAndItsParentsAreExcludedInEverySearchPath() = runTest {
        product("Semmelknödel")
        val child = recipe("Knödel Grundrezept")
        val parent = recipe("Knödel Pfanne", child = child)
        val grandparent = recipe("Knödel Auflauf", child = parent)
        val allowed = recipe("Knödel Ohne Grundrezept")
        for (id in listOf(child, parent, grandparent, allowed)) suggest(recipeId = id, at = NOW)

        assertSearch("knodel", setOf("Semmelknödel", "Knödel Ohne Grundrezept"), excluded = child)
        assertRows(
            dao.observeFoodByQueryFromSources("knodel", listOf(FoodSourceType.User), true, child),
            dao.observeFoodCountByQueryFromSources("knodel", listOf(FoodSourceType.User), true, child),
            setOf("Semmelknödel", "Knödel Ohne Grundrezept"),
        )
        assertRecent("knodel", setOf("Knödel Ohne Grundrezept"), excluded = child)
    }

    @Test
    fun productEditsInvalidatePagesAndUpdateActiveCounters() = runBlocking<Unit> {
        val id = product("Reis")
        val counts = Channel<Int>(Channel.UNLIMITED)
        val collector = launch {
            dao.observeFoodCountByQuery("knodel", FoodSourceType.User, null).collect { counts.send(it) }
        }
        suspend fun expectCount(expected: Int) = withTimeout(10_000) {
            while (counts.receive() != expected) { /* Skip coalesced invalidations. */ }
        }
        try {
            expectCount(0)
            suspend fun edit(name: String, brand: String? = null, note: String? = null) {
                val pages = dao.observeFoodByQuery("knodel", FoodSourceType.User, null)
                pages.rows()
                val invalidated = Channel<Unit>(Channel.CONFLATED)
                pages.registerInvalidatedCallback { invalidated.trySend(Unit) }
                val current = assertNotNull(database.productDao.observeProduct(id).first())
                database.productDao.updateProduct(current.copy(name = name, brand = brand, note = note))
                withTimeout(10_000) { invalidated.receive() }
                assertTrue(pages.invalid)
            }
            edit("Semmelknödel")
            expectCount(1)
            edit("Reis")
            expectCount(0)
            edit("Reis", brand = "Knödelmanufaktur")
            expectCount(1)
            edit("Reis", note = "Für Semmelknödel")
            expectCount(1)
            assertSearch("knodel", setOf("Reis"))
            edit("Reis")
            expectCount(0)
        } finally {
            collector.cancel()
            counts.close()
        }
    }

    @Test
    fun recipeEditsAndMeasurementChangesInvalidateRecentPagesAndCounters() = runBlocking<Unit> {
        val id = recipe("Rezept")
        val pages = dao.observeRecentFoodByQuery("knodel", NOW)
        pages.rows()
        val invalidated = Channel<Unit>(Channel.CONFLATED)
        pages.registerInvalidatedCallback { invalidated.trySend(Unit) }
        val counts = Channel<Int>(Channel.UNLIMITED)
        val collector = launch {
            dao.observeRecentFoodCountByQuery("knodel", NOW).collect { counts.send(it) }
        }
        try {
            withTimeout(10_000) { assertEquals(0, counts.receive()) }
            database.recipeDao.updateRecipeWithIngredients(
                RecipeEntity(id, "Semmelknödel", 1, null, false), emptyList(),
            )
            suggest(recipeId = id, at = NOW)
            withTimeout(10_000) {
                invalidated.receive()
                while (counts.receive() != 1) { /* Wait for both edits to be observed. */ }
            }
            assertTrue(pages.invalid)
            assertRecent("knodel", setOf("Semmelknödel"))
        } finally {
            collector.cancel()
            counts.close()
        }
    }

    private suspend fun assertSearch(
        query: String,
        expected: Set<String>,
        source: FoodSourceType? = FoodSourceType.User,
        excluded: Long? = null,
    ) = assertRows(
        dao.observeFoodByQuery(query, source, excluded),
        dao.observeFoodCountByQuery(query, source, excluded), expected,
    )

    private suspend fun assertRecent(query: String, expected: Set<String>, excluded: Long? = null) =
        assertRows(
            dao.observeRecentFoodByQuery(query, NOW, excluded),
            dao.observeRecentFoodCountByQuery(query, NOW, excluded), expected,
        )

    private suspend fun assertRows(
        pages: PagingSource<Int, FoodSearch>, counts: Flow<Int>, expected: Set<String>,
    ): List<FoodSearch> {
        val rows = pages.rows()
        assertEquals(expected, rows.map { it.name }.toSet())
        assertEquals(expected.size, rows.size, "FTS and substring matches must not duplicate rows")
        assertEquals(rows.size, counts.first(), "Counter must match the result list")
        return rows
    }

    private suspend fun PagingSource<Int, FoodSearch>.rows(): List<FoodSearch> {
        val result = load(PagingSource.LoadParams.Refresh(null, 100, false))
        if (result is PagingSource.LoadResult.Error) throw result.throwable
        assertTrue(result is PagingSource.LoadResult.Page)
        assertFalse(invalid)
        return result.data
    }

    private suspend fun product(
        name: String, brand: String? = null, note: String? = null,
        source: FoodSource.Type = FoodSource.Type.User,
    ): Long = RoomProductRepository(database.productDao, database.productPortionDao).insertProduct(
        name = name, brand = brand, barcode = null, note = note, isLiquid = false,
        packageWeight = null, servingWeight = null, source = FoodSource(source),
        nutritionFacts = NutritionFacts(),
    ).id

    private suspend fun recipe(name: String, note: String? = null, child: Long? = null): Long =
        database.recipeDao.insertRecipeWithIngredients(
            RecipeEntity(name = name, servings = 1, note = note, isLiquid = false),
            child?.let { listOf(RecipeIngredientEntity(
                ingredientProductId = null, ingredientRecipeId = it,
                measurement = MeasurementType.Gram, quantity = 100.0,
            )) } ?: emptyList(),
        )

    private suspend fun suggest(
        productId: Long? = null, recipeId: Long? = null, at: Long, grams: Double = 100.0,
    ) = database.measurementSuggestionDao.insert(
        MeasurementSuggestionEntity(
            productId = productId, recipeId = recipeId, type = MeasurementType.Gram,
            value = grams, epochSeconds = at,
        )
    )

    private companion object {
        const val NOW = 10_000_000L
        const val MONTH = 2_592_000L
    }
}
