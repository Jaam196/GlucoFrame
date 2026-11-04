package com.example

import com.example.core.common.GlucoseUtils
import com.example.core.model.FreshnessState
import com.example.core.model.GlucoseReading
import com.example.core.model.GlucoseUnit
import com.example.core.model.ThresholdSettings
import com.example.core.model.TrendArrow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlucoseUtilsTest {

    @Test
    fun testMgDlToMmolConversion() {
        // 180 mg/dL / 18.0182 = ~9.9898 -> 10.0 mmol/L
        val mmol180 = GlucoseUtils.mgDlToMmol(180)
        assertEquals(9.99, mmol180, 0.05)

        val formatted180 = GlucoseUtils.formatValue(180, GlucoseUnit.MMOL_L)
        assertEquals("10.0", formatted180)

        // 90 mg/dL / 18.0182 = ~4.99 -> 5.0 mmol/L
        val formatted90 = GlucoseUtils.formatValue(90, GlucoseUnit.MMOL_L)
        assertEquals("5.0", formatted90)

        // mg/dL direct
        assertEquals("125", GlucoseUtils.formatValue(125, GlucoseUnit.MG_DL))
    }

    @Test
    fun testFactoryTimestampUtcParsing() {
        // "11/10/2023 3:45:16 PM" in UTC
        val parsedUtc = GlucoseUtils.parseFactoryTimestampUtc("11/10/2023 3:45:16 PM")
        assertTrue("Parsed UTC timestamp must be positive", parsedUtc > 0)

        // Invalid or null timestamp returns 0
        assertEquals(0L, GlucoseUtils.parseFactoryTimestampUtc(null))
        assertEquals(0L, GlucoseUtils.parseFactoryTimestampUtc(""))
    }

    @Test
    fun testFreshnessStateTransitions() {
        val thresholds = ThresholdSettings(delayedMinutes = 5, obsoleteMinutes = 10)
        val now = 1000000L

        // 1. Null reading -> SIN_DATOS
        assertEquals(FreshnessState.SIN_DATOS, GlucoseUtils.computeFreshness(null, now, thresholds))

        // 2. Reading measured 3 minutes ago -> ACTUAL (<= 5m)
        val reading3m = GlucoseReading(
            sourceId = "TEST",
            measuredAtUtcMs = now - (3 * 60 * 1000L),
            valueMgDl = 110,
            trend = TrendArrow.FLAT
        )
        assertEquals(FreshnessState.ACTUAL, GlucoseUtils.computeFreshness(reading3m, now, thresholds))

        // 3. Exactly 5 minutes ago -> ACTUAL (boundary)
        val reading5m = reading3m.copy(measuredAtUtcMs = now - (5 * 60 * 1000L))
        assertEquals(FreshnessState.ACTUAL, GlucoseUtils.computeFreshness(reading5m, now, thresholds))

        // 4. Reading measured 7 minutes ago -> RETRASADA (5-10m)
        val reading7m = reading3m.copy(measuredAtUtcMs = now - (7 * 60 * 1000L))
        assertEquals(FreshnessState.RETRASADA, GlucoseUtils.computeFreshness(reading7m, now, thresholds))

        // 5. Reading measured 12 minutes ago -> OBSOLETA (> 10m)
        val reading12m = reading3m.copy(measuredAtUtcMs = now - (12 * 60 * 1000L))
        assertEquals(FreshnessState.OBSOLETA, GlucoseUtils.computeFreshness(reading12m, now, thresholds))

        // 6. Max obsolete limit: Even if user configured obsoleteMinutes = 25, it must be capped at 15m
        val highThresholds = thresholds.copy(obsoleteMinutes = 25)
        val reading16m = reading3m.copy(measuredAtUtcMs = now - (16 * 60 * 1000L))
        assertEquals(FreshnessState.OBSOLETA, GlucoseUtils.computeFreshness(reading16m, now, highThresholds))
    }

    @Test
    fun testChartGapDetection() {
        val medianIntervalMs = 5 * 60 * 1000L // 5 min median
        val t0 = 1000000L

        // Normal 5m interval is not a gap
        assertFalse(GlucoseUtils.isGap(t0, t0 + (5 * 60 * 1000L), medianIntervalMs))

        // 8m interval is not a gap (< 15m)
        assertFalse(GlucoseUtils.isGap(t0, t0 + (8 * 60 * 1000L), medianIntervalMs))

        // 16m interval (> 3x median = 15m) IS a gap
        assertTrue(GlucoseUtils.isGap(t0, t0 + (16 * 60 * 1000L), medianIntervalMs))

        // Small median (1 min): minGapMs must be enforced (minimum 10m)
        val smallMedian = 1 * 60 * 1000L
        assertFalse("8m should not be a gap when minGap is 10m", GlucoseUtils.isGap(t0, t0 + (8 * 60 * 1000L), smallMedian))
        assertTrue("12m should be a gap", GlucoseUtils.isGap(t0, t0 + (12 * 60 * 1000L), smallMedian))
    }

    @Test
    fun testTrendMapping() {
        // LibreLink trends
        assertEquals(TrendArrow.FALLING_FAST, TrendArrow.fromLibre(1))
        assertEquals(TrendArrow.FALLING, TrendArrow.fromLibre(2))
        assertEquals(TrendArrow.FLAT, TrendArrow.fromLibre(3))
        assertEquals(TrendArrow.RISING, TrendArrow.fromLibre(4))
        assertEquals(TrendArrow.RISING_FAST, TrendArrow.fromLibre(5))
        assertEquals(TrendArrow.UNKNOWN, TrendArrow.fromLibre(99))

        // Nightscout trends
        assertEquals(TrendArrow.FALLING_FAST, TrendArrow.fromNightscout("DoubleDown"))
        assertEquals(TrendArrow.FALLING_FAST, TrendArrow.fromNightscout("SingleDown"))
        assertEquals(TrendArrow.FALLING, TrendArrow.fromNightscout("FortyFiveDown"))
        assertEquals(TrendArrow.FLAT, TrendArrow.fromNightscout("Flat"))
        assertEquals(TrendArrow.RISING, TrendArrow.fromNightscout("FortyFiveUp"))
        assertEquals(TrendArrow.RISING_FAST, TrendArrow.fromNightscout("SingleUp"))
        assertEquals(TrendArrow.RISING_FAST, TrendArrow.fromNightscout("DoubleUp"))
        assertEquals(TrendArrow.UNKNOWN, TrendArrow.fromNightscout("invalid"))
    }

    @Test
    fun testParseHttpDateHeader() {
        val dateStr = "Wed, 21 Oct 2026 07:28:00 GMT"
        val parsed = GlucoseUtils.parseHttpDateHeader(dateStr)
        assertTrue("Parsed date should be positive", parsed != null && parsed > 0L)

        // Invalid or null
        assertEquals(null, GlucoseUtils.parseHttpDateHeader(null))
        assertEquals(null, GlucoseUtils.parseHttpDateHeader("Invalid Date String"))
    }

    @Test
    fun testComputeFreshnessWithClockOffset() {
        val thresholds = ThresholdSettings(delayedMinutes = 5, obsoleteMinutes = 10)
        val serverTime = 1700000000000L
        val deviceClock = serverTime - (180 * 1000L) // Device clock is 3 minutes behind server
        val offsetMs = serverTime - deviceClock // +180_000 ms

        // Reading was measured 4 minutes ago relative to server
        val reading = GlucoseReading(
            sourceId = "TEST",
            measuredAtUtcMs = serverTime - (4 * 60 * 1000L),
            valueMgDl = 120,
            trend = TrendArrow.FLAT
        )

        // If computed using raw deviceClock without offset:
        // age = deviceClock - (serverTime - 4m) = (serverTime - 3m) - (serverTime - 4m) = +1 min (wrong age)
        // Now computed using adjusted now = deviceClock + offsetMs (= serverTime):
        val adjustedNow = deviceClock + offsetMs
        val freshness = GlucoseUtils.computeFreshness(reading, adjustedNow, thresholds)
        assertEquals(FreshnessState.ACTUAL, freshness)

        // If reading was measured 8 minutes ago relative to server:
        val reading8m = reading.copy(measuredAtUtcMs = serverTime - (8 * 60 * 1000L))
        val freshness8m = GlucoseUtils.computeFreshness(reading8m, adjustedNow, thresholds)
        assertEquals(FreshnessState.RETRASADA, freshness8m)

        // If reading was measured 12 minutes ago relative to server:
        val reading12m = reading.copy(measuredAtUtcMs = serverTime - (12 * 60 * 1000L))
        val freshness12m = GlucoseUtils.computeFreshness(reading12m, adjustedNow, thresholds)
        assertEquals(FreshnessState.OBSOLETA, freshness12m)
    }
}
