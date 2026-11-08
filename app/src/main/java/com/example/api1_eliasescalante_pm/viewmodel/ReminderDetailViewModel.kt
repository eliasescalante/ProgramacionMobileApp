package com.example.api1_eliasescalante_pm.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.api1_eliasescalante_pm.data.repository.ReminderRepository
import com.example.api1_eliasescalante_pm.domain.model.ReminderCategory
import com.example.api1_eliasescalante_pm.domain.model.ReminderPriority
import com.example.api1_eliasescalante_pm.domain.model.ReminderStatus
import com.example.api1_eliasescalante_pm.ui.ui.state.ReminderDetailUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ReminderDetailViewModel(
    private val repository: ReminderRepository,
    private val reminderId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReminderDetailUiState(isLoading = true))
    val uiState: StateFlow<ReminderDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeReminder(reminderId)
                .catch { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "No se pudo leer el recordatorio: " +
                                (throwable.message ?: "error desconocido")
                        )
                    }
                }
                .collect { reminder ->
                    _uiState.update { state ->
                        when {
                            reminder == null && state.deleted -> state

                            reminder == null -> state.copy(
                                isLoading = false,
                                reminder = null,
                                error = "No se encontró el recordatorio."
                            )

                            state.isEditing -> state.copy(
                                isLoading = false,
                                reminder = reminder,
                                error = null
                            )

                            else -> state.copy(
                                isLoading = false,
                                reminder = reminder,
                                error = null,
                                editTitle = reminder.title,
                                editDescription = reminder.description.orEmpty(),
                                editCategory = reminder.category,
                                editPriority = reminder.priority,
                                editDueDate = reminder.dueDate,
                                editLocation = reminder.locationOrNotice.orEmpty()
                            )
                        }
                    }
                }
        }
    }

    fun startEditing() {
        val reminder = _uiState.value.reminder ?: return
        _uiState.update {
            it.copy(
                isEditing = true,
                editTitle = reminder.title,
                editDescription = reminder.description.orEmpty(),
                editCategory = reminder.category,
                editPriority = reminder.priority,
                editDueDate = reminder.dueDate,
                editLocation = reminder.locationOrNotice.orEmpty(),
                editTitleError = null,
                editDescriptionError = null,
                operationError = null
            )
        }
    }

    fun cancelEditing() {
        _uiState.update {
            it.copy(
                isEditing = false,
                editTitleError = null,
                editDescriptionError = null,
                operationError = null
            )
        }
    }

    fun onEditTitleChange(value: String) {
        _uiState.update { it.copy(editTitle = value, editTitleError = validateTitle(value)) }
    }

    fun onEditDescriptionChange(value: String) {
        _uiState.update {
            it.copy(
                editDescription = value,
                editDescriptionError = validateDescription(value)
            )
        }
    }

    fun onEditCategoryChange(category: ReminderCategory) {
        _uiState.update { it.copy(editCategory = category) }
    }

    fun onEditPriorityChange(priority: ReminderPriority) {
        _uiState.update { it.copy(editPriority = priority) }
    }

    fun onEditDueDateChange(dueDate: Long?) {
        _uiState.update { it.copy(editDueDate = dueDate) }
    }

    fun onEditLocationChange(value: String) {
        _uiState.update { it.copy(editLocation = value) }
    }

    fun saveChanges() {
        val state = _uiState.value
        val reminder = state.reminder ?: return
        if (state.isSaving) return

        val titleError = validateTitle(state.editTitle)
        val descriptionError = validateDescription(state.editDescription)
        if (titleError != null || descriptionError != null) {
            _uiState.update {
                it.copy(
                    editTitleError = titleError,
                    editDescriptionError = descriptionError
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, operationError = null) }

            val updated = reminder.copy(
                title = state.editTitle.trim(),
                description = state.editDescription.trim().takeIf { it.isNotEmpty() },
                category = state.editCategory,
                priority = state.editPriority,
                dueDate = state.editDueDate,
                locationOrNotice = state.editLocation.trim().takeIf { it.isNotEmpty() }
            )

            repository.updateReminder(updated).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(isEditing = false, isSaving = false, operationError = null)
                    }
                },
                onFailure = { throwable ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            operationError = throwable.message
                                ?: "No se pudieron guardar los cambios."
                        )
                    }
                }
            )
        }
    }

    fun setStatus(status: ReminderStatus) {
        viewModelScope.launch {
            repository.setStatus(reminderId, status).onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        operationError = throwable.message
                            ?: "No se pudo cambiar el estado."
                    )
                }
            }
        }
    }

    fun deleteReminder() {
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, operationError = null) }
            repository.deleteReminder(reminderId).fold(
                onSuccess = {
                    _uiState.update { it.copy(isSaving = false, deleted = true) }
                },
                onFailure = { throwable ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            operationError = throwable.message
                                ?: "No se pudo eliminar el recordatorio."
                        )
                    }
                }
            )
        }
    }

    fun clearOperationError() {
        _uiState.update { it.copy(operationError = null) }
    }

    private fun validateTitle(value: String): String? = when {
        value.trim().length < 4 -> "El título debe tener al menos 4 caracteres."
        value.trim().length > 80 -> "El título no puede superar los 80 caracteres."
        else -> null
    }

    private fun validateDescription(value: String): String? =
        if (value.length > 200) {
            "La descripción no puede superar los 200 caracteres."
        } else {
            null
        }
}
