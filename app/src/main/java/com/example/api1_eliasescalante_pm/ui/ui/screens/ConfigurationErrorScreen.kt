package com.example.api1_eliasescalante_pm.ui.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Pantalla de diagnóstico para compilaciones sin google-services.json.
 * Preferimos explicar el problema antes que fallar con una excepción de
 * Firebase al iniciar.
 */
@Composable
fun ConfigurationErrorScreen(
    message: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Falta configurar Firebase",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "Pasos resumidos:\n" +
                "1. Crear el proyecto en console.firebase.google.com\n" +
                "2. Habilitar Authentication (correo y contraseña)\n" +
                "3. Crear Cloud Firestore y publicar las reglas del archivo " +
                "firestore.rules\n" +
                "4. Registrar la app Android con el package " +
                "com.example.api1_eliasescalante_pm\n" +
                "5. Descargar google-services.json en app/\n" +
                "6. Volver a compilar e instalar.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
