package com.maksimowiczm.foodyou.food.infrastructure.room

import android.app.Application
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.maksimowiczm.foodyou.app.infrastructure.room.FoodYouDatabase
import com.maksimowiczm.foodyou.app.infrastructure.room.ProductStandardPortionVisibilityMigration
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
class ProductStandardPortionsTest {
    @Test
    fun deletionSurvivesReloadAndSourceRefreshWithoutChangingReferenceWeights() = runTest {
        val database =
            Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                FoodYouDatabase::class.java,
            ).allowMainThreadQueries().build()
        try {
            val repository = RoomProductRepository(database.productDao, database.productPortionDao)
            val id = repository.insertProduct(
                name = "Corny Protein Kakao",
                brand = "Corny",
                barcode = "test-barcode",
                note = null,
                isLiquid = false,
                packageWeight = 20.0,
                servingWeight = 25.0,
                source = FoodSource(FoodSource.Type.FDDB, "https://example.test/corny"),
                nutritionFacts = NutritionFacts(),
            )
            val bar = ProductPortion("Riegel", 25.0, ProductPortion.Unit.Gram)
            val pack = ProductPortion("Packung", 20.0, ProductPortion.Unit.Gram)
            repository.replaceProductPortions(id, FoodSource.Type.FDDB, listOf(bar, pack))

            repository.updateProductPortions(id, listOf(bar))
            repository.hideProductStandardPortions(id, hidePackage = true, hideServing = false)

            val deleted = assertNotNull(repository.observeProduct(id).first())
            assertTrue(deleted.isPackagePortionHidden)
            assertFalse(deleted.isServingPortionHidden)
            assertEquals(listOf(bar), deleted.portions)
            assertEquals(40.0, deleted.weight(Measurement.Package(2.0)))
            assertEquals(25.0, deleted.weight(Measurement.Serving(1.0)))

            // Source synchronization copies the current product and replaces imported portions.
            repository.updateProduct(deleted.copy(packageWeight = 30.0, servingWeight = 35.0))
            repository.replaceProductPortions(id, FoodSource.Type.FDDB, listOf(bar, pack))
            val reloadedRepository =
                RoomProductRepository(database.productDao, database.productPortionDao)
            val refreshed = assertNotNull(reloadedRepository.observeProduct(id).first())
            assertTrue(refreshed.isPackagePortionHidden)
            assertEquals(listOf(bar), refreshed.portions)
            assertTrue(
                assertNotNull(repository.getProductByBarcode("test-barcode")).isPackagePortionHidden
            )
            assertTrue(
                assertNotNull(
                    repository.getProductBySource(FoodSource.Type.FDDB, "https://example.test/corny")
                ).isPackagePortionHidden
            )

            repository.hideProductStandardPortions(id, hidePackage = false, hideServing = true)
            val bothDeleted = assertNotNull(repository.observeProduct(id).first())
            assertTrue(bothDeleted.isPackagePortionHidden)
            assertTrue(bothDeleted.isServingPortionHidden)
        } finally {
            database.close()
        }
    }

    @Test
    fun version53MigrationKeepsExistingProductsAndMakesReferencePortionsVisible() {
        val schema =
            File("schemas/com.maksimowiczm.foodyou.app.infrastructure.room.FoodYouDatabase/53.json")
        val productSchema =
            Json.parseToJsonElement(schema.readText()).jsonObject.getValue("database").jsonObject
                .getValue("entities").jsonArray.first {
                    it.jsonObject.getValue("tableName").jsonPrimitive.content == "Product"
                }.jsonObject
        AndroidSQLiteDriver().open(":memory:").use { connection ->
            connection.execSQL(
                productSchema.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", "Product")
            )
            connection.execSQL(
                "INSERT INTO Product (id, name, sourceType, isLiquid, packageWeight, servingWeight) VALUES (1, 'Corny', 0, 0, 20, 25)"
            )

            ProductStandardPortionVisibilityMigration.migrate(connection)

            connection.prepare(
                "SELECT name, packageWeight, servingWeight, isPackagePortionHidden, isServingPortionHidden FROM Product WHERE id = 1"
            ).use {
                assertTrue(it.step())
                assertEquals("Corny", it.getText(0))
                assertEquals(20.0, it.getDouble(1))
                assertEquals(25.0, it.getDouble(2))
                assertEquals(0L, it.getLong(3))
                assertEquals(0L, it.getLong(4))
            }
        }
    }
}
