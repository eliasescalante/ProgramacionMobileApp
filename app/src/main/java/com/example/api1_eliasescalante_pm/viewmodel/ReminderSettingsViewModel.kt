package com.example.api1_eliasescalante_pm.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.api1_eliasescalante_pm.data.repository.AuthRepository
import com.example.api1_eliasescalante_pm.data.repository.ReminderRepository
import com.example.api1_eliasescalante_pm.domain.model.AuthState
import com.example.api1_eliasescalante_pm.domain.model.AuthUser
import com.example.api1_eliasescalante_pm.domain.model.PriorityFilter
import com.example.api1_eliasescalante_pm.domain.model.ReminderSortOrder
import com.example.api1_eliasescalante_pm.domain.model.SyncStatus
import com.example.api1_eliasescalante_pm.domain.model.ThemeMode
import com.example.api1_eliasescalante_pm.domain.model.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReminderSettingsViewModel(
    private val repository: ReminderRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = repository.preferences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserPreferences()
    )

    /** Cuenta autenticada que se muestra en la sección de sesión. */
    val account: StateFlow<AuthUser?> = authRepository.authState
        .map { state -> (state as? AuthState.SignedIn)?.user }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = (authRepository.authState.value as? AuthState.SignedIn)?.user
        )

    val pendingCount: StateFlow<Int> = repository.pendingCount.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 0
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
                    _message.value =
                        "Se borró la copia local. Los datos siguen en Firestore."
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
                is SyncStatus.Success ->
                    "Sincronizado: ${status.pushed} enviados, ${status.pulled} recibidos."

                is SyncStatus.Offline -> status.message
                is SyncStatus.Error -> status.message
                is SyncStatus.NoSession -> status.message
                is SyncStatus.Syncing -> "Sincronizando…"
                is SyncStatus.Idle -> "Sin cambios para sincronizar."
            }
        }
    }

    /**
     * Antes de cerrar sesión se intenta enviar lo pendiente. Si no hay red, el
     * usuario ya fue advertido en el diálogo de confirmación: la copia local se
     * elimina al cerrar sesión para no exponerla a la cuenta siguiente.
     */
    fun logout() {
        viewModelScope.launch {
            if (pendingCount.value > 0) {
                repository.syncNow()
            }
            authRepository.logout().onFailure { throwable ->
                _message.value = throwable.message ?: "No se pudo cerrar sesión."
            }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
