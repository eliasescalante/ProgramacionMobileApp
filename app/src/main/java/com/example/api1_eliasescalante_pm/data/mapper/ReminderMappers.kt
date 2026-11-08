package com.example.api1_eliasescalante_pm.data.mapper

import com.example.api1_eliasescalante_pm.data.local.ReminderEntity
import com.example.api1_eliasescalante_pm.data.remote.dto.RemoteReminderDto
import com.example.api1_eliasescalante_pm.data.remote.dto.RemoteReminderRequest
import com.example.api1_eliasescalante_pm.domain.model.Reminder
import com.example.api1_eliasescalante_pm.domain.model.ReminderCategory
import com.example.api1_eliasescalante_pm.domain.model.ReminderPriority
import com.example.api1_eliasescalante_pm.domain.model.ReminderStatus
import com.example.api1_eliasescalante_pm.domain.model.SyncState

// ---------------------------------------------------------------------------
// Entity (Room) <-> Domain
// ---------------------------------------------------------------------------

fun ReminderEntity.toDomain(): Reminder = Reminder(
    id = id,
    remoteId = remoteId,
    title = title,
    description = description,
    category = ReminderCategory.fromStorage(category),
    priority = ReminderPriority.fromStorage(priority),
    status = ReminderStatus.fromStorage(status),
    dueDate = dueDate,
    locationOrNotice = locationOrNotice,
    syncState = SyncState.fromStorage(syncState),
    syncError = syncError,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Reminder.toEntity(isDeleted: Boolean = false): ReminderEntity = ReminderEntity(
    id = id,
    remoteId = remoteId,
    title = title,
    description = description,
    category = category.storageValue,
    priority = priority.storageValue,
    status = status.storageValue,
    dueDate = dueDate,
    locationOrNotice = locationOrNotice,
    syncState = syncState.storageValue,
    syncError = syncError,
    isDeleted = isDeleted,
    createdAt = createdAt,
    updatedAt = updatedAt
)

// ---------------------------------------------------------------------------
// Domain -> DTO de request
// ---------------------------------------------------------------------------

fun Reminder.toRemoteRequest(): RemoteReminderRequest = RemoteReminderRequest(
    title = title,
    body = encodeRemoteBody(this),
    clientId = id,
    description = description,
    category = category.storageValue,
    priority = priority.storageValue,
    status = status.storageValue,
    dueDate = dueDate,
    location = locationOrNotice,
    updatedAt = updatedAt
)

// ---------------------------------------------------------------------------
// DTO de respuesta -> Domain
// ---------------------------------------------------------------------------

/**
 * Devuelve null cuando el registro remoto no pertenece a esta aplicación
 * (por ejemplo, los posts de ejemplo de JSONPlaceholder). Así la
 * sincronización no importa datos ajenos ni genera duplicados.
 */
fun RemoteReminderDto.toDomainOrNull(): Reminder? {
    val metadata = parseRemoteMetadata(body)
    val stableId = clientId ?: metadata["clientId"] ?: return null
    val safeTitle = title?.trim()?.takeIf { it.isNotEmpty() } ?: return null

    val remoteUpdatedAt = updatedAt
        ?: metadata["updatedAt"]?.toLongOrNull()
        ?: 0L

    return Reminder(
        id = stableId,
        remoteId = remoteId,
        title = safeTitle,
        description = description?.takeIf { it.isNotBlank() }
            ?: descriptionFromBody(body),
        category = ReminderCategory.fromStorage(category ?: metadata["category"]),
        priority = ReminderPriority.fromStorage(priority ?: metadata["priority"]),
        status = ReminderStatus.fromStorage(status ?: metadata["status"]),
        dueDate = dueDate ?: metadata["dueDate"]?.toLongOrNull(),
        locationOrNotice = location ?: metadata["location"]?.takeIf { it.isNotBlank() },
        syncState = SyncState.SYNCED,
        syncError = null,
        createdAt = metadata["createdAt"]?.toLongOrNull() ?: remoteUpdatedAt,
        updatedAt = remoteUpdatedAt
    )
}

// ---------------------------------------------------------------------------
// Metadatos dentro de "body"
// ---------------------------------------------------------------------------

internal const val META_HEADER = "[recordatorio]"

internal fun encodeRemoteBody(reminder: Reminder): String = buildString {
    val description = reminder.description.orEmpty()
    if (description.isNotBlank()) {
        append(description.trim())
        append("\n\n")
    }
    append(META_HEADER)
    append('\n')
    append("clientId=").append(reminder.id).append('\n')
    append("category=").append(reminder.category.storageValue).append('\n')
    append("priority=").append(reminder.priority.storageValue).append('\n')
    append("status=").append(reminder.status.storageValue).append('\n')
    append("dueDate=").append(reminder.dueDate ?: "").append('\n')
    append("location=").append(reminder.locationOrNotice.orEmpty().replace('\n', ' ')).append('\n')
    append("createdAt=").append(reminder.createdAt).append('\n')
    append("updatedAt=").append(reminder.updatedAt)
}

internal fun parseRemoteMetadata(body: String?): Map<String, String> {
    if (body.isNullOrBlank()) return emptyMap()
    val headerIndex = body.indexOf(META_HEADER)
    if (headerIndex < 0) return emptyMap()

    val metadataBlock = body.substring(headerIndex + META_HEADER.length)
    return metadataBlock.lineSequence()
        .mapNotNull { line ->
            val separator = line.indexOf('=')
            if (separator <= 0) return@mapNotNull null
            line.substring(0, separator).trim() to line.substring(separator + 1).trim()
        }
        .toMap()
}

internal fun descriptionFromBody(body: String?): String? {
    if (body.isNullOrBlank()) return null
    val headerIndex = body.indexOf(META_HEADER)
    val description = if (headerIndex >= 0) {
        body.substring(0, headerIndex)
    } else {
        body
    }
    return description.trim().takeIf { it.isNotEmpty() }
}
