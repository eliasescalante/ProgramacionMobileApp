package com.example.api1_eliasescalante_pm

import android.app.Application

/**
 * Inicializa una sola vez la base Room, DataStore, Retrofit y el repositorio.
 * Se declara en AndroidManifest con android:name=".ReminderApplication".
 */
class ReminderApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AppContainer.init(this)
    }
}
