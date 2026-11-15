package com.example.api1_eliasescalante_pm.data.cloud

/**
 * Resultado de una operación en la nube, expresado en términos de la
 * aplicación. El repositorio y los ViewModel nunca ven códigos de Firestore
 * ni excepciones del proveedor.
 */
sealed interface CloudResult<out T> {

    data class Success<T>(val value: T) : CloudResult<T>

    /** La sesión no existe o dejó de ser válida. */
    data class Unauthenticated(val message: String) : CloudResult<Nothing>

    /** Hay sesión, pero el servidor rechazó el acceso a esos datos. */
    data class PermissionDenied(val message: String) : CloudResult<Nothing>

    /** No se pudo contactar al servicio (sin red, timeout, servicio caído). */
    data class Unavailable(val message: String) : CloudResult<Nothing>

    data class Failure(
        val message: String,
        val retryable: Boolean = true
    ) : CloudResult<Nothing>

    val isSuccess: Boolean get() = this is Success

    fun messageOrNull(): String? = when (this) {
        is Success -> null
        is Unauthenticated -> message
        is PermissionDenied -> message
        is Unavailable -> message
        is Failure -> message
    }
}
