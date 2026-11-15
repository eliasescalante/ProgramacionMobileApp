package com.example.api1_eliasescalante_pm.ui.ui.state

import com.example.api1_eliasescalante_pm.domain.model.AuthMode

/**
 * Estado del formulario de acceso. No contiene objetos de Firebase: solo
 * datos de presentación y mensajes ya traducidos.
 */
data class AuthUiState(
    val mode: AuthMode = AuthMode.LOGIN,
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmationError: String? = null,
    val isSubmitting: Boolean = false,
    val error: String? = null
) {
    val isRegister: Boolean get() = mode == AuthMode.REGISTER
}
