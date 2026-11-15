package com.example.api1_eliasescalante_pm.data.cloud

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Fuente remota basada en Cloud Firestore.
 *
 * Estructura:
 *   users/{uid}/reminders/{reminderId}
 *
 * El uid se recibe del repositorio (que lo toma de la sesión autenticada) y se
 * contrasta con `FirebaseAuth.currentUser`. El cliente nunca acepta un uid
 * proveniente de la interfaz.
 */
class FirestoreReminderCloudDataSource(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ReminderCloudDataSource {

    override suspend fun fetchAll(uid: String): CloudResult<List<CloudReminder>> {
        sessionProblem(uid)?.let { return it }

        return runCloud {
            val snapshot = userReminders(uid).get().await()
            snapshot.documents.mapNotNull { document ->
                document.data?.toCloudReminderOrNull(
                    documentId = document.id,
                    expectedOwnerUid = uid
                )
            }
        }
    }

    override suspend fun upsert(
        uid: String,
        reminder: CloudReminder
    ): CloudResult<Unit> {
        sessionProblem(uid)?.let { return it }
        if (reminder.ownerUid != uid) {
            return CloudResult.PermissionDenied(
                "El recordatorio no pertenece a la cuenta autenticada."
            )
        }

        return runCloud {
            userReminders(uid)
                .document(reminder.id)
                .set(reminder.toDocument())
                .await()
        }
    }

    override suspend fun delete(uid: String, reminderId: String): CloudResult<Unit> {
        sessionProblem(uid)?.let { return it }

        return runCloud {
            // Firestore considera exitoso el borrado de un documento ausente.
            userReminders(uid).document(reminderId).delete().await()
        }
    }

    private fun userReminders(uid: String) =
        firestore.collection(USERS_COLLECTION)
            .document(uid)
            .collection(REMINDERS_COLLECTION)

    private fun sessionProblem(uid: String): CloudResult<Nothing>? {
        val authenticatedUid = auth.currentUser?.uid
        return when {
            authenticatedUid == null -> CloudResult.Unauthenticated(
                "La sesión no está activa. Iniciá sesión para sincronizar."
            )

            authenticatedUid != uid -> CloudResult.PermissionDenied(
                "La sesión cambió durante la operación. Volvé a intentarlo."
            )

            else -> null
        }
    }

    private suspend fun <T> runCloud(block: suspend () -> T): CloudResult<T> = try {
        CloudResult.Success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        error.toCloudFailure()
    }

    companion object {
        const val USERS_COLLECTION = "users"
        const val REMINDERS_COLLECTION = "reminders"
    }
}

/**
 * Traduce errores del proveedor a resultados de la aplicación. La interfaz
 * nunca ve códigos de Firestore.
 */
internal fun Throwable.toCloudFailure(): CloudResult<Nothing> {
    if (this is FirebaseAuthInvalidUserException ||
        this is FirebaseAuthInvalidCredentialsException
    ) {
        return CloudResult.Unauthenticated(
            "Tu sesión expiró o dejó de ser válida. Iniciá sesión nuevamente."
        )
    }

    return when ((this as? FirebaseFirestoreException)?.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            CloudResult.PermissionDenied(
                "Firestore rechazó la operación: la cuenta no tiene permiso " +
                    "sobre estos datos."
            )

        FirebaseFirestoreException.Code.UNAUTHENTICATED ->
            CloudResult.Unauthenticated(
                "La sesión no es válida. Iniciá sesión nuevamente."
            )

        FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
        FirebaseFirestoreException.Code.CANCELLED,
        FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED,
        FirebaseFirestoreException.Code.ABORTED,
        FirebaseFirestoreException.Code.INTERNAL ->
            CloudResult.Unavailable(
                "No se pudo contactar a Firestore. Los datos locales se conservaron."
            )

        FirebaseFirestoreException.Code.NOT_FOUND ->
            CloudResult.Failure(
                "El documento ya no existe en la nube.",
                retryable = false
            )

        FirebaseFirestoreException.Code.INVALID_ARGUMENT,
        FirebaseFirestoreException.Code.FAILED_PRECONDITION ->
            CloudResult.Failure(
                "Firestore rechazó los datos enviados.",
                retryable = false
            )

        else -> CloudResult.Failure(
            "No se pudo completar la operación en la nube."
        )
    }
}
