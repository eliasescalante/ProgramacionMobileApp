package com.example.api1_eliasescalante_pm.data.repository

import com.example.api1_eliasescalante_pm.data.local.ReminderDao
import com.example.api1_eliasescalante_pm.data.local.ReminderEntity
import com.example.api1_eliasescalante_pm.data.network.NetworkMonitor
import com.example.api1_eliasescalante_pm.data.preferences.UserPreferencesDataSource
import com.example.api1_eliasescalante_pm.data.remote.ApiResult
import com.example.api1_eliasescalante_pm.data.remote.ReminderRemoteDataSource
import com.example.api1_eliasescalante_pm.data.remote.dto.RemoteReminderDto
import com.example.api1_eliasescalante_pm.data.remote.dto.RemoteReminderRequest
import com.example.api1_eliasescalante_pm.domain.model.PriorityFilter
import com.example.api1_eliasescalante_pm.domain.model.ReminderDraft
import com.example.api1_eliasescalante_pm.domain.model.ReminderPriority
import com.example.api1_eliasescalante_pm.domain.model.ReminderSortOrder
import com.example.api1_eliasescalante_pm.domain.model.ReminderStatus
import com.example.api1_eliasescalante_pm.domain.model.SyncState
import com.example.api1_eliasescalante_pm.domain.model.SyncStatus
import com.example.api1_eliasescalante_pm.domain.model.ThemeMode
import com.example.api1_eliasescalante_pm.domain.model.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineFirstReminderRepositoryTest {

    private val draft = ReminderDraft(
        title = "Comprar pan",
        description = "Pan integral",
        category = com.example.api1_eliasescalante_pm.domain.model.ReminderCategory.PERSONAL,
        priority = ReminderPriority.HIGH
    )

    private fun CoroutineScope.repository(
        dao: FakeReminderDao,
        preferences: FakePreferences,
        remote: FakeRemoteDataSource,
        network: FakeNetworkMonitor
    ) = OfflineFirstReminderRepository(
        dao = dao,
        preferencesRepository = preferences,
        remoteDataSource = remote,
        networkMonitor = network,
        appScope = this
    )

    @Test
    fun `offline creation stays in room as pending`() = runTest {
        val dao = FakeReminderDao()
        val network = FakeNetworkMonitor(online = false)
        val repository = repository(dao, FakePreferences(), FakeRemoteDataSource(), network)

        val result = repository.createReminder(draft)

        assertTrue(result.isSuccess)
        val stored = dao.single()
        assertEquals("Comprar pan", stored.title)
        assertEquals(SyncState.PENDING.storageValue, stored.syncState)
        assertNull(stored.remoteId)
    }

    @Test
    fun `sync pushes a pending reminder once and marks it synced`() = runTest {
        val dao = FakeReminderDao()
        val network = FakeNetworkMonitor(online = false)
        val remote = FakeRemoteDataSource()
        val repository = repository(dao, FakePreferences(), remote, network)

        repository.createReminder(draft)
        network.online = true

        val status = repository.syncNow()

        assertTrue(status is SyncStatus.Success)
        assertEquals(1, remote.createCalls)
        assertEquals(SyncState.SYNCED.storageValue, dao.single().syncState)
        assertEquals(1L, dao.single().remoteId)

        // Un segundo disparador no vuelve a enviar lo ya confirmado.
        repository.syncNow()
        assertEquals(1, remote.createCalls)
    }

    @Test
    fun `concurrent sync triggers never duplicate the remote creation`() = runTest {
        val dao = FakeReminderDao()
        val network = FakeNetworkMonitor(online = false)
        val remote = FakeRemoteDataSource()
        val repository = repository(dao, FakePreferences(), remote, network)

        repository.createReminder(draft)
        network.online = true

        val first = async { repository.syncNow() }
        val second = async { repository.syncNow() }
        first.await()
        second.await()

        assertEquals(1, remote.createCalls)
        assertEquals(1, dao.all().size)
    }

    @Test
    fun `server error marks the row as error and a later sync recovers it`() = runTest {
        val dao = FakeReminderDao()
        val network = FakeNetworkMonitor(online = false)
        val remote = FakeRemoteDataSource()
        val repository = repository(dao, FakePreferences(), remote, network)

        repository.createReminder(draft)
        network.online = true

        remote.createResult = ApiResult.ServerError(500, "Error interno")
        val failed = repository.syncNow()

        assertTrue(failed is SyncStatus.Error)
        assertEquals(SyncState.ERROR.storageValue, dao.single().syncState)
        assertNotNull(dao.single().syncError)

        remote.createResult = null
        val recovered = repository.syncNow()

        assertTrue(recovered is SyncStatus.Success)
        assertEquals(SyncState.SYNCED.storageValue, dao.single().syncState)
    }

    @Test
    fun `pull ignores remote records without clientId`() = runTest {
        val dao = FakeReminderDao()
        val network = FakeNetworkMonitor(online = true)
        val remote = FakeRemoteDataSource().apply {
            fetchResult = ApiResult.Success(
                listOf(
                    RemoteReminderDto(
                        remoteId = 1L,
                        title = "Post ajeno",
                        body = "Contenido de ejemplo"
                    )
                )
            )
        }
        val repository = repository(dao, FakePreferences(), remote, network)

        repository.syncNow()

        assertTrue(dao.all().isEmpty())
    }

    @Test
    fun `remote 404 while updating recreates with the same local row`() = runTest {
        val dao = FakeReminderDao()
        val network = FakeNetworkMonitor(online = true)
        val remote = FakeRemoteDataSource().apply {
            updateResult = ApiResult.ClientError(404, "No existe")
        }
        val repository = repository(dao, FakePreferences(), remote, network)

        dao.upsert(
            ReminderEntity(
                id = "client-id",
                remoteId = 10L,
                title = "Recordatorio existente",
                category = "OTHER",
                priority = "MEDIUM",
                status = "PENDING",
                syncState = SyncState.PENDING.storageValue,
                createdAt = 1L,
                updatedAt = 2L
            )
        )

        repository.syncNow()

        assertEquals(1, remote.createCalls)
        assertEquals(1, dao.all().size)
        assertEquals(1L, dao.single().remoteId)
        assertEquals(SyncState.SYNCED.storageValue, dao.single().syncState)
    }

    @Test
    fun `deleting a synced row keeps a tombstone until the remote delete succeeds`() = runTest {
        val dao = FakeReminderDao()
        val network = FakeNetworkMonitor(online = false)
        val remote = FakeRemoteDataSource()
        val repository = repository(dao, FakePreferences(), remote, network)

        dao.upsert(
            ReminderEntity(
                id = "client-id",
                remoteId = 5L,
                title = "Se va a borrar",
                category = "WORK",
                priority = "LOW",
                status = "PENDING",
                syncState = SyncState.SYNCED.storageValue,
                createdAt = 1L,
                updatedAt = 2L
            )
        )

        repository.deleteReminder("client-id")

        assertTrue(dao.single().isDeleted)
        assertTrue(dao.visible().isEmpty())

        network.online = true
        repository.syncNow()

        assertEquals(1, remote.deleteCalls)
        assertTrue(dao.all().isEmpty())
    }

    @Test
    fun `completed reminders are filtered according to preferences`() = runTest {
        val dao = FakeReminderDao()
        val preferences = FakePreferences(
            initial = UserPreferences(showCompleted = false)
        )
        val repository = repository(
            dao,
            preferences,
            FakeRemoteDataSource(),
            FakeNetworkMonitor(online = false)
        )

        dao.upsert(
            ReminderEntity(
                id = "a",
                title = "Completado",
                category = "OTHER",
                priority = "MEDIUM",
                status = ReminderStatus.COMPLETED.storageValue,
                syncState = SyncState.SYNCED.storageValue,
                createdAt = 1L,
                updatedAt = 1L
            )
        )

        val visible = repository.reminders.first()

        assertTrue(visible.isEmpty())
    }
}

