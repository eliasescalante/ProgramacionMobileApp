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
        factory = ReminderViewModelFactory(
            repository = AppContainer.reminderRepository,
            authRepository = AppContainer.authRepository
        )
    )
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()
    val pendingCount by viewModel.pendingCount.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    var showClearDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cuenta y preferencias") },
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

            // -----------------------------------------------------------------
            // Sesión
            // -----------------------------------------------------------------
            SectionTitle("Cuenta")

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = account?.email ?: "Cuenta sin correo",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Identificador de la cuenta: " +
                            (account?.shortUid ?: "no disponible"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = when {
                            pendingCount == 0 ->
                                "Todos los cambios están sincronizados."

                            pendingCount == 1 ->
                                "1 cambio esperando sincronización."

                            else ->
                                "$pendingCount cambios esperando sincronización."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (pendingCount == 0) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.tertiary
                        }
                    )
                }
            }

            Text(
                text = "La sesión la administra Firebase Authentication. La " +
                    "aplicación solo conserva el identificador para separar los " +
                    "datos de cada cuenta: no guarda contraseñas ni tokens " +
                    "propios en DataStore.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = viewModel::syncNow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sincronizar ahora")
            }

            OutlinedButton(
                onClick = { showLogoutDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cerrar sesión")
            }

            HorizontalDivider()

            // -----------------------------------------------------------------
            // Preferencias
            // -----------------------------------------------------------------
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

            // -----------------------------------------------------------------
            // Datos locales
            // -----------------------------------------------------------------
            SectionTitle("Datos en este dispositivo")
            Text(
                text = "Room (reminders.db) guarda únicamente los " +
                    "recordatorios de la cuenta con sesión activa. Firestore " +
                    "conserva la copia en la nube. DataStore (user_preferences) " +
                    "guarda el tema, el orden y los filtros; no contiene datos " +
                    "de negocio.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Al cerrar sesión se borra de Room la copia local de la " +
                    "cuenta. Las preferencias de apariencia se conservan porque " +
                    "no pertenecen a ninguna cuenta.",
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
                Text("Borrar la copia local de mis recordatorios")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Los botones y secciones solo muestran lo que la sesión " +
                    "permite; la autorización real la aplican el filtro por uid " +
                    "en Room y las reglas de Firestore.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Cerrar sesión") },
            text = {
                Text(
                    buildString {
                        append(
                            "Se borrará de este dispositivo la copia local de " +
                                "tus recordatorios para que otra cuenta no pueda " +
                                "verla."
                        )
                        if (pendingCount > 0) {
                            append(
                                "\n\nHay $pendingCount cambio(s) sin " +
                                    "sincronizar. Se intentará enviarlos antes de " +
                                    "cerrar sesión; si no hay red, se perderán " +
                                    "porque la copia local se elimina."
                            )
                        }
                        append(
                            "\n\nTus datos seguirán en Firestore y volverán a " +
                                "descargarse al iniciar sesión con la misma cuenta."
                        )
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.logout()
                    }
                ) { Text("Cerrar sesión") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Borrar copia local") },
            text = {
                Text(
                    "Se eliminarán los registros de Room de esta cuenta. " +
                        "Firestore no se modifica: al sincronizar se volverán a " +
                        "descargar. Las preferencias de DataStore no se tocan."
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
