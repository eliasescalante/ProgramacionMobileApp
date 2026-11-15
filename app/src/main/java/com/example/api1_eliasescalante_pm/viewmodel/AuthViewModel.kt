package com.example.api1_eliasescalante_pm.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.api1_eliasescalante_pm.data.repository.AuthRepository
import com.example.api1_eliasescalante_pm.domain.model.AuthValidation
import com.example.api1_eliasescalante_pm.ui.ui.state.AuthUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Administra el formulario de registro e inicio de sesión.
 * Toda la conversación con Firebase ocurre en el repositorio; este ViewModel
 * solo valida, expone estado y dispara acciones.
 */
class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) {
        _uiState.update {
            it.copy(email = value, emailError = null, error = null)
        }
    }

    fun onPasswordChange(value: String) {
        _uiState.update {
            it.copy(password = value, passwordError = null, error = null)
        }
    }

    fun onConfirmationChange(value: String) {
        _uiState.update {
            it.copy(confirmPassword = value, confirmationError = null, error = null)
        }
    }

    fun toggleMode() {
        _uiState.update {
            it.copy(
                mode = it.mode.toggle(),
                password = "",
                confirmPassword = "",
                passwordError = null,
                confirmationError = null,
                error = null
            )
        }
    }

    fun submit() {
        val state = _uiState.value
        if (state.isSubmitting) return

        val emailError = AuthValidation.emailError(state.email)
        val passwordError = AuthValidation.passwordError(state.password)
        val confirmationError = if (state.isRegister) {
            AuthValidation.confirmationError(state.password, state.confirmPassword)
        } else {
            null
        }

        if (emailError != null || passwordError != null || confirmationError != null) {
            _uiState.update {
                it.copy(
                    emailError = emailError,
                    passwordError = passwordError,
                    confirmationError = confirmationError
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }

            val result = if (state.isRegister) {
                authRepository.register(state.email, state.password)
            } else {
                authRepository.login(state.email, state.password)
            }

            result.fold(
                onSuccess = {
                    // El cambio de AuthState hace que la navegación raíz
                    // reemplace esta pantalla por el listado.
                    _uiState.update {
                        it.copy(isSubmitting = false, password = "", confirmPassword = "")
                    }
                },
                onFailure = { throwable ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            error = throwable.message
                                ?: "No se pudo completar la operación."
                        )
                    }
                }
            )
        }
    }
}
