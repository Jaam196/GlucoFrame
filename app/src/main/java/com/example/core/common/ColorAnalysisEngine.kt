package com.example.core.common

import android.graphics.Bitmap
import android.graphics.Color
import com.example.core.database.background.BackgroundImageEntity
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

data class ColorAnalysisResult(
    val primaryHue: Float,
    val primaryHueQuota: Float,
    val secondaryHue: Float,
    val secondaryHueQuota: Float,
    val neutralQuota: Float,
    val averageBrightness: Float,
    val primaryClassification: String,
    val secondaryClassification: String,
    val histogram: FloatArray // 36 bins
) {
    fun toHistogramCsv(): String {
        return histogram.joinToString(",") { String.format(java.util.Locale.US, "%.4f", it) }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ColorAnalysisResult
        return primaryHue == other.primaryHue &&
            primaryHueQuota == other.primaryHueQuota &&
            secondaryHue == other.secondaryHue &&
            secondaryHueQuota == other.secondaryHueQuota &&
            neutralQuota == other.neutralQuota &&
            averageBrightness == other.averageBrightness &&
            primaryClassification == other.primaryClassification &&
            secondaryClassification == other.secondaryClassification &&
            histogram.contentEquals(other.histogram)
    }

    override fun hashCode(): Int {
        var result = primaryHue.hashCode()
        result = 31 * result + primaryHueQuota.hashCode()
        result = 31 * result + secondaryHue.hashCode()
        result = 31 * result + secondaryHueQuota.hashCode()
        result = 31 * result + neutralQuota.hashCode()
        result = 31 * result + averageBrightness.hashCode()
        result = 31 * result + primaryClassification.hashCode()
        result = 31 * result + secondaryClassification.hashCode()
        result = 31 * result + histogram.contentHashCode()
        return result
    }
}

object ColorAnalysisEngine {

    const val NUM_BINS = 36 // 10 degrees each
    const val BIN_DEGREES = 10f

    /**
     * Analyzes pixel colors of a bitmap downscaled to ~64x64.
     * Computes 36-bin HSV histogram, neutral quota, average brightness, and hue classifications.
     */
    fun analyzeBitmap(bitmap: Bitmap): ColorAnalysisResult {
        // Ensure working on ~64x64 or smaller
        val scaled = if (bitmap.width > 96 || bitmap.height > 96) {
            val aspect = bitmap.width.toFloat() / bitmap.height.toFloat()
            val (targetW, targetH) = if (aspect >= 1f) {
                Pair(64, (64 / aspect).toInt().coerceAtLeast(1))
            } else {
                Pair((64 * aspect).toInt().coerceAtLeast(1), 64)
            }
            Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        } else {
            bitmap
        }

        val width = scaled.width
        val height = scaled.height
        val totalPixels = width * height
        if (totalPixels <= 0) {
            return defaultEmptyResult()
        }

        val pixels = IntArray(totalPixels)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)

        if (scaled !== bitmap) {
            scaled.recycle()
        }

        val rawBins = FloatArray(NUM_BINS)
        var totalNeutralWeight = 0f
        var totalChromaticWeight = 0f
        var totalLuminance = 0f
        val hsv = FloatArray(3)

        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            // Relative luminance (standard Rec. 601)
            val lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
            totalLuminance += lum

            Color.colorToHSV(pixel, hsv)
            val hue = (hsv[0] % 360f + 360f) % 360f
            val sat = hsv[1]
            val value = hsv[2]

