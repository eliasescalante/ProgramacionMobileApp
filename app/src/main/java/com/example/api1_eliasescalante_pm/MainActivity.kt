package com.example.api1_eliasescalante_pm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.api1_eliasescalante_pm.domain.model.ThemeMode
import com.example.api1_eliasescalante_pm.domain.model.UserPreferences
import com.example.api1_eliasescalante_pm.navigation.AppRoot
import com.example.api1_eliasescalante_pm.ui.ui.API1ELIASESCALANTEPMTheme
import com.example.api1_eliasescalante_pm.ui.ui.screens.ConfigurationErrorScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val configurationError = AppContainer.configurationError

            if (configurationError != null) {
                API1ELIASESCALANTEPMTheme(darkTheme = isSystemInDarkTheme()) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        ConfigurationErrorScreen(message = configurationError)
                    }
                }
            } else {
                // El tema se lee de DataStore: la preferencia sobrevive al
                // cierre de la aplicación y se aplica en el primer frame.
                val preferences by AppContainer.reminderRepository.preferences
                    .collectAsStateWithLifecycle(initialValue = UserPreferences())

                API1ELIASESCALANTEPMTheme(
                    darkTheme = preferences.themeMode.toDarkTheme()
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        AppRoot()
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeMode.toDarkTheme(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}
