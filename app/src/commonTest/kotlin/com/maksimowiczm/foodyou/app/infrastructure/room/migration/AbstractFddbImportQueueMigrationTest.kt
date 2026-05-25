package com.maksimowiczm.foodyou.app.infrastructure.room.migration

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.execSQL
import com.maksimowiczm.foodyou.app.infrastructure.room.FddbImportQueueMigration
import kotlin.test.assertEquals

abstract class AbstractFddbImportQueueMigrationTest {
    abstract fun getTestHelper(): MigrationTestHelper

    open fun migrate() {
        val helper = getTestHelper()

        helper.createDatabase(36).close()

        helper.runMigrationsAndValidate(37, listOf(FddbImportQueueMigration)).use { connection ->
            connection.execSQL(
                """
                INSERT INTO FddbImportQueueItem (url, createdAt, lastAttemptedAt, lastError)
                VALUES ('https://fddb.info/db/de/lebensmittel/product_1/index.html', 1, NULL, NULL)
                """
                    .trimIndent()
            )

            connection
                .prepare(
                    """
                    SELECT COUNT(*) FROM FddbImportQueueItem
                    WHERE url = 'https://fddb.info/db/de/lebensmittel/product_1/index.html'
                    """
                        .trimIndent()
                )
                .use { statement ->
                    statement.step()
                    assertEquals(1, statement.getLong(0))
                }

            try {
                connection.execSQL(
                    """
                    INSERT INTO FddbImportQueueItem (url, createdAt, lastAttemptedAt, lastError)
                    VALUES ('https://fddb.info/db/de/lebensmittel/product_1/index.html', 2, NULL, NULL)
                    """
                        .trimIndent()
                )
            } catch (_: Throwable) {
                return
            }

            error("FddbImportQueueItem.url must be unique")
        }
    }
}
