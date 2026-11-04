package com.example.data.broadcast

import android.os.Bundle
import com.example.core.common.GlucoseUtils
import com.example.core.model.TrendArrow
import kotlin.math.roundToInt

data class BroadcastReading(
    val valueMgDl: Int,
    val timeMs: Long,
    val trend: TrendArrow,
    val origin: String
)

/**
 * Understands the glucose broadcasts sent by other apps on the same phone/tablet:
 *  - xDrip+ and Juggluco ("xDrip+ broadcast") -> com.eveningoutpost.dexdrip.BgEstimate
 *  - Diabox                                  -> com.outshineiot.diabox.BgEstimate
 *  - Juggluco ("Glucodata broadcast")        -> glucodata.Minute
 *  - Dexcom BYODA                            -> com.dexcom.cgm.EXTERNAL_BROADCAST / com.dexcom.g7.EXTERNAL_BROADCAST
 *
 * Every value is read defensively (numbers may arrive as Int, Long, Float, Double or text).
 */
object GlucoseBroadcastParser {

    const val ACTION_XDRIP = "com.eveningoutpost.dexdrip.BgEstimate"
    const val ACTION_DIABOX = "com.outshineiot.diabox.BgEstimate"
    const val ACTION_JUGGLUCO = "glucodata.Minute"
    const val ACTION_DEXCOM_CGM = "com.dexcom.cgm.EXTERNAL_BROADCAST"
    const val ACTION_DEXCOM_G7 = "com.dexcom.g7.EXTERNAL_BROADCAST"

    val ALL_ACTIONS = listOf(ACTION_XDRIP, ACTION_DIABOX, ACTION_JUGGLUCO, ACTION_DEXCOM_CGM, ACTION_DEXCOM_G7)

    private const val XDRIP_PREFIX = "com.eveningoutpost.dexdrip.Extras."
    private const val MGDL_PER_MMOL = 18.0182

    fun originName(action: String?): String = when (action) {
        ACTION_XDRIP -> "xDrip+ / Juggluco"
        ACTION_DIABOX -> "Diabox"
        ACTION_JUGGLUCO -> "Juggluco"
        ACTION_DEXCOM_CGM, ACTION_DEXCOM_G7 -> "Dexcom (BYODA)"
        else -> action ?: "desconocido"
    }

    private fun Bundle.number(key: String): Double? {
        @Suppress("DEPRECATION")
        val value = get(key)
        return when (value) {
            is Number -> value.toDouble()
            is String -> value.trim().replace(',', '.').toDoubleOrNull()
            else -> null
        }
    }

    private fun Bundle.text(key: String): String? {
        @Suppress("DEPRECATION")
        val value = get(key)
        return value?.toString()
    }

    fun parse(action: String?, extras: Bundle?, nowMs: Long): BroadcastReading? {
        if (action == null || extras == null) return null
        return when (action) {
            ACTION_XDRIP, ACTION_DIABOX -> parseXdrip(action, extras, nowMs)
            ACTION_JUGGLUCO -> parseJuggluco(extras, nowMs)
            ACTION_DEXCOM_CGM, ACTION_DEXCOM_G7 -> parseDexcom(action, extras, nowMs)
            else -> null
        }
    }

    private fun parseXdrip(action: String, extras: Bundle, nowMs: Long): BroadcastReading? {
        val value = extras.number(XDRIP_PREFIX + "BgEstimate") ?: return null
        val mgdl = validMgDl(value) ?: return null
        val time = sanitizeTime(extras.number(XDRIP_PREFIX + "Time")?.toLong(), nowMs)

        val slopeName = extras.text(XDRIP_PREFIX + "BgSlopeName")
        var trend = TrendArrow.fromNightscout(slopeName)
        if (trend == TrendArrow.UNKNOWN) {
            // BgSlope is mg/dL per millisecond
            val slopePerMs = extras.number(XDRIP_PREFIX + "BgSlope")
            if (slopePerMs != null) trend = trendFromRate(slopePerMs * 60_000.0)
        }
        return BroadcastReading(mgdl, time, trend, originName(action))
    }

    private fun parseJuggluco(extras: Bundle, nowMs: Long): BroadcastReading? {
        val mgdlDirect = extras.number("glucodata.Minute.mgdl")
        val rawMgDl: Double = if (mgdlDirect != null) {
            mgdlDirect
        } else {
            // "glucose" is in the unit chosen in Juggluco: small numbers are mmol/L
            val custom = extras.number("glucodata.Minute.glucose") ?: return null
            if (custom < 30.0) custom * MGDL_PER_MMOL else custom
        }
        val mgdl = validMgDl(rawMgDl) ?: return null
        val time = sanitizeTime(extras.number("glucodata.Minute.Time")?.toLong(), nowMs)
        val rate = extras.number("glucodata.Minute.Rate")
        val trend = if (rate != null) trendFromRate(rate) else TrendArrow.UNKNOWN
        return BroadcastReading(mgdl, time, trend, originName(ACTION_JUGGLUCO))
    }

    private fun parseDexcom(action: String, extras: Bundle, nowMs: Long): BroadcastReading? {
        var value = extras.number("glucoseValue")
        if (value == null) {
            // Fall back to any numeric extra whose name mentions glucose
            for (key in extras.keySet()) {
                if (key.contains("glucose", ignoreCase = true) && !key.contains("unit", ignoreCase = true)) {
                    value = extras.number(key)
                    if (value != null) break
                }
            }
        }
        val mgdl = validMgDl(value ?: return null) ?: return null

        var timestamp = extras.number("timestamp")?.toLong()
        if (timestamp != null && timestamp in 1..99_999_999_999L) timestamp *= 1000L // seconds -> ms
        val time = sanitizeTime(timestamp, nowMs)

        val arrowText = extras.text("trendArrow")
        var trend = TrendArrow.fromNightscout(arrowText)
        if (trend == TrendArrow.UNKNOWN) {
            trend = when (arrowText?.trim()?.toIntOrNull()) {
                1, 2 -> TrendArrow.RISING_FAST
                3 -> TrendArrow.RISING
                4 -> TrendArrow.FLAT
                5 -> TrendArrow.FALLING
                6, 7 -> TrendArrow.FALLING_FAST
                else -> TrendArrow.UNKNOWN
            }
        }
        return BroadcastReading(mgdl, time, trend, originName(action))
    }

    /** Rate of change in mg/dL per minute -> arrow. */
    fun trendFromRate(mgdlPerMinute: Double): TrendArrow = when {
        mgdlPerMinute >= 2.0 -> TrendArrow.RISING_FAST
        mgdlPerMinute >= 1.0 -> TrendArrow.RISING
        mgdlPerMinute > -1.0 -> TrendArrow.FLAT
        mgdlPerMinute > -2.0 -> TrendArrow.FALLING
        else -> TrendArrow.FALLING_FAST
    }

    private fun validMgDl(value: Double): Int? {
        val rounded = value.roundToInt()
        return if (GlucoseUtils.isValidGlucose(rounded)) rounded else null
    }

    /** A missing, zero or far-future time is replaced by "now" so the reading is never shown as old or from the future. */
    private fun sanitizeTime(timeMs: Long?, nowMs: Long): Long {
        if (timeMs == null || timeMs <= 0L) return nowMs
        if (timeMs > nowMs + 5 * 60_000L) return nowMs
        return timeMs
    }
}
