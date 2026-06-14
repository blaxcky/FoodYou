package com.maksimowiczm.foodyou.app.infrastructure.room

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.immediateTransaction
import androidx.room.migration.Migration
import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.maksimowiczm.foodyou.app.infrastructure.room.migration.FoodSearchFtsCyrillicMigration
import com.maksimowiczm.foodyou.app.infrastructure.room.migration.FoodSearchFtsMigration
import com.maksimowiczm.foodyou.app.infrastructure.room.migration.LegacyMigrations
import com.maksimowiczm.foodyou.app.infrastructure.room.migration.ActivityMigration
import com.maksimowiczm.foodyou.activity.ActivityDatabase
import com.maksimowiczm.foodyou.activity.infrastructure.room.DailyStepSummaryEntity
import com.maksimowiczm.foodyou.activity.infrastructure.room.ManualActivityEntryEntity
import com.maksimowiczm.foodyou.app.infrastructure.room.migration.deleteUsedFoodEvent
import com.maksimowiczm.foodyou.app.infrastructure.room.migration.fixMeasurementSuggestions
import com.maksimowiczm.foodyou.app.infrastructure.room.migration.foodYou3Migration
import com.maksimowiczm.foodyou.app.infrastructure.room.migration.unlinkDiaryMigration
import com.maksimowiczm.foodyou.common.domain.database.TransactionProvider
import com.maksimowiczm.foodyou.common.domain.database.TransactionScope as DomainTransactionScope
import com.maksimowiczm.foodyou.common.infrastructure.room.FoodSourceTypeConverter
import com.maksimowiczm.foodyou.common.infrastructure.room.MeasurementTypeConverter
import com.maksimowiczm.foodyou.common.infrastructure.room.RoomTransactionScope
import com.maksimowiczm.foodyou.food.infrastructure.room.FoodDatabase
import com.maksimowiczm.foodyou.food.infrastructure.room.FoodEventEntity
import com.maksimowiczm.foodyou.food.infrastructure.room.FddbDiarySyncEntryEntity
import com.maksimowiczm.foodyou.food.infrastructure.room.FddbImportQueueItemEntity
import com.maksimowiczm.foodyou.food.infrastructure.room.FoodEventTypeConverter
import com.maksimowiczm.foodyou.food.infrastructure.room.LatestMeasurementSuggestion
import com.maksimowiczm.foodyou.food.infrastructure.room.MeasurementSuggestionEntity
import com.maksimowiczm.foodyou.food.infrastructure.room.PendingProductEntity
import com.maksimowiczm.foodyou.food.infrastructure.room.ProductEntity
import com.maksimowiczm.foodyou.food.infrastructure.room.ProductFts
import com.maksimowiczm.foodyou.food.infrastructure.room.ProductPortionEntity
import com.maksimowiczm.foodyou.food.infrastructure.room.RecipeEntity
import com.maksimowiczm.foodyou.food.infrastructure.room.RecipeFts
import com.maksimowiczm.foodyou.food.infrastructure.room.RecipeIngredientEntity
import com.maksimowiczm.foodyou.food.search.infrastructure.room.FoodSearchDatabase
import com.maksimowiczm.foodyou.food.search.infrastructure.room.OpenFoodFactsPagingKeyEntity
import com.maksimowiczm.foodyou.food.search.infrastructure.room.RecipeAllIngredientsView
import com.maksimowiczm.foodyou.food.search.infrastructure.room.SearchEntry
import com.maksimowiczm.foodyou.food.search.infrastructure.room.USDAPagingKeyEntity
import com.maksimowiczm.foodyou.fooddiary.infrastructure.room.DiaryProductEntity
import com.maksimowiczm.foodyou.fooddiary.infrastructure.room.DiaryRecipeEntity
import com.maksimowiczm.foodyou.fooddiary.infrastructure.room.DiaryRecipeIngredientEntity
import com.maksimowiczm.foodyou.fooddiary.infrastructure.room.FoodDiaryDatabase
import com.maksimowiczm.foodyou.fooddiary.infrastructure.room.InitializeMealsCallback
import com.maksimowiczm.foodyou.fooddiary.infrastructure.room.ManualDiaryEntryEntity
import com.maksimowiczm.foodyou.fooddiary.infrastructure.room.MealEntity
import com.maksimowiczm.foodyou.fooddiary.infrastructure.room.MeasurementEntity
import com.maksimowiczm.foodyou.sponsorship.infrastructure.room.SponsorshipDatabase
import com.maksimowiczm.foodyou.sponsorship.infrastructure.room.SponsorshipEntity
import com.maksimowiczm.foodyou.weight.WeightDatabase
import com.maksimowiczm.foodyou.weight.infrastructure.room.DailyWeightEntryEntity

