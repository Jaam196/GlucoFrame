package com.example

import com.example.core.model.FreshnessState
import com.example.core.model.GlucoseReading
import com.example.core.model.GlucoseSurfaceState
import com.example.core.model.GlucoseUnit
import com.example.core.model.GlucoseZone
import com.example.core.model.ThresholdSettings
import com.example.core.model.TrendArrow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlucoseSurfaceStateTest {

    private val defaultThresholds = ThresholdSettings(
        urgentLowMgDl = 54,
        lowMgDl = 70,
        targetReferenceMgDl = 100,
        highMgDl = 180,
        veryHighMgDl = 250,
        delayedMinutes = 5,
        obsoleteMinutes = 10
    )

    @Test
    fun testActualReadingFormattingMgDl() {
        val now = 1700000000000L
        val reading = GlucoseReading(
            sourceId = "LIBRELINKUP",
            measuredAtUtcMs = now - (2 * 60_000L), // 2 min ago
            valueMgDl = 112,
            trend = TrendArrow.RISING
        )

        val state = GlucoseSurfaceState.from(
            reading = reading,
            thresholds = defaultThresholds,
            unit = GlucoseUnit.MG_DL,
            nowMs = now
        )

        assertEquals(FreshnessState.ACTUAL, state.freshness)
        assertEquals("112", state.valueText)
        assertEquals("↑", state.trendSymbol)
        assertEquals("112 ↑", state.title)
        assertEquals(GlucoseZone.IN_RANGE, state.zone)
        assertTrue(state.isTextDark)
        assertEquals(2L, state.minutesAgo)
        assertTrue(state.subtitle.contains("En rango"))
    }

    @Test
    fun testActualReadingFormattingMmolL() {
        val now = 1700000000000L
        val reading = GlucoseReading(
            sourceId = "LIBRELINKUP",
            measuredAtUtcMs = now - (1 * 60_000L),
            valueMgDl = 112,
            trend = TrendArrow.FLAT
        )

        val state = GlucoseSurfaceState.from(
            reading = reading,
            thresholds = defaultThresholds,
            unit = GlucoseUnit.MMOL_L,
            nowMs = now
        )

        assertEquals("6.2", state.valueText)
        assertEquals("→", state.trendSymbol)
        assertEquals("6.2 →", state.title)
        assertEquals(GlucoseUnit.MMOL_L, state.unit)
    }

    @Test
    fun testAllGlucoseZonesAndContrast() {
        val now = 1700000000000L

        // Urgent Low (< 54) -> White text on red
        val rUrgent = GlucoseReading(sourceId = "test", measuredAtUtcMs = now, valueMgDl = 50, trend = TrendArrow.FALLING_FAST)
        val sUrgent = GlucoseSurfaceState.from(rUrgent, defaultThresholds, GlucoseUnit.MG_DL, now)
        assertEquals(GlucoseZone.URGENT_LOW, sUrgent.zone)
        assertFalse(sUrgent.isTextDark)

        // Low (54..69) -> Dark text on orange
        val rLow = GlucoseReading(sourceId = "test", measuredAtUtcMs = now, valueMgDl = 65, trend = TrendArrow.FALLING)
        val sLow = GlucoseSurfaceState.from(rLow, defaultThresholds, GlucoseUnit.MG_DL, now)
        assertEquals(GlucoseZone.LOW, sLow.zone)
        assertTrue(sLow.isTextDark)

        // In Range (70..180) -> Dark text on green
        val rInRange = GlucoseReading(sourceId = "test", measuredAtUtcMs = now, valueMgDl = 120, trend = TrendArrow.FLAT)
        val sInRange = GlucoseSurfaceState.from(rInRange, defaultThresholds, GlucoseUnit.MG_DL, now)
        assertEquals(GlucoseZone.IN_RANGE, sInRange.zone)
        assertTrue(sInRange.isTextDark)

        // High (181..250) -> Dark text on yellow
        val rHigh = GlucoseReading(sourceId = "test", measuredAtUtcMs = now, valueMgDl = 210, trend = TrendArrow.RISING)
        val sHigh = GlucoseSurfaceState.from(rHigh, defaultThresholds, GlucoseUnit.MG_DL, now)
        assertEquals(GlucoseZone.HIGH, sHigh.zone)
        assertTrue(sHigh.isTextDark)

        // Very High (> 250) -> White text on red
        val rVeryHigh = GlucoseReading(sourceId = "test", measuredAtUtcMs = now, valueMgDl = 280, trend = TrendArrow.RISING_FAST)
        val sVeryHigh = GlucoseSurfaceState.from(rVeryHigh, defaultThresholds, GlucoseUnit.MG_DL, now)
        assertEquals(GlucoseZone.VERY_HIGH, sVeryHigh.zone)
        assertFalse(sVeryHigh.isTextDark)
    }

    @Test
    fun testAllTrendArrows() {
        val now = 1700000000000L
        for (arrow in TrendArrow.values()) {
            val reading = GlucoseReading(sourceId = "test", measuredAtUtcMs = now, valueMgDl = 100, trend = arrow)
            val state = GlucoseSurfaceState.from(reading, defaultThresholds, GlucoseUnit.MG_DL, now)
            assertEquals(arrow.symbol, state.trendSymbol)
        }
    }

    @Test
    fun testRetrasadaSafetyRule() {
        val now = 1700000000000L
        // 7 minutes ago: between delayed (5) and obsolete (10)
        val reading = GlucoseReading(
            sourceId = "LIBRELINKUP",
            measuredAtUtcMs = now - (7 * 60_000L),
            valueMgDl = 140,
            trend = TrendArrow.FLAT
        )

        val state = GlucoseSurfaceState.from(
            reading = reading,
            thresholds = defaultThresholds,
            unit = GlucoseUnit.MG_DL,
            nowMs = now
        )

        assertEquals(FreshnessState.RETRASADA, state.freshness)
        assertEquals("140", state.valueText)
        assertEquals("→", state.trendSymbol)
        // Safety: must include age in title/presentation
        assertTrue("Title should mention age for delayed reading", state.title.contains("hace 7 min"))
        assertTrue("Subtitle should mention age for delayed reading", state.subtitle.contains("hace 7 min"))
    }

    @Test
    fun testObsoletaSafetyRuleNeverShowsOldValue() {
        val now = 1700000000000L
        // 16 minutes ago: exceeds obsolete threshold (capped at 15 min max)
        val reading = GlucoseReading(
            sourceId = "LIBRELINKUP",
            measuredAtUtcMs = now - (16 * 60_000L),
            valueMgDl = 140,
            trend = TrendArrow.FLAT
        )

        val state = GlucoseSurfaceState.from(
            reading = reading,
            thresholds = defaultThresholds,
            unit = GlucoseUnit.MG_DL,
            nowMs = now
        )

        assertEquals(FreshnessState.OBSOLETA, state.freshness)
        // Safety: MUST NEVER display old value as actual!
        assertEquals("--", state.valueText)
        assertEquals("", state.trendSymbol)
        assertEquals("--", state.title)
        assertEquals("Sin datos actuales", state.subtitle)
        assertFalse(state.isTextDark)
    }

    @Test
    fun testSinDatosSafetyRule() {
        val now = 1700000000000L
        val state = GlucoseSurfaceState.from(
            reading = null,
            thresholds = defaultThresholds,
            unit = GlucoseUnit.MG_DL,
            nowMs = now
        )

        assertEquals(FreshnessState.SIN_DATOS, state.freshness)
        assertEquals("--", state.valueText)
        assertEquals("", state.trendSymbol)
        assertEquals("--", state.title)
        assertEquals("Sin datos actuales", state.subtitle)
        assertFalse(state.isTextDark)
    }
}
