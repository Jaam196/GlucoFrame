package com.example.feature.frame

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.common.GlucoseUtils
import com.example.core.designsystem.GlucoseColors
import com.example.core.designsystem.LocalDisplayStyle
import com.example.core.designsystem.StyleColors
import com.example.core.model.ChartStyle
import com.example.core.model.ColorSource
import com.example.core.model.GlucoseReading
import com.example.core.model.GlucoseUnit
import com.example.core.model.ThresholdSettings
import com.example.core.model.Treatment
import com.example.core.model.TreatmentKind
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GlucoseChartCanvas(
    readings: List<GlucoseReading>,
    windowHours: Int,
    thresholds: ThresholdSettings,
    unit: GlucoseUnit,
    modifier: Modifier = Modifier,
    treatments: List<Treatment> = emptyList()
) {
    val style = LocalDisplayStyle.current
    val textMeasurer = rememberTextMeasurer()
    var selectedReading by remember { mutableStateOf<GlucoseReading?>(null) }

    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val targetBandColor = GlucoseColors.InRange.copy(alpha = style.chartBandOpacity.coerceIn(0f, 0.6f))
    val refLineColor = onSurfaceVariant.copy(alpha = 0.35f)
    val gridLineColor = onSurfaceVariant.copy(alpha = 0.15f)
    val customChartColor = StyleColors.parse(style.chartCustomHex, GlucoseColors.InRange)
    val labelSize = (10f * style.chartTextScale).sp

    // Colour of a reading: by glucose level (default) or the single colour chosen by the user
    fun colorForValue(valueMgDl: Int): Color =
        if (style.chartColorSource == ColorSource.CUSTOM) {
            customChartColor
        } else {
            GlucoseColors.colorForZone(thresholds.zoneFor(valueMgDl))
        }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Color.Black.copy(alpha = style.chartPanelOpacity.coerceIn(0f, 0.9f)),
                RoundedCornerShape(12.dp)
            )
            .pointerInput(readings) {
                detectTapGestures { tapOffset ->
                    // Touch scrubber logic to view point detail
                    if (readings.isEmpty()) return@detectTapGestures
                    val now = System.currentTimeMillis()
                    val windowStart = now - (windowHours * 3600 * 1000L)
                    val xRatio = (tapOffset.x / size.width).coerceIn(0f, 1f)
                    val tappedTime = windowStart + (xRatio * (now - windowStart)).toLong()
                    val closest = readings.minByOrNull { kotlin.math.abs(it.measuredAtUtcMs - tappedTime) }
                    selectedReading = closest
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            val paddingLeft = 44.dp.toPx()
            val paddingRight = 16.dp.toPx()
            val paddingTop = 20.dp.toPx()
            val paddingBottom = 28.dp.toPx()

            val chartWidth = width - paddingLeft - paddingRight
            val chartHeight = height - paddingTop - paddingBottom
            if (chartWidth <= 0f || chartHeight <= 0f) return@Canvas

            val now = System.currentTimeMillis()
            val windowStart = now - (windowHours * 3600 * 1000L)

            // Y-range: 40 mg/dL up to the top chosen by the user (300 by default)
            val minY = 40f
            val maxY = style.chartMaxMgDl.toFloat().coerceAtLeast(200f)
            val rangeY = maxY - minY

            fun yForMgDl(v: Float): Float {
                val clamped = v.coerceIn(minY, maxY)
                val ratio = (clamped - minY) / rangeY
                return paddingTop + chartHeight * (1f - ratio)
            }

            fun xForTime(t: Long): Float {
                val ratio = (t - windowStart).toFloat() / (now - windowStart).toFloat()
                return paddingLeft + chartWidth * ratio.coerceIn(0f, 1f)
            }

            // 1. Draw Target Range Band (e.g. 70 to 180)
            if (style.chartShowTargetBand) {
                val bandTopY = yForMgDl(thresholds.highMgDl.toFloat())
                val bandBottomY = yForMgDl(thresholds.lowMgDl.toFloat())
                drawRect(
                    color = targetBandColor,
                    topLeft = Offset(paddingLeft, bandTopY),
                    size = Size(chartWidth, bandBottomY - bandTopY)
                )
            }

            // 2. Draw Target Reference Line (e.g. 100 mg/dL)
            if (style.chartShowRefLine) {
                val refY = yForMgDl(thresholds.targetReferenceMgDl.toFloat())
                drawLine(
                    color = refLineColor,
                    start = Offset(paddingLeft, refY),
                    end = Offset(paddingLeft + chartWidth, refY),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
            }

            // 3. Draw Threshold Guide Lines & Labels (54, 70, 180, 250)
            val guideValues = listOf(54, 70, thresholds.targetReferenceMgDl, 180, 250)
            for (gVal in guideValues) {
                if (gVal.toFloat() >= maxY) continue
                val gy = yForMgDl(gVal.toFloat())
                if (style.chartShowGrid) {
                    drawLine(
                        color = gridLineColor,
                        start = Offset(paddingLeft, gy),
                        end = Offset(paddingLeft + chartWidth, gy),
                        strokeWidth = 1f
                    )
                }
                if (style.chartShowAxisLabels) {
                    val labelText = GlucoseUtils.formatValue(gVal, unit)
                    val textLayout = textMeasurer.measure(
                        text = labelText,
                        style = TextStyle(fontSize = labelSize, color = onSurfaceVariant.copy(alpha = 0.7f))
                    )
                    drawText(
                        textLayoutResult = textLayout,
                        topLeft = Offset(paddingLeft - textLayout.size.width - 6.dp.toPx(), gy - textLayout.size.height / 2f)
                    )
                }
            }

            // 4. Time horizontal axis labels (e.g. -24h, -12h, -6h, -3h, -1h, ahora)
            val stepHours = when (windowHours) {
                1 -> 0.25
                3 -> 0.5
                6 -> 1.0
                12 -> 2.0
                else -> 4.0
            }
            val stepMs = (stepHours * 3600 * 1000).toLong()
            var tCursor = (windowStart / stepMs) * stepMs
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

            while (tCursor <= now) {
                if (tCursor >= windowStart) {
                    val tx = xForTime(tCursor)
                    if (style.chartShowGrid) {
                        drawLine(
                            color = gridLineColor,
                            start = Offset(tx, paddingTop),
                            end = Offset(tx, paddingTop + chartHeight),
                            strokeWidth = 1f
                        )
                    }
                    if (style.chartShowAxisLabels) {
                        val tStr = timeFormat.format(Date(tCursor))
                        val tLayout = textMeasurer.measure(
                            text = tStr,
                            style = TextStyle(fontSize = labelSize, color = onSurfaceVariant.copy(alpha = 0.65f))
                        )
                        drawText(
                            textLayoutResult = tLayout,
                            topLeft = Offset(tx - tLayout.size.width / 2f, paddingTop + chartHeight + 4.dp.toPx())
                        )
                    }
                }
                tCursor += stepMs
            }

            // 4b. Insulin (blue triangle, top), food (orange dot, bottom) and notes (grey diamond)
            val insulinColor = Color(0xFF4FC3F7)
            val foodColor = Color(0xFFFFB74D)
            val noteColor = Color(0xFFB0BEC5)
            val markerLabelSize = (9f * style.chartTextScale).sp
            for (t in treatments) {
                if (t.atMs !in windowStart..now) continue
                val tx = xForTime(t.atMs)
                val labelText: String
                val color: Color
                val my: Float
                when (t.kind) {
                    TreatmentKind.INSULIN -> {
                        color = insulinColor
                        my = paddingTop + 9.dp.toPx()
                        labelText = formatAmount(t.amount) + "U"
                        val tri = Path()
                        tri.moveTo(tx - 6.dp.toPx(), my - 6.dp.toPx())
                        tri.lineTo(tx + 6.dp.toPx(), my - 6.dp.toPx())
                        tri.lineTo(tx, my + 6.dp.toPx())
                        tri.close()
                        drawPath(tri, color)
                    }
                    TreatmentKind.FOOD -> {
                        color = foodColor
                        my = paddingTop + chartHeight - 9.dp.toPx()
                        labelText = formatAmount(t.amount) + "g"
                        drawCircle(color = color, radius = 6.dp.toPx(), center = Offset(tx, my))
                    }
                    TreatmentKind.NOTE -> {
                        color = noteColor
                        my = paddingTop + chartHeight / 2f
                        labelText = ""
                        val d = Path()
                        d.moveTo(tx, my - 6.dp.toPx())
                        d.lineTo(tx + 5.dp.toPx(), my)
                        d.lineTo(tx, my + 6.dp.toPx())
                        d.lineTo(tx - 5.dp.toPx(), my)
                        d.close()
                        drawPath(d, color.copy(alpha = 0.8f))
                    }
                }
                // thin guide line from the marker to the other edge so it is easy to relate to the curve
                drawLine(
                    color = color.copy(alpha = 0.18f),
                    start = Offset(tx, paddingTop),
                    end = Offset(tx, paddingTop + chartHeight),
                    strokeWidth = 1.dp.toPx()
                )
                if (labelText.isNotEmpty()) {
                    val lay = textMeasurer.measure(
                        text = labelText,
                        style = TextStyle(fontSize = markerLabelSize, color = color)
                    )
                    val lx = (tx - lay.size.width / 2f).coerceIn(paddingLeft, width - paddingRight - lay.size.width)
                    val ly = if (t.kind == TreatmentKind.INSULIN) my + 7.dp.toPx() else my - 7.dp.toPx() - lay.size.height
                    drawText(textLayoutResult = lay, topLeft = Offset(lx, ly))
                }
            }

            // 5. Filter readings in current window, sorted chronologically
            val windowReadings = readings
                .filter { it.measuredAtUtcMs in windowStart..now }
                .sortedBy { it.measuredAtUtcMs }

            if (windowReadings.isEmpty()) return@Canvas

            val medianIntervalMs = GlucoseUtils.calculateMedianInterval(windowReadings)

            // If few readings (< 3), draw dots only
            if (windowReadings.size < 3) {
                for (r in windowReadings) {
                    val rx = xForTime(r.measuredAtUtcMs)
                    val ry = yForMgDl(r.valueMgDl.toFloat())
                    drawCircle(color = colorForValue(r.valueMgDl), radius = (style.chartPointRadiusDp + 2f).dp.toPx(), center = Offset(rx, ry))
                }
                return@Canvas
            }

            // 6. Draw broken segments on gaps (>= 3x median, min 10 min)
            val segments = mutableListOf<MutableList<GlucoseReading>>()
            var currentSegment = mutableListOf<GlucoseReading>()

            for (i in windowReadings.indices) {
                val current = windowReadings[i]
                if (currentSegment.isEmpty()) {
                    currentSegment.add(current)
                } else {
                    val prev = currentSegment.last()
                    if (GlucoseUtils.isGap(prev.measuredAtUtcMs, current.measuredAtUtcMs, medianIntervalMs)) {
                        // GAP DETECTED: never join or interpolate!
                        segments.add(currentSegment)
                        currentSegment = mutableListOf(current)
                    } else {
                        currentSegment.add(current)
                    }
                }
            }
            if (currentSegment.isNotEmpty()) {
                segments.add(currentSegment)
            }

            val drawLines = style.chartStyle != ChartStyle.POINTS
            val drawPoints = style.chartStyle == ChartStyle.LINE_POINTS || style.chartStyle == ChartStyle.POINTS
            val fillArea = style.chartStyle == ChartStyle.AREA
            val lineWidthPx = style.chartLineWidthDp.dp.toPx()
            val pointRadiusPx = style.chartPointRadiusDp.dp.toPx()
            val baselineY = paddingTop + chartHeight

            for (seg in segments) {
                if (seg.size == 1) {
                    val pt = seg.first()
                    val px = xForTime(pt.measuredAtUtcMs)
                    val py = yForMgDl(pt.valueMgDl.toFloat())
                    drawCircle(color = colorForValue(pt.valueMgDl), radius = pointRadiusPx + 1.dp.toPx(), center = Offset(px, py))
                    continue
                }

                // Area under the line (optional)
                if (fillArea) {
                    val fillPath = Path()
                    fillPath.moveTo(xForTime(seg[0].measuredAtUtcMs), baselineY)
                    for (p in seg) {
                        fillPath.lineTo(xForTime(p.measuredAtUtcMs), yForMgDl(p.valueMgDl.toFloat()))
                    }
                    fillPath.lineTo(xForTime(seg.last().measuredAtUtcMs), baselineY)
                    fillPath.close()
                    drawPath(path = fillPath, color = colorForValue(seg.last().valueMgDl).copy(alpha = 0.22f))
                }

                if (drawLines) {
                    if (style.chartColorSource == ColorSource.ZONE) {
                        // Each piece of the line takes the colour of the glucose level it reaches
                        for (i in 1 until seg.size) {
                            val p0 = seg[i - 1]
                            val p1 = seg[i]
                            drawLine(
                                color = colorForValue(p1.valueMgDl).copy(alpha = 0.9f),
                                start = Offset(xForTime(p0.measuredAtUtcMs), yForMgDl(p0.valueMgDl.toFloat())),
                                end = Offset(xForTime(p1.measuredAtUtcMs), yForMgDl(p1.valueMgDl.toFloat())),
                                strokeWidth = lineWidthPx,
                                cap = StrokeCap.Round
                            )
                        }
                    } else {
                        val path = Path()
                        path.moveTo(xForTime(seg[0].measuredAtUtcMs), yForMgDl(seg[0].valueMgDl.toFloat()))
                        for (i in 1 until seg.size) {
                            val p = seg[i]
                            path.lineTo(xForTime(p.measuredAtUtcMs), yForMgDl(p.valueMgDl.toFloat()))
                        }
                        drawPath(
                            path = path,
                            color = customChartColor.copy(alpha = 0.9f),
                            style = Stroke(
                                width = lineWidthPx,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }

                if (drawPoints) {
                    for (p in seg) {
                        val px = xForTime(p.measuredAtUtcMs)
                        val py = yForMgDl(p.valueMgDl.toFloat())
                        drawCircle(color = colorForValue(p.valueMgDl), radius = pointRadiusPx, center = Offset(px, py))
                    }
                }
            }

            // 7. If user tapped a point, highlight it with scrubber
            selectedReading?.let { sel ->
                if (sel.measuredAtUtcMs in windowStart..now) {
                    val sx = xForTime(sel.measuredAtUtcMs)
                    val sy = yForMgDl(sel.valueMgDl.toFloat())
                    drawCircle(color = Color.White, radius = 6.dp.toPx(), center = Offset(sx, sy))
                    drawCircle(color = colorForValue(sel.valueMgDl), radius = 4.dp.toPx(), center = Offset(sx, sy))

                    val detail = "${GlucoseUtils.formatValue(sel.valueMgDl, unit)} (${GlucoseUtils.formatTime(sel.measuredAtUtcMs)})"
                    val detailLayout = textMeasurer.measure(
                        text = detail,
                        style = TextStyle(fontSize = (11f * style.chartTextScale).sp, color = onSurface)
                    )
                    drawText(
                        textLayoutResult = detailLayout,
                        topLeft = Offset((sx - detailLayout.size.width / 2f).coerceIn(paddingLeft, width - detailLayout.size.width - 8f), (sy - 24.dp.toPx()).coerceAtLeast(0f))
                    )
                }
            }
        }
    }
}

/** 4.0 -> "4", 4.5 -> "4.5" (units / grams shown on the chart markers). */
internal fun formatAmount(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.1f", value)
