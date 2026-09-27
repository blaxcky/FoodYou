package com.maksimowiczm.foodyou.training

import android.app.Application
import androidx.room.Room
import androidx.room.execSQL
import androidx.room.useWriterConnection
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.maksimowiczm.foodyou.app.infrastructure.room.FoodYouDatabase
import kotlin.test.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class TrainingImportDaoTest {
    private val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FoodYouDatabase::class.java).build()
    private val dao get() = database.trainingImportDao
    private val account = TrainingAccount(uid = "A", email = null)
    private val date = LocalDate.parse("2026-09-27").toEpochDays()
    @After fun close() { database.close() }

    @Test fun replaysAndDifferentTrainingsAndAccountsNeverCollide() = runTest {
        repeat(5) { dao.importDocument(account, trainingDocument(), 100) }
        assertEquals(2, dao.observeEntries(TRAINING_PROJECT, "A", date).first().size)
        assertEquals(330L, dao.observeEntries(TRAINING_PROJECT, "A", date).first().sumOf { it.energyKcal })
        assertEquals(TrainingImportResult.Conflict, dao.importDocument(account, trainingDocument(strength = 211), 200))
        assertEquals(330L, dao.observeEntries(TRAINING_PROJECT, "A", date).first().sumOf { it.energyKcal })
        dao.importDocument(account, trainingDocument(id = "96aa75c0-0686-4e8c-8b31-750c13409d23"), 100)
        dao.importDocument(account.copy(uid = "B"), trainingDocument(), 100)
        assertEquals(4, dao.observeEntries(TRAINING_PROJECT, "A", date).first().size)
        assertEquals(2, dao.observeEntries(TRAINING_PROJECT, "B", date).first().size)
    }
    @Test fun zeroCaloriesHaveReceiptButNoEntriesAndSingleCategoriesStaySingle() = runTest {
        assertEquals(TrainingImportResult.ZeroCalories, dao.importDocument(account, trainingDocument(strength = 0, cardio = 0), 100))
        assertTrue(dao.observeEntries(TRAINING_PROJECT, "A", date).first().isEmpty())
        assertNotNull(dao.receipt(TRAINING_PROJECT, "A", TEST_SESSION))
        assertEquals(TrainingImportResult.AlreadyImported, dao.importDocument(account, trainingDocument(strength = 0, cardio = 0), 100))
        dao.importDocument(account, trainingDocument("96aa75c0-0686-4e8c-8b31-750c13409d23", cardio = 0), 100)
        dao.importDocument(account, trainingDocument("76aa75c0-0686-4e8c-8b31-750c13409d23", strength = 0), 100)
        assertEquals(listOf("Cardio", "Krafttraining"), dao.observeEntries(TRAINING_PROJECT, "A", date).first().map { it.name }.sorted())
    }
    @Test fun failureBetweenCategoriesRollsBackEntryAndReceipt() = runTest {
        database.useWriterConnection { it.execSQL("CREATE TRIGGER fail_cardio BEFORE INSERT ON ImportedTrainingActivity WHEN NEW.name = 'Cardio' BEGIN SELECT RAISE(ABORT, 'test failure'); END") }
        assertFails { dao.importDocument(account, trainingDocument(), 100) }
        assertTrue(dao.observeEntries(TRAINING_PROJECT, "A", date).first().isEmpty())
        assertNull(dao.receipt(TRAINING_PROJECT, "A", TEST_SESSION))
        database.useWriterConnection { it.execSQL("DROP TRIGGER fail_cardio") }
        assertEquals(TrainingImportResult.Imported, dao.importDocument(account, trainingDocument(), 200))
    }
}
