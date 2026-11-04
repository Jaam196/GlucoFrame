package com.example.data.nightscout

import com.example.core.model.GlucoseReading
import com.example.core.model.SyncResult
import com.example.core.security.KeystoreManager
import com.example.data.network.NetworkClients
import com.example.data.source.SourceError
import com.example.data.source.SourceResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class LocalNightscoutSource(
    private val keystoreManager: KeystoreManager,
    customClient: OkHttpClient? = null
) : NightscoutSource(keystoreManager, customClient) {

    override val sourceId: String = "JUGGLUCO"
    override val displayName: String = "Juggluco (Local)"

    override val client: OkHttpClient = customClient ?: NetworkClients.localClient

    override fun getBaseUrl(): String {
        val creds = keystoreManager.loadCredentials()
        return creds.jugglucoUrl.trim().trimEnd('/')
    }

    override fun getAuthParams(): Pair<String, String> {
        val creds = keystoreManager.loadCredentials()
        return Pair(creds.jugglucoToken.trim(), "")
    }

    override suspend fun fetchCurrentAndGraph(fetchGraph: Boolean): SourceResult = withContext(Dispatchers.IO) {
        val baseUrl = getBaseUrl()
        if (baseUrl.isBlank()) {
            return@withContext SourceResult.Error(
                SourceError(
                    resultType = SyncResult.AUTH,
                    message = "Configura la URL local de Juggluco (ej. http://192.168.1.50:17580)",
                    pauseLoop = true
                )
            )
        }

        // Validate private IP restriction
        if (!NetworkClients.isPrivateNetworkAddress(baseUrl)) {
            return@withContext SourceResult.Error(
                SourceError(
                    resultType = SyncResult.AUTH,
                    message = "Juggluco solo admite direcciones privadas de red local (192.168.x.x, 10.x.x.x, 172.16-31.x.x)",
                    pauseLoop = true
                )
            )
        }

        val (token, _) = getAuthParams()

        // 1. Try /sgv.json first as required for Juggluco
        val readings = mutableListOf<GlucoseReading>()
        var primarySuccess = false

        // When the graph is needed, ask for up to ~24 h of readings (servers that ignore "count" still answer normally)
        val sgvQuery = buildList {
            if (token.isNotBlank()) add("token=$token")
            if (fetchGraph) add("count=288")
        }.joinToString("&")
        val sgvUrl = if (sgvQuery.isNotEmpty()) "$baseUrl/sgv.json?$sgvQuery" else "$baseUrl/sgv.json"
        try {
            val req = Request.Builder().url(sgvUrl).get().build()
            client.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val list = parseEntries(body)
                    if (list.isNotEmpty()) {
                        readings.addAll(list)
                        primarySuccess = true
                    }
                }
            }
        } catch (e: Exception) {
            // Will fallback below
        }

        // 2. If /sgv.json failed or returned empty, try standard Nightscout fallback
        if (!primarySuccess) {
            val fallbackUrl = if (token.isNotBlank()) "$baseUrl/api/v1/entries/current.json?token=$token" else "$baseUrl/api/v1/entries/current.json"
            try {
                val req = Request.Builder().url(fallbackUrl).get().build()
                client.newCall(req).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val list = parseEntries(body)
                        if (list.isNotEmpty()) {
                            readings.addAll(list)
                            primarySuccess = true
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                return@withContext SourceResult.Error(
                    SourceError(
                        resultType = SyncResult.NO_CONNECTION,
                        message = "No se pudo conectar al servidor local de Juggluco. Verifica que Juggluco esté en ejecución en el móvil y en la misma red Wi-Fi."
                    )
                )
            }
        }

        if (readings.isEmpty()) {
            return@withContext SourceResult.Error(
                SourceError(
                    resultType = SyncResult.FORMAT,
                    message = "Respuesta sin datos del servidor Juggluco local"
                )
            )
        }

        SourceResult.Success(readings, serverTimeMs = null)
    }
}
