package com.example.core.model

enum class SyncResult(val userMessageEs: String) {
    OK("Conectado"),
    NO_NETWORK("Sin conexión a Internet"),
    AUTH("Revisa la cuenta de seguidor"),
    REGION("Redirección de región completada"),
    TERMS_PENDING("Hay que aceptar los términos en la cuenta de seguidor"),
    RATE_LIMIT("El servicio limita las peticiones"),
    VERSION("LibreLinkUp cambió. Revisa los ajustes avanzados o usa Nightscout"),
    FORMAT("Respuesta inesperada del servicio"),
    NO_CONNECTION("Falta aceptar la invitación o la cuenta está en otra región"),
    OTHER("Error de sincronización")
}

data class SyncEvent(
    val id: Long = 0,
    val atUtcMs: Long = System.currentTimeMillis(),
    val sourceId: String,
    val result: SyncResult,
    val httpStatus: Int? = null,
    val message: String = ""
)
