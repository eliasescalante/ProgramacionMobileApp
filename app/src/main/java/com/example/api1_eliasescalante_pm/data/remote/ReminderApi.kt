package com.example.api1_eliasescalante_pm.data.remote

import com.example.api1_eliasescalante_pm.data.remote.dto.RemoteReminderDto
import com.example.api1_eliasescalante_pm.data.remote.dto.RemoteReminderRequest
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * Contrato del servicio remoto (Retrofit).
 *
 * Por defecto apunta a JSONPlaceholder, un servicio REST simulado y público.
 * La URL base se configura en gradle.properties (REMOTE_BASE_URL) o con
 * -PREMOTE_BASE_URL=... y siempre termina en "/".
 */
interface ReminderApi {

    @GET("posts?_limit=50")
    suspend fun getReminders(): Response<List<RemoteReminderDto>>

    @POST("posts")
    suspend fun createReminder(
        @Body request: RemoteReminderRequest
    ): Response<RemoteReminderDto>

    @PUT("posts/{id}")
    suspend fun updateReminder(
        @Path("id") remoteId: Long,
        @Body request: RemoteReminderRequest
    ): Response<RemoteReminderDto>

    @DELETE("posts/{id}")
    suspend fun deleteReminder(
        @Path("id") remoteId: Long
    ): Response<ResponseBody>
}
