package com.example.api1_eliasescalante_pm.ui.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.api1_eliasescalante_pm.domain.model.Reminder
import com.example.api1_eliasescalante_pm.domain.model.ReminderStatus
import com.example.api1_eliasescalante_pm.domain.model.SyncStatus
import com.example.api1_eliasescalante_pm.ui.ui.color
import com.example.api1_eliasescalante_pm.ui.ui.formatDate
import com.example.api1_eliasescalante_pm.ui.ui.formatShortDate
import com.example.api1_eliasescalante_pm.ui.ui.label

@Composable
fun SyncStatusBanner(
    syncStatus: SyncStatus,
    pendingCount: Int,
    onSync: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (syncStatus is SyncStatus.Idle && pendingCount == 0) return

    val containerColor = when (syncStatus) {
        is SyncStatus.Error -> MaterialTheme.colorScheme.errorContainer
        is SyncStatus.Offline -> MaterialTheme.colorScheme.tertiaryContainer
        is SyncStatus.Success -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = containerColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            when (syncStatus) {
                is SyncStatus.Error -> {
                    Text(
                        text = "No se pudo sincronizar",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = syncStatus.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    TextButton(onClick = onSync) { Text("Reintentar") }
                }

                is SyncStatus.Offline -> {
                    Text(
                        text = "Modo offline",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = syncStatus.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    TextButton(onClick = onSync) { Text("Reintentar ahora") }
                }

                is SyncStatus.Success -> {
                    Text(
                        text = "Sincronización completada",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "${syncStatus.pushed} enviados · ${syncStatus.pulled} recibidos · " +
                            formatShortDate(syncStatus.at),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                SyncStatus.Syncing -> {
                    Text("Sincronizando con el servicio remoto…")
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                SyncStatus.Idle -> Unit
            }

            if (pendingCount > 0 && syncStatus !is SyncStatus.Syncing) {
                Text(
                    text = "$pendingCount cambio(s) pendiente(s) de enviar",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = onSync) { Text("Sincronizar ahora") }
            }
        }
    }
}

@Composable
fun ReminderCard(
    reminder: Reminder,
    onClick: () -> Unit,
    onToggleCompleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = reminder.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textDecoration = if (reminder.status == ReminderStatus.COMPLETED) {
                        TextDecoration.LineThrough
                    } else {
                        TextDecoration.None
                    },
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(reminder.priority.color())
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = reminder.priority.label(),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            reminder.description?.let { description ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = description,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = reminder.category.label(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = reminder.category.color()
                )
                Text(
                    text = reminder.status.label(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = reminder.status.color()
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Fecha: ${formatDate(reminder.dueDate)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )
                Text(
                    text = reminder.syncState.label(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = reminder.syncState.color()
                )
            }

            reminder.locationOrNotice?.let { location ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "📍 $location",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            if (reminder.syncState == com.example.api1_eliasescalante_pm.domain.model.SyncState.ERROR) {
                reminder.syncError?.let { error ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = error,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            TextButton(
                onClick = onToggleCompleted,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(
                    if (reminder.status == ReminderStatus.COMPLETED) {
                        "Marcar como pendiente"
                    } else {
                        "Marcar como completado"
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DueDateField(
    value: Long?,
    onValueChange: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    error: String? = null
) {
    var showDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        OutlinedTextField(
            value = formatDate(value),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text("Fecha") },
            modifier = Modifier.fillMaxWidth(),
            isError = error != null,
            supportingText = {
                if (error != null) {
                    Text(error)
                } else {
                    Text("Opcional. Se usa para ordenar el listado.")
                }
            },
            trailingIcon = {
                TextButton(
                    onClick = { showDialog = true },
                    enabled = enabled
                ) { Text("Elegir") }
            }
        )

        if (value != null) {
            TextButton(
                onClick = { onValueChange(null) },
                enabled = enabled
            ) {
                Text("Quitar fecha")
            }
        }
    }

    if (showDialog) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = value ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onValueChange(pickerState.selectedDateMillis)
                        showDialog = false
                    }
                ) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}
