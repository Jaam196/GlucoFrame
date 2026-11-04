package com.example

import android.graphics.Bitmap
import android.graphics.Color
import com.example.core.common.ColorAnalysisEngine
import com.example.core.database.background.BackgroundImageEntity
import com.example.core.model.FreshnessState
import com.example.core.model.GlucoseZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackgroundSystemTest {

    @Test
    fun testPureRedBitmapAnalysis() {
        val bitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED) // Hue = 0°

        val analysis = ColorAnalysisEngine.analyzeBitmap(bitmap)
        assertEquals("Pure red should be classified as rojo", "rojo", analysis.primaryClassification)
        assertTrue("Primary quota should be ~0.50 due to 3-bin smoothing kernel", analysis.primaryHueQuota >= 0.45f)
        assertTrue("Neutral percentage should be low", analysis.neutralQuota < 0.05f)

        // Matching with red target hex (#FF0000) vs green target hex (#00FF00)
        val redScore = ColorAnalysisEngine.calculateMatchScore(
            histogram = analysis.histogram,
            neutralQuota = analysis.neutralQuota,
            averageBrightness = analysis.averageBrightness,
            targetHex = "#FF0000"
        )
        val greenScore = ColorAnalysisEngine.calculateMatchScore(
            histogram = analysis.histogram,
            neutralQuota = analysis.neutralQuota,
            averageBrightness = analysis.averageBrightness,
            targetHex = "#00FF00"
        )

        assertTrue("Red image should score higher for red target than green target", redScore > greenScore)
        assertTrue("Red target score should be significant (> 0.5)", redScore > 0.5f)
        assertEquals("Green target score on pure red image should be 0", 0f, greenScore, 0.001f)
    }

    @Test
    fun testPureGreenBitmapAnalysis() {
        val bitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.GREEN) // Hue = 120°

        val analysis = ColorAnalysisEngine.analyzeBitmap(bitmap)
        assertEquals("Pure green should be classified as verde", "verde", analysis.primaryClassification)
        assertTrue("Green quota should be ~0.50 due to 3-bin smoothing", analysis.primaryHueQuota >= 0.45f)

        val greenScore = ColorAnalysisEngine.calculateMatchScore(
            histogram = analysis.histogram,
            neutralQuota = analysis.neutralQuota,
            averageBrightness = analysis.averageBrightness,
            targetHex = "#00FF00"
        )
        assertTrue("Green score should be high (> 0.5)", greenScore > 0.5f)
    }

    @Test
    fun testPureYellowBitmapAnalysis() {
        val bitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.YELLOW) // Hue = 60°

        val analysis = ColorAnalysisEngine.analyzeBitmap(bitmap)
        assertEquals("Pure yellow should be classified as amarillo", "amarillo", analysis.primaryClassification)

        val yellowScore = ColorAnalysisEngine.calculateMatchScore(
            histogram = analysis.histogram,
            neutralQuota = analysis.neutralQuota,
            averageBrightness = analysis.averageBrightness,
            targetHex = "#FFFF00"
        )
        assertTrue("Yellow score should be high (> 0.5)", yellowScore > 0.5f)
    }

    @Test
    fun testTwoColorBitmapDominance() {
        // 70% blue, 30% orange
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        for (y in 0 until 100) {
            for (x in 0 until 100) {
                if (y < 70) {
                    bitmap.setPixel(x, y, Color.BLUE) // 240°
                } else {
                    bitmap.setPixel(x, y, Color.rgb(255, 128, 0)) // ~30° Orange
                }
            }
        }

        val analysis = ColorAnalysisEngine.analyzeBitmap(bitmap)
        assertEquals("Primary color should be azul", "azul", analysis.primaryClassification)
        assertEquals("Secondary color should be naranja", "naranja", analysis.secondaryClassification)
        assertTrue("Blue peak in smoothed histogram should be ~0.35 (70% * 0.5 kernel)", analysis.primaryHueQuota in 0.30f..0.40f)
        assertTrue("Orange peak in smoothed histogram should be ~0.15 (30% * 0.5 kernel)", analysis.secondaryHueQuota in 0.10f..0.20f)
    }

    @Test
    fun testCandidateSelectionAvoidsConsecutiveRepeat() {
        // Two entities matching the same green color
        val greenBitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        greenBitmap.eraseColor(Color.GREEN)
        val analysis = ColorAnalysisEngine.analyzeBitmap(greenBitmap)

        val entity1 = BackgroundImageEntity(
            uriString = "uri://image1",
            sourceType = "PRESET",
            category = "abstractos",
            displayName = "Green 1",
            lastModified = 1000L,
            fileSize = 1024L,
            histogramCsv = analysis.toHistogramCsv(),
            primaryClassification = analysis.primaryClassification,
            secondaryClassification = analysis.secondaryClassification,
            primaryHueQuota = analysis.primaryHueQuota,
            secondaryHueQuota = analysis.secondaryHueQuota,
            neutralQuota = analysis.neutralQuota,
            averageBrightness = analysis.averageBrightness
        )

        val entity2 = BackgroundImageEntity(
            uriString = "uri://image2",
            sourceType = "PRESET",
            category = "abstractos",
            displayName = "Green 2",
            lastModified = 1000L,
            fileSize = 1024L,
            histogramCsv = analysis.toHistogramCsv(),
            primaryClassification = analysis.primaryClassification,
            secondaryClassification = analysis.secondaryClassification,
            primaryHueQuota = analysis.primaryHueQuota,
            secondaryHueQuota = analysis.secondaryHueQuota,
            neutralQuota = analysis.neutralQuota,
            averageBrightness = analysis.averageBrightness
        )

        val candidates = listOf(entity1, entity2)

        // When lastUri was entity1, selection should prioritize entity2 to avoid immediate repeat
        val picked = ColorAnalysisEngine.selectBestMatchingImage(candidates, "#00FF00", "uri://image1")
        assertNotNull(picked)
        assertEquals("Should pick entity2 when entity1 was lastUri", "uri://image2", picked?.uriString)
    }

    @Test
    fun testZoneDebounceLogic() {
        var currentZone: GlucoseZone? = GlucoseZone.IN_RANGE
        var pendingZone: GlucoseZone? = null
        var pendingCount = 0

        fun onNewReading(newZone: GlucoseZone): GlucoseZone? {
            if (newZone == GlucoseZone.URGENT_LOW) {
                // Immediate transition
                currentZone = newZone
                pendingZone = null
                pendingCount = 0
                return currentZone
            }

            if (newZone == currentZone) {
                pendingZone = null
                pendingCount = 0
                return currentZone
            }

            if (newZone == pendingZone) {
                pendingCount++
                if (pendingCount >= 2) {
                    currentZone = newZone
                    pendingZone = null
                    pendingCount = 0
                }
            } else {
                pendingZone = newZone
                pendingCount = 1
            }
            return currentZone
        }

        // Initially IN_RANGE
        assertEquals(GlucoseZone.IN_RANGE, currentZone)

        // First HIGH reading: should NOT change active zone yet (debounce requires 2 consecutive)
        var result = onNewReading(GlucoseZone.HIGH)
        assertEquals(GlucoseZone.IN_RANGE, result)

        // Second HIGH reading: now changes active zone to HIGH
        result = onNewReading(GlucoseZone.HIGH)
        assertEquals(GlucoseZone.HIGH, result)

        // Transition to URGENT_LOW: must change immediately on first reading!
        result = onNewReading(GlucoseZone.URGENT_LOW)
        assertEquals(GlucoseZone.URGENT_LOW, result)
    }

    @Test
    fun testFreshnessGuardFallback() {
        fun shouldShowNeutralBackground(freshness: FreshnessState): Boolean {
            return freshness != FreshnessState.ACTUAL
        }

        assertFalse(shouldShowNeutralBackground(FreshnessState.ACTUAL))
        assertTrue(shouldShowNeutralBackground(FreshnessState.RETRASADA))
        assertTrue(shouldShowNeutralBackground(FreshnessState.OBSOLETA))
        assertTrue(shouldShowNeutralBackground(FreshnessState.SIN_DATOS))
    }
}
