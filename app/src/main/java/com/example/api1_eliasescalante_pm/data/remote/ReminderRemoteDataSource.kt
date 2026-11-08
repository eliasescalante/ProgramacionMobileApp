package com.example.api1_eliasescalante_pm.data.remote

import com.example.api1_eliasescalante_pm.data.remote.dto.RemoteReminderDto
import com.example.api1_eliasescalante_pm.data.remote.dto.RemoteReminderRequest
import retrofit2.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException

/**
 * Contrato de la fuente remota. El repositorio depende de esta interfaz,
 * no de Retrofit, lo que facilita sustituirla por un doble de prueba.
 */
interface ReminderRemoteDataSource {

    suspend fun fetchReminders(): ApiResult<List<RemoteReminderDto>>

    suspend fun createReminder(
        request: RemoteReminderRequest
    ): ApiResult<RemoteReminderDto>

    suspend fun updateReminder(
        remoteId: Long,
        request: RemoteReminderRequest
    ): ApiResult<RemoteReminderDto>

    suspend fun deleteReminder(remoteId: Long): ApiResult<Unit>
}

class RetrofitReminderRemoteDataSource(
    private val api: ReminderApi
) : ReminderRemoteDataSource {

    override suspend fun fetchReminders(): ApiResult<List<RemoteReminderDto>> =
        safeCall { api.getReminders() }

    override suspend fun createReminder(
        request: RemoteReminderRequest
    ): ApiResult<RemoteReminderDto> = safeCall { api.createReminder(request) }

    override suspend fun updateReminder(
        remoteId: Long,
        request: RemoteReminderRequest
    ): ApiResult<RemoteReminderDto> = safeCall { api.updateReminder(remoteId, request) }

    override suspend fun deleteReminder(remoteId: Long): ApiResult<Unit> {
        return try {
            val response = api.deleteReminder(remoteId)
            if (response.isSuccessful) {
                ApiResult.Success(Unit)
            } else {
                response.toUnitApiError()
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            exception.toApiError()
        }
    }

    private suspend fun <T> safeCall(
        block: suspend () -> Response<T>
    ): ApiResult<T> {
        return try {
            val response = block()
            if (!response.isSuccessful) {
                return response.toApiError()
            }

            val body = response.body()
            if (body == null) {
                ApiResult.UnknownError(
                    "El servidor respondió ${response.code()} sin contenido."
                )
            } else {
                ApiResult.Success(body)
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            exception.toApiError()
        }
    }

    private fun Response<*>.toUnitApiError(): ApiResult<Unit> {
        val code = code()
        val detail = errorBody()?.string()?.take(300).orEmpty()
        val suffix = if (detail.isBlank()) "" else " Detalle: $detail"
        return when (code) {
            in 400..499 -> ApiResult.ClientError(
                code,
                "Solicitud inválida ($code). Revisá los datos enviados.$suffix"
            )

            in 500..599 -> ApiResult.ServerError(
                code,
                "Error del servidor ($code). La operación se reintentará.$suffix"
            )

            else -> ApiResult.UnknownError(
                "Respuesta inesperada del servidor ($code).$suffix"
            )
        }
    }

    private fun <T> Response<T>.toApiError(): ApiResult<T> {
        val code = code()
        val detail = errorBody()?.string()?.take(300).orEmpty()
        val suffix = if (detail.isBlank()) "" else " Detalle: $detail"
        return when (code) {
            in 400..499 -> ApiResult.ClientError(
                code,
                "Solicitud inválida ($code). Revisá los datos enviados.$suffix"
            )

            in 500..599 -> ApiResult.ServerError(
                code,
                "Error del servidor ($code). La operación se reintentará.$suffix"
            )

            else -> ApiResult.UnknownError(
                "Respuesta inesperada del servidor ($code).$suffix"
            )
        }
    }

    private fun Exception.toApiError(): ApiResult<Nothing> {
        val message = message.orEmpty()
        return when (this) {
            is SocketTimeoutException -> ApiResult.Timeout(
                "El servidor tardó demasiado en responder. Los datos locales se conservaron."
            )

            is UnknownHostException,
            is ConnectException,
            is SocketException,
            is SSLException -> ApiResult.NoConnection(
                "No se pudo conectar con el servicio. Los datos locales se conservaron."
            )

            is IOException -> ApiResult.NoConnection(
                "Problema de red: ${message.ifBlank { "sin conexión" }}. " +
                    "Los datos locales se conservaron."
            )

            else -> ApiResult.UnknownError(
                "Error inesperado al llamar al servicio: " +
                    message.ifBlank { this::class.java.simpleName }
            )
        }
    }
}
