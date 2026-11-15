package com.example.api1_eliasescalante_pm.data.repository

import com.example.api1_eliasescalante_pm.data.cloud.CloudReminder
import com.example.api1_eliasescalante_pm.data.cloud.CloudResult
import com.example.api1_eliasescalante_pm.data.cloud.ReminderCloudDataSource
import com.example.api1_eliasescalante_pm.data.local.ReminderDao
import com.example.api1_eliasescalante_pm.data.local.ReminderEntity
import com.example.api1_eliasescalante_pm.data.network.NetworkMonitor
import com.example.api1_eliasescalante_pm.data.preferences.UserPreferencesDataSource
import com.example.api1_eliasescalante_pm.domain.model.AuthState
import com.example.api1_eliasescalante_pm.domain.model.AuthUser
import com.example.api1_eliasescalante_pm.domain.model.PriorityFilter
import com.example.api1_eliasescalante_pm.domain.model.ReminderSortOrder
import com.example.api1_eliasescalante_pm.domain.model.ThemeMode
import com.example.api1_eliasescalante_pm.domain.model.UserPreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

// ---------------------------------------------------------------------------
// Dobles de prueba: sustituyen Room, Firestore, la red y la sesión para poder
// probar el repositorio sin Android, sin Firebase y sin credenciales.
// ---------------------------------------------------------------------------

internal class FakeReminderDao : ReminderDao {

    private val state = MutableStateFlow<List<ReminderEntity>>(emptyList())

    val snapshot: List<ReminderEntity> get() = state.value

    private fun edit(block: (List<ReminderEntity>) -> List<ReminderEntity>) {
        state.value = block(state.value)
    }

    override fun observeVisible(ownerUid: String): Flow<List<ReminderEntity>> =
        state.map { entities ->
            entities.filter { it.ownerUid == ownerUid && !it.isDeleted }
                .sortedByDescending { it.updatedAt }
        }

    override fun observeByStatus(
        ownerUid: String,
        status: String
    ): Flow<List<ReminderEntity>> = state.map { entities ->
        entities.filter {
            it.ownerUid == ownerUid && !it.isDeleted && it.status == status
        }
    }

    override fun observeByPriority(
        ownerUid: String,
        priority: String
    ): Flow<List<ReminderEntity>> = state.map { entities ->
        entities.filter {
            it.ownerUid == ownerUid && !it.isDeleted && it.priority == priority
        }
    }

    override fun observeById(ownerUid: String, id: String): Flow<ReminderEntity?> =
        state.map { entities -> entities.firstOrNull { it.ownerUid == ownerUid && it.id == id } }

    override fun observePendingCount(ownerUid: String): Flow<Int> =
        state.map { entities ->
            entities.count {
                it.ownerUid == ownerUid && !it.isDeleted &&
                    (it.syncState == "PENDING" || it.syncState == "ERROR")
            }
        }

    override suspend fun getById(ownerUid: String, id: String): ReminderEntity? =
        state.value.firstOrNull { it.ownerUid == ownerUid && it.id == id }

    override suspend fun getAllVisible(ownerUid: String): List<ReminderEntity> =
        state.value.filter { it.ownerUid == ownerUid && !it.isDeleted }

    override suspend fun getAllForUser(ownerUid: String): List<ReminderEntity> =
        state.value.filter { it.ownerUid == ownerUid }

    override suspend fun getPendingForSync(ownerUid: String): List<ReminderEntity> =
        state.value.filter {
            it.ownerUid == ownerUid && (it.syncState == "PENDING" || it.syncState == "ERROR")
        }

    override suspend fun getSyncedForUser(ownerUid: String): List<ReminderEntity> =
        state.value.filter {
            it.ownerUid == ownerUid && it.syncState == "SYNCED" && !it.isDeleted
        }

    override suspend fun countByOwner(ownerUid: String): Int =
        state.value.count { it.ownerUid == ownerUid }

    override suspend fun upsert(entity: ReminderEntity) {
        edit { entities -> entities.filterNot { it.id == entity.id } + entity }
    }

    override suspend fun upsertAll(entities: List<ReminderEntity>) {
        entities.forEach { entity ->
            edit { current -> current.filterNot { it.id == entity.id } + entity }
        }
    }

    override suspend fun deleteById(ownerUid: String, id: String) {
        edit { entities -> entities.filterNot { it.ownerUid == ownerUid && it.id == id } }
    }

    override suspend fun deleteAllForUser(ownerUid: String) {
        edit { entities -> entities.filterNot { it.ownerUid == ownerUid } }
    }

