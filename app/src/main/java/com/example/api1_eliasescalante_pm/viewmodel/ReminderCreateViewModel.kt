package com.example.api1_eliasescalante_pm.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.api1_eliasescalante_pm.data.repository.ReminderRepository
import com.example.api1_eliasescalante_pm.domain.model.ReminderCategory
import com.example.api1_eliasescalante_pm.domain.model.ReminderDraft
import com.example.api1_eliasescalante_pm.domain.model.ReminderPriority
import com.example.api1_eliasescalante_pm.ui.ui.state.ReminderCreateUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ReminderCreateViewModel(
    private val repository: ReminderRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReminderCreateUiState())
    val uiState: StateFlow<ReminderCreateUiState> = _uiState.asStateFlow()

    val categories: List<ReminderCategory> = ReminderCategory.entries
    val priorities: List<ReminderPriority> = ReminderPriority.entries

    fun onTitleChange(value: String) {
        _uiState.update {
            it.copy(title = value, titleError = validateTitle(value))
        }
    }

    fun onDescriptionChange(value: String) {
        _uiState.update {
            it.copy(description = value, descriptionError = validateDescription(value))
        }
    }

    fun onCategoryChange(category: ReminderCategory) {
        _uiState.update {
            it.copy(selectedCategory = category, categoryError = null)
        }
    }

    fun onPriorityChange(priority: ReminderPriority) {
        _uiState.update {
            it.copy(selectedPriority = priority, priorityError = null)
        }
    }

    fun onDueDateChange(dueDate: Long?) {
        _uiState.update {
            it.copy(dueDate = dueDate, dueDateError = null)
        }
    }

    fun onLocationChange(value: String) {
        _uiState.update { it.copy(location = value) }
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

    fun saveReminder() {
        val current = _uiState.value
        if (current.isSaving || current.saved) return
        if (!validateForm()) return

        val category = current.selectedCategory ?: return
        val priority = current.selectedPriority ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, generalError = null) }

            try {
                val result = repository.createReminder(
                    ReminderDraft(
                        title = current.title,
                        description = current.description,
                        category = category,
                        priority = priority,
                        dueDate = current.dueDate,
                        locationOrNotice = current.location
                    )
                )

                result.fold(
                    onSuccess = {
                        // El guardado local ya terminó. La sincronización
                        // continúa en segundo plano y la lista mostrará el
                        // estado pendiente/confirmado.
                        _uiState.update {
                            it.copy(isSaving = false, saved = true, generalError = null)
                        }
                    },
                    onFailure = { throwable ->
                        _uiState.update {
                            it.copy(
                                isSaving = false,
                                generalError = throwable.message
                                    ?: "No se pudo guardar el recordatorio."
                            )
                        }
                    }
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        generalError = exception.message
                            ?: "No se pudo guardar el recordatorio."
                    )
                }
            }
        }
    }

    private fun validateForm(): Boolean {
        val state = _uiState.value
        val titleError = validateTitle(state.title)
        val descriptionError = validateDescription(state.description)
        val categoryError = if (state.selectedCategory == null) {
            "Seleccioná una categoría."
        } else {
            null
        }
        val priorityError = if (state.selectedPriority == null) {
            "Seleccioná una prioridad."
        } else {
            null
        }
        val dueDateError = if (state.dueDate != null && state.dueDate <= 0L) {
            "La fecha seleccionada no es válida."
        } else {
            null
        }

        _uiState.update {
            it.copy(
                titleError = titleError,
                descriptionError = descriptionError,
                categoryError = categoryError,
                priorityError = priorityError,
                dueDateError = dueDateError
            )
        }

        return listOf(titleError, descriptionError, categoryError, priorityError, dueDateError)
            .all { it == null }
    }
}
