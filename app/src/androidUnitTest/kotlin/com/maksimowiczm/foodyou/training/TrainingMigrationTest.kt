package com.maksimowiczm.foodyou.training

import android.app.Application
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.maksimowiczm.foodyou.app.infrastructure.room.TrainingImportMigration
import java.io.File
import kotlin.test.*
import kotlinx.serialization.json.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class TrainingMigrationTest {
    @Test fun version51DataAndUniqueAccountScopedImportKeysSurviveMigration() {
        val schema = Json.parseToJsonElement(File("schemas/com.maksimowiczm.foodyou.app.infrastructure.room.FoodYouDatabase/51.json").readText()).jsonObject.getValue("database").jsonObject
        AndroidSQLiteDriver().open(":memory:").use { connection ->
            for (item in schema.getValue("entities").jsonArray) {
                val entity = item.jsonObject
                connection.execSQL(entity.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", entity.getValue("tableName").jsonPrimitive.content))
            }
            connection.execSQL("INSERT INTO ManualActivityEntry (id,dateEpochDay,name,energyKcal,createdEpochSeconds,updatedEpochSeconds) VALUES (1,1,'Existing',42,0,0)")
            TrainingImportMigration.migrate(connection)
            connection.prepare("SELECT energyKcal FROM ManualActivityEntry WHERE id=1").use { assertTrue(it.step()); assertEquals(42.0, it.getDouble(0)) }
            val sql = "INSERT INTO ImportedTrainingActivity (firebaseProjectId,firebaseUid,importId,sessionId,dateEpochDay,name,energyKcal) VALUES ('krafttraining-59773','A','same','session',1,'Krafttraining',100)"
            connection.execSQL(sql)
            assertFails { connection.execSQL(sql) }
            connection.execSQL(sql.replace("'A'", "'B'"))
        }
    }
}