    override suspend fun deleteAllExcept(ownerUid: String) {
        edit { entities -> entities.filter { it.ownerUid == ownerUid } }
    }

    override suspend fun deleteAll() {
        state.value = emptyList()
    }

    override suspend fun updateSyncInfo(
        ownerUid: String,
        id: String,
        syncState: String,
        syncError: String?,
        syncedAt: Long?
    ) {
        edit { entities ->
            entities.map { entity ->
                if (entity.ownerUid == ownerUid && entity.id == id) {
                    entity.copy(
                        syncState = syncState,
                        syncError = syncError,
                        syncedAt = syncedAt
                    )
                } else {
                    entity
                }
            }
        }
    }

    override suspend fun markDeleted(
        ownerUid: String,
        id: String,
        syncState: String,
        updatedAt: Long
    ) {
        edit { entities ->
            entities.map { entity ->
                if (entity.ownerUid == ownerUid && entity.id == id) {
                    entity.copy(
                        isDeleted = true,
                        syncState = syncState,
                        syncError = null,
                        updatedAt = updatedAt
                    )
                } else {
                    entity
                }
            }
        }
    }
}

internal class FakeCloudDataSource : ReminderCloudDataSource {

    val documents = mutableMapOf<String, MutableMap<String, CloudReminder>>()
    val upserts = mutableListOf<Pair<String, String>>()
    val deletes = mutableListOf<Pair<String, String>>()

    var fetchCalls = 0
        private set

    var fetchOverride: CloudResult<List<CloudReminder>>? = null
    var upsertOverride: CloudResult<Unit>? = null
    var deleteOverride: CloudResult<Unit>? = null
    var fetchGate: CompletableDeferred<Unit>? = null

    fun put(reminder: CloudReminder) {
        documents.getOrPut(reminder.ownerUid) { mutableMapOf() }[reminder.id] = reminder
    }

    fun document(uid: String, id: String): CloudReminder? = documents[uid]?.get(id)

    override suspend fun fetchAll(uid: String): CloudResult<List<CloudReminder>> {
        fetchCalls++
        fetchGate?.await()
        fetchOverride?.let { return it }
        return CloudResult.Success(documents[uid]?.values?.toList().orEmpty())
    }

    override suspend fun upsert(
        uid: String,
        reminder: CloudReminder
    ): CloudResult<Unit> {
        upsertOverride?.let { return it }
        if (reminder.ownerUid != uid) {
            return CloudResult.PermissionDenied("El recordatorio no pertenece a la cuenta.")
        }
        documents.getOrPut(uid) { mutableMapOf() }[reminder.id] = reminder
        upserts += uid to reminder.id
        return CloudResult.Success(Unit)
    }

    override suspend fun delete(uid: String, reminderId: String): CloudResult<Unit> {
        deleteOverride?.let { return it }
        documents[uid]?.remove(reminderId)
        deletes += uid to reminderId
        return CloudResult.Success(Unit)
    }
}

internal class FakeSessionProvider : SessionProvider {

    private val state = MutableStateFlow<AuthState>(AuthState.SignedOut)
    override val authState: StateFlow<AuthState> = state

    val invalidations = mutableListOf<String>()

    override fun currentUid(): String? = (state.value as? AuthState.SignedIn)?.user?.uid

    override suspend fun invalidateSession(reason: String) {
        invalidations += reason
        state.value = AuthState.SessionExpired(reason)
    }

    fun signedIn(uid: String, email: String = "$uid@test.dev") {
        state.value = AuthState.SignedIn(AuthUser(uid = uid, email = email))
    }

    fun signedOut() {
        state.value = AuthState.SignedOut
    }
}

internal class FakeNetworkMonitor(online: Boolean = true) : NetworkMonitor {

    val online = MutableStateFlow(online)

    override fun isOnline(): Boolean = online.value

    override val isOnlineFlow: Flow<Boolean> = this.online
}

internal class FakePreferencesDataSource : UserPreferencesDataSource {

    private val state = MutableStateFlow(UserPreferences())
    override val preferences: Flow<UserPreferences> = state

    override suspend fun setThemeMode(mode: ThemeMode) {
        state.value = state.value.copy(themeMode = mode)
    }

    override suspend fun setShowCompleted(show: Boolean) {
        state.value = state.value.copy(showCompleted = show)
    }

    override suspend fun setSortOrder(order: ReminderSortOrder) {
        state.value = state.value.copy(sortOrder = order)
    }

    override suspend fun setPriorityFilter(filter: PriorityFilter) {
        state.value = state.value.copy(priorityFilter = filter)
    }

    override suspend fun resetToDefaults() {
        state.value = UserPreferences()
    }
}
