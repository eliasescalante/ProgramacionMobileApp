package com.example.api1_eliasescalante_pm.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.api1_eliasescalante_pm.domain.model.PriorityFilter
import com.example.api1_eliasescalante_pm.domain.model.ReminderSortOrder
import com.example.api1_eliasescalante_pm.domain.model.ThemeMode
import com.example.api1_eliasescalante_pm.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Contrato de preferencias que consume el repositorio. Permite sustituir
 * DataStore por un doble de prueba sin tocar la lógica local-first.
 */
interface UserPreferencesDataSource {
    val preferences: Flow<UserPreferences>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setShowCompleted(show: Boolean)
    suspend fun setSortOrder(order: ReminderSortOrder)
    suspend fun setPriorityFilter(filter: PriorityFilter)
    suspend fun resetToDefaults()
}

/**
 * Una única instancia de DataStore para toda la aplicación.
 *
 * - El delegate de la propiedad garantiza que solo exista un DataStore por
 *   proceso para el archivo "user_preferences".
 * - Si el archivo está corrupto se reemplaza por preferencias vacías.
 * - Si la lectura falla por un IOException, se emiten valores predeterminados
 *   en lugar de romper la aplicación.
 */
private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_preferences",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

class UserPreferencesRepository(
    private val dataStore: DataStore<Preferences>
) : UserPreferencesDataSource {

    /**
     * Flujo de preferencias ya transformado al modelo de dominio.
     * Cualquier valor desconocido o ausente cae en el valor predeterminado.
     */
    override val preferences: Flow<UserPreferences> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) {
                // Error de lectura recuperable: seguimos con los valores por defecto.
                emit(emptyPreferences())
            } else {
                throw throwable
            }
        }
        .map { stored -> stored.toUserPreferences() }
        .distinctUntilChanged()

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[UserPreferencesRepositoryKey.THEME_MODE] = mode.storageValue }
    }

    override suspend fun setShowCompleted(show: Boolean) {
        dataStore.edit { it[UserPreferencesRepositoryKey.SHOW_COMPLETED] = show }
    }

    override suspend fun setSortOrder(order: ReminderSortOrder) {
        dataStore.edit { it[UserPreferencesRepositoryKey.SORT_ORDER] = order.storageValue }
    }

    override suspend fun setPriorityFilter(filter: PriorityFilter) {
        dataStore.edit { it[UserPreferencesRepositoryKey.PRIORITY_FILTER] = filter.storageValue }
    }

    override suspend fun resetToDefaults() {
        dataStore.edit { it.clear() }
    }

    companion object {
        fun create(context: Context): UserPreferencesRepository =
            UserPreferencesRepository(context.applicationContext.userPreferencesDataStore)
    }
}

/**
 * Mapeo explícito Preferences -> modelo de dominio, con valores por defecto.
 * Es público para poder probarlo con pruebas unitarias.
 */
fun Preferences.toUserPreferences(): UserPreferences {
    val defaults = UserPreferences()
    return UserPreferences(
        themeMode = ThemeMode.fromStorage(this[UserPreferencesRepositoryKey.THEME_MODE]),
        showCompleted = this[UserPreferencesRepositoryKey.SHOW_COMPLETED]
            ?: defaults.showCompleted,
        sortOrder = ReminderSortOrder.fromStorage(
            this[UserPreferencesRepositoryKey.SORT_ORDER]
        ),
        priorityFilter = PriorityFilter.fromStorage(
            this[UserPreferencesRepositoryKey.PRIORITY_FILTER]
        )
    )
}

/**
 * Las claves se exponen de forma interna para las pruebas; el repositorio es
 * el único que las usa en producción.
 */
internal object UserPreferencesRepositoryKey {
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val SHOW_COMPLETED = booleanPreferencesKey("show_completed")
    val SORT_ORDER = stringPreferencesKey("sort_order")
    val PRIORITY_FILTER = stringPreferencesKey("priority_filter")
}
