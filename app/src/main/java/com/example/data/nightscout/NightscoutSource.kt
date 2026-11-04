package com.example.data.nightscout

import com.example.core.common.CryptoUtils
import com.example.core.common.GlucoseUtils
import com.example.core.model.GlucoseReading
import com.example.core.model.SyncResult
import com.example.core.model.Treatment
import com.example.core.model.TreatmentKind
import com.example.core.model.TrendArrow
import com.example.core.security.KeystoreManager
import com.example.data.network.NetworkClients
import com.example.data.source.GlucoseSource
import com.example.data.source.SourceError
import com.example.data.source.SourceResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

open class NightscoutSource(
    private val keystoreManager: KeystoreManager,
    private val customClient: OkHttpClient? = null
) : GlucoseSource {

    override val sourceId: String = "NIGHTSCOUT"
    override val displayName: String = "Nightscout"

    protected open val client: OkHttpClient
        get() = customClient ?: NetworkClients.cloudClient

    companion object {
        const val CURRENT_ENDPOINT = "/api/v1/entries/current.json"
        const val SGV_ENDPOINT = "/api/v1/entries/sgv.json"
        const val TREATMENTS_ENDPOINT = "/api/v1/treatments.json"
    }

    protected open fun getBaseUrl(): String {
        val creds = keystoreManager.loadCredentials()
        return creds.nightscoutUrl.trim().trimEnd('/')
    }

    protected open fun getAuthParams(): Pair<String, String> {
        val creds = keystoreManager.loadCredentials()
        return Pair(creds.nightscoutToken.trim(), creds.nightscoutSecret.trim())
    }

    private fun appendTokenToUrl(url: String, token: String): String {
        if (token.isBlank()) return url
        val sep = if (url.contains("?")) "&" else "?"
        return "$url${sep}token=$token"
    }

    override suspend fun fetchCurrentAndGraph(fetchGraph: Boolean): SourceResult = withContext(Dispatchers.IO) {
        val baseUrl = getBaseUrl()
        if (baseUrl.isBlank()) {
            return@withContext SourceResult.Error(
                SourceError(
                    resultType = SyncResult.AUTH,
                    message = "Introduce la URL de Nightscout",
                    pauseLoop = true
                )
            )
        }

        val (token, secret) = getAuthParams()

        try {
            val readings = mutableListOf<GlucoseReading>()

            var serverTimeMs: Long? = null

            // 1. Fetch current
            val currentUrl = appendTokenToUrl("$baseUrl$CURRENT_ENDPOINT", token)
            val reqBuilder = Request.Builder().url(currentUrl).get()
            if (secret.isNotBlank()) {
                reqBuilder.addHeader("api-secret", CryptoUtils.sha1(secret))
            }

            client.newCall(reqBuilder.build()).execute().use { response ->
                serverTimeMs = GlucoseUtils.parseHttpDateHeader(response.header("Date"))
                if (!response.isSuccessful) {
                    return@withContext SourceResult.Error(
                        SourceError(
                            resultType = if (response.code in 401..403) SyncResult.AUTH else SyncResult.OTHER,
                            httpStatus = response.code,
                            message = "Error en Nightscout HTTP ${response.code}"
                        )
                    )
                }
                val body = response.body?.string() ?: ""
                val curList = parseEntries(body)
                readings.addAll(curList)
            }

            // 2. Fetch historical if requested
            if (fetchGraph) {
                val past24hMs = System.currentTimeMillis() - (24 * 60 * 60 * 1000L)
                val histUrl = appendTokenToUrl("$baseUrl$SGV_ENDPOINT?find[date][\$gt]=$past24hMs&count=288", token)
                val histReqBuilder = Request.Builder().url(histUrl).get()
                if (secret.isNotBlank()) {
                    histReqBuilder.addHeader("api-secret", CryptoUtils.sha1(secret))
                }

                client.newCall(histReqBuilder.build()).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val histList = parseEntries(body)
                        readings.addAll(histList)
                    }
                }
            }

            if (readings.isEmpty()) {
                return@withContext SourceResult.Error(
                    SourceError(
                        resultType = SyncResult.FORMAT,
                        message = "No se recibieron lecturas válidas de Nightscout"
                    )
                )
            }

            SourceResult.Success(readings, serverTimeMs = serverTimeMs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SourceResult.Error(
                SourceError(
                    resultType = SyncResult.NO_NETWORK,
                    message = "Sin conexión con Nightscout: ${e.message}"
                )
            )
        }
    }

    override suspend fun testConnection(): SourceResult {
        return fetchCurrentAndGraph(fetchGraph = false)
    }

    /**
     * Insulin doses, carbohydrates and notes recorded in Nightscout (last 48 h).
     * Returns an empty list on any problem: this extra data must never break the glucose reading.
     */
    suspend fun fetchTreatments(): List<Treatment> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = getBaseUrl()
            if (baseUrl.isBlank()) return@withContext emptyList()
            val (token, secret) = getAuthParams()
            val sinceIso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.format(java.util.Date(System.currentTimeMillis() - 48 * 3_600_000L))
            val url = appendTokenToUrl("$baseUrl$TREATMENTS_ENDPOINT?find[created_at][\$gte]=$sinceIso&count=200", token)
            val req = Request.Builder().url(url).get()
            if (secret.isNotBlank()) req.addHeader("api-secret", CryptoUtils.sha1(secret))
            client.newCall(req.build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                parseTreatments(response.body?.string() ?: "")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    protected fun parseTreatments(jsonString: String): List<Treatment> {
        val out = mutableListOf<Treatment>()
        try {
            val arr = JSONArray(jsonString)
            val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("_id", o.optString("identifier", ""))
                if (id.isBlank()) continue
                var at = o.optLong("mills", 0L)
                if (at <= 0L) {
                    val created = o.optString("created_at", "")
                    at = if (created.length >= 19) fmt.parse(created.substring(0, 19))?.time ?: 0L else 0L
                }
                if (at <= 0L) continue
                val insulin = o.optDouble("insulin", 0.0).let { if (it.isNaN()) 0.0 else it }
                val carbs = o.optDouble("carbs", 0.0).let { if (it.isNaN()) 0.0 else it }
                val notes = o.optString("notes", "").ifBlank { o.optString("eventType", "") }
                if (insulin > 0.0) {
                    out.add(Treatment(atMs = at, kind = TreatmentKind.INSULIN, amount = insulin, note = notes,
                        origin = Treatment.ORIGIN_NIGHTSCOUT, externalId = "ns-$id-i"))
                }
                if (carbs > 0.0) {
                    out.add(Treatment(atMs = at, kind = TreatmentKind.FOOD, amount = carbs, note = notes,
                        origin = Treatment.ORIGIN_NIGHTSCOUT, externalId = "ns-$id-c"))
                }
                if (insulin <= 0.0 && carbs <= 0.0 && o.optString("notes", "").isNotBlank()) {
                    out.add(Treatment(atMs = at, kind = TreatmentKind.NOTE, note = o.optString("notes", ""),
                        origin = Treatment.ORIGIN_NIGHTSCOUT, externalId = "ns-$id-n"))
                }
            }
        } catch (e: Exception) {
            // ignore malformed answer
        }
        return out
    }

    protected fun parseEntries(jsonString: String): List<GlucoseReading> {
        val list = mutableListOf<GlucoseReading>()
        try {
            val jsonArray = when {
                jsonString.trim().startsWith("[") -> JSONArray(jsonString)
                jsonString.trim().startsWith("{") -> {
                    val obj = JSONObject(jsonString)
                    obj.optJSONArray("bgs") ?: JSONArray().apply { put(obj) }
                }
                else -> JSONArray()
            }

            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.optJSONObject(i) ?: continue
                val sgv = item.optInt("sgv", item.optInt("value", 0))
                if (!GlucoseUtils.isValidGlucose(sgv)) continue

                val dateMs = item.optLong("date", 0L)
                if (dateMs <= 0L) continue

                val dir = item.optString("direction", "")
                val trend = TrendArrow.fromNightscout(dir)

                list.add(
                    GlucoseReading(
                        sourceId = sourceId,
                        measuredAtUtcMs = dateMs,
                        valueMgDl = sgv,
                        trend = trend,
                        isHigh = sgv > 180,
                        isLow = sgv < 70,
                        receivedAtUtcMs = System.currentTimeMillis()
                    )
                )
            }
        } catch (e: Exception) {
            // Ignore format error in individual batch
        }
        return list
    }
}
