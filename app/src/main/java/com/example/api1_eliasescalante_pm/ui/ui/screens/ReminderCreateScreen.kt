package com.example.api1_eliasescalante_pm.ui.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.api1_eliasescalante_pm.AppContainer
import com.example.api1_eliasescalante_pm.ui.ui.components.DueDateField
import com.example.api1_eliasescalante_pm.ui.ui.label
import com.example.api1_eliasescalante_pm.viewmodel.ReminderCreateViewModel
import com.example.api1_eliasescalante_pm.viewmodel.ReminderViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderCreateScreen(
    onGuardar: () -> Unit,
    onCancelar: () -> Unit
) {
    val viewModel: ReminderCreateViewModel = viewModel(
        factory = ReminderViewModelFactory(
            repository = AppContainer.reminderRepository,
            authRepository = AppContainer.authRepository
        )
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) onGuardar()
    }

    val formValid =
        uiState.title.trim().length in 4..80 &&
            uiState.description.length <= 200 &&
            uiState.selectedCategory != null &&
            uiState.selectedPriority != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nuevo recordatorio") },
                navigationIcon = {
                    TextButton(onClick = onCancelar) { Text("Volver") }
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
                text = "Crear recordatorio",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = "Se guarda primero en Room. Si no hay conexión, queda " +
                    "marcado como pendiente y se sincroniza al recuperar la red.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = uiState.title,
                onValueChange = viewModel::onTitleChange,
                label = { Text("Título") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = uiState.titleError != null,
                supportingText = { uiState.titleError?.let { Text(it) } }
            )

            OutlinedTextField(
                value = uiState.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text("Descripción") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
                isError = uiState.descriptionError != null,
                supportingText = {
                    if (uiState.descriptionError != null) {
                        Text(uiState.descriptionError!!)
                    } else {
                        Text("${uiState.description.length}/200 caracteres")
                    }
                }
            )

            Text("Categoría", style = MaterialTheme.typography.titleMedium)
            viewModel.categories.forEach { category ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = uiState.selectedCategory == category,
                        onClick = { viewModel.onCategoryChange(category) }
                    )
                    Text(
                        text = category.label(),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            uiState.categoryError?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Text("Prioridad", style = MaterialTheme.typography.titleMedium)
            viewModel.priorities.forEach { priority ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = uiState.selectedPriority == priority,
                        onClick = { viewModel.onPriorityChange(priority) }
                    )
                    Text(
                        text = priority.label(),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            uiState.priorityError?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            DueDateField(
                value = uiState.dueDate,
                onValueChange = viewModel::onDueDateChange,
                error = uiState.dueDateError,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = uiState.location,
                onValueChange = viewModel::onLocationChange,
                label = { Text("Ubicación o aviso (opcional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            uiState.generalError?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = viewModel::saveReminder,
                enabled = formValid && !uiState.isSaving && !uiState.saved,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Guardando…")
                } else {
                    Text("Guardar recordatorio")
                }
            }

            OutlinedButton(
                onClick = onCancelar,
                enabled = !uiState.isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancelar")
            }
        }
    }
}
