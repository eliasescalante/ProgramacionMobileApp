package com.example.api1_eliasescalante_pm.domain.model

/**
 * Estado observable del proceso de sincronización. La interfaz reacciona a
 * este estado, pero nunca interpreta códigos HTTP ni excepciones de red.
 */
sealed interface SyncStatus {

    data object Idle : SyncStatus

    data object Syncing : SyncStatus

    data class Success(
        val pushed: Int,
        val pulled: Int,
        val at: Long = System.currentTimeMillis()
    ) : SyncStatus

    data class Offline(
        val message: String = "Sin conexión. Los datos locales siguen disponibles."
    ) : SyncStatus

    data class Error(
        val message: String,
        val recoverable: Boolean = true,
        val at: Long = System.currentTimeMillis()
    ) : SyncStatus
}
