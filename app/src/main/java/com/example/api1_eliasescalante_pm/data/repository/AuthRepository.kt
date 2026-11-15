package com.example.api1_eliasescalante_pm.data.repository

import com.example.api1_eliasescalante_pm.data.auth.AuthDataSource
import com.example.api1_eliasescalante_pm.data.auth.isSessionInvalid
import com.example.api1_eliasescalante_pm.data.auth.toAuthMessage
import com.example.api1_eliasescalante_pm.domain.model.AuthException
import com.example.api1_eliasescalante_pm.domain.model.AuthState
import com.example.api1_eliasescalante_pm.domain.model.AuthUser
import com.example.api1_eliasescalante_pm.domain.model.AuthValidation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Contrato de autenticación que consume la presentación.
 *
 * Responsabilidades:
 *  - Exponer el estado de sesión (restaurando, sin sesión, autenticado, expirado).
 *  - Validar credenciales y delegar el registro/inicio de sesión al proveedor.
 *  - Cerrar sesión y notificar la invalidación.
 *
 * No administra recordatorios ni conoce Room o Firestore.
 */
interface AuthRepository : SessionProvider {

    /** Aviso puntual (por ejemplo, sesión expirada) para mostrar una sola vez. */
    val sessionNotice: StateFlow<String?>

    /** Registra los listeners de sesión y verifica la sesión persistida. */
    fun start()

    suspend fun login(email: String, password: String): Result<AuthUser>

    suspend fun register(email: String, password: String): Result<AuthUser>

    suspend fun logout(): Result<Unit>

    fun consumeNotice()
}

/**
 * Implementación que coordina el proveedor de identidad con el estado de la
 * aplicación. El uid solo se publica dentro de [AuthState.SignedIn]; de esa
 * forma el resto de la app no puede "olvidarse" de pedir una sesión.
 */
class DefaultAuthRepository(
    private val dataSource: AuthDataSource,
    private val appScope: CoroutineScope
) : AuthRepository {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Restoring)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _sessionNotice = MutableStateFlow<String?>(null)
    override val sessionNotice: StateFlow<String?> = _sessionNotice.asStateFlow()

    private val started = AtomicBoolean(false)

    override fun currentUid(): String? =
        (_authState.value as? AuthState.SignedIn)?.user?.uid

    override fun start() {
        if (!started.compareAndSet(false, true)) return

        // 1) Firebase notifica inmediatamente la sesión persistida (si existe)
        //    y cualquier cambio posterior (login, logout, revocación).
        appScope.launch {
            dataSource.authState.collect { user -> applyUser(user) }
        }

        // 2) Revalidación explícita: una sesión guardada puede haber sido
        //    revocada o deshabilitada mientras la app estaba cerrada.
        appScope.launch { verifyRestoredSession() }
    }

    private fun applyUser(user: AuthUser?) {
        if (user != null) {
            _authState.value = AuthState.SignedIn(user)
            _sessionNotice.value = null
        } else if (_authState.value !is AuthState.SessionExpired) {
            _authState.value = AuthState.SignedOut
        }
    }

    private suspend fun verifyRestoredSession() {
        if (dataSource.currentUser() == null) return

        try {
            dataSource.reloadSession()
            dataSource.currentUser()?.let(::applyUser)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            if (error.isSessionInvalid()) {
                safelySignOut()
                invalidateSession(error.toAuthMessage())
            }
            // Un error de red no invalida la sesión: la aplicación sigue
            // operando con la copia local y reintenta al recuperar la red.
        }
    }

    override suspend fun login(email: String, password: String): Result<AuthUser> =
        authenticate(email, password, isRegistration = false)

    override suspend fun register(email: String, password: String): Result<AuthUser> =
        authenticate(email, password, isRegistration = true)

    private suspend fun authenticate(
        email: String,
        password: String,
        isRegistration: Boolean
    ): Result<AuthUser> {
        val validationError = AuthValidation.emailError(email)
            ?: AuthValidation.passwordError(password)
        if (validationError != null) {
            return Result.failure(AuthException(validationError))
        }

        return try {
            val user = if (isRegistration) {
                dataSource.signUp(email.trim(), password)
            } else {
                dataSource.signIn(email.trim(), password)
            }
            applyUser(user)
            Result.success(user)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Result.failure(AuthException(error.toAuthMessage()))
        }
    }

    override suspend fun logout(): Result<Unit> = try {
        safelySignOut()
        _authState.value = AuthState.SignedOut
        _sessionNotice.value = null
        Result.success(Unit)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        Result.failure(AuthException(error.toAuthMessage()))
    }

    override suspend fun invalidateSession(reason: String) {
        safelySignOut()
        _authState.value = AuthState.SessionExpired(reason)
        _sessionNotice.value = reason
    }

    override fun consumeNotice() {
        _sessionNotice.value = null
    }

    private suspend fun safelySignOut() {
        try {
            dataSource.signOut()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // El estado local se limpia igual: nunca dejamos una sesión a medias.
        }
    }
}
