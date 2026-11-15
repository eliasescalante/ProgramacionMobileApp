package com.example.api1_eliasescalante_pm.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entidad de Room: refleja exactamente el esquema de la base local.
 * Los enums se guardan como texto para que una migración de código no
 * invalide los datos existentes.
 *
 * [ownerUid] es el uid de Firebase Authentication dueño del registro. Todas
 * las consultas del DAO lo exigen, por lo que una cuenta nunca puede leer las
 * filas de otra aunque ambas convivan en el mismo archivo de base de datos.
 *
 * [id] es el identificador estable: se usa como clave primaria local y como id
 * del documento en `users/{uid}/reminders/{id}`. Un reintento escribe el mismo
 * documento, por lo que no puede duplicar registros en Firestore.
 *
 * [isDeleted] implementa un "tombstone": el registro se oculta de la interfaz
 * de inmediato y se elimina físicamente cuando Firestore confirma el borrado.
 * Así una eliminación hecha sin conexión no se pierde.
 */
@Entity(
    tableName = "reminders",
    indices = [
        Index(value = ["ownerUid"]),
        Index(value = ["ownerUid", "status"]),
        Index(value = ["ownerUid", "priority"]),
        Index(value = ["ownerUid", "syncState"]),
        Index(value = ["syncedAt"])
    ]
)
data class ReminderEntity(
    @PrimaryKey
    val id: String,
    val ownerUid: String,
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
    val updatedAt: Long,
    /** Última confirmación de Firestore. Se usa para diagnosticar la sincronización. */
    val syncedAt: Long? = null
)
