package com.example.api1_eliasescalante_pm.data.repository

import com.example.api1_eliasescalante_pm.domain.model.AuthState
import kotlinx.coroutines.flow.StateFlow

/**
 * Acceso de solo lectura a la sesión que consume el repositorio de datos.
 * Mantenerlo como interfaz evita que la capa de datos dependa de la
 * implementación concreta de autenticación (y permite probarla con dobles).
 */
interface SessionProvider {

    val authState: StateFlow<AuthState>

    /** uid de la cuenta autenticada, o null si no hay sesión. */
    fun currentUid(): String?

    /**
     * Se invoca cuando el cloud responde que la sesión ya no es válida.
     * El repositorio de datos no cierra sesión por su cuenta: delega aquí.
     */
    suspend fun invalidateSession(reason: String)
}
