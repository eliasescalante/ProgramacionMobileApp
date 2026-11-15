package com.example.api1_eliasescalante_pm.data.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException

/**
 * Traduce los errores de Firebase Authentication a mensajes de la aplicación.
 * La interfaz nunca recibe códigos ni nombres de clases del proveedor.
 */
internal fun Throwable.toAuthMessage(): String = when (this) {
    is FirebaseAuthUserCollisionException ->
        "Ya existe una cuenta con ese correo."

    is FirebaseAuthWeakPasswordException ->
        "La contraseña es demasiado débil. Usá al menos 6 caracteres."

    is FirebaseAuthRecentLoginRequiredException ->
        "Por seguridad, volvé a iniciar sesión para continuar."

    is FirebaseNetworkException ->
        "Sin conexión. Revisá tu red e intentá nuevamente."

    is FirebaseAuthInvalidCredentialsException -> when (errorCode) {
        "ERROR_WRONG_PASSWORD",
        "ERROR_INVALID_CREDENTIAL",
        "ERROR_INVALID_LOGIN_CREDENTIALS" -> "Correo o contraseña incorrectos."

        "ERROR_INVALID_EMAIL" -> "El correo no tiene un formato válido."
        else -> "Las credenciales no son válidas."
    }

    is FirebaseAuthInvalidUserException -> when (errorCode) {
        "ERROR_USER_DISABLED" -> "La cuenta está deshabilitada."
        "ERROR_USER_NOT_FOUND" -> "No existe una cuenta con ese correo."
        else -> "La cuenta ya no está disponible. Iniciá sesión nuevamente."
    }

    is FirebaseAuthException -> when (errorCode) {
        "ERROR_USER_NOT_FOUND" -> "No existe una cuenta con ese correo."
        "ERROR_USER_DISABLED" -> "La cuenta está deshabilitada."
        "ERROR_EMAIL_ALREADY_IN_USE" -> "Ya existe una cuenta con ese correo."
        "ERROR_WEAK_PASSWORD" -> "La contraseña debe tener al menos 6 caracteres."
        "ERROR_TOO_MANY_REQUESTS" ->
            "Demasiados intentos. Esperá unos minutos y volvé a intentarlo."
        "ERROR_OPERATION_NOT_ALLOWED" ->
            "El inicio de sesión con correo no está habilitado en Firebase."
        "ERROR_USER_TOKEN_EXPIRED",
        "ERROR_INVALID_USER_TOKEN" ->
            "Tu sesión expiró. Iniciá sesión nuevamente."
        "ERROR_NETWORK_REQUEST_FAILED" ->
            "Sin conexión. Revisá tu red e intentá nuevamente."
        "ERROR_APP_NOT_AUTHORIZED",
        "ERROR_INVALID_API_KEY" ->
            "Firebase no está configurado correctamente para esta aplicación."
        else -> "No se pudo completar la operación. Intentá nuevamente."
    }

    else -> "No se pudo completar la operación. Intentá nuevamente."
}

/** Indica si el error obliga a descartar la sesión local. */
internal fun Throwable.isSessionInvalid(): Boolean = when (this) {
    is FirebaseAuthInvalidUserException -> true
    is FirebaseAuthException -> errorCode in setOf(
        "ERROR_USER_TOKEN_EXPIRED",
        "ERROR_INVALID_USER_TOKEN",
        "ERROR_USER_DISABLED",
        "ERROR_USER_NOT_FOUND"
    )
    else -> false
}
