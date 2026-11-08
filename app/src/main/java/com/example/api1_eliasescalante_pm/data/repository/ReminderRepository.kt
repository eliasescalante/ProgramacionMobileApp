package com.example.api1_eliasescalante_pm.data.repository

import com.example.api1_eliasescalante_pm.domain.model.PriorityFilter
import com.example.api1_eliasescalante_pm.domain.model.Reminder
import com.example.api1_eliasescalante_pm.domain.model.ReminderDraft
import com.example.api1_eliasescalante_pm.domain.model.ReminderSortOrder
import com.example.api1_eliasescalante_pm.domain.model.ReminderStatus
import com.example.api1_eliasescalante_pm.domain.model.SyncStatus
import com.example.api1_eliasescalante_pm.domain.model.ThemeMode
import com.example.api1_eliasescalante_pm.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Punto de entrada único de la capa de datos para la presentación.
 * Room es la fuente de verdad para [reminders]; DataStore lo es para
 * [preferences]. La interfaz nunca ve entidades, DTO ni el DAO.
 */
interface ReminderRepository {

    val reminders: Flow<List<Reminder>>
    val preferences: Flow<UserPreferences>
    val syncStatus: StateFlow<SyncStatus>
    val pendingCount: Flow<Int>

    fun observeReminder(id: String): Flow<Reminder?>

    /** Registra la observación de conectividad y el primer intento de sync. */
    fun start()

    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setShowCompleted(showCompleted: Boolean)
    suspend fun setSortOrder(order: ReminderSortOrder)
    suspend fun setPriorityFilter(filter: PriorityFilter)
    suspend fun resetPreferences()

    suspend fun createReminder(draft: ReminderDraft): Result<Reminder>
    suspend fun updateReminder(reminder: Reminder): Result<Unit>
    suspend fun deleteReminder(id: String): Result<Unit>
    suspend fun setStatus(id: String, status: ReminderStatus): Result<Unit>

    /** Intenta enviar pendientes y traer cambios remotos. Es serializada. */
    suspend fun syncNow(): SyncStatus

    suspend fun clearLocalData(): Result<Unit>
}
