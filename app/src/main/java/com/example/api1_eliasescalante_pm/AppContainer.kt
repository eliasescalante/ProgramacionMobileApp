package com.example.api1_eliasescalante_pm

import android.content.Context
import com.example.api1_eliasescalante_pm.data.auth.FirebaseAuthDataSource
import com.example.api1_eliasescalante_pm.data.cloud.FirestoreReminderCloudDataSource
import com.example.api1_eliasescalante_pm.data.local.AppDatabase
import com.example.api1_eliasescalante_pm.data.network.AndroidNetworkMonitor
import com.example.api1_eliasescalante_pm.data.preferences.UserPreferencesRepository
import com.example.api1_eliasescalante_pm.data.repository.AuthRepository
import com.example.api1_eliasescalante_pm.data.repository.DefaultAuthRepository
import com.example.api1_eliasescalante_pm.data.repository.OfflineFirstReminderRepository
import com.example.api1_eliasescalante_pm.data.repository.ReminderRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Contenedor de dependencias manual (una única instancia por proceso).
 * Se inicializa en ReminderApplication.onCreate antes de que exista la UI.
 *
 * Orden de construcción:
 *   FirebaseApp -> FirebaseAuth -> FirebaseFirestore
 *   -> AuthRepository -> Room -> DataStore -> CloudDataSource -> ReminderRepository
 */
object AppContainer {

    @Volatile
    private var repository: ReminderRepository? = null

    @Volatile
    private var authentication: AuthRepository? = null

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Si Firebase no está configurado (falta app/google-services.json), la
     * aplicación no se cae: muestra una pantalla que explica cómo configurarlo.
     */
    @Volatile
    var configurationError: String? = null
        private set

    fun init(context: Context) {
        if (repository != null || configurationError != null) return

        synchronized(this) {
            if (repository != null || configurationError != null) return

            val applicationContext = context.applicationContext

            val firebaseApp = FirebaseApp.initializeApp(applicationContext)
                ?: FirebaseApp.getApps(applicationContext).firstOrNull()
            if (firebaseApp == null) {
                configurationError =
                    "Firebase no está configurado para esta aplicación.\n\n" +
                        "Descargá google-services.json desde la consola de Firebase " +
                        "y colocalo en la carpeta app/ del proyecto. " +
                        "Después volvé a compilar. Ver FIREBASE_SETUP.md."
                return
            }

            val firebaseAuth = FirebaseAuth.getInstance(firebaseApp)
            val firestore = FirebaseFirestore.getInstance(firebaseApp)

            val authRepository = DefaultAuthRepository(
                dataSource = FirebaseAuthDataSource(firebaseAuth),
                appScope = applicationScope
            )

            val database = AppDatabase.getInstance(applicationContext)
            val preferencesRepository = UserPreferencesRepository.create(applicationContext)
            val networkMonitor = AndroidNetworkMonitor(applicationContext)
            val cloudDataSource = FirestoreReminderCloudDataSource(
                firestore = firestore,
                auth = firebaseAuth
            )

            val reminderRepository = OfflineFirstReminderRepository(
                dao = database.reminderDao(),
                preferencesRepository = preferencesRepository,
                cloudDataSource = cloudDataSource,
                networkMonitor = networkMonitor,
                sessionProvider = authRepository,
                appScope = applicationScope
            )

            authentication = authRepository
            repository = reminderRepository

            // Primero la sesión (define el uid y limpia datos ajenos) y luego
            // el repositorio de datos (que reacciona a ella).
            authRepository.start()
            reminderRepository.start()
        }
    }

    val reminderRepository: ReminderRepository
        get() = repository ?: error(configurationError ?: "AppContainer no fue inicializado.")

    val authRepository: AuthRepository
        get() = authentication ?: error(configurationError ?: "AppContainer no fue inicializado.")
}
