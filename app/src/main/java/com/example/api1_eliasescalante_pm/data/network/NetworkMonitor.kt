package com.example.api1_eliasescalante_pm.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Observa la conectividad del dispositivo. El repositorio se suscribe a este
 * flujo para reintentar la sincronización cuando la red vuelve.
 */
interface NetworkMonitor {
    fun isOnline(): Boolean
    val isOnlineFlow: Flow<Boolean>
}

class AndroidNetworkMonitor(context: Context) : NetworkMonitor {

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE)
            as ConnectivityManager

    override fun isOnline(): Boolean {
        return try {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities =
                connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (exception: SecurityException) {
            false
        }
    }

    override val isOnlineFlow: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(isOnline())
            }

            override fun onLost(network: Network) {
                trySend(isOnline())
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                trySend(isOnline())
            }
        }

        trySend(isOnline())

        try {
            connectivityManager.registerDefaultNetworkCallback(callback)
        } catch (exception: Exception) {
            // Si no se puede registrar el callback, seguimos con el estado actual.
            trySend(isOnline())
        }

        awaitClose {
            try {
                connectivityManager.unregisterNetworkCallback(callback)
            } catch (exception: IllegalArgumentException) {
                // El callback ya estaba desregistrado.
            }
        }
    }.distinctUntilChanged().conflate()
}
