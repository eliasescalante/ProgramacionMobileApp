package com.example.api1_eliasescalante_pm.data.repository

import com.example.api1_eliasescalante_pm.data.local.ReminderDao
import com.example.api1_eliasescalante_pm.data.mapper.toDomain
import com.example.api1_eliasescalante_pm.data.mapper.toEntity
import com.example.api1_eliasescalante_pm.data.mapper.toDomainOrNull
import com.example.api1_eliasescalante_pm.data.mapper.toRemoteRequest
import com.example.api1_eliasescalante_pm.data.network.NetworkMonitor
import com.example.api1_eliasescalante_pm.data.preferences.UserPreferencesDataSource
import com.example.api1_eliasescalante_pm.data.remote.ApiResult
import com.example.api1_eliasescalante_pm.data.remote.ReminderRemoteDataSource
import com.example.api1_eliasescalante_pm.data.remote.dto.RemoteReminderDto
import com.example.api1_eliasescalante_pm.data.remote.dto.RemoteReminderRequest
import com.example.api1_eliasescalante_pm.domain.model.PriorityFilter
import com.example.api1_eliasescalante_pm.domain.model.Reminder
import com.example.api1_eliasescalante_pm.domain.model.ReminderDraft
import com.example.api1_eliasescalante_pm.domain.model.ReminderPriority
import com.example.api1_eliasescalante_pm.domain.model.ReminderSortOrder
import com.example.api1_eliasescalante_pm.domain.model.ReminderStatus
import com.example.api1_eliasescalante_pm.domain.model.SyncState
import com.example.api1_eliasescalante_pm.domain.model.SyncStatus
import com.example.api1_eliasescalante_pm.domain.model.ThemeMode
import com.example.api1_eliasescalante_pm.domain.model.UserPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Implementación local-first:
 *
 * 1. Toda escritura va primero a Room.
 * 2. La operación se marca como PENDING (o ERROR si falla el envío).
 * 3. La sincronización se serializa con un [Mutex] para evitar envíos
 *    duplicados cuando varios eventos detectan conectividad a la vez.
 * 4. La interfaz observa únicamente flujos de Room, por lo que sigue
 *    funcionando sin red.
 */
