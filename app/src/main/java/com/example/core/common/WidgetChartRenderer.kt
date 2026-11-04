package com.example.core.common

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathEffect
import android.graphics.DashPathEffect
import com.example.core.designsystem.GlucoseColors
import com.example.core.model.GlucoseReading
import com.example.core.model.ThresholdSettings

/** Draws the small glucose graph shown inside the home-screen widget (RemoteViews cannot use Compose). */
object WidgetChartRenderer {

    fun render(
        readings: List<GlucoseReading>,
        thresholds: ThresholdSettings,
        windowHours: Int,
        widthPx: Int,
        heightPx: Int,
        maxMgDl: Int
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val nowMs = System.currentTimeMillis()
        val windowMs = windowHours.coerceIn(1, 24) * 3_600_000L
        val startMs = nowMs - windowMs
        val minY = 40f
        val maxY = maxMgDl.toFloat().coerceAtLeast(200f)

        val pad = heightPx * 0.06f
        val chartW = widthPx - 2 * pad
        val chartH = heightPx - 2 * pad

        fun xFor(t: Long): Float = pad + chartW * ((t - startMs).toFloat() / windowMs.toFloat()).coerceIn(0f, 1f)
        fun yFor(v: Int): Float {
            val ratio = (v.toFloat().coerceIn(minY, maxY) - minY) / (maxY - minY)
            return pad + chartH * (1f - ratio)
        }

        // Target range band
        val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(40, 255, 255, 255)
            style = Paint.Style.FILL
        }
        canvas.drawRect(pad, yFor(thresholds.highMgDl), pad + chartW, yFor(thresholds.lowMgDl), bandPaint)

        // Dashed limits of the band
        val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = heightPx * 0.008f + 1f
            pathEffect = DashPathEffect(floatArrayOf(12f, 10f), 0f) as PathEffect
        }
        canvas.drawLine(pad, yFor(thresholds.highMgDl), pad + chartW, yFor(thresholds.highMgDl), guidePaint)
        canvas.drawLine(pad, yFor(thresholds.lowMgDl), pad + chartW, yFor(thresholds.lowMgDl), guidePaint)

        val points = readings
            .filter { it.measuredAtUtcMs in startMs..nowMs }
            .sortedBy { it.measuredAtUtcMs }
        if (points.isEmpty()) return bitmap

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = heightPx * 0.018f + 1f
            strokeCap = Paint.Cap.ROUND
        }
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val dotRadius = heightPx * 0.025f + 1f
        val maxGapMs = 15 * 60_000L

        // Line pieces take the colour of the level they reach; gaps are never joined
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            if (b.measuredAtUtcMs - a.measuredAtUtcMs > maxGapMs) continue
            linePaint.color = GlucoseColors.argbForZone(thresholds.zoneFor(b.valueMgDl))
            canvas.drawLine(xFor(a.measuredAtUtcMs), yFor(a.valueMgDl), xFor(b.measuredAtUtcMs), yFor(b.valueMgDl), linePaint)
        }
        for (p in points) {
            dotPaint.color = GlucoseColors.argbForZone(thresholds.zoneFor(p.valueMgDl))
            canvas.drawCircle(xFor(p.measuredAtUtcMs), yFor(p.valueMgDl), dotRadius, dotPaint)
        }
        return bitmap
    }
}
