package com.example.api1_eliasescalante_pm

import androidx.compose.ui.graphics.Color

// Sealed class para Representar la Prioridad con etiqueta y color
sealed class Priority(val label: String, val color: Color) {
    object High : Priority("Alta", Color(0xFFE53935))     // Rojo
    object Medium : Priority("Media", Color(0xFFFB8C00))  // Naranja
    object Low : Priority("Baja", Color(0xFF4CAF50))      // Verde
}

// Sealed class para el Estado del Recordatorio
sealed class ReminderStatus(val label: String) {
    object Pending : ReminderStatus("Pendiente")
    object InProgress : ReminderStatus("En Progreso")
    object Completed : ReminderStatus("Completado")
}

// Data class para la Categoría
data class Category(
    val id: String,
    val name: String,
    val colorHex: Color
)

// Data class inmutable principal para el Recordatorio
data class Reminder(
    val id: String,
    val title: String,
    val description: String?, // Atributo opcional
    val category: Category,
    val priority: Priority,
    val status: ReminderStatus,
    val locationOrNotice: String? = null // Atributo opcional
)