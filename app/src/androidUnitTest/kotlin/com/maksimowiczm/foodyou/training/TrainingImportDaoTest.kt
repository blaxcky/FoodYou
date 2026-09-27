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
    @Test fun receiptsSurviveDatabaseRestart() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "training-restart-" + java.util.UUID.randomUUID()
        try {
            Room.databaseBuilder(context, FoodYouDatabase::class.java, name).build().let { db ->
                try { db.trainingImportDao.importDocument(account, trainingDocument(), 100) }
                finally { db.close() }
            }
            Room.databaseBuilder(context, FoodYouDatabase::class.java, name).build().let { db ->
                try {
                    assertEquals(TrainingImportResult.AlreadyImported, db.trainingImportDao.importDocument(account, trainingDocument(), 200))
                    assertEquals(330L, db.trainingImportDao.observeEntries(TRAINING_PROJECT, account.uid, date).first().sumOf { it.energyKcal })
                } finally { db.close() }
            }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun onlyActiveAccountCountsAndLogoutPreservesManualEntries() = runTest {
        val owner = kotlinx.coroutines.flow.MutableStateFlow<TrainingAccount?>(account)
        val repository = com.maksimowiczm.foodyou.activity.infrastructure.repository.RoomActivityRepository(
            database.manualActivityEntryDao, database.dailyStepSummaryDao,
            database.stepExclusionPeriodDao, dao, owner)
        val day = LocalDate.fromEpochDays(date)
        val time = kotlinx.datetime.LocalDateTime.parse("2026-09-27T12:00:00")
        repository.createManualEntry(com.maksimowiczm.foodyou.activity.domain.entity.ManualActivityEntry(
            com.maksimowiczm.foodyou.activity.domain.entity.ManualActivityEntryId(0),
            day, "Manuell", 42.0, time, time))
        dao.importDocument(account, trainingDocument(), 100)
        dao.importDocument(account.copy(uid = "B"), trainingDocument(strength = 10, cardio = 0), 100)
        assertEquals(372.0, repository.observeDailySummary(day, null).first().totalEnergyKcal)
        owner.value = account.copy(uid = "B")
        assertEquals(52.0, repository.observeDailySummary(day, null).first().totalEnergyKcal)
        owner.value = null
        assertEquals(42.0, repository.observeDailySummary(day, null).first().totalEnergyKcal)
        owner.value = account
        assertEquals(372.0, repository.observeDailySummary(day, null).first().totalEnergyKcal)
        assertEquals(0.0, repository.observeDailySummary(LocalDate.parse("2026-09-28"), null).first().totalEnergyKcal)
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
