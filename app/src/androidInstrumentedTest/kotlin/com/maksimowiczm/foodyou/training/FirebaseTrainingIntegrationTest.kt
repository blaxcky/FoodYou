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
            remote.signOut()
            remote.signIn(email, password)
            val account = remote.account.value!!
            assertEquals(uid, account.uid)
            repeat(5) {
                val documents = remote.fetch(account)
                assertEquals(2, documents.size)
                documents.forEach { database.trainingImportDao.importDocument(account, it, 100) }
            }
            val day = LocalDate.parse("2026-09-27").toEpochDays()
            val entries = database.trainingImportDao.observeEntries(TRAINING_PROJECT, uid, day).first()
            assertEquals(2, entries.size)
            assertEquals(330L, entries.sumOf { it.energyKcal })
            firestore.disableNetwork().await()
            try {
                remote.fetch(account)
                fail("Server-only reads must fail offline even after a successful fetch")
            } catch (expected: com.google.firebase.firestore.FirebaseFirestoreException) {
                assertEquals(com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE, expected.code)
            } finally { firestore.enableNetwork().await() }
            remote.signOut()
            assertNull(remote.account.value)
            auth.createUserWithEmailAndPassword("other-" + UUID.randomUUID() + "@example.test", password).await()
            remote.signIn(auth.currentUser!!.email!!, password)
            assertTrue(remote.fetch(remote.account.value!!).isEmpty())
            try {
                firestore.collection("users").document(uid).collection("trainingSessions")
                    .get(com.google.firebase.firestore.Source.SERVER).await()
                fail("Another account must not read the original user's documents")
            } catch (expected: com.google.firebase.firestore.FirebaseFirestoreException) {
                assertEquals(com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED, expected.code)
            }
            remote.signOut()
            remote.signIn(email, password)
            assertEquals(2, remote.fetch(remote.account.value!!).size)
        } finally {
            database.close()
            auth.signOut()
            firestore.terminate().await()
            app.delete()
        }
    }
}
