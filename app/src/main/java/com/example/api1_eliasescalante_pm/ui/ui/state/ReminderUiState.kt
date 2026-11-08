package com.example.api1_eliasescalante_pm.ui.ui.state

import com.example.api1_eliasescalante_pm.domain.model.Reminder
import com.example.api1_eliasescalante_pm.domain.model.ReminderCategory
import com.example.api1_eliasescalante_pm.domain.model.ReminderPriority

/**
 * Estados posibles de la pantalla de listado. La lista que contiene viene
 * siempre de Room, nunca de un DTO remoto.
 */
sealed interface ReminderListUiState {

    data object Loading : ReminderListUiState

    data class Empty(
        val pendingCount: Int = 0
    ) : ReminderListUiState

    data class Success(
        val reminders: List<Reminder>,
        val pendingCount: Int = 0
    ) : ReminderListUiState

    data class Error(
        val message: String
    ) : ReminderListUiState
}

data class ReminderCreateUiState(
    val title: String = "",
    val description: String = "",
    val selectedCategory: ReminderCategory? = null,
    val selectedPriority: ReminderPriority? = null,
    val dueDate: Long? = null,
    val location: String = "",
    val titleError: String? = null,
    val descriptionError: String? = null,
    val categoryError: String? = null,
    val priorityError: String? = null,
    val dueDateError: String? = null,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val generalError: String? = null
)

data class ReminderDetailUiState(
    val isLoading: Boolean = true,
    val reminder: Reminder? = null,
    val error: String? = null,
    val isEditing: Boolean = false,
    val editTitle: String = "",
    val editDescription: String = "",
    val editCategory: ReminderCategory = ReminderCategory.OTHER,
    val editPriority: ReminderPriority = ReminderPriority.MEDIUM,
    val editDueDate: Long? = null,
    val editLocation: String = "",
    val editTitleError: String? = null,
    val editDescriptionError: String? = null,
    val isSaving: Boolean = false,
    val operationError: String? = null,
    val deleted: Boolean = false
)
