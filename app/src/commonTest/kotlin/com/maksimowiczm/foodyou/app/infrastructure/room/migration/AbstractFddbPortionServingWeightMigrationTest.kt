package com.maksimowiczm.foodyou.app.infrastructure.room.migration

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.maksimowiczm.foodyou.app.infrastructure.room.FddbPortionServingWeightMigration
import com.maksimowiczm.foodyou.app.infrastructure.room.FddbServingWeightCleanupMigration
import kotlin.test.assertEquals
import kotlin.test.assertNull

abstract class AbstractFddbPortionServingWeightMigrationTest {
    abstract fun getTestHelper(): MigrationTestHelper

    open fun migrate() {
        val helper = getTestHelper()

        helper.createDatabase(40).apply {
            insertProduct(id = 1, sourceType = FDDB, servingWeight = 12.0)
            insertPortion(productId = 1, sourceType = FDDB, label = "Stück", amount = 12.0)

            insertProduct(id = 2, sourceType = FDDB, servingWeight = 12.0)
            insertPortion(productId = 2, sourceType = FDDB, label = "Stück", amount = 20.0)

            insertProduct(id = 3, sourceType = USER, servingWeight = 12.0)
            insertPortion(productId = 3, sourceType = FDDB, label = "Stück", amount = 12.0)

            insertProduct(id = 4, sourceType = FDDB, servingWeight = 200.0)
            insertPortion(
                productId = 4,
                sourceType = FDDB,
                label = "Glas",
                amount = 200.0,
                unit = "ml",
            )

            close()
        }

        helper
            .runMigrationsAndValidate(
                42,
                listOf(FddbPortionServingWeightMigration, FddbServingWeightCleanupMigration),
            )
            .use { connection ->
                assertNull(connection.servingWeight(productId = 1))
                assertNull(connection.servingWeight(productId = 2))
                assertEquals(12.0, connection.servingWeight(productId = 3))
                assertNull(connection.servingWeight(productId = 4))
                assertEquals(4, connection.portionCount())
            }
    }

    private fun SQLiteConnection.insertProduct(
        id: Long,
        sourceType: Int,
        servingWeight: Double,
    ) {
        execSQL(
            """
            INSERT INTO `Product` (`id`, `name`, `sourceType`, `isLiquid`, `servingWeight`)
            VALUES ($id, 'Product $id', $sourceType, 0, $servingWeight)
            """
                .trimIndent()
        )
    }

    private fun SQLiteConnection.insertPortion(
        productId: Long,
        sourceType: Int,
        label: String,
        amount: Double,
        unit: String = "g",
    ) {
        execSQL(
            """
            INSERT INTO `ProductPortion` (
                `productId`,
                `sourceType`,
                `label`,
                `normalizedLabel`,
                `amount`,
                `unit`
            )
            VALUES ($productId, $sourceType, '$label', '${label.lowercase()}', $amount, '$unit')
            """
                .trimIndent()
        )
    }

    private fun SQLiteConnection.servingWeight(productId: Long): Double? =
        prepare("SELECT `servingWeight` FROM `Product` WHERE `id` = $productId").use { statement ->
            statement.step()
            if (statement.isNull(0)) null else statement.getDouble(0)
        }

    private fun SQLiteConnection.portionCount(): Int =
        prepare("SELECT COUNT(*) FROM `ProductPortion`").use { statement ->
            statement.step()
            statement.getLong(0).toInt()
        }

    private companion object {
        const val USER = 0
        const val FDDB = 4
    }
}
