package com.maksimowiczm.foodyou.food.infrastructure.room

import android.app.Application
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.maksimowiczm.foodyou.app.infrastructure.room.FoodYouDatabase
import com.maksimowiczm.foodyou.app.infrastructure.room.ProductPreferredMealMigration
import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.food.domain.entity.ProductPortion
import com.maksimowiczm.foodyou.food.infrastructure.repository.RoomProductRepository
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class ProductPreferredMealTest {
    @Test fun preferenceSurvivesReloadAndProductRefresh() = runTest {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), FoodYouDatabase::class.java,
        ).allowMainThreadQueries().build()
        try {
            val repository = RoomProductRepository(database.productDao, database.productPortionDao)
            val id = repository.insertProduct(
                name = "Snack", brand = null, barcode = "123", note = null,
                isLiquid = false, packageWeight = null, servingWeight = null,
                source = FoodSource(FoodSource.Type.FDDB, "https://example.test/snack"),
                nutritionFacts = NutritionFacts(),
            )
            val original = assertNotNull(repository.observeProduct(id).first())
            assertEquals(null, original.preferredMealId)
            repository.updateProduct(original.copy(preferredMealId = 4))
            val reloaded = RoomProductRepository(database.productDao, database.productPortionDao)
            val saved = assertNotNull(reloaded.getProductByBarcode("123"))
            assertEquals(4L, saved.preferredMealId)
            reloaded.updateProduct(saved.copy(name = "Refreshed snack"))
            assertEquals(4L, assertNotNull(reloaded.observeProduct(id).first()).preferredMealId)
            reloaded.updateProduct(saved.copy(preferredMealId = null))
            assertEquals(null, assertNotNull(reloaded.observeProduct(id).first()).preferredMealId)
        } finally { database.close() }
    }

    @Test fun migrationPreservesProductsAndDefaultsToAutomatic() {
        val schema = File("schemas/com.maksimowiczm.foodyou.app.infrastructure.room.FoodYouDatabase/54.json")
        val productSchema = Json.parseToJsonElement(schema.readText()).jsonObject
            .getValue("database").jsonObject.getValue("entities").jsonArray.first {
                it.jsonObject.getValue("tableName").jsonPrimitive.content == "Product"
            }.jsonObject
        AndroidSQLiteDriver().open(":memory:").use { connection ->
            connection.execSQL(productSchema.getValue("createSql").jsonPrimitive.content
                .replace("\${TABLE_NAME}", "Product"))
            connection.execSQL("INSERT INTO Product (id, name, sourceType, isLiquid) VALUES (1, 'Snack', 0, 0)")
            ProductPreferredMealMigration.migrate(connection)
            connection.prepare("SELECT name, preferredMealId FROM Product WHERE id = 1").use {
                assertTrue(it.step())
                assertEquals("Snack", it.getText(0))
                assertTrue(it.isNull(1))
            }
        }
    }
}
