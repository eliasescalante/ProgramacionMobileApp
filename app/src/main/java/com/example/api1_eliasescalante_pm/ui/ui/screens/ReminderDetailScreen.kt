package com.example.api1_eliasescalante_pm.ui.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.api1_eliasescalante_pm.AppContainer
import com.example.api1_eliasescalante_pm.domain.model.ReminderStatus
import com.example.api1_eliasescalante_pm.ui.ui.color
import com.example.api1_eliasescalante_pm.ui.ui.components.DueDateField
import com.example.api1_eliasescalante_pm.ui.ui.formatDate
import com.example.api1_eliasescalante_pm.ui.ui.label
import com.example.api1_eliasescalante_pm.ui.ui.state.ReminderDetailUiState
import com.example.api1_eliasescalante_pm.viewmodel.ReminderDetailViewModel
import com.example.api1_eliasescalante_pm.viewmodel.ReminderViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderDetailScreen(
    reminderId: String,
    onVolver: () -> Unit
) {
    val viewModel: ReminderDetailViewModel = viewModel(
        key = "detail-$reminderId",
        factory = ReminderViewModelFactory(
            repository = AppContainer.reminderRepository,
            authRepository = AppContainer.authRepository,
            reminderId = reminderId
        )
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.deleted) {
        if (uiState.deleted) onVolver()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalle del recordatorio") },
                navigationIcon = {
                    TextButton(onClick = onVolver) { Text("Volver") }
                }
            )
        }
    ) { padding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            when {
                uiState.isLoading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )

                uiState.error != null -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Text(uiState.error!!, color = MaterialTheme.colorScheme.error)
                    Button(onClick = onVolver) { Text("Volver al listado") }
                }

                uiState.reminder != null && uiState.isEditing -> {
                    EditReminderContent(
                        state = uiState,
                        onTitleChange = viewModel::onEditTitleChange,
                        onDescriptionChange = viewModel::onEditDescriptionChange,
                        onCategoryChange = viewModel::onEditCategoryChange,
                        onPriorityChange = viewModel::onEditPriorityChange,
                        onDueDateChange = viewModel::onEditDueDateChange,
                        onLocationChange = viewModel::onEditLocationChange,
                        onSave = viewModel::saveChanges,
                        onCancel = viewModel::cancelEditing
                    )
                }

                uiState.reminder != null -> {
                    val reminder = uiState.reminder!!
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = reminder.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Badge(
                                text = reminder.priority.label(),
                                color = reminder.priority.color()
                            )
                            Badge(
                                text = reminder.syncState.label(),
                                color = reminder.syncState.color()
                            )
                        }

                        if (reminder.syncError != null) {
                            Text(
                                text = "Último error de sincronización: ${reminder.syncError}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        HorizontalDivider()

                        DetailRow("Descripción", reminder.description ?: "Sin descripción")
                        DetailRow("Categoría", reminder.category.label())
                        DetailRow("Prioridad", reminder.priority.label())
                        DetailRow("Estado", reminder.status.label())
                        DetailRow("Fecha", formatDate(reminder.dueDate))
                        DetailRow(
                            "Ubicación / aviso",
                            reminder.locationOrNotice ?: "Sin ubicación ni aviso"
                        )
                        DetailRow("Creado", formatDate(reminder.createdAt))
                        DetailRow("Última modificación", formatDate(reminder.updatedAt))
                        // El mismo id identifica la fila de Room y el documento
                        // users/{uid}/reminders/{id} de Firestore.
                        DetailRow("Identificador (Room y Firestore)", reminder.id)
                        DetailRow("Cuenta propietaria", reminder.ownerUid)

                        Text(
                            text = "Cambiar estado",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ReminderStatus.entries.forEach { status ->
                                FilterChip(
                                    selected = reminder.status == status,
                                    onClick = { viewModel.setStatus(status) },
                                    label = { Text(status.label()) }
                                )
                            }
                        }

                        uiState.operationError?.let { error ->
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = viewModel::startEditing,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Editar")
                            }
                            OutlinedButton(
                                onClick = { showDeleteDialog = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Eliminar")
                            }
                        }

                        Text(
                            text = "Los cambios se guardan primero en Room y se " +
                                "sincronizan automáticamente.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar recordatorio") },
            text = {
                Text(
                    "Se ocultará de inmediato. Si no hay conexión, el borrado " +
                        "se completará en el servidor cuando vuelva la red."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteReminder()
                    }
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun EditReminderContent(
    state: ReminderDetailUiState,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onCategoryChange: (com.example.api1_eliasescalante_pm.domain.model.ReminderCategory) -> Unit,
    onPriorityChange: (com.example.api1_eliasescalante_pm.domain.model.ReminderPriority) -> Unit,
    onDueDateChange: (Long?) -> Unit,
    onLocationChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Editar recordatorio", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = state.editTitle,
            onValueChange = onTitleChange,
            label = { Text("Título") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = state.editTitleError != null,
            supportingText = { state.editTitleError?.let { Text(it) } }
        )

        OutlinedTextField(
            value = state.editDescription,
            onValueChange = onDescriptionChange,
            label = { Text("Descripción") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 5,
            isError = state.editDescriptionError != null,
            supportingText = {
                if (state.editDescriptionError != null) {
                    Text(state.editDescriptionError!!)
                } else {
                    Text("${state.editDescription.length}/200 caracteres")
                }
            }
        )

        Text("Categoría", style = MaterialTheme.typography.titleMedium)
        com.example.api1_eliasescalante_pm.domain.model.ReminderCategory.entries
            .forEach { category ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = state.editCategory == category,
                        onClick = { onCategoryChange(category) }
                    )
                    Text(category.label(), modifier = Modifier.padding(start = 8.dp))
                }
            }

        Text("Prioridad", style = MaterialTheme.typography.titleMedium)
        com.example.api1_eliasescalante_pm.domain.model.ReminderPriority.entries
            .forEach { priority ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = state.editPriority == priority,
                        onClick = { onPriorityChange(priority) }
                    )
                    Text(priority.label(), modifier = Modifier.padding(start = 8.dp))
                }
            }

        DueDateField(
            value = state.editDueDate,
            onValueChange = onDueDateChange,
            enabled = !state.isSaving,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = state.editLocation,
            onValueChange = onLocationChange,
            label = { Text("Ubicación o aviso (opcional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        state.operationError?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onSave,
                enabled = !state.isSaving,
                modifier = Modifier.weight(1f)
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Guardando…")
                } else {
                    Text("Guardar cambios")
                }
            }
            OutlinedButton(
                onClick = onCancel,
                enabled = !state.isSaving,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancelar")
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Surface(
        color = color,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
