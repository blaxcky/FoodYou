package com.maksimowiczm.foodyou.app.infrastructure.room.migration

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.execSQL
import com.maksimowiczm.foodyou.app.infrastructure.room.WeightMeasurementsMigration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

abstract class AbstractWeightMeasurementsMigrationTest {
    abstract fun getTestHelper(): MigrationTestHelper

    open fun preservesLocalAndImportedMeasurements() {
        val helper = getTestHelper()
        helper.createDatabase(49).apply {
            execSQL("INSERT INTO `DailyWeightEntry` VALUES (100, 1000, 80.5, 'own-id', 1)")
            execSQL("INSERT INTO `DailyWeightEntry` VALUES (101, 2000, 79.8, 'foreign-id', 0)")
            close()
        }

        helper.runMigrationsAndValidate(50, listOf(WeightMeasurementsMigration)).use { connection ->
            connection.prepare(
                "SELECT `id`, `weightKg`, `healthConnectRecordId`, `isFoodYouRecord`, `isHidden` " +
                    "FROM `DailyWeightEntry` ORDER BY `dateEpochDay`"
            ).use { statement ->
                assertTrue(statement.step())
                assertEquals("local:100", statement.getText(0))
                assertEquals(80.5, statement.getDouble(1))
                assertEquals("own-id", statement.getText(2))
                assertEquals(1L, statement.getLong(3))
                assertEquals(0L, statement.getLong(4))
                assertTrue(statement.step())
                assertEquals("hc:foreign-id", statement.getText(0))
                assertEquals(79.8, statement.getDouble(1))
                assertEquals("foreign-id", statement.getText(2))
                assertFalse(statement.step())
            }
        }
    }
}
