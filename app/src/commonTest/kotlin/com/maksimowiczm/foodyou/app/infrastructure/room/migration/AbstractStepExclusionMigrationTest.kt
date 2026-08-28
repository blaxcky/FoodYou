package com.maksimowiczm.foodyou.app.infrastructure.room.migration

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.execSQL
import com.maksimowiczm.foodyou.app.infrastructure.room.StepExclusionMigration
import kotlin.test.assertEquals

abstract class AbstractStepExclusionMigrationTest {
    abstract fun getTestHelper(): MigrationTestHelper

    open fun migrate() {
        val helper = getTestHelper()

        helper.createDatabase(47).apply {
            execSQL(
                "INSERT INTO `DailyStepSummary` " +
                    "(`dateEpochDay`, `steps`, `syncedEpochSeconds`) VALUES (42, 12345, 99)"
            )
            close()
        }

        helper.runMigrationsAndValidate(48, listOf(StepExclusionMigration)).use { connection ->
            connection
                .prepare(
                    "SELECT `rawSteps`, `excludedSteps` FROM `DailyStepSummary` " +
                        "WHERE `dateEpochDay` = 42"
                )
                .use { statement ->
                    statement.step()
                    assertEquals(12_345, statement.getLong(0))
                    assertEquals(0, statement.getLong(1))
                }

            connection.execSQL(
                "INSERT INTO `StepExclusionPeriod` " +
                    "(`dateEpochDay`, `startMinute`, `endMinute`) VALUES (42, 480, 540)"
            )
            connection.execSQL(
                "INSERT INTO `StepExclusionPeriod` " +
                    "(`dateEpochDay`, `startMinute`, `endMinute`) VALUES (42, 600, 660)"
            )
            connection.prepare("SELECT COUNT(*) FROM `StepExclusionPeriod`").use { statement ->
                statement.step()
                assertEquals(2, statement.getLong(0))
            }
        }
    }
}
