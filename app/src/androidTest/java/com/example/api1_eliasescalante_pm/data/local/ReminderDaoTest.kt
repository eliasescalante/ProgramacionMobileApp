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
 *
 * Además de la persistencia básica, verifica la primera barrera de
 * aislamiento multiusuario: todas las consultas exigen `ownerUid`, por lo que
 * dos cuentas que compartan archivo de base de datos nunca ven las filas de la
 * otra.
 */
@RunWith(AndroidJUnit4::class)
class ReminderDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: ReminderDao

    private val uidA = "uid-cuenta-a"
    private val uidB = "uid-cuenta-b"

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
        dao.upsert(entity(ownerUid = uidA, id = "a", title = "Primero"))

        assertEquals("Primero", dao.getById(uidA, "a")?.title)
        assertNull(dao.getById(uidA, "inexistente"))
    }

    @Test
    fun rowsAreInvisibleToAnotherOwner() = runBlocking {
        dao.upsert(entity(ownerUid = uidA, id = "a", title = "De A"))

        assertEquals(1, dao.observeVisible(uidA).first().size)
        assertEquals(0, dao.observeVisible(uidB).first().size)
        assertNull(dao.getById(uidB, "a"))
        assertEquals(0, dao.countByOwner(uidB))
    }

    @Test
    fun visibleFlowHidesTombstonesAndFiltersByPriority() = runBlocking {
        dao.upsert(entity(ownerUid = uidA, id = "alta", priority = "HIGH"))
        dao.upsert(entity(ownerUid = uidA, id = "baja", priority = "LOW"))

        assertEquals(2, dao.observeVisible(uidA).first().size)
        assertEquals(
            listOf("alta"),
            dao.observeByPriority(uidA, "HIGH").first().map { it.id }
        )

        dao.markDeleted(ownerUid = uidA, id = "baja", syncState = "PENDING", updatedAt = 10L)

        assertEquals(
            listOf("alta"),
            dao.observeVisible(uidA).first().map { it.id }
        )
    }

    @Test
    fun pendingCountIncludesErrorRows() = runBlocking {
        dao.upsert(entity(ownerUid = uidA, id = "pending", syncState = "PENDING"))
        dao.upsert(entity(ownerUid = uidA, id = "error", syncState = "ERROR"))
        dao.upsert(entity(ownerUid = uidA, id = "synced", syncState = "SYNCED"))

        assertEquals(2, dao.observePendingCount(uidA).first())

        val pending = dao.getPendingForSync(uidA).map { it.id }.toSet()
        assertTrue(pending.containsAll(setOf("pending", "error")))
    }

    @Test
    fun updateSyncInfoStoresSyncedAtAndState() = runBlocking {
        dao.upsert(entity(ownerUid = uidA, id = "a", syncState = "PENDING"))

        dao.updateSyncInfo(
            ownerUid = uidA,
            id = "a",
            syncState = "SYNCED",
            syncError = null,
            syncedAt = 1234L
        )

        val stored = dao.getById(uidA, "a")
        assertEquals("SYNCED", stored?.syncState)
        assertEquals(1234L, stored?.syncedAt)
        assertEquals("a", stored?.id)
    }

    @Test
    fun updateSyncInfoOfAnotherOwnerDoesNotModifyRow() = runBlocking {
        dao.upsert(entity(ownerUid = uidA, id = "a", syncState = "PENDING"))

        dao.updateSyncInfo(
            ownerUid = uidB,
            id = "a",
            syncState = "SYNCED",
            syncError = null,
            syncedAt = 1234L
        )

        val stored = dao.getById(uidA, "a")
        assertEquals("PENDING", stored?.syncState)
        assertNull(stored?.syncedAt)
    }

    @Test
    fun deleteAllExceptKeepsOnlyAuthenticatedOwner() = runBlocking {
        dao.upsert(entity(ownerUid = uidA, id = "a"))
        dao.upsert(entity(ownerUid = uidB, id = "b"))

        dao.deleteAllExcept(uidA)

        assertEquals(listOf("a"), dao.getAllForUser(uidA).map { it.id })
        assertEquals(0, dao.countByOwner(uidB))
    }

    @Test
    fun deleteAllLeavesDatabaseEmpty() = runBlocking {
        dao.upsert(entity(ownerUid = uidA, id = "a"))
        dao.upsert(entity(ownerUid = uidB, id = "b"))

        dao.deleteAll()

        assertEquals(0, dao.countByOwner(uidA))
        assertEquals(0, dao.countByOwner(uidB))
    }

    private fun entity(
        ownerUid: String,
        id: String,
        title: String = "Recordatorio",
        priority: String = "MEDIUM",
        syncState: String = "PENDING"
    ) = ReminderEntity(
        id = id,
        ownerUid = ownerUid,
        title = title,
        category = "OTHER",
        priority = priority,
        status = "PENDING",
        syncState = syncState,
        createdAt = 1L,
        updatedAt = 1L
    )
}
