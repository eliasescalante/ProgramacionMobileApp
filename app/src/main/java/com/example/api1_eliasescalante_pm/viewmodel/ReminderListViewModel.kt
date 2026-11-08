package com.example.api1_eliasescalante_pm.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.api1_eliasescalante_pm.data.repository.ReminderRepository
import com.example.api1_eliasescalante_pm.domain.model.PriorityFilter
import com.example.api1_eliasescalante_pm.domain.model.Reminder
import com.example.api1_eliasescalante_pm.domain.model.ReminderSortOrder
import com.example.api1_eliasescalante_pm.domain.model.ReminderStatus
import com.example.api1_eliasescalante_pm.domain.model.UserPreferences
import com.example.api1_eliasescalante_pm.ui.ui.state.ReminderListUiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReminderListViewModel(
    private val repository: ReminderRepository
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = repository.preferences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserPreferences()
    )

    val syncStatus = repository.syncStatus

    val uiState: StateFlow<ReminderListUiState> =
        combine(repository.reminders, repository.pendingCount) { reminders, pendingCount ->
            if (reminders.isEmpty()) {
                ReminderListUiState.Empty(pendingCount)
            } else {
                ReminderListUiState.Success(reminders, pendingCount)
            }
        }
            .catch { throwable ->
                emit(
                    ReminderListUiState.Error(
                        "No se pudieron leer los recordatorios locales: " +
                            (throwable.message ?: "error desconocido")
                    )
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = ReminderListUiState.Loading
            )

    fun syncNow() {
        viewModelScope.launch { repository.syncNow() }
    }

    fun setShowCompleted(showCompleted: Boolean) {
        viewModelScope.launch { repository.setShowCompleted(showCompleted) }
    }

    fun setSortOrder(order: ReminderSortOrder) {
        viewModelScope.launch { repository.setSortOrder(order) }
    }

    fun setPriorityFilter(filter: PriorityFilter) {
        viewModelScope.launch { repository.setPriorityFilter(filter) }
    }

    fun toggleCompleted(reminder: Reminder) {
        val nextStatus = if (reminder.status == ReminderStatus.COMPLETED) {
            ReminderStatus.PENDING
        } else {
            ReminderStatus.COMPLETED
        }
        viewModelScope.launch { repository.setStatus(reminder.id, nextStatus) }
    }

    fun delete(reminder: Reminder) {
        viewModelScope.launch { repository.deleteReminder(reminder.id) }
    }
}