// ---------------------------------------------------------------------------
// Dobles de prueba
// ---------------------------------------------------------------------------

private class FakePreferences(
    initial: UserPreferences = UserPreferences()
) : UserPreferencesDataSource {

    private val state = MutableStateFlow(initial)
    override val preferences: Flow<UserPreferences> = state

    override suspend fun setThemeMode(mode: ThemeMode) {
        state.update { it.copy(themeMode = mode) }
    }

    override suspend fun setShowCompleted(show: Boolean) {
        state.update { it.copy(showCompleted = show) }
    }

    override suspend fun setSortOrder(order: ReminderSortOrder) {
        state.update { it.copy(sortOrder = order) }
    }

    override suspend fun setPriorityFilter(filter: PriorityFilter) {
        state.update { it.copy(priorityFilter = filter) }
    }

    override suspend fun resetToDefaults() {
        state.value = UserPreferences()
    }
}

private class FakeNetworkMonitor(
    @Volatile var online: Boolean
) : NetworkMonitor {

    override fun isOnline(): Boolean = online

    override val isOnlineFlow: Flow<Boolean> = MutableStateFlow(online)
}

private class FakeRemoteDataSource : ReminderRemoteDataSource {

    @Volatile
    var fetchResult: ApiResult<List<RemoteReminderDto>> =
        ApiResult.Success(emptyList())

