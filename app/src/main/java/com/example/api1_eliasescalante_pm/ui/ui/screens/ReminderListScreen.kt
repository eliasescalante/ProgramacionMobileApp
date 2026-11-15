package com.example.api1_eliasescalante_pm.ui.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.api1_eliasescalante_pm.AppContainer
import com.example.api1_eliasescalante_pm.domain.model.Reminder
import com.example.api1_eliasescalante_pm.domain.model.UserPreferences
import com.example.api1_eliasescalante_pm.ui.ui.components.ReminderCard
import com.example.api1_eliasescalante_pm.ui.ui.components.SyncStatusBanner
import com.example.api1_eliasescalante_pm.ui.ui.label
import com.example.api1_eliasescalante_pm.ui.ui.state.ReminderListUiState
import com.example.api1_eliasescalante_pm.viewmodel.ReminderListViewModel
import com.example.api1_eliasescalante_pm.viewmodel.ReminderViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderListScreen(
    onCrear: () -> Unit,
    onReminderClick: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val viewModel: ReminderListViewModel = viewModel(
        factory = ReminderViewModelFactory(
            repository = AppContainer.reminderRepository,
            authRepository = AppContainer.authRepository
        )
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()

    val pendingCount = when (val state = uiState) {
        is ReminderListUiState.Success -> state.pendingCount
        is ReminderListUiState.Empty -> state.pendingCount
        else -> 0
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onCrear) {
                Text(text = "+", fontSize = 28.sp)
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Recordatorios",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    TextButton(onClick = { viewModel.syncNow() }) {
                        Text("Sync")
                    }
                    TextButton(onClick = onOpenSettings) {
                        Text("Ajustes")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            SyncStatusBanner(
                syncStatus = syncStatus,
                pendingCount = pendingCount,
                onSync = viewModel::syncNow,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            PreferencesSummary(preferences)

            when (val state = uiState) {
                ReminderListUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is ReminderListUiState.Empty -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "No hay recordatorios para mostrar",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Creá uno con el botón +, o cambiá los filtros en Ajustes.",
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        if (state.pendingCount > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${state.pendingCount} cambio(s) esperando sincronización.",
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }

                is ReminderListUiState.Success -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = state.reminders,
                            key = { it.id }
                        ) { reminder ->
                            ReminderCard(
                                reminder = reminder,
                                onClick = { onReminderClick(reminder.id) },
                                onToggleCompleted = {
                                    viewModel.toggleCompleted(reminder)
                                }
                            )
                        }
                    }
                }

                is ReminderListUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "No se pudieron cargar los recordatorios",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = state.message)
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(onClick = viewModel::syncNow) {
                            Text("Reintentar")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreferencesSummary(preferences: UserPreferences) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = preferences.sortOrder.label(),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = preferences.priorityFilter.label(),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                text = if (preferences.showCompleted) {
                    "Mostrando completados"
                } else {
                    "Ocultando completados"
                },
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
