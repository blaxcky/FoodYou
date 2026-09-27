package com.maksimowiczm.foodyou.training

import java.io.File
import kotlin.test.*
import org.junit.Test

class TrainingAuthBackupTest {
    @Test fun excludesAndRemovesFirebaseTokensButKeepsOtherPreferences() {
        val root = kotlin.io.path.createTempDirectory("training-backup").toFile()
        try {
            val token = File(root, "com.google.firebase.auth.api.Store.test.xml").apply { writeText("token") }
            val backup = File(root, "${token.name}.bak").apply { writeText("token") }
            val other = File(root, "settings.xml").apply { writeText("preferences") }
            assertTrue(isTrainingAuthPreference(token))
            assertTrue(isTrainingAuthPreference(backup))
            assertFalse(isTrainingAuthPreference(other))
            removeTrainingAuthPreferences(root)
            assertEquals(listOf("settings.xml"), root.listFiles()!!.map { it.name })
        } finally { root.deleteRecursively() }
    }
}