    @Volatile
    var createResult: ApiResult<RemoteReminderDto>? = null

    @Volatile
    var updateResult: ApiResult<RemoteReminderDto>? = null

    @Volatile
    var deleteResult: ApiResult<Unit> = ApiResult.Success(Unit)

    @Volatile
    var createCalls = 0
        private set

    @Volatile
    var updateCalls = 0
        private set

    @Volatile
    var deleteCalls = 0
        private set

    private var nextId = 1L

    override suspend fun fetchReminders(): ApiResult<List<RemoteReminderDto>> =
        fetchResult

    override suspend fun createReminder(
        request: RemoteReminderRequest
    ): ApiResult<RemoteReminderDto> {
        createCalls++
        return createResult ?: ApiResult.Success(
            RemoteReminderDto(
                remoteId = nextId++,
                clientId = request.clientId,
                title = request.title,
                body = request.body
            )
        )
    }

    override suspend fun updateReminder(
        remoteId: Long,
        request: RemoteReminderRequest
    ): ApiResult<RemoteReminderDto> {
        updateCalls++
        return updateResult ?: ApiResult.Success(
            RemoteReminderDto(
                remoteId = remoteId,
                clientId = request.clientId,
                title = request.title,
                body = request.body
            )
        )
    }

    override suspend fun deleteReminder(remoteId: Long): ApiResult<Unit> {
        deleteCalls++
        return deleteResult
    }
}

private class FakeReminderDao : ReminderDao {

    private val state = MutableStateFlow<Map<String, ReminderEntity>>(emptyMap())

    fun all(): List<ReminderEntity> = state.value.values.toList()

    fun visible(): List<ReminderEntity> = all().filter { !it.isDeleted }

    fun single(): ReminderEntity = all().single()

    override fun observeVisible(): Flow<List<ReminderEntity>> =
        state.map { map -> map.values.filter { !it.isDeleted } }
            .distinctUntilChanged()

    override fun observeByStatus(status: String): Flow<List<ReminderEntity>> =
        state.map { map ->
            map.values.filter { !it.isDeleted && it.status == status }
        }.distinctUntilChanged()

    override fun observeByPriority(priority: String): Flow<List<ReminderEntity>> =
        state.map { map ->
            map.values.filter { !it.isDeleted && it.priority == priority }
        }.distinctUntilChanged()

    override fun observeById(id: String): Flow<ReminderEntity?> =
        state.map { it[id] }.distinctUntilChanged()

    override fun observePendingCount(): Flow<Int> =
        state.map { map ->
            map.values.count {
                !it.isDeleted && (it.syncState == "PENDING" || it.syncState == "ERROR")
            }
        }.distinctUntilChanged()

    override suspend fun getById(id: String): ReminderEntity? = state.value[id]

    override suspend fun getByRemoteId(remoteId: Long): ReminderEntity? =
        state.value.values.firstOrNull { it.remoteId == remoteId }

    override suspend fun getAllVisible(): List<ReminderEntity> = visible()

    override suspend fun getPendingForSync(): List<ReminderEntity> =
        all().filter { it.syncState == "PENDING" || it.syncState == "ERROR" }

    override suspend fun upsert(entity: ReminderEntity) {
        state.update { it + (entity.id to entity) }
    }

    override suspend fun upsertAll(entities: List<ReminderEntity>) {
        state.update { current -> current + entities.associateBy { it.id } }
    }

    override suspend fun deleteById(id: String) {
        state.update { it - id }
    }

    override suspend fun deleteAll() {
        state.value = emptyMap()
    }

    override suspend fun updateSyncInfo(
        id: String,
        syncState: String,
        syncError: String?,
        remoteId: Long?
    ) {
        state.update { current ->
            val entity = current[id] ?: return@update current
            current + (
                id to entity.copy(
                    syncState = syncState,
                    syncError = syncError,
                    remoteId = remoteId ?: entity.remoteId
                )
                )
        }
    }

    override suspend fun markDeleted(
        id: String,
        syncState: String,
        updatedAt: Long
    ) {
        state.update { current ->
            val entity = current[id] ?: return@update current
            current + (
                id to entity.copy(
                    isDeleted = true,
                    syncState = syncState,
                    syncError = null,
                    updatedAt = updatedAt
                )
                )
        }
    }
}
