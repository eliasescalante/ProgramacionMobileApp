package com.example.api1_eliasescalante_pm.ui.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.api1_eliasescalante_pm.AppContainer
import com.example.api1_eliasescalante_pm.BuildConfig
import com.example.api1_eliasescalante_pm.domain.model.PriorityFilter
import com.example.api1_eliasescalante_pm.domain.model.ReminderSortOrder
import com.example.api1_eliasescalante_pm.domain.model.ThemeMode
import com.example.api1_eliasescalante_pm.ui.ui.label
import com.example.api1_eliasescalante_pm.viewmodel.ReminderSettingsViewModel
import com.example.api1_eliasescalante_pm.viewmodel.ReminderViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderSettingsScreen(
    onVolver: () -> Unit
) {
    val viewModel: ReminderSettingsViewModel = viewModel(
        factory = ReminderViewModelFactory(AppContainer.reminderRepository)
    )
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Preferencias y datos") },
                navigationIcon = {
                    TextButton(onClick = onVolver) { Text("Volver") }
                }
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Estas opciones se guardan en DataStore y se conservan " +
                    "aunque cierres la aplicación por completo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            message?.let { text ->
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = text,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(onClick = viewModel::consumeMessage) {
                            Text("OK")
                        }
                    }
                }
            }

            SectionTitle("Apariencia")
            Text("Tema", style = MaterialTheme.typography.titleSmall)
            ThemeMode.entries.forEach { mode ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = preferences.themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) }
                    )
                    Text(mode.label(), modifier = Modifier.padding(start = 8.dp))
                }
            }

            HorizontalDivider()

            SectionTitle("Listado")
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Mostrar recordatorios completados",
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Si está desactivado, los completados se ocultan del listado.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = preferences.showCompleted,
                    onCheckedChange = viewModel::setShowCompleted
                )
            }

            Text("Criterio de ordenamiento", style = MaterialTheme.typography.titleSmall)
            ReminderSortOrder.entries.forEach { order ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = preferences.sortOrder == order,
                        onClick = { viewModel.setSortOrder(order) }
                    )
                    Text(order.label(), modifier = Modifier.padding(start = 8.dp))
                }
            }

            Text("Filtro de prioridad", style = MaterialTheme.typography.titleSmall)
            PriorityFilter.entries.forEach { filter ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = preferences.priorityFilter == filter,
                        onClick = { viewModel.setPriorityFilter(filter) }
                    )
                    Text(filter.label(), modifier = Modifier.padding(start = 8.dp))
                }
            }

            HorizontalDivider()

            SectionTitle("Servicio remoto")
            Text(
                text = "URL base configurada:\n${BuildConfig.REMOTE_BASE_URL}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Se puede cambiar con -PREMOTE_BASE_URL=... al compilar " +
                    "o desde gradle.properties. Ver remote.properties.example.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = viewModel::syncNow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sincronizar ahora")
            }

            HorizontalDivider()

            SectionTitle("Datos locales")
            Text(
                text = "Los recordatorios están en Room (reminders.db). Las " +
                    "preferencias están en DataStore (user_preferences).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedButton(
                onClick = viewModel::resetPreferences,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Restablecer preferencias")
            }

            OutlinedButton(
                onClick = { showClearDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Borrar todos los recordatorios locales")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Prueba sugerida: cambiá el tema o el orden, cerrá la " +
                    "aplicación desde Recientes y volvé a abrirla. La " +
                    "configuración debe mantenerse.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Borrar recordatorios") },
            text = {
                Text(
                    "Se eliminarán todos los registros de Room. Esta acción no " +
                        "borra las preferencias de DataStore."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        viewModel.clearReminders()
                    }
                ) { Text("Borrar") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
}
