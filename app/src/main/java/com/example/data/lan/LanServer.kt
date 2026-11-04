package com.example.data.lan

import android.util.Log
import com.example.GlucoseFrameApplication
import com.example.core.common.CryptoUtils
import com.example.core.model.GlucoseReading
import com.example.core.model.TrendArrow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Small read-only web server so other devices running GlucoseFrame (or any Nightscout/xDrip+/Juggluco-style
 * client) can read this device's glucose over the home network instead of each one contacting the cloud.
 *
 * Endpoints (same shape as xDrip+/Juggluco local web service and Nightscout):
 *   /sgv.json  /api/v1/entries.json  /api/v1/entries/sgv.json  -> latest readings, newest first (?count=N)
 *   /api/v1/entries/current.json                               -> latest reading only
 *   /status.json                                               -> basic status and thresholds
 *
 * Safety: GET only, local-network clients only, token required (query "token" or header "api-secret" = SHA-1).
 */
class LanServer(private val app: GlucoseFrameApplication) {

    private var scope: CoroutineScope? = null
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    @Volatile
    private var token: String = ""

    val isRunning: Boolean get() = serverSocket?.isClosed == false

    /** Returns null on success or a short error message (for example when the port is already in use). */
    @Synchronized
    fun start(port: Int, accessToken: String): String? {
        stop()
        token = accessToken.trim()
        return try {
            val socket = ServerSocket(port)
            serverSocket = socket
            val newScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            scope = newScope
            acceptJob = newScope.launch {
                while (isActive && !socket.isClosed) {
                    val client = try {
                        socket.accept()
                    } catch (e: Exception) {
                        break
                    }
                    launch { handle(client) }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Could not start LAN server on port $port", e)
            "No se pudo abrir el puerto $port: ${e.message}"
        }
    }

    @Synchronized
    fun stop() {
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // Ignore
        }
        serverSocket = null
        acceptJob?.cancel()
        acceptJob = null
        scope?.cancel()
        scope = null
    }

    private suspend fun handle(client: Socket) {
        try {
            client.soTimeout = 5_000
            client.use { socket ->
                if (!isLocalClient(socket.inetAddress)) {
                    write(socket, 403, """{"error":"solo red local"}""")
                    return
                }

                val reader = socket.getInputStream().bufferedReader(Charsets.UTF_8)
                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2 || parts[0] != "GET") {
                    write(socket, 405, """{"error":"solo GET"}""")
                    return
                }

                // Headers (we only need api-secret); cap the amount read
                var apiSecret = ""
                var lines = 0
                while (lines < 40) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    if (line.startsWith("api-secret:", ignoreCase = true)) {
                        apiSecret = line.substringAfter(":").trim()
                    }
                    lines++
                }

                val target = parts[1]
                val path = target.substringBefore("?")
                val query = parseQuery(target.substringAfter("?", ""))

                if (!isAuthorized(query["token"], apiSecret)) {
                    write(socket, 401, """{"error":"token incorrecto"}""")
                    return
                }

                val body = withTimeoutOrNull(4_000L) { route(path, query) }
                if (body == null) {
                    write(socket, 404, """{"error":"no encontrado"}""")
                } else {
                    write(socket, 200, body)
                }
            }
        } catch (e: Exception) {
            // A broken client connection must never affect the app
        }
    }

    private fun isAuthorized(queryToken: String?, apiSecret: String): Boolean {
        if (token.isEmpty()) return false // never serve without a token
        if (queryToken != null && queryToken == token) return true
        if (apiSecret.isNotEmpty() && apiSecret.equals(CryptoUtils.sha1(token), ignoreCase = true)) return true
        return false
    }

    private suspend fun route(path: String, query: Map<String, String>): String? {
        val prefs = app.preferencesRepository.userPreferencesFlow.first()
        return when (path) {
            "/status.json" -> JSONObject()
                .put("status", "ok")
                .put("name", "GlucoseFrame")
                .put(
                    "thresholds",
                    JSONObject()
                        .put("bgHigh", prefs.thresholds.highMgDl)
                        .put("bgLow", prefs.thresholds.lowMgDl)
                )
                .toString()

            "/sgv.json", "/api/v1/entries.json", "/api/v1/entries/sgv.json", "/api/v1/entries/current.json" -> {
                val onlyCurrent = path.endsWith("current.json")
                val count = if (onlyCurrent) 1 else (query["count"]?.toIntOrNull() ?: DEFAULT_COUNT).coerceIn(1, MAX_COUNT)
                val nowMs = System.currentTimeMillis()
                val readings = app.repository
                    .getReadingsBetweenSync(prefs.activeSource.name, nowMs - 24 * 3_600_000L, nowMs)
                    .sortedByDescending { it.measuredAtUtcMs }
                    .take(count)
                toJson(readings)
            }

            else -> null
        }
    }

    private fun toJson(readings: List<GlucoseReading>): String {
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val array = JSONArray()
        for (r in readings) {
            array.put(
                JSONObject()
                    .put("_id", "gf-${r.measuredAtUtcMs}")
                    .put("device", "GlucoseFrame")
                    .put("date", r.measuredAtUtcMs)
                    .put("dateString", iso.format(Date(r.measuredAtUtcMs)))
                    .put("sgv", r.valueMgDl)
                    .put("direction", directionName(r.trend))
                    .put("type", "sgv")
            )
        }
        return array.toString()
    }

    private fun directionName(trend: TrendArrow): String = when (trend) {
        TrendArrow.FALLING_FAST -> "DoubleDown"
        TrendArrow.FALLING -> "FortyFiveDown"
        TrendArrow.FLAT -> "Flat"
        TrendArrow.RISING -> "FortyFiveUp"
        TrendArrow.RISING_FAST -> "DoubleUp"
        TrendArrow.UNKNOWN -> "NONE"
    }

    private fun parseQuery(raw: String): Map<String, String> {
        if (raw.isEmpty()) return emptyMap()
        val map = HashMap<String, String>()
        for (pair in raw.split("&")) {
            val key = pair.substringBefore("=")
            val value = pair.substringAfter("=", "")
            try {
                map[URLDecoder.decode(key, "UTF-8")] = URLDecoder.decode(value, "UTF-8")
            } catch (e: Exception) {
                // Ignore malformed pair
            }
        }
        return map
    }

    private fun write(socket: Socket, status: Int, body: String) {
        val reason = when (status) {
            200 -> "OK"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            404 -> "Not Found"
            else -> "Method Not Allowed"
        }
        val bytes = body.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 $status $reason\r\n" +
            "Content-Type: application/json; charset=utf-8\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            "Connection: close\r\n\r\n"
        val out = socket.getOutputStream()
        out.write(header.toByteArray(Charsets.UTF_8))
        out.write(bytes)
        out.flush()
    }

    companion object {
        private const val TAG = "LanServer"
        const val DEFAULT_PORT = 17581
        private const val DEFAULT_COUNT = 24
        private const val MAX_COUNT = 400

        /** Loopback, private (192.168/10/172.16-31), link-local and Tailscale-style (100.64/10) addresses only. */
        fun isLocalClient(address: InetAddress?): Boolean {
            if (address == null) return false
            if (address.isLoopbackAddress || address.isSiteLocalAddress || address.isLinkLocalAddress) return true
            val b = address.address
            return b.size == 4 && (b[0].toInt() and 0xFF) == 100 && (b[1].toInt() and 0xFF) in 64..127
        }
    }
}