class OfflineFirstReminderRepository(
    private val dao: ReminderDao,
    private val preferencesRepository: UserPreferencesDataSource,
    private val remoteDataSource: ReminderRemoteDataSource,
    private val networkMonitor: NetworkMonitor,
    private val appScope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis
) : ReminderRepository {

    private val syncMutex = Mutex()
    private val started = AtomicBoolean(false)
    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)

    override val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    override val preferences: Flow<UserPreferences> = preferencesRepository.preferences

    override val reminders: Flow<List<Reminder>> =
        combine(dao.observeVisible(), preferencesRepository.preferences) { entities, prefs ->
            entities.asSequence()
                .map { it.toDomain() }
                .filter { prefs.showCompleted || it.status != ReminderStatus.COMPLETED }
                .filter {
                    prefs.priorityFilter == PriorityFilter.ALL ||
                        it.priority.storageValue == prefs.priorityFilter.storageValue
                }
                .sortedWith(comparatorFor(prefs))
                .toList()
        }.distinctUntilChanged()

    override val pendingCount: Flow<Int> = dao.observePendingCount()

    override fun observeReminder(id: String): Flow<Reminder?> =
        dao.observeById(id).map { entity ->
            entity?.takeUnless { it.isDeleted }?.toDomain()
        }

    override fun start() {
        if (!started.compareAndSet(false, true)) return

        appScope.launch {
            networkMonitor.isOnlineFlow.collect { online ->
                if (online) {
                    syncNow()
                } else {
                    _syncStatus.value = SyncStatus.Offline()
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Preferencias (DataStore)
    // -----------------------------------------------------------------------

    override suspend fun setThemeMode(mode: ThemeMode) =
        preferencesRepository.setThemeMode(mode)

    override suspend fun setShowCompleted(showCompleted: Boolean) =
        preferencesRepository.setShowCompleted(showCompleted)

    override suspend fun setSortOrder(order: ReminderSortOrder) =
        preferencesRepository.setSortOrder(order)

    override suspend fun setPriorityFilter(filter: PriorityFilter) =
        preferencesRepository.setPriorityFilter(filter)

    override suspend fun resetPreferences() =
        preferencesRepository.resetToDefaults()

    // -----------------------------------------------------------------------
    // Escrituras locales (siempre primero en Room)
    // -----------------------------------------------------------------------

    override suspend fun createReminder(draft: ReminderDraft): Result<Reminder> =
        runCatching {
            require(draft.title.isNotBlank()) { "El título no puede estar vacío." }
            val now = clock()
            val reminder = Reminder(
                id = UUID.randomUUID().toString(),
                title = draft.title.trim(),
                description = draft.description?.trim()?.takeIf { it.isNotEmpty() },
                category = draft.category,
                priority = draft.priority,
                status = ReminderStatus.PENDING,
                dueDate = draft.dueDate,
                locationOrNotice = draft.locationOrNotice
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() },
                syncState = SyncState.PENDING,
                createdAt = now,
                updatedAt = now
            )
            dao.upsert(reminder.toEntity())
            requestSync()
            reminder
        }

    override suspend fun updateReminder(reminder: Reminder): Result<Unit> =
        runCatching {
            require(reminder.title.isNotBlank()) { "El título no puede estar vacío." }
            val current = dao.getById(reminder.id)
                ?: error("El recordatorio ya no existe en la base local.")

            val updated = reminder.copy(
                remoteId = current.remoteId,
                syncState = SyncState.PENDING,
                syncError = null,
                createdAt = current.createdAt,
                updatedAt = clock()
            )
            dao.upsert(updated.toEntity(isDeleted = current.isDeleted))
            requestSync()
        }

    override suspend fun deleteReminder(id: String): Result<Unit> =
        runCatching {
            val current = dao.getById(id) ?: return@runCatching

            if (current.remoteId == null) {
                // Nunca llegó al servidor: se puede borrar físicamente.
                dao.deleteById(id)
            } else {
                // Tombstone: se oculta de la UI y se elimina en el servidor
                // en la próxima sincronización, incluso si no hay red ahora.
                dao.markDeleted(
                    id = id,
                    syncState = SyncState.PENDING.storageValue,
                    updatedAt = clock()
                )
            }
            requestSync()
        }

    override suspend fun setStatus(id: String, status: ReminderStatus): Result<Unit> =
        runCatching {
            val current = dao.getById(id) ?: return@runCatching
            if (current.status == status.storageValue) return@runCatching

            val updated = current.copy(
                status = status.storageValue,
                syncState = SyncState.PENDING.storageValue,
                syncError = null,
                updatedAt = clock()
            )
            dao.upsert(updated)
            requestSync()
        }

    override suspend fun clearLocalData(): Result<Unit> = runCatching {
        dao.deleteAll()
    }

    // -----------------------------------------------------------------------
    // Sincronización
    // -----------------------------------------------------------------------

    /**
     * Un solo Mutex serializa todas las sincronizaciones. Si dos eventos
     * (arranque, retorno de red, botón manual) coinciden, el segundo espera y
     * encuentra la cola de pendientes vacía: no se reenvía nada.
     */
    override suspend fun syncNow(): SyncStatus = withContext(Dispatchers.IO) {
        if (!networkMonitor.isOnline()) {
            return@withContext publish(
                SyncStatus.Offline(
                    "Sin conexión. Los cambios quedaron guardados y se enviarán al recuperar la red."
                )
            )
        }

        syncMutex.withLock {
            if (!networkMonitor.isOnline()) {
                return@withLock publish(SyncStatus.Offline())
            }

            _syncStatus.value = SyncStatus.Syncing
            var pulled = 0
            var pushed = 0
            val errors = mutableListOf<String>()

            try {
                // 1) Pull primero: permite reconciliar una creación cuyo
                //    response se perdió (mismo clientId en el body).
                when (val pull = remoteDataSource.fetchReminders()) {
                    is ApiResult.Success -> pulled = mergeRemote(pull.value)
                    is ApiResult.NoConnection -> return@withLock publish(
                        SyncStatus.Offline(pull.message)
                    )

                    is ApiResult.Timeout -> errors += pull.message
                    is ApiResult.ClientError -> errors += pull.message
                    is ApiResult.ServerError -> errors += pull.message
                    is ApiResult.UnknownError -> errors += pull.message
                }

                // 2) Push de la cola local, serializado.
                val pending = dao.getPendingForSync()
                for (entity in pending) {
                    if (!networkMonitor.isOnline()) {
                        return@withLock publish(
                            SyncStatus.Offline(
                                "Se perdió la conexión durante la sincronización. " +
                                    "Los cambios pendientes permanecen en Room."
                            )
                        )
                    }

                    when (val outcome = pushOne(entity)) {
                        is PushOutcome.Success -> pushed++

                        is PushOutcome.NoConnection -> return@withLock publish(
                            SyncStatus.Offline(outcome.message)
                        )

                        is PushOutcome.Failure -> {
                            errors += outcome.message
                            dao.updateSyncInfo(
                                id = entity.id,
                                syncState = SyncState.ERROR.storageValue,
                                syncError = outcome.message,
                                remoteId = entity.remoteId
                            )
                        }
                    }
                }

                val status = if (errors.isEmpty()) {
                    SyncStatus.Success(pushed = pushed, pulled = pulled)
                } else {
                    SyncStatus.Error(errors.first(), recoverable = true)
                }
                publish(status)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                publish(
                    SyncStatus.Error(
                        "No se pudo completar la sincronización: " +
                            (exception.message ?: "error desconocido")
                    )
                )
            }
        }
    }

    private fun publish(status: SyncStatus): SyncStatus {
        _syncStatus.value = status
        return status
    }

    private fun requestSync() {
        appScope.launch { syncNow() }
    }

    /**
     * Fusiona lo recibido del servicio. Solo importa registros con clientId
     * propio; nunca duplica un registro ya existente en Room.
     */
    private suspend fun mergeRemote(remoteItems: List<RemoteReminderDto>): Int {
        var imported = 0

        for (dto in remoteItems) {
            val remote = dto.toDomainOrNull() ?: continue

            val existing = remote.remoteId?.let { dao.getByRemoteId(it) }
                ?: dao.getById(remote.id)

            if (existing == null) {
                dao.upsert(remote.copy(syncState = SyncState.SYNCED).toEntity())
                imported++
                continue
            }

            if (existing.isDeleted) {
                // Una eliminación local pendiente no debe resucitar por un pull.
                if (existing.remoteId == null && remote.remoteId != null) {
                    dao.updateSyncInfo(
                        id = existing.id,
                        syncState = existing.syncState,
                        syncError = existing.syncError,
                        remoteId = remote.remoteId
                    )
                }
                continue
            }

            val local = existing.toDomain()
            val remoteIsNewer = remote.updatedAt >= local.updatedAt
            val hasLocalPendingChanges =
                local.syncState == SyncState.PENDING ||
                    local.syncState == SyncState.ERROR ||
                    local.syncState == SyncState.SYNCING

            val merged = when {
                hasLocalPendingChanges && remoteIsNewer ->
                    remote.copy(id = local.id, syncState = SyncState.SYNCED, syncError = null)

                hasLocalPendingChanges ->
                    local.copy(remoteId = remote.remoteId ?: local.remoteId)

                remoteIsNewer ->
                    remote.copy(id = local.id, syncState = SyncState.SYNCED, syncError = null)

                else -> local.copy(remoteId = remote.remoteId ?: local.remoteId)
            }

            if (merged != local) {
                dao.upsert(merged.toEntity())
            }
        }

        return imported
    }

    private suspend fun pushOne(entity: com.example.api1_eliasescalante_pm.data.local.ReminderEntity): PushOutcome {
        if (entity.isDeleted) {
            return pushDelete(entity)
        }

        val local = entity.toDomain()
        val request = local.toRemoteRequest()
        val remoteId = entity.remoteId

        if (remoteId == null) {
            return createRemote(entity, request)
        }

        return when (val result = remoteDataSource.updateReminder(remoteId, request)) {
            is ApiResult.Success -> markSynced(entity, result.value.remoteId ?: remoteId)

            is ApiResult.ClientError ->
                if (result.code == 404) {
                    // El servidor ya no tiene el registro: se vuelve a crear
                    // con el mismo clientId para no duplicar localmente.
                    createRemote(entity, request)
                } else {
                    PushOutcome.Failure(result.message)
                }

            is ApiResult.ServerError -> PushOutcome.Failure(result.message)
            is ApiResult.Timeout -> PushOutcome.Failure(result.message)
            is ApiResult.NoConnection -> PushOutcome.NoConnection(result.message)
            is ApiResult.UnknownError -> PushOutcome.Failure(result.message)
        }
    }

    private suspend fun createRemote(
        entity: com.example.api1_eliasescalante_pm.data.local.ReminderEntity,
        request: RemoteReminderRequest
    ): PushOutcome {
        return when (val result = remoteDataSource.createReminder(request)) {
            is ApiResult.Success -> markSynced(entity, result.value.remoteId)
            is ApiResult.ClientError -> PushOutcome.Failure(result.message)
            is ApiResult.ServerError -> PushOutcome.Failure(result.message)
            is ApiResult.Timeout -> PushOutcome.Failure(result.message)
            is ApiResult.NoConnection -> PushOutcome.NoConnection(result.message)
            is ApiResult.UnknownError -> PushOutcome.Failure(result.message)
        }
    }

    private suspend fun pushDelete(
        entity: com.example.api1_eliasescalante_pm.data.local.ReminderEntity
    ): PushOutcome {
        val remoteId = entity.remoteId
        if (remoteId == null) {
            dao.deleteById(entity.id)
            return PushOutcome.Success
        }

        return when (val result = remoteDataSource.deleteReminder(remoteId)) {
            is ApiResult.Success -> {
                dao.deleteById(entity.id)
                PushOutcome.Success
            }

            is ApiResult.ClientError ->
                if (result.code == 404) {
                    // Ya no existe en el servidor: se completa el borrado local.
                    dao.deleteById(entity.id)
                    PushOutcome.Success
                } else {
                    PushOutcome.Failure(result.message)
                }

            is ApiResult.ServerError -> PushOutcome.Failure(result.message)
            is ApiResult.Timeout -> PushOutcome.Failure(result.message)
            is ApiResult.NoConnection -> PushOutcome.NoConnection(result.message)
            is ApiResult.UnknownError -> PushOutcome.Failure(result.message)
        }
    }

    private suspend fun markSynced(
        entity: com.example.api1_eliasescalante_pm.data.local.ReminderEntity,
        remoteId: Long?
    ): PushOutcome {
        if (remoteId == null) {
            return PushOutcome.Failure(
                "El servicio confirmó la operación pero no devolvió un identificador remoto."
            )
        }
        dao.updateSyncInfo(
            id = entity.id,
            syncState = SyncState.SYNCED.storageValue,
            syncError = null,
            remoteId = remoteId
        )
        return PushOutcome.Success
    }

    private fun comparatorFor(preferences: UserPreferences): Comparator<Reminder> {
        return when (preferences.sortOrder) {
            ReminderSortOrder.DATE_DESC ->
                compareByDescending<Reminder> { it.dueDate ?: it.updatedAt }
                    .thenByDescending { it.updatedAt }

            ReminderSortOrder.DATE_ASC ->
                compareBy<Reminder> { it.dueDate ?: Long.MAX_VALUE }
                    .thenByDescending { it.updatedAt }

            ReminderSortOrder.PRIORITY_DESC ->
                compareByDescending<Reminder> { priorityWeight(it.priority) }
                    .thenByDescending { it.updatedAt }

            ReminderSortOrder.TITLE_ASC ->
                compareBy<Reminder> { it.title.lowercase() }
                    .thenByDescending { it.updatedAt }
        }
    }

    private fun priorityWeight(priority: ReminderPriority): Int = when (priority) {
        ReminderPriority.HIGH -> 3
        ReminderPriority.MEDIUM -> 2
        ReminderPriority.LOW -> 1
    }

    private sealed interface PushOutcome {
        data object Success : PushOutcome
        data class Failure(val message: String) : PushOutcome
        data class NoConnection(val message: String) : PushOutcome
    }
}
