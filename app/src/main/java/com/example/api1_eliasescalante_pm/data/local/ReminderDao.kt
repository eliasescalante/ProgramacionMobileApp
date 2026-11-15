package com.example.api1_eliasescalante_pm.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Operaciones de Room. La interfaz (Compose) nunca usa este DAO
 * directamente: siempre pasa por el repositorio y el ViewModel.
 *
 * Todas las consultas de lectura y las mutaciones por identificador incluyen
 * `ownerUid`. El uid proviene de la sesión autenticada, nunca de un parámetro
 * controlado por la pantalla. Esta es la primera barrera de aislamiento local;
 * las reglas de Firestore son la barrera del lado del servidor.
 */
@Dao
interface ReminderDao {

    // ---------------------------------------------------------------------
    // Consultas observables por la interfaz (Flow = cambios automáticos)
    // ---------------------------------------------------------------------

    @Query(
        "SELECT * FROM reminders WHERE ownerUid = :ownerUid AND isDeleted = 0 " +
            "ORDER BY updatedAt DESC"
    )
    fun observeVisible(ownerUid: String): Flow<List<ReminderEntity>>

    @Query(
        "SELECT * FROM reminders WHERE ownerUid = :ownerUid AND isDeleted = 0 " +
            "AND status = :status ORDER BY updatedAt DESC"
    )
    fun observeByStatus(ownerUid: String, status: String): Flow<List<ReminderEntity>>

    @Query(
        "SELECT * FROM reminders WHERE ownerUid = :ownerUid AND isDeleted = 0 " +
            "AND priority = :priority ORDER BY updatedAt DESC"
    )
    fun observeByPriority(ownerUid: String, priority: String): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE ownerUid = :ownerUid AND id = :id")
    fun observeById(ownerUid: String, id: String): Flow<ReminderEntity?>

    @Query(
        "SELECT COUNT(*) FROM reminders WHERE ownerUid = :ownerUid " +
            "AND isDeleted = 0 AND syncState IN ('PENDING', 'ERROR')"
    )
    fun observePendingCount(ownerUid: String): Flow<Int>

    // ---------------------------------------------------------------------
    // Lecturas puntuales
    // ---------------------------------------------------------------------

    @Query("SELECT * FROM reminders WHERE ownerUid = :ownerUid AND id = :id")
    suspend fun getById(ownerUid: String, id: String): ReminderEntity?

    @Query(
        "SELECT * FROM reminders WHERE ownerUid = :ownerUid AND isDeleted = 0 " +
            "ORDER BY updatedAt DESC"
    )
    suspend fun getAllVisible(ownerUid: String): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE ownerUid = :ownerUid")
    suspend fun getAllForUser(ownerUid: String): List<ReminderEntity>

    @Query(
        "SELECT * FROM reminders WHERE ownerUid = :ownerUid " +
            "AND syncState IN ('PENDING', 'ERROR')"
    )
    suspend fun getPendingForSync(ownerUid: String): List<ReminderEntity>

    /** Filas ya confirmadas por el cloud, para detectar borrados remotos. */
    @Query(
        "SELECT * FROM reminders WHERE ownerUid = :ownerUid " +
            "AND syncState = 'SYNCED' AND isDeleted = 0"
    )
    suspend fun getSyncedForUser(ownerUid: String): List<ReminderEntity>

    @Query("SELECT COUNT(*) FROM reminders WHERE ownerUid = :ownerUid")
    suspend fun countByOwner(ownerUid: String): Int

    // ---------------------------------------------------------------------
    // Escrituras
    // ---------------------------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReminderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ReminderEntity>)

    @Query("DELETE FROM reminders WHERE ownerUid = :ownerUid AND id = :id")
    suspend fun deleteById(ownerUid: String, id: String)

    /** Borra únicamente los datos de la cuenta indicada. */
    @Query("DELETE FROM reminders WHERE ownerUid = :ownerUid")
    suspend fun deleteAllForUser(ownerUid: String)

    /**
     * Se ejecuta al restaurar una sesión: elimina los datos locales que no
     * pertenezcan a la cuenta autenticada.
     */
    @Query("DELETE FROM reminders WHERE ownerUid != :ownerUid")
    suspend fun deleteAllExcept(ownerUid: String)

    /** Se ejecuta al cerrar sesión: el dispositivo queda sin recordatorios. */
    @Query("DELETE FROM reminders")
    suspend fun deleteAll()

    @Query(
        "UPDATE reminders SET syncState = :syncState, syncError = :syncError, " +
            "syncedAt = :syncedAt WHERE ownerUid = :ownerUid AND id = :id"
    )
    suspend fun updateSyncInfo(
        ownerUid: String,
        id: String,
        syncState: String,
        syncError: String?,
        syncedAt: Long?
    )

    @Query(
        "UPDATE reminders SET isDeleted = 1, syncState = :syncState, " +
            "syncError = NULL, updatedAt = :updatedAt " +
            "WHERE ownerUid = :ownerUid AND id = :id"
    )
    suspend fun markDeleted(
        ownerUid: String,
        id: String,
        syncState: String,
        updatedAt: Long
    )
}
