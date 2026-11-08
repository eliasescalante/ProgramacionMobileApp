package com.example.api1_eliasescalante_pm.data.preferences

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.api1_eliasescalante_pm.domain.model.PriorityFilter
import com.example.api1_eliasescalante_pm.domain.model.ReminderSortOrder
import com.example.api1_eliasescalante_pm.domain.model.ThemeMode
import com.example.api1_eliasescalante_pm.domain.model.UserPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class UserPreferencesMappingTest {

    @Test
    fun `empty preferences produce documented defaults`() {
        val result = preferencesOf().toUserPreferences()

        assertEquals(UserPreferences(), result)
        assertEquals(ThemeMode.SYSTEM, result.themeMode)
        assertEquals(true, result.showCompleted)
        assertEquals(ReminderSortOrder.DATE_DESC, result.sortOrder)
        assertEquals(PriorityFilter.ALL, result.priorityFilter)
    }

    @Test
    fun `stored values are mapped to domain enums`() {
        val stored = preferencesOf(
            stringPreferencesKey("theme_mode") to "DARK",
            booleanPreferencesKey("show_completed") to false,
            stringPreferencesKey("sort_order") to "PRIORITY_DESC",
            stringPreferencesKey("priority_filter") to "HIGH"
        )

        val result = stored.toUserPreferences()

        assertEquals(ThemeMode.DARK, result.themeMode)
        assertEquals(false, result.showCompleted)
        assertEquals(ReminderSortOrder.PRIORITY_DESC, result.sortOrder)
        assertEquals(PriorityFilter.HIGH, result.priorityFilter)
    }

    @Test
    fun `invalid stored enum values fall back to defaults`() {
        val stored = preferencesOf(
            stringPreferencesKey("theme_mode") to "NEON",
            stringPreferencesKey("sort_order") to "RANDOM",
            stringPreferencesKey("priority_filter") to "URGENT"
        )

        val result = stored.toUserPreferences()

        assertEquals(ThemeMode.SYSTEM, result.themeMode)
        assertEquals(ReminderSortOrder.DATE_DESC, result.sortOrder)
        assertEquals(PriorityFilter.ALL, result.priorityFilter)
    }

    @Test
    fun `unknown keys do not affect the result`() {
        val stored = preferencesOf(
            stringPreferencesKey("otra_clave") to "valor",
            stringPreferencesKey("theme_mode") to "LIGHT"
        )

        val result = stored.toUserPreferences()

        assertEquals(ThemeMode.LIGHT, result.themeMode)
        assertEquals(UserPreferences().showCompleted, result.showCompleted)
    }
}
