package com.example.core.common

import com.example.core.model.FreshnessState
import com.example.core.model.GlucoseReading
import com.example.core.model.GlucoseUnit
import com.example.core.model.ThresholdSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToInt

object GlucoseUtils {
    const val MMOL_FACTOR = 18.0182

    fun mgDlToMmol(mgDl: Int): Double {
        return mgDl / MMOL_FACTOR
    }

    fun mmolToMgDl(mmol: Double): Int {
        return (mmol * MMOL_FACTOR).roundToInt()
    }

    fun formatValue(mgDl: Int, unit: GlucoseUnit): String {
        return when (unit) {
            GlucoseUnit.MG_DL -> mgDl.toString()
            GlucoseUnit.MMOL_L -> String.format(Locale.US, "%.1f", mgDlToMmol(mgDl))
        }
    }

    fun formatDelta(deltaMgDl: Int, unit: GlucoseUnit): String {
        val sign = if (deltaMgDl > 0) "+" else ""
        return when (unit) {
            GlucoseUnit.MG_DL -> "$sign$deltaMgDl"
            GlucoseUnit.MMOL_L -> String.format(Locale.US, "%s%.1f", sign, deltaMgDl / MMOL_FACTOR)
        }
    }

    /**
     * Parse FactoryTimestamp (M/d/y h:m:s a in Locale.ENGLISH, UTC).
     * Fallback to local Timestamp if UTC parsing fails.
     */
    fun parseFactoryTimestampUtc(timestampStr: String?): Long {
        if (timestampStr.isNullOrBlank()) return 0L
        return try {
            val format = SimpleDateFormat("M/d/y h:m:s a", Locale.ENGLISH)
            format.timeZone = TimeZone.getTimeZone("UTC")
            val date = format.parse(timestampStr.trim())
            date?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    fun parseLocalTimestamp(timestampStr: String?): Long {
        if (timestampStr.isNullOrBlank()) return 0L
        return try {
            val format = SimpleDateFormat("M/d/y h:m:s a", Locale.ENGLISH)
            val date = format.parse(timestampStr.trim())
            date?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Parses HTTP Date header: "EEE, dd MMM yyyy HH:mm:ss z" in Locale.US, GMT
     */
    fun parseHttpDateHeader(dateHeader: String?): Long? {
        if (dateHeader.isNullOrBlank()) return null
        return try {
            val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
            format.timeZone = TimeZone.getTimeZone("GMT")
            format.parse(dateHeader.trim())?.time
        } catch (e: Exception) {
            null
        }
    }

    fun isValidGlucose(valueMgDl: Int): Boolean {
        return valueMgDl in 20..600
    }

    /**
     * Compute freshness state according to Section 5:
     * age = now - measuredAtUtcMs of the last reading (never from download time)
     */
    fun computeFreshness(
        reading: GlucoseReading?,
        nowMs: Long,
        thresholds: ThresholdSettings
    ): FreshnessState {
        if (reading == null) return FreshnessState.SIN_DATOS
        val ageMs = nowMs - reading.measuredAtUtcMs
        val ageMinutes = ageMs / 60_000.0
        val effectiveObsoleteMinutes = thresholds.obsoleteMinutes.coerceAtMost(15)

        return when {
            ageMinutes < 0 -> FreshnessState.ACTUAL // clock skew tolerant
            ageMinutes <= thresholds.delayedMinutes -> FreshnessState.ACTUAL
            ageMinutes <= effectiveObsoleteMinutes -> FreshnessState.RETRASADA
            else -> FreshnessState.OBSOLETA
        }
    }

    /**
     * Age in minutes formatted: "hace X min" or "hace X s"
     */
    fun formatAge(measuredAtUtcMs: Long, nowMs: Long): String {
        val ageSeconds = ((nowMs - measuredAtUtcMs) / 1000).coerceAtLeast(0)
        val minutes = ageSeconds / 60
        return when {
            minutes < 1 -> "hace ${ageSeconds} s"
            minutes < 60 -> "hace $minutes min"
            else -> {
                val hours = minutes / 60
                val remMin = minutes % 60
                "hace ${hours}h ${remMin}m"
            }
        }
    }

    fun formatTime(timeMs: Long): String {
        val format = SimpleDateFormat("HH:mm", Locale.getDefault())
        return format.format(Date(timeMs))
    }

    fun formatFullDateTime(timeMs: Long): String {
        val format = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        return format.format(Date(timeMs))
    }

    /**
     * Detects gap between two consecutive readings (sorted chronologically).
     * Rule: gap when difference > 3 * medianInterval of the window (minimum 10 minutes).
     */
    fun isGap(prevTimeMs: Long, currentTimeMs: Long, medianIntervalMs: Long): Boolean {
        val diffMs = currentTimeMs - prevTimeMs
        val minGapMs = 10 * 60 * 1000L // 10 min
        val dynamicGapMs = (3 * medianIntervalMs).coerceAtLeast(minGapMs)
        return diffMs > dynamicGapMs
    }

    /**
     * Calculate median interval in ms between consecutive readings in list
     */
    fun calculateMedianInterval(readings: List<GlucoseReading>): Long {
        if (readings.size < 2) return 5 * 60 * 1000L // default 5 min
        val intervals = mutableListOf<Long>()
        for (i in 1 until readings.size) {
            val diff = abs(readings[i].measuredAtUtcMs - readings[i - 1].measuredAtUtcMs)
            if (diff > 0) intervals.add(diff)
        }
        if (intervals.isEmpty()) return 5 * 60 * 1000L
        intervals.sort()
        val mid = intervals.size / 2
        return if (intervals.size % 2 == 1) {
            intervals[mid]
        } else {
            (intervals[mid - 1] + intervals[mid]) / 2
        }
    }
}
