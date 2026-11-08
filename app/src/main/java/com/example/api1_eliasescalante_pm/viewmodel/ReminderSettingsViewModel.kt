package com.example.api1_eliasescalante_pm.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.api1_eliasescalante_pm.data.repository.ReminderRepository
import com.example.api1_eliasescalante_pm.domain.model.PriorityFilter
import com.example.api1_eliasescalante_pm.domain.model.ReminderSortOrder
import com.example.api1_eliasescalante_pm.domain.model.ThemeMode
import com.example.api1_eliasescalante_pm.domain.model.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReminderSettingsViewModel(
    private val repository: ReminderRepository
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = repository.preferences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserPreferences()
    )

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setShowCompleted(show: Boolean) {
        viewModelScope.launch { repository.setShowCompleted(show) }
    }

    fun setSortOrder(order: ReminderSortOrder) {
        viewModelScope.launch { repository.setSortOrder(order) }
    }

    fun setPriorityFilter(filter: PriorityFilter) {
        viewModelScope.launch { repository.setPriorityFilter(filter) }
    }

    fun resetPreferences() {
        viewModelScope.launch {
            repository.resetPreferences()
            _message.value = "Preferencias restablecidas a sus valores predeterminados."
        }
    }

    fun clearReminders() {
        viewModelScope.launch {
            repository.clearLocalData().fold(
                onSuccess = {
                    _message.value = "Se borraron los recordatorios locales."
                },
                onFailure = {
                    _message.value = it.message ?: "No se pudieron borrar los datos locales."
                }
            )
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            val status = repository.syncNow()
            _message.value = when (status) {
                is com.example.api1_eliasescalante_pm.domain.model.SyncStatus.Success ->
                    "Sincronizado: ${status.pushed} enviados, ${status.pulled} recibidos."

                is com.example.api1_eliasescalante_pm.domain.model.SyncStatus.Offline ->
                    status.message

                is com.example.api1_eliasescalante_pm.domain.model.SyncStatus.Error ->
                    status.message

                com.example.api1_eliasescalante_pm.domain.model.SyncStatus.Syncing ->
                    "Sincronizando…"

                com.example.api1_eliasescalante_pm.domain.model.SyncStatus.Idle ->
                    "Sin cambios para sincronizar."
            }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
