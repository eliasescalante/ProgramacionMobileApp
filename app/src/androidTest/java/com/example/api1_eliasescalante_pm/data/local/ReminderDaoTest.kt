package com.example.api1_eliasescalante_pm.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prueba de integración del DAO contra una base Room en memoria.
 * Requiere un emulador o dispositivo conectado (prueba instrumentada).
 */
@RunWith(AndroidJUnit4::class)
class ReminderDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: ReminderDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.reminderDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndReadById() = runBlocking {
        dao.upsert(entity(id = "a", title = "Primero"))

        assertEquals("Primero", dao.getById("a")?.title)
        assertNull(dao.getById("inexistente"))
    }

    @Test
    fun visibleFlowHidesTombstonesAndFiltersByPriority() = runBlocking {
        dao.upsert(entity(id = "alta", priority = "HIGH"))
        dao.upsert(entity(id = "baja", priority = "LOW"))

        assertEquals(2, dao.observeVisible().first().size)
        assertEquals(
            listOf("alta"),
            dao.observeByPriority("HIGH").first().map { it.id }
        )

        dao.markDeleted(id = "baja", syncState = "PENDING", updatedAt = 10L)

        assertEquals(
            listOf("alta"),
            dao.observeVisible().first().map { it.id }
        )
    }

    @Test
    fun pendingCountIncludesErrorRows() = runBlocking {
        dao.upsert(entity(id = "pending", syncState = "PENDING"))
        dao.upsert(entity(id = "error", syncState = "ERROR"))
        dao.upsert(entity(id = "synced", syncState = "SYNCED"))

        assertEquals(2, dao.observePendingCount().first())

        val pending = dao.getPendingForSync().map { it.id }.toSet()
        assertTrue(pending.containsAll(setOf("pending", "error")))
    }

    @Test
    fun updateSyncInfoStoresRemoteId() = runBlocking {
        dao.upsert(entity(id = "a", syncState = "PENDING"))

        dao.updateSyncInfo(
            id = "a",
            syncState = "SYNCED",
            syncError = null,
            remoteId = 99L
        )

        val stored = dao.getById("a")
        assertEquals("SYNCED", stored?.syncState)
        assertEquals(99L, stored?.remoteId)
        assertEquals("a", dao.getByRemoteId(99L)?.id)
    }

    private fun entity(
        id: String,
        title: String = "Recordatorio",
        priority: String = "MEDIUM",
        syncState: String = "PENDING"
    ) = ReminderEntity(
        id = id,
        title = title,
        category = "OTHER",
        priority = priority,
        status = "PENDING",
        syncState = syncState,
        createdAt = 1L,
        updatedAt = 1L
    )
}
