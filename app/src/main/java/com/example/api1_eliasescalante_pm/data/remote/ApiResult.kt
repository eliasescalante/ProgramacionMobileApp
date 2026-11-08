package com.example.api1_eliasescalante_pm.data.remote

/**
 * Resultado de una llamada remota. La capa de datos traduce aquí los códigos
 * HTTP y las excepciones de red; los ViewModel y Compose nunca ven Retrofit,
 * OkHttp ni excepciones de bajo nivel.
 */
sealed interface ApiResult<out T> {

    data class Success<T>(val value: T) : ApiResult<T>

    /** 4xx: la solicitud no es válida (datos, recurso inexistente, etc.). */
    data class ClientError(val code: Int, val message: String) : ApiResult<Nothing>

    /** 5xx: el servidor falló; la operación puede reintentarse. */
    data class ServerError(val code: Int, val message: String) : ApiResult<Nothing>

    /** No se pudo establecer conexión (sin red, DNS, conexión rechazada). */
    data class NoConnection(val message: String) : ApiResult<Nothing>

    /** La conexión existió pero el servidor no respondió a tiempo. */
    data class Timeout(val message: String) : ApiResult<Nothing>

    data class UnknownError(val message: String) : ApiResult<Nothing>

    val isSuccess: Boolean
        get() = this is Success

    fun messageOrNull(): String? = when (this) {
        is Success -> null
        is ClientError -> message
        is ServerError -> message
        is NoConnection -> message
        is Timeout -> message
        is UnknownError -> message
    }
}
