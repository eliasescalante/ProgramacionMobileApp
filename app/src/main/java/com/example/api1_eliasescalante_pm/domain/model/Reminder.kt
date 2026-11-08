package com.example.api1_eliasescalante_pm.domain.model

/**
 * Categorías de un recordatorio. Se guardan en Room como texto para que el
 * esquema sea estable aunque el orden del enum cambie en el futuro.
 */
enum class ReminderCategory(val storageValue: String) {
    WORK("WORK"),
    PERSONAL("PERSONAL"),
    HEALTH("HEALTH"),
    OTHER("OTHER");

    companion object {
        fun fromStorage(value: String?): ReminderCategory =
            entries.firstOrNull { it.storageValue == value } ?: OTHER
    }
}

enum class ReminderPriority(val storageValue: String) {
    HIGH("HIGH"),
    MEDIUM("MEDIUM"),
    LOW("LOW");

    companion object {
        fun fromStorage(value: String?): ReminderPriority =
            entries.firstOrNull { it.storageValue == value } ?: MEDIUM
    }
}

enum class ReminderStatus(val storageValue: String) {
    PENDING("PENDING"),
    IN_PROGRESS("IN_PROGRESS"),
    COMPLETED("COMPLETED");

    companion object {
        fun fromStorage(value: String?): ReminderStatus =
            entries.firstOrNull { it.storageValue == value } ?: PENDING
    }
}

/**
 * Estado de sincronización de un registro local.
 *
 * - PENDING: existe en Room y todavía no fue confirmado por el servicio.
 * - SYNCING: se está enviando en este momento.
 * - SYNCED: el servicio confirmó la operación.
 * - ERROR: el último intento falló; se reintentará en la próxima sincronización.
 */
enum class SyncState(val storageValue: String) {
    PENDING("PENDING"),
    SYNCING("SYNCING"),
    SYNCED("SYNCED"),
    ERROR("ERROR");

    companion object {
        fun fromStorage(value: String?): SyncState =
            entries.firstOrNull { it.storageValue == value } ?: PENDING
    }
}

/**
 * Modelo de dominio consumido por los ViewModel y por Compose.
 * No depende de Room, Retrofit ni de clases de Compose.
 */
data class Reminder(
    val id: String,
    val remoteId: Long? = null,
    val title: String,
    val description: String? = null,
    val category: ReminderCategory = ReminderCategory.OTHER,
    val priority: ReminderPriority = ReminderPriority.MEDIUM,
    val status: ReminderStatus = ReminderStatus.PENDING,
    val dueDate: Long? = null,
    val locationOrNotice: String? = null,
    val syncState: SyncState = SyncState.PENDING,
    val syncError: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

/**
 * Datos que la capa de presentación entrega al repositorio para crear un
 * recordatorio. El identificador y las marcas de tiempo los genera el
 * repositorio, no la pantalla.
 */
data class ReminderDraft(
    val title: String,
    val description: String? = null,
    val category: ReminderCategory = ReminderCategory.OTHER,
    val priority: ReminderPriority = ReminderPriority.MEDIUM,
    val dueDate: Long? = null,
    val locationOrNotice: String? = null
)
