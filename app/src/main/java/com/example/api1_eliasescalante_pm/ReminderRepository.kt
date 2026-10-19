package com.example.api1_eliasescalante_pm

import androidx.compose.ui.graphics.Color

object ReminderRepository {

    // Categorías iniciales
    val workCategory = Category("1", "Trabajo", Color(0xFF1976D2))
    val personalCategory = Category("2", "Personal", Color(0xFF8E24AA))
    val healthCategory = Category("3", "Salud", Color(0xFF43A047))

    // Set para evitar categorías repetidas (Uso de Set)
    val categoriesSet: Set<Category> = setOf(workCategory, personalCategory, healthCategory)

    // Map para asociar cada categoría con su color (Uso de Map)
    val categoryColorMap: Map<Category, Color> = categoriesSet.associateWith { it.colorHex }

    // Lista inmutable de recordatorios simulados (Uso de List)
    val sampleReminders: List<Reminder> = listOf(
        Reminder(
            id = "r1",
            title = "Reunión de planificación trimestral",
            description = "Revisar objetivos del módulo y avance del equipo.",
            category = workCategory,
            priority = Priority.High,
            status = ReminderStatus.Pending,
            locationOrNotice = "Oficina Central / Zoom"
        ),
        Reminder(
            id = "r2",
            title = "Comprar insumos médicos",
            description = "Botiquín de primeros auxilios para la sede.",
            category = healthCategory,
            priority = Priority.Medium,
            status = ReminderStatus.InProgress
        ),
        Reminder(
            id = "r3",
            title = "Mantenimiento preventivo de servidor",
            description = null,
            category = workCategory,
            priority = Priority.Low,
            status = ReminderStatus.Completed,
            locationOrNotice = "Data Center"
        ),
        Reminder(
            id = "r4",
            title = "Renovar credenciales de acceso",
            description = "Actualizar firmas digitales de la institución.",
            category = personalCategory,
            priority = Priority.High,
            status = ReminderStatus.Pending
        )
    )

    // Ejemplo de filtrado de recordatorios pendientes
    fun getPendingReminders(): List<Reminder> {
        return sampleReminders.filter { it.status is ReminderStatus.Pending }
    }
}