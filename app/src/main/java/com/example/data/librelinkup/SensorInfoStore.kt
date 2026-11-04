package com.example.data.librelinkup

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Data of the active sensor as reported by LibreLinkUp (`sensor`, `patientDevice` and `connection`).
 * The service does not report the sensor type name or its life span, only a type code; the life span
 * is chosen by the user (14 days by default).
 */
data class SensorInfo(
    val serial: String = "",
    val activatedAtSec: Long = 0L,      // "a": epoch seconds when the sensor was started
    val warmupMinutes: Int = 0,         // "w"
    val typeCode: Int = 0,              // "pt"
    val appVersion: String = "",        // patientDevice "v"
    val lowLimitMgDl: Int = 0,          // patientDevice "ll"
    val highLimitMgDl: Int = 0,         // patientDevice "hl"
    val targetLowMgDl: Int = 0,         // connection "targetLow"
    val targetHighMgDl: Int = 0,        // connection "targetHigh"
    val alarmsEnabled: Boolean = false, // patientDevice "alarms"
    val updatedAtMs: Long = 0L
) {
    val isKnown: Boolean get() = activatedAtSec > 0L

    /** Day of use (1 = first day) given the current time; 0 if unknown. */
    fun dayOfUse(nowMs: Long): Int {
        if (!isKnown) return 0
        val elapsed = nowMs - activatedAtSec * 1000L
        return if (elapsed < 0) 1 else (elapsed / 86_400_000L).toInt() + 1
    }

    fun endsAtMs(lifeDays: Int): Long = activatedAtSec * 1000L + lifeDays * 86_400_000L

    fun hoursLeft(nowMs: Long, lifeDays: Int): Long = (endsAtMs(lifeDays) - nowMs) / 3_600_000L
}

object SensorInfoStore {
    private const val PREFS = "sensor_info"
    private const val KEY_INFO = "info"
    private const val KEY_LIFE = "life_days"

    private val _info = MutableStateFlow<SensorInfo?>(null)
    val info: StateFlow<SensorInfo?> = _info.asStateFlow()

    private val _lifeDays = MutableStateFlow(14)
    val lifeDays: StateFlow<Int> = _lifeDays.asStateFlow()

    @Volatile
    private var loaded = false

    /** Loads the last saved values once (call from the Application and from the screens). */
    fun load(context: Context) {
        if (loaded) return
        loaded = true
        try {
            val sp = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            _lifeDays.value = sp.getInt(KEY_LIFE, 14).coerceIn(10, 20)
            val raw = sp.getString(KEY_INFO, null)
            if (!raw.isNullOrBlank() && _info.value == null) {
                _info.value = decode(JSONObject(raw))
            }
        } catch (e: Exception) {
            // A damaged file must never stop the app
        }
    }

    fun update(context: Context?, info: SensorInfo) {
        if (info == _info.value?.copy(updatedAtMs = info.updatedAtMs)) return
        _info.value = info
        if (context != null) {
            try {
                context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(KEY_INFO, encode(info).toString()).apply()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun setLifeDays(context: Context, days: Int) {
        _lifeDays.value = days.coerceIn(10, 20)
        try {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putInt(KEY_LIFE, _lifeDays.value).apply()
        } catch (e: Exception) {
            // ignore
        }
    }

    /** Builds the info from the `data` object of /connections (one item) or /graph responses. */
    fun parse(data: JSONObject, nowMs: Long): SensorInfo? {
        val connection = data.optJSONObject("connection") ?: data
        val active = data.optJSONArray("activeSensors")?.optJSONObject(0)
        val sensor = active?.optJSONObject("sensor") ?: connection.optJSONObject("sensor") ?: return null
        val device = active?.optJSONObject("device") ?: connection.optJSONObject("patientDevice")
        val activated = sensor.optLong("a", 0L)
        if (activated <= 0L) return null
        return SensorInfo(
            serial = sensor.optString("sn", ""),
            activatedAtSec = activated,
            warmupMinutes = sensor.optInt("w", 0),
            typeCode = sensor.optInt("pt", 0),
            appVersion = device?.optString("v", "") ?: "",
            lowLimitMgDl = device?.optInt("ll", 0) ?: 0,
            highLimitMgDl = device?.optInt("hl", 0) ?: 0,
            targetLowMgDl = connection.optInt("targetLow", 0),
            targetHighMgDl = connection.optInt("targetHigh", 0),
            alarmsEnabled = device?.optBoolean("alarms", false) ?: false,
            updatedAtMs = nowMs
        )
    }

    private fun encode(i: SensorInfo) = JSONObject().apply {
        put("serial", i.serial)
        put("a", i.activatedAtSec)
        put("w", i.warmupMinutes)
        put("pt", i.typeCode)
        put("v", i.appVersion)
        put("ll", i.lowLimitMgDl)
        put("hl", i.highLimitMgDl)
        put("tl", i.targetLowMgDl)
        put("th", i.targetHighMgDl)
        put("alarms", i.alarmsEnabled)
        put("updated", i.updatedAtMs)
    }

    private fun decode(o: JSONObject) = SensorInfo(
        serial = o.optString("serial", ""),
        activatedAtSec = o.optLong("a", 0L),
        warmupMinutes = o.optInt("w", 0),
        typeCode = o.optInt("pt", 0),
        appVersion = o.optString("v", ""),
        lowLimitMgDl = o.optInt("ll", 0),
        highLimitMgDl = o.optInt("hl", 0),
        targetLowMgDl = o.optInt("tl", 0),
        targetHighMgDl = o.optInt("th", 0),
        alarmsEnabled = o.optBoolean("alarms", false),
        updatedAtMs = o.optLong("updated", 0L)
    )
}