@Database(
    entities =
        [
            ProductEntity::class,
            RecipeEntity::class,
            RecipeIngredientEntity::class,
            OpenFoodFactsPagingKeyEntity::class,
            USDAPagingKeyEntity::class,
            FoodEventEntity::class,
            SearchEntry::class,
            MealEntity::class,
            MeasurementEntity::class,
            DiaryProductEntity::class,
            DiaryRecipeEntity::class,
            DiaryRecipeIngredientEntity::class,
            SponsorshipEntity::class,
            MeasurementSuggestionEntity::class,
            ManualDiaryEntryEntity::class,
            ManualActivityEntryEntity::class,
            DailyStepSummaryEntity::class,
            PendingProductEntity::class,
            ProductFts::class,
            RecipeFts::class,
            FddbImportQueueItemEntity::class,
            FddbDiarySyncEntryEntity::class,
            ProductPortionEntity::class,
            DailyWeightEntryEntity::class,
        ],
    views = [RecipeAllIngredientsView::class, LatestMeasurementSuggestion::class],
    version = FoodYouDatabase.VERSION,
    exportSchema = true,
    autoMigrations =
        [
            /** @see [LegacyMigrations.MIGRATION_1_2] Add rank to MealEntity */
            /** @see [LegacyMigrations.MIGRATION_2_3] 2.0.0 schema change */
            AutoMigration(from = 3, to = 4),
            AutoMigration(from = 4, to = 5),
            AutoMigration(from = 5, to = 6),
            AutoMigration(from = 6, to = 7),
            /**
             * @see [LegacyMigrations.MIGRATION_7_8] Remove unused products from OpenFoodFacts
             *   source
             */
            /** @see [LegacyMigrations.MIGRATION_8_9] Remove OpenFoodFactsPagingKeyEntity */
            AutoMigration(from = 9, to = 10, spec = LegacyMigrations.MIGRATION_9_10::class),
            AutoMigration(from = 10, to = 11),
            /**
             * @see [LegacyMigrations.MIGRATION_11_12] Fix sodium value in ProductEntity. Convert
             *   grams to milligrams.
             */
            AutoMigration(from = 12, to = 13),
            AutoMigration(from = 13, to = 14),
            AutoMigration(from = 14, to = 15),
            AutoMigration(from = 15, to = 16),
            AutoMigration(from = 16, to = 17),
            AutoMigration(from = 17, to = 18),
            /**
             * @see [LegacyMigrations.MIGRATION_18_19] Merge product and recipe measurements into
             *   MeasurementEntity
             */
            AutoMigration(from = 19, to = 20),
            /**
             * @see [LegacyMigrations.MIGRATION_20_21] Add isLiquid column to ProductEntity and
             *   RecipeEntity
             */
            /**
             * @see [LegacyMigrations.MIGRATION_21_22] Add `note` column to ProductEntity and
             *   RecipeEntity
             */
            AutoMigration(from = 23, to = 24), // Add LatestFoodMeasuredEventView
            AutoMigration(from = 24, to = 25), // Add FoodEventEntity onDelete cascade
            AutoMigration(from = 28, to = 29), // Add ManualDiaryEntryEntity
            AutoMigration(from = 29, to = 30), // Add MeasurementSuggestion indices
            /** @see [FoodSearchFtsMigration] Add FTS tables for ProductEntity and RecipeEntity */
            /**
             * @see [FoodSearchFtsCyrillicMigration] Add Cyrillic tokenizer support to FTS tables
             */
        ],
)
@TypeConverters(
    FoodSourceTypeConverter::class,
    MeasurementTypeConverter::class,
    FoodEventTypeConverter::class,
)
abstract class FoodYouDatabase :
    RoomDatabase(),
    TransactionProvider,
    FoodDatabase,
    FoodSearchDatabase,
    FoodDiaryDatabase,
    ActivityDatabase,
    SponsorshipDatabase,
    WeightDatabase {

    override suspend fun <T> withTransaction(block: suspend DomainTransactionScope<T>.() -> T): T =
        useWriterConnection {
            it.immediateTransaction {
                val scope = RoomTransactionScope<T>(this)
                scope.block()
            }
        }

    companion object {
        const val VERSION = 41

        private val migrations: List<Migration> =
            listOf(
                LegacyMigrations.MIGRATION_1_2,
                LegacyMigrations.MIGRATION_2_3,
                LegacyMigrations.MIGRATION_7_8,
                LegacyMigrations.MIGRATION_8_9,
                LegacyMigrations.MIGRATION_11_12,
                LegacyMigrations.MIGRATION_18_19,
                LegacyMigrations.MIGRATION_20_21,
                LegacyMigrations.MIGRATION_21_22,
                foodYou3Migration,
                unlinkDiaryMigration,
                deleteUsedFoodEvent,
                fixMeasurementSuggestions,
                FoodSearchFtsMigration,
                FoodSearchFtsCyrillicMigration,
                ActivityMigration,
                PendingProductMigration,
                PendingProductNullableBarcodeMigration,
                PendingProductMultiplePhotosMigration,
                FddbImportQueueMigration,
                FddbDiarySyncEntryMigration,
                ProductPortionMigration,
                DailyWeightEntryMigration,
                FddbPortionServingWeightMigration,
            )

        fun Builder<FoodYouDatabase>.buildDatabase(
            mealsCallback: InitializeMealsCallback
        ): FoodYouDatabase {
            addMigrations(*migrations.toTypedArray())
            addCallback(mealsCallback)
            return build()
        }
    }
}

