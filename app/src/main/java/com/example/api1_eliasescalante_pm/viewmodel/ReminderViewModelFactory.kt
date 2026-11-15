package com.example.api1_eliasescalante_pm.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.api1_eliasescalante_pm.data.repository.AuthRepository
import com.example.api1_eliasescalante_pm.data.repository.ReminderRepository

class ReminderViewModelFactory(
    private val repository: ReminderRepository,
    private val authRepository: AuthRepository,
    private val reminderId: String? = null
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(ReminderListViewModel::class.java) ->
                ReminderListViewModel(repository) as T

            modelClass.isAssignableFrom(ReminderCreateViewModel::class.java) ->
                ReminderCreateViewModel(repository) as T

            modelClass.isAssignableFrom(ReminderDetailViewModel::class.java) -> {
                val id = requireNotNull(reminderId) {
                    "ReminderDetailViewModel requiere un reminderId."
                }
                ReminderDetailViewModel(repository, id) as T
            }

            modelClass.isAssignableFrom(ReminderSettingsViewModel::class.java) ->
                ReminderSettingsViewModel(repository, authRepository) as T

            else -> throw IllegalArgumentException(
                "ViewModel desconocido: ${modelClass.name}"
            )
        }
    }
}
