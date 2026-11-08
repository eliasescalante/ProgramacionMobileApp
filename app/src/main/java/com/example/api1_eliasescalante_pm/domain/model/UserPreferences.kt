package com.example.api1_eliasescalante_pm.domain.model

enum class ThemeMode(val storageValue: String) {
    SYSTEM("SYSTEM"),
    LIGHT("LIGHT"),
    DARK("DARK");

    companion object {
        fun fromStorage(value: String?): ThemeMode =
            entries.firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}

enum class ReminderSortOrder(val storageValue: String) {
    DATE_DESC("DATE_DESC"),
    DATE_ASC("DATE_ASC"),
    PRIORITY_DESC("PRIORITY_DESC"),
    TITLE_ASC("TITLE_ASC");

    companion object {
        fun fromStorage(value: String?): ReminderSortOrder =
            entries.firstOrNull { it.storageValue == value } ?: DATE_DESC
    }
}

enum class PriorityFilter(val storageValue: String) {
    ALL("ALL"),
    HIGH("HIGH"),
    MEDIUM("MEDIUM"),
    LOW("LOW");

    companion object {
        fun fromStorage(value: String?): PriorityFilter =
            entries.firstOrNull { it.storageValue == value } ?: ALL
    }
}

/**
 * Preferencias simples de la aplicación. Viven en DataStore, nunca en Room.
 * Todos los campos tienen un valor predeterminado para tolerar primeras
 * ejecuciones, valores desconocidos o errores de lectura recuperables.
 */
data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val showCompleted: Boolean = true,
    val sortOrder: ReminderSortOrder = ReminderSortOrder.DATE_DESC,
    val priorityFilter: PriorityFilter = PriorityFilter.ALL
)