            // If S < 0.15 or V < 0.15, classify as neutral (gray, black, white)
            if (sat < 0.15f || value < 0.15f) {
                totalNeutralWeight += 1f
            } else {
                val weight = sat * value
                val binIndex = ((hue / BIN_DEGREES).toInt()).coerceIn(0, NUM_BINS - 1)
                rawBins[binIndex] += weight
                totalChromaticWeight += weight
            }
        }

        val totalWeight = totalNeutralWeight + totalChromaticWeight
        val neutralQuota = if (totalWeight > 0f) totalNeutralWeight / totalWeight else 1f
        val averageBrightness = totalLuminance / totalPixels

        // Circular smoothing of histogram with kernel [0.25, 0.5, 0.25]
        val smoothedBins = FloatArray(NUM_BINS)
        if (totalWeight > 0f) {
            for (i in 0 until NUM_BINS) {
                val prev = rawBins[(i - 1 + NUM_BINS) % NUM_BINS]
                val curr = rawBins[i]
                val next = rawBins[(i + 1) % NUM_BINS]
                val smoothedVal = 0.25f * prev + 0.50f * curr + 0.25f * next
                smoothedBins[i] = smoothedVal / totalWeight
            }
        }

        // Find primary peak (bin with max weight)
        var maxBin = 0
        var maxVal = 0f
        for (i in 0 until NUM_BINS) {
            if (smoothedBins[i] > maxVal) {
                maxVal = smoothedBins[i]
                maxBin = i
            }
        }

        val primaryHue = maxBin * BIN_DEGREES + (BIN_DEGREES / 2f)
        val primaryQuota = maxVal

        // Find secondary peak separated by >= 3 bins (30 degrees)
        var secBin = maxBin
        var secVal = 0f
        for (i in 0 until NUM_BINS) {
            val dist = min(abs(i - maxBin), NUM_BINS - abs(i - maxBin))
            if (dist >= 3 && smoothedBins[i] > secVal) {
                secVal = smoothedBins[i]
                secBin = i
            }
        }
        val secondaryHue = secBin * BIN_DEGREES + (BIN_DEGREES / 2f)
        val secondaryQuota = secVal

        val primaryClass = classifyHue(primaryHue, neutralQuota)
        val secondaryClass = if (secondaryQuota >= 0.10f) classifyHue(secondaryHue, 0f) else ""

        return ColorAnalysisResult(
            primaryHue = primaryHue,
            primaryHueQuota = primaryQuota,
            secondaryHue = secondaryHue,
            secondaryHueQuota = secondaryQuota,
            neutralQuota = neutralQuota,
            averageBrightness = averageBrightness,
            primaryClassification = primaryClass,
            secondaryClassification = secondaryClass,
            histogram = smoothedBins
        )
    }

    /**
     * Maps hue angle (0-360) and neutral quota to name.
     */
    fun classifyHue(hue: Float, neutralQuota: Float): String {
        if (neutralQuota >= 0.55f) return "neutro"
        val h = (hue % 360f + 360f) % 360f
        return when {
            h in 345f..360f || h in 0f..15f -> "rojo"
            h in 15f..45f -> "naranja"
            h in 45f..70f -> "amarillo"
            h in 70f..165f -> "verde"
            h in 165f..195f -> "cian"
            h in 195f..255f -> "azul"
            h in 255f..290f -> "morado"
            h in 290f..345f -> "rosa"
            else -> "neutro"
        }
    }

    /**
     * Calculates compatibility score (0.0 to 1.0) of an analyzed image against any target HEX color.
     * Uses the 36-bin histogram for chromatic colors (pixels within <= 25 degrees)
     * and neutral quota & brightness for neutral target colors.
     */
    fun calculateMatchScore(
        histogram: FloatArray,
        neutralQuota: Float,
        averageBrightness: Float,
        targetHex: String
    ): Float {
        val targetColor = try {
            Color.parseColor(targetHex)
        } catch (e: Exception) {
            Color.GREEN
        }

        val targetHsv = FloatArray(3)
        Color.colorToHSV(targetColor, targetHsv)
        val targetHue = (targetHsv[0] % 360f + 360f) % 360f
        val targetSat = targetHsv[1]
        val targetVal = targetHsv[2]

        // If target color is neutral (gray/black/white)
        if (targetSat < 0.15f || targetVal < 0.15f) {
            val brightnessDiff = abs(averageBrightness - targetVal)
            return (neutralQuota * (1f - brightnessDiff)).coerceIn(0f, 1f)
        }

        // Chromatic target: Sum bins whose center is within <= 25 degrees of target hue
        var score = 0f
        for (i in 0 until min(histogram.size, NUM_BINS)) {
            val binCenter = i * BIN_DEGREES + (BIN_DEGREES / 2f)
            val diff = abs(binCenter - targetHue)
            val circDiff = min(diff, 360f - diff)
            if (circDiff <= 25f) {
                // Weight slightly higher for directly adjacent bins
                val weight = if (circDiff <= 10f) 1.0f else (1.0f - (circDiff - 10f) / 30f)
                score += histogram[i] * weight
            }
        }

        return score.coerceIn(0f, 1f)
    }

    /**
     * Selects best matching image for the target HEX color among candidates:
     * - Filters candidates meeting threshold (~0.25f, or best available).
     * - Takes top 30% matches.
     * - Picks randomly, avoiding consecutive repeat of lastUri if alternatives exist.
     */
    fun selectBestMatchingImage(
        candidates: List<BackgroundImageEntity>,
        targetHex: String,
        lastUri: String?
    ): BackgroundImageEntity? {
        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates.first()

        // Calculate scores
        val scored = candidates.map { entity ->
            val hist = entity.parseHistogram()
            val score = calculateMatchScore(
                histogram = hist,
                neutralQuota = entity.neutralQuota,
                averageBrightness = entity.averageBrightness,
                targetHex = targetHex
            )
            Pair(entity, score)
        }.sortedByDescending { it.second }

        val bestScore = scored.first().second
        val threshold = (bestScore * 0.70f).coerceAtLeast(0.20f)
        val viable = scored.filter { it.second >= threshold }

        // Top viable candidates: for small lists (<= 3) consider all viable, for larger lists top 30%
        val topCount = if (viable.size <= 3) viable.size else (viable.size * 0.30f).toInt().coerceAtLeast(2)
        val topTier = viable.take(topCount).map { it.first }

        // Avoid repeating lastUri if more than 1 option in top tier
        val filtered = if (topTier.size > 1 && lastUri != null) {
            topTier.filter { it.uriString != lastUri }.ifEmpty { topTier }
        } else {
            topTier
        }

        return filtered[Random.nextInt(filtered.size)]
    }

    private fun defaultEmptyResult(): ColorAnalysisResult {
        return ColorAnalysisResult(
            primaryHue = 0f,
            primaryHueQuota = 0f,
            secondaryHue = 0f,
            secondaryHueQuota = 0f,
            neutralQuota = 1f,
            averageBrightness = 0.5f,
            primaryClassification = "neutro",
            secondaryClassification = "",
            histogram = FloatArray(NUM_BINS)
        )
    }
}
