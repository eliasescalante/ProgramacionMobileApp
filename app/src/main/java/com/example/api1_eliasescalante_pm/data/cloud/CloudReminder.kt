package com.example.api1_eliasescalante_pm.data.cloud

import com.example.api1_eliasescalante_pm.domain.model.ReminderCategory
import com.example.api1_eliasescalante_pm.domain.model.ReminderPriority
import com.example.api1_eliasescalante_pm.domain.model.ReminderStatus

/**
 * Representación de un recordatorio en Firestore.
 *
 * Es un modelo propio de la capa de datos: no es la entidad de Room ni el
 * modelo de dominio. Su única responsabilidad es describir el documento
 * remoto y permitir mapearlo de forma explícita y testeable.
 *
 * El documento vive en `users/{ownerUid}/reminders/{id}` y repite `ownerUid`
 * como campo para que las reglas de seguridad puedan validarlo.
 */
data class CloudReminder(
    val id: String,
    val ownerUid: String,
    val title: String,
    val description: String? = null,
    val category: ReminderCategory = ReminderCategory.OTHER,
    val priority: ReminderPriority = ReminderPriority.MEDIUM,
    val status: ReminderStatus = ReminderStatus.PENDING,
    val dueDate: Long? = null,
    val locationOrNotice: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION
) {
    companion object {
        /** Permite evolucionar el documento sin romper lecturas antiguas. */
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

// ---------------------------------------------------------------------------
// CloudReminder <-> Map (documento de Firestore)
// ---------------------------------------------------------------------------

/**
 * Convierte el modelo a un mapa plano. Se usa `set()` sobre un id de documento
 * estable, por lo que repetir la operación sobrescribe el mismo documento y
 * nunca duplica registros.
 */
fun CloudReminder.toDocument(): Map<String, Any?> = mapOf(
    "id" to id,
    "ownerUid" to ownerUid,
    "title" to title,
    "description" to description,
    "category" to category.storageValue,
    "priority" to priority.storageValue,
    "status" to status.storageValue,
    "dueDate" to dueDate,
    "locationOrNotice" to locationOrNotice,
    "createdAt" to createdAt,
    "updatedAt" to updatedAt,
    "schemaVersion" to schemaVersion
)

/**
 * Interpreta un documento remoto. Devuelve null si:
 *  - el documento no tiene un título válido,
 *  - el `ownerUid` no coincide con el uid esperado,
 *  - el `id` del campo no coincide con el id del documento.
 *
 * Es una defensa adicional: aunque el servidor ya está protegido por reglas,
 * el cliente no confía en datos de una cuenta distinta.
 */
fun Map<String, Any?>.toCloudReminderOrNull(
    documentId: String,
    expectedOwnerUid: String
): CloudReminder? {
    val owner = this["ownerUid"] as? String ?: return null
    if (owner != expectedOwnerUid) return null

    val storedId = this["id"] as? String
    if (storedId != null && storedId != documentId) return null

    val title = (this["title"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return null

    val updatedAt = this["updatedAt"].asLongOrNull() ?: return null
    val createdAt = this["createdAt"].asLongOrNull() ?: updatedAt

    return CloudReminder(
        id = documentId,
        ownerUid = owner,
        title = title,
        description = (this["description"] as? String)?.takeIf { it.isNotBlank() },
        category = ReminderCategory.fromStorage(this["category"] as? String),
        priority = ReminderPriority.fromStorage(this["priority"] as? String),
        status = ReminderStatus.fromStorage(this["status"] as? String),
        dueDate = this["dueDate"].asLongOrNull(),
        locationOrNotice = (this["locationOrNotice"] as? String)
            ?.takeIf { it.isNotBlank() },
        createdAt = createdAt,
        updatedAt = updatedAt,
        schemaVersion = (this["schemaVersion"].asLongOrNull() ?: 1L).toInt()
    )
}

private fun Any?.asLongOrNull(): Long? = when (this) {
    null -> null
    is Long -> this
    is Int -> toLong()
    is Number -> toLong()
    else -> null
}
