package com.example.api1_eliasescalante_pm.data.auth

import com.example.api1_eliasescalante_pm.domain.model.AuthUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

/**
 * Contrato con el proveedor de identidad. El repositorio y la presentación no
 * dependen de Firebase: solo de esta interfaz.
 */
interface AuthDataSource {

    /** Emite el usuario autenticado o null cuando no hay sesión. */
    val authState: Flow<AuthUser?>

    fun currentUser(): AuthUser?

    suspend fun signIn(email: String, password: String): AuthUser

    suspend fun signUp(email: String, password: String): AuthUser

    suspend fun signOut()

    /** Fuerza una recarga del token para detectar sesiones revocadas. */
    suspend fun reloadSession()
}

/**
 * Implementación con Firebase Authentication.
 *
 * No se guardan contraseñas ni tokens en DataStore: Firebase administra la
 * sesión y persiste el token cifrado en el almacenamiento privado de la app.
 * La aplicación solo conserva el uid para asociar los datos.
 */
class FirebaseAuthDataSource(
    private val auth: FirebaseAuth
) : AuthDataSource {

    override val authState: Flow<AuthUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser?.toAuthUser())
        }
        auth.addAuthStateListener(listener)
        // Emisión inmediata: FirebaseAuth ya conoce la sesión persistida.
        trySend(auth.currentUser?.toAuthUser())
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override fun currentUser(): AuthUser? = auth.currentUser?.toAuthUser()

    override suspend fun signIn(email: String, password: String): AuthUser {
        val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
        return requireNotNull(result.user?.toAuthUser()) {
            "Firebase no devolvió un usuario autenticado."
        }
    }

    override suspend fun signUp(email: String, password: String): AuthUser {
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        return requireNotNull(result.user?.toAuthUser()) {
            "Firebase no devolvió un usuario autenticado."
        }
    }

    override suspend fun signOut() {
        auth.signOut()
    }

    override suspend fun reloadSession() {
        auth.currentUser?.reload()?.await()
    }
}

private fun FirebaseUser.toAuthUser(): AuthUser = AuthUser(
    uid = uid,
    email = email,
    isEmailVerified = isEmailVerified
)
