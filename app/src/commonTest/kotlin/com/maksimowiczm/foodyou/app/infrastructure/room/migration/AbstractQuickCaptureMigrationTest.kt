package com.maksimowiczm.foodyou.app.infrastructure.room.migration

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.execSQL
import com.maksimowiczm.foodyou.app.infrastructure.room.QuickCaptureMigration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

abstract class AbstractQuickCaptureMigrationTest {
    abstract fun getTestHelper(): MigrationTestHelper

    open fun migrateFoodSnapDataWithoutLoss() {
        val helper = getTestHelper()

        helper.createDatabase(48).apply {
            execSQL(
                "INSERT INTO `FoodSnapEntry` " +
                    "(`id`, `photoPath`, `createdAt`, `foodType`, `foodId`, `foodName`, `weightInGrams`) " +
                    "VALUES (1, 'pending.jpg', 100, NULL, NULL, NULL, NULL)"
            )
            execSQL(
                "INSERT INTO `FoodSnapEntry` " +
                    "(`id`, `photoPath`, `createdAt`, `foodType`, `foodId`, `foodName`, `weightInGrams`) " +
                    "VALUES (2, 'apple-1.jpg', 200, NULL, NULL, ' Apfel ', 120.0)"
            )
            execSQL(
                "INSERT INTO `FoodSnapEntry` " +
                    "(`id`, `photoPath`, `createdAt`, `foodType`, `foodId`, `foodName`, `weightInGrams`) " +
                    "VALUES (3, 'apple-2.jpg', 300, NULL, NULL, 'APFEL', 80.0)"
            )
            close()
        }

        helper.runMigrationsAndValidate(49, listOf(QuickCaptureMigration)).use { connection ->
            connection.prepare(
                "SELECT `photoPath`, `foodName`, `directWeightInGrams`, `foodNameId` " +
                    "FROM `QuickCaptureLogEntry` ORDER BY `id`"
            ).use { statement ->
                assertTrue(statement.step())
                assertEquals("pending.jpg", statement.getText(0))
                assertTrue(statement.isNull(1))
                assertTrue(statement.isNull(2))
                assertTrue(statement.isNull(3))

                assertTrue(statement.step())
                assertEquals("apple-1.jpg", statement.getText(0))
                assertEquals(" Apfel ", statement.getText(1))
                assertEquals(120.0, statement.getDouble(2))
                assertFalse(statement.isNull(3))

                assertTrue(statement.step())
                assertEquals("apple-2.jpg", statement.getText(0))
                assertEquals(80.0, statement.getDouble(2))
                assertFalse(statement.isNull(3))
                assertFalse(statement.step())
            }

            connection.prepare(
                "SELECT `normalizedName`, `usageCount`, `lastUsedAt` " +
                    "FROM `QuickCaptureFoodName`"
            ).use { statement ->
                assertTrue(statement.step())
                assertEquals("apfel", statement.getText(0))
                assertEquals(2, statement.getLong(1))
                assertEquals(300, statement.getLong(2))
                assertFalse(statement.step())
            }
        }
    }
}
