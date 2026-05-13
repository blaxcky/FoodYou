package com.maksimowiczm.foodyou.app.infrastructure.room.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

object ActivityMigration : Migration(32, 33) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
                CREATE TABLE IF NOT EXISTS `ManualActivityEntry` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `dateEpochDay` INTEGER NOT NULL,
                    `name` TEXT NOT NULL,
                    `energyKcal` REAL NOT NULL,
                    `createdEpochSeconds` INTEGER NOT NULL,
                    `updatedEpochSeconds` INTEGER NOT NULL
                )
            """
                .trimIndent()
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_ManualActivityEntry_dateEpochDay` ON `ManualActivityEntry` (`dateEpochDay`)"
        )
        connection.execSQL(
            """
                CREATE TABLE IF NOT EXISTS `DailyStepSummary` (
                    `dateEpochDay` INTEGER NOT NULL,
                    `steps` INTEGER NOT NULL,
                    `syncedEpochSeconds` INTEGER NOT NULL,
                    PRIMARY KEY(`dateEpochDay`)
                )
            """
                .trimIndent()
        )
    }
}
