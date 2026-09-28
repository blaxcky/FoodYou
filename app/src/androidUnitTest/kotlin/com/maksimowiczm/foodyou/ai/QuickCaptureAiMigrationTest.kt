package com.maksimowiczm.foodyou.ai

import android.app.Application
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.maksimowiczm.foodyou.app.infrastructure.room.QuickCaptureAiMigration
import com.maksimowiczm.foodyou.app.infrastructure.room.QuickCaptureSuggestionFeedbackMigration
import kotlinx.serialization.json.*
import java.io.File
import kotlin.test.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class QuickCaptureAiMigrationTest {
    @Test fun migratesRealVersion50SchemaWithoutChangingWeights() {
        val schema = File("schemas/com.maksimowiczm.foodyou.app.infrastructure.room.FoodYouDatabase/50.json")
        val entity = Json.parseToJsonElement(schema.readText()).jsonObject.getValue("database").jsonObject
            .getValue("entities").jsonArray.first { it.jsonObject["tableName"]?.jsonPrimitive?.content == "QuickCaptureLogEntry" }.jsonObject
        AndroidSQLiteDriver().open(":memory:").use { connection ->
            connection.execSQL(entity.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", "QuickCaptureLogEntry"))
            connection.execSQL("INSERT INTO QuickCaptureLogEntry (id, foodName, weightMode, directWeightInGrams, afterRequired, photoPath, createdAt) VALUES (1, 'Apfel', 0, 125, 0, 'photo.jpg', 100)")
            connection.execSQL("INSERT INTO QuickCaptureLogEntry (id, weightMode, afterRequired, photoPath, createdAt) VALUES (2, 0, 0, 'pending.jpg', 200)")
            QuickCaptureAiMigration.migrate(connection)
            connection.prepare("SELECT directWeightInGrams, suggestedWeightInGrams, aiAnalysisStatus, aiAnalysisProvider, aiAnalysisModel, aiAnalyzedAt FROM QuickCaptureLogEntry ORDER BY id").use {
                assertTrue(it.step())
                assertEquals(125.0, it.getDouble(0))
                for (column in 1..5) assertTrue(it.isNull(column))
                assertTrue(it.step())
                for (column in 0..5) assertTrue(it.isNull(column))
                assertFalse(it.step())
            }
        }
    }

    @Test fun addsSuggestionFeedbackToRealVersion52Schema() {
        val schema =
            File("schemas/com.maksimowiczm.foodyou.app.infrastructure.room.FoodYouDatabase/52.json")
        val entity =
            Json.parseToJsonElement(schema.readText()).jsonObject.getValue("database").jsonObject
                .getValue("entities").jsonArray.first {
                    it.jsonObject["tableName"]?.jsonPrimitive?.content == "QuickCaptureLogEntry"
                }.jsonObject
        AndroidSQLiteDriver().open(":memory:").use { connection ->
            connection.execSQL(
                entity.getValue("createSql").jsonPrimitive.content.replace(
                    "\${TABLE_NAME}",
                    "QuickCaptureLogEntry",
                )
            )
            connection.execSQL(
                "INSERT INTO QuickCaptureLogEntry (id, weightMode, afterRequired, photoPath, createdAt, suggestedWeightInGrams) VALUES (1, 0, 0, 'pending.jpg', 200, 125)"
            )

            QuickCaptureSuggestionFeedbackMigration.migrate(connection)

            connection.prepare(
                "SELECT aiSuggestionRejected FROM QuickCaptureLogEntry WHERE id = 1"
            ).use {
                assertTrue(it.step())
                assertEquals(0L, it.getLong(0))
            }
            connection.execSQL(
                "UPDATE QuickCaptureLogEntry SET aiSuggestionRejected = 1 WHERE id = 1"
            )
            connection.prepare(
                "SELECT aiSuggestionRejected FROM QuickCaptureLogEntry WHERE id = 1"
            ).use {
                assertTrue(it.step())
                assertEquals(1L, it.getLong(0))
            }
        }
    }
}
