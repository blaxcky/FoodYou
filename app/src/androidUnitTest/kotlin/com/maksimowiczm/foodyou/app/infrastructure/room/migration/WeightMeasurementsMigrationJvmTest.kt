package com.maksimowiczm.foodyou.app.infrastructure.room.migration

import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.maksimowiczm.foodyou.app.infrastructure.room.WeightMeasurementsMigration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class WeightMeasurementsMigrationJvmTest {
    @Test
    fun preservesOldRowsAndAddsIndividualIdentity() {
        AndroidSQLiteDriver().open(":memory:").use { connection ->
            connection.execSQL(
                """
                CREATE TABLE `DailyWeightEntry` (
                    `dateEpochDay` INTEGER NOT NULL PRIMARY KEY,
                    `measuredEpochSeconds` INTEGER NOT NULL,
                    `weightKg` REAL NOT NULL,
                    `healthConnectRecordId` TEXT,
                    `isFoodYouRecord` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            connection.execSQL("INSERT INTO `DailyWeightEntry` VALUES (100, 1000, 80.5, 'own-id', 1)")
            connection.execSQL("INSERT INTO `DailyWeightEntry` VALUES (101, 2000, 79.8, 'foreign-id', 0)")

            WeightMeasurementsMigration.migrate(connection)

            connection.prepare(
                "SELECT `id`, `weightKg`, `healthConnectRecordId`, `isHidden` " +
                    "FROM `DailyWeightEntry` ORDER BY `dateEpochDay`"
            ).use { statement ->
                assertTrue(statement.step())
                assertEquals("local:100", statement.getText(0))
                assertEquals(80.5, statement.getDouble(1))
                assertEquals("own-id", statement.getText(2))
                assertEquals(0L, statement.getLong(3))
                assertTrue(statement.step())
                assertEquals("hc:foreign-id", statement.getText(0))
                assertEquals(79.8, statement.getDouble(1))
                assertFalse(statement.step())
            }
        }
    }
}
