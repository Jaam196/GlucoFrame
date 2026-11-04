package com.example.core.model

import com.example.core.common.GlucoseUtils
import com.example.core.designsystem.GlucoseColors

data class GlucoseSurfaceState(
    val valueText: String,
    val trendSymbol: String,
    val zone: GlucoseZone,
    val freshness: FreshnessState,
    val minutesAgo: Long,
    val ageText: String,
    val unit: GlucoseUnit,
    val title: String,
    val subtitle: String,
    val isTextDark: Boolean,
    val reading: GlucoseReading?,
    /** Change since the previous reading, e.g. "+4" or "-7"; empty when unknown or too old to compare. */
    val deltaText: String = "",
    /** Speed of change in mg/dL per minute (from the previous reading); null when it cannot be known. */
    val ratePerMinute: Float? = null
) {
    companion object {
        fun from(
            reading: GlucoseReading?,
            thresholds: ThresholdSettings,
            unit: GlucoseUnit,
            nowMs: Long = System.currentTimeMillis(),
            previousReading: GlucoseReading? = null
        ): GlucoseSurfaceState {
            val freshness = GlucoseUtils.computeFreshness(reading, nowMs, thresholds)
            val zone = reading?.let { thresholds.zoneFor(it.valueMgDl) } ?: GlucoseZone.IN_RANGE
            val minutesAgo = if (reading != null) {
                ((nowMs - reading.measuredAtUtcMs) / 60_000L).coerceAtLeast(0L)
            } else {
                0L
            }
            val ageText = if (reading != null) {
                GlucoseUtils.formatAge(reading.measuredAtUtcMs, nowMs)
            } else {
                ""
            }

            // Delta only when the previous reading is a real neighbour (at most 15 minutes earlier)
            val deltaText = if (reading != null && previousReading != null &&
                previousReading.measuredAtUtcMs < reading.measuredAtUtcMs &&
                reading.measuredAtUtcMs - previousReading.measuredAtUtcMs <= 15 * 60_000L
            ) {
                GlucoseUtils.formatDelta(reading.valueMgDl - previousReading.valueMgDl, unit)
            } else {
                ""
            }

            val ratePerMinute: Float? = if (reading != null && previousReading != null &&
                previousReading.measuredAtUtcMs < reading.measuredAtUtcMs
            ) {
                val minutes = (reading.measuredAtUtcMs - previousReading.measuredAtUtcMs) / 60_000f
                // Only meaningful between 30 s and 6 min apart
                if (minutes >= 0.5f && minutes <= 6f) (reading.valueMgDl - previousReading.valueMgDl) / minutes else null
            } else {
                null
            }

            return when (freshness) {
                FreshnessState.ACTUAL -> {
                    val value = GlucoseUtils.formatValue(reading!!.valueMgDl, unit)
                    val symbol = reading.trend.symbol
                    val isDark = GlucoseColors.prefersDarkText(zone)
                    GlucoseSurfaceState(
                        valueText = value,
                        trendSymbol = symbol,
                        zone = zone,
                        freshness = freshness,
                        minutesAgo = minutesAgo,
                        ageText = ageText,
                        unit = unit,
                        title = "$value $symbol".trim(),
                        subtitle = "${zone.labelEs} • $ageText",
                        isTextDark = isDark,
                        reading = reading,
                        deltaText = deltaText,
                        ratePerMinute = ratePerMinute
                    )
                }
                FreshnessState.RETRASADA -> {
                    val value = GlucoseUtils.formatValue(reading!!.valueMgDl, unit)
                    val symbol = reading.trend.symbol
                    val isDark = GlucoseColors.prefersDarkText(zone)
                    GlucoseSurfaceState(
                        valueText = value,
                        trendSymbol = symbol,
                        zone = zone,
                        freshness = freshness,
                        minutesAgo = minutesAgo,
                        ageText = ageText,
                        unit = unit,
                        title = "$value $symbol ($ageText)".trim(),
                        subtitle = "${zone.labelEs} • $ageText",
                        isTextDark = isDark,
                        reading = reading,
                        deltaText = deltaText,
                        ratePerMinute = ratePerMinute
                    )
                }
                FreshnessState.OBSOLETA, FreshnessState.SIN_DATOS -> {
                    GlucoseSurfaceState(
                        valueText = "--",
                        trendSymbol = "",
                        zone = zone,
                        freshness = freshness,
                        minutesAgo = minutesAgo,
                        ageText = ageText,
                        unit = unit,
                        title = "--",
                        subtitle = "Sin datos actuales",
                        isTextDark = false, // White text on gray background
                        reading = reading
                    )
                }
            }
        }
    }
}