internal object FddbPortionServingWeightMigration : Migration(40, 41) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            UPDATE `Product`
            SET `servingWeight` = NULL
            WHERE `sourceType` = 4
                AND `servingWeight` IS NOT NULL
                AND EXISTS (
                    SELECT 1
                    FROM `ProductPortion`
                    WHERE `ProductPortion`.`productId` = `Product`.`id`
                        AND `ProductPortion`.`sourceType` = 4
                        AND `ProductPortion`.`unit` = 'g'
                        AND ABS(`ProductPortion`.`amount` - `Product`.`servingWeight`) < 0.000001
                )
            """
                .trimIndent()
        )
    }
}

internal object DailyWeightEntryMigration : Migration(39, 40) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `DailyWeightEntry` (
                `dateEpochDay` INTEGER NOT NULL,
                `measuredEpochSeconds` INTEGER NOT NULL,
                `weightKg` REAL NOT NULL,
                `healthConnectRecordId` TEXT,
                `isFoodYouRecord` INTEGER NOT NULL,
                PRIMARY KEY(`dateEpochDay`)
            )
            """
                .trimIndent()
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_DailyWeightEntry_measuredEpochSeconds` ON `DailyWeightEntry` (`measuredEpochSeconds`)"
        )
    }
}

internal object ProductPortionMigration : Migration(38, 39) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `ProductPortion` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `productId` INTEGER NOT NULL,
                `sourceType` INTEGER NOT NULL,
                `label` TEXT NOT NULL,
                `normalizedLabel` TEXT NOT NULL,
                `amount` REAL NOT NULL,
                `unit` TEXT NOT NULL,
                FOREIGN KEY(`productId`) REFERENCES `Product`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """
                .trimIndent()
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_ProductPortion_productId` ON `ProductPortion` (`productId`)"
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_ProductPortion_productId_sourceType_normalizedLabel` ON `ProductPortion` (`productId`, `sourceType`, `normalizedLabel`)"
        )
    }
}

internal object FddbDiarySyncEntryMigration : Migration(37, 38) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `FddbDiarySyncEntry` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `fddbEntryId` TEXT NOT NULL,
                `syncedAt` INTEGER NOT NULL
            )
            """
                .trimIndent()
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_FddbDiarySyncEntry_fddbEntryId` ON `FddbDiarySyncEntry` (`fddbEntryId`)"
        )
    }
}

internal object FddbImportQueueMigration : Migration(36, 37) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `FddbImportQueueItem` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `url` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `lastAttemptedAt` INTEGER,
                `lastError` TEXT
            )
            """
                .trimIndent()
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_FddbImportQueueItem_url` ON `FddbImportQueueItem` (`url`)"
        )
    }
}

private object PendingProductMigration : Migration(33, 34) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `PendingProduct` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `barcode` TEXT,
                `photoPath` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER
            )
            """
                .trimIndent()
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_PendingProduct_barcode` ON `PendingProduct` (`barcode`)"
        )
    }
}

private object PendingProductNullableBarcodeMigration : Migration(34, 35) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `PendingProduct_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `barcode` TEXT,
                `photoPath` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER
            )
            """
                .trimIndent()
        )
        connection.execSQL(
            """
            INSERT INTO `PendingProduct_new` (`id`, `barcode`, `photoPath`, `createdAt`, `updatedAt`)
            SELECT `id`, `barcode`, `photoPath`, `createdAt`, `updatedAt`
            FROM `PendingProduct`
            """
                .trimIndent()
        )
        connection.execSQL("DROP TABLE `PendingProduct`")
        connection.execSQL("ALTER TABLE `PendingProduct_new` RENAME TO `PendingProduct`")
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_PendingProduct_barcode` ON `PendingProduct` (`barcode`)"
        )
    }
}

private object PendingProductMultiplePhotosMigration : Migration(35, 36) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `PendingProduct_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `barcode` TEXT,
                `photoPaths` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER
            )
            """
                .trimIndent()
        )
        connection.execSQL(
            """
            INSERT INTO `PendingProduct_new` (`id`, `barcode`, `photoPaths`, `createdAt`, `updatedAt`)
            SELECT `id`, `barcode`, `photoPath`, `createdAt`, `updatedAt`
            FROM `PendingProduct`
            """
                .trimIndent()
        )
        connection.execSQL("DROP TABLE `PendingProduct`")
        connection.execSQL("ALTER TABLE `PendingProduct_new` RENAME TO `PendingProduct`")
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_PendingProduct_barcode` ON `PendingProduct` (`barcode`)"
        )
    }
}
