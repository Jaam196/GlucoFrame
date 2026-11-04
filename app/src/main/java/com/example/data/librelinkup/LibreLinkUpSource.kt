package com.example.data.librelinkup

import com.example.core.common.CryptoUtils
import com.example.core.common.GlucoseUtils
import com.example.core.datastore.PreferencesRepository
import com.example.core.model.GlucoseReading
import com.example.core.model.SyncResult
import com.example.core.model.TrendArrow
import com.example.core.security.KeystoreManager
import com.example.data.network.NetworkClients
import com.example.data.source.GlucoseSource
import com.example.data.source.SourceError
import com.example.data.source.SourceResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

class LibreLinkUpSource(
    private val keystoreManager: KeystoreManager,
    private val preferencesRepository: PreferencesRepository,
    private val customClient: OkHttpClient? = null
) : GlucoseSource {

    override val sourceId: String = "LIBRELINKUP"
    override val displayName: String = "LibreLinkUp"

    private val client: OkHttpClient
        get() = customClient ?: NetworkClients.cloudClient

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // 429 rate limit countdown
    private var rateLimitUntilMs: Long = 0L

    // Minimum 5 minutes between relogin attempts after 401/403
    private var lastReloginMs: Long = 0L

    companion object {
        const val LOGIN_ENDPOINT = "/llu/auth/login"
        const val CONNECTIONS_ENDPOINT = "/llu/connections"
        const val GRAPH_ENDPOINT = "/llu/connections/%s/graph"
        const val CONTINUE_ENDPOINT = "/auth/continue/%s"
    }

    fun buildBaseUrl(domain: String, region: String): String {
        if (domain.startsWith("http://")) {
            throw IllegalArgumentException("Solo se admite HTTPS para LibreLinkUp")
        }
        if (domain.startsWith("https://")) {
            return domain.trimEnd('/')
        }
        val cleanDomain = domain.ifBlank { "io" }
        return if (region.isBlank()) {
            "https://api.libreview.$cleanDomain"
        } else {
            "https://api-$region.libreview.$cleanDomain"
        }
    }

    private fun buildHeaders(
        version: String,
        product: String,
        userId: String,
        token: String
    ): Map<String, String> {
        val headers = mutableMapOf(
            "product" to product.ifBlank { "llu.android" },
            "version" to version.ifBlank { "4.17.0" },
            "Accept" to "application/json",
            "Content-Type" to "application/json",
            "cache-control" to "no-cache",
            "Account-Id" to if (userId.isNotBlank()) CryptoUtils.sha256(userId) else ""
        )
        if (token.isNotBlank()) {
            headers["Authorization"] = "Bearer $token"
        }
        return headers
    }

    override suspend fun fetchCurrentAndGraph(fetchGraph: Boolean): SourceResult = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (now < rateLimitUntilMs) {
            val remainingSec = ((rateLimitUntilMs - now) / 1000).toInt()
            return@withContext SourceResult.Error(
                SourceError(
                    resultType = SyncResult.RATE_LIMIT,
                    message = "El servicio limita las peticiones. Reintento en $remainingSec s",
                    rateLimitRemainingSeconds = remainingSec,
                    retryDelaySeconds = remainingSec
                )
            )
        }

        val prefs = preferencesRepository.userPreferencesFlow.first()
        var creds = keystoreManager.loadCredentials()

        try {
            buildBaseUrl(prefs.lluDomain, creds.libreRegion)
        } catch (e: IllegalArgumentException) {
            return@withContext SourceResult.Error(
                SourceError(
                    resultType = SyncResult.AUTH,
                    message = e.message ?: "Solo se admite HTTPS para LibreLinkUp",
                    pauseLoop = true
                )
            )
        }

        if (creds.libreEmail.isBlank() || creds.librePassword.isBlank()) {
            return@withContext SourceResult.Error(
                SourceError(
                    resultType = SyncResult.AUTH,
                    message = "Configura el correo y la contraseña del seguidor",
                    pauseLoop = true
                )
            )
        }

        // Check if token valid
        var token = creds.libreToken
        val tokenExpires = creds.libreTokenExpiresMs
        if (token.isBlank() || now >= tokenExpires) {
            val loginResult = performLogin(prefs.lluDomain, creds.libreRegion, prefs.lluVersion, prefs.lluProduct, prefs.lluAutoAcceptTerms)
            if (loginResult is SourceResult.Error) {
                return@withContext loginResult
            }
            creds = keystoreManager.loadCredentials()
            token = creds.libreToken
        }

        // Get patientId or connection
        var patientId = creds.librePatientId
        if (patientId.isBlank()) {
            val connResult = fetchConnections(prefs.lluDomain, creds.libreRegion, prefs.lluVersion, prefs.lluProduct, token, creds.libreUserId)
            if (connResult is SourceResult.Error) {
                return@withContext connResult
            }
            creds = keystoreManager.loadCredentials()
            patientId = creds.librePatientId
        }

        if (patientId.isBlank()) {
            return@withContext SourceResult.Error(
                SourceError(
                    resultType = SyncResult.NO_CONNECTION,
                    message = "Falta seleccionar un paciente en la lista de conexiones",
                    pauseLoop = true
                )
            )
        }

        // If fetchGraph is requested (on start or every 30m), call graph endpoint
        // Otherwise, connection or graph can provide current reading
        val result = fetchGraphData(patientId, prefs.lluDomain, creds.libreRegion, prefs.lluVersion, prefs.lluProduct, token, creds.libreUserId, fetchGraph)
        if (result is SourceResult.Error && (result.error.httpStatus == 401 || result.error.httpStatus == 403)) {
            val nowMs = System.currentTimeMillis()
            if (nowMs - lastReloginMs >= 5 * 60 * 1000L) {
                lastReloginMs = nowMs
                val retryLogin = performLogin(prefs.lluDomain, creds.libreRegion, prefs.lluVersion, prefs.lluProduct, prefs.lluAutoAcceptTerms)
                if (retryLogin is SourceResult.Success) {
                    val freshCreds = keystoreManager.loadCredentials()
                    return@withContext fetchGraphData(
                        freshCreds.librePatientId,
                        prefs.lluDomain,
                        freshCreds.libreRegion,
                        prefs.lluVersion,
                        prefs.lluProduct,
                        freshCreds.libreToken,
                        freshCreds.libreUserId,
                        fetchGraph
                    )
                }
            }
        }
        result
    }

    override suspend fun testConnection(): SourceResult {
        return fetchCurrentAndGraph(fetchGraph = false)
    }

    suspend fun fetchConnectionsList(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val prefs = preferencesRepository.userPreferencesFlow.first()
        var creds = keystoreManager.loadCredentials()
        if (creds.libreToken.isBlank() || System.currentTimeMillis() >= creds.libreTokenExpiresMs) {
            val loginRes = performLogin(prefs.lluDomain, creds.libreRegion, prefs.lluVersion, prefs.lluProduct, prefs.lluAutoAcceptTerms)
            if (loginRes is SourceResult.Error) return@withContext emptyList()
            creds = keystoreManager.loadCredentials()
        }

        val baseUrl = buildBaseUrl(prefs.lluDomain, creds.libreRegion)
        val headers = buildHeaders(prefs.lluVersion, prefs.lluProduct, creds.libreUserId, creds.libreToken)
        val requestBuilder = Request.Builder()
            .url("$baseUrl$CONNECTIONS_ENDPOINT")
            .get()
        headers.forEach { (k, v) -> if (v.isNotBlank()) requestBuilder.addHeader(k, v) }

        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                val json = JSONObject(bodyStr)
                val data = json.optJSONArray("data") ?: return@withContext emptyList()
                val list = mutableListOf<Pair<String, String>>()
                for (i in 0 until data.length()) {
                    val item = data.getJSONObject(i)
                    val pid = item.optString("patientId", "")
                    val firstName = item.optString("firstName", "")
                    val lastName = item.optString("lastName", "")
                    val name = "$firstName $lastName".trim().ifBlank { "Paciente #${i + 1}" }
                    if (pid.isNotBlank()) {
                        list.add(Pair(pid, name))
                    }
                }
                list
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun performLogin(
        domain: String,
        initialRegion: String,
        version: String,
        product: String,
        autoAcceptTerms: Boolean,
        lastError4Type: String = "",
        depth: Int = 0
    ): SourceResult {
        if (depth > 3) {
            return SourceResult.Error(
                SourceError(
                    resultType = SyncResult.OTHER,
                    message = "Demasiadas redirecciones de LibreLinkUp",
                    pauseLoop = true
                )
            )
        }

        val creds = keystoreManager.loadCredentials()
        val email = creds.libreEmail
        val password = creds.librePassword
        var region = initialRegion.ifBlank { creds.libreRegion }

        val baseUrl = buildBaseUrl(domain, region)
        val headers = buildHeaders(version, product, creds.libreUserId, "")

        val jsonBody = JSONObject().apply {
            put("email", email)
            put("password", password)
        }

        val requestBuilder = Request.Builder()
            .url("$baseUrl$LOGIN_ENDPOINT")
            .post(jsonBody.toString().toRequestBody(jsonMediaType))
        headers.forEach { (k, v) -> if (v.isNotBlank()) requestBuilder.addHeader(k, v) }

        return try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                val code = response.code
                val bodyStr = response.body?.string() ?: ""

                // 429 check
                if (code == 429) {
                    rateLimitUntilMs = System.currentTimeMillis() + (300 * 1000L)
                    return SourceResult.Error(
                        SourceError(
                            resultType = SyncResult.RATE_LIMIT,
                            httpStatus = 429,
                            message = "El servicio limita las peticiones. Pausa de 5 min.",
                            rateLimitRemainingSeconds = 300,
                            retryDelaySeconds = 300
                        )
                    )
                }

                // 403 minimumVersion check (status 920)
                if (code == 403 && bodyStr.isNotBlank()) {
                    val errJson = runCatching { JSONObject(bodyStr) }.getOrNull()
                    val minVer = errJson?.optJSONObject("data")?.optString("minimumVersion")
                    if (!minVer.isNullOrBlank() && minVer != version) {
                        preferencesRepository.updateLluVersion(minVer)
                        // Retry once with new version
                        return performLogin(domain, region, minVer, product, autoAcceptTerms, lastError4Type, depth + 1)
                    }
                    return SourceResult.Error(
                        SourceError(
                            resultType = SyncResult.VERSION,
                            httpStatus = 403,
                            message = "LibreLinkUp cambió. Revisa los ajustes avanzados o usa Nightscout"
                        )
                    )
                }

                if (!response.isSuccessful) {
                    return SourceResult.Error(
                        SourceError(
                            resultType = if (code in 400..403) SyncResult.AUTH else SyncResult.OTHER,
                            httpStatus = code,
                            message = "Error en inicio de sesión ($code)"
                        )
                    )
                }

                val json = runCatching { JSONObject(bodyStr) }.getOrNull()
                    ?: return SourceResult.Error(
                        SourceError(
                            resultType = SyncResult.FORMAT,
                            message = "Respuesta inesperada del servicio (JSON inválido)"
                        )
                    )

                val status = json.optInt("status", -1)

                // Error 4: Terms of use or Privacy policy step
                if (status == 4) {
                    return handleStatus4(json, domain, region, version, product, autoAcceptTerms, lastError4Type, depth)
                }

                if (status != 0) {
                    return SourceResult.Error(
                        SourceError(
                            resultType = SyncResult.AUTH,
                            httpStatus = code,
                            message = "Estado de servicio: $status"
                        )
                    )
                }

                val data = json.optJSONObject("data")
                    ?: return SourceResult.Error(
                        SourceError(
                            resultType = SyncResult.FORMAT,
                            message = "Respuesta sin datos de autenticación"
                        )
                    )

                // Check region redirect
                if (data.optBoolean("redirect", false)) {
                    val redirectRegion = data.optString("region", "")
                    if (redirectRegion.isNotBlank()) {
                        preferencesRepository.updateLluRegion(redirectRegion)
                        keystoreManager.updateCredentials { it.copy(libreRegion = redirectRegion) }
                        return performLogin(domain, redirectRegion, version, product, autoAcceptTerms, lastError4Type, depth + 1)
                    }
                }

                // Extract token and user id
                val authTicket = data.optJSONObject("authTicket")
                val token = authTicket?.optString("token", "") ?: ""
                val expiresSec = authTicket?.optLong("expires", 0L) ?: 0L
                val expiresMs = expiresSec * 1000L

                val userObj = data.optJSONObject("user")
                val userId = userObj?.optString("id", "") ?: creds.libreUserId

                if (token.isBlank()) {
                    return SourceResult.Error(
                        SourceError(
                            resultType = SyncResult.AUTH,
                            message = "No se recibió token de autorización"
                        )
                    )
                }

                keystoreManager.updateCredentials {
                    it.copy(
                        libreToken = token,
                        libreTokenExpiresMs = expiresMs,
                        libreUserId = userId,
                        libreRegion = region
                    )
                }

                val serverTimeMs = GlucoseUtils.parseHttpDateHeader(response.header("Date"))
                SourceResult.Success(emptyList(), serverTimeMs = serverTimeMs)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SourceResult.Error(
                SourceError(
                    resultType = SyncResult.NO_NETWORK,
                    message = "Sin conexión a Internet: ${e.message}"
                )
            )
        }
    }

    private suspend fun handleStatus4(
        json: JSONObject,
        domain: String,
        region: String,
        version: String,
        product: String,
        autoAcceptTerms: Boolean,
        lastError4Type: String,
        depth: Int = 0
    ): SourceResult {
        val data = json.optJSONObject("data")
        val step = data?.optJSONObject("step")
        val stepType = step?.optString("type", "") ?: ""
        val provToken = data?.optJSONObject("authTicket")?.optString("token", "") ?: ""

        if (stepType.isBlank() || provToken.isBlank()) {
            return SourceResult.Error(
                SourceError(
                    resultType = SyncResult.TERMS_PENDING,
                    message = "Hay que aceptar los términos en la cuenta de seguidor",
                    pauseLoop = true
                )
            )
        }

        if (!autoAcceptTerms) {
            return SourceResult.Error(
                SourceError(
                    resultType = SyncResult.TERMS_PENDING,
                    message = "Hay que aceptar los términos ($stepType) en la cuenta de seguidor",
                    pauseLoop = true
                )
            )
        }

        if (stepType == lastError4Type) {
            // Prevent infinite loop if already attempted this step
            return SourceResult.Error(
                SourceError(
                    resultType = SyncResult.TERMS_PENDING,
                    message = "Paso de términos ($stepType) pendiente de confirmación",
                    pauseLoop = true
                )
            )
        }

        // Send POST /auth/continue/{tipo} with provisional token
        val baseUrl = buildBaseUrl(domain, region)
        val continueUrl = baseUrl + String.format(CONTINUE_ENDPOINT, stepType)
        val headers = mutableMapOf(
            "product" to product,
            "version" to version,
            "Accept" to "application/json",
            "Content-Type" to "application/json",
            "Authorization" to "Bearer $provToken"
        )

        val requestBuilder = Request.Builder()
            .url(continueUrl)
            .post("{}".toRequestBody(jsonMediaType))
        headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }

        return try {
            client.newCall(requestBuilder.build()).execute().use { resp ->
                // Now re-login
                performLogin(domain, region, version, product, autoAcceptTerms, lastError4Type = stepType, depth = depth + 1)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SourceResult.Error(
                SourceError(
                    resultType = SyncResult.TERMS_PENDING,
                    message = "Fallo al aceptar términos automáticamente: ${e.message}"
                )
            )
        }
    }

    private suspend fun fetchConnections(
        domain: String,
        region: String,
        version: String,
        product: String,
        token: String,
        userId: String
    ): SourceResult {
        val baseUrl = buildBaseUrl(domain, region)
        val headers = buildHeaders(version, product, userId, token)
        val requestBuilder = Request.Builder()
            .url("$baseUrl$CONNECTIONS_ENDPOINT")
            .get()
        headers.forEach { (k, v) -> if (v.isNotBlank()) requestBuilder.addHeader(k, v) }

        return try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                val code = response.code
                if (code == 429) {
                    rateLimitUntilMs = System.currentTimeMillis() + (300 * 1000L)
                    return SourceResult.Error(
                        SourceError(
                            resultType = SyncResult.RATE_LIMIT,
                            httpStatus = 429,
                            message = "El servicio limita las peticiones. Pausa de 5 min.",
                            rateLimitRemainingSeconds = 300,
                            retryDelaySeconds = 300
                        )
                    )
                }

                if (!response.isSuccessful) {
                    return SourceResult.Error(
                        SourceError(
                            resultType = if (code in 401..403) SyncResult.AUTH else SyncResult.OTHER,
                            httpStatus = code,
                            message = "Error al consultar conexiones ($code)"
                        )
                    )
                }

                val bodyStr = response.body?.string() ?: ""
                val json = runCatching { JSONObject(bodyStr) }.getOrNull()
                    ?: return SourceResult.Error(
                        SourceError(
                            resultType = SyncResult.FORMAT,
                            message = "Respuesta de conexiones inválida"
                        )
                    )

                val data = json.optJSONArray("data")
                if (data == null || data.length() == 0) {
                    return SourceResult.Error(
                        SourceError(
                            resultType = SyncResult.NO_CONNECTION,
                            message = "Falta aceptar la invitación de seguidor en la app LibreLinkUp, o la cuenta está en otra región",
                            pauseLoop = true
                        )
                    )
                }

                // Take first connection or selected patient
                val firstConn = data.getJSONObject(0)
                captureSensorInfo(firstConn)
                val pId = firstConn.optString("patientId", "")
                val pName = "${firstConn.optString("firstName", "")} ${firstConn.optString("lastName", "")}".trim()
                keystoreManager.updateCredentials {
                    it.copy(librePatientId = pId, librePatientName = pName)
                }

                // Check if current reading is inside connection
                val readings = mutableListOf<GlucoseReading>()
                val measurement = firstConn.optJSONObject("glucoseMeasurement")
                if (measurement != null) {
                    val r = parseMeasurement(measurement)
                    if (r != null) readings.add(r)
                }

                val serverTimeMs = GlucoseUtils.parseHttpDateHeader(response.header("Date"))
                SourceResult.Success(readings, serverTimeMs = serverTimeMs)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SourceResult.Error(
                SourceError(
                    resultType = SyncResult.NO_NETWORK,
                    message = "Sin conexión a Internet al obtener conexiones: ${e.message}"
                )
            )
        }
    }

    private suspend fun fetchGraphData(
        patientId: String,
        domain: String,
        region: String,
        version: String,
        product: String,
        token: String,
        userId: String,
        includeHistorical: Boolean
    ): SourceResult {
        val baseUrl = buildBaseUrl(domain, region)
        val endpoint = String.format(GRAPH_ENDPOINT, patientId)
        val headers = buildHeaders(version, product, userId, token)

        val requestBuilder = Request.Builder()
            .url("$baseUrl$endpoint")
            .get()
        headers.forEach { (k, v) -> if (v.isNotBlank()) requestBuilder.addHeader(k, v) }

        return try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                val code = response.code

                if (code == 429) {
                    rateLimitUntilMs = System.currentTimeMillis() + (300 * 1000L)
                    return SourceResult.Error(
                        SourceError(
                            resultType = SyncResult.RATE_LIMIT,
                            httpStatus = 429,
                            message = "El servicio limita las peticiones. Pausa de 5 min.",
                            rateLimitRemainingSeconds = 300,
                            retryDelaySeconds = 300
                        )
                    )
                }

                if (!response.isSuccessful) {
                    return SourceResult.Error(
                        SourceError(
                            resultType = if (code in 401..403) SyncResult.AUTH else SyncResult.OTHER,
                            httpStatus = code,
                            message = "Error al obtener gráfico ($code)"
                        )
                    )
                }

                val bodyStr = response.body?.string() ?: ""
                val json = runCatching { JSONObject(bodyStr) }.getOrNull()
                    ?: return SourceResult.Error(
                        SourceError(
                            resultType = SyncResult.FORMAT,
                            message = "Respuesta de gráfico inválida"
                        )
                    )

                val data = json.optJSONObject("data")
                    ?: return SourceResult.Error(
                        SourceError(
                            resultType = SyncResult.FORMAT,
                            message = "Respuesta sin datos"
                        )
                    )

                val readings = mutableListOf<GlucoseReading>()
                captureSensorInfo(data)

                // Current measurement from data.connection.glucoseMeasurement
                val connection = data.optJSONObject("connection")
                val currentMeasurement = connection?.optJSONObject("glucoseMeasurement")
                if (currentMeasurement != null) {
                    val currentReading = parseMeasurement(currentMeasurement)
                    if (currentReading != null) {
                        readings.add(currentReading)
                    }
                }

                // Historical graph data from data.graphData
                if (includeHistorical) {
                    val graphArray = data.optJSONArray("graphData")
                    if (graphArray != null) {
                        for (i in 0 until graphArray.length()) {
                            val item = graphArray.optJSONObject(i) ?: continue
                            val histReading = parseMeasurement(item)
                            if (histReading != null) {
                                readings.add(histReading)
                            }
                        }
                    }
                }

                if (readings.isEmpty()) {
                    return SourceResult.Error(
                        SourceError(
                            resultType = SyncResult.FORMAT,
                            message = "El sensor no envía datos nuevos"
                        )
                    )
                }

                val serverTimeMs = GlucoseUtils.parseHttpDateHeader(response.header("Date"))
                SourceResult.Success(readings, serverTimeMs = serverTimeMs)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SourceResult.Error(
                SourceError(
                    resultType = SyncResult.NO_NETWORK,
                    message = "Sin conexión a Internet: ${e.message}"
                )
            )
        }
    }

    /** Saves the sensor data (serial, start date, limits...) that LibreLinkUp reports. Never throws. */
    private fun captureSensorInfo(data: JSONObject) {
        try {
            val info = SensorInfoStore.parse(data, System.currentTimeMillis()) ?: return
            SensorInfoStore.update(com.example.GlucoseFrameApplication.instanceOrNull(), info)
        } catch (e: Exception) {
            // Extra data must never break the reading
        }
    }

    private fun parseMeasurement(json: JSONObject): GlucoseReading? {
        val valueMgDl = json.optInt("ValueInMgPerDl", 0)
        if (!GlucoseUtils.isValidGlucose(valueMgDl)) return null

        val factoryTimeStr = json.optString("FactoryTimestamp", "")
        val localTimeStr = json.optString("Timestamp", "")

        val parsedUtc = GlucoseUtils.parseFactoryTimestampUtc(factoryTimeStr)
        val parsedLocal = GlucoseUtils.parseLocalTimestamp(localTimeStr)
        val measuredAt = if (parsedUtc > 0L) parsedUtc else parsedLocal
        if (measuredAt <= 0L) return null

        val trendVal = json.optInt("TrendArrow", 3)
        val trend = TrendArrow.fromLibre(trendVal)
        val isHigh = json.optBoolean("isHigh", valueMgDl > 180)
        val isLow = json.optBoolean("isLow", valueMgDl < 70)

        return GlucoseReading(
            sourceId = sourceId,
            measuredAtUtcMs = measuredAt,
            valueMgDl = valueMgDl,
            trend = trend,
            isHigh = isHigh,
            isLow = isLow,
            receivedAtUtcMs = System.currentTimeMillis()
        )
    }
}
