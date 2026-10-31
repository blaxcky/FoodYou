package com.maksimowiczm.foodyou.ai

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.maksimowiczm.foodyou.app.infrastructure.room.FoodYouDatabase
import com.maksimowiczm.foodyou.common.crypto.MasterCrypto
import java.io.File
import kotlin.test.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class ClaudeSettingsPersistenceTest {
    @Test fun preservesLegacyGeminiAndStoresIndependentEncryptedKeys() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dir = File(context.noBackupFilesDir, "ai").apply { mkdirs() }
        val prefs = File(dir, "settings.properties")
        prefs.writeText("provider=Gemini\nmodel=legacy-gemini\nvision_backend=cpu\n")
        val crypto = object : MasterCrypto {
            override val isSupported = flowOf(true)
            override suspend fun encrypt(data: ByteArray) = data.reversedArray()
            override suspend fun decrypt(encryptedData: ByteArray) = encryptedData.reversedArray()
        }
        File(dir, "gemini.key").writeBytes(crypto.encrypt("google-secret".encodeToByteArray()))
        val db = Room.inMemoryDatabaseBuilder(context, FoodYouDatabase::class.java).allowMainThreadQueries().build()
        try {
            val controller = AndroidAiController(context, db, crypto)
            assertEquals("legacy-gemini", controller.settings.value.model)
            assertTrue(controller.settings.value.visionOnCpu)
            assertEquals(4, controller.settings.value.claude.photoParallelism)
            controller.saveOpenAi(OpenAiSettings(), "openai-secret")
            controller.saveClaude(ClaudeSettings(photoParallelism = 5), "claude-secret")
            assertEquals("terces-edualc", File(dir, "claude.key").readText())
            controller.saveSettings(AiProvider.Local, "legacy-gemini", true, null)
            val restored = AndroidAiController(context, db, crypto)
            assertTrue(restored.settings.value.claude.hasApiKey)
            assertEquals(5, restored.settings.value.claude.photoParallelism)
            for (value in listOf(0, 6)) {
                assertFailsWith<IllegalArgumentException> { restored.saveClaude(ClaudeSettings(photoParallelism = value), null) }
            }
            for (value in listOf("0", "6", "bad")) {
                prefs.appendText("\nclaude.photo.parallelism=$value\n")
                assertEquals(4, AndroidAiController(context, db, crypto).settings.value.claude.photoParallelism)
            }
            assertEquals(ClaudeTask("claude-sonnet-5-5", "low"), restored.settings.value.claude.photo)
            assertEquals(NutritionProvider.OpenAI, restored.settings.value.nutritionProvider)
            restored.saveOpenAi(restored.settings.value.openAi, null, true)
            assertEquals(NutritionProvider.Claude, restored.settings.value.nutritionProvider)
            restored.saveNutritionProvider(NutritionProvider.OpenAI)
            assertEquals(NutritionProvider.OpenAI, AndroidAiController(context, db, crypto).settings.value.nutritionProvider)
            restored.saveOpenAi(restored.settings.value.openAi, "openai-secret")
            restored.saveNutritionProvider(NutritionProvider.Claude)
            assertEquals(NutritionProvider.Claude, AndroidAiController(context, db, crypto).settings.value.nutritionProvider)
            restored.saveClaude(restored.settings.value.claude, null, true)
            assertFalse(File(dir, "claude.key").exists())
            assertTrue(restored.settings.value.openAi.hasApiKey)
            assertEquals("openai-secret", crypto.decrypt(File(dir, "openai.key").readBytes()).decodeToString())
            assertEquals("google-secret", crypto.decrypt(File(dir, "gemini.key").readBytes()).decodeToString())
        } finally { db.close(); dir.deleteRecursively() }
    }
}
