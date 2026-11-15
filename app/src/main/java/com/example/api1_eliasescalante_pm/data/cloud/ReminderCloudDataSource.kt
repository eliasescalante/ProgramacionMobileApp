package com.example.api1_eliasescalante_pm.data.cloud

/**
 * Contrato de la fuente remota. El repositorio depende de esta interfaz, no
 * de Firebase, lo que permite sustituirla por un doble en las pruebas.
 *
 * `uid` siempre proviene de la sesión autenticada (nunca de la interfaz) y se
 * usa para construir la ruta `users/{uid}/reminders`.
 */
interface ReminderCloudDataSource {

    suspend fun fetchAll(uid: String): CloudResult<List<CloudReminder>>

    /**
     * Crea o reemplaza el documento `users/{uid}/reminders/{reminder.id}`.
     * Es idempotente: repetir la llamada no genera copias.
     */
    suspend fun upsert(uid: String, reminder: CloudReminder): CloudResult<Unit>

    /** Elimina el documento. Si ya no existe, Firestore responde con éxito. */
    suspend fun delete(uid: String, reminderId: String): CloudResult<Unit>
}
