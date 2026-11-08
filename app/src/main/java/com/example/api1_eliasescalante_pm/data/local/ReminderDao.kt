package com.example.api1_eliasescalante_pm.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Operaciones de Room. La interfaz (Compose) nunca usa este DAO
 * directamente: siempre pasa por el repositorio y el ViewModel.
 */
@Dao
interface ReminderDao {

    // ---------------------------------------------------------------------
    // Consultas observables por la interfaz (Flow = cambios automáticos)
    // ---------------------------------------------------------------------

    @Query("SELECT * FROM reminders WHERE isDeleted = 0 ORDER BY updatedAt DESC")
    fun observeVisible(): Flow<List<ReminderEntity>>

    @Query(
        "SELECT * FROM reminders WHERE isDeleted = 0 AND status = :status " +
            "ORDER BY updatedAt DESC"
    )
    fun observeByStatus(status: String): Flow<List<ReminderEntity>>

    @Query(
        "SELECT * FROM reminders WHERE isDeleted = 0 AND priority = :priority " +
            "ORDER BY updatedAt DESC"
    )
    fun observeByPriority(priority: String): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE isDeleted = 0 AND id = :id")
    fun observeById(id: String): Flow<ReminderEntity?>

    @Query(
        "SELECT COUNT(*) FROM reminders " +
            "WHERE isDeleted = 0 AND syncState IN ('PENDING', 'ERROR')"
    )
    fun observePendingCount(): Flow<Int>

    // ---------------------------------------------------------------------
    // Lecturas puntuales
    // ---------------------------------------------------------------------

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: String): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: Long): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE isDeleted = 0 ORDER BY updatedAt DESC")
    suspend fun getAllVisible(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE syncState IN ('PENDING', 'ERROR')")
    suspend fun getPendingForSync(): List<ReminderEntity>

    // ---------------------------------------------------------------------
    // Escrituras
    // ---------------------------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReminderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ReminderEntity>)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM reminders")
    suspend fun deleteAll()

    @Query(
        "UPDATE reminders SET syncState = :syncState, syncError = :syncError, " +
            "remoteId = :remoteId WHERE id = :id"
    )
    suspend fun updateSyncInfo(
        id: String,
        syncState: String,
        syncError: String?,
        remoteId: Long?
    )

    @Query(
        "UPDATE reminders SET isDeleted = 1, syncState = :syncState, " +
            "syncError = NULL, updatedAt = :updatedAt WHERE id = :id"
    )
    suspend fun markDeleted(
        id: String,
        syncState: String,
        updatedAt: Long
    )
}
