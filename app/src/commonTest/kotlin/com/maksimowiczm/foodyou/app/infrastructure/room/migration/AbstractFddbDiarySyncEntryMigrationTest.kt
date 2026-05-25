package com.maksimowiczm.foodyou.app.infrastructure.room.migration

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.execSQL
import com.maksimowiczm.foodyou.app.infrastructure.room.FddbDiarySyncEntryMigration
import kotlin.test.assertEquals

abstract class AbstractFddbDiarySyncEntryMigrationTest {
    abstract fun getTestHelper(): MigrationTestHelper

    open fun migrate() {
        val helper = getTestHelper()

        helper.createDatabase(37).close()

        helper.runMigrationsAndValidate(38, listOf(FddbDiarySyncEntryMigration)).use { connection ->
            connection.execSQL(
                """
                INSERT INTO FddbDiarySyncEntry (fddbEntryId, syncedAt)
                VALUES ('2131111111', 1)
                """
                    .trimIndent()
            )

            connection
                .prepare("SELECT COUNT(*) FROM FddbDiarySyncEntry WHERE fddbEntryId = '2131111111'")
                .use { statement ->
                    statement.step()
                    assertEquals(1, statement.getLong(0))
                }

            try {
                connection.execSQL(
                    """
                    INSERT INTO FddbDiarySyncEntry (fddbEntryId, syncedAt)
                    VALUES ('2131111111', 2)
                    """
                        .trimIndent()
                )
            } catch (_: Throwable) {
                return
            }

            error("FddbDiarySyncEntry.fddbEntryId must be unique")
        }
    }
}
