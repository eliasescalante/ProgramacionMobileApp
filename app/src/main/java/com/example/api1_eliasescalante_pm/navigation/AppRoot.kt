package com.example.api1_eliasescalante_pm.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.api1_eliasescalante_pm.AppContainer
import com.example.api1_eliasescalante_pm.domain.model.AuthState
import com.example.api1_eliasescalante_pm.ui.ui.screens.AuthScreen

/**
 * Raíz de navegación condicionada por la sesión.
 *
 * La lista de recordatorios nunca se compone si no hay un [AuthState.SignedIn].
 * Esto evita el problema reportado en las pruebas: dos personas en el mismo
 * dispositivo viendo información de la sesión anterior. Aun si la UI fallara,
 * el repositorio filtra por uid y Firestore aplica reglas del lado servidor.
 */
@Composable
fun AppRoot() {
    val authState by AppContainer.authRepository.authState.collectAsStateWithLifecycle()

    when (val state = authState) {
        AuthState.Restoring -> RestoringSessionScreen()

        is AuthState.SignedIn -> ReminderNavHost()

        AuthState.SignedOut -> AuthScreen(sessionMessage = null)

        is AuthState.SessionExpired -> AuthScreen(sessionMessage = state.reason)
    }
}

@Composable
private fun RestoringSessionScreen() {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(36.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Restaurando sesión…",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}
