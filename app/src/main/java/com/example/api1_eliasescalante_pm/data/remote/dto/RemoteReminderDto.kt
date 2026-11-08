package com.example.api1_eliasescalante_pm.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * DTO de respuesta del servicio remoto.
 *
 * El servicio de demostración (JSONPlaceholder) solo garantiza id, userId,
 * title y body. Los campos restantes son opcionales: si un backend propio los
 * devuelve se usan directamente; si no, se recuperan del bloque de metadatos
 * que la aplicación escribe dentro de "body". De esta manera el contrato
 * remoto no contamina el modelo de dominio ni la base local.
 */
data class RemoteReminderDto(
    @SerializedName("id")
    val remoteId: Long? = null,

    @SerializedName("userId")
    val userId: Long? = null,

    @SerializedName("title")
    val title: String? = null,

    @SerializedName("body")
    val body: String? = null,

    @SerializedName("clientId")
    val clientId: String? = null,

    @SerializedName("description")
    val description: String? = null,

    @SerializedName("category")
    val category: String? = null,

    @SerializedName("priority")
    val priority: String? = null,

    @SerializedName("status")
    val status: String? = null,

    @SerializedName("dueDate")
    val dueDate: Long? = null,

    @SerializedName("location")
    val location: String? = null,

    @SerializedName("updatedAt")
    val updatedAt: Long? = null
)

/**
 * Cuerpo que se envía al servicio. [clientId] es el identificador estable
 * generado en el dispositivo: permite reconciliar una creación cuyo response
 * se perdió por timeout sin volver a crear el registro.
 */
data class RemoteReminderRequest(
    @SerializedName("userId")
    val userId: Long = DEMO_USER_ID,

    @SerializedName("title")
    val title: String,

    @SerializedName("body")
    val body: String,

    @SerializedName("clientId")
    val clientId: String,

    @SerializedName("description")
    val description: String?,

    @SerializedName("category")
    val category: String,

    @SerializedName("priority")
    val priority: String,

    @SerializedName("status")
    val status: String,

    @SerializedName("dueDate")
    val dueDate: Long?,

    @SerializedName("location")
    val location: String?,

    @SerializedName("updatedAt")
    val updatedAt: Long
) {
    companion object {
        const val DEMO_USER_ID = 1L
    }
}
