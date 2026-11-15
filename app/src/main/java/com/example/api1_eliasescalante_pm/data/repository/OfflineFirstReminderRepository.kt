package com.example.api1_eliasescalante_pm.data.repository

import com.example.api1_eliasescalante_pm.data.cloud.CloudResult
import com.example.api1_eliasescalante_pm.data.cloud.CloudReminder
import com.example.api1_eliasescalante_pm.data.cloud.ReminderCloudDataSource
import com.example.api1_eliasescalante_pm.data.local.ReminderDao
import com.example.api1_eliasescalante_pm.data.local.ReminderEntity
import com.example.api1_eliasescalante_pm.data.mapper.toCloud
import com.example.api1_eliasescalante_pm.data.mapper.toDomain
import com.example.api1_eliasescalante_pm.data.mapper.toEntity
import com.example.api1_eliasescalante_pm.data.network.NetworkMonitor
import com.example.api1_eliasescalante_pm.data.preferences.UserPreferencesDataSource
import com.example.api1_eliasescalante_pm.domain.model.AuthState
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Repositorio local-first con Firebase como copia remota.
 *
 * Principios:
 *  1. Toda escritura va primero a Room y queda PENDING, con el uid del dueño.
 *  2. La interfaz observa únicamente flujos de Room; funciona sin red.
 *  3. La sincronización se serializa con un [Mutex] y hace pull antes del push.
 *  4. El id del registro es el mismo en Room y en Firestore, por lo que un
 *     reintento hace `set` sobre el mismo documento y no puede duplicar.
 *  5. El uid se toma siempre de la sesión autenticada; ninguna consulta puede
 *     pedir datos de otra cuenta.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class OfflineFirstReminderRepository(
    private val dao: ReminderDao,
    private val preferencesRepository: UserPreferencesDataSource,
    private val cloudDataSource: ReminderCloudDataSource,
    private val networkMonitor: NetworkMonitor,
    private val sessionProvider: SessionProvider,
    private val appScope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis
) : ReminderRepository {

    private val syncMutex = Mutex()
    private val started = AtomicBoolean(false)
    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)

    override val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    override val preferences: Flow<UserPreferences> = preferencesRepository.preferences

    /**
     * La lista se reconstruye cuando cambia la cuenta. Al cerrar sesión el
     * flujo emite una lista vacía: la interfaz no puede conservar datos de la
     * sesión anterior aunque el proceso siga vivo.
     */
    override val reminders: Flow<List<Reminder>> =
        sessionProvider.authState.flatMapLatest { state ->
            val uid = state.signedInUid()
            if (uid == null) {
                flowOf(emptyList())
            } else {
                combine(
                    dao.observeVisible(uid),
                    preferencesRepository.preferences
                ) { entities, prefs -> applyPreferences(entities, prefs) }
            }
        }.distinctUntilChanged()

    override val pendingCount: Flow<Int> =
        sessionProvider.authState.flatMapLatest { state ->
            val uid = state.signedInUid()
            if (uid == null) flowOf(0) else dao.observePendingCount(uid)
        }.distinctUntilChanged()

    override fun observeReminder(id: String): Flow<Reminder?> =
        sessionProvider.authState.flatMapLatest { state ->
            val uid = state.signedInUid()
            if (uid == null) {
                flowOf(null)
            } else {
                dao.observeById(uid, id).map { entity ->
                    entity?.takeUnless { it.isDeleted }?.toDomain()
                }
            }
        }

    override fun start() {
        if (!started.compareAndSet(false, true)) return

        // 1) Reacción a la sesión: al entrar se purgan datos de otras cuentas
        //    y se sincroniza; al salir se borra la copia local.
        appScope.launch {
            sessionProvider.authState.collect { state ->
                when (state) {
                    is AuthState.SignedIn -> {
                        dao.deleteAllExcept(state.user.uid)
                        // La sincronización se lanza aparte: el colector de
                        // sesión no debe quedar bloqueado esperando a la red,
                        // porque un logout durante la sincronización tiene que
                        // limpiar Room igualmente.
                        appScope.launch { syncNow() }
                    }

                    AuthState.SignedOut,
                    is AuthState.SessionExpired -> {
                        dao.deleteAll()
                        _syncStatus.value = SyncStatus.Idle
                    }

                    AuthState.Restoring -> Unit
                }
            }
        }

        // 2) Reacción a la conectividad: al volver la red se reintenta la cola.
        appScope.launch {
            networkMonitor.isOnlineFlow.collect { online ->
                val uid = sessionProvider.currentUid() ?: return@collect
                if (online) {
                    appScope.launch { syncNow() }
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
            val uid = requireUid()
            require(draft.title.isNotBlank()) { "El título no puede estar vacío." }
            val now = clock()
            val reminder = Reminder(
                id = UUID.randomUUID().toString(),
                ownerUid = uid,
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
            val uid = requireUid()
            require(reminder.title.isNotBlank()) { "El título no puede estar vacío." }

            val current = dao.getById(uid, reminder.id)
                ?: error("El recordatorio ya no existe en la base local.")

            val updated = reminder.copy(
                ownerUid = uid,
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
            val uid = requireUid()
            val current = dao.getById(uid, id) ?: return@runCatching

            // Tombstone: se oculta de inmediato y la sincronización elimina el
            // documento remoto. Si el documento no existe, el borrado remoto
            // también es exitoso, por lo que el caso "nunca sincronizado" no
            // necesita una rama distinta.
            dao.markDeleted(
                ownerUid = uid,
                id = id,
                syncState = SyncState.PENDING.storageValue,
                updatedAt = clock()
            )
            requestSync()
        }

    override suspend fun setStatus(id: String, status: ReminderStatus): Result<Unit> =
        runCatching {
            val uid = requireUid()
            val current = dao.getById(uid, id) ?: return@runCatching
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
        val uid = requireUid()
        dao.deleteAllForUser(uid)
    }

    // -----------------------------------------------------------------------
    // Sincronización
    // -----------------------------------------------------------------------

    /**
     * Un solo [Mutex] serializa todas las sincronizaciones. Si coinciden el
     * arranque, el retorno de red y un botón manual, el segundo espera y
     * encuentra la cola ya vacía: no se reenvía lo confirmado.
     */
    override suspend fun syncNow(): SyncStatus = withContext(Dispatchers.IO) {
        val uid = sessionProvider.currentUid()
            ?: return@withContext publish(SyncStatus.NoSession())

        if (!networkMonitor.isOnline()) {
            return@withContext publish(
                SyncStatus.Offline(
                    "Sin conexión. Los cambios quedaron guardados y se " +
                        "enviarán al recuperar la red."
                )
            )
        }

        syncMutex.withLock {
            // La sesión pudo cambiar mientras se esperaba el mutex.
            if (sessionProvider.currentUid() != uid) {
                return@withLock publish(SyncStatus.NoSession())
            }
            if (!networkMonitor.isOnline()) {
                return@withLock publish(SyncStatus.Offline())
            }

            _syncStatus.value = SyncStatus.Syncing
            var pulled = 0
            var pushed = 0
            val errors = mutableListOf<String>()

            try {
                // 1) PULL: traer la copia remota y reconciliar contra Room.
                when (val pull = cloudDataSource.fetchAll(uid)) {
                    is CloudResult.Success -> {
                        pulled = mergeCloud(uid, pull.value)
                        removeDeletedRemotely(
                            uid = uid,
                            remoteIds = pull.value.map { it.id }.toSet()
                        )
                    }

                    is CloudResult.Unauthenticated -> {
                        sessionProvider.invalidateSession(pull.message)
                        return@withLock publish(SyncStatus.NoSession(pull.message))
                    }

                    is CloudResult.PermissionDenied ->
                        return@withLock publish(SyncStatus.Error(pull.message, false))

                    is CloudResult.Unavailable ->
                        return@withLock publish(SyncStatus.Offline(pull.message))

                    is CloudResult.Failure -> errors += pull.message
                }

                // 2) PUSH: enviar únicamente filas pendientes o con error.
                val pending = dao.getPendingForSync(uid)
                for (entity in pending) {
                    if (!networkMonitor.isOnline()) {
                        return@withLock publish(
                            SyncStatus.Offline(
                                "Se perdió la conexión durante la sincronización. " +
                                    "Los cambios pendientes permanecen en Room."
                            )
                        )
                    }
                    if (sessionProvider.currentUid() != uid) {
                        return@withLock publish(SyncStatus.NoSession())
                    }

                    when (val outcome = pushOne(uid, entity)) {
                        is PushOutcome.Success -> pushed++

                        is PushOutcome.NoConnection ->
                            return@withLock publish(SyncStatus.Offline(outcome.message))

                        is PushOutcome.Unauthorized -> {
                            sessionProvider.invalidateSession(outcome.message)
                            return@withLock publish(SyncStatus.NoSession(outcome.message))
                        }

                        is PushOutcome.Failure -> {
                            errors += outcome.message
                            markError(uid, entity.id, outcome.message)
                        }
                    }
                }

                val status = if (errors.isEmpty()) {
                    SyncStatus.Success(pushed = pushed, pulled = pulled)
                } else {
                    SyncStatus.Error(errors.first(), recoverable = true)
                }
                publish(status)
            } catch (cancellation: CancellationException) {
                throw cancellation
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
     * Fusiona la copia remota con Room usando `updatedAt` como criterio
     * (last-write-wins). Nunca crea dos filas para el mismo documento porque
     * el id remoto es la clave primaria local.
     */
    private suspend fun mergeCloud(uid: String, remoteItems: List<CloudReminder>): Int {
        var imported = 0

        for (cloud in remoteItems) {
            // Defensa adicional: el servidor ya filtra por reglas, pero el
            // cliente tampoco importa documentos de otra cuenta.
            if (cloud.ownerUid != uid) continue

            val existing = dao.getById(uid, cloud.id)
            if (existing == null) {
                dao.upsert(cloud.toDomain().toEntity())
                imported++
                continue
            }

            // Una eliminación local pendiente no debe resucitarse con el pull.
            if (existing.isDeleted) continue

            val local = existing.toDomain()
            val hasLocalPendingChanges = local.syncState != SyncState.SYNCED
            val remoteIsNewer = cloud.updatedAt >= local.updatedAt

            val merged = if (hasLocalPendingChanges && !remoteIsNewer) {
                // El cambio local es más reciente: se conserva y se enviará.
                local
            } else {
                cloud.toDomain()
            }

            if (merged != local) {
                dao.upsert(merged.toEntity())
            }
        }

        return imported
    }

    /**
     * Si otro dispositivo eliminó un documento, el pull deja de devolverlo.
     * Las filas ya confirmadas que no aparecen en el cloud se eliminan localmente;
     * las pendientes se respetan porque todavía tienen cambios sin enviar.
     */
    private suspend fun removeDeletedRemotely(uid: String, remoteIds: Set<String>) {
        dao.getSyncedForUser(uid)
            .filterNot { it.id in remoteIds }
            .forEach { dao.deleteById(uid, it.id) }
    }

    private suspend fun pushOne(uid: String, entity: ReminderEntity): PushOutcome {
        if (entity.ownerUid != uid) {
            return PushOutcome.Failure(
                "Se detectó un registro de otra cuenta en la cola local."
            )
        }

        if (entity.isDeleted) {
            return when (val result = cloudDataSource.delete(uid, entity.id)) {
                is CloudResult.Success -> {
                    dao.deleteById(uid, entity.id)
                    PushOutcome.Success
                }
                is CloudResult.Unauthenticated -> PushOutcome.Unauthorized(result.message)
                is CloudResult.PermissionDenied -> PushOutcome.Failure(result.message)
                is CloudResult.Unavailable -> PushOutcome.NoConnection(result.message)
                is CloudResult.Failure -> PushOutcome.Failure(result.message)
            }
        }

        val cloud = entity.toDomain().toCloud()
        return when (val result = cloudDataSource.upsert(uid, cloud)) {
            is CloudResult.Success -> {
                dao.updateSyncInfo(
                    ownerUid = uid,
                    id = entity.id,
                    syncState = SyncState.SYNCED.storageValue,
                    syncError = null,
                    syncedAt = clock()
                )
                PushOutcome.Success
            }
            is CloudResult.Unauthenticated -> PushOutcome.Unauthorized(result.message)
            is CloudResult.PermissionDenied -> PushOutcome.Failure(result.message)
            is CloudResult.Unavailable -> PushOutcome.NoConnection(result.message)
            is CloudResult.Failure -> PushOutcome.Failure(result.message)
        }
    }

    private suspend fun markError(uid: String, id: String, message: String) {
        dao.updateSyncInfo(
            ownerUid = uid,
            id = id,
            syncState = SyncState.ERROR.storageValue,
            syncError = message,
            syncedAt = null
        )
    }

    private fun requireUid(): String = sessionProvider.currentUid()
        ?: throw IllegalStateException(
            "No hay una sesión iniciada. Iniciá sesión para administrar recordatorios."
        )

    private fun AuthState.signedInUid(): String? =
        (this as? AuthState.SignedIn)?.user?.uid

    private fun applyPreferences(
        entities: List<ReminderEntity>,
        preferences: UserPreferences
    ): List<Reminder> {
        return entities.asSequence()
            .map { it.toDomain() }
            .filter { preferences.showCompleted || it.status != ReminderStatus.COMPLETED }
            .filter {
                preferences.priorityFilter == PriorityFilter.ALL ||
                    it.priority.storageValue == preferences.priorityFilter.storageValue
            }
            .sortedWith(comparatorFor(preferences))
            .toList()
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
        data class Unauthorized(val message: String) : PushOutcome
    }
}
