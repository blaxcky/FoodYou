package com.maksimowiczm.foodyou.food.infrastructure.room

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.maksimowiczm.foodyou.app.infrastructure.room.FoodYouDatabase
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class QuickCaptureDaoTest {
    private lateinit var database: FoodYouDatabase
    private lateinit var dao: QuickCaptureDao

    @Before
    fun setUp() {
        database =
            Room.inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    FoodYouDatabase::class.java,
                )
                .allowMainThreadQueries()
                .build()
        dao = database.quickCaptureDao
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun usageOrderingRenameMergeAndDeletePreserveLogSnapshots() = runTest {
        val apple = dao.resolveFoodName("Apple", "apple", 100)
        dao.resolveFoodName("Apple", "apple", 200)
        val pear = dao.resolveFoodName("Pear", "pear", 300)
        val entryId =
            dao.insertEntry(
                QuickCaptureLogEntryEntity(
                    foodNameId = pear.id,
                    foodName = pear.name,
                    weightMode = 0,
                    directWeightInGrams = 125.0,
                    beforeWeightInGrams = null,
                    afterWeightInGrams = null,
                    afterRequired = false,
                    photoPath = null,
                    createdAt = 300,
                    completedAt = null,
                )
            )

        assertEquals(listOf("Apple", "Pear"), dao.observeFoodNames().first().map { it.name })

        dao.renameFoodName(pear.id, "Apple", "apple", 400)

        val merged = dao.observeFoodNames().first()
        assertEquals(1, merged.size)
        assertEquals(3, merged.single().usageCount)
        val movedEntry = dao.observeEntry(entryId).first()
        assertEquals(apple.id, movedEntry?.foodNameId)
        assertEquals("Apple", movedEntry?.foodName)

        dao.deleteFoodName(apple.id)

        val retainedEntry = dao.observeEntry(entryId).first()
        assertNull(retainedEntry?.foodNameId)
        assertEquals("Apple", retainedEntry?.foodName)
    }

    @Test
    fun statusAndCreationOrderAreStableAcrossCrud() = runTest {
        val name = dao.resolveFoodName("Skyr", "skyr", 100)
        val first = dao.insertEntry(entry(name, weight = 100.0, createdAt = 100))
        val second = dao.insertEntry(entry(name, weight = 200.0, createdAt = 200))

        assertEquals(listOf(second, first), dao.observeEntries().first().map { it.id })

        dao.markCompleted(listOf(first), completedAt = 500)
        assertEquals(500, dao.observeEntry(first).first()?.completedAt)

        dao.deleteEntries(listOf(second))
        assertEquals(listOf(first), dao.observeEntries().first().map { it.id })
    }

    private fun entry(
        name: QuickCaptureFoodNameEntity,
        weight: Double,
        createdAt: Long,
    ) =
        QuickCaptureLogEntryEntity(
            foodNameId = name.id,
            foodName = name.name,
            weightMode = 0,
            directWeightInGrams = weight,
            beforeWeightInGrams = null,
            afterWeightInGrams = null,
            afterRequired = false,
            photoPath = null,
            createdAt = createdAt,
            completedAt = null,
        )
}
