package com.example.api1_eliasescalante_pm.data.mapper

import com.example.api1_eliasescalante_pm.data.cloud.CloudReminder
import com.example.api1_eliasescalante_pm.data.local.ReminderEntity
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
    ownerUid = ownerUid,
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

fun Reminder.toEntity(
    isDeleted: Boolean = false,
    syncedAt: Long? = null
): ReminderEntity = ReminderEntity(
    id = id,
    ownerUid = ownerUid,
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
    updatedAt = updatedAt,
    syncedAt = syncedAt
)

// ---------------------------------------------------------------------------
// Domain -> Cloud
// ---------------------------------------------------------------------------

/**
 * Proyección del dominio al documento de Firestore. El id y el ownerUid se
 * conservan tal cual: son los que garantizan que un reintento actualice el
 * mismo documento y que las reglas de seguridad puedan validar la propiedad.
 */
fun Reminder.toCloud(): CloudReminder = CloudReminder(
    id = id,
    ownerUid = ownerUid,
    title = title,
    description = description,
    category = category,
    priority = priority,
    status = status,
    dueDate = dueDate,
    locationOrNotice = locationOrNotice,
    createdAt = createdAt,
    updatedAt = updatedAt
)

// ---------------------------------------------------------------------------
// Cloud -> Domain
// ---------------------------------------------------------------------------

/**
 * Un registro que llega de Firestore se considera confirmado por el cloud.
 * El repositorio decide si pisa o no la copia local comparando [updatedAt].
 */
fun CloudReminder.toDomain(): Reminder = Reminder(
    id = id,
    ownerUid = ownerUid,
    title = title,
    description = description,
    category = category,
    priority = priority,
    status = status,
    dueDate = dueDate,
    locationOrNotice = locationOrNotice,
    syncState = SyncState.SYNCED,
    syncError = null,
    createdAt = createdAt,
    updatedAt = updatedAt
)
