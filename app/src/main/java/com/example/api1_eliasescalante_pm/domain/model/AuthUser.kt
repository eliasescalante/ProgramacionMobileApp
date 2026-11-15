package com.example.api1_eliasescalante_pm.domain.model

/**
 * Identidad autenticada que la aplicación obtiene de Firebase Authentication.
 * Es un modelo propio: ni el ViewModel ni la interfaz ven FirebaseUser.
 */
data class AuthUser(
    val uid: String,
    val email: String?,
    val isEmailVerified: Boolean = false
) {
    /** Forma corta y segura de mostrar el uid en la interfaz. */
    val shortUid: String get() = if (uid.length <= 8) uid else "${uid.take(8)}…"
}

/**
 * Estado de autenticación observado por la capa de presentación.
 *
 * - [Restoring]: se está comprobando si existe una sesión persistida.
 * - [SignedOut]: no hay sesión; la aplicación muestra el acceso.
 * - [SignedIn]: hay una sesión activa y un uid con el cual aislar los datos.
 * - [SessionExpired]: la sesión existía pero dejó de ser válida (revocada,
 *   usuario deshabilitado o token vencido).
 */
sealed interface AuthState {
    data object Restoring : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val user: AuthUser) : AuthState
    data class SessionExpired(val reason: String) : AuthState
}

/** Modo del formulario de acceso. */
enum class AuthMode {
    LOGIN,
    REGISTER;

    fun toggle(): AuthMode = if (this == LOGIN) REGISTER else LOGIN
}

/** Error de autenticación ya traducido a lenguaje de la aplicación. */
class AuthException(override val message: String) : Exception(message)

/**
 * Validaciones de credenciales ejecutadas antes de llamar al proveedor.
 * No reemplazan las reglas del servidor: evitan viajes innecesarios y
 * mejoran los mensajes de error.
 */
object AuthValidation {

    private val EMAIL_PATTERN = Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")

    const val MIN_PASSWORD_LENGTH = 6

    fun emailError(value: String): String? {
        val email = value.trim()
        return when {
            email.isEmpty() -> "Ingresá tu correo electrónico."
            !EMAIL_PATTERN.matches(email) -> "El correo no tiene un formato válido."
            else -> null
        }
    }

    fun passwordError(value: String): String? = when {
        value.isEmpty() -> "Ingresá tu contraseña."
        value.length < MIN_PASSWORD_LENGTH ->
            "La contraseña debe tener al menos $MIN_PASSWORD_LENGTH caracteres."
        value.length > 128 -> "La contraseña es demasiado larga."
        else -> null
    }

    fun confirmationError(password: String, confirmation: String): String? = when {
        confirmation.isEmpty() -> "Repetí la contraseña."
        password != confirmation -> "Las contraseñas no coinciden."
        else -> null
    }
}
