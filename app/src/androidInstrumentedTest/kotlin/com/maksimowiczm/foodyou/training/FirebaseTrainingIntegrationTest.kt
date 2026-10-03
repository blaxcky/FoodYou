package com.maksimowiczm.foodyou.training

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.room.Room
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.maksimowiczm.foodyou.app.infrastructure.room.FoodYouDatabase
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Synthetic accounts ONLY: all network endpoints explicitly point at local emulators. */
@RunWith(AndroidJUnit4::class)
class FirebaseTrainingIntegrationTest {
    @Test fun existingLoginServerReadReplayAndAccountIsolation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = FirebaseApp.initializeApp(context, FirebaseOptions.Builder()
            .setProjectId("demo-training-sync").setApplicationId("1:123456789:web:synthetic")
            .setApiKey("fake-api-key").build(), "training-test-" + UUID.randomUUID())
        val auth = FirebaseAuth.getInstance(app)
        auth.useEmulator("10.0.2.2", 9299)
        val firestore = FirebaseFirestore.getInstance(app)
        firestore.useEmulator("10.0.2.2", 8280)
        val remote = FirebaseTrainingRemote(app)
        val database = Room.inMemoryDatabaseBuilder(context, FoodYouDatabase::class.java).build()
        val storageDirectory = java.io.File(context.cacheDir, "training-cursor-test-" + UUID.randomUUID())
        try {
            val email = "training-" + UUID.randomUUID() + "@example.test"
            val password = "synthetic-password-123"
            auth.createUserWithEmailAndPassword(email, password).await()
            val uid = auth.currentUser!!.uid
            suspend fun seed(strength: Long, cardio: Long) {
                val id = UUID.randomUUID().toString()
                firestore.collection("users").document(uid).collection("trainingSessions").document(id)
                    .set(mapOf("schemaVersion" to 1L, "source" to TRAINING_SOURCE, "sessionId" to id,
                        "startedAt" to "2026-09-27T21:50:00.000Z", "endedAt" to "2026-09-28T00:30:00.000Z",
                        "activityDate" to "2026-09-27", "timeZone" to "Europe/Vienna",
                        "strengthKcal" to strength, "cardioKcal" to cardio, "receivedAt" to FieldValue.serverTimestamp())).await()
            }
            seed(210, 120)
            seed(0, 0)
            // One atomic batch gives several documents the exact same server timestamp.
            val tiedIds = (1..5).map { UUID.randomUUID().toString() }
            val batch = firestore.batch()
            tiedIds.forEach { id ->
                val reference = firestore.collection("users").document(uid).collection("trainingSessions").document(id)
                batch.set(reference, mapOf("schemaVersion" to 1L, "source" to TRAINING_SOURCE, "sessionId" to id,
                    "startedAt" to "2026-09-27T21:50:00.000Z", "endedAt" to "2026-09-28T00:30:00.000Z",
                    "activityDate" to "2026-09-27", "timeZone" to "Europe/Vienna",
                    "strengthKcal" to 0L, "cardioKcal" to 0L, "receivedAt" to FieldValue.serverTimestamp()))
            }
            batch.commit().await()
            remote.signOut()
            remote.signIn(email, password)
            val account = remote.account.value!!
            assertEquals(uid, account.uid)
            repeat(5) {
                val documents = remote.fetchPage(account, null, 200).documents
                assertEquals(7, documents.size)
                documents.forEach { database.trainingImportDao.importDocument(account, it, 100) }
            }
            val pagedIds = mutableListOf<String>()
            var cursor: TrainingCursor? = null
            while (true) {
                val page = remote.fetchPage(account, cursor, 2)
                pagedIds += page.documents.map { it.id }
                cursor = page.documents.lastOrNull()?.let(TrainingCursor::of) ?: cursor
                if (page.documents.size < 2) break
            }
            assertEquals(7, pagedIds.size)
            assertEquals(7, pagedIds.distinct().size)
            val tied = remote.fetchByIds(account, tiedIds)
            assertEquals(tiedIds.toSet(), tied.map { it.id }.toSet())
            assertEquals(1, tied.map { it.receivedAt }.distinct().size)
            val storage = FileTrainingSyncStorage(storageDirectory)
            val progress = TrainingSyncProgress(cursor, initialImportComplete = true)
            storage.saveProgress(account, progress)
            storage.saveReport(account, TrainingSyncReport(123))
            assertEquals(progress, FileTrainingSyncStorage(storageDirectory).load(account).progress)
            assertTrue(remote.fetchPage(account, cursor, 2).documents.isEmpty())
            assertTrue(remote.fetchByIds(account, emptyList()).isEmpty())
            // The new upload still books on the old activityDate, regardless of its ID ordering.
            seed(0, 0)
            assertEquals(1, remote.fetchPage(account, cursor, 2).documents.size)
            val day = LocalDate.parse("2026-09-27").toEpochDays()
            val entries = database.trainingImportDao.observeEntries(TRAINING_PROJECT, uid, day).first()
            assertEquals(2, entries.size)
            assertEquals(330L, entries.sumOf { it.energyKcal })
            firestore.disableNetwork().await()
            try {
                remote.fetchPage(account, null, 200).documents
                fail("Server-only reads must fail offline even after a successful fetch")
            } catch (expected: com.google.firebase.firestore.FirebaseFirestoreException) {
                assertEquals(com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE, expected.code)
            } finally { firestore.enableNetwork().await() }
            remote.signOut()
            assertNull(remote.account.value)
            auth.createUserWithEmailAndPassword("other-" + UUID.randomUUID() + "@example.test", password).await()
            remote.signIn(auth.currentUser!!.email!!, password)
            assertTrue(remote.fetchPage(remote.account.value!!, null, 200).documents.isEmpty())
            try {
                firestore.collection("users").document(uid).collection("trainingSessions")
                    .get(com.google.firebase.firestore.Source.SERVER).await()
                fail("Another account must not read the original user's documents")
            } catch (expected: com.google.firebase.firestore.FirebaseFirestoreException) {
                assertEquals(com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED, expected.code)
            }
            remote.signOut()
            remote.signIn(email, password)
            assertEquals(8, remote.fetchPage(remote.account.value!!, null, 200).documents.size)
        } finally {
            database.close()
            storageDirectory.deleteRecursively()
            auth.signOut()
            firestore.terminate().await()
            app.delete()
        }
    }
}
