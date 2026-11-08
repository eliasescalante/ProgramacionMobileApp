package com.example.api1_eliasescalante_pm

import android.content.Context
import com.example.api1_eliasescalante_pm.data.local.AppDatabase
import com.example.api1_eliasescalante_pm.data.network.AndroidNetworkMonitor
import com.example.api1_eliasescalante_pm.data.preferences.UserPreferencesRepository
import com.example.api1_eliasescalante_pm.data.remote.RemoteServiceFactory
import com.example.api1_eliasescalante_pm.data.remote.RetrofitReminderRemoteDataSource
import com.example.api1_eliasescalante_pm.data.repository.OfflineFirstReminderRepository
import com.example.api1_eliasescalante_pm.data.repository.ReminderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Contenedor de dependencias manual (una única instancia por proceso).
 * Se inicializa en ReminderApplication.onCreate antes de que exista la UI.
 */
object AppContainer {

    @Volatile
    private var repository: ReminderRepository? = null

    private lateinit var applicationScope: CoroutineScope

    fun init(context: Context) {
        if (repository != null) return

        synchronized(this) {
            if (repository != null) return

            val appContext = context.applicationContext
            val database = AppDatabase.getInstance(appContext)
            val preferencesRepository = UserPreferencesRepository.create(appContext)
            val networkMonitor = AndroidNetworkMonitor(appContext)
            val remoteDataSource = RetrofitReminderRemoteDataSource(
                RemoteServiceFactory.create()
            )

            applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

            val newRepository = OfflineFirstReminderRepository(
                dao = database.reminderDao(),
                preferencesRepository = preferencesRepository,
                remoteDataSource = remoteDataSource,
                networkMonitor = networkMonitor,
                appScope = applicationScope
            )

            repository = newRepository
            newRepository.start()
        }
    }

    val reminderRepository: ReminderRepository
        get() = repository ?: error(
            "AppContainer no fue inicializado. Verificá ReminderApplication en el manifest."
        )
}
