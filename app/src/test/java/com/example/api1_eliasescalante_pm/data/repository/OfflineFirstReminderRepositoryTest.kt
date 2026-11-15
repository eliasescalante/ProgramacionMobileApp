package com.example.api1_eliasescalante_pm.data.repository

import com.example.api1_eliasescalante_pm.data.cloud.CloudReminder
import com.example.api1_eliasescalante_pm.data.cloud.CloudResult
import com.example.api1_eliasescalante_pm.data.local.ReminderEntity
import com.example.api1_eliasescalante_pm.domain.model.ReminderCategory
import com.example.api1_eliasescalante_pm.domain.model.ReminderDraft
import com.example.api1_eliasescalante_pm.domain.model.ReminderPriority
import com.example.api1_eliasescalante_pm.domain.model.ReminderStatus
import com.example.api1_eliasescalante_pm.domain.model.SyncStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pruebas del repositorio local-first con Firestore.
 *
 * Se sustituyen Room, Firestore, la red y la sesión por dobles en memoria, por
 * lo que las pruebas no necesitan emulador, credenciales ni conexión.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineFirstReminderRepositoryTest {

    private val uidA = "uid-cuenta-a"
    private val uidB = "uid-cuenta-b"

    private val dispatcher = StandardTestDispatcher()
    private lateinit var scope: CoroutineScope
    private lateinit var dao: FakeReminderDao
    private lateinit var cloud: FakeCloudDataSource
    private lateinit var network: FakeNetworkMonitor
    private lateinit var session: FakeSessionProvider
    private lateinit var repository: OfflineFirstReminderRepository

    private var now = 1_000L

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + dispatcher)
        dao = FakeReminderDao()
        cloud = FakeCloudDataSource()
        network = FakeNetworkMonitor(online = true)
        session = FakeSessionProvider()
        repository = OfflineFirstReminderRepository(
            dao = dao,
            preferencesRepository = FakePreferencesDataSource(),
            cloudDataSource = cloud,
            networkMonitor = network,
            sessionProvider = session,
            appScope = scope,
            clock = { now }
        )
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    // -----------------------------------------------------------------------
    // Sesión y aislamiento
    // -----------------------------------------------------------------------

    @Test
    fun `creating without a session fails and does not touch the cloud`() =
        runTest(dispatcher) {
            val result = repository.createReminder(draft("Comprar pan"))

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("sesión"))
            assertEquals(0, dao.snapshot.size)
            assertEquals(0, cloud.upserts.size)
        }

    @Test
    fun `sync without a session reports NoSession`() = runTest(dispatcher) {
        val status = repository.syncNow()

        assertTrue(status is SyncStatus.NoSession)
        assertEquals(0, cloud.fetchCalls)
    }

    @Test
    fun `queries only expose the authenticated account's rows`() =
        runTest(dispatcher) {
            session.signedIn(uidA)
            dao.upsert(entity(uidA, "a1", syncState = "PENDING"))
            dao.upsert(entity(uidB, "b1", syncState = "PENDING"))

            val visible = repository.reminders.first()

            assertEquals(listOf("a1"), visible.map { it.id })
            assertEquals(1, repository.pendingCount.first())
            assertNull(repository.observeReminder("b1").first())
            assertEquals(1, dao.countByOwner(uidB))
        }

    @Test
    fun `signing out deletes the local copy and the list becomes empty`() =
        runTest(dispatcher) {
            session.signedIn(uidA)
            dao.upsert(entity(uidA, "a1", syncState = "SYNCED"))
            dao.upsert(entity(uidB, "b1", syncState = "SYNCED"))

            // La sincronización queda bloqueada para que la prueba no dependa
            // de una carrera entre el pull y el cierre de sesión.
            cloud.fetchGate = CompletableDeferred()
            repository.start()
            advanceUntilIdle()

            // Al entrar, se descartan las filas de otras cuentas.
            assertEquals(listOf("a1"), dao.snapshot.map { it.id })

            dao.upsert(entity(uidA, "a2", syncState = "SYNCED"))
            session.signedOut()
            advanceUntilIdle()

            assertEquals(0, dao.snapshot.size)
            assertEquals(emptyList<String>(), repository.reminders.first().map { it.id })
        }

    // -----------------------------------------------------------------------
    // Creación, modificación y sincronización
    // -----------------------------------------------------------------------

    @Test
    fun `offline creation stays pending and syncs exactly once`() =
        runTest(dispatcher) {
            session.signedIn(uidA)
            network.online.value = false

            val created = repository.createReminder(draft("Comprar pan")).getOrThrow()

            val pending = dao.getById(uidA, created.id)!!
            assertEquals("PENDING", pending.syncState)
            assertEquals(uidA, pending.ownerUid)
            assertTrue(cloud.documents.isEmpty())

            val offline = repository.syncNow()
            assertTrue(offline is SyncStatus.Offline)
            assertEquals("PENDING", dao.getById(uidA, created.id)?.syncState)
            assertTrue(cloud.documents.isEmpty())

            network.online.value = true
            now = 2_000L
            val firstSync = repository.syncNow()
            assertTrue(firstSync is SyncStatus.Success)
            assertEquals("SYNCED", dao.getById(uidA, created.id)?.syncState)
            assertEquals(2_000L, dao.getById(uidA, created.id)?.syncedAt)
            assertEquals(setOf(created.id), cloud.documents[uidA]?.keys)

            // Reintentar no duplica: se escribe el mismo documento.
            val secondSync = repository.syncNow()
            assertTrue(secondSync is SyncStatus.Success)
            assertEquals(1, cloud.documents[uidA]?.size)
            assertEquals(listOf(created.id), cloud.upserts.map { it.second })
        }

    @Test
    fun `update marks the row pending and preserves createdAt`() =
        runTest(dispatcher) {
            session.signedIn(uidA)
            val created = repository.createReminder(draft("Original")).getOrThrow()

            now = 5_000L
            repository.updateReminder(
                created.copy(title = "Cambiado", description = "Nueva descripción")
            ).getOrThrow()

            val stored = dao.getById(uidA, created.id)!!
            assertEquals("Cambiado", stored.title)
            assertEquals("PENDING", stored.syncState)
            assertEquals(created.createdAt, stored.createdAt)
            assertEquals(5_000L, stored.updatedAt)
        }

    @Test
    fun `a rejected push marks ERROR and the retry sends only once`() =
        runTest(dispatcher) {
            session.signedIn(uidA)
            val created = repository.createReminder(draft("Reintento")).getOrThrow()

            cloud.upsertOverride = CloudResult.Failure("Firestore rechazó los datos.")
            val failed = repository.syncNow()
            assertTrue(failed is SyncStatus.Error)
            assertEquals("ERROR", dao.getById(uidA, created.id)?.syncState)
            assertEquals("Firestore rechazó los datos.", dao.getById(uidA, created.id)?.syncError)
            assertTrue(cloud.upserts.isEmpty())

            cloud.upsertOverride = null
            val retried = repository.syncNow()
            assertTrue(retried is SyncStatus.Success)
            assertEquals("SYNCED", dao.getById(uidA, created.id)?.syncState)
            assertEquals(1, cloud.documents[uidA]?.size)
            assertEquals(listOf(created.id), cloud.upserts.map { it.second })
        }

    // -----------------------------------------------------------------------
    // Pull, reconciliación y borrados
    // -----------------------------------------------------------------------

    @Test
    fun `pull imports a reminder created on another device`() =
        runTest(dispatcher) {
            session.signedIn(uidA)
            cloud.put(cloudReminder("r1", uidA, "Creado en otro equipo", 500L))

            val status = repository.syncNow()

            assertTrue(status is SyncStatus.Success)
            val imported = dao.getById(uidA, "r1")!!
            assertEquals("Creado en otro equipo", imported.title)
            assertEquals("SYNCED", imported.syncState)
            assertEquals(listOf("r1"), repository.reminders.first().map { it.id })
        }

    @Test
    fun `pull never imports documents owned by another account`() =
        runTest(dispatcher) {
            session.signedIn(uidA)
            // El servidor "falla" y devuelve un documento de otra cuenta.
            cloud.put(cloudReminder("b1", uidB, "Secreto de B", 500L))

            val status = repository.syncNow()

            assertTrue(status is SyncStatus.Success)
            assertEquals(0, dao.countByOwner(uidB))
            assertEquals(0, dao.countByOwner(uidA))
        }

    @Test
    fun `last write wins when the cloud copy is newer`() = runTest(dispatcher) {
        session.signedIn(uidA)
        dao.upsert(entity(uidA, "r1", title = "Local viejo", syncState = "SYNCED", updatedAt = 100L))
        cloud.put(cloudReminder("r1", uidA, "Nube nueva", 200L))

        repository.syncNow()

        assertEquals("Nube nueva", dao.getById(uidA, "r1")?.title)
        assertEquals(200L, dao.getById(uidA, "r1")?.updatedAt)
    }

    @Test
    fun `a newer local pending change wins and is pushed`() = runTest(dispatcher) {
        session.signedIn(uidA)
        dao.upsert(
            entity(uidA, "r1", title = "Local pendiente", syncState = "PENDING", updatedAt = 300L)
        )
        cloud.put(cloudReminder("r1", uidA, "Nube vieja", 250L))

        repository.syncNow()

        assertEquals("Local pendiente", dao.getById(uidA, "r1")?.title)
        assertEquals("Local pendiente", cloud.document(uidA, "r1")?.title)
        assertEquals("SYNCED", dao.getById(uidA, "r1")?.syncState)
    }

    @Test
    fun `a reminder deleted on another device disappears locally`() =
        runTest(dispatcher) {
            session.signedIn(uidA)
            dao.upsert(entity(uidA, "r1", syncState = "SYNCED"))

            repository.syncNow()

            assertNull(dao.getById(uidA, "r1"))
        }

    @Test
    fun `offline deletion keeps a tombstone until the cloud confirms`() =
        runTest(dispatcher) {
            session.signedIn(uidA)
            val created = repository.createReminder(draft("Borrar")).getOrThrow()

            repository.syncNow()
            assertTrue(cloud.document(uidA, created.id) != null)

            network.online.value = false
            now = 2_000L
            repository.deleteReminder(created.id).getOrThrow()

            val tombstone = dao.getById(uidA, created.id)!!
            assertTrue(tombstone.isDeleted)
            assertEquals("PENDING", tombstone.syncState)
            assertEquals(0, repository.reminders.first().size)
            assertTrue(cloud.document(uidA, created.id) != null)

            assertTrue(repository.syncNow() is SyncStatus.Offline)
            assertTrue(cloud.document(uidA, created.id) != null)

            network.online.value = true
            assertTrue(repository.syncNow() is SyncStatus.Success)
            assertNull(dao.getById(uidA, created.id))
            assertNull(cloud.document(uidA, created.id))
            assertEquals(listOf(uidA to created.id), cloud.deletes)
        }

    // -----------------------------------------------------------------------
    // Errores del proveedor y concurrencia
    // -----------------------------------------------------------------------

    @Test
    fun `an invalid session from the cloud invalidates the session`() =
        runTest(dispatcher) {
            session.signedIn(uidA)
            cloud.fetchOverride = CloudResult.Unauthenticated("Tu sesión expiró.")

            val status = repository.syncNow()

            assertTrue(status is SyncStatus.NoSession)
            assertEquals(listOf("Tu sesión expiró."), session.invalidations)
        }

    @Test
    fun `permission denied surfaces as a non recoverable error`() =
        runTest(dispatcher) {
            session.signedIn(uidA)
            cloud.fetchOverride = CloudResult.PermissionDenied("No autorizado.")

            val status = repository.syncNow()

            assertTrue(status is SyncStatus.Error)
            assertFalse((status as SyncStatus.Error).recoverable)
            assertEquals("No autorizado.", status.message)
        }

    @Test
    fun `concurrent synchronizations never duplicate a document`() =
        runTest(dispatcher) {
            session.signedIn(uidA)
            val created = repository.createReminder(draft("Concurrente")).getOrThrow()

            val first = async(Dispatchers.Default) { repository.syncNow() }
            val second = async(Dispatchers.Default) { repository.syncNow() }
            assertTrue(first.await() is SyncStatus.Success)
            assertTrue(second.await() is SyncStatus.Success)

            assertEquals(1, cloud.documents[uidA]?.size)
            assertEquals(listOf(created.id), cloud.upserts.map { it.second })
            assertEquals("SYNCED", dao.getById(uidA, created.id)?.syncState)
        }

    // -----------------------------------------------------------------------
    // Utilidades
    // -----------------------------------------------------------------------

    private fun draft(title: String) = ReminderDraft(
        title = title,
        category = ReminderCategory.WORK,
        priority = ReminderPriority.MEDIUM
    )

    private fun entity(
        uid: String,
        id: String,
        title: String = "Recordatorio",
        syncState: String = "PENDING",
        createdAt: Long = 1L,
        updatedAt: Long = 1L
    ) = ReminderEntity(
        id = id,
        ownerUid = uid,
        title = title,
        category = ReminderCategory.WORK.storageValue,
        priority = ReminderPriority.MEDIUM.storageValue,
        status = ReminderStatus.PENDING.storageValue,
        syncState = syncState,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun cloudReminder(
        id: String,
        uid: String,
        title: String,
        updatedAt: Long
    ) = CloudReminder(
        id = id,
        ownerUid = uid,
        title = title,
        category = ReminderCategory.WORK,
        priority = ReminderPriority.MEDIUM,
        status = ReminderStatus.PENDING,
        createdAt = 1L,
        updatedAt = updatedAt
    )
}
