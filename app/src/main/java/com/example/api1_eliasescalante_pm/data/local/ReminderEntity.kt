package com.example.api1_eliasescalante_pm.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entidad de Room: refleja exactamente el esquema de la base local.
 * Los enums se guardan como texto para que una migración de código no
 * invalide los datos existentes.
 *
 * [isDeleted] implementa un "tombstone": el registro se oculta de la interfaz
 * de inmediato y se elimina físicamente cuando el servicio confirma el DELETE.
 * Así una eliminación hecha sin conexión no se pierde.
 */
@Entity(
    tableName = "reminders",
    indices = [
        Index(value = ["status"]),
        Index(value = ["priority"]),
        Index(value = ["syncState"]),
        // No es único: el servicio de demostración (JSONPlaceholder) devuelve
        // el mismo id para cada POST y la identidad local estable es "id".
        Index(value = ["remoteId"])
    ]
)
data class ReminderEntity(
    @PrimaryKey
    val id: String,
    val remoteId: Long? = null,
    val title: String,
    val description: String? = null,
    val category: String,
    val priority: String,
    val status: String,
    val dueDate: Long? = null,
    val locationOrNotice: String? = null,
    val syncState: String = "PENDING",
    val syncError: String? = null,
    val isDeleted: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)
