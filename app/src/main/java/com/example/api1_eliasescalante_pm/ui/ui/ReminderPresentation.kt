package com.example.api1_eliasescalante_pm.ui.ui

import androidx.compose.ui.graphics.Color
import com.example.api1_eliasescalante_pm.domain.model.PriorityFilter
import com.example.api1_eliasescalante_pm.domain.model.ReminderCategory
import com.example.api1_eliasescalante_pm.domain.model.ReminderPriority
import com.example.api1_eliasescalante_pm.domain.model.ReminderSortOrder
import com.example.api1_eliasescalante_pm.domain.model.ReminderStatus
import com.example.api1_eliasescalante_pm.domain.model.SyncState
import com.example.api1_eliasescalante_pm.domain.model.ThemeMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun ReminderCategory.label(): String = when (this) {
    ReminderCategory.WORK -> "Trabajo"
    ReminderCategory.PERSONAL -> "Personal"
    ReminderCategory.HEALTH -> "Salud"
    ReminderCategory.OTHER -> "Otros"
}

fun ReminderCategory.color(): Color = when (this) {
    ReminderCategory.WORK -> Color(0xFF1976D2)
    ReminderCategory.PERSONAL -> Color(0xFF8E24AA)
    ReminderCategory.HEALTH -> Color(0xFF43A047)
    ReminderCategory.OTHER -> Color(0xFF546E7A)
}

fun ReminderPriority.label(): String = when (this) {
    ReminderPriority.HIGH -> "Alta"
    ReminderPriority.MEDIUM -> "Media"
    ReminderPriority.LOW -> "Baja"
}

fun ReminderPriority.color(): Color = when (this) {
    ReminderPriority.HIGH -> Color(0xFFE53935)
    ReminderPriority.MEDIUM -> Color(0xFFFB8C00)
    ReminderPriority.LOW -> Color(0xFF4CAF50)
}

fun ReminderStatus.label(): String = when (this) {
    ReminderStatus.PENDING -> "Pendiente"
    ReminderStatus.IN_PROGRESS -> "En progreso"
    ReminderStatus.COMPLETED -> "Completado"
}

fun ReminderStatus.color(): Color = when (this) {
    ReminderStatus.PENDING -> Color(0xFFF9A825)
    ReminderStatus.IN_PROGRESS -> Color(0xFF1565C0)
    ReminderStatus.COMPLETED -> Color(0xFF2E7D32)
}

fun SyncState.label(): String = when (this) {
    SyncState.PENDING -> "Pendiente de envío"
    SyncState.SYNCING -> "Sincronizando"
    SyncState.SYNCED -> "Sincronizado"
    SyncState.ERROR -> "Error de sincronización"
}

fun SyncState.color(): Color = when (this) {
    SyncState.PENDING -> Color(0xFFF9A825)
    SyncState.SYNCING -> Color(0xFF1565C0)
    SyncState.SYNCED -> Color(0xFF2E7D32)
    SyncState.ERROR -> Color(0xFFC62828)
}

fun ReminderSortOrder.label(): String = when (this) {
    ReminderSortOrder.DATE_DESC -> "Fecha: más reciente primero"
    ReminderSortOrder.DATE_ASC -> "Fecha: más antigua primero"
    ReminderSortOrder.PRIORITY_DESC -> "Prioridad: alta primero"
    ReminderSortOrder.TITLE_ASC -> "Título: A-Z"
}

fun PriorityFilter.label(): String = when (this) {
    PriorityFilter.ALL -> "Todas las prioridades"
    PriorityFilter.HIGH -> "Solo prioridad alta"
    PriorityFilter.MEDIUM -> "Solo prioridad media"
    PriorityFilter.LOW -> "Solo prioridad baja"
}

fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "Seguir al sistema"
    ThemeMode.LIGHT -> "Tema claro"
    ThemeMode.DARK -> "Tema oscuro"
}

fun formatDate(millis: Long?): String {
    if (millis == null) return "Sin fecha"
    val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    return formatter.format(Date(millis))
}

fun formatShortDate(millis: Long): String {
    val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(millis))
}
