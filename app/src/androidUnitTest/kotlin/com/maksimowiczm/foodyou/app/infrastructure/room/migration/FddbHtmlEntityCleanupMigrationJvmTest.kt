package com.maksimowiczm.foodyou.app.infrastructure.room.migration

import android.app.Application
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.maksimowiczm.foodyou.app.infrastructure.room.FddbHtmlEntityCleanupMigration
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class FddbHtmlEntityCleanupMigrationJvmTest {
    @Test
    fun decodesFddbNamesAndKeepsOtherSources() {
        AndroidSQLiteDriver().open(":memory:").use { connection ->
            connection.createTables()
            connection.execSQL(
                "INSERT INTO `Product` VALUES (1, 'M&M&#039;s', 'Mars &amp; Co', NULL, 4)"
            )
            connection.execSQL("INSERT INTO `Product` VALUES (2, 'Tom &amp; Jerry', NULL, NULL, 0)")
            connection.execSQL("INSERT INTO `Product` VALUES (3, 'Apfel, frisch', NULL, NULL, 4)")
            connection.execSQL("INSERT INTO `DiaryProduct` VALUES (1, 'M&M&#039;s (Mars)', 4)")
            connection.execSQL("INSERT INTO `DiaryProduct` VALUES (2, 'Tom &amp; Jerry', 0)")
            connection.execSQL("INSERT INTO `ProductFts`(`ProductFts`) VALUES('rebuild')")

            FddbHtmlEntityCleanupMigration.migrate(connection)

            assertEquals(
                listOf(
                    listOf("M&M's", "Mars & Co"),
                    listOf("Tom &amp; Jerry", null),
                    listOf("Apfel, frisch", null),
                ),
                connection.rows("SELECT `name`, `brand` FROM `Product` ORDER BY `id`", 2),
            )
            assertEquals(
                listOf(listOf("M&M's (Mars)"), listOf("Tom &amp; Jerry")),
                connection.rows("SELECT `name` FROM `DiaryProduct` ORDER BY `id`", 1),
            )
            assertEquals(
                listOf(listOf("1")),
                connection.rows(
                    "SELECT CAST(`docid` AS TEXT) FROM `ProductFts` WHERE `ProductFts` MATCH 'Mars'",
                    1,
                ),
            )
            assertEquals(
                emptyList(),
                connection.rows("SELECT `docid` FROM `ProductFts` WHERE `ProductFts` MATCH '039'", 1),
            )
            assertEquals(
                listOf(listOf("2")),
                connection.rows(
                    "SELECT CAST(`docid` AS TEXT) FROM `ProductFts` WHERE `ProductFts` MATCH 'amp'",
                    1,
                ),
            )
        }
    }

    @Test
    fun keepsNullBrand() {
        AndroidSQLiteDriver().open(":memory:").use { connection ->
            connection.createTables()
            connection.execSQL("INSERT INTO `Product` VALUES (1, 'Ben&#x27;s', NULL, NULL, 4)")

            FddbHtmlEntityCleanupMigration.migrate(connection)

            val row = connection.rows("SELECT `name`, `brand` FROM `Product`", 2).single()
            assertEquals("Ben's", row[0])
            assertNull(row[1])
        }
    }

    private fun SQLiteConnection.createTables() {
        execSQL(
            "CREATE TABLE `Product` (`id` INTEGER PRIMARY KEY NOT NULL, `name` TEXT NOT NULL, " +
                "`brand` TEXT, `note` TEXT, `sourceType` INTEGER NOT NULL)"
        )
        execSQL(
            "CREATE TABLE `DiaryProduct` (`id` INTEGER PRIMARY KEY NOT NULL, `name` TEXT NOT NULL, " +
                "`sourceType` INTEGER NOT NULL)"
        )
        execSQL(
            "CREATE VIRTUAL TABLE `ProductFts` USING FTS4(`name` TEXT NOT NULL, `brand` TEXT, " +
                "`note` TEXT, content=`Product`)"
        )
    }

    private fun SQLiteConnection.rows(sql: String, columns: Int): List<List<String?>> =
        prepare(sql).use { statement ->
            buildList {
                while (statement.step()) {
                    add(
                        (0 until columns).map { index ->
                            if (statement.isNull(index)) null else statement.getText(index)
                        }
                    )
                }
            }
        }
}
