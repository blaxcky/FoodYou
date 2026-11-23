package com.maksimowiczm.foodyou.workout

import android.app.Application
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.maksimowiczm.foodyou.app.infrastructure.room.WorkoutMigration
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class WorkoutMigrationTest {
    private fun schema(version: Int): JsonObject =
        Json.parseToJsonElement(
                File(
                        "schemas/com.maksimowiczm.foodyou.app.infrastructure.room.FoodYouDatabase/$version.json"
                    )
                    .readText()
            )
            .jsonObject
            .getValue("database")
            .jsonObject

    private fun SQLiteConnection.createTables(schema: JsonObject) {
        for (item in schema.getValue("entities").jsonArray) {
            val entity = item.jsonObject
            val table = entity.getValue("tableName").jsonPrimitive.content
            execSQL(entity.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
        }
    }

    private fun SQLiteConnection.workoutSql(): Map<String, String> =
        prepare(
                "SELECT name, sql FROM sqlite_master WHERE name LIKE '%Workout%' AND sql IS NOT NULL"
            )
            .use { statement ->
                buildMap {
                    while (statement.step()) put(statement.getText(0), statement.getText(1))
                }
            }

    @Test
    fun migrationCreatesTheSameWorkoutTablesAsRoomAndKeepsExistingData() {
        AndroidSQLiteDriver().open(":memory:").use { migrated ->
            migrated.createTables(schema(56))
            migrated.execSQL(
                "INSERT INTO ManualActivityEntry (id,dateEpochDay,name,energyKcal,createdEpochSeconds,updatedEpochSeconds) VALUES (1,1,'Existing',42,0,0)"
            )
            WorkoutMigration.migrate(migrated)

            migrated.prepare("SELECT energyKcal FROM ManualActivityEntry WHERE id=1").use {
                assertTrue(it.step())
                assertEquals(42.0, it.getDouble(0))
            }

            AndroidSQLiteDriver().open(":memory:").use { fresh ->
                fresh.createTables(schema(57))
                val expected = schema(57).getValue("entities").jsonArray
                for (item in expected) {
                    val entity = item.jsonObject
                    val table = entity.getValue("tableName").jsonPrimitive.content
                    if (!table.startsWith("Workout")) continue
                    entity["indices"]?.jsonArray?.forEach { index ->
                        fresh.execSQL(
                            index.jsonObject
                                .getValue("createSql")
                                .jsonPrimitive
                                .content
                                .replace("\${TABLE_NAME}", table)
                        )
                    }
                }
                assertEquals(fresh.workoutSql(), migrated.workoutSql())
            }

            migrated.execSQL(
                "INSERT INTO WorkoutExercise (id,name,kind,measure,usesWeight,weightOptionsKg,note,kcalMode,kcalValue,helpsWith,reminderDays,cardioConversion,cardioFactor,cardioDiscountPercent,askDistance,excludeSteps,archived,createdEpochSeconds) VALUES (1,'Rudern','Cardio','Reps',0,'','','PerSet',0,'',NULL,'Concept2',1,10,1,1,0,0)"
            )
            migrated.execSQL(
                "INSERT INTO WorkoutEntry (exerciseId,dateEpochDay,minuteOfDay,durationMinutes,deviceKcal,energyKcal,createdEpochSeconds,updatedEpochSeconds) VALUES (1,20000,965,20,260,238,0,0)"
            )
            migrated.execSQL("PRAGMA foreign_keys = ON")
            assertFails {
                migrated.execSQL(
                    "INSERT INTO WorkoutEntry (exerciseId,dateEpochDay,minuteOfDay,energyKcal,createdEpochSeconds,updatedEpochSeconds) VALUES (99,20000,0,1,0,0)"
                )
            }
        }
    }
}
